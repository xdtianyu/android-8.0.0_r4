/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tradefed.testtype;

import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Interprets the output of tests run with Python's unittest framework and translates it into
 * calls on a series of {@link ITestRunListener}s. Output from these tests follows this
 * EBNF grammar:
 *
 * TestReport   ::= TestResult* Line TimeMetric [FailMessage*] Status.
 * TestResult   ::= string “(“string”)” “…” SingleStatus.
 * FailMessage  ::= EqLine “ERROR:” string “(“string”)” Line Traceback Line.
 * SingleStatus ::= “ok” | “ERROR”.
 * TimeMetric   ::= “Ran” integer “tests in” float ”s”.
 * Status       ::= “OK” | “FAILED (errors=” int “)”.
 * Traceback    ::= string+.
 *
 * Example output:
 * (passing)
 * test_size (test_rangelib.RangeSetTest) ... ok
 * test_str (test_rangelib.RangeSetTest) ... ok
 * test_subtract (test_rangelib.RangeSetTest) ... ok
 * test_to_string_raw (test_rangelib.RangeSetTest) ... ok
 * test_union (test_rangelib.RangeSetTest) ... ok
 *
 * ----------------------------------------------------------------------
 * Ran 5 tests in 0.002s
 *
 * OK
 * (failed)
 * test_size (test_rangelib.RangeSetTest) ... ERROR
 *
 * ======================================================================
 * ERROR: test_size (test_rangelib.RangeSetTest)
 * ----------------------------------------------------------------------
 * Traceback (most recent call last):
 *     File "test_rangelib.py", line 129, in test_rangelib
 *         raise ValueError()
 *     ValueError
 * ----------------------------------------------------------------------
 * Ran 1 tests in 0.001s
 * FAILED (errors=1)
 */
public class PythonUnitTestResultParser extends MultiLineReceiver {

    // Parser state
    String[] mAllLines;
    String mCurrentLine;
    int mLineNum;
    ParserState mCurrentParseState = null; // do not assume it always starts with TEST_CASE

    // Current test state
    TestIdentifier mCurrentTestId;
    StringBuilder mCurrentTraceback;
    long mTotalElapsedTime;
    int mTotalTestCount;

    // General state
    private Map<TestIdentifier, String> mTestResultCache;
    private int mFailedTestCount;
    private final Collection<ITestRunListener> mListeners;
    private final String mRunName;

    // Constant tokens that appear in the result grammar.
    static final String EQLINE =
            "======================================================================";
    static final String LINE =
            "----------------------------------------------------------------------";
    static final String TRACEBACK_LINE = "Traceback (most recent call last):";
    static final String CASE_OK = "ok";
    static final String CASE_EXPECTED_FAILURE_1 = "expected";
    static final String CASE_EXPECTED_FAILURE_2 = "failure";
    static final String RUN_OK = "OK";
    static final String RUN_FAILED = "FAILED";

    /**
     * Keeps track of the state the parser is currently in.
     * Since the parser may receive an incomplete set of lines,
     * it's important for the parse to be resumable. So we need to
     * keep a record of the parser's current state, so we know which
     * method to drop into from processNewLines.
     *
     * State progression:
     *
     *     v------,
     * TEST_CASE-'->[failed?]-(n)->TEST_SUMMARY-->TEST_STATUS-->COMPLETE
     *                         |          ^
     *                        (y)         '------(n)--,
     *                         |  ,-TEST_TRACEBACK->[more?]
     *                         v  v       ^           |
     *                    FAIL_MESSAGE ---'          (y)
     *                            ^-------------------'
     */
    static enum ParserState {
        TEST_CASE,
        TEST_TRACEBACK,
        TEST_SUMMARY,
        TEST_STATUS,
        FAIL_MESSAGE,
        COMPLETE
    }

    private class PythonUnitTestParseException extends Exception {
        static final long serialVersionUID = -3387516993124229948L;

        public PythonUnitTestParseException(String reason) {
            super(reason);
        }
    }

    public PythonUnitTestResultParser(Collection<ITestRunListener> listeners, String runName) {
        mListeners = listeners;
        mRunName = runName;
        mTestResultCache = new HashMap<>();
    }

    @Override
    public void processNewLines(String[] lines) {
        try {
            init(lines);
            do {
                parse();
            } while (advance());
        } catch (PythonUnitTestParseException e) {
            throw new RuntimeException("Failed to parse python-unittest", e);
        }
    }

    void init(String[] lines) throws PythonUnitTestParseException {
        mAllLines = lines;
        mLineNum = 0;
        mCurrentLine = mAllLines[0];
        if (mCurrentParseState == null) {
            // parser on the first line of *the entire* test output
            if (tracebackLine()) {
                throw new PythonUnitTestParseException("Test execution failed");
            }
            mCurrentParseState = ParserState.TEST_CASE;
        }
    }

    void parse() throws PythonUnitTestParseException {
        switch (mCurrentParseState) {
            case TEST_CASE:
                testResult();
                break;
            case TEST_TRACEBACK:
                traceback();
                break;
            case TEST_SUMMARY:
                summary();
                break;
            case TEST_STATUS:
                completeTestRun();
                break;
            case FAIL_MESSAGE:
                failMessage();
                break;
            case COMPLETE:
                break;
        }
    }

