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

import com.android.vts.entity.DeviceInfoEntity;
import com.android.vts.entity.ProfilingPointRunEntity;
import com.android.vts.entity.TestCaseRunEntity;
import com.android.vts.entity.TestEntity;
import com.android.vts.entity.TestRunEntity;
import com.android.vts.proto.VtsReportMessage.TestCaseResult;
import com.android.vts.util.DatastoreHelper;
import com.android.vts.util.FilterUtil;
import com.android.vts.util.TestResults;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;

/** Servlet for handling requests to load individual tables. */
public class ShowTableServlet extends BaseServlet {
    private static final String TABLE_JSP = "WEB-INF/jsp/show_table.jsp";
    // Error message displayed on the webpage is tableName passed is null.
    private static final String TABLE_NAME_ERROR = "Error : Table name must be passed!";
    private static final String PROFILING_DATA_ALERT = "No profiling data was found.";
    private static final int MAX_BUILD_IDS_PER_PAGE = 10;

    private static final String SEARCH_HELP_HEADER = "Search Help";
    private static final String SEARCH_HELP = "Data can be queried using one or more filters. "
            + "If more than one filter is provided, results will be returned that match <i>all</i>. "
            + "<br><br>Filters are delimited by spaces; to specify a multi-word token, enclose it in "
            + "double quotes. A query must be in the format: \"field:value\".<br><br>"
            + "<b>Supported field qualifiers:</b> "
            + StringUtils.join(FilterUtil.FilterKey.values(), ", ") + ".";

    @Override
    public List<String[]> getNavbarLinks(HttpServletRequest request) {
        List<String[]> links = new ArrayList<>();
        Page root = Page.HOME;
        String[] rootEntry = new String[] {root.getUrl(), root.getName()};
        links.add(rootEntry);

        Page table = Page.TABLE;
        String testName = request.getParameter("testName");
        String name = table.getName() + testName;
        String url = table.getUrl() + "?testName=" + testName;
        String[] tableEntry = new String[] {url, name};
        links.add(tableEntry);
        return links;
    }

