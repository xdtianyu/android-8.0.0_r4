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
import com.android.vts.entity.TestRunEntity;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** FilterUtil, a helper class for parsing and matching search queries to data. */
public class FilterUtil {
    private static final String TERM_DELIMITER = ":";
    private static final String SEARCH_REGEX = "([^\"]\\S*|\".+?\")\\s*";

    /** Key class to represent a filter token. */
    public static enum FilterKey {
        DEVICE_BUILD_ID("devicebuildid", DeviceInfoEntity.BUILD_ID),
        BRANCH("branch", DeviceInfoEntity.BRANCH),
        TARGET("target", DeviceInfoEntity.BUILD_FLAVOR),
        DEVICE("device", DeviceInfoEntity.PRODUCT),
        VTS_BUILD_ID("vtsbuildid", TestRunEntity.TEST_BUILD_ID),
        HOSTNAME("hostname", TestRunEntity.HOST_NAME),
        PASSING("passing", TestRunEntity.PASS_COUNT),
        NONPASSING("nonpassing", TestRunEntity.FAIL_COUNT);

        private static final Map<String, FilterKey> keyMap;

        static {
            keyMap = new HashMap<>();
            for (FilterKey k : EnumSet.allOf(FilterKey.class)) {
                keyMap.put(k.toString(), k);
            }
        }

        /**
         * Test if a string is a valid key.
         *
         * @param keyString The key string.
         * @return True if they key string matches a key.
         */
        public static boolean isKey(String keyString) {
            return keyMap.containsKey(keyString);
        }

        /**
         * Parses a key string into a key.
         *
         * @param keyString The key string.
         * @return The key matching the key string.
         */
        public static FilterKey parse(String keyString) {
            return keyMap.get(keyString);
        }

        private final String keyString;
        private final String property;

        /**
         * Constructs a key with the specified key string.
         *
         * @param keyString The identifying key string.
         * @param propertyName The name of the property to match.
         */
        private FilterKey(String keyString, String propertyName) {
            this.keyString = keyString;
            this.property = propertyName;
        }

        @Override
        public String toString() {
            return this.keyString;
        }

        public Filter getFilterForString(String matchString) {
            return new FilterPredicate(this.property, FilterOperator.EQUAL, matchString);
        }

        public Filter getFilterForNumber(long matchNumber) {
            return new FilterPredicate(this.property, FilterOperator.EQUAL, matchNumber);
        }
    }

    /**
     * Gets a device filter from the user search string.
     *
     * @param searchString The user search string.
     * @return A filter to apply to a test run's device entities.
     */
    public static Filter getDeviceFilter(String searchString) {
        Filter deviceFilter = null;
        if (searchString != null) {
            Matcher m = Pattern.compile(SEARCH_REGEX).matcher(searchString);
            while (m.find()) {
                String term = m.group(1).replace("\"", "");
                if (!term.contains(TERM_DELIMITER))
                    continue;
                String[] terms = term.split(TERM_DELIMITER, 2);
                if (terms.length != 2 || !FilterKey.isKey(terms[0].toLowerCase()))
                    continue;

                FilterKey key = FilterKey.parse(terms[0].toLowerCase());
                switch (key) {
                    case BRANCH:
                    case DEVICE:
                    case DEVICE_BUILD_ID:
                    case TARGET:
                        String value = terms[1].toLowerCase();
                        Filter f = key.getFilterForString(value);
                        if (deviceFilter == null) {
                            deviceFilter = f;
                        } else {
                            deviceFilter = CompositeFilterOperator.and(deviceFilter, f);
                        }
                        break;
                    default:
                        break;
                }
            }
        }
        return deviceFilter;
    }