    void testResult() throws PythonUnitTestParseException {
        // we're at the end of the TEST_CASE section
        if (eqline()) {
            mCurrentParseState = ParserState.FAIL_MESSAGE;
            return;
        }
        if (line()) {
            mCurrentParseState = ParserState.TEST_SUMMARY;
            return;
        }

        // actually process the test case
        mCurrentParseState = ParserState.TEST_CASE;
        String[] toks = mCurrentLine.split(" ");
        try {
            String testName = toks[0];
            // strip surrounding parens from class name
            String testClass = toks[1].substring(1, toks[1].length() - 1);
            mCurrentTestId = new TestIdentifier(testClass, testName);
            // 3rd token is just "..."
            if (toks.length == 4) {
                // one-word status ("ok" | "ERROR")
                String status = toks[3];
                if (CASE_OK.equals(status)) {
                    markTestSuccess();
                }
                // if there's an error just do nothing, we can't get the trace
                // immediately anyway
            } else if (toks.length == 5) {
                // two-word status ("expected failure")
                String status1 = toks[3];
                String status2 = toks[4];
                if (CASE_EXPECTED_FAILURE_1.equals(status1)
                        && CASE_EXPECTED_FAILURE_2.equals(status2)) {
                    markTestSuccess();
                }
            } else {
                parseError("TestResult");
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            CLog.d("Underlying error in testResult: " + e);
            throw new PythonUnitTestParseException("FailMessage");
        }
    }

    void failMessage() throws PythonUnitTestParseException {
        // traceback is starting
        if (line()) {
            mCurrentParseState = ParserState.TEST_TRACEBACK;
            mCurrentTraceback = new StringBuilder();
            return;
        }
        String[] toks = mCurrentLine.split(" ");
        // 1st token is "ERROR:"
        try {
            String testName = toks[1];
            String testClass = toks[2].substring(1, toks[2].length() - 1);
            mCurrentTestId = new TestIdentifier(testClass, testName);
        } catch (ArrayIndexOutOfBoundsException e) {
            CLog.d("Underlying error in failMessage: " + e);
            throw new PythonUnitTestParseException("FailMessage");
        }
    }

    void traceback() throws PythonUnitTestParseException {
        // traceback is always terminated with LINE or EQLINE
        while (!mCurrentLine.startsWith(LINE) && !mCurrentLine.startsWith(EQLINE)) {
            mCurrentTraceback.append(mCurrentLine);
            if (!advance()) return;
        }
        // end of traceback section
        // first report the failure
        markTestFailure();
        // move on to the next section
        if (line()) {
            mCurrentParseState = ParserState.TEST_SUMMARY;
        }
        else if (eqline()) {
            mCurrentParseState = ParserState.FAIL_MESSAGE;
        }
        else {
            parseError(EQLINE);
        }
    }

    void summary() throws PythonUnitTestParseException {
        String[] toks = mCurrentLine.split(" ");
        double time = 0;
        try {
            mTotalTestCount = Integer.parseInt(toks[1]);
        } catch (NumberFormatException e) {
            parseError("integer");
        }
        try {
            time = Double.parseDouble(toks[4].substring(0, toks[4].length() - 1));
        } catch (NumberFormatException e) {
            parseError("double");
        }
        mTotalElapsedTime = (long) time * 1000;
        mCurrentParseState = ParserState.TEST_STATUS;
    }

    boolean completeTestRun() throws PythonUnitTestParseException {
        String failReason = String.format("Failed %d tests", mFailedTestCount);
        for (ITestRunListener listener: mListeners) {
            // do testRunStarted
            listener.testRunStarted(mRunName, mTotalTestCount);

            // mark each test passed or failed
            for (Entry<TestIdentifier, String> test : mTestResultCache.entrySet()) {
                listener.testStarted(test.getKey());
                if (test.getValue() != null) {
                    listener.testFailed(test.getKey(), test.getValue());
                }
                listener.testEnded(test.getKey(), Collections.<String, String>emptyMap());
            }

            // mark the whole run as passed or failed
            if (mCurrentLine.startsWith(RUN_FAILED)) {
                listener.testRunFailed(failReason);
            }
            listener.testRunEnded(mTotalElapsedTime, Collections.<String, String>emptyMap());
            if (!mCurrentLine.startsWith(RUN_FAILED) && !mCurrentLine.startsWith(RUN_OK)) {
                parseError("Status");
            }
        }
        return true;
    }

    boolean eqline() {
        return mCurrentLine.startsWith(EQLINE);
    }

    boolean line() {
        return mCurrentLine.startsWith(LINE);
    }

    boolean tracebackLine() {
        return mCurrentLine.startsWith(TRACEBACK_LINE);
    }

    /**
     * Advance to the next non-empty line.
     * @return true if a non-empty line was found, false otherwise.
     */
    boolean advance() {
        do {
            if (mLineNum == mAllLines.length - 1) {
                return false;
            }
            mCurrentLine = mAllLines[++mLineNum];
        } while (mCurrentLine.length() == 0);
        return true;
    }

    private void parseError(String expected)
            throws PythonUnitTestParseException {
        throw new PythonUnitTestParseException(
                String.format("Expected \"%s\" on line %d, found %s instead",
                    expected, mLineNum + 1, mCurrentLine));
    }

    private void markTestSuccess() {
        mTestResultCache.put(mCurrentTestId, null);
    }

    private void markTestFailure() {
        mTestResultCache.put(mCurrentTestId, mCurrentTraceback.toString());
        mFailedTestCount++;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
