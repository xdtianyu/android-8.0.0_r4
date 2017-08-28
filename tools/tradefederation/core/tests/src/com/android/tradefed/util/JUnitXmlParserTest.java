/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tradefed.util;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.util.xml.AbstractXmlParser.ParseException;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

/**
 * Simple unit tests for {@link JUnitXmlParser}.
 */
public class JUnitXmlParserTest extends TestCase {

    private CollectingTestListener mListener = new CollectingTestListener();
    private JUnitXmlParser mParser;

    @Override
    public void setUp() {
        mParser = new JUnitXmlParser(mListener);
    }

    /**
     * Test behavior when data to parse is empty
     */
    public void testEmptyParse() {
        try {
            mParser.parse(new ByteArrayInputStream(new byte[0]));
        } catch (ParseException e) {
            // expected
            return;
        }
        fail("ParseException not thrown");
    }

    /**
     * Simple success test for xml parsing
     */
    public void testParse() throws ParseException {
        mParser.parse(extractTestXml("JUnitXmlParserTest_testParse.xml"));
        assertEquals(3, mListener.getNumTotalTests());
        assertEquals(1, mListener.getNumAllFailedTests());
        TestRunResult runData = mListener.getCurrentRunResults();
        assertEquals("null", runData.getName());
        assertTrue(runData.getTestResults().containsKey(new TestIdentifier("PassTest", "testPass")));
        TestResult result = runData.getTestResults().get(new TestIdentifier("FailTest", "testFail"));
        assertNotNull(result);
        assertTrue(result.getStackTrace().contains("java.lang.NullPointerException"));
    }

    private InputStream extractTestXml(String fileName) {
        return getClass().getResourceAsStream(File.separator + "util" +
                File.separator + fileName);
    }
}
