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

import com.android.vts.entity.DeviceInfoEntity;
import com.android.vts.entity.ProfilingPointRunEntity;
import com.android.vts.entity.TestEntity;
import com.android.vts.entity.TestRunEntity;
import com.android.vts.proto.VtsReportMessage.VtsProfilingRegressionMode;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;

/** PerformanceUtil, a helper class for analyzing profiling and performance data. */
public class PerformanceUtil {
    protected static Logger logger = Logger.getLogger(PerformanceUtil.class.getName());

    private static final DecimalFormat FORMATTER;
    private static final String NAME_DELIMITER = ", ";

    /**
     * Initialize the decimal formatter.
     */
    static {
        FORMATTER = new DecimalFormat("#.##");
        FORMATTER.setRoundingMode(RoundingMode.HALF_UP);
    }

    public static class TimeInterval {
        public final long start;
        public final long end;
        public final String label;

        public TimeInterval(long start, long end, String label) {
            this.start = start;
            this.end = end;
            this.label = label;
        }

        public TimeInterval(long start, long end) {
            this(start, end, "<span class='date-label'>"
                            + Long.toString(TimeUnit.MICROSECONDS.toMillis(end)) + "</span>");
        }
    }

    /**
     * Creates the HTML for a table cell representing the percent change between two numbers.
     *
     * <p>Computes the percent change (after - before)/before * 100 and inserts it into a table cell
     * with the specified style. The color of the cell is white if 'after' is less than before.
     * Otherwise, the cell is colored red with opacity according to the percent change (100%+ delta
     * means 100% opacity). If the before value is 0 and the after value is positive, then the color
     * of the cell is 100% red to indicate an increase of undefined magnitude.
     *
     * @param baseline The baseline value observed.
     * @param test The value to compare against the baseline.
     * @param classNames A string containing HTML classes to apply to the table cell.
     * @param style A string containing additional CSS styles.
     * @returns An HTML string for a colored table cell containing the percent change.
     */
    public static String getPercentChangeHTML(double baseline, double test, String classNames,
            String style, VtsProfilingRegressionMode mode) {
        String pctChangeString = "0 %";
        double alpha = 0;
        double delta = test - baseline;
        if (baseline != 0) {
            double pctChange = delta / baseline;
            alpha = pctChange * 2;
            pctChangeString = FORMATTER.format(pctChange * 100) + " %";
        } else if (delta != 0) {
            // If the percent change is undefined, the cell will be solid red or white
            alpha = (int) Math.signum(delta); // get the sign of the delta (+1, 0, -1)
            pctChangeString = "";
        }
        if (mode == VtsProfilingRegressionMode.VTS_REGRESSION_MODE_DECREASING) {
            alpha = -alpha;
        }
        String color = "background-color: rgba(255, 0, 0, " + alpha + "); ";
        String html = "<td class='" + classNames + "' style='" + color + style + "'>";
        html += pctChangeString + "</td>";
        return html;
    }

    /**
     * Compares a test StatSummary to a baseline StatSummary using best-case performance.
     *
     * @param baseline The StatSummary object containing initial values to compare against
     * @param test The StatSummary object containing test values to be compared against the baseline
     * @param innerClasses Class names to apply to cells on the inside of the grid
     * @param outerClasses Class names to apply to cells on the outside of the grid
     * @param innerStyles CSS styles to apply to cells on the inside of the grid
     * @param outerStyles CSS styles to apply to cells on the outside of the grid
     * @return HTML string representing the performance of the test versus the baseline
     */
    public static String getBestCasePerformanceComparisonHTML(StatSummary baseline,
            StatSummary test, String innerClasses, String outerClasses, String innerStyles,
            String outerStyles) {
        if (test == null || baseline == null) {
            return "<td></td><td></td><td></td><td></td>";
        }
        String row = "";
        // Intensity of red color is a function of the relative (percent) change
        // in the new value compared to the previous day's. Intensity is a linear function
        // of percentage change, reaching a ceiling at 100% change (e.g. a doubling).
        row += getPercentChangeHTML(baseline.getBestCase(), test.getBestCase(), innerClasses,
                innerStyles, test.getRegressionMode());
        row += "<td class='" + innerClasses + "' style='" + innerStyles + "'>";
        row += FORMATTER.format(baseline.getBestCase());
        row += "<td class='" + innerClasses + "' style='" + innerStyles + "'>";
        row += FORMATTER.format(baseline.getMean());
        row += "<td class='" + outerClasses + "' style='" + outerStyles + "'>";
        row += FORMATTER.format(baseline.getStd()) + "</td>";
        return row;
    }

