/**
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.vts.util;

import com.android.vts.entity.ProfilingPointRunEntity;
import com.android.vts.proto.VtsReportMessage.VtsProfilingRegressionMode;
import com.google.appengine.api.datastore.Entity;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** PerformanceSummary, an object summarizing performance across profiling points for a test run. */
public class PerformanceSummary {
    protected static Logger logger = Logger.getLogger(PerformanceSummary.class.getName());
    private Map<String, ProfilingPointSummary> summaryMap;
    private Set<String> optionSplitKeys;

    /** Creates a performance summary object. */
    public PerformanceSummary() {
        this.summaryMap = new HashMap<>();
        this.optionSplitKeys = new HashSet<>();
    }

    /**
     * Creates a performance summary object with the specified device name filter. If the specified
     * name is null, then use no filter.
     *
     * @param optionSplitKeys A set of option keys to split on (i.e. profiling data with different
     *     values corresponding to the option key will be analyzed as different profiling points).
     */
    public PerformanceSummary(Set<String> optionSplitKeys) {
        this();
        this.optionSplitKeys = optionSplitKeys;
    }

    /**
     * Add the profiling data from a ProfilingPointRunEntity to the performance summary.
     *
     * @param profilingRun The Entity object whose data to add.
     */
    public void addData(Entity profilingRun) {
        ProfilingPointRunEntity pt = ProfilingPointRunEntity.fromEntity(profilingRun);
        if (pt == null)
            return;
        if (pt.regressionMode == VtsProfilingRegressionMode.VTS_REGRESSION_MODE_DISABLED) {
            return;
        }

        String name = pt.name;
        String optionSuffix = PerformanceUtil.getOptionAlias(profilingRun, optionSplitKeys);

        if (pt.labels != null) {
            if (pt.labels.size() != pt.values.size()) {
                logger.log(Level.WARNING, "Labels and values are different sizes.");
                return;
            }
            if (!optionSuffix.equals("")) {
                name += " (" + optionSuffix + ")";
            }
            if (!summaryMap.containsKey(name)) {
                summaryMap.put(name, new ProfilingPointSummary());
            }
            summaryMap.get(name).update(pt);
        } else {
            // Use the option suffix as the table name.
            // Group all profiling points together into one table
            if (!summaryMap.containsKey(optionSuffix)) {
                summaryMap.put(optionSuffix, new ProfilingPointSummary());
            }
            summaryMap.get(optionSuffix).updateLabel(pt, pt.name);
        }
    }

    /**
     * Adds a ProfilingPointSummary object into the summary map only if the key doesn't exist.
     *
     * @param key The name of the profiling point.
     * @param summary The ProfilingPointSummary object to add into the summary map.
     * @return True if the data was inserted into the performance summary, false otherwise.
     */
    public boolean insertProfilingPointSummary(String key, ProfilingPointSummary summary) {
        if (!summaryMap.containsKey(key)) {
            summaryMap.put(key, summary);
            return true;
        }
        return false;
    }

    /**
     * Gets the number of profiling points.
     *
     * @return The number of profiling points in the performance summary.
     */
    public int size() {
        return summaryMap.size();
    }

    /**
     * Gets the names of the profiling points.
     *
     * @return A string array of profiling point names.
     */
    public String[] getProfilingPointNames() {
        String[] profilingNames = summaryMap.keySet().toArray(new String[summaryMap.size()]);
        Arrays.sort(profilingNames);
        return profilingNames;
    }

    /**
     * Determines if a profiling point is described by the performance summary.
     *
     * @param profilingPointName The name of the profiling point.
     * @return True if the profiling point is contained in the performance summary, else false.
     */
    public boolean hasProfilingPoint(String profilingPointName) {
        return summaryMap.containsKey(profilingPointName);
    }

    /**
     * Gets the profiling point summary by name.
     *
     * @param profilingPointName The name of the profiling point to fetch.
     * @return The ProfilingPointSummary object describing the specified profiling point.
     */
    public ProfilingPointSummary getProfilingPointSummary(String profilingPointName) {
        return summaryMap.get(profilingPointName);
    }
}
