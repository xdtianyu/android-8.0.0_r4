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

/**
 * A class representing a metric which {@link TraceMetricsRecorder} can receive.
 *
 * Instances of TraceMetric should usually be acquired via the {@link #parse} method, which
 * constructs them from a string with the following format:
 *   prefix:funcname:param[=expval]:mtype
 * These variables represent the following:
 * * prefix: The directory under /d/tracing/events containing the metric name.
 * * funcname: The name of the metric, located under /d/tracing/events/[prefix].
 * * param: Which column of output from /d/tracing/trace to record.
 * * expval (optional): An expected value for this metric. Metrics will only be recorded if they
 *                      match this value.
 * * mtype: The {@link MetricType} which describes how this metric should be aggregated.
 */
public class TraceMetric {

    private String mPrefix;
    private String mFuncName;
    private String mParam;
    private Double mExpectedVal;
    private MetricType mMetricType;

    /**
     * Constructor with no expected value parameter.
     * @param prefix the directory under /d/tracing/events containing the metric
     * @param funcName the name of the metric from /d/tracing/events/[prefix]
     * @param param the column from /d/tracing/trace containing data to record
     * @param metricType the {@link MetricType} describing how to aggregate this metric
     */
    public TraceMetric(String prefix, String funcName, String param, MetricType metricType) {
        mPrefix = prefix;
        mFuncName = funcName;
        mParam = param;
        mExpectedVal = null;
        mMetricType = metricType;
    }

    /**
     * Constructor with expected value parameter.
     * @param prefix the directory under /d/tracing/events containing the metric
     * @param funcName the name of the metric from /d/tracing/events/[prefix]
     * @param param the column from /d/tracing/trace containing data to record
     * @param expectedVal the expected value of [param]
     * @param metricType the {@link MetricType} describing how to aggregate this metric
     */
    public TraceMetric(String prefix, String funcName, String param,
            Double expectedVal, MetricType metricType) {
        mPrefix = prefix;
        mFuncName = funcName;
        mParam = param;
        mExpectedVal = expectedVal;
        mMetricType = metricType;
    }

    public String getPrefix() {
        return mPrefix;
    }

    public String getFuncName() {
        return mFuncName;
    }

    public String getParam() {
        return mParam;
    }

    public Double getExpectedVal() {
        return mExpectedVal;
    }

    public MetricType getMetricType() {
        return mMetricType;
    }

    @Override
    public String toString() {
        String base = String.format("%s:%s:%s", getPrefix(), getFuncName(), getParam());
        if (getExpectedVal() != null) {
            base = base + "=" + getExpectedVal();
        }
        base += ":" + getMetricType();
        return base;
    }

    /**
     * Expected format: prefix:funcname:param[=expval]:mtype
     */
    public static TraceMetric parse(String text) {
        String[] parts = text.split(":");
        if (!(parts.length == 4)) {
            throw new IllegalArgumentException(
                    "bad metric format (should be prefix:funcname:param[=expval]:evtype): " + text);
        }
        String prefix = parts[0];
        String funcname = parts[1];
        MetricType mtype = MetricType.valueOf(parts[3]);
        if (parts[2].contains("=")) {
            String[] paramSplit = parts[2].split("=");
            Double exVal;
            if (paramSplit[1].contains("x")) {
                exVal = Double.valueOf(Integer.parseInt(paramSplit[1].substring(2), 16));
            } else {
                exVal = Double.parseDouble(paramSplit[1]);
            }
            return new TraceMetric(prefix, funcname, paramSplit[0], exVal, mtype);
        } else {
            return new TraceMetric(prefix, funcname, parts[2], mtype);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mExpectedVal == null) ? 0 : mExpectedVal.hashCode());
        result = prime * result + ((mFuncName == null) ? 0 : mFuncName.hashCode());
        result = prime * result + ((mMetricType == null) ? 0 : mMetricType.hashCode());
        result = prime * result + ((mParam == null) ? 0 : mParam.hashCode());
        result = prime * result + ((mPrefix == null) ? 0 : mPrefix.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        TraceMetric other = (TraceMetric) obj;
        if (mExpectedVal == null) {
            if (other.mExpectedVal != null) return false;
        } else if (!mExpectedVal.equals(other.mExpectedVal)) return false;
        if (mFuncName == null) {
            if (other.mFuncName != null) return false;
        } else if (!mFuncName.equals(other.mFuncName)) return false;
        if (mMetricType != other.mMetricType) return false;
        if (mParam == null) {
            if (other.mParam != null) return false;
        } else if (!mParam.equals(other.mParam)) return false;
        if (mPrefix == null) {
            if (other.mPrefix != null) return false;
        } else if (!mPrefix.equals(other.mPrefix)) return false;
        return true;
    }
}
