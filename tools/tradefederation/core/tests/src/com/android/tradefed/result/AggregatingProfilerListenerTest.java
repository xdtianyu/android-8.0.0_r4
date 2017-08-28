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

package com.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.profiler.IAggregatingTestProfiler;
import com.android.tradefed.profiler.MetricOutputData;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AggregatingProfilerListenerTest {

    private IAggregatingTestProfiler mMockProfiler =
            EasyMock.createMock(IAggregatingTestProfiler.class);
    private AggregatingProfilerListener mListener;
    private MetricOutputData mData;

    @Before
    public void setUp() throws Exception {
        mData = new MetricOutputData();
        EasyMock.expect(mMockProfiler.getMetricOutputUtil()).andReturn(mData);
        EasyMock.replay(mMockProfiler);
        mListener = new AggregatingProfilerListener(mMockProfiler);
        EasyMock.reset(mMockProfiler);
    }

    @Test
    public void testTestStarted() throws Exception {
        mMockProfiler.startRecordingMetrics();
        EasyMock.expectLastCall();
        EasyMock.replay(mMockProfiler);
        mListener.testStarted(new TestIdentifier("a", "b"));
        EasyMock.verify(mMockProfiler);
    }

    @Test
    public void testTestEnded() throws Exception {
        TestIdentifier id = new TestIdentifier("a", "b");
        EasyMock.expect(mMockProfiler.stopRecordingMetrics(EasyMock.eq(id))).andReturn(null);
        EasyMock.replay(mMockProfiler);
        mListener.testEnded(id, null);
        EasyMock.verify(mMockProfiler);
    }
}