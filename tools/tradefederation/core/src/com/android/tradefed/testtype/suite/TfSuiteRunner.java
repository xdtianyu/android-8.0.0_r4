/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.ConfigurationUtil;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.DirectedGraph;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil2;

import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Implementation of {@link ITestSuite} which will load tests from TF jars res/config/suite/
 * folder.
 */
public class TfSuiteRunner extends ITestSuite {

    @Option(name = "run-suite-tag", description = "The tag that must be run.",
            mandatory = true)
    private String mSuiteTag = null;

    @Option(
        name = "suite-config-prefix",
        description = "Search only configs with given prefix for suite tags."
    )
    private String mSuitePrefix = null;

    @Option(
        name = "additional-tests-zip",
        description = "Path to a zip file containing additional tests to be loaded."
    )
    private String mAdditionalTestsZip = null;

    private DirectedGraph<String> mLoadedConfigGraph = null;

    /** {@inheritDoc} */
    @Override
    public LinkedHashMap<String, IConfiguration> loadTests() {
        mLoadedConfigGraph = new DirectedGraph<>();
        return loadTests(null, mLoadedConfigGraph);
    }

    /**
     * Internal load configuration. Load configuration, expand sub suite runners and track cycle
     * inclusion to prevent infinite recursion.
     *
     * @param parentConfig the name of the config being loaded.
     * @param graph the directed graph tracking inclusion.
     * @return a map of module name and the configuration associated.
     */
    private LinkedHashMap<String, IConfiguration> loadTests(
            String parentConfig, DirectedGraph<String> graph) {
        LinkedHashMap <String, IConfiguration> configMap =
                new LinkedHashMap<String, IConfiguration>();
        IConfigurationFactory configFactory = ConfigurationFactory.getInstance();
        // TODO: Do a better job searching for configs.
        List<String> configs = configFactory.getConfigList(mSuitePrefix);

        if (getBuildInfo() instanceof IDeviceBuildInfo) {
            IDeviceBuildInfo deviceBuildInfo = (IDeviceBuildInfo) getBuildInfo();
            File testsDir = deviceBuildInfo.getTestsDir();
            if (testsDir != null) {
                if (mAdditionalTestsZip != null) {
                    CLog.d(
                            "Extract general-tests.zip (%s) to tests directory.",
                            mAdditionalTestsZip);
                    ZipFile zip = null;
                    try {
                        zip = new ZipFile(mAdditionalTestsZip);
                        ZipUtil2.extractZip(zip, testsDir);
                    } catch (IOException e) {
                        RuntimeException runtimeException =
                                new RuntimeException(
                                        String.format(
                                                "IO error (%s) when unzipping general-tests.zip",
                                                e.toString()),
                                        e);
                        throw runtimeException;
                    } finally {
                        StreamUtil.close(zip);
                    }
                }

                CLog.d(
                        "Loading extra test configs from the tests directory: %s",
                        testsDir.getAbsolutePath());
                List<File> extraTestCasesDirs = Arrays.asList(testsDir);
                configs.addAll(
                        ConfigurationUtil.getConfigNamesFromDirs(mSuitePrefix, extraTestCasesDirs));
            }
        }

        for (String configName : configs) {
            try {
                IConfiguration testConfig =
                        configFactory.createConfigurationFromArgs(new String[]{configName});
                if (testConfig.getConfigurationDescription().getSuiteTags().contains(mSuiteTag)) {
                    // In case some sub-config are suite too, we expand them to avoid weirdness
                    // of modules inside modules.
                    if (parentConfig != null) {
                        graph.addEdge(parentConfig, configName);
                        if (!graph.isDag()) {
                            CLog.e("%s", graph);
                            throw new RuntimeException(
                                    String.format(
                                            "Circular configuration detected: %s has been included "
                                                    + "several times.",
                                            configName));
                        }
                    }
                    LinkedHashMap<String, IConfiguration> expandedConfig =
                            expandTestSuites(configName, testConfig, graph);
                    configMap.putAll(expandedConfig);
                }
            } catch (ConfigurationException | NoClassDefFoundError e) {
                // Do not print the stack it's too verbose.
                CLog.e("Configuration '%s' cannot be loaded, ignoring.", configName);
            }
        }
        return configMap;
    }

    /**
     * Helper to expand all TfSuiteRunner included in sub-configuration. Avoid having module inside
     * module if a suite is ran as part of another suite.
     */
    private LinkedHashMap<String, IConfiguration> expandTestSuites(
            String configName, IConfiguration config, DirectedGraph<String> graph) {
        LinkedHashMap<String, IConfiguration> configMap =
                new LinkedHashMap<String, IConfiguration>();
        List<IRemoteTest> tests = new ArrayList<>(config.getTests());
        for (IRemoteTest test : tests) {
            if (test instanceof TfSuiteRunner) {
                TfSuiteRunner runner = (TfSuiteRunner) test;
                // Suite runner can only load and run stuff, if it has a suite tag set.
                if (runner.getSuiteTag() != null) {
                    LinkedHashMap<String, IConfiguration> subConfigs =
                            runner.loadTests(configName, graph);
                    configMap.putAll(subConfigs);
                } else {
                    CLog.w(
                            "Config %s does not have a suite-tag it cannot run anything.",
                            configName);
                }
                config.getTests().remove(test);
            }
        }
        // If we have any IRemoteTests remaining in the base configuration, it will run.
        if (!config.getTests().isEmpty()) {
            configMap.put(configName, config);
        }

        return configMap;
    }

    private String getSuiteTag() {
        return mSuiteTag;
    }
}
