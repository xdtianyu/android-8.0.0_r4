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
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.profiler.IAggregatingTestProfiler;
import com.android.tradefed.profiler.ITestProfiler;
import com.android.tradefed.profiler.MetricOutputData;

import java.util.Map;

/**
 * An {@link ITestInvocationListener} which communicates test progress to instances of {@link
 * ITestProfiler}.
 */
public class AggregatingProfilerListener implements ITestInvocationListener {

    private IAggregatingTestProfiler mProfiler;
    private MetricOutputData mMetricOutputUtil;

    public AggregatingProfilerListener(ITestProfiler profiler) {
        if (!(profiler instanceof IAggregatingTestProfiler)) {
            throw new IllegalArgumentException("must receive an IAggregatingTestProfiler");
        }
        mProfiler = (IAggregatingTestProfiler) profiler;
        mMetricOutputUtil = mProfiler.getMetricOutputUtil();
    }

    /** {@inheritDoc} */
    @Override
    public void testRunStarted(String runName, int testCount) {
        mMetricOutputUtil.startNewMetric();
    }

    /** {@inheritDoc} */
    @Override
    public void testStarted(TestIdentifier test) {
        try {
            mProfiler.startRecordingMetrics();
        } catch (DeviceNotAvailableException e) {
            throw new RuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void testEnded(TestIdentifier test, Map<String, String> metrics) {
        try {
            mProfiler.stopRecordingMetrics(test);
        } catch (DeviceNotAvailableException e) {
            throw new RuntimeException(e);
        }
    }
}
