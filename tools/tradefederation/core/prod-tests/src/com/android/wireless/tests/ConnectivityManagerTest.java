/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.wireless.tests;

import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.BugreportCollector;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RunUtil;

import org.junit.Assert;

import java.util.concurrent.TimeUnit;

/**
 * Run the connectivity manager functional tests. This test verifies the
 * connectivity state transition and data connectivity when the device
 * switches on different network interfaces.
 */
public class ConnectivityManagerTest implements IRemoteTest, IDeviceTest {
    private ITestDevice mTestDevice = null;

    // Define instrumentation test package and runner.
    private static final String TEST_PACKAGE_NAME =
        "com.android.connectivitymanagertest";
    private static final String TEST_RUNNER_NAME =
        ".ConnectivityManagerTestRunner";
    private static final String TEST_CLASS_NAME =
        String.format("%s.functional.ConnectivityManagerMobileTest", TEST_PACKAGE_NAME);
    private static final int TEST_TIMER = 60 * 60 * 1000;  // 1 hour

    @Option(name="ssid", description="The ssid used for wifi connection.")
    private String mSsid = null;

    @Option(name="password", description="The password used for wifi connection.")
    private String mPassword = null;

    @Option(name="method", description="Test method to run")
    private String mTestMethodName = null;

    @Option(name="wifi-only")
    private boolean mWifiOnly = false;

    @Option(name="start-sleep", description="The amount of time to sleep before starting in secs.")
    private int mStartSleepSecs = 5 * 60;

    @Override
    public void setDevice(ITestDevice testDevice) {
        mTestDevice = testDevice;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    @Override
    public void run(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        Assert.assertNotNull(mSsid);

        CLog.d("Sleeping for %d secs", mStartSleepSecs);
        RunUtil.getDefault().sleep(mStartSleepSecs * 1000);

        if (!mWifiOnly) {
            final RadioHelper radioHelper = new RadioHelper(mTestDevice);
            Assert.assertTrue("Radio activation failed", radioHelper.radioActivation());
            Assert.assertTrue("Data setup failed", radioHelper.waitForDataSetup());
        }

        // Add bugreport listener for bugreport after each test case fails
        BugreportCollector bugListener = new BugreportCollector(listener, mTestDevice);
        bugListener.addPredicate(BugreportCollector.AFTER_FAILED_TESTCASES);
        bugListener.setDescriptiveName("connectivity_manager_test");
        // Device may reboot during the test, to capture a bugreport after that,
        // wait for 30 seconds for device to be online, otherwise, bugreport will be empty
        bugListener.setDeviceWaitTime(30);

        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
                TEST_PACKAGE_NAME, TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.addInstrumentationArg("ssid", mSsid);
        if (mPassword != null) {
            runner.addInstrumentationArg("password", mPassword);
        }
        runner.setMaxTimeToOutputResponse(TEST_TIMER, TimeUnit.MILLISECONDS);
        if (mTestMethodName != null) {
            runner.setMethodName(TEST_CLASS_NAME, mTestMethodName);
        }
        if (mWifiOnly) {
            runner.addBooleanArg("wifi-only", true);
        }
        mTestDevice.runInstrumentationTests(runner, bugListener);
    }
}
