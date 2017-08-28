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

package com.android.tradefed.profiler;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.profiler.recorder.IMetricsRecorder;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Tests for {@link AggregatingProfiler}.
 */
@RunWith(JUnit4.class)
public class AggregatingProfilerTest {

    AggregatingProfiler mProfiler;

    ITestDevice mTestDevice1 = EasyMock.createMock(ITestDevice.class);
    ITestDevice mTestDevice2 = EasyMock.createMock(ITestDevice.class);

    IMetricsRecorder mRecorder1 = EasyMock.createMock(IMetricsRecorder.class);
    IMetricsRecorder mRecorder2 = EasyMock.createMock(IMetricsRecorder.class);

    IInvocationContext mContext = EasyMock.createMock(IInvocationContext.class);

    @Before
    public void setUp() throws Exception {
        mProfiler =
                new AggregatingProfiler() {
                    @Override
                    public List<ITestDevice> getDevices() {
                        return Arrays.asList(mTestDevice1, mTestDevice2);
                    }

                    @Override
                    public List<IMetricsRecorder> getRecorders() {
                        return Arrays.asList(mRecorder1, mRecorder2);
                    }

                    @Override
                    public String getDescription() {
                        return "test";
                    }

                    @Override
                    public MetricOutputData getMetricOutputUtil() {
                        return new MetricOutputData();
                    }
                };
        EasyMock.expect(mTestDevice1.getSerialNumber()).andReturn("-1").anyTimes();
        EasyMock.expect(mTestDevice2.getSerialNumber()).andReturn("-2").anyTimes();
        EasyMock.expect(mRecorder1.getName()).andReturn("1").anyTimes();
        EasyMock.expect(mRecorder2.getName()).andReturn("2").anyTimes();
        EasyMock.expect(mContext.getDevices()).andReturn(new ArrayList<>()).anyTimes();
        EasyMock.expect(mContext.getTestTag()).andReturn("AggregatingProfilerTest").anyTimes();
        EasyMock.replay(mContext);
        mProfiler.setUp(mContext);
    }

    @Test
    public void testStartMetrics() throws Exception {
        mRecorder1.startMetrics((ITestDevice) EasyMock.anyObject());
        EasyMock.expectLastCall().times(2);
        mRecorder2.startMetrics((ITestDevice) EasyMock.anyObject());
        EasyMock.expectLastCall().times(2);
        EasyMock.replay(mTestDevice1, mTestDevice2, mRecorder1, mRecorder2);
        mProfiler.startRecordingMetrics();
        EasyMock.verify(mRecorder1, mRecorder2);
    }

    @Test
    public void testStopMetrics() throws Exception {
        TestIdentifier id = new TestIdentifier("foo", "bar");
        Map<String, Double> metric1 = new HashMap<>();
        metric1.put("hello1", 1.0);
        metric1.put("goodbye1", 2.0);
        metric1.put("generic", 3.0);
        Map<String, Double> metric2 = new HashMap<>();
        metric2.put("hello2", 1.0);
        metric2.put("goodbye2", 2.0);
        metric2.put("generic", 3.0);
        EasyMock.expect(mRecorder1.stopMetrics((ITestDevice) EasyMock.anyObject()))
                .andReturn(metric1)
                .times(2);
        EasyMock.expect(mRecorder2.stopMetrics((ITestDevice) EasyMock.anyObject()))
                .andReturn(metric2)
                .times(2);
        EasyMock.expect(mRecorder1.getMergeFunction((String) EasyMock.anyObject()))
                .andReturn(sum())
                .times(12);
        EasyMock.expect(mRecorder2.getMergeFunction((String) EasyMock.anyObject()))
                .andReturn(sum())
                .times(12);
        EasyMock.replay(mTestDevice1, mTestDevice2, mRecorder1, mRecorder2);
        mProfiler.setAggregateMetrics(new HashMap<>());

        Map<String, Double> m = mProfiler.stopRecordingMetrics(id);

        EasyMock.verify(mRecorder1, mRecorder2);
        Assert.assertEquals(m, mProfiler.getAggregateMetrics());
        Assert.assertTrue(mProfiler.getAggregateMetrics().containsKey("generic"));
        Assert.assertEquals(12.0d, mProfiler.getAggregateMetrics().get("generic"), 0.001);
        Assert.assertTrue(m.containsKey("generic"));
        Assert.assertEquals(12.0d, m.get("generic"), 0.001);

        EasyMock.reset(mRecorder1, mRecorder2);
        EasyMock.expect(mRecorder1.stopMetrics((ITestDevice) EasyMock.anyObject()))
                .andReturn(metric1)
                .times(2);
        EasyMock.expect(mRecorder2.stopMetrics((ITestDevice) EasyMock.anyObject()))
                .andReturn(metric2)
                .times(2);
        EasyMock.expect(mRecorder1.getMergeFunction((String) EasyMock.anyObject()))
                .andReturn(sum())
                .times(12);
        EasyMock.expect(mRecorder2.getMergeFunction((String) EasyMock.anyObject()))
                .andReturn(sum())
                .times(12);
        EasyMock.replay(mRecorder1, mRecorder2);

        Map<String, Double> m2 = mProfiler.stopRecordingMetrics(id);
        Assert.assertTrue(mProfiler.getAggregateMetrics().containsKey("generic"));
        Assert.assertEquals(24.0d, mProfiler.getAggregateMetrics().get("generic"), 0.001);
        Assert.assertTrue(m2.containsKey("generic"));
        Assert.assertEquals(12.0d, m2.get("generic"), 0.001);
    }

    @Test
    public void testReportAllMetrics() throws Exception {
        Map<String, Double> metric1 = new HashMap<>();
        metric1.put("hello1", 1.0);
        mProfiler.setAggregateMetrics(metric1);
        ITestInvocationListener mockListener = EasyMock.createMock(ITestInvocationListener.class);
        mockListener.testLog((String)EasyMock.anyObject(), EasyMock.eq(LogDataType.TEXT),
                (InputStreamSource)EasyMock.anyObject());
        EasyMock.replay(mockListener);
        mProfiler.reportAllMetrics(mockListener);
        EasyMock.verify(mockListener);
    }

    private BiFunction<Double, Double, Double> sum() {
        return (x, y) -> x + y;
    }
}
