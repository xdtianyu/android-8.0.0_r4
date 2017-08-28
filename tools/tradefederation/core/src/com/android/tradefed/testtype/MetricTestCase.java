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
package com.android.tradefed.testtype;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

/**
 * Extension of {@link TestCase} that allows to log metrics when running as part of TradeFed. Either
 * directly as a {@link DeviceTestCase} or as part of a {@link HostTest}. TODO: Evaluate if having
 * run metric (not only test metric) make sense for JUnit3 tests.
 */
public class MetricTestCase extends TestCase {

    public Map<String, String> mMetrics = new HashMap<>();

    public MetricTestCase() {
        super();
    }

    /** Constructs a test case with the given name. Inherited from {@link TestCase} constructor. */
    public MetricTestCase(String name) {
        super(name);
    }

    /**
     * Log a metric for the test case.
     *
     * @param key the key under which the metric will be found.
     * @param value associated to the key.
     */
    public final void addTestMetric(String key, String value) {
        mMetrics.put(key, value);
    }
}
