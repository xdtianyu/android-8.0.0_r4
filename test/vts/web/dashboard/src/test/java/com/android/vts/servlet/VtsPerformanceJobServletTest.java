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

package com.android.vts.servlet;

import static org.junit.Assert.*;

import com.android.vts.entity.ProfilingPointRunEntity;
import com.android.vts.entity.TestEntity;
import com.android.vts.proto.VtsReportMessage.VtsProfilingRegressionMode;
import com.android.vts.util.PerformanceSummary;
import com.android.vts.util.ProfilingPointSummary;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class VtsPerformanceJobServletTest {
    private final LocalServiceTestHelper helper =
            new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());

    private static final String LABEL = "testLabel";
    private static final String ROOT = "src/test/res/servlet";
    private static final String[] LABELS = new String[] {"label1", "label2", "label3"};
    private static final long[] HIGH_VALS = new long[] {10, 20, 30};
    private static final long[] LOW_VALS = new long[] {1, 2, 3};

    List<PerformanceSummary> dailySummaries;
    List<String> legendLabels;

    /**
     * Helper method for creating ProfilingPointRunEntity objects.
     *
     * @param labels The list of data labels.
     * @param values The list of data values. Must be equal in size to the labels list.
     * @param regressionMode The regression mode.
     * @return A ProfilingPointRunEntity with specified arguments.
     */
    private static ProfilingPointRunEntity createProfilingReport(
            String[] labels, long[] values, VtsProfilingRegressionMode regressionMode) {
        List<String> labelList = Arrays.asList(labels);
        List<Long> valueList = new ArrayList<>();
        for (long value : values) {
            valueList.add(value);
        }
        return new ProfilingPointRunEntity(KeyFactory.createKey(TestEntity.KIND, "test"), "name", 0,
                regressionMode.getNumber(), labelList, valueList, "", "");
    }

    /** Asserts whether text is the same as the contents in the baseline file specified. */
    private static void compareToBaseline(String text, String baselineFilename)
            throws FileNotFoundException, IOException {
        File f = new File(ROOT, baselineFilename);
        String baseline = "";
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                line = br.readLine();
            }
            baseline = sb.toString();
        }
        assertEquals(baseline, text);
    }

    @Before
    public void setUp() {
        helper.setUp();
    }

    @After
    public void tearDown() {
        helper.tearDown();
    }

    public void setUp(boolean grouped) {
        dailySummaries = new ArrayList<>();
        legendLabels = new ArrayList<>();
        legendLabels.add("");

        // Add today's data
        PerformanceSummary today = new PerformanceSummary();
        ProfilingPointSummary summary = new ProfilingPointSummary();
        VtsProfilingRegressionMode mode = VtsProfilingRegressionMode.VTS_REGRESSION_MODE_INCREASING;
        ProfilingPointRunEntity pt = createProfilingReport(LABELS, HIGH_VALS, mode);
        if (grouped) {
            summary.updateLabel(pt, LABEL);
            summary.updateLabel(pt, LABEL);
        } else {
            summary.update(pt);
            summary.update(pt);
        }
        today.insertProfilingPointSummary("p1", summary);

        summary = new ProfilingPointSummary();
        mode = VtsProfilingRegressionMode.VTS_REGRESSION_MODE_DECREASING;
        pt = createProfilingReport(LABELS, LOW_VALS, mode);
        if (grouped) {
            summary.updateLabel(pt, LABEL);
            summary.updateLabel(pt, LABEL);
        } else {
            summary.update(pt);
            summary.update(pt);
        }
        today.insertProfilingPointSummary("p2", summary);
        dailySummaries.add(today);
        legendLabels.add("today");

        // Add yesterday data with regressions
        PerformanceSummary yesterday = new PerformanceSummary();
        summary = new ProfilingPointSummary();
        mode = VtsProfilingRegressionMode.VTS_REGRESSION_MODE_INCREASING;
        pt = createProfilingReport(LABELS, LOW_VALS, mode);
        if (grouped) {
            summary.updateLabel(pt, LABEL);
            summary.updateLabel(pt, LABEL);
        } else {
            summary.update(pt);
            summary.update(pt);
        }
        yesterday.insertProfilingPointSummary("p1", summary);

        summary = new ProfilingPointSummary();
        mode = VtsProfilingRegressionMode.VTS_REGRESSION_MODE_DECREASING;
        pt = createProfilingReport(LABELS, HIGH_VALS, mode);
        if (grouped) {
            summary.updateLabel(pt, LABEL);
            summary.updateLabel(pt, LABEL);
        } else {
            summary.update(pt);
            summary.update(pt);
        }
        yesterday.insertProfilingPointSummary("p2", summary);
        dailySummaries.add(yesterday);
        legendLabels.add("yesterday");

        // Add last week data without regressions
        PerformanceSummary lastWeek = new PerformanceSummary();
        summary = new ProfilingPointSummary();
        mode = VtsProfilingRegressionMode.VTS_REGRESSION_MODE_INCREASING;
        pt = createProfilingReport(LABELS, HIGH_VALS, mode);
        summary.update(pt);
        summary.update(pt);
        lastWeek.insertProfilingPointSummary("p1", summary);

        summary = new ProfilingPointSummary();
        mode = VtsProfilingRegressionMode.VTS_REGRESSION_MODE_DECREASING;
        pt = createProfilingReport(LABELS, LOW_VALS, mode);
        summary.update(pt);
        summary.update(pt);
        lastWeek.insertProfilingPointSummary("p2", summary);
        dailySummaries.add(lastWeek);
        legendLabels.add("last week");
    }

    /**
     * End-to-end test of performance report in the normal case. The normal case is when a profiling
     * point is added or removed from the test.
     */
    @Test
    public void testPerformanceSummaryNormal() throws FileNotFoundException, IOException {
        setUp(false);
        String output =
                VtsPerformanceJobServlet.getPeformanceSummary("test", dailySummaries, legendLabels);
        compareToBaseline(output, "performanceSummary1.html");
    }

    /** End-to-end test of performance report when a profiling point was removed in the latest run.
     */
    @Test
    public void testPerformanceSummaryDroppedProfilingPoint()
            throws FileNotFoundException, IOException {
        setUp(false);
        PerformanceSummary yesterday = dailySummaries.get(dailySummaries.size() - 1);
        ProfilingPointSummary summary = new ProfilingPointSummary();
        VtsProfilingRegressionMode mode = VtsProfilingRegressionMode.VTS_REGRESSION_MODE_INCREASING;
        ProfilingPointRunEntity pt = createProfilingReport(LABELS, HIGH_VALS, mode);
        summary.update(pt);
        summary.update(pt);
        yesterday.insertProfilingPointSummary("p3", summary);
        String output =
                VtsPerformanceJobServlet.getPeformanceSummary("test", dailySummaries, legendLabels);
        compareToBaseline(output, "performanceSummary2.html");
    }

    /** End-to-end test of performance report when a profiling point was added in the latest run. */
    @Test
    public void testPerformanceSummaryAddedProfilingPoint()
            throws FileNotFoundException, IOException {
        setUp(false);
        PerformanceSummary today = dailySummaries.get(0);
        ProfilingPointSummary summary = new ProfilingPointSummary();
        VtsProfilingRegressionMode mode = VtsProfilingRegressionMode.VTS_REGRESSION_MODE_INCREASING;
        ProfilingPointRunEntity pt = createProfilingReport(LABELS, HIGH_VALS, mode);
        summary.update(pt);
        summary.update(pt);
        today.insertProfilingPointSummary("p3", summary);
        String output =
                VtsPerformanceJobServlet.getPeformanceSummary("test", dailySummaries, legendLabels);
        compareToBaseline(output, "performanceSummary3.html");
    }

    /** End-to-end test of performance report labels are grouped (e.g. as if using unlabeled data)
     */
    @Test
    public void testPerformanceSummaryGroupedNormal() throws FileNotFoundException, IOException {
        setUp(true);
        String output =
                VtsPerformanceJobServlet.getPeformanceSummary("test", dailySummaries, legendLabels);
        compareToBaseline(output, "performanceSummary4.html");
    }
}
