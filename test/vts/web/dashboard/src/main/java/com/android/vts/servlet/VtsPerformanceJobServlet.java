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

import com.android.vts.entity.TestEntity;
import com.android.vts.util.EmailHelper;
import com.android.vts.util.PerformanceSummary;
import com.android.vts.util.PerformanceUtil;
import com.android.vts.util.PerformanceUtil.TimeInterval;
import com.android.vts.util.ProfilingPointSummary;
import com.android.vts.util.StatSummary;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.Query;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Represents the notifications service which is automatically called on a fixed schedule. */
public class VtsPerformanceJobServlet extends BaseServlet {
    private static final String MEAN = "Mean";
    private static final String MAX = "Max";
    private static final String MIN = "Min";
    private static final String MIN_DELTA = "&Delta;Min (%)";
    private static final String MAX_DELTA = "&Delta;Max (%)";
    private static final String HIGHER_IS_BETTER =
            "Note: Higher values are better. Maximum is the best-case performance.";
    private static final String LOWER_IS_BETTER =
            "Note: Lower values are better. Minimum is the best-case performance.";
    private static final String STD = "Std";
    private static final String SUBJECT_PREFIX = "Daily Performance Digest: ";
    private static final String LAST_WEEK = "Last Week";
    private static final String LABEL_STYLE = "font-family: arial";
    private static final String SUBTEXT_STYLE = "font-family: arial; font-size: 12px";
    private static final String TABLE_STYLE =
            "width: 100%; border-collapse: collapse; border: 1px solid black; font-size: 12px; font-family: arial;";
    private static final String SECTION_LABEL_STYLE =
            "border: 1px solid black; border-bottom: none; background-color: lightgray;";
    private static final String COL_LABEL_STYLE =
            "border: 1px solid black; border-bottom-width: 2px; border-top: 1px dotted gray; background-color: lightgray;";
    private static final String HEADER_COL_STYLE =
            "border-top: 1px dotted gray; border-right: 2px solid black; text-align: right; background-color: lightgray;";
    private static final String INNER_CELL_STYLE =
            "border-top: 1px dotted gray; border-right: 1px dotted gray; text-align: right;";
    private static final String OUTER_CELL_STYLE =
            "border-top: 1px dotted gray; border-right: 2px solid black; text-align: right;";

    private static final DecimalFormat FORMATTER;

    /**
     * Initialize the decimal formatter.
     */
    static {
        FORMATTER = new DecimalFormat("#.##");
        FORMATTER.setRoundingMode(RoundingMode.HALF_UP);
    }

    @Override
    public List<String[]> getNavbarLinks(HttpServletRequest request) {
        return null;
    }

