package com.android.tradefed.profiler;

import com.android.tradefed.profiler.recorder.IMetricsRecorder;

import java.util.List;
import java.util.Map;

/**
 * An {@link ITestProfiler} which handles aggregating metrics across multiple devices and test runs.
 * This interface should be used for any profiler which sends different metrics for {@link
 * #getAggregateMetrics}.
 */
public interface IAggregatingTestProfiler extends ITestProfiler {
    /**
     * Return a description of this test profiler.
     * @return the description
     */
    public String getDescription();

    /**
     * Return metrics that have been aggregated over all tests and devices.
     * @return the metrics
     */
    public Map<String, Double> getAggregateMetrics();

    /**
     * Return a {@link List} of all {@link IMetricsRecorder}s used by this profiler.
     * @return the recorders
     */
    public List<IMetricsRecorder> getRecorders();

    /**
     * Return the {@link MetricOutputData} object used to hold formatted metrics.
     * @return the current {@link MetricOutputData}
     */
    public MetricOutputData getMetricOutputUtil();
}
