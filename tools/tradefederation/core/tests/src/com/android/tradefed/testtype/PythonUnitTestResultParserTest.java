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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;

import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.util.ArrayUtil;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import java.util.Collections;

public class PythonUnitTestResultParserTest extends TestCase {

    private PythonUnitTestResultParser mParser;
    private ITestRunListener mMockListener;

    @Override
    public void setUp() throws Exception {
        mMockListener = createMock(ITestRunListener.class);
        mParser = new PythonUnitTestResultParser(ArrayUtil.list(mMockListener), "test");
    }

    public void testAdvance_noBlankLines() throws Exception {
        String[] lines = {"hello", "goodbye"};
        mParser.init(lines);
        boolean result = mParser.advance();
        assertTrue(result);
        assertEquals("goodbye", mParser.mCurrentLine);
        assertEquals(PythonUnitTestResultParser.ParserState.TEST_CASE, mParser.mCurrentParseState);
        assertEquals(1, mParser.mLineNum);
    }

    public void testAdvance_blankLinesMid() throws Exception {
        String[] lines = {"hello", "", "goodbye"};
        mParser.init(lines);
        boolean result = mParser.advance();
        assertTrue(result);
        assertEquals("goodbye", mParser.mCurrentLine);
        assertEquals(PythonUnitTestResultParser.ParserState.TEST_CASE, mParser.mCurrentParseState);
        assertEquals(2, mParser.mLineNum);
    }

    public void testAdvance_atEnd() throws Exception {
        String[] lines = {"hello"};
        mParser.init(lines);
        boolean result = mParser.advance();
        assertTrue(!result);
        assertEquals("hello", mParser.mCurrentLine);
        assertEquals(PythonUnitTestResultParser.ParserState.TEST_CASE, mParser.mCurrentParseState);
        assertEquals(0, mParser.mLineNum);
    }

    public void testParse_singleTestPass_contiguous() throws Exception {
        String[] output = {
                "b (a) ... ok",
                "",
                PythonUnitTestResultParser.LINE,
                "Ran 1 tests in 1s",
                "",
                "OK"
        };
        TestIdentifier[] ids = {new TestIdentifier("a", "b")};
        boolean[] didPass = {true};
        setTestIdChecks(ids, didPass);
        setRunListenerChecks(1, 1000, true);

        replay(mMockListener);
        mParser.processNewLines(output);
        verify(mMockListener);
    }

    public void testParse_singleTestPassWithExpectedFailure_contiguous() throws Exception {
        String[] output = {
                "b (a) ... expected failure",
                "",
                PythonUnitTestResultParser.LINE,
                "Ran 1 tests in 1s",
                "",
                "OK (expected failures=1)"
        };
        TestIdentifier[] ids = {new TestIdentifier("a", "b")};
        boolean[] didPass = {true};
        setTestIdChecks(ids, didPass);
        setRunListenerChecks(1, 1000, true);

        replay(mMockListener);
        mParser.processNewLines(output);
        verify(mMockListener);
    }

    public void testParse_multiTestPass_contiguous() throws Exception {
        String[] output = {
                "b (a) ... ok",
                "d (c) ... ok",
                "",
                PythonUnitTestResultParser.LINE,
                "Ran 2 tests in 1s",
                "",
                "OK"
        };
        TestIdentifier[] ids = {new TestIdentifier("a", "b"), new TestIdentifier("c", "d")};
        boolean didPass[] = {true, true};
        setTestIdChecks(ids, didPass);
        setRunListenerChecks(2, 1000, true);

        replay(mMockListener);
        mParser.processNewLines(output);
        verify(mMockListener);
    }

    public void testParse_multiTestPassWithOneExpectedFailure_contiguous() throws Exception {
        String[] output = {
                "b (a) ... expected failure",
                "d (c) ... ok",
                "",
                PythonUnitTestResultParser.LINE,
                "Ran 2 tests in 1s",
                "",
                "OK (expected failures=1)"
        };
        TestIdentifier[] ids = {new TestIdentifier("a", "b"), new TestIdentifier("c", "d")};
        boolean[] didPass = {true, true};
        setTestIdChecks(ids, didPass);
        setRunListenerChecks(2, 1000, true);

        replay(mMockListener);
        mParser.processNewLines(output);
        verify(mMockListener);
    }

    public void testParse_multiTestPassWithAllExpectedFailure_contiguous() throws Exception {
        String[] output = {
                "b (a) ... expected failure",
                "d (c) ... expected failure",
                "",
                PythonUnitTestResultParser.LINE,
                "Ran 2 tests in 1s",
                "",
                "OK (expected failures=2)"
        };
        TestIdentifier[] ids = {new TestIdentifier("a", "b"), new TestIdentifier("c", "d")};
        boolean[] didPass = {true, true};
        setTestIdChecks(ids, didPass);
        setRunListenerChecks(2, 1000, true);

        replay(mMockListener);
        mParser.processNewLines(output);
        verify(mMockListener);
    }

