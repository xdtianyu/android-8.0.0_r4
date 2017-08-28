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

import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class BaseServlet extends HttpServlet {
    protected final Logger logger = Logger.getLogger(getClass().getName());

    // Environment variables
    protected static final String GERRIT_URI = System.getProperty("GERRIT_URI");
    protected static final String GERRIT_SCOPE = System.getProperty("GERRIT_SCOPE");
    protected static final String CLIENT_ID = System.getProperty("CLIENT_ID");
    protected static final String ANALYTICS_ID = System.getProperty("ANALYTICS_ID");

    // Common constants
    protected static final long ONE_DAY = 86400000000L; // units microseconds
    protected static final long MILLI_TO_MICRO =
            1000; // conversion factor from milli to micro units
    protected static final String TABLE_PREFIX = "result_";
    protected static final String CURRENT_PAGE = "#";

    public enum Page {
        HOME("VTS Dashboard Home", "/"),
        PREFERENCES("Preferences", "/show_preferences"),
        TABLE("", "/show_table"),
        GRAPH("Profiling", "/show_graph"),
        COVERAGE("Coverage", "/show_coverage"),
        PERFORMANCE("Performance Digest", "/show_performance_digest");

        private final String name;
        private final String url;

        Page(String name, String url) {
            this.name = name;
            this.url = url;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }
    }

    /**
     * Get a list of URL/Display name pairs for the navbar heirarchy.
     *
     * @param request The HttpServletRequest object for the page request.
     * @return a list of 2-entried String arrays in the order [page url, page name]
     */
    public abstract List<String[]> getNavbarLinks(HttpServletRequest request);

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // If the user is logged out, allow them to log back in and return to the page.
        // Set the logout URL to direct back to a login page that directs to the current request.
        UserService userService = UserServiceFactory.getUserService();
        User currentUser = userService.getCurrentUser();
        String requestUri = request.getRequestURI();
        String requestArgs = request.getQueryString();
        String loginURI = userService.createLoginURL(requestUri + '?' + requestArgs);
        String logoutURI = userService.createLogoutURL(loginURI);
        if (currentUser == null || currentUser.getEmail() == null) {
            response.sendRedirect(loginURI);
            return;
        }
        request.setAttribute("logoutURL", logoutURI);
        request.setAttribute("email", currentUser.getEmail());
        request.setAttribute("analyticsID", new Gson().toJson(ANALYTICS_ID));
        request.setAttribute("navbarLinksJson", new Gson().toJson(getNavbarLinks(request)));
        request.setAttribute("navbarLinks", getNavbarLinks(request));
        response.setContentType("text/html");
        doGetHandler(request, response);
    }

    /**
     * Implementation of the doGet method to be executed by servlet subclasses.
     *
     * @param request The HttpServletRequest object.
     * @param response The HttpServletResponse object.
     * @throws IOException
     */
    public abstract void doGetHandler(HttpServletRequest request, HttpServletResponse response)
            throws IOException;
}
