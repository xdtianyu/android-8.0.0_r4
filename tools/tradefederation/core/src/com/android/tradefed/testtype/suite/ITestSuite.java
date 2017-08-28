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

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.suite.checker.ISystemStatusChecker;
import com.android.tradefed.suite.checker.ISystemStatusCheckerReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IShardableTest;
import com.android.tradefed.testtype.ITestCollector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

/**
 * Abstract class used to run Test Suite. This class provide the base of how the Suite will be run.
 * Each implementation can define the list of tests via the {@link #loadTests()} method.
 */
public abstract class ITestSuite
        implements IRemoteTest,
                IDeviceTest,
                IBuildReceiver,
                ISystemStatusCheckerReceiver,
                IShardableTest,
                ITestCollector {

    public static final String MODULE_CHECKER_PRE = "PreModuleChecker";
    public static final String MODULE_CHECKER_POST = "PostModuleChecker";

    // Options for test failure case
    @Option(
        name = "bugreport-on-failure",
        description =
                "Take a bugreport on every test failure. Warning: This may require a lot"
                        + "of storage space of the machine running the tests."
    )
    private boolean mBugReportOnFailure = false;

    @Option(name = "logcat-on-failure",
            description = "Take a logcat snapshot on every test failure.")
    private boolean mLogcatOnFailure = false;

    @Option(name = "logcat-on-failure-size",
            description = "The max number of logcat data in bytes to capture when "
            + "--logcat-on-failure is on. Should be an amount that can comfortably fit in memory.")
    private int mMaxLogcatBytes = 500 * 1024; // 500K

    @Option(name = "screenshot-on-failure",
            description = "Take a screenshot on every test failure.")
    private boolean mScreenshotOnFailure = false;

    @Option(name = "reboot-on-failure",
            description = "Reboot the device after every test failure.")
    private boolean mRebootOnFailure = false;

    // Options for suite runner behavior
    @Option(name = "reboot-per-module", description = "Reboot the device before every module run.")
    private boolean mRebootPerModule = false;

    @Option(name = "skip-all-system-status-check",
            description = "Whether all system status check between modules should be skipped")
    private boolean mSkipAllSystemStatusCheck = false;

    @Option(
        name = "report-system-checkers",
        description = "Whether reporting system checkers as test or not."
    )
    private boolean mReportSystemChecker = false;

    @Option(
        name = "collect-tests-only",
        description =
                "Only invoke the suite to collect list of applicable test cases. All "
                        + "test run callbacks will be triggered, but test execution will not be "
                        + "actually carried out."
    )
    private boolean mCollectTestsOnly = false;

    private ITestDevice mDevice;
    private IBuildInfo mBuildInfo;
    private List<ISystemStatusChecker> mSystemStatusCheckers;

    // Sharding attributes
    private boolean mIsSharded = false;
    private ModuleDefinition mDirectModule = null;
    private boolean mShouldMakeDynamicModule = true;

    /**
     * Abstract method to load the tests configuration that will be run. Each tests is defined by a
     * {@link IConfiguration} and a unique name under which it will report results.
     */
    public abstract LinkedHashMap<String, IConfiguration> loadTests();

    /**
     * Return an instance of the class implementing {@link ITestSuite}.
     */
    private ITestSuite createInstance() {
        try {
            return this.getClass().newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /** Helper that creates and returns the list of {@link ModuleDefinition} to be executed. */
    private List<ModuleDefinition> createExecutionList() {
        List<ModuleDefinition> runModules = new ArrayList<>();
        if (mDirectModule != null) {
            // If we are sharded and already know what to run then we just do it.
            runModules.add(mDirectModule);
            mDirectModule.setDevice(mDevice);
            mDirectModule.setBuild(mBuildInfo);
            return runModules;
        }

        LinkedHashMap<String, IConfiguration> runConfig = loadTests();
        if (runConfig.isEmpty()) {
            CLog.i("No config were loaded. Nothing to run.");
            return runModules;
        }

        for (Entry<String, IConfiguration> config : runConfig.entrySet()) {
            if (!ValidateSuiteConfigHelper.validateConfig(config.getValue())) {
                throw new RuntimeException(
                        new ConfigurationException(
                                String.format(
                                        "Configuration %s cannot be run in a suite.",
                                        config.getValue().getName())));
            }
            ModuleDefinition module = new ModuleDefinition(config.getKey(),
                    config.getValue().getTests(), config.getValue().getTargetPreparers());
            module.setDevice(mDevice);
            module.setBuild(mBuildInfo);
            runModules.add(module);
        }
        // Free the map once we are done with it.
        runConfig = null;
        return runModules;
    }

    /** Generic run method for all test loaded from {@link #loadTests()}. */
    @Override
    public final void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        List<ModuleDefinition> runModules = createExecutionList();
        // Check if we have something to run.
        if (runModules.isEmpty()) {
            CLog.i("No tests to be run.");
            return;
        }

        /** Setup a special listener to take actions on test failures. */
        TestFailureListener failureListener =
                new TestFailureListener(
                        listener,
                        getDevice(),
                        mBugReportOnFailure,
                        mLogcatOnFailure,
                        mScreenshotOnFailure,
                        mRebootOnFailure,
                        mMaxLogcatBytes);

        // Only print the running log if we are going to run something.
        if (runModules.get(0).hasTests()) {
            CLog.logAndDisplay(
                    LogLevel.INFO,
                    "%s running %s modules: %s",
                    mDevice.getSerialNumber(),
                    runModules.size(),
                    runModules);
        }

        /** Run all the module, make sure to reduce the list to release resources as we go. */
        try {
            while (!runModules.isEmpty()) {
                ModuleDefinition module = runModules.remove(0);
                // Before running the module we ensure it has tests at this point or skip completely
                // to avoid running SystemCheckers and preparation for nothing.
                if (module.hasTests()) {
                    continue;
                }
                runSingleModule(module, listener, failureListener);
            }
        } catch (DeviceNotAvailableException e) {
            CLog.e(
                    "A DeviceNotAvailableException occurred, following modules did not run: %s",
                    runModules);
            for (ModuleDefinition module : runModules) {
                listener.testRunStarted(module.getId(), 0);
                listener.testRunFailed("Module did not run due to device not available.");
                listener.testRunEnded(0, Collections.emptyMap());
            }
            throw e;
        }
    }

    /**
     * Helper method that handle running a single module logic.
     *
     * @param module The {@link ModuleDefinition} to be ran.
     * @param listener The {@link ITestInvocationListener} where to report results
     * @param failureListener The {@link TestFailureListener} that collect infos on failures.
     * @throws DeviceNotAvailableException
     */
    private void runSingleModule(
            ModuleDefinition module,
            ITestInvocationListener listener,
            TestFailureListener failureListener)
            throws DeviceNotAvailableException {
        if (mRebootPerModule) {
            if ("user".equals(mDevice.getProperty("ro.build.type"))) {
                CLog.e(
                        "reboot-per-module should only be used during development, "
                                + "this is a\" user\" build device");
            } else {
                CLog.d("Rebooting device before starting next module");
                mDevice.reboot();
            }
        }

        if (!mSkipAllSystemStatusCheck) {
            runPreModuleCheck(module.getId(), mSystemStatusCheckers, mDevice, listener);
        }
        if (mCollectTestsOnly) {
            module.setCollectTestsOnly(mCollectTestsOnly);
        }
        // Actually run the module
        module.run(listener, failureListener);

        if (!mSkipAllSystemStatusCheck) {
            runPostModuleCheck(module.getId(), mSystemStatusCheckers, mDevice, listener);
        }
    }

    /**
     * Helper to run the System Status checkers preExecutionChecks defined for the test and log
     * their failures.
     */
    private void runPreModuleCheck(
            String moduleName,
            List<ISystemStatusChecker> checkers,
            ITestDevice device,
            ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        long startTime = System.currentTimeMillis();
        CLog.i("Running system status checker before module execution: %s", moduleName);
        List<String> failures = new ArrayList<>();
        for (ISystemStatusChecker checker : checkers) {
            boolean result = checker.preExecutionCheck(device);
            if (!result) {
                failures.add(checker.getClass().getCanonicalName());
                CLog.w("System status checker [%s] failed", checker.getClass().getCanonicalName());
            }
        }
        if (!failures.isEmpty()) {
            CLog.w("There are failed system status checkers: %s capturing a bugreport",
                    failures.toString());
            InputStreamSource bugSource = device.getBugreport();
            listener.testLog(
                    String.format("bugreport-checker-pre-module-%s", moduleName),
                    LogDataType.BUGREPORT,
                    bugSource);
            bugSource.cancel();
        }

        // We report System checkers like tests.
        reportModuleCheckerResult(MODULE_CHECKER_PRE, moduleName, failures, startTime, listener);
    }

    /**
     * Helper to run the System Status checkers postExecutionCheck defined for the test and log
     * their failures.
     */
    private void runPostModuleCheck(
            String moduleName,
            List<ISystemStatusChecker> checkers,
            ITestDevice device,
            ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        long startTime = System.currentTimeMillis();
        CLog.i("Running system status checker after module execution: %s", moduleName);
        List<String> failures = new ArrayList<>();
        for (ISystemStatusChecker checker : checkers) {
            boolean result = checker.postExecutionCheck(device);
            if (!result) {
                failures.add(checker.getClass().getCanonicalName());
                CLog.w("System status checker [%s] failed", checker.getClass().getCanonicalName());
            }
        }
        if (!failures.isEmpty()) {
            CLog.w("There are failed system status checkers: %s capturing a bugreport",
                    failures.toString());
            InputStreamSource bugSource = device.getBugreport();
            listener.testLog(
                    String.format("bugreport-checker-post-module-%s", moduleName),
                    LogDataType.BUGREPORT,
                    bugSource);
            bugSource.cancel();
        }

        // We report System checkers like tests.
        reportModuleCheckerResult(MODULE_CHECKER_POST, moduleName, failures, startTime, listener);
    }

    /** Helper to report status checker results as test results. */
    private void reportModuleCheckerResult(
            String identifier,
            String moduleName,
            List<String> failures,
            long startTime,
            ITestInvocationListener listener) {
        if (!mReportSystemChecker) {
            // do not log here, otherwise it could be very verbose.
            return;
        }
        // Avoid messing with the final test count by making them empty runs.
        listener.testRunStarted(identifier + "_" + moduleName, 0);
        if (!failures.isEmpty()) {
            listener.testRunFailed(String.format("%s failed '%s' checkers", moduleName, failures));
        }
        listener.testRunEnded(System.currentTimeMillis() - startTime, Collections.emptyMap());
    }

    /** {@inheritDoc} */
    @Override
    public Collection<IRemoteTest> split(int shardCountHint) {
        if (shardCountHint <= 1 || mIsSharded) {
            // cannot shard or already sharded
            return null;
        }

        LinkedHashMap<String, IConfiguration> runConfig = loadTests();
        if (runConfig.isEmpty()) {
            CLog.i("No config were loaded. Nothing to run.");
            return null;
        }
        injectInfo(runConfig);

        // We split individual tests on double the shardCountHint to provide better average.
        // The test pool mechanism prevent this from creating too much overhead.
        List<ModuleDefinition> splitModules =
                ModuleSplitter.splitConfiguration(
                        runConfig, shardCountHint, mShouldMakeDynamicModule);
        runConfig.clear();
        runConfig = null;
        // create an association of one ITestSuite <=> one ModuleDefinition as the smallest
        // execution unit supported.
        List<IRemoteTest> splitTests = new ArrayList<>();
        for (ModuleDefinition m : splitModules) {
            ITestSuite suite = createInstance();
            OptionCopier.copyOptionsNoThrow(this, suite);
            suite.mIsSharded = true;
            suite.mDirectModule = m;
            splitTests.add(suite);
        }
        // return the list of ITestSuite with their ModuleDefinition assigned
        return splitTests;
    }

    /**
     * Inject {@link ITestDevice} and {@link IBuildInfo} to the {@link IRemoteTest}s in the config
     * before sharding since they may be needed.
     */
    private void injectInfo(LinkedHashMap<String, IConfiguration> runConfig) {
        for (IConfiguration config : runConfig.values()) {
            for (IRemoteTest test : config.getTests()) {
                if (test instanceof IBuildReceiver) {
                    ((IBuildReceiver) test).setBuild(mBuildInfo);
                }
                if (test instanceof IDeviceTest) {
                    ((IDeviceTest) test).setDevice(mDevice);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /**
     * Implementation of {@link ITestSuite} may require the build info to load the tests.
     */
    public IBuildInfo getBuildInfo() {
        return mBuildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSystemStatusChecker(List<ISystemStatusChecker> systemCheckers) {
        mSystemStatusCheckers = systemCheckers;
    }

    /**
     * Run the test suite in collector only mode, this requires all the sub-tests to implements this
     * interface too.
     */
    @Override
    public void setCollectTestsOnly(boolean shouldCollectTest) {
        mCollectTestsOnly = shouldCollectTest;
    }

    /**
     * When doing distributed sharding, we cannot have ModuleDefinition that shares tests in a pool
     * otherwise intra-module sharding will not work, so we allow to disable it.
     */
    public void setShouldMakeDynamicModule(boolean dynamicModule) {
        mShouldMakeDynamicModule = dynamicModule;
    }
}
