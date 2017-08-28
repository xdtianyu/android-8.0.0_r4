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
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.ILogRegistry.EventType;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.suite.checker.ISystemStatusCheckerReceiver;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestCollector;
import com.android.tradefed.util.StreamUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container for the test run configuration. This class is an helper to prepare and run the tests.
 */
public class ModuleDefinition implements Comparable<ModuleDefinition>, ITestCollector {

    /** key names used for saving module info into {@link IInvocationContext} */
    public static final String MODULE_NAME = "module-name";
    public static final String MODULE_ABI = "module-abi";

    private final String mId;
    private Collection<IRemoteTest> mTests = null;
    private List<ITargetPreparer> mPreparers = new ArrayList<>();
    private List<ITargetCleaner> mCleaners = new ArrayList<>();
    private IBuildInfo mBuild;
    private ITestDevice mDevice;
    private boolean mCollectTestsOnly = false;

    private List<TestRunResult> mTestsResults = new ArrayList<>();
    private int mExpectedTests = 0;
    private boolean mIsFailedModule = false;

    // Tracking of preparers performance
    private long mElapsedPreparation = 0l;
    private long mElapsedTearDown = 0l;

    private long mElapsedTest = 0l;

    public static final String PREPARATION_TIME = "PREP_TIME";
    public static final String TEAR_DOWN_TIME = "TEARDOWN_TIME";
    public static final String TEST_TIME = "TEST_TIME";

    /**
     * Constructor
     *
     * @param name unique name of the test configuration.
     * @param tests list of {@link IRemoteTest} that needs to run.
     * @param preparers list of {@link ITargetPreparer} to be used to setup the device.
     */
    public ModuleDefinition(
            String name, Collection<IRemoteTest> tests, List<ITargetPreparer> preparers) {
        mId = name;
        mTests = tests;
        for (ITargetPreparer preparer : preparers) {
            mPreparers.add(preparer);
            if (preparer instanceof ITargetCleaner) {
                mCleaners.add((ITargetCleaner) preparer);
            }
        }
        // Reverse cleaner order, so that last target_preparer to setup is first to clean up.
        Collections.reverse(mCleaners);
    }

    /**
     * Returns the next {@link IRemoteTest} from the list of tests. The list of tests of a module
     * may be shared with another one in case of sharding.
     */
    IRemoteTest poll() {
        synchronized (mTests) {
            if (mTests.isEmpty()) {
                return null;
            }
            IRemoteTest test = mTests.iterator().next();
            mTests.remove(test);
            return test;
        }
    }

    /**
     * Return True if the Module still has {@link IRemoteTest} to run in its pool. False otherwise.
     */
    protected boolean hasTests() {
        synchronized (mTests) {
            return mTests.isEmpty();
        }
    }

    /** Return the unique module name. */
    public String getId() {
        return mId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(ModuleDefinition moduleDef) {
        return getId().compareTo(moduleDef.getId());
    }

    /**
     * Inject the {@link IBuildInfo} to be used during the tests.
     */
    public void setBuild(IBuildInfo build) {
        mBuild = build;
    }

    /**
     * Inject the {@link ITestDevice} to be used during the tests.
     */
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * Run all the {@link IRemoteTest} contained in the module and use all the preparers before and
     * after to setup and clean the device.
     *
     * @param listener the {@link ITestInvocationListener} where to report results.
     * @throws DeviceNotAvailableException in case of device going offline.
     */
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        run(listener, null);
    }

