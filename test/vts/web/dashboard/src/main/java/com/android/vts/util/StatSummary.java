/*
 * Copyright (c) 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.vts.util;

import com.android.vts.proto.VtsReportMessage.VtsProfilingRegressionMode;

/** Helper object for storing statistics. */
public class StatSummary {
    private String label;
    private double min;
    private double max;
    private double mean;
    private double var;
    private int n;
    private VtsProfilingRegressionMode regression_mode;

    /**
     * Initializes the statistical summary.
     *
     * <p>Sets the label as provided. Initializes the mean, variance, and n (number of values seen)
     * to
     * 0.
     *
     * @param label The (String) label to assign to the summary.
     * @param mode The VtsProfilingRegressionMode to use when analyzing performance.
     */
    public StatSummary(String label, VtsProfilingRegressionMode mode) {
        this.label = label;
        this.min = Double.MAX_VALUE;
        this.max = Double.MIN_VALUE;
        this.mean = 0;
        this.var = 0;
        this.n = 0;
        this.regression_mode = mode;
    }

    /**
     * Update the mean and variance using Welford's single-pass method.
     *
     * @param value The observed value in the stream.
     */
    public void updateStats(double value) {
        n += 1;
        double oldMean = mean;
        mean = oldMean + (value - oldMean) / n;
        var = var + (value - mean) * (value - oldMean);
        if (value < min)
            min = value;
        if (value > max)
            max = value;
    }

    /**
     * Gets the best case of the stream.
     *
     * @return The min or max.
     */
    public double getBestCase() {
        switch (regression_mode) {
            case VTS_REGRESSION_MODE_DECREASING:
                return getMax();
            default:
                return getMin();
        }
    }

    /**
     * Gets the calculated min of the stream.
     *
     * @return The min.
     */
    public double getMin() {
        return min;
    }

    /**
     * Gets the calculated max of the stream.
     *
     * @return The max.
     */
    public double getMax() {
        return max;
    }

    /**
     * Gets the calculated mean of the stream.
     *
     * @return The mean.
     */
    public double getMean() {
        return mean;
    }

    /**
     * Gets the calculated standard deviation of the stream.
     *
     * @return The standard deviation.
     */
    public double getStd() {
        return Math.sqrt(var / (n - 1));
    }

    /**
     * Gets the number of elements that have passed through the stream.
     *
     * @return Number of elements.
     */
    public int getCount() {
        return n;
    }

    /**
     * Gets the label for the summarized statistics.
     *
     * @return The (string) label.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Gets the regression mode.
     *
     * @return The VtsProfilingRegressionMode value.
     */
    public VtsProfilingRegressionMode getRegressionMode() {
        return regression_mode;
    }
}
