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
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RegexTrie;
import com.android.tradefed.util.StreamUtil;

import org.junit.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Run telephony stability test. The test stresses the stability of telephony by
 * putting device into sleep mode and wake up, voice connection and data connection
 * are verified after device wakeup.
 */
public class TelephonyStabilityTest implements IRemoteTest, IDeviceTest {
    private static final int TEST_TIMEOUT = 9 * 60 * 60 * 1000; // 9 hours

    // Number of iterations to skip taking bugreports for.  This is to avoid taking 1000 bugreports
    // if there are 1000 failures
    private static final int BUGREPORT_SKIP_ITERATIONS = 10;

    private static final String METRICS_NAME = "telephony_stability";
    private static final String VOICE_REGISTRATION_KEY = "voice_registration";
    private static final String VOICE_CONNECTION_KEY = "voice_call";
    private static final String DATA_REGISTRATION_KEY = "data_registration";
    private static final String DATA_CONNECTION_KEY = "data_connection";
    private static final String TEST_FAILURE_KEY = "test_failure";

    private static final String OUTPUT_FILE = "output.txt";
    // Output file in format:
    // iteration voice_registration voice_connection data_registration data_connection
    private static final Pattern OUTPUT_LINE_REGEX = Pattern.compile(
            "(\\d+) (\\d+) (\\d+) (\\d+) (\\d+)");
    private static final RegexTrie<String> OUTPUT_KEY_PATTERNS = new RegexTrie<String>();
    static {
        OUTPUT_KEY_PATTERNS.put(VOICE_REGISTRATION_KEY, "^Voice registration: (\\d+)");
        OUTPUT_KEY_PATTERNS.put(VOICE_CONNECTION_KEY, "^Voice connection: (\\d+)");
        OUTPUT_KEY_PATTERNS.put(DATA_REGISTRATION_KEY, "^Data registration: (\\d+)");
        OUTPUT_KEY_PATTERNS.put(DATA_CONNECTION_KEY, "^Data connection: (\\d+)");
    }

    // Define instrumentation test package and runner.
    private static final String TEST_PACKAGE_NAME = "com.android.phonetests";
    private static final String TEST_RUNNER_NAME = ".PhoneInstrumentationStressTestRunner";
    private static final String TEST_CLASS_NAME =
            "com.android.phonetests.stress.telephony.TelephonyStress2";
    private static final String TEST_METHOD = "testStability";

    private static final String SETTINGS_DB = "/data/data/com.android.providers.settings/databases/"
            + "settings.db";
    private static final String LOCKSCREEN_DB = "/data/system/locksettings.db";

    @Option(name="phone-number",
            description="The phone number used for outgoing call test")
    private String mPhoneNumber = null;

    @Option(name="iterations",
            description="The number of calls to make during the test")
    private int mIterations = 100;

    @Option(name="call-duration",
            description="The time of a call to be held in the test (in seconds)")
    private long mCallDurationSec = 60;

    @Option(name="suspend-duration",
            description="The time to allow device staty in suspend mode (in seconds)")
    private long mSuspendDurationSec = 120;

    @Option(name="screen-timeout",
            description="Set screen timer (in minutes)")
    private long mScreenTimeoutMin = 30;

    private ITestDevice mTestDevice = null;

    /**
     * Run the telephony stability test  and collect results
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        Assert.assertNotNull(mPhoneNumber);

        setScreenTimeout();

        final RadioHelper radioHelper = new RadioHelper(mTestDevice);
        Assert.assertTrue("Radio activation failed", radioHelper.radioActivation());
        Assert.assertTrue("Data setup failed", radioHelper.waitForDataSetup());

        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(TEST_PACKAGE_NAME,
                TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.setClassName(TEST_CLASS_NAME);
        runner.setMethodName(TEST_CLASS_NAME, TEST_METHOD);

        runner.addInstrumentationArg("phone-number", mPhoneNumber);
        runner.addInstrumentationArg("iterations", Integer.toString(mIterations));
        runner.addInstrumentationArg("call-duration", Long.toString(mCallDurationSec));
        runner.addInstrumentationArg("suspend-duration", Long.toString(mSuspendDurationSec));
        runner.setMaxTimeToOutputResponse(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        Map<String, Integer> metrics = new HashMap<String, Integer>(4);
        metrics.put(VOICE_REGISTRATION_KEY, 0);
        metrics.put(VOICE_CONNECTION_KEY, 0);
        metrics.put(DATA_REGISTRATION_KEY, 0);
        metrics.put(DATA_CONNECTION_KEY, 0);
        metrics.put(TEST_FAILURE_KEY, 0);

        int currentIteration = 0;
        Integer lastBugreportIteration = null;
        while (currentIteration < mIterations) {
            runner.addInstrumentationArg("current-iteration", Integer.toString(currentIteration));
            mTestDevice.runInstrumentationTests(runner, listener);
            mTestDevice.waitForDeviceOnline(30 * 1000);
            currentIteration = processOutputFile(currentIteration, metrics) + 1;

            // Take a bugreport if at last iteration, if we havent taken a bugreport, or if the
            // bugreport was taken more than BUGREPORT_SKIP_ITERATIONS ago.
            boolean shouldTakeReport = (currentIteration >= mIterations ||
                    lastBugreportIteration == null ||
                    (currentIteration - lastBugreportIteration) > BUGREPORT_SKIP_ITERATIONS);

            if (shouldTakeReport) {
                lastBugreportIteration = currentIteration - 1;
                InputStreamSource bugreport = mTestDevice.getBugreport();
                try {
                    listener.testLog(String.format("bugreport_%04d", lastBugreportIteration),
                            LogDataType.BUGREPORT, bugreport);
                } finally {
                    bugreport.cancel();
                }
            }

            InputStreamSource screenshot = mTestDevice.getScreenshot();
            try {
                listener.testLog(String.format("screenshot_%04d", lastBugreportIteration),
                        LogDataType.PNG, screenshot);
            } finally {
                StreamUtil.cancel(screenshot);
            }
        }
        reportMetrics(listener, metrics);
    }

    /**
     * Configure screen timeout property and lockscreen
     */
    private void setScreenTimeout() throws DeviceNotAvailableException {
        String command = String.format("sqlite3 %s \"UPDATE system SET value=\'%s\' "
                + "WHERE name=\'screen_off_timeout\';\"", SETTINGS_DB,
                mScreenTimeoutMin * 60 * 1000);
        mTestDevice.executeShellCommand(command);

        command = String.format("sqlite3 %s \"UPDATE locksettings SET value=\'1\' "
                + "WHERE name=\'lockscreen.disabled\'\"", LOCKSCREEN_DB);
        mTestDevice.executeShellCommand(command);

        command = String.format("sqlite3 %s \"UPDATE locksettings SET value=\'0\' "
                + "WHERE name=\'lockscreen.password_type\'\"", LOCKSCREEN_DB);
        mTestDevice.executeShellCommand(command);

        command = String.format("sqlite3 %s \"UPDATE locksettings SET value=\'0\' "
                + "WHERE name=\'lockscreen.password_type_alternate\'\"", LOCKSCREEN_DB);
        mTestDevice.executeShellCommand(command);

        // Set device screen_off_timeout as svc power can be set to false in the Wi-Fi test
        mTestDevice.executeShellCommand("svc power stayon false");

        // reboot to allow the setting to take effect, post setup will be taken care by the reboot
        mTestDevice.reboot();
    }