    public void testParse_multiTestPass_pauseDuringRun() throws Exception {
        String[] output1 = {
                "b (a) ... ok"};
        String[] output2 = {
                "d (c) ... ok",
                "",
                PythonUnitTestResultParser.LINE,
                "Ran 2 tests in 1s",
                "",
                "OK"
        };
        TestIdentifier[] ids = new TestIdentifier[2];
        ids[0] = new TestIdentifier("a", "b");
        boolean didPass[] = new boolean[2];
        didPass[0] = true;
        setRunListenerChecks(2, 1000, true);
        setTestIdChecks(ids, didPass);

        replay(mMockListener);
        mParser.processNewLines(output1);

        reset(mMockListener);
        ids[1] = new TestIdentifier("c", "d");
        didPass[1] = true;
        setTestIdChecks(ids, didPass);
        setRunListenerChecks(2, 1000, true);

        replay(mMockListener);
        mParser.processNewLines(output2);
        verify(mMockListener);
    }

    public void testParse_singleTestFail_contiguous() throws Exception {
        String[] output = {
                "b (a) ... ERROR",
                "",
                PythonUnitTestResultParser.EQLINE,
                "ERROR: b (a)",
                PythonUnitTestResultParser.LINE,
                "Traceback (most recent call last):",
                "  File \"test_rangelib.py\", line 129, in test_reallyfail",
                "    raise ValueError()",
                "ValueError",
                "",
                PythonUnitTestResultParser.LINE,
                "Ran 1 tests in 1s",
                "",
                "FAILED (errors=1)"
        };
        TestIdentifier[] ids = {new TestIdentifier("a", "b")};
        boolean[] didPass = {false};
        setRunListenerChecks(1, 1000, false);
        setTestIdChecks(ids, didPass);

        replay(mMockListener);
        mParser.processNewLines(output);
        verify(mMockListener);
    }

    public void testParse_multiTestFailWithExpectedFailure_contiguous() throws Exception {
        String[] output = {
                "b (a) ... expected failure",
                "d (c) ... ERROR",
                "",
                PythonUnitTestResultParser.EQLINE,
                "ERROR: d (c)",
                PythonUnitTestResultParser.LINE,
                "Traceback (most recent call last):",
                "  File \"test_rangelib.py\", line 129, in test_reallyfail",
                "    raise ValueError()",
                "ValueError",
                "",
                PythonUnitTestResultParser.LINE,
                "Ran 1 tests in 1s",
                "",
                "FAILED (errors=1)"
        };
        TestIdentifier[] ids = {new TestIdentifier("a", "b"), new TestIdentifier("c", "d")};
        boolean[] didPass = {true, false};
        setRunListenerChecks(1, 1000, false);
        setTestIdChecks(ids, didPass);

        replay(mMockListener);
        mParser.processNewLines(output);
        verify(mMockListener);
    }

    public void testParse_singleTestFail_pauseInTraceback() throws Exception {
        String[] output1 = {
                "b (a) ... ERROR",
                "",
                PythonUnitTestResultParser.EQLINE,
                "ERROR: b (a)",
                PythonUnitTestResultParser.LINE,
                "Traceback (most recent call last):",
                "  File \"test_rangelib.py\", line 129, in test_reallyfail"};
        String[] output2 = {
                "    raise ValueError()",
                "ValueError",
                "",
                PythonUnitTestResultParser.LINE,
                "Ran 1 tests in 1s",
                "",
                "FAILED (errors=1)"
        };
        TestIdentifier[] ids = {new TestIdentifier("a", "b")};
        boolean[] didPass = {false};
        setRunListenerChecks(1, 1000, false);
        setTestIdChecks(ids, didPass);

        replay(mMockListener);
        mParser.processNewLines(output1);

        reset(mMockListener);
        ids[0] = new TestIdentifier("a", "b");
        didPass[0] = false;
        setTestIdChecks(ids, didPass);
        setRunListenerChecks(1, 1000, false);

        replay(mMockListener);
        mParser.processNewLines(output2);
        verify(mMockListener);
    }

    private void setRunListenerChecks(int numTests, long time, boolean didPass) {
        mMockListener.testRunStarted("test", numTests);
        expectLastCall().times(1);
        mMockListener.testRunFailed((String)anyObject());
        if (!didPass) {
            expectLastCall().times(1);
        } else {
            expectLastCall().andThrow(new AssertionFailedError()).anyTimes();
        }
        mMockListener.testRunEnded(time, Collections.<String, String>emptyMap());
        expectLastCall().times(1);
    }

    private void setTestIdChecks(TestIdentifier[] ids, boolean[] didPass) {
        for (int i = 0; i < ids.length; i++) {
            mMockListener.testStarted(ids[i]);
            expectLastCall().times(1);
            if (didPass[i]) {
                mMockListener.testEnded(ids[i], Collections.<String, String>emptyMap());
                expectLastCall().times(1);
                mMockListener.testFailed(eq(ids[i]), (String)anyObject());
                expectLastCall().andThrow(new AssertionFailedError()).anyTimes();
            } else {
                mMockListener.testFailed(eq(ids[i]), (String)anyObject());
                expectLastCall().times(1);
                mMockListener.testEnded(ids[i], Collections.<String, String>emptyMap());
                expectLastCall().times(1);
            }
        }
    }
}

