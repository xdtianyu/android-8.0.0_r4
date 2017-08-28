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

import com.android.vts.entity.ProfilingPointRunEntity;
import com.android.vts.proto.VtsReportMessage.VtsProfilingRegressionMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Represents statistical summaries of each profiling point. */
public class ProfilingPointSummary implements Iterable<StatSummary> {
    private List<StatSummary> statSummaries;
    private Map<String, Integer> labelIndices;
    private List<String> labels;
    private VtsProfilingRegressionMode regressionMode;
    public String xLabel;
    public String yLabel;

    /** Initializes the summary with empty arrays. */
    public ProfilingPointSummary() {
        statSummaries = new ArrayList<>();
        labelIndices = new HashMap<>();
        labels = new ArrayList<>();
    }

    /**
     * Determines if a data label is present in the profiling point.
     *
     * @param label The name of the label.
     * @return true if the label is present, false otherwise.
     */
    public boolean hasLabel(String label) {
        return labelIndices.containsKey(label);
    }

    /**
     * Gets the statistical summary for a specified data label.
     *
     * @param label The name of the label.
     * @return The StatSummary object if it exists, or null otherwise.
     */
    public StatSummary getStatSummary(String label) {
        if (!hasLabel(label))
            return null;
        return statSummaries.get(labelIndices.get(label));
    }

    /**
     * Gets the last-seen regression mode.
     *
     * @return The VtsProfilingRegressionMode value.
     */
    public VtsProfilingRegressionMode getRegressionMode() {
        return regressionMode;
    }

    /**
     * Updates the profiling summary with the data from a new profiling report.
     *
     * @param profilingRun The profiling point run entity object containing profiling data.
     */
    public void update(ProfilingPointRunEntity profilingRun) {
        for (int i = 0; i < profilingRun.labels.size(); i++) {
            String label = profilingRun.labels.get(i);
            if (!labelIndices.containsKey(label)) {
                StatSummary summary = new StatSummary(label, profilingRun.regressionMode);
                labelIndices.put(label, statSummaries.size());
                statSummaries.add(summary);
            }
            StatSummary summary = getStatSummary(label);
            summary.updateStats(profilingRun.values.get(i));
        }
        this.regressionMode = profilingRun.regressionMode;
        this.labels = profilingRun.labels;
        this.xLabel = profilingRun.xLabel;
        this.yLabel = profilingRun.yLabel;
    }

    /**
     * Updates the profiling summary at a label with the data from a new profiling report.
     *
     * <p>Updates the summary specified by the label with all values provided in the report. If
     * labels
     * are provided in the report, they will be ignored -- all values are updated only to the
     * provided
     * label.
     *
     * @param profilingEntity The ProfilingPointRunEntity object containing profiling data.
     * @param label The ByteString label for which all values in the report will be updated.
     */
    public void updateLabel(ProfilingPointRunEntity profilingEntity, String label) {
        if (!labelIndices.containsKey(label)) {
            StatSummary summary = new StatSummary(label, profilingEntity.regressionMode);
            labelIndices.put(label, labels.size());
            labels.add(label);
            statSummaries.add(summary);
        }
        StatSummary summary = getStatSummary(label);
        for (long value : profilingEntity.values) {
            summary.updateStats(value);
        }
        this.regressionMode = profilingEntity.regressionMode;
        this.xLabel = profilingEntity.xLabel;
        this.yLabel = profilingEntity.yLabel;
    }

    /**
     * Gets an iterator that returns stat summaries in the ordered the labels were specified in the
     * ProfilingReportMessage objects.
     */
    @Override
    public Iterator<StatSummary> iterator() {
        Iterator<StatSummary> it = new Iterator<StatSummary>() {
            private int currentIndex = 0;

            @Override
            public boolean hasNext() {
                return labels != null && currentIndex < labels.size();
            }

            @Override
            public StatSummary next() {
                String label = labels.get(currentIndex++);
                return statSummaries.get(labelIndices.get(label));
            }
        };
        return it;
    }
}
