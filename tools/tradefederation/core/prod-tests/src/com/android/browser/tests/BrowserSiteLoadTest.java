/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.browser.tests;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Base test harness class for browser site load test.
 *
 * The class can be configured to run Android Browser stability test with a preconfigured list
 * of sites pushed into ${EXTERNAL_STORAGE}/popular_urls.txt
 *
 */
public class BrowserSiteLoadTest implements IRemoteTest, IDeviceTest {

    private static final String URLS_FILE_NAME = "popular_urls.txt";
    private static final String STATUS_FILE_NAME = "test_status.txt";
    private static final String TEST_CLASS_NAME = "com.android.browser.PopularUrlsTest";
    private static final String TEST_METHOD_NAME = "testStability";
    private static final String TEST_PACKAGE_NAME = "com.android.browser.tests";
    private static final String TEST_RUNNER_NAME = "android.test.InstrumentationTestRunner";

    private static final int MAX_TIMEOUT_MS = 8 * 60 * 60 * 1000; // max test timeout is 8 hrs

    private ITestDevice mTestDevice = null;

    @Option(name = "metrics-name", description = "name used to identify the metrics for reporting",
            importance = Importance.ALWAYS)
    private String mMetricsName;

    @Option(name = "schema-key",
            description = "the schema key that number of successful loads should be reported under",
            importance = Importance.ALWAYS)
    private String mSchemaKey;

    @Option(name = "test-package",
            description = "package name of the stability test. defaults to that of AOSP Browser")
    private String mTestPackage = TEST_PACKAGE_NAME;

    @Option(name = "test-class",
            description = "class name of the stability test. defaults to that of AOSP Browser")
    private String mTestClass = TEST_CLASS_NAME;

    @Option(name = "test-method",
            description = "method name of the stability test. defaults to that of AOSP Browser")
    private String mTestMethod = TEST_METHOD_NAME;

    @Option(name = "total-sites", description = "expected total number of site loads")
    private int mTotalSites = 0;

    private String mUrlsFilePath;
    private String mStatusFilePath;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        int failCounter = 0;
        Map<String, String> finalMetrics = new HashMap<String, String>();

        preTestSetup();

        // Create and config runner for instrumentation test
        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(mTestPackage,
                TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.setClassName(mTestClass);
        runner.setMethodName(mTestClass, mTestMethod);
        // max timeout is the smaller of: 8 hrs or 1 minute per site
        runner.setMaxTimeToOutputResponse(Math.min(MAX_TIMEOUT_MS, 60 * 1000 * mTotalSites),
                TimeUnit.MILLISECONDS);

        CollectingTestListener collectingTestListener = new CollectingTestListener();
        failCounter = runStabilityTest(runner, collectingTestListener);
        finalMetrics.put(mSchemaKey, Integer.toString(mTotalSites - failCounter));

        reportMetrics(listener, mMetricsName, finalMetrics);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    /**
     * Wipes the device's external memory of test collateral from prior runs.
     *
     * @throws DeviceNotAvailableException If the device is unavailable or
     *             something happened while deleting files
     */
    private void preTestSetup() throws DeviceNotAvailableException {
        String extStore = mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        mUrlsFilePath = String.format("%s/%s", extStore, URLS_FILE_NAME);
        mStatusFilePath = String.format("%s/%s", extStore, STATUS_FILE_NAME);
        if (!mTestDevice.doesFileExist(mUrlsFilePath)) {
            throw new RuntimeException("missing URL list file at: " + mUrlsFilePath);
        }
        mTestDevice.executeShellCommand("rm " + mStatusFilePath);
    }

    /**
     * Report run metrics by creating an empty test run to stick them in.
     *
     * @param listener The {@link ITestInvocation} of test results
     * @param runName The test name
     * @param metrics The {@link Map} that contains metrics for the given test
     */
    private void reportMetrics(ITestInvocationListener listener, String runName,
            Map<String, String> metrics) {
        CLog.i(String.format("About to report metrics: loaded=%s", metrics.get(mSchemaKey)));
        listener.testRunStarted(runName, 0);
        listener.testRunEnded(0, metrics);
    }

    /**
     * Helper method for iterating through popular_urls.txt and reporting back
     * how many pages fail to load.
     *
     * @param runner The {@link IRemoteAndroidTestRunner} for instrumentation
     * @param listener The {@link CollectingTestListener} of test results
     * @return Number of pages that failed to load
     */
    private int runStabilityTest(IRemoteAndroidTestRunner runner, CollectingTestListener listener)
            throws DeviceNotAvailableException {
        boolean exitConditionMet = false;
        int failureCounter = 0;
        while (!exitConditionMet) {
            mTestDevice.runInstrumentationTests(runner, listener);
            // if the status file exists, then the previous instrumentation has crashed
            if (!mTestDevice.doesFileExist(mStatusFilePath)) {
                exitConditionMet = true;
            } else {
                ++failureCounter;
            }
        }
        return failureCounter;
    }
}
