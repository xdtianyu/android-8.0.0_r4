/*
 * Copyright (c) 2017 Google Inc. All Rights Reserved.
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

package com.android.vts.entity;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Entity describing the execution of a test case. */
public class TestCaseRunEntity implements DashboardEntity {
    protected static final Logger logger = Logger.getLogger(TestCaseRunEntity.class.getName());

    public static final String KIND = "TestCaseRun";

    // Property keys
    public static final String TEST_CASE_NAME = "testCaseName";
    public static final String RESULT = "result";
    public static final String SYSTRACE_URL = "systraceUrl";

    public final Key key;
    public final String testCaseName;
    public final int result;
    public final String systraceUrl;

    /**
     * Create a TestCaseRunEntity
     *
     * @param key The key at which the object will be stored in the database.
     * @param testCaseName The name of the test case.
     * @param result The (numerical) result of the testcase.
     * @param systraceUrl URL to systrace links.
     */
    public TestCaseRunEntity(Key key, String testCaseName, int result, String systraceUrl) {
        this.key = key;
        this.testCaseName = testCaseName;
        this.result = result;
        this.systraceUrl = systraceUrl;
    }

    /**
     * Create a TestCaseRunEntity
     *
     * @param key The key at which the object will be stored in the database.
     * @param testCaseName The name of the test case.
     * @param result The (numerical) result of the testcase.
     */
    public TestCaseRunEntity(Key key, String testCaseName, int result) {
        this(key, testCaseName, result, null);
    }

    @Override
    public Entity toEntity() {
        Entity testCaseRunEntity = new Entity(key);
        testCaseRunEntity.setProperty(TEST_CASE_NAME, this.testCaseName);
        testCaseRunEntity.setProperty(RESULT, this.result);
        if (systraceUrl != null) {
            testCaseRunEntity.setUnindexedProperty(SYSTRACE_URL, this.systraceUrl);
        }

        return testCaseRunEntity;
    }

    /**
     * Convert an Entity object to a TestCaseRunEntity.
     *
     * @param e The entity to process.
     * @return TestCaseRunEntity object with the properties from e, or null if incompatible.
     */
    public static TestCaseRunEntity fromEntity(Entity e) {
        if (!e.getKind().equals(KIND) || !e.hasProperty(TEST_CASE_NAME) || !e.hasProperty(RESULT)) {
            logger.log(Level.WARNING, "Missing test case attributes in entity: " + e.toString());
            return null;
        }
        try {
            String testCaseName = (String) e.getProperty(TEST_CASE_NAME);
            int result = (int) ((long) e.getProperty(RESULT));
            if (e.hasProperty(SYSTRACE_URL)) {
                String systraceUrl = (String) e.getProperty(SYSTRACE_URL);
                return new TestCaseRunEntity(e.getKey(), testCaseName, result, systraceUrl);
            }
            return new TestCaseRunEntity(e.getKey(), testCaseName, result);
        } catch (ClassCastException exception) {
            // Invalid cast
            logger.log(Level.WARNING, "Error parsing test case run entity.", exception);
        }
        return null;
    }
}
