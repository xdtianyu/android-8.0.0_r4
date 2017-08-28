/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;

import org.easymock.EasyMock;

import java.util.HashMap;
import java.util.Map;


/**
 * Unit tests for {@link GTestResultParserTest}.
 */
public class GTestResultParserTest extends GTestParserTestBase {

    /**
     * Tests the parser for a simple test run output with 11 tests.
     */
    @SuppressWarnings("unchecked")
    public void testParseSimpleFile() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_1);
        ITestRunListener mockRunListener = EasyMock.createMock(ITestRunListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 11);
        // 11 passing test cases in this run
        for (int i=0; i<11; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }
        // TODO: validate param values
        mockRunListener.testRunEnded(EasyMock.anyLong(),
                (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
    }

    /**
     * Tests the parser for a simple test run output with 53 tests and no times.
     */
    @SuppressWarnings("unchecked")
    public void testParseSimpleFileNoTimes() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_2);
        ITestRunListener mockRunListener = EasyMock.createMock(ITestRunListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 53);
        // 53 passing test cases in this run
        for (int i=0; i<53; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }
        // TODO: validate param values
        mockRunListener.testRunEnded(EasyMock.anyLong(),
                (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
    }

    /**
     * Tests the parser for a simple test run output with 0 tests and no times.
     */
    public void testParseNoTests() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_3);
        Map<String, String> expected = new HashMap<>();
        ITestRunListener mockRunListener = EasyMock.createMock(ITestRunListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 0);
        mockRunListener.testRunEnded(EasyMock.anyLong(), EasyMock.eq(expected));
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
    }

    /**
     * Tests the parser for a run with 268 tests.
     */
    @SuppressWarnings("unchecked")
    public void testParseLargerFile() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_4);
        ITestRunListener mockRunListener = EasyMock.createMock(ITestRunListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 268);
        // 268 passing test cases in this run
        for (int i=0; i<268; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }
        // TODO: validate param values
        mockRunListener.testRunEnded(EasyMock.anyLong(),
                (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
    }

    /**
     * Tests the parser for a run with test failures.
     */
    @SuppressWarnings("unchecked")
    public void testParseWithFailures() throws Exception {
        String MESSAGE_OUTPUT =
                "This is some random text that should get captured by the parser.";
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_5);
        ITestRunListener mockRunListener = EasyMock.createMock(ITestRunListener.class);
        // 13 test cases in this run
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 13);
        mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        // test failure
        mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestIdentifier)EasyMock.anyObject(), (String)EasyMock.anyObject());
        mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());
        // 4 passing tests
        for (int i=0; i<4; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }
        // 2 consecutive test failures
        mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestIdentifier)EasyMock.anyObject(), (String)EasyMock.anyObject());
        mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());

        mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestIdentifier)EasyMock.anyObject(), EasyMock.matches(MESSAGE_OUTPUT));
        mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());

        // 5 passing tests
        for (int i=0; i<5; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }

        // TODO: validate param values
        mockRunListener.testRunEnded(EasyMock.anyLong(),
                (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
    }

    /**
     * Tests the parser for a run with test errors.
     */
    @SuppressWarnings("unchecked")
    public void testParseWithErrors() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_6);
        ITestRunListener mockRunListener = EasyMock.createMock(ITestRunListener.class);
        // 10 test cases in this run
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 10);
        mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        // test failure
        mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestIdentifier)EasyMock.anyObject(), (String)EasyMock.anyObject());
        mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());
        // 5 passing tests
        for (int i=0; i<5; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }
        // another test error
        mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestIdentifier)EasyMock.anyObject(), (String)EasyMock.anyObject());
        mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());
        // 2 passing tests
        for (int i=0; i<2; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }

        // TODO: validate param values
        mockRunListener.testRunEnded(EasyMock.anyLong(),
                (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
    }

    /**
     * Tests the parser for a run with 11 tests.
     */
    @SuppressWarnings("unchecked")
    public void testParseNonAlignedTag() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_7);
        ITestRunListener mockRunListener = EasyMock.createMock(ITestRunListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 11);
        // 11 passing test cases in this run
        for (int i=0; i<11; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }
        mockRunListener.testRunEnded(EasyMock.anyLong(),
                (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
    }

    /**
     * Tests the parser for a simple test run output with 18 tests with Non GTest format
     * Should not crash.
     */
    @SuppressWarnings("unchecked")
    public void testParseSimpleFile_AltFormat() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_8);
        ITestRunListener mockRunListener = EasyMock.createMock(ITestRunListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 18);
        // 14 passing tests
        for (int i=0; i<14; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }
        // 3 consecutive test failures
        mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestIdentifier)EasyMock.anyObject(), (String)EasyMock.anyObject());
        mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());
        mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestIdentifier)EasyMock.anyObject(), (String)EasyMock.anyObject());
        mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());

        mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestIdentifier)EasyMock.anyObject(), (String)EasyMock.anyObject());
        mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());
        // 1 passing test
        mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());

        mockRunListener.testRunEnded(EasyMock.anyLong(),
                (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
    }
}
