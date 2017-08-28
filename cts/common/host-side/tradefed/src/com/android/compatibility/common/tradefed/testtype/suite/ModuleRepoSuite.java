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
package com.android.compatibility.common.tradefed.testtype.suite;

import com.android.compatibility.common.util.TestFilter;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestFileFilterReceiver;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.util.AbiUtils;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Retrieves Compatibility test module definitions from the repository.
 */
public class ModuleRepoSuite {

    private static final String CONFIG_EXT = ".config";
    private Map<String, Map<String, List<String>>> mTestArgs = new HashMap<>();
    private Map<String, Map<String, List<String>>> mModuleArgs = new HashMap<>();
    private boolean mIncludeAll;
    private Map<String, List<TestFilter>> mIncludeFilters = new HashMap<>();
    private Map<String, List<TestFilter>> mExcludeFilters = new HashMap<>();
    private IConfigurationFactory mConfigFactory = ConfigurationFactory.getInstance();

    /**
     * Main loading of configurations, looking into testcases/ folder
     */
    public LinkedHashMap<String, IConfiguration> loadConfigs(File testsDir, Set<IAbi> abis,
            List<String> testArgs, List<String> moduleArgs,
            Set<String> includeFilters, Set<String> excludeFilters) {
        CLog.d("Initializing ModuleRepo\nTests Dir:%s\nABIs:%s\n" +
                "Test Args:%s\nModule Args:%s\nIncludes:%s\nExcludes:%s",
                testsDir.getAbsolutePath(), abis, testArgs, moduleArgs,
                includeFilters, excludeFilters);

        LinkedHashMap<String, IConfiguration> toRun = new LinkedHashMap<>();

        putArgs(testArgs, mTestArgs);
        putArgs(moduleArgs, mModuleArgs);
        mIncludeAll = includeFilters.isEmpty();
        // Include all the inclusions
        addFilters(includeFilters, mIncludeFilters, abis);
        // Exclude all the exclusions
        addFilters(excludeFilters, mExcludeFilters, abis);

        File[] configFiles = testsDir.listFiles(new ConfigFilter());
        if (configFiles.length == 0) {
            throw new IllegalArgumentException(
                    String.format("No config files found in %s", testsDir.getAbsolutePath()));
        }
        for (File configFile : configFiles) {
            final String name = configFile.getName().replace(CONFIG_EXT, "");
            final String[] pathArg = new String[] { configFile.getAbsolutePath() };
            try {
                // Invokes parser to process the test module config file
                // Need to generate a different config for each ABI as we cannot guarantee the
                // configs are idempotent. This however means we parse the same file multiple times
                for (IAbi abi : abis) {
                    String id = AbiUtils.createId(abi.getName(), name);
                    if (!shouldRunModule(id)) {
                        // If the module should not run tests based on the state of filters,
                        // skip this name/abi combination.
                        continue;
                    }
                    IConfiguration config = mConfigFactory.createConfigurationFromArgs(pathArg);
                    Map<String, List<String>> args = new HashMap<>();
                    if (mModuleArgs.containsKey(name)) {
                        args.putAll(mModuleArgs.get(name));
                    }
                    if (mModuleArgs.containsKey(id)) {
                        args.putAll(mModuleArgs.get(id));
                    }
                    for (Entry<String, List<String>> entry : args.entrySet()) {
                        for (String value : entry.getValue()) {
                            // Collection-type options can be injected with multiple values
                            config.injectOptionValue(entry.getKey(), value);
                        }
                    }

                    List<IRemoteTest> tests = config.getTests();
                    for (IRemoteTest test : tests) {
                        String className = test.getClass().getName();
                        Map<String, List<String>> testArgsMap = new HashMap<>();
                        if (mTestArgs.containsKey(className)) {
                            testArgsMap.putAll(mTestArgs.get(className));
                        }
                        for (Entry<String, List<String>> entry : testArgsMap.entrySet()) {
                            for (String value : entry.getValue()) {
                                config.injectOptionValue(entry.getKey(), value);
                            }
                        }
                        addFiltersToTest(test, abi, name);
                        if (test instanceof IAbiReceiver) {
                            ((IAbiReceiver)test).setAbi(abi);
                        }
                    }
                    List<ITargetPreparer> preparers = config.getTargetPreparers();
                    for (ITargetPreparer preparer : preparers) {
                        if (preparer instanceof IAbiReceiver) {
                            ((IAbiReceiver)preparer).setAbi(abi);
                        }
                    }
                    toRun.put(id, config);
                }
            } catch (ConfigurationException e) {
                throw new RuntimeException(String.format("Error parsing config file: %s",
                        configFile.getName()), e);
            }
        }
        return toRun;
    }

