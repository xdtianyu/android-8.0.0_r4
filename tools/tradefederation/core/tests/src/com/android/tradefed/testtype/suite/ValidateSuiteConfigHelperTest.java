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
package com.android.tradefed.testtype.suite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.build.LocalFolderBuildProvider;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TextResultReporter;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Unit tests for {@link ValidateSuiteConfigHelper} */
public class ValidateSuiteConfigHelperTest {

    /** Test that a config with default value can run as suite. */
    @Test
    public void testCanRunAsSuite() {
        IConfiguration config = new Configuration("test", "test description");
        assertTrue(ValidateSuiteConfigHelper.validateConfig(config));
    }

    /** Test that a config with a build provider cannot run as suite. */
    @Test
    public void testNotRunningAsSuite_buildProvider() {
        IConfiguration config = new Configuration("test", "test description");
        // LocalFolderBuildProvider extends the default StubBuildProvider but is still correctly
        // rejected.
        config.setBuildProvider(new LocalFolderBuildProvider());
        assertFalse(ValidateSuiteConfigHelper.validateConfig(config));
    }

    /** Test that a config with a result reporter cannot run as suite. */
    @Test
    public void testNotRunningAsSuite_resultReporter() {
        IConfiguration config = new Configuration("test", "test description");
        config.setTestInvocationListener(new CollectingTestListener());
        assertFalse(ValidateSuiteConfigHelper.validateConfig(config));
    }

    /** Test that a config with a multiple result reporter cannot run as suite. */
    @Test
    public void testNotRunningAsSuite_multi_resultReporter() {
        IConfiguration config = new Configuration("test", "test description");
        List<ITestInvocationListener> listeners = new ArrayList<>();
        listeners.add(new TextResultReporter());
        listeners.add(new CollectingTestListener());
        config.setTestInvocationListeners(listeners);
        assertFalse(ValidateSuiteConfigHelper.validateConfig(config));
    }
}
