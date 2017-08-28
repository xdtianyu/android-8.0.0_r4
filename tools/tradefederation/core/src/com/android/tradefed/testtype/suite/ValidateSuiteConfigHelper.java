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

import com.android.tradefed.build.StubBuildProvider;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.result.TextResultReporter;

/**
 * This class will help validating that the {@link IConfiguration} loaded for the suite are meeting
 * the expected requirements: - No Build providers - No Result reporters
 */
public class ValidateSuiteConfigHelper {

    /**
     * Check that a configuration is properly built to run in a suite.
     *
     * @param config a {@link IConfiguration} to be checked if valide for suite.
     * @return True if the config can be run, false otherwise.
     */
    public static boolean validateConfig(IConfiguration config) {
        if (!config.getBuildProvider().getClass().isAssignableFrom(StubBuildProvider.class)) {
            return false;
        }
        if (config.getTestInvocationListeners().size() != 1) {
            return false;
        }
        if (!config.getTestInvocationListeners()
                .get(0)
                .getClass()
                .isAssignableFrom(TextResultReporter.class)) {
            return false;
        }
        return true;
    }
}
