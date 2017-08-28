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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Entity describing test metadata. */
public class TestEntity implements DashboardEntity {
    protected static final Logger logger = Logger.getLogger(TestEntity.class.getName());

    public static final String KIND = "Test";

    // Property keys
    public static final String PASS_COUNT = "passCount";
    public static final String FAIL_COUNT = "failCount";
    public static final String UPDATED_TIMESTAMP = "updatedTimestamp";
    public static final String FAILING_IDS = "failingTestcaseIds";

    public final String testName;
    public final int passCount;
    public final int failCount;
    public final long timestamp;
    public final List<Long> failingTestcaseIds;

    /**
     * Create a TestEntity object with status metadata.
     *
     * @param testName The name of the test.
     * @param timestamp The timestamp indicating the most recent test run event in the test state.
     * @param passCount The number of tests passing up to the timestamp specified.
     * @param failCount The number of tests failing up to the timestamp specified.
     * @param failingTestcaseIds The key IDs of the last observed failing test cases.
     */
    public TestEntity(String testName, long timestamp, int passCount, int failCount,
            List<Long> failingTestcaseIds) {
        this.testName = testName;
        this.timestamp = timestamp;
        this.passCount = passCount;
        this.failCount = failCount;
        this.failingTestcaseIds = failingTestcaseIds;
    }

    /**
     * Create a TestEntity object without metadata.
     *
     * @param testName The name of the test.
     */
    public TestEntity(String testName) {
        this(testName, 0, -1, -1, null);
    }

    @Override
    public Entity toEntity() {
        Entity testEntity = new Entity(KIND, this.testName);
        if (this.timestamp >= 0 && this.passCount >= 0 && this.failCount >= 0) {
            testEntity.setProperty(UPDATED_TIMESTAMP, this.timestamp);
            testEntity.setProperty(PASS_COUNT, this.passCount);
            testEntity.setProperty(FAIL_COUNT, this.failCount);
            if (this.failingTestcaseIds != null && this.failingTestcaseIds.size() > 0) {
                testEntity.setProperty(FAILING_IDS, this.failingTestcaseIds);
            }
        }
        return testEntity;
    }

    /**
     * Convert an Entity object to a TestEntity.
     *
     * @param e The entity to process.
     * @return TestEntity object with the properties from e processed, or null if incompatible.
     */
    @SuppressWarnings("unchecked")
    public static TestEntity fromEntity(Entity e) {
        if (!e.getKind().equals(KIND) || e.getKey().getName() == null) {
            logger.log(Level.WARNING, "Missing test attributes in entity: " + e.toString());
            return null;
        }
        String testName = e.getKey().getName();
        long timestamp = 0;
        int passCount = -1;
        int failCount = -1;
        List<Long> failingTestcaseIds = null;
        try {
            if (e.hasProperty(UPDATED_TIMESTAMP)) {
                timestamp = (long) e.getProperty(UPDATED_TIMESTAMP);
            }
            if (e.hasProperty(PASS_COUNT)) {
                passCount = ((Long) e.getProperty(PASS_COUNT)).intValue();
            }
            if (e.hasProperty(FAIL_COUNT)) {
                failCount = ((Long) e.getProperty(FAIL_COUNT)).intValue();
            }
            if (e.hasProperty(FAILING_IDS)) {
                failingTestcaseIds = (List<Long>) e.getProperty(FAILING_IDS);
            }
        } catch (ClassCastException exception) {
            // Invalid contents or null values
            logger.log(Level.WARNING, "Error parsing test entity.", exception);
        }
        return new TestEntity(testName, timestamp, passCount, failCount, failingTestcaseIds);
    }
}
