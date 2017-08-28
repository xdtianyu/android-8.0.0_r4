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
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.profiler.recorder.IMetricsRecorder;
import com.android.tradefed.result.ITestInvocationListener;
import java.util.Map;

/**
 * Collects metrics about the device to be made available to {@link ITestInvocationListener}s.
 *
 * <p>In addition to collecting metrics, implementers can define <i>background work</i> to be done,
 * which is intended to simulate specific resource consumption conditions on the device.
 */
public interface ITestProfiler {

    /**
     * Set up the test profiler.
     *
     * @param context the {@link IInvocationContext} of the test invocation.
     */
    public void setUp(IInvocationContext context) throws DeviceNotAvailableException;

    /**
     * Begins recording metrics for a single test on all devices. This method is called on each call
     * to {@link ITestInvocationListener#testStarted}.
     */
    public void startRecordingMetrics() throws DeviceNotAvailableException;

    /**
     * Stops recording metrics for a single test on all devices and returns an aggregated version of
     * the metrics. The metrics are aggregated with {@link IMetricsRecorder#getMergeFunction}. This
     * method is called on each call to {@link ITestInvocationListener#testEnded}.
     *
     * @param test the test to stop recording on
     * @return a {@link Map} containing the metrics.
     * @throws DeviceNotAvailableException
     */
    public Map<String, Double> stopRecordingMetrics(TestIdentifier test)
            throws DeviceNotAvailableException;

    /**
     * Send all of the metrics recorded by this profiler to an {@link ITestInvocationListener}.
     *
     * @param listener the listener to send metrics to.
     */
    public void reportAllMetrics(ITestInvocationListener listener);
}
