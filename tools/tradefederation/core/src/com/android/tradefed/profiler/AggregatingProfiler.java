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
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.profiler.recorder.IMetricsRecorder;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link ITestProfiler} which handles aggregating metrics across multiple devices and test runs.
 * This class should be used as a base class for any profiler which sends different metrics for
 * {@link #getAggregateMetrics}.
 */
public class AggregatingProfiler implements IAggregatingTestProfiler {

    @Option(name = "recorder", description = "full class names of metrics recorders to add")
    private Collection<String> mRecorderClassNames = new ArrayList<>();

    @Option(name = "metric-descriptor", description = "metric descriptors to be forwarded to"
            + " recorders")
    private Collection<String> mMetricDescriptors = new ArrayList<>();

    @Option(name = "profiler-name", description = "name for this profiler")
    private String mDescription;

    private IInvocationContext mContext;
    private List<IMetricsRecorder> mRecorders;
    private MetricOutputData mOutputUtil;
    private Map<String, Double> mAggregateMetrics;

    public AggregatingProfiler() {
        // some data needs to be accessible before setUp is invoked
        mRecorders = new ArrayList<>();
        mAggregateMetrics = new HashMap<String, Double>();
        mOutputUtil = new MetricOutputData();
    }

    /** {@inheritDoc} */
    @Override
    public void setUp(IInvocationContext context) throws DeviceNotAvailableException {
        mContext = context;
        for (String clazz : mRecorderClassNames) {
            try {
                mRecorders.add((IMetricsRecorder) Class.forName(clazz).newInstance());
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        for (ITestDevice device : context.getDevices()) {
            for (IMetricsRecorder recorder : getRecorders()) {
                recorder.setUp(device, mMetricDescriptors);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void startRecordingMetrics() throws DeviceNotAvailableException {
        for (ITestDevice device : getDevices()) {
            for (IMetricsRecorder recorder : getRecorders()) {
                recorder.startMetrics(device);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Double> stopRecordingMetrics(TestIdentifier test) throws DeviceNotAvailableException {
        Map<String, Double> allMetrics = new HashMap<>();
        for (ITestDevice device : getDevices()) {
            for (IMetricsRecorder recorder : getRecorders()) {
                Map<String, Double> metrics = recorder.stopMetrics(device);
                for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                    String key = entry.getKey();
                    // merge metrics into the aggregate map unmodified
                    mAggregateMetrics.merge(key, entry.getValue(), recorder.getMergeFunction(key));
                    // this map aggregates metrics just across devices
                    allMetrics.merge(key, entry.getValue(), recorder.getMergeFunction(key));
                    CLog.v("allMetrics is %s", allMetrics);
                }
            }
        }
        getMetricOutputUtil().addMetrics("test", test, allMetrics);
        return allMetrics;
    }

    /** {@inheritDoc} */
    @Override
    public void reportAllMetrics(ITestInvocationListener listener) {
        mOutputUtil.addMetrics("aggregate", mContext.getTestTag(), mAggregateMetrics);
        listener.testLog(getDescription(), LogDataType.TEXT, mOutputUtil.getFormattedMetrics());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return mDescription;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Double> getAggregateMetrics() {
        return mAggregateMetrics;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IMetricsRecorder> getRecorders() {
        return mRecorders;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MetricOutputData getMetricOutputUtil() {
        return mOutputUtil;
    }

    /**
     * Get all {@link ITestDevice}s being profiled. Exposed for testing.
     *
     * @return the {@link ITestDevice}s
     */
    protected Collection<ITestDevice> getDevices() {
        return mContext.getDevices();
    }

    /** Set the aggregate metrics map. Exposed for testing. */
    protected void setAggregateMetrics(Map<String, Double> metrics) {
        mAggregateMetrics = metrics;
    }

    /** Set the aggregate metrics map. Exposed for testing. */
    protected void setMetricOutputUtil(MetricOutputData mod) {
        mOutputUtil = mod;
    }
}
