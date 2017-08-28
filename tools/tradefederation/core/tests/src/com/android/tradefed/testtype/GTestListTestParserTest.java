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

import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;

import org.easymock.EasyMock;

import java.util.List;
import java.util.Map;

/**
 * Test {@link GTestListTestParser}
 */
public class GTestListTestParserTest extends GTestParserTestBase {

    /**
     * Tests the parser for a test run output with 1 class and 23 tests.
     */
    @SuppressWarnings("unchecked")
    public void testParseSimpleList() throws Exception {
        String[] contents =  readInFile(GTEST_LIST_FILE_1);
        ITestRunListener mockRunListener = EasyMock.createMock(ITestRunListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 23);
        // 11 passing test cases in this run
        for (int i = 0; i < 23; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }
        mockRunListener.testRunEnded(EasyMock.anyLong(),
                (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mockRunListener);
        GTestListTestParser parser = new GTestListTestParser(TEST_MODULE_NAME, mockRunListener);
        parser.processNewLines(contents);
        parser.flush();
        EasyMock.verify(mockRunListener);
        verifyTestIdentifiers(parser.mTests, 1);
    }

    /**
     * Tests the parser for a test run output with 29 classes and 127 tests.
     */
    @SuppressWarnings("unchecked")
    public void testParseMultiClassList() throws Exception {
        String[] contents =  readInFile(GTEST_LIST_FILE_2);
        ITestRunListener mockRunListener = EasyMock.createMock(ITestRunListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 127);
        // 11 passing test cases in this run
        for (int i = 0; i < 127; ++i) {
            mockRunListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mockRunListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    (Map<String, String>)EasyMock.anyObject());
        }
        mockRunListener.testRunEnded(EasyMock.anyLong(),
                (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mockRunListener);
        GTestListTestParser parser = new GTestListTestParser(TEST_MODULE_NAME, mockRunListener);
        parser.processNewLines(contents);
        parser.flush();
        EasyMock.verify(mockRunListener);
        verifyTestIdentifiers(parser.mTests, 29);
    }

    /**
     * Tests the parser against a malformed list of tests.
     */
    public void testParseMalformedList() throws Exception {
        String[] contents =  readInFile(GTEST_LIST_FILE_3);
        ITestRunListener mockRunListener = EasyMock.createMock(ITestRunListener.class);
        GTestListTestParser parser = new GTestListTestParser(TEST_MODULE_NAME, mockRunListener);
        try {
            parser.processNewLines(contents);
            parser.flush();
            fail("Expected IllegalStateException not thrown");
        } catch (IllegalStateException ise) {
            // expected
        }
    }

    private void verifyTestIdentifiers(List<TestIdentifier> tests, int classesExpected)
            throws Exception {
        int classesFound = 0;
        String lastClass = "notaclass";
        for (TestIdentifier test : tests) {
            String className = test.getClassName();
            String methodName = test.getTestName();
            assertFalse(String.format("Class name %s improperly formatted", className),
                    className.matches("^.*\\.$")); // should not end with '.'
            assertFalse(String.format("Method name %s improperly formatted", methodName),
                    methodName.matches("^\\s+.*")); // should not begin with whitespace
            if (!className.equals(lastClass)) {
                lastClass = className;
                classesFound++;
            }
        }
        assertEquals(classesExpected, classesFound);
    }
}