    private void addFilters(Set<String> stringFilters,
            Map<String, List<TestFilter>> filters, Set<IAbi> abis) {
        for (String filterString : stringFilters) {
            TestFilter filter = TestFilter.createFrom(filterString);
            String abi = filter.getAbi();
            if (abi == null) {
                for (IAbi a : abis) {
                    addFilter(a.getName(), filter, filters);
                }
            } else {
                addFilter(abi, filter, filters);
            }
        }
    }

    private void addFilter(String abi, TestFilter filter,
            Map<String, List<TestFilter>> filters) {
        getFilterList(filters, AbiUtils.createId(abi, filter.getName())).add(filter);
    }

    private List<TestFilter> getFilterList(Map<String, List<TestFilter>> filters, String id) {
        List<TestFilter> fs = filters.get(id);
        if (fs == null) {
            fs = new ArrayList<>();
            filters.put(id, fs);
        }
        return fs;
    }

    private void addFiltersToTest(IRemoteTest test, IAbi abi, String name) {
        String moduleId = AbiUtils.createId(abi.getName(), name);
        if (!(test instanceof ITestFilterReceiver)) {
            throw new IllegalArgumentException(String.format(
                    "Test in module %s must implement ITestFilterReceiver.", moduleId));
        }
        List<TestFilter> mdIncludes = getFilterList(mIncludeFilters, moduleId);
        List<TestFilter> mdExcludes = getFilterList(mExcludeFilters, moduleId);
        if (!mdIncludes.isEmpty()) {
            addTestIncludes((ITestFilterReceiver) test, mdIncludes, name);
        }
        if (!mdExcludes.isEmpty()) {
            addTestExcludes((ITestFilterReceiver) test, mdExcludes, name);
        }
    }

    private boolean shouldRunModule(String moduleId) {
        List<TestFilter> mdIncludes = getFilterList(mIncludeFilters, moduleId);
        List<TestFilter> mdExcludes = getFilterList(mExcludeFilters, moduleId);
        // if including all modules or includes exist for this module, and there are not excludes
        // for the entire module, this module should be run.
        return (mIncludeAll || !mdIncludes.isEmpty()) && !containsModuleExclude(mdExcludes);
    }

    private void addTestIncludes(ITestFilterReceiver test, List<TestFilter> includes,
            String name) {
        if (test instanceof ITestFileFilterReceiver) {
            File includeFile = createFilterFile(name, ".include", includes);
            ((ITestFileFilterReceiver)test).setIncludeTestFile(includeFile);
        } else {
            // add test includes one at a time
            for (TestFilter include : includes) {
                String filterTestName = include.getTest();
                if (filterTestName != null) {
                    test.addIncludeFilter(filterTestName);
                }
            }
        }
    }

    private void addTestExcludes(ITestFilterReceiver test, List<TestFilter> excludes,
            String name) {
        if (test instanceof ITestFileFilterReceiver) {
            File excludeFile = createFilterFile(name, ".exclude", excludes);
            ((ITestFileFilterReceiver)test).setExcludeTestFile(excludeFile);
        } else {
            // add test excludes one at a time
            for (TestFilter exclude : excludes) {
                test.addExcludeFilter(exclude.getTest());
            }
        }
    }

    private File createFilterFile(String prefix, String suffix, List<TestFilter> filters) {
        File filterFile = null;
        PrintWriter out = null;
        try {
            filterFile = FileUtil.createTempFile(prefix, suffix);
            out = new PrintWriter(filterFile);
            for (TestFilter filter : filters) {
                String filterTest = filter.getTest();
                if (filterTest != null) {
                    out.println(filterTest);
                }
            }
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create filter file");
        } finally {
            StreamUtil.close(out);
        }
        filterFile.deleteOnExit();
        return filterFile;
    }

    /**
     * Returns true iff one or more test filters in excludes apply to the entire module.
     */
    private boolean containsModuleExclude(Collection<TestFilter> excludes) {
        for (TestFilter exclude : excludes) {
            if (exclude.getTest() == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * A {@link FilenameFilter} to find all the config files in a directory.
     */
    public static class ConfigFilter implements FilenameFilter {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean accept(File dir, String name) {
            CLog.d("%s/%s", dir.getAbsolutePath(), name);
            return name.endsWith(CONFIG_EXT);
        }
    }

    private static void putArgs(List<String> args,
            Map<String, Map<String, List<String>>> argsMap) {
        for (String arg : args) {
            String[] parts = arg.split(":");
            String target = parts[0];
            String key = parts[1];
            String value = parts[2];
            Map<String, List<String>> map = argsMap.get(target);
            if (map == null) {
                map = new HashMap<>();
                argsMap.put(target, map);
            }
            List<String> valueList = map.get(key);
            if (valueList == null) {
                valueList = new ArrayList<>();
                map.put(key, valueList);
            }
            valueList.add(value);
        }
    }
}
