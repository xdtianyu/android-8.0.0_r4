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

import java.util.function.BiFunction;

/**
 * A {@link IMetricsRecorder} that aggregates metrics using some basic numeric functions. This class
 * doesn't implement any methods of the {@link IMetricsRecorder} interface, it just provides
 * (possibly stateful) numeric functions to its subclasses.
 */
public abstract class NumericMetricsRecorder implements IMetricsRecorder {

    private double mRunningCount = 0;

    /**
     * Provides an aggregator function which sums values.
     *
     * @return a sum function
     */
    protected BiFunction<Double, Double, Double> sum() {
        return (oldVal, newVal) -> oldVal + newVal;
    }

    /**
     * Provides an aggregator function which counts values.
     *
     * @return a count function
     */
    protected BiFunction<Double, Double, Double> count() {
        return (oldVal, newVal) -> oldVal + 1;
    }

    /**
     * Provides an aggregator function which counts positive values.
     *
     * @return a countpos function
     */
    protected BiFunction<Double, Double, Double> countpos() {
        return (oldVal, newVal) -> (newVal == 0 ? oldVal : oldVal + 1);
    }

    /**
     * Provides an aggregator function which average values.
     *
     * @return an average function
     */
    protected BiFunction<Double, Double, Double> avg() {
        return (prevAvg, newVal) -> prevAvg + ((newVal - prevAvg) / ++mRunningCount);
    }
}