    /**
     * Get a filter on the test run type.
     *
     * @param showPresubmit True to display presubmit tests.
     * @param showPostsubmit True to display postsubmit tests.
     * @param unfiltered True if no filtering should be applied.
     * @return A filter on the test type.
     */
    public static Filter getTestTypeFilter(
            boolean showPresubmit, boolean showPostsubmit, boolean unfiltered) {
        if (unfiltered) {
            return null;
        } else if (showPresubmit && !showPostsubmit) {
            return new FilterPredicate(TestRunEntity.TYPE, FilterOperator.EQUAL,
                    TestRunEntity.TestRunType.PRESUBMIT.getNumber());
        } else if (showPostsubmit && !showPresubmit) {
            return new FilterPredicate(TestRunEntity.TYPE, FilterOperator.EQUAL,
                    TestRunEntity.TestRunType.POSTSUBMIT.getNumber());
        } else {
            List<Integer> types = new ArrayList<>();
            types.add(TestRunEntity.TestRunType.PRESUBMIT.getNumber());
            types.add(TestRunEntity.TestRunType.POSTSUBMIT.getNumber());
            return new FilterPredicate(TestRunEntity.TYPE, FilterOperator.IN, types);
        }
    }

    /**
     * Get a filter on test runs from a user search string.
     *
     * @param searchString The user search string to parse.
     * @param runTypeFilter An existing filter on test runs, or null.
     * @return A filter with the values from the user search string.
     */
    public static Filter getUserTestFilter(String searchString, Filter runTypeFilter) {
        Filter testRunFilter = runTypeFilter;
        if (searchString != null) {
            Matcher m = Pattern.compile(SEARCH_REGEX).matcher(searchString);
            while (m.find()) {
                String term = m.group(1).replace("\"", "");
                if (!term.contains(TERM_DELIMITER))
                    continue;
                String[] terms = term.split(TERM_DELIMITER, 2);
                if (terms.length != 2 || !FilterKey.isKey(terms[0].toLowerCase()))
                    continue;

                FilterKey key = FilterKey.parse(terms[0].toLowerCase());
                Filter f = null;
                switch (key) {
                    case NONPASSING:
                    case PASSING:
                        String valueString = terms[1];
                        try {
                            Long value = Long.parseLong(valueString);
                            f = key.getFilterForNumber(value);
                        } catch (NumberFormatException e) {
                            // invalid number
                        }
                        break;
                    case HOSTNAME:
                    case VTS_BUILD_ID:
                        String value = terms[1].toLowerCase();
                        f = key.getFilterForString(value);
                        break;
                    default:
                        break;
                }
                if (testRunFilter == null) {
                    testRunFilter = f;
                } else if (f != null) {
                    testRunFilter = CompositeFilterOperator.and(testRunFilter, f);
                }
            }
        }
        return testRunFilter;
    }

    /**
     * Get the time range filter to apply to a query.
     *
     * @param testKey The key of the parent TestEntity object.
     * @param startTime The start time in microseconds, or null if unbounded.
     * @param endTime The end time in microseconds, or null if unbounded.
     * @param testRunFilter The existing filter on test runs to apply, or null.
     * @return A filter to apply on test runs.
     */
    public static Filter getTimeFilter(
            Key testKey, Long startTime, Long endTime, Filter testRunFilter) {
        if (startTime == null && endTime == null) {
            endTime = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        }

        Filter startFilter = null;
        Filter endFilter = null;
        Filter filter = null;
        if (startTime != null) {
            Key startKey = KeyFactory.createKey(testKey, TestRunEntity.KIND, startTime);
            startFilter = new FilterPredicate(
                    Entity.KEY_RESERVED_PROPERTY, FilterOperator.GREATER_THAN_OR_EQUAL, startKey);
            filter = startFilter;
        }
        if (endTime != null) {
            Key endKey = KeyFactory.createKey(testKey, TestRunEntity.KIND, endTime);
            endFilter = new FilterPredicate(
                    Entity.KEY_RESERVED_PROPERTY, FilterOperator.LESS_THAN_OR_EQUAL, endKey);
            filter = endFilter;
        }
        if (startFilter != null && endFilter != null) {
            filter = CompositeFilterOperator.and(startFilter, endFilter);
        }
        if (testRunFilter != null) {
            filter = CompositeFilterOperator.and(filter, testRunFilter);
        }
        return filter;
    }

    public static Filter getTimeFilter(Key testKey, Long startTime, Long endTime) {
        return getTimeFilter(testKey, startTime, endTime, null);
    }
}