    /**
     * Process the output file, add the failure reason to the file, and return the current
     * iteration that the call failed on.
     */
    private int processOutputFile(int currentIteration, Map<String, Integer> metrics)
            throws DeviceNotAvailableException {
        final File resFile = mTestDevice.pullFileFromExternal(OUTPUT_FILE);
        BufferedReader reader = null;
        try {
            if (resFile == null) {
                CLog.w("Output file did not exist");
                metrics.put(TEST_FAILURE_KEY, metrics.get(TEST_FAILURE_KEY) + 1);
                return currentIteration;
            }
            reader = new BufferedReader(new FileReader(resFile));
            String line = getLastLine(reader);

            if (line == null) {
                CLog.w("Output file was emtpy");
                metrics.put(TEST_FAILURE_KEY, metrics.get(TEST_FAILURE_KEY) + 1);
                return currentIteration;
            }

            Matcher m = OUTPUT_LINE_REGEX.matcher(line);
            if (!m.matches()) {
                CLog.w("Output did not match the expected pattern. Line was \"%s\"", line);
                metrics.put(TEST_FAILURE_KEY, metrics.get(TEST_FAILURE_KEY) + 1);
                return currentIteration;
            }

            CLog.i("Parsed line was \"%s\"", line);

            final int recordedIteration = Integer.parseInt(m.group(1));
            metrics.put(VOICE_REGISTRATION_KEY,
                    metrics.get(VOICE_REGISTRATION_KEY) + Integer.parseInt(m.group(2)));
            metrics.put(VOICE_CONNECTION_KEY,
                    metrics.get(VOICE_CONNECTION_KEY) + Integer.parseInt(m.group(3)));
            metrics.put(DATA_REGISTRATION_KEY,
                    metrics.get(DATA_REGISTRATION_KEY) + Integer.parseInt(m.group(4)));
            metrics.put(DATA_CONNECTION_KEY,
                    metrics.get(DATA_CONNECTION_KEY) + Integer.parseInt(m.group(5)));

            return Math.max(recordedIteration, currentIteration);
        } catch (IOException e) {
            CLog.e("IOException while reading outputfile %s", resFile.getAbsolutePath());
            return currentIteration;
        } finally {
            FileUtil.deleteFile(resFile);
            StreamUtil.close(reader);
            mTestDevice.executeShellCommand(String.format("rm ${EXTERNAL_STORAGE}/%s",
                    OUTPUT_FILE));
        }
    }

    /**
     * Get the last line from a buffered reader.
     */
    private String getLastLine(BufferedReader reader) throws IOException {
        String lastLine = null;
        String currentLine;
        while ((currentLine = reader.readLine()) != null) {
            lastLine = currentLine;
        }
        return lastLine;
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     */
    private void reportMetrics(ITestInvocationListener listener, Map<String, Integer> metrics) {
        Map<String, String> reportedMetrics = new HashMap<String, String>();
        for (Map.Entry<String, Integer> entry : metrics.entrySet()) {
            final Integer keyFailures = entry.getValue();
            reportedMetrics.put(entry.getKey(), keyFailures.toString());
        }

        // Create an empty testRun to report the parsed runMetrics
        CLog.d("About to report metrics to %s: %s", METRICS_NAME, reportedMetrics);
        listener.testRunStarted(METRICS_NAME, 0);
        listener.testRunEnded(0, reportedMetrics);
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
