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
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import org.junit.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Run the Sms stress test. This test stresses sms message sending and receiving
 */
public class SmsStressTest implements IRemoteTest, IDeviceTest {
    private static final String TEST_PACKAGE_NAME = "com.android.messagingtests";
    private static final String TEST_RUNNER_NAME = "android.test.InstrumentationTestRunner";

    private static final String OUTPUT_PATH = "result.txt";

    private static final String ITEM_KEY = "single_thread";
    private static final String METRICS_NAME = "sms_stress";

    private static final String OUTPUT_PASSED = "passed";
    private static final String OUTPUT_FAILED = "failed";
    private static final Pattern OUTPUT_PATTERN = Pattern.compile(
            String.format("^(\\d+),(%s|%s)$", OUTPUT_PASSED, OUTPUT_FAILED));

    private static final String INSERT_COMMAND =
            "sqlite3 /data/data/com.android.providers.settings/databases/settings.db "
            + "\"INSERT INTO global (name, value) values (\'%s\',\'%s\');\"";

    private enum MessagingApp {
        HANGOUTS ("com.android.messagingtests.stress.HangoutsStressTest"),
        MESSAGING ("com.android.messagingtests.stress.MessagingStressTest"),
        MESSENGER ("com.android.messagingtests.stress.MessengerStressTest");

        private final String mTestClass;

        MessagingApp(String testClass) {
            mTestClass = testClass;
        }

        public String getTestClass() {
            return mTestClass;
        }
    }

    private ITestDevice mTestDevice = null;

    @Option(name="iterations", description="The total number of iterations to run",
            importance=Importance.ALWAYS)
    private int mIterations = 100;

    @Option(name="phone-number", description="The phone number to use")
    private String mPhoneNumber = null;

    @Option(name="app", description="The default messaging app on the device",
            importance=Importance.IF_UNSET, mandatory=true)
    private MessagingApp mApp = null;

    /**
     * Configure device with special settings
     */
    private void setupDevice() throws DeviceNotAvailableException {
        String command = String.format(
                INSERT_COMMAND, "sms_outgoing_check_max_count", "20000");
        CLog.d("Command to set sms_outgoing_check_max_count: %s", command);
        mTestDevice.executeShellCommand(command);

        // reboot to allow the setting to take effect
        // post setup will be taken care by the reboot
        mTestDevice.reboot();
    }

    /**
     * Run messaging stress test and parse test results
     */
    @Override
    public void run(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        setupDevice();

        final RadioHelper radioHelper = new RadioHelper(mTestDevice);
        Assert.assertTrue("Radio activation failed", radioHelper.radioActivation());
        Assert.assertTrue("Data setup failed", radioHelper.waitForDataSetup());

        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
                TEST_PACKAGE_NAME, TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.setClassName(mApp.getTestClass());
        runner.addInstrumentationArg("iterations", Integer.toString(mIterations));
        if (mPhoneNumber != null) {
            runner.addInstrumentationArg("phone_number", mPhoneNumber);
        }
        mTestDevice.runInstrumentationTests(runner, listener);

        InputStreamSource screenshot = mTestDevice.getScreenshot();
        try {
            listener.testLog(String.format("screenshot"),
                    LogDataType.PNG, screenshot);
        } finally {
            StreamUtil.cancel(screenshot);
        }

        parseOutput(listener);
    }

    /**
     * Collect test results and report test results.
     */
    private void parseOutput(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        File outputFile = null;
        InputStreamSource outputSource = null;
        BufferedReader outputReader = null;
        Integer iterations = null;

        try {
            outputFile = mTestDevice.pullFileFromExternal(OUTPUT_PATH);
            if (outputFile != null) {
                CLog.d("Sending %d byte file %s into the logosphere!",
                        outputFile.length(), outputFile);
                outputSource = new FileInputStreamSource(outputFile);
                listener.testLog("sms_stress_output", LogDataType.TEXT, outputSource);

                outputReader = new BufferedReader(new FileReader(outputFile));
                String line = null;
                while ((line = outputReader.readLine()) != null) {
                    Matcher m = OUTPUT_PATTERN.matcher(line);
                    if (m.matches()) {
                        if (OUTPUT_PASSED.equals(m.group(2))) {
                            iterations = Integer.parseInt(m.group(1));
                        }
                    } else {
                        CLog.w("Line '%s' did not match output pattern", line);
                    }
                }
            }

            Map<String, String> metrics = new HashMap<>();
            metrics.put(ITEM_KEY, Integer.toString(iterations == null ? 0 : iterations + 1));
            reportMetrics(METRICS_NAME, listener, metrics);
        } catch (IOException e) {
            CLog.e("IOException parsing output file");
            CLog.e(e);
            Assert.fail("IOException parsing output file");
        } finally {
            FileUtil.deleteFile(outputFile);
            StreamUtil.cancel(outputSource);
            StreamUtil.close(outputReader);
        }
    }


    /**
     * Report run metrics by creating an empty test run to stick them in
     */
    private void reportMetrics(String metricsName, ITestInvocationListener listener,
            Map<String, String> metrics) {
        // Create an empty testRun to report the parsed runMetrics
        CLog.d("About to report metrics to %s: %s", metricsName, metrics);
        listener.testRunStarted(metricsName, 0);
        listener.testRunEnded(0, metrics);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice testDevice) {
        mTestDevice = testDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }
}
