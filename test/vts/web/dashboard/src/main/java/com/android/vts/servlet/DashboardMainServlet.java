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
import com.android.vts.entity.UserFavoriteEntity;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.CompositeFilter;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Represents the servlet that is invoked on loading the first page of dashboard. */
public class DashboardMainServlet extends BaseServlet {
    private static final String DASHBOARD_MAIN_JSP = "WEB-INF/jsp/dashboard_main.jsp";
    private static final String DASHBOARD_ALL_LINK = "/?showAll=true";
    private static final String DASHBOARD_FAVORITES_LINK = "/";
    private static final String ALL_HEADER = "All Tests";
    private static final String FAVORITES_HEADER = "Favorites";
    private static final String NO_FAVORITES_ERROR =
            "No subscribed tests. Click the edit button to add to favorites.";
    private static final String NO_TESTS_ERROR = "No test results available.";
    private static final String FAVORITES_BUTTON = "Show Favorites";
    private static final String ALL_BUTTON = "Show All";
    private static final String UP_ARROW = "keyboard_arrow_up";
    private static final String DOWN_ARROW = "keyboard_arrow_down";

    @Override
    public List<String[]> getNavbarLinks(HttpServletRequest request) {
        List<String[]> links = new ArrayList<>();
        Page root = Page.HOME;
        String[] rootEntry = new String[] {CURRENT_PAGE, root.getName()};
        links.add(rootEntry);
        return links;
    }

    /** Helper class for displaying test entries on the main dashboard. */
    public class TestDisplay implements Comparable<TestDisplay> {
        private final Key testKey;
        private final int failCount;

        /**
         * Test display constructor.
         *
         * @param testKey The key of the test.
         * @param failCount The number of tests failing.
         */
        public TestDisplay(Key testKey, int failCount) {
            this.testKey = testKey;
            this.failCount = failCount;
        }

        /**
         * Get the key of the test.
         *
         * @return The key of the test.
         */
        public String getName() {
            return this.testKey.getName();
        }

        /**
         * Get the number of failing test cases.
         *
         * @return The number of failing test cases.
         */
        public int getFailCount() {
            return this.failCount;
        }

        @Override
        public int compareTo(TestDisplay test) {
            return this.testKey.getName().compareTo(test.getName());
        }
    }

    @Override
    public void doGetHandler(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        UserService userService = UserServiceFactory.getUserService();
        User currentUser = userService.getCurrentUser();
        RequestDispatcher dispatcher = null;
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        List<TestDisplay> displayedTests = new ArrayList<>();

        Map<Key, Integer> failCountMap = new HashMap<>(); // map from table name to fail count

        boolean showAll = request.getParameter("showAll") != null;
        String header;
        String buttonLabel;
        String buttonIcon;
        String buttonLink;
        String error = null;

        Query q = new Query(TestEntity.KIND)
                          .addProjection(new PropertyProjection(TestEntity.FAIL_COUNT, Long.class));
        for (Entity test : datastore.prepare(q).asIterable()) {
            TestEntity testEntity = TestEntity.fromEntity(test);
            if (test != null) {
                failCountMap.put(test.getKey(), testEntity.failCount);
            }
        }

        if (showAll) {
            for (Key testKey : failCountMap.keySet()) {
                TestDisplay test = new TestDisplay(testKey, failCountMap.get(testKey));
                displayedTests.add(test);
            }
            if (displayedTests.size() == 0) {
                error = NO_TESTS_ERROR;
            }
            header = ALL_HEADER;
            buttonLabel = FAVORITES_BUTTON;
            buttonIcon = UP_ARROW;
            buttonLink = DASHBOARD_FAVORITES_LINK;
        } else {
            if (failCountMap.size() > 0) {
                Filter userFilter = new FilterPredicate(
                        UserFavoriteEntity.USER, FilterOperator.EQUAL, currentUser);
                Filter propertyFilter = new FilterPredicate(
                        UserFavoriteEntity.TEST_KEY, FilterOperator.IN, failCountMap.keySet());
                CompositeFilter filter = CompositeFilterOperator.and(userFilter, propertyFilter);
                q = new Query(UserFavoriteEntity.KIND).setFilter(filter);

                for (Entity favorite : datastore.prepare(q).asIterable()) {
                    Key testKey = (Key) favorite.getProperty(UserFavoriteEntity.TEST_KEY);
                    TestDisplay test = new TestDisplay(testKey, failCountMap.get(testKey));
                    displayedTests.add(test);
                }
            }
            if (displayedTests.size() == 0) {
                error = NO_FAVORITES_ERROR;
            }
            header = FAVORITES_HEADER;
            buttonLabel = ALL_BUTTON;
            buttonIcon = DOWN_ARROW;
            buttonLink = DASHBOARD_ALL_LINK;
        }
        Collections.sort(displayedTests);

        response.setStatus(HttpServletResponse.SC_OK);
        request.setAttribute("testNames", displayedTests);
        request.setAttribute("headerLabel", header);
        request.setAttribute("showAll", showAll);
        request.setAttribute("buttonLabel", buttonLabel);
        request.setAttribute("buttonIcon", buttonIcon);
        request.setAttribute("buttonLink", buttonLink);
        request.setAttribute("error", error);
        dispatcher = request.getRequestDispatcher(DASHBOARD_MAIN_JSP);
        try {
            dispatcher.forward(request, response);
        } catch (ServletException e) {
            logger.log(Level.SEVERE, "Servlet Excpetion caught : ", e);
        }
    }
}
