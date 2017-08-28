/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tradefed.profiler.recorder;

import com.android.tradefed.device.ITestDevice;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Map;

@RunWith(JUnit4.class)
public class TraceMetricsRecorderTest {

    private static class MockTraceMetricsRecorder extends TraceMetricsRecorder {

        /**
         * A shim to return a reader over a string instead of a file. To fit the method signature,
         * the filename is used as the string.
         */
        @Override
        protected BufferedReader getReaderFromFile(File f) {
            return new BufferedReader(new StringReader(f.getName()));
        }
    }

    TraceMetricsRecorder mRecorder = new MockTraceMetricsRecorder();
    ITestDevice mDevice;

    @Before
    public void setUp() throws Exception {
        mDevice = EasyMock.createMock(ITestDevice.class);
    }

    @Test
    public void testIgnoreComment() throws Exception {
        String line = "# hello";
        EasyMock.expect(mDevice.executeShellCommand((String) EasyMock.anyObject()))
                .andReturn("")
                .anyTimes();
        EasyMock.expect(mDevice.pullFile((String) EasyMock.anyObject()))
                .andStubReturn(new File(line));
        EasyMock.replay(mDevice);
        mRecorder.setUp(mDevice, Arrays.asList("mmc:mmc_cmd_rw_start:int_status:COUNT"));
        Map<String, Double> metrics = mRecorder.stopMetrics(mDevice);
        EasyMock.verify(mDevice);
        Assert.assertTrue(metrics.isEmpty());
    }

    @Test
    public void testSingleLineRecord_noMatch() throws Exception {
        String line =
                " msm-core:sampli-287   [000] d.h2 87062.264209: mmc_cmd_rw_end: cmd=0,int_status=0x00000001,response=0x00000000";
        EasyMock.expect(mDevice.executeShellCommand((String) EasyMock.anyObject()))
                .andReturn("")
                .anyTimes();
        EasyMock.expect(mDevice.pullFile((String) EasyMock.anyObject()))
                .andStubReturn(new File(line));
        EasyMock.replay(mDevice);
        mRecorder.setUp(mDevice, Arrays.asList("mmc:mmc_cmd_rw_start:int_status:COUNT"));
        Map<String, Double> metrics = mRecorder.stopMetrics(mDevice);
        EasyMock.verify(mDevice);
        Assert.assertTrue(metrics.isEmpty());
    }

    @Test
    public void testSingleLineRecord_match() throws Exception {
        String line =
                " msm-core:sampli-287   [000] d.h2 87062.264209: mmc_cmd_rw_end: cmd=0,int_status=0x00000001,response=0x00000000";
        EasyMock.expect(mDevice.executeShellCommand((String) EasyMock.anyObject()))
                .andReturn("")
                .anyTimes();
        EasyMock.expect(mDevice.pullFile((String) EasyMock.anyObject()))
                .andStubReturn(new File(line));
        EasyMock.replay(mDevice);
        mRecorder.setUp(mDevice, Arrays.asList("mmc:mmc_cmd_rw_end:int_status:COUNT"));
        Map<String, Double> metrics = mRecorder.stopMetrics(mDevice);
        EasyMock.verify(mDevice);
        Assert.assertEquals(metrics.get("mmc:mmc_cmd_rw_end:int_status:COUNT"), 1.0, 0.001);
    }

    @Test
    public void testMultiLineRecord_Count() throws Exception {
        String line =
                " msm-core:sampli-287   [000] d.h2 87062.264209: mmc_cmd_rw_end: cmd=0,int_status=0x00000001,response=0x00000000\n"
                        + "          <idle>-0     [000] d.h3 87062.279952: mmc_cmd_rw_end: cmd=1,int_status=0x00000001,response=0x00ff8080\n"
                        + "         mmcqd:0-260   [000] d..2 87062.293003: mmc_cmd_rw_start: cmd=1,arg=0x40000080,flags=0x000000e1\n"
                        + "         <idle>-1     [000] d.h3 87062.293286: mmc_cmd_rw_end: cmd=1,int_status=0x00000001,response=0xc0ff8080\n"
                        + "         <idle>-2     [000] d.h3 87062.293286: mmc_cmd_rw_end: cmd=1,int_statrs=0x00000001,response=0xc0ff8080\n";
        EasyMock.expect(mDevice.executeShellCommand((String) EasyMock.anyObject()))
                .andReturn("")
                .anyTimes();
        EasyMock.expect(mDevice.pullFile((String) EasyMock.anyObject()))
                .andStubReturn(new File(line));
        EasyMock.replay(mDevice);
        mRecorder.setUp(mDevice, Arrays.asList("mmc:mmc_cmd_rw_end:int_status:COUNT"));
        Map<String, Double> metrics = mRecorder.stopMetrics(mDevice);
        EasyMock.verify(mDevice);
        Assert.assertEquals(metrics.get("mmc:mmc_cmd_rw_end:int_status:COUNT"), 3.0, 0.001);
    }
}