    /**
     * Generates an HTML summary of the performance changes for the profiling results in the
     * specified
     * table.
     *
     * <p>Retrieves the past 24 hours of profiling data and compares it to the 24 hours that
     * preceded
     * it. Creates a table representation of the mean and standard deviation for each profiling
     * point.
     * When performance degrades, the cell is shaded red.
     *
     * @param testName The name of the test whose profiling data to summarize.
     * @param perfSummaries List of PerformanceSummary objects for each profiling run (in reverse
     *     chronological order).
     * @param labels List of string labels for use as the column headers.
     * @returns An HTML string containing labeled table summaries.
     */
    public static String getPeformanceSummary(
            String testName, List<PerformanceSummary> perfSummaries, List<String> labels) {
        if (perfSummaries.size() == 0)
            return "";
        PerformanceSummary now = perfSummaries.get(0);
        String tableHTML = "<p style='" + LABEL_STYLE + "'><b>";
        tableHTML += testName + "</b></p>";
        for (String profilingPoint : now.getProfilingPointNames()) {
            ProfilingPointSummary summary = now.getProfilingPointSummary(profilingPoint);
            tableHTML += "<table cellpadding='2' style='" + TABLE_STYLE + "'>";

            // Format header rows
            String[] headerRows = new String[] {profilingPoint, summary.yLabel};
            int colspan = labels.size() * 4;
            for (String content : headerRows) {
                tableHTML += "<tr><td colspan='" + colspan + "'>" + content + "</td></tr>";
            }

            // Format section labels
            tableHTML += "<tr>";
            for (int i = 0; i < labels.size(); i++) {
                String content = labels.get(i);
                tableHTML += "<th style='" + SECTION_LABEL_STYLE + "' ";
                if (i == 0)
                    tableHTML += "colspan='1'";
                else if (i == 1)
                    tableHTML += "colspan='3'";
                else
                    tableHTML += "colspan='4'";
                tableHTML += ">" + content + "</th>";
            }
            tableHTML += "</tr>";

            String deltaString;
            String bestCaseString;
            String subtext;
            switch (now.getProfilingPointSummary(profilingPoint).getRegressionMode()) {
                case VTS_REGRESSION_MODE_DECREASING:
                    deltaString = MAX_DELTA;
                    bestCaseString = MAX;
                    subtext = HIGHER_IS_BETTER;
                    break;
                default:
                    deltaString = MIN_DELTA;
                    bestCaseString = MIN;
                    subtext = LOWER_IS_BETTER;
                    break;
            }

            // Format column labels
            tableHTML += "<tr>";
            for (int i = 0; i < labels.size(); i++) {
                if (i > 1) {
                    tableHTML += "<th style='" + COL_LABEL_STYLE + "'>" + deltaString + "</th>";
                }
                if (i == 0) {
                    tableHTML += "<th style='" + COL_LABEL_STYLE + "'>";
                    tableHTML += summary.xLabel + "</th>";
                } else if (i > 0) {
                    tableHTML += "<th style='" + COL_LABEL_STYLE + "'>" + bestCaseString + "</th>";
                    tableHTML += "<th style='" + COL_LABEL_STYLE + "'>" + MEAN + "</th>";
                    tableHTML += "<th style='" + COL_LABEL_STYLE + "'>" + STD + "</th>";
                }
            }
            tableHTML += "</tr>";

            // Populate data cells
            for (StatSummary stats : summary) {
                String label = stats.getLabel();
                tableHTML += "<tr><td style='" + HEADER_COL_STYLE + "'>" + label;
                tableHTML += "</td><td style='" + INNER_CELL_STYLE + "'>";
                tableHTML += FORMATTER.format(stats.getBestCase()) + "</td>";
                tableHTML += "<td style='" + INNER_CELL_STYLE + "'>";
                tableHTML +=  FORMATTER.format(stats.getMean()) + "</td>";
                tableHTML += "<td style='" + OUTER_CELL_STYLE + "'>";
                tableHTML +=  FORMATTER.format(stats.getStd()) + "</td>";
                for (int i = 1; i < perfSummaries.size(); i++) {
                    PerformanceSummary oldPerfSummary = perfSummaries.get(i);
                    if (oldPerfSummary.hasProfilingPoint(profilingPoint)) {
                        StatSummary baseline =
                                oldPerfSummary.getProfilingPointSummary(profilingPoint)
                                        .getStatSummary(label);
                        tableHTML += PerformanceUtil.getBestCasePerformanceComparisonHTML(
                                baseline, stats, "", "", INNER_CELL_STYLE, OUTER_CELL_STYLE);
                    } else
                        tableHTML += "<td></td><td></td><td></td><td></td>";
                }
                tableHTML += "</tr>";
            }
            tableHTML += "</table>";
            tableHTML += "<i style='" + SUBTEXT_STYLE + "'>" + subtext + "</i><br><br>";
        }
        return tableHTML;
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doGetHandler(request, response);
    }

    @Override
    public void doGetHandler(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Set<Key> allTestKeys = new HashSet<>();

        Query q = new Query(TestEntity.KIND).setKeysOnly();
        for (Entity test : datastore.prepare(q).asIterable()) {
            if (test.getKey().getName() == null) {
                continue;
            }
            allTestKeys.add(test.getKey());
        }

        // Add today to the list of time intervals to analyze
        List<TimeInterval> timeIntervals = new ArrayList<>();
        long now = System.currentTimeMillis();
        String dateString = new SimpleDateFormat("MM-dd-yyyy").format(new Date(now));
        TimeInterval today = new TimeInterval(now - ONE_DAY / MILLI_TO_MICRO, now, dateString);
        timeIntervals.add(today);

        // Add yesterday as a baseline time interval for analysis
        long oneDayAgo = now - ONE_DAY / MILLI_TO_MICRO;
        String dateStringYesterday = new SimpleDateFormat("MM-dd-yyyy").format(new Date(oneDayAgo));
        TimeInterval yesterday = new TimeInterval(
                oneDayAgo - ONE_DAY / MILLI_TO_MICRO, oneDayAgo, dateStringYesterday);
        timeIntervals.add(yesterday);

        // Add last week as a baseline time interval for analysis
        long oneWeek = 7 * ONE_DAY / MILLI_TO_MICRO;
        long oneWeekAgo = now - oneWeek;
        TimeInterval lastWeek = new TimeInterval(oneWeekAgo - oneWeek, oneWeekAgo, LAST_WEEK);
        timeIntervals.add(lastWeek);

        for (Key testKey : allTestKeys) {
            List<PerformanceSummary> perfSummaries = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            labels.add("");
            for (TimeInterval interval : timeIntervals) {
                PerformanceSummary perfSummary = new PerformanceSummary();
                PerformanceUtil.updatePerformanceSummary(
                        testKey.getName(), interval.start, interval.end, null, perfSummary);
                if (perfSummary.size() == 0) {
                    continue;
                }
                perfSummaries.add(perfSummary);
                labels.add(interval.label);
            }
            String body = getPeformanceSummary(testKey.getName(), perfSummaries, labels);
            if (body == null || body.equals("")) {
                continue;
            }
            List<String> emails = EmailHelper.getSubscriberEmails(testKey);
            if (emails.size() == 0) {
                continue;
            }
            String subject = SUBJECT_PREFIX + testKey.getName();
            EmailHelper.send(emails, subject, body);
        }
    }
}