    public static void processTestRun(TestResults testResults, Entity testRun) {
        TestRunEntity testRunEntity = TestRunEntity.fromEntity(testRun);
        if (testRunEntity == null) {
            return;
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        List<Entity> testCases = new ArrayList<>();
        for (long testCaseId : testRunEntity.testCaseIds) {
            try {
                Entity testCaseRun =
                        datastore.get(KeyFactory.createKey(TestCaseRunEntity.KIND, testCaseId));
                testCases.add(testCaseRun);
            } catch (EntityNotFoundException e) {
                // not found
            }
        }

        Query deviceInfoQuery = new Query(DeviceInfoEntity.KIND).setAncestor(testRun.getKey());
        Iterable<Entity> deviceInfos = datastore.prepare(deviceInfoQuery).asIterable();

        Query profilingPointQuery =
                new Query(ProfilingPointRunEntity.KIND).setAncestor(testRun.getKey()).setKeysOnly();
        Iterable<Entity> profilingPoints = datastore.prepare(profilingPointQuery).asIterable();

        testResults.addTestRun(testRun, testCases, deviceInfos, profilingPoints);
    }

    @Override
    public void doGetHandler(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        boolean unfiltered = request.getParameter("unfiltered") != null;
        boolean showPresubmit = request.getParameter("showPresubmit") != null;
        boolean showPostsubmit = request.getParameter("showPostsubmit") != null;
        String searchString = request.getParameter("search");
        Long startTime = null; // time in microseconds
        Long endTime = null; // time in microseconds
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        RequestDispatcher dispatcher = null;

        // message to display if profiling point data is not available
        String profilingDataAlert = "";

        if (request.getParameter("testName") == null) {
            request.setAttribute("testName", TABLE_NAME_ERROR);
            return;
        }
        String testName = request.getParameter("testName");

        if (request.getParameter("startTime") != null) {
            String time = request.getParameter("startTime");
            try {
                startTime = Long.parseLong(time);
                startTime = startTime > 0 ? startTime : null;
            } catch (NumberFormatException e) {
                startTime = null;
            }
        }
        if (request.getParameter("endTime") != null) {
            String time = request.getParameter("endTime");
            try {
                endTime = Long.parseLong(time);
                endTime = endTime > 0 ? endTime : null;
            } catch (NumberFormatException e) {
                endTime = null;
            }
        }

        // If no params are specified, set to default of postsubmit-only.
        if (!(showPresubmit || showPostsubmit)) {
            showPostsubmit = true;
        }

        // If unfiltered, set showPre- and Post-submit to true for accurate UI.
        if (unfiltered) {
            showPostsubmit = true;
            showPresubmit = true;
        }

        // Add result names to list
        List<String> resultNames = new ArrayList<>();
        for (TestCaseResult r : TestCaseResult.values()) {
            resultNames.add(r.name());
        }

        TestResults testResults = new TestResults(testName);

        SortDirection dir = SortDirection.DESCENDING;
        if (startTime != null && endTime == null) {
            dir = SortDirection.ASCENDING;
        }
        Key testKey = KeyFactory.createKey(TestEntity.KIND, testName);
        Filter deviceFilter = FilterUtil.getDeviceFilter(searchString);

        Filter runTypeFilter =
                FilterUtil.getTestTypeFilter(showPresubmit, showPostsubmit, unfiltered);
        Filter testRunFilter = FilterUtil.getUserTestFilter(searchString, runTypeFilter);

        Filter filter = FilterUtil.getTimeFilter(testKey, startTime, endTime, testRunFilter);
        Query testRunQuery = new Query(TestRunEntity.KIND)
                                     .setAncestor(testKey)
                                     .setFilter(filter)
                                     .addSort(Entity.KEY_RESERVED_PROPERTY, dir);
        if (deviceFilter != null) {
            testRunQuery.setKeysOnly();
            for (Entity testRunKey : datastore.prepare(testRunQuery).asIterable()) {
                Query deviceQuery = new Query(DeviceInfoEntity.KIND)
                                            .setAncestor(testRunKey.getKey())
                                            .setFilter(deviceFilter)
                                            .setKeysOnly();
                if (!DatastoreHelper.hasEntities(deviceQuery)) {
                    // No devices matching device filter.
                    continue;
                }
                Entity testRun;
                try {
                    testRun = datastore.get(testRunKey.getKey());
                    processTestRun(testResults, testRun);
                } catch (EntityNotFoundException e) {
                    logger.log(Level.INFO, "Key not found.", e);
                }
                if (testResults.getSize() == MAX_BUILD_IDS_PER_PAGE) {
                    break;
                }
            }
        } else {
            for (Entity testRun :
                    datastore.prepare(testRunQuery)
                            .asIterable(FetchOptions.Builder.withLimit(MAX_BUILD_IDS_PER_PAGE))) {
                processTestRun(testResults, testRun);
            }
        }
        testResults.processReport();

        if (testResults.profilingPointNames.length == 0) {
            profilingDataAlert = PROFILING_DATA_ALERT;
        }

        request.setAttribute("testName", request.getParameter("testName"));

        request.setAttribute("error", profilingDataAlert);
        request.setAttribute("searchString", searchString);
        request.setAttribute("searchHelpHeader", SEARCH_HELP_HEADER);
        request.setAttribute("searchHelpBody", SEARCH_HELP);

        // pass values by converting to JSON
        request.setAttribute("headerRow", new Gson().toJson(testResults.headerRow));
        request.setAttribute("timeGrid", new Gson().toJson(testResults.timeGrid));
        request.setAttribute("durationGrid", new Gson().toJson(testResults.durationGrid));
        request.setAttribute("summaryGrid", new Gson().toJson(testResults.summaryGrid));
        request.setAttribute("resultsGrid", new Gson().toJson(testResults.resultsGrid));
        request.setAttribute("profilingPointNames", testResults.profilingPointNames);
        request.setAttribute("resultNames", resultNames);
        request.setAttribute("resultNamesJson", new Gson().toJson(resultNames));
        request.setAttribute("logInfoMap", new Gson().toJson(testResults.logInfoMap));

        // data for pie chart
        request.setAttribute(
                "topBuildResultCounts", new Gson().toJson(testResults.totResultCounts));
        request.setAttribute("topBuildId", testResults.totBuildId);
        request.setAttribute("startTime", new Gson().toJson(testResults.startTime));
        request.setAttribute("endTime", new Gson().toJson(testResults.endTime));
        request.setAttribute("hasNewer",
                new Gson().toJson(DatastoreHelper.hasNewer(testName, testResults.endTime)));
        request.setAttribute("hasOlder",
                new Gson().toJson(DatastoreHelper.hasOlder(testName, testResults.startTime)));
        request.setAttribute("unfiltered", unfiltered);
        request.setAttribute("showPresubmit", showPresubmit);
        request.setAttribute("showPostsubmit", showPostsubmit);

        dispatcher = request.getRequestDispatcher(TABLE_JSP);
        try {
            dispatcher.forward(request, response);
        } catch (ServletException e) {
            logger.log(Level.SEVERE, "Servlet Exception caught : " + e.toString());
        }
    }
}
