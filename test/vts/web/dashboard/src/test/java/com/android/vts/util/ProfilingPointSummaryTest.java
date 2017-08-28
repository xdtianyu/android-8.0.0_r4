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

package com.android.vts.util;

import static org.junit.Assert.*;

import com.android.vts.entity.ProfilingPointRunEntity;
import com.android.vts.entity.TestEntity;
import com.android.vts.proto.VtsReportMessage.VtsProfilingRegressionMode;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ProfilingPointSummaryTest {
    private final LocalServiceTestHelper helper =
            new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());
    private static String[] labels = new String[] {"label1", "label2", "label3"};
    private static long[] values = new long[] {1, 2, 3};
    private static ProfilingPointSummary summary;

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
                regressionMode.getNumber(), labelList, valueList, "x", "y");
    }

    @Before
    public void setUp() {
        helper.setUp();
        summary = new ProfilingPointSummary();
        VtsProfilingRegressionMode mode = VtsProfilingRegressionMode.VTS_REGRESSION_MODE_INCREASING;
        ProfilingPointRunEntity pt = createProfilingReport(labels, values, mode);
        summary.update(pt);
    }

    @After
    public void tearDown() {
        helper.tearDown();
    }

    /** Test that all labels are found by hasLabel. */
    @Test
    public void testHasLabel() {
        for (String label : labels) {
            assertTrue(summary.hasLabel(label));
        }
    }

    /** Test that invalid labels are not found by hasLabel. */
    @Test
    public void testInvalidHasLabel() {
        assertFalse(summary.hasLabel("bad label"));
    }

    /** Test that all stat summaries can be retrieved by profiling point label. */
    @Test
    public void testGetStatSummary() {
        for (String label : labels) {
            StatSummary stats = summary.getStatSummary(label);
            assertNotNull(stats);
            assertEquals(label, stats.getLabel());
        }
    }

    /** Test that the getStatSummary method returns null when the label is not present. */
    @Test
    public void testInvalidGetStatSummary() {
        StatSummary stats = summary.getStatSummary("bad label");
        assertNull(stats);
    }

    /** Test that StatSummary objects are iterated in the order that the labels were provided. */
    @Test
    public void testIterator() {
        VtsProfilingRegressionMode mode = VtsProfilingRegressionMode.VTS_REGRESSION_MODE_INCREASING;
        ProfilingPointRunEntity pt = createProfilingReport(labels, values, mode);
        summary.update(pt);

        int i = 0;
        for (StatSummary stats : summary) {
            assertEquals(labels[i++], stats.getLabel());
        }
    }

    /** Test that the updateLabel method updates the StatSummary for just the label provided. */
    @Test
    public void testUpdateLabelGrouped() {
        summary = new ProfilingPointSummary();
        VtsProfilingRegressionMode mode = VtsProfilingRegressionMode.VTS_REGRESSION_MODE_INCREASING;
        ProfilingPointRunEntity pt = createProfilingReport(labels, values, mode);
        summary.updateLabel(pt, labels[0]);

        // Ensure the label specified is present and has been updated for each data point.
        assertTrue(summary.hasLabel(labels[0]));
        assertNotNull(summary.getStatSummary(labels[0]));
        assertEquals(summary.getStatSummary(labels[0]).getCount(), labels.length);

        // Check that the other labels were not updated.
        for (int i = 1; i < labels.length; i++) {
            assertFalse(summary.hasLabel(labels[i]));
        }
    }
}
