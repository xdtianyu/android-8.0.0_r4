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
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;

/** Represents the servlet that is invoked on loading the preferences page to manage favorites. */
public class ShowPreferencesServlet extends BaseServlet {
    private static final String PREFERENCES_JSP = "WEB-INF/jsp/show_preferences.jsp";
    private static final String DASHBOARD_MAIN_LINK = "/";

    /** Helper class for displaying test subscriptions. */
    public class Subscription {
        private final String testName;
        private final String key;

        /**
         * Test display constructor.
         *
         * @param testName The name of the test.
         * @param key The websafe string serialization of the subscription key.
         */
        public Subscription(String testName, String key) {
            this.testName = testName;
            this.key = key;
        }

        /**
         * Get the name of the test.
         *
         * @return The name of the test.
         */
        public String getTestName() {
            return this.testName;
        }

        /**
         * Get the subscription key.
         *
         * @return The subscription key.
         */
        public String getKey() {
            return this.key;
        }
    }

    @Override
    public List<String[]> getNavbarLinks(HttpServletRequest request) {
        List<String[]> links = new ArrayList<>();
        Page root = Page.HOME;
        String[] rootEntry = new String[] {root.getUrl(), root.getName()};
        links.add(rootEntry);

        Page prefs = Page.PREFERENCES;
        String[] prefsEntry = new String[] {CURRENT_PAGE, prefs.getName()};
        links.add(prefsEntry);
        return links;
    }

    @Override
    public void doGetHandler(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        // Get the user's information
        UserService userService = UserServiceFactory.getUserService();
        User currentUser = userService.getCurrentUser();
        RequestDispatcher dispatcher = null;
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        List<Subscription> subscriptions = new ArrayList<>();

        // Map from TestEntity key to the UserFavoriteEntity object
        Map<Key, Entity> subscriptionEntityMap = new HashMap<>();

        // Map from test name to the subscription object
        Map<String, Subscription> subscriptionMap = new HashMap<>();

        // Query for the favorites entities matching the current user
        Filter propertyFilter =
                new FilterPredicate(UserFavoriteEntity.USER, FilterOperator.EQUAL, currentUser);
        Query q = new Query(UserFavoriteEntity.KIND).setFilter(propertyFilter);
        for (Entity favorite : datastore.prepare(q).asIterable()) {
            UserFavoriteEntity favoriteEntity = UserFavoriteEntity.fromEntity(favorite);
            if (favoriteEntity == null) {
                continue;
            }
            subscriptionEntityMap.put(favoriteEntity.testKey, favorite);
        }
        if (subscriptionEntityMap.size() > 0) {
            // Query for the tests specified by the user favorite entities
            propertyFilter = new FilterPredicate(Entity.KEY_RESERVED_PROPERTY, FilterOperator.IN,
                    subscriptionEntityMap.keySet());
            q = new Query(TestEntity.KIND).setFilter(propertyFilter).setKeysOnly();
            for (Entity test : datastore.prepare(q).asIterable()) {
                String testName = test.getKey().getName();
                Entity subscriptionEntity = subscriptionEntityMap.get(test.getKey());
                Subscription sub = new Subscription(
                        testName, KeyFactory.keyToString(subscriptionEntity.getKey()));
                subscriptions.add(sub);
                subscriptionMap.put(testName, sub);
            }
        }
        List<String> allTests = new ArrayList<>();
        for (Entity result : datastore.prepare(new Query(TestEntity.KIND)).asIterable()) {
            allTests.add(result.getKey().getName());
        }

        request.setAttribute("allTestsJson", new Gson().toJson(allTests));
        request.setAttribute("subscriptions", subscriptions);
        request.setAttribute("subscriptionMapJson", new Gson().toJson(subscriptionMap));
        request.setAttribute("subscriptionsJson", new Gson().toJson(subscriptions));

        dispatcher = request.getRequestDispatcher(PREFERENCES_JSP);
        try {
            dispatcher.forward(request, response);
        } catch (ServletException e) {
            logger.log(Level.SEVERE, "Servlet Exception caught : ", e);
        }
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        UserService userService = UserServiceFactory.getUserService();
        User currentUser = userService.getCurrentUser();
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        // Retrieve the added tests from the request.
        String addedTestsString = request.getParameter("addedTests");
        List<Key> addedTests = new ArrayList<>();
        if (!StringUtils.isBlank(addedTestsString)) {
            for (String test : addedTestsString.trim().split(",")) {
                addedTests.add(KeyFactory.createKey(TestEntity.KIND, test));
            }
        }
        if (addedTests.size() > 0) {
            // Filter the tests that exist from the set of tests to add
            Filter propertyFilter = new FilterPredicate(
                    Entity.KEY_RESERVED_PROPERTY, FilterOperator.IN, addedTests);
            Query q = new Query(TestEntity.KIND).setFilter(propertyFilter).setKeysOnly();
            List<Entity> newSubscriptions = new ArrayList<>();

            // Create subscription entities
            for (Entity test : datastore.prepare(q).asIterable()) {
                UserFavoriteEntity favorite = new UserFavoriteEntity(currentUser, test.getKey());
                newSubscriptions.add(favorite.toEntity());
            }
            datastore.put(newSubscriptions);
        }

        // Retrieve the removed tests from the request.
        String removedSubscriptionString = request.getParameter("removedKeys");
        if (!StringUtils.isBlank(removedSubscriptionString)) {
            for (String stringKey : removedSubscriptionString.trim().split(",")) {
                try {
                    datastore.delete(KeyFactory.stringToKey(stringKey));
                } catch (IllegalArgumentException e) {
                    continue;
                }
            }
        }
        response.sendRedirect(DASHBOARD_MAIN_LINK);
    }
}
