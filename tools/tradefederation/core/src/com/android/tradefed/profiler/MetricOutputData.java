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

package com.android.tradefed.profiler;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import java.util.Map;

/**
 * A class which produces formatted metrics. It builds up formatted metrics using a
 * {@link StringBuilder} as a buffer.
 */
public class MetricOutputData {

    StringBuilder mMetrics;

    public MetricOutputData() {
        mMetrics = new StringBuilder();
    }

    /**
     * Start a new metric in the output buffer.
     */
    public void startNewMetric() {
        mMetrics.append("\n\n");
    }

    /**
     * Add new metrics to the output buffer.
     * @param prefix A prefix to append to the test name.
     * @param test A {@link TestIdentifier} which contains the test name.
     * @param metrics The metrics to add.
     */
    public void addMetrics(String prefix, TestIdentifier test, Map<String, Double> metrics) {
        mMetrics.append(String.format("%s:%s:\n", prefix, test.getTestName()));
        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            mMetrics.append(String.format("%s=%.5f\n", entry.getKey(), entry.getValue()));
        }
        mMetrics.append("---\n");
    }

    /**
     * Add new metrics to the output buffer.
     * @param prefix A prefix to append to the test name.
     * @param test The name of the test.
     * @param metrics The metrics to add.
     */
    public void addMetrics(String prefix, String test, Map<String, Double> metrics) {
        mMetrics.append(String.format("%s:%s:\n", prefix, test));
        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            mMetrics.append(String.format("%s=%.5f\n", entry.getKey(), entry.getValue()));
        }
        mMetrics.append("---\n");
    }

    /**
     * Returns an {@link InputStreamSource} representation of the output buffer
     * @return the {@link InputStreamSource}
     */
    public InputStreamSource getFormattedMetrics() {
        return new ByteArrayInputStreamSource(mMetrics.toString().getBytes());
    }
}
