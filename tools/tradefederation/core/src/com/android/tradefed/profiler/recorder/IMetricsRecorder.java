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

package com.android.tradefed.profiler.recorder;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.profiler.ITestProfiler;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Records metrics to communicate directly to an {@link ITestProfiler}. The metrics recorded cover a
 * single test on a single {@link ITestDevice} only.
 */
public interface IMetricsRecorder {

    /**
     * Sets up the recorder. After calling this method, the recorder is ready to begin. This method
     * should be called by {@link ITestProfiler#setUp}.
     *
     * @param device The device on which this recorder will monitor metrics.
     * @param descriptors A collection of strings describing what metrics to collect and by what
     *     means to collect them. It is up to individual implementers to decide how to interpret
     *     these string descriptors.
     */
    public void setUp(ITestDevice device, Collection<String> descriptors)
            throws DeviceNotAvailableException;

    /**
     * Begin recording metrics on a device. This should be called at the beginning of a single test.
     *
     * @param device the device to start recording on
     * @throws DeviceNotAvailableException
     */
    public void startMetrics(ITestDevice device) throws DeviceNotAvailableException;

    /**
     * Stop recording metrics on a device. This should be called at the end of a single test.
     *
     * @param device the device to stop recording on
     * @return a {@link Map} which contains all metrics recorded over the duration of the test.
     * @throws DeviceNotAvailableException
     */
    public Map<String, Double> stopMetrics(ITestDevice device) throws DeviceNotAvailableException;

    /**
     * Returns a {@link BiFunction} describing how to aggregate results for a particular metric over
     * the course of multiple test runs. Examples of relevant functions are average, sum, or count.
     * The {@link BiFunction} is used as an argument to {@link Map#merge}.
     *
     * @param key the name of the metric
     * @return a {@link BiFunction} used to aggregate values of that metric
     */
    public BiFunction<Double, Double, Double> getMergeFunction(String key);

    /**
     * Returns a name for this {@link IMetricsRecorder}.
     *
     * @return the name
     */
    public String getName();
}