    /**
     * Run all the {@link IRemoteTest} contained in the module and use all the preparers before and
     * after to setup and clean the device.
     *
     * @param listener the {@link ITestInvocationListener} where to report results.
     * @param failureListener a particular listener to collect logs on testFail. Can be null.
     * @throws DeviceNotAvailableException in case of device going offline.
     */
    public void run(ITestInvocationListener listener, TestFailureListener failureListener)
            throws DeviceNotAvailableException {
        CLog.d("Running module %s", getId());
        Exception preparationException = null;
        // Setup
        long prepStartTime = getCurrentTime();
        for (ITargetPreparer preparer : mPreparers) {
            preparationException = runPreparerSetup(preparer, listener);
            if (preparationException != null) {
                mIsFailedModule = true;
                CLog.e("Some preparation step failed. failing the module %s", getId());
                break;
            }
        }
        mElapsedPreparation = getCurrentTime() - prepStartTime;
        // Run the tests
        try {
            if (preparationException != null) {
                // For reporting purpose we create a failure placeholder with the error stack
                // similar to InitializationError of JUnit.
                TestIdentifier testid = new TestIdentifier(getId(), "PreparationError");
                listener.testRunStarted(getId(), 1);
                listener.testStarted(testid);
                StringWriter sw = new StringWriter();
                preparationException.printStackTrace(new PrintWriter(sw));
                listener.testFailed(testid, sw.toString());
                listener.testEnded(testid, Collections.emptyMap());
                listener.testRunFailed(sw.toString());
                Map<String, String> metrics = new HashMap<>();
                metrics.put(TEST_TIME, "0");
                listener.testRunEnded(0, metrics);
                return;
            }
            mElapsedTest = getCurrentTime();
            while (true) {
                IRemoteTest test = poll();
                if (test == null) {
                    return;
                }

                if (test instanceof IBuildReceiver) {
                    ((IBuildReceiver) test).setBuild(mBuild);
                }
                if (test instanceof IDeviceTest) {
                    ((IDeviceTest) test).setDevice(mDevice);
                }
                if (test instanceof ISystemStatusCheckerReceiver) {
                    // We do not pass down Status checker because they are already running at the
                    // top level suite.
                    ((ISystemStatusCheckerReceiver) test).setSystemStatusChecker(new ArrayList<>());
                }
                if (test instanceof ITestCollector) {
                    ((ITestCollector) test).setCollectTestsOnly(mCollectTestsOnly);
                }

                // Run the test, only in case of DeviceNotAvailable we exit the module
                // execution in order to execute as much as possible.
                ModuleListener moduleListener = new ModuleListener(listener);
                List<ITestInvocationListener> currentTestListener = new ArrayList<>();
                if (failureListener != null) {
                    currentTestListener.add(failureListener);
                }
                currentTestListener.add(moduleListener);
                try {
                    test.run(new ResultForwarder(currentTestListener));
                } catch (RuntimeException re) {
                    CLog.e("Module '%s' - test '%s' threw exception:", getId(), test.getClass());
                    CLog.e(re);
                    CLog.e("Proceeding to the next test.");
                } catch (DeviceUnresponsiveException due) {
                    // being able to catch a DeviceUnresponsiveException here implies that
                    // recovery was successful, and test execution should proceed to next
                    // module.
                    CLog.w(
                            "Ignored DeviceUnresponsiveException because recovery was "
                                    + "successful, proceeding with next module. Stack trace:");
                    CLog.w(due);
                    CLog.w("Proceeding to the next test.");
                } catch (DeviceNotAvailableException dnae) {
                    // We do special logging of some information in Context of the module for easier
                    // debugging.
                    CLog.e(
                            "Module %s threw a DeviceNotAvailableException on device %s during test %s",
                            getId(), mDevice.getSerialNumber(), test.getClass());
                    CLog.e(dnae);
                    // log an events
                    logDeviceEvent(
                            EventType.MODULE_DEVICE_NOT_AVAILABLE,
                            mDevice.getSerialNumber(),
                            dnae,
                            getId());
                    throw dnae;
                } finally {
                    mTestsResults.addAll(moduleListener.getRunResults());
                    mExpectedTests += moduleListener.getNumTotalTests();
                }
            }
        } finally {
            long cleanStartTime = getCurrentTime();
            try {
                // Tear down
                for (ITargetCleaner cleaner : mCleaners) {
                    CLog.d("Cleaner: %s", cleaner.getClass().getSimpleName());
                    cleaner.tearDown(mDevice, mBuild, null);
                }
            } catch (DeviceNotAvailableException tearDownException) {
                CLog.e(
                        "Module %s failed during tearDown with: %s",
                        getId(), StreamUtil.getStackTrace(tearDownException));
                throw tearDownException;
            } finally {
                mElapsedTearDown = getCurrentTime() - cleanStartTime;
                // finalize results
                if (preparationException == null) {
                    reportFinalResults(listener, mExpectedTests, mTestsResults);
                }
            }
        }
    }

    /** Helper to log the device events. */
    private void logDeviceEvent(EventType event, String serial, Throwable t, String moduleId) {
        Map<String, String> args = new HashMap<>();
        args.put("serial", serial);
        args.put("trace", StreamUtil.getStackTrace(t));
        args.put("module-id", moduleId);
        LogRegistry.getLogRegistry().logEvent(LogLevel.DEBUG, event, args);
    }

