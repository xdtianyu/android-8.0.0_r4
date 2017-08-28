/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.TestAppConstants;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.RemoteAndroidDevice;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.KeyguardControllerState;
import com.android.tradefed.util.RunUtil;

import org.easymock.EasyMock;

import java.io.IOException;

/**
 * Functional tests for {@link InstrumentationTest}.
 */
public class InstrumentationTestFuncTest extends DeviceTestCase {

    private static final String LOG_TAG = "InstrumentationTestFuncTest";
    private static final long SHELL_TIMEOUT = 2500;
    private static final int TEST_TIMEOUT = 2000;
    private static final long WAIT_FOR_DEVICE_AVAILABLE = 5 * 60 * 1000;

    /** The {@link InstrumentationTest} under test */
    private InstrumentationTest mInstrumentationTest;

    private ITestInvocationListener mMockListener;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentationTest = new InstrumentationTest();
        mInstrumentationTest.setPackageName(TestAppConstants.TESTAPP_PACKAGE);
        mInstrumentationTest.setDevice(getDevice());
        // use no timeout by default
        mInstrumentationTest.setShellTimeout(-1);
        // set to no rerun by default
        mInstrumentationTest.setRerunMode(false);
        mMockListener = EasyMock.createStrictMock(ITestInvocationListener.class);
        getDevice().disableKeyguard();
    }

    /**
     * Test normal run scenario with a single passed test result.
     */
    public void testRun() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testRun");
        TestIdentifier expectedTest = new TestIdentifier(TestAppConstants.TESTAPP_CLASS,
                TestAppConstants.PASSED_TEST_METHOD);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.PASSED_TEST_METHOD);
        mInstrumentationTest.setTestTimeout(TEST_TIMEOUT);
        mInstrumentationTest.setShellTimeout(SHELL_TIMEOUT);
        mMockListener.testRunStarted(TestAppConstants.TESTAPP_PACKAGE, 1);
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        mMockListener.testEnded(EasyMock.eq(expectedTest), EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());
        EasyMock.replay(mMockListener);
        mInstrumentationTest.run(mMockListener);
        EasyMock.verify(mMockListener);
    }

    /**
     * Test normal run scenario with a single failed test result.
     */
    public void testRun_testFailed() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testRun_testFailed");
        TestIdentifier expectedTest = new TestIdentifier(TestAppConstants.TESTAPP_CLASS,
                TestAppConstants.FAILED_TEST_METHOD);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.FAILED_TEST_METHOD);
        mInstrumentationTest.setTestTimeout(TEST_TIMEOUT);
        mInstrumentationTest.setShellTimeout(SHELL_TIMEOUT);
        mMockListener.testRunStarted(
                EasyMock.eq(TestAppConstants.TESTAPP_PACKAGE), EasyMock.anyInt());
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        mMockListener.testFailed(
                EasyMock.eq(expectedTest),
                EasyMock.contains("junit.framework.AssertionFailedError: test failed"));
        mMockListener.testEnded(EasyMock.eq(expectedTest), EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());
        EasyMock.replay(mMockListener);
        mInstrumentationTest.run(mMockListener);
        EasyMock.verify(mMockListener);
    }

    /**
     * Test run scenario where test process crashes.
     */
    public void testRun_testCrash() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testRun_testCrash");
        TestIdentifier expectedTest = new TestIdentifier(TestAppConstants.TESTAPP_CLASS,
                TestAppConstants.CRASH_TEST_METHOD);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.CRASH_TEST_METHOD);
        mInstrumentationTest.setTestTimeout(TEST_TIMEOUT);
        mInstrumentationTest.setShellTimeout(SHELL_TIMEOUT);
        mMockListener.testRunStarted(TestAppConstants.TESTAPP_PACKAGE, 1);
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        if (getDevice().getApiLevel() <= 23) {
            // Before N handling of instrumentation crash is slightly different.
            mMockListener.testFailed(
                    EasyMock.eq(expectedTest), EasyMock.contains("RuntimeException"));
            mMockListener.testEnded(EasyMock.eq(expectedTest), EasyMock.anyObject());
            mMockListener.testRunFailed(
                    EasyMock.eq("Instrumentation run failed due to 'java.lang.RuntimeException'"));
        } else {
            mMockListener.testFailed(
                    EasyMock.eq(expectedTest), EasyMock.contains("Process crashed."));
            mMockListener.testEnded(EasyMock.eq(expectedTest), EasyMock.anyObject());
            mMockListener.testRunFailed(
                    EasyMock.eq("Instrumentation run failed due to 'Process crashed.'"));
        }
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());
        EasyMock.replay(mMockListener);
        mInstrumentationTest.run(mMockListener);
        EasyMock.verify(mMockListener);
    }

    /**
     * Test run scenario where test run hangs indefinitely, and times out.
     */
    public void testRun_testTimeout() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testRun_testTimeout");
        RecoveryMode initMode = getDevice().getRecoveryMode();
        getDevice().setRecoveryMode(RecoveryMode.NONE);
        try {
            TestIdentifier expectedTest =
                    new TestIdentifier(
                            TestAppConstants.TESTAPP_CLASS, TestAppConstants.TIMEOUT_TEST_METHOD);
            mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
            mInstrumentationTest.setMethodName(TestAppConstants.TIMEOUT_TEST_METHOD);
            mInstrumentationTest.setShellTimeout(SHELL_TIMEOUT);
            mInstrumentationTest.setTestTimeout(TEST_TIMEOUT);

            mMockListener.testRunStarted(
                    EasyMock.eq(TestAppConstants.TESTAPP_PACKAGE), EasyMock.anyInt());
            mMockListener.testStarted(EasyMock.eq(expectedTest));
            mMockListener.testFailed(
                    EasyMock.eq(expectedTest),
                    EasyMock.contains(
                            String.format(
                                    "Failed to receive adb shell test output within %s ms",
                                    SHELL_TIMEOUT)));
            mMockListener.testEnded(EasyMock.eq(expectedTest), EasyMock.anyObject());
            mMockListener.testRunFailed(
                    EasyMock.contains(
                            String.format(
                                    "Failed to receive adb shell test output within %s ms",
                                    SHELL_TIMEOUT)));
            mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());
            EasyMock.replay(mMockListener);
            mInstrumentationTest.run(mMockListener);
            EasyMock.verify(mMockListener);
        } finally {
            getDevice().setRecoveryMode(initMode);
            RunUtil.getDefault().sleep(500);
        }
    }

    /**
     * Test run scenario where device reboots during test run.
     */
    public void testRun_deviceReboot() throws Exception {
        Log.i(LOG_TAG, "testRun_deviceReboot");

        TestIdentifier expectedTest = new TestIdentifier(TestAppConstants.TESTAPP_CLASS,
                TestAppConstants.TIMEOUT_TEST_METHOD);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.TIMEOUT_TEST_METHOD);

        mMockListener.testRunStarted(TestAppConstants.TESTAPP_PACKAGE, 1);
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        mMockListener.testFailed(EasyMock.eq(expectedTest), EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.eq(expectedTest), EasyMock.anyObject());
        mMockListener.testRunFailed(
                EasyMock.eq("Test run failed to complete. Expected 1 tests, received 0"));
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());
        EasyMock.replay(mMockListener);
        // fork off a thread to do the reboot
        Thread rebootThread =
                new Thread() {
                    @Override
                    public void run() {
                        // wait for test run to begin
                        try {
                            // Give time to the instrumentation to start
                            Thread.sleep(2000);
                            getDevice().reboot();
                        } catch (InterruptedException e) {
                            Log.w(LOG_TAG, "interrupted");
                        } catch (DeviceNotAvailableException dnae) {
                            Log.w(LOG_TAG, "Device did not come back online after reboot");
                        }
                    }
                };
        rebootThread.setName("InstrumentationTestFuncTest#testRun_deviceReboot");
        rebootThread.start();
        RecoveryMode initMode = getDevice().getRecoveryMode();
        getDevice().setRecoveryMode(RecoveryMode.NONE);
        try {
            mInstrumentationTest.run(mMockListener);
            // Remote device will not throw the DUE because of the different recovery path.
            if (!(getDevice() instanceof RemoteAndroidDevice)) {
                fail("Should have thrown an exception.");
            }
        } catch (DeviceUnresponsiveException expected) {
            // expected
        } finally {
            getDevice().setRecoveryMode(initMode);
        }
        rebootThread.join(WAIT_FOR_DEVICE_AVAILABLE);
        EasyMock.verify(mMockListener);
        // Give some time after device available so that keyguard disabled is picked up.
        RunUtil.getDefault().sleep(5000);
        // now we check that the keyguard is dismissed.
        KeyguardControllerState kcs = getDevice().getKeyguardState();
        if (kcs != null) {
            assertFalse("Keyguard is showing when it should not.", kcs.isKeyguardShowing());
        } else {
            assertTrue(runUITests());
        }
    }

    /**
     * Test run scenario where device runtime resets during test run.
     * <p/>
     * TODO: this test probably belongs more in TestDeviceFuncTest
     */
    public void testRun_deviceRuntimeReset() throws Exception {
        Log.i(LOG_TAG, "testRun_deviceRuntimeReset");
        TestIdentifier expectedTest = new TestIdentifier(TestAppConstants.TESTAPP_CLASS,
                TestAppConstants.TIMEOUT_TEST_METHOD);
        mInstrumentationTest.setShellTimeout(SHELL_TIMEOUT);
        mInstrumentationTest.setTestTimeout(TEST_TIMEOUT);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.TIMEOUT_TEST_METHOD);

        mMockListener.testRunStarted(
                EasyMock.eq(TestAppConstants.TESTAPP_PACKAGE), EasyMock.anyInt());
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        EasyMock.expectLastCall().anyTimes();
        mMockListener.testFailed(EasyMock.eq(expectedTest), EasyMock.anyObject());
        EasyMock.expectLastCall().anyTimes();
        mMockListener.testEnded(EasyMock.eq(expectedTest), EasyMock.anyObject());
        EasyMock.expectLastCall().anyTimes();
        mMockListener.testRunFailed(EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());

        EasyMock.replay(mMockListener);
        // fork off a thread to do the runtime reset
        Thread resetThread =
                new Thread() {
                    @Override
                    public void run() {
                        // wait for test run to begin
                        try {
                            Thread.sleep(1000);
                            Runtime.getRuntime()
                                    .exec(
                                            String.format(
                                                    "adb -s %s shell stop",
                                                    getDevice().getIDevice().getSerialNumber()));
                            Thread.sleep(500);
                            Runtime.getRuntime()
                                    .exec(
                                            String.format(
                                                    "adb -s %s shell start",
                                                    getDevice().getIDevice().getSerialNumber()));
                        } catch (InterruptedException e) {
                            Log.w(LOG_TAG, "interrupted");
                        } catch (IOException e) {
                            Log.w(LOG_TAG, "IOException when rebooting");
                        }
                    }
                };
        resetThread.setName("InstrumentationTestFuncTest#testRun_deviceRuntimeReset");
        resetThread.start();
        mInstrumentationTest.run(mMockListener);
        resetThread.join(WAIT_FOR_DEVICE_AVAILABLE);
        EasyMock.verify(mMockListener);
        RunUtil.getDefault().sleep(5000);
        getDevice().waitForDeviceAvailable();
    }

    /**
     * Run the test app UI tests and return true if they all pass.
     */
    private boolean runUITests() throws DeviceNotAvailableException {
        InstrumentationTest uiTest = new InstrumentationTest();
        uiTest.setPackageName(TestAppConstants.UITESTAPP_PACKAGE);
        uiTest.setDevice(getDevice());
        CollectingTestListener uilistener = new CollectingTestListener();
        uiTest.run(uilistener);
        return TestAppConstants.UI_TOTAL_TESTS == uilistener.getNumTestsInState(TestStatus.PASSED);
    }

    /**
     * Test running all the tests with rerun on. At least one method will cause run to stop
     * (currently TIMEOUT_TEST_METHOD and CRASH_TEST_METHOD). Verify that results are recorded for
     * all tests in the suite.
     */
    public void testRun_rerun() throws Exception {
        Log.i(LOG_TAG, "testRun_rerun");
        // run all tests in class
        RecoveryMode initMode = getDevice().getRecoveryMode();
        getDevice().setRecoveryMode(RecoveryMode.NONE);
        try {
            mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
            mInstrumentationTest.setRerunMode(true);
            mInstrumentationTest.setShellTimeout(SHELL_TIMEOUT);
            mInstrumentationTest.setTestTimeout(TEST_TIMEOUT);
            CollectingTestListener listener = new CollectingTestListener();
            mInstrumentationTest.run(listener);
            assertEquals(TestAppConstants.TOTAL_TEST_CLASS_TESTS, listener.getNumTotalTests());
            assertEquals(
                    TestAppConstants.TOTAL_TEST_CLASS_PASSED_TESTS,
                    listener.getNumTestsInState(TestStatus.PASSED));
        } finally {
            getDevice().setRecoveryMode(initMode);
        }
    }

    /**
     * Test a run that crashes when collecting tests.
     * <p/>
     * Expect run to proceed, but be reported as a run failure
     */
    public void testRun_rerunCrash() throws Exception {
        Log.i(LOG_TAG, "testRun_rerunCrash");
        mInstrumentationTest.setClassName(TestAppConstants.CRASH_ON_INIT_TEST_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.CRASH_ON_INIT_TEST_METHOD);
        mInstrumentationTest.setRerunMode(false);
        mInstrumentationTest.setShellTimeout(SHELL_TIMEOUT);
        mInstrumentationTest.setTestTimeout(TEST_TIMEOUT);
        CollectingTestListener listener = new CollectingTestListener();
        mInstrumentationTest.run(listener);
        assertEquals(0, listener.getNumTotalTests());
        assertNotNull(listener.getCurrentRunResults());
        assertEquals(TestAppConstants.TESTAPP_PACKAGE, listener.getCurrentRunResults().getName());
        assertTrue(listener.getCurrentRunResults().isRunFailure());
        assertTrue(listener.getCurrentRunResults().isRunComplete());
    }

    /**
     * Test a run that hangs when collecting tests.
     * <p/>
     * Expect a run failure to be reported
     */
    public void testRun_rerunHang() throws Exception {
        Log.i(LOG_TAG, "testRun_rerunHang");
        RecoveryMode initMode = getDevice().getRecoveryMode();
        getDevice().setRecoveryMode(RecoveryMode.NONE);
        try {
            OptionSetter setter = new OptionSetter(mInstrumentationTest);
            setter.setOptionValue("collect-tests-timeout", Long.toString(SHELL_TIMEOUT));
            mInstrumentationTest.setClassName(TestAppConstants.HANG_ON_INIT_TEST_CLASS);
            mInstrumentationTest.setRerunMode(false);
            mInstrumentationTest.setShellTimeout(SHELL_TIMEOUT);
            mInstrumentationTest.setTestTimeout(TEST_TIMEOUT);
            CollectingTestListener listener = new CollectingTestListener();
            mInstrumentationTest.run(listener);
            assertEquals(0, listener.getNumTotalTests());
            assertEquals(
                    TestAppConstants.TESTAPP_PACKAGE, listener.getCurrentRunResults().getName());
            assertTrue(listener.getCurrentRunResults().isRunFailure());
            assertTrue(listener.getCurrentRunResults().isRunComplete());
        } finally {
            getDevice().setRecoveryMode(initMode);
        }
    }
}
