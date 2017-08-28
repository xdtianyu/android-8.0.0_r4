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
 * Run radio outgoing call stress test. The test stresses the voice connection when making
 * outgoing calls, number of failures will be collected and reported.
 */
public class TelephonyTest implements IRemoteTest, IDeviceTest {
    private static final int TEST_TIMEOUT = 8 * 60 * 60 * 1000; // 8 hours

    private static final String OUTPUT_FILE = "output.txt";
    // Output file in format: iteration [failure_reason]
    private static final Pattern OUTPUT_LINE_REGEX = Pattern.compile("(\\d+)( (\\d+))?");

    // Define metrics for result report
    private static final String METRICS_NAME = "PhoneVoiceConnectionStress";
    private static final String SUCCESS_KEY = "SuccessfulCall";
    private static final String TEST_FAILURE_KEY = "TestFailure";
    private static final String[] FAILURE_KEYS = {"CallActiveFailure", "CallDisconnectionFailure",
        "HangupFailure", "ServiceStateChange", TEST_FAILURE_KEY};

    // Define instrumentation test package and runner.
    private static final String TEST_PACKAGE_NAME = "com.android.phonetests";
    private static final String TEST_RUNNER_NAME = ".PhoneInstrumentationStressTestRunner";
    private static final String TEST_CLASS_NAME =
        "com.android.phonetests.stress.telephony.TelephonyStress2";
    public static final String TEST_METHOD = "testOutgoingCalls";

    @Option(name="phone-number",
            description="The phone number used for outgoing call test")
    private String mPhoneNumber = null;

    @Option(name="iterations",
            description="The number of calls to make during the test")
    private int mIterations = 1000;

    @Option(name="call-duration",
            description="The time of a call to be held in the test (in seconds)")
    private long mCallDurationSec = 5;

    @Option(name="start-pause-duration",
            description="The time to wait before starting tests (in seconds)")
    private long mStartPauseDurationSec = 2;

    private ITestDevice mTestDevice = null;

    /**
     * Run the telephony outgoing call stress test
     * Collect results and post results to dash board
     */
    @Override
    public void run(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        Assert.assertNotNull(mPhoneNumber);

        final RadioHelper radioHelper = new RadioHelper(mTestDevice);
        Assert.assertTrue("Radio activation failed", radioHelper.radioActivation());
        Assert.assertTrue("Data setup failed", radioHelper.waitForDataSetup());

        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(TEST_PACKAGE_NAME,
                TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.setClassName(TEST_CLASS_NAME);
        runner.setMethodName(TEST_CLASS_NAME, TEST_METHOD);

        runner.addInstrumentationArg("phone-number", mPhoneNumber);
        runner.addInstrumentationArg("iterations", Integer.toString(mIterations));
        runner.addInstrumentationArg("current-iteration", Integer.toString(0));
        runner.addInstrumentationArg("call-duration", Long.toString(mCallDurationSec));
        runner.addInstrumentationArg("start-pause-duration", Long.toString(mStartPauseDurationSec));
        runner.setMaxTimeToOutputResponse(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        mTestDevice.runInstrumentationTests(runner, listener);

        Map<String, Integer> metrics = new HashMap<String, Integer>(4);
        for (String key : FAILURE_KEYS) {
            metrics.put(key, 0);
        }
        int successfulIterations = processOutputFile(metrics);
        metrics.put(SUCCESS_KEY, successfulIterations);

        if (successfulIterations < mIterations) {
            mTestDevice.waitForDeviceOnline(30 * 1000);
            InputStreamSource bugreport = mTestDevice.getBugreport();
            InputStreamSource screenshot = mTestDevice.getScreenshot();
            try {
                listener.testLog(String.format("bugreport_%s", successfulIterations),
                        LogDataType.BUGREPORT, bugreport);
                listener.testLog(String.format("screenshot_%s", successfulIterations),
                        LogDataType.PNG, screenshot);
            } finally {
                StreamUtil.cancel(bugreport);
                StreamUtil.cancel(screenshot);
            }
        }

        reportMetrics(listener, metrics);
    }

    /**
     * Process the output file, add the failure reason to the file, and return the number of
     * successful iterations.
     */
    private int processOutputFile(Map<String, Integer> failures)
            throws DeviceNotAvailableException {
        final File resFile = mTestDevice.pullFileFromExternal(OUTPUT_FILE);
        BufferedReader reader = null;
        try {
            if (resFile == null) {
                CLog.w("Output file did not exist");
                failures.put(TEST_FAILURE_KEY, failures.get(TEST_FAILURE_KEY) + 1);
                return 0;
            }
            reader = new BufferedReader(new FileReader(resFile));
            String line = getLastLine(reader);

            if (line == null) {
                CLog.w("Output file was emtpy");
                failures.put(TEST_FAILURE_KEY, failures.get(TEST_FAILURE_KEY) + 1);
                return 0;
            }

            Matcher m = OUTPUT_LINE_REGEX.matcher(line);
            if (!m.matches()) {
                CLog.w("Output did not match the expected pattern. Line was \"%s\"", line);
                failures.put(TEST_FAILURE_KEY, failures.get(TEST_FAILURE_KEY) + 1);
                return 0;
            }

            final int recordedIteration = Integer.parseInt(m.group(1));
            if (m.group(3) != null) {
                final int failureCode = Integer.parseInt(m.group(3));
                final String key = FAILURE_KEYS[failureCode];
                failures.put(key, 1);
                return recordedIteration;
            }

            return recordedIteration + 1;
        } catch (IOException e) {
            CLog.e("IOException while reading outputfile %s", resFile.getAbsolutePath());
            failures.put(TEST_FAILURE_KEY, failures.get(TEST_FAILURE_KEY) + 1);
            return 0;
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
            reportedMetrics.put(entry.getKey(), Integer.toString(entry.getValue()));
        }

        // Create an empty testRun to report the parsed runMetrics
        CLog.d("About to report metrics to %s: %s", METRICS_NAME, reportedMetrics);
        listener.testRunStarted(METRICS_NAME, 0);
        listener.testRunEnded(0, reportedMetrics);
    }

    @Override
    public void setDevice(ITestDevice testDevice) {
        mTestDevice = testDevice;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }
}