    /** Finalize results to report them all and count if there are missing tests. */
    private void reportFinalResults(
            ITestInvocationListener listener,
            int totalExpectedTests,
            List<TestRunResult> listResults) {
        long elapsedTime = 0l;
        Map<String, String> metrics = new HashMap<>();
        listener.testRunStarted(getId(), totalExpectedTests);

        int numResults = 0;
        for (TestRunResult runResult : listResults) {
            numResults += runResult.getTestResults().size();
            forwardTestResults(runResult.getTestResults(), listener);
            if (runResult.isRunFailure()) {
                listener.testRunFailed(runResult.getRunFailureMessage());
                mIsFailedModule = true;
            }
            elapsedTime += runResult.getElapsedTime();
            // put metrics from the tests
            metrics.putAll(runResult.getRunMetrics());
        }
        // put metrics from the preparation
        metrics.put(PREPARATION_TIME, Long.toString(mElapsedPreparation));
        metrics.put(TEAR_DOWN_TIME, Long.toString(mElapsedTearDown));
        metrics.put(TEST_TIME, Long.toString(elapsedTime));
        if (totalExpectedTests != numResults) {
            String error =
                    String.format(
                            "Module %s only ran %d out of %d expected tests.",
                            getId(), numResults, totalExpectedTests);
            listener.testRunFailed(error);
            CLog.e(error);
            mIsFailedModule = true;
        }
        listener.testRunEnded(getCurrentTime() - mElapsedTest, metrics);
    }

    private void forwardTestResults(
            Map<TestIdentifier, TestResult> testResults, ITestInvocationListener listener) {
        for (Map.Entry<TestIdentifier, TestResult> testEntry : testResults.entrySet()) {
            listener.testStarted(testEntry.getKey(), testEntry.getValue().getStartTime());
            switch (testEntry.getValue().getStatus()) {
                case FAILURE:
                    listener.testFailed(testEntry.getKey(), testEntry.getValue().getStackTrace());
                    break;
                case ASSUMPTION_FAILURE:
                    listener.testAssumptionFailure(
                            testEntry.getKey(), testEntry.getValue().getStackTrace());
                    break;
                case IGNORED:
                    listener.testIgnored(testEntry.getKey());
                    break;
                case INCOMPLETE:
                    listener.testFailed(
                            testEntry.getKey(), "Test did not complete due to exception.");
                    break;
                default:
                    break;
            }
            listener.testEnded(
                    testEntry.getKey(),
                    testEntry.getValue().getEndTime(),
                    testEntry.getValue().getMetrics());
        }
    }

    /** Run all the prepare steps. */
    private Exception runPreparerSetup(ITargetPreparer preparer, ITestLogger logger)
            throws DeviceNotAvailableException {
        CLog.d("Preparer: %s", preparer.getClass().getSimpleName());
        try {
            // set the logger in case they need it.
            if (preparer instanceof ITestLoggerReceiver) {
                ((ITestLoggerReceiver) preparer).setTestLogger(logger);
            }
            preparer.setUp(mDevice, mBuild);
            return null;
        } catch (BuildError | TargetSetupError e) {
            CLog.e("Unexpected Exception from preparer: %s", preparer.getClass().getName());
            CLog.e(e);
            return e;
        }
    }

    /** Returns the current time. */
    private long getCurrentTime() {
        return System.currentTimeMillis();
    }

    @Override
    public void setCollectTestsOnly(boolean collectTestsOnly) {
        mCollectTestsOnly = collectTestsOnly;
    }

    /** Returns a list of tests that ran in this module. */
    List<TestRunResult> getTestsResults() {
        return mTestsResults;
    }

    /** Returns the number of tests that was expected to be run */
    int getNumExpectedTests() {
        return mExpectedTests;
    }

    /** Returns True if a testRunFailure has been called on the module * */
    public boolean hasModuleFailed() {
        return mIsFailedModule;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return getId();
    }

    /** Returns the list of {@link ITargetPreparer} defined for this module. */
    @VisibleForTesting
    List<ITargetPreparer> getTargetPreparers() {
        return mPreparers;
    }

    /** Returns the list of {@link IRemoteTest} defined for this module. */
    @VisibleForTesting
    List<IRemoteTest> getTests() {
        return new ArrayList<>(mTests);
    }
}
