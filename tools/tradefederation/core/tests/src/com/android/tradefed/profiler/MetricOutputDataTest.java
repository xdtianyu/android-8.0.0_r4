/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tradefed.profiler;

import com.android.ddmlib.testrunner.TestIdentifier;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link com.android.tradefed.profiler.MetricOutputData}.
 */
@RunWith(JUnit4.class)
public class MetricOutputDataTest {

    private MetricOutputData mOutputUtil;

    @Before
    public void setUp() throws Exception {
        mOutputUtil = new MetricOutputData();
    }

    @Test
    public void testAddMetrics_String() {
        Map<String, Double> metrics = new HashMap<String, Double>();
        metrics.put("baz", 1.0);
        metrics.put("quux", 2.0);
        mOutputUtil.addMetrics("foo", "bar", metrics);
        String expected = "foo:bar:\nquux=2.00000\nbaz=1.00000\n---\n";
        String formatted = mOutputUtil.mMetrics.toString();
        Assert.assertEquals(expected, formatted);
    }

    @Test
    public void testAddMetrics_TestIdentifier() {
        Map<String, Double> metrics = new HashMap<String, Double>();
        metrics.put("baz", 1.0);
        metrics.put("quux", 2.0);
        TestIdentifier id = new TestIdentifier(
                "com.android.tradefed.profiler.MetricOutputDataTest", "bar");
        mOutputUtil.addMetrics("foo", id, metrics);
        String expected = "foo:bar:\nquux=2.00000\nbaz=1.00000\n---\n";
        String formatted = mOutputUtil.mMetrics.toString();
        Assert.assertEquals(expected, formatted);
    }
}