    /**
     * Compares a test StatSummary to a baseline StatSummary using average-case performance.
     *
     * @param baseline The StatSummary object containing initial values to compare against
     * @param test The StatSummary object containing test values to be compared against the baseline
     * @param innerClasses Class names to apply to cells on the inside of the grid
     * @param outerClasses Class names to apply to cells on the outside of the grid
     * @param innerStyles CSS styles to apply to cells on the inside of the grid
     * @param outerStyles CSS styles to apply to cells on the outside of the grid
     * @return HTML string representing the performance of the test versus the baseline
     */
    public static String getAvgCasePerformanceComparisonHTML(StatSummary baseline, StatSummary test,
            String innerClasses, String outerClasses, String innerStyles, String outerStyles) {
        if (test == null || baseline == null) {
            return "<td></td><td></td><td></td><td></td>";
        }
        String row = "";
        // Intensity of red color is a function of the relative (percent) change
        // in the new value compared to the previous day's. Intensity is a linear function
        // of percentage change, reaching a ceiling at 100% change (e.g. a doubling).
        row += getPercentChangeHTML(baseline.getMean(), test.getMean(), innerClasses, innerStyles,
                test.getRegressionMode());
        row += "<td class='" + innerClasses + "' style='" + innerStyles + "'>";
        row += FORMATTER.format(baseline.getBestCase());
        row += "<td class='" + innerClasses + "' style='" + innerStyles + "'>";
        row += FORMATTER.format(baseline.getMean());
        row += "<td class='" + outerClasses + "' style='" + outerStyles + "'>";
        row += FORMATTER.format(baseline.getStd()) + "</td>";
        return row;
    }

    /**
     * Updates a PerformanceSummary object with data in the specified window.
     *
     * @param testName The name of the table whose profiling vectors to retrieve.
     * @param startTime The (inclusive) start time in milliseconds to scan from.
     * @param endTime The (inclusive) end time in milliseconds at which to stop scanning.
     * @param selectedDevice The name of the device whose data to query for, or null for unfiltered.
     * @param perfSummary The PerformanceSummary object to update with data.
     * @throws IOException
     */
    public static void updatePerformanceSummary(String testName, long startTime, long endTime,
            String selectedDevice, PerformanceSummary perfSummary) throws IOException {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Key testKey = KeyFactory.createKey(TestEntity.KIND, testName);
        Filter testTypeFilter = FilterUtil.getTestTypeFilter(false, true, false);
        Filter runFilter = FilterUtil.getTimeFilter(testKey, startTime, endTime, testTypeFilter);

        Filter deviceFilter = null;
        if (selectedDevice != null) {
            deviceFilter = new FilterPredicate(
                    DeviceInfoEntity.PRODUCT, FilterOperator.EQUAL, selectedDevice);
        }
        Query testRunQuery = new Query(TestRunEntity.KIND)
                                     .setAncestor(testKey)
                                     .setFilter(runFilter)
                                     .setKeysOnly();
        for (Entity testRun : datastore.prepare(testRunQuery).asIterable()) {
            if (deviceFilter != null) {
                Query deviceQuery = new Query(DeviceInfoEntity.KIND)
                                            .setAncestor(testRun.getKey())
                                            .setFilter(deviceFilter)
                                            .setKeysOnly();
                if (!DatastoreHelper.hasEntities(deviceQuery))
                    continue;
            }
            Query q = new Query(ProfilingPointRunEntity.KIND).setAncestor(testRun.getKey());

            for (Entity profilingRun : datastore.prepare(q).asIterable()) {
                perfSummary.addData(profilingRun);
            }
        }
    }

    /**
     * Generates a string of the values in optionsList which have matches in the profiling entity.
     *
     * @param profilingRun The entity for a profiling point run.
     * @param optionKeys A list of keys to match against the optionsList key value pairs.
     * @return The values in optionsList whose key match a key in optionKeys.
     */
    public static String getOptionAlias(Entity profilingRun, Set<String> optionKeys) {
        String name = "";
        List<String> nameSuffixes = new ArrayList<String>();
        for (String key : optionKeys) {
            if (profilingRun.hasProperty(key)) {
                try {
                    nameSuffixes.add((String) profilingRun.getProperty(key));
                } catch (ClassCastException e) {
                    continue;
                }
            }
        }
        if (nameSuffixes.size() > 0) {
            StringUtils.join(nameSuffixes, NAME_DELIMITER);
            name += StringUtils.join(nameSuffixes, NAME_DELIMITER);
        }
        return name;
    }
}
