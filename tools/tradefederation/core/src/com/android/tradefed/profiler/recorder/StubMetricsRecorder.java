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
import com.android.tradefed.log.LogUtil.CLog;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * An {@link IMetricsRecorder} which does nothing.
 */
public class StubMetricsRecorder implements IMetricsRecorder {

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, Collection<String> descriptors)
            throws DeviceNotAvailableException {
        CLog.d("Setting up StubMetricsRecorder");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startMetrics(ITestDevice device) {
        CLog.d("Running startMetrics");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Double> stopMetrics(ITestDevice device) {
        CLog.d("Running stopMetrics");
        Map<String, Double> superFakeMetrics = new HashMap<>();
        superFakeMetrics.put("stub", 1.0);
        return superFakeMetrics;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "stub-metrics";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BiFunction<Double, Double, Double> getMergeFunction(String key) {
        return (x, y) -> x + y;
    }
}
