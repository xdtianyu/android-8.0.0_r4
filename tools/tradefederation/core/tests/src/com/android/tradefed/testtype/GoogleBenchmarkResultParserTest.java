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
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.log.LogUtil.CLog;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link GoogleBenchmarkResultParser}.
 */
public class GoogleBenchmarkResultParserTest extends TestCase {

    private static final String TEST_TYPE_DIR = "testtype";
    private static final String GBENCH_OUTPUT_FILE_1 = "gbench_output1.json";
    private static final String GBENCH_OUTPUT_FILE_2 = "gbench_output2.json";
    private static final String GBENCH_OUTPUT_FILE_3 = "gbench_output3.json";
    private static final String GBENCH_OUTPUT_FILE_4 = "gbench_output4.json";

    private static final String TEST_RUN = "test_run";

    /**
     * Helper to read a file from the res/testtype directory and return its contents as a String[]
     *
     * @param filename the name of the file (without the extension) in the res/testtype directory
     * @return a String[] of the
     */
    private CollectingOutputReceiver readInFile(String filename) {
        CollectingOutputReceiver output = null;
        try {
            output = new CollectingOutputReceiver();
            InputStream gtestResultStream1 = getClass().getResourceAsStream(File.separator +
                    TEST_TYPE_DIR + File.separator + filename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(gtestResultStream1));
            String line = null;
            while ((line = reader.readLine()) != null) {
                output.addOutput(line.getBytes(), 0, line.length());
            }
        }
        catch (NullPointerException e) {
            CLog.e("GTest output file does not exist: " + filename);
        }
        catch (IOException e) {
            CLog.e("Unable to read contents of gtest output file: " + filename);
        }
        return output;
    }

    /**
     * Tests the parser for a simple test output for 2 tests.
     */
    public void testParseSimpleFile() throws Exception {
        ITestRunListener mMockInvocationListener =
                EasyMock.createMock(ITestRunListener.class);
        mMockInvocationListener.testStarted((TestIdentifier)EasyMock.anyObject());
        EasyMock.expectLastCall().times(3);
        Map<String, String> test1 = new HashMap<String, String>();
        test1.put("cpu_time", "5");
        test1.put("real_time", "5");
        test1.put("name", "BM_one");
        test1.put("iterations", "109451958");
        Map<String, String> test2 = new HashMap<String, String>();
        test2.put("cpu_time", "11");
        test2.put("real_time", "1");
        test2.put("name", "BM_two");
        test2.put("iterations", "50906784");
        Map<String, String> test3 = new HashMap<String, String>();
        test3.put("cpu_time", "60");
        test3.put("real_time", "60");
        test3.put("name", "BM_string_strlen/64");
        test3.put("iterations", "10499948");
        test3.put("bytes_per_second", "1061047935");
        mMockInvocationListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                EasyMock.eq(test1));
        mMockInvocationListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                EasyMock.eq(test2));
        mMockInvocationListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                EasyMock.eq(test3));
        EasyMock.replay(mMockInvocationListener);
        CollectingOutputReceiver contents =  readInFile(GBENCH_OUTPUT_FILE_1);
        GoogleBenchmarkResultParser resultParser =
                new GoogleBenchmarkResultParser(TEST_RUN, mMockInvocationListener);
        Map<String, String> results = resultParser.parse(contents);
        Map<String, String> expectedRes = new HashMap<String, String>();
        expectedRes.put("date", "2016-03-07 10:59:01");
        expectedRes.put("num_cpus", "48");
        expectedRes.put("mhz_per_cpu", "2601");
        expectedRes.put("cpu_scaling_enabled", "true");
        expectedRes.put("library_build_type", "debug");

        expectedRes.put("Pass", "3");
        assertEquals(expectedRes, results);
        EasyMock.verify(mMockInvocationListener);
    }

    /**
     * Tests the parser for a two simple test with different keys on the second test.
     */
    @SuppressWarnings("unchecked")
    public void testParseSimpleFile_twoTests() throws Exception {
        ITestRunListener mMockInvocationListener =
                EasyMock.createMock(ITestRunListener.class);
        mMockInvocationListener.testStarted((TestIdentifier)EasyMock.anyObject());
        EasyMock.expectLastCall().times(2);
        Map<String, String> test1 = new HashMap<String, String>();
        test1.put("cpu_time", "5");
        test1.put("real_time", "5");
        test1.put("name", "BM_one");
        test1.put("iterations", "109451958");
        mMockInvocationListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                EasyMock.eq(test1));
        mMockInvocationListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mMockInvocationListener);
        CollectingOutputReceiver contents =  readInFile(GBENCH_OUTPUT_FILE_2);
        GoogleBenchmarkResultParser resultParser =
                new GoogleBenchmarkResultParser(TEST_RUN, mMockInvocationListener);
        resultParser.parse(contents);
        EasyMock.verify(mMockInvocationListener);
    }

    /**
     * Tests the parser with an error in the context, should stop parsing because format is
     * unexpected.
     */
    @SuppressWarnings("unchecked")
    public void testParse_contextError() throws Exception {
        ITestRunListener mMockInvocationListener =
                EasyMock.createMock(ITestRunListener.class);
        mMockInvocationListener.testRunFailed((String)EasyMock.anyObject());
        EasyMock.replay(mMockInvocationListener);
        CollectingOutputReceiver contents =  readInFile(GBENCH_OUTPUT_FILE_3);
        GoogleBenchmarkResultParser resultParser =
                new GoogleBenchmarkResultParser(TEST_RUN, mMockInvocationListener);
        resultParser.parse(contents);
        EasyMock.verify(mMockInvocationListener);
    }

    /**
     * Tests the parser with an error: context is fine but no benchmark results
     */
    @SuppressWarnings("unchecked")
    public void testParse_noBenchmarkResults() throws Exception {
        ITestRunListener mMockInvocationListener =
                EasyMock.createMock(ITestRunListener.class);
        mMockInvocationListener.testRunFailed((String)EasyMock.anyObject());
        EasyMock.replay(mMockInvocationListener);
        CollectingOutputReceiver contents =  readInFile(GBENCH_OUTPUT_FILE_4);
        GoogleBenchmarkResultParser resultParser =
                new GoogleBenchmarkResultParser(TEST_RUN, mMockInvocationListener);
        resultParser.parse(contents);
        EasyMock.verify(mMockInvocationListener);
    }

    /**
     * Test for {@link GoogleBenchmarkResultParser#parseJsonToMap(JSONObject)}
     */
    public void testJsonParse() throws JSONException {
        ITestRunListener mMockInvocationListener =
                EasyMock.createMock(ITestRunListener.class);
        String jsonTest = "{ \"key1\": \"value1\", \"key2\": 2, \"key3\": [\"one\", \"two\"]}";
        JSONObject test = new JSONObject(jsonTest);
        GoogleBenchmarkResultParser resultParser =
                new GoogleBenchmarkResultParser(TEST_RUN, mMockInvocationListener);
        Map<String, String> results = resultParser.parseJsonToMap(test);
        assertEquals(results.get("key1"), "value1");
        assertEquals(results.get("key2"), "2");
        assertEquals(results.get("key3"), "[\"one\",\"two\"]");
    }
}
