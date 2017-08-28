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

package com.android.bluetooth.tests;

import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.BackgroundDeviceAction;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.LargeOutputReceiver;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.BugreportCollector;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.BluetoothUtils;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import junit.framework.TestCase;

import org.junit.Assert;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the Bluetooth stress testcases.
 * FIXME: more details on what the testcases do
 */
public class BluetoothStressTest implements IDeviceTest, IRemoteTest {
    ITestDevice mTestDevice = null;

    // Constants for running the tests
    private static final String TEST_RUNNER_NAME = "android.bluetooth.BluetoothTestRunner";
    private static final String HCIDUMP_CMD = "hcidump -Xt";
    private static final String HCIDUMP_DESC = "hcidump";
    private static final long HCIDUMP_LOG_SIZE = 4 * 1024 * 1024; // 4 MB.

    /**
     * Generic string for running the instrumentation on the second device. Completed with:
     * <p/>
     * {@code String.format(INSTRUCTIONS_INSTRUMENT_CMD, remoteSerial, localAddress, iterKey,
     * iterCount, testName);}
     * <p/>
     * where {@code remoteSerial} is the device on which you want to run the instrumentation,
     * {@code localAddress} is the bluetooth address of the DUT, {@code iterKey} is the key for the
     * number of iterations, {@code iterCount} is the number of iterations to run, and
     * {@code testName} is the test method to run.
     */
    private static final String INSTRUCTIONS_INSTRUMENT_CMD = (
            "adb -s %s shell am instrument -w -r -e device_address %s -e %s %d -e class %s#%s "
            + "%s/%s");

    private static final Pattern ITERATION_PATTERN =
            Pattern.compile("\\S+ iteration (\\d+) of (\\d+)");

    /**
     * Matches {@code methodName(arg1=arg1, arg2=arg2) completed} where {@code args} are
     * additional information used for debugging the logs.
     */
    private static final String METHOD_COMPLETED_STR = "((?:\\w|-)+)\\((?:.*)\\) completed";
    private static final Pattern PERF_PATTERN =
            Pattern.compile(METHOD_COMPLETED_STR + " in (\\d+) ms");
    private static final Pattern METHOD_PATTERN = Pattern.compile(METHOD_COMPLETED_STR + ".*");

    private static final String OUTPUT_PATH = "BluetoothStressTestOutput.txt";

    /**
     * Stores the test cases that we should consider running.
     * <p/>
     * This currently includes "discoverable", "enable", and "scan"
     */
    private List<TestInfo> mTestCases = null;

    /**
     * A struct that contains useful info about the tests to run
     */
    static class TestInfo {
        public String mTestName = null;
        public String mTestMethod = null;
        public String mIterKey = null;
        public Integer mIterCount = null;
        public Set<String> mPerfMetrics = new HashSet<>();

        // Arguments for remote device tests
        public boolean mRemoteDeviceTest = false;
        public String mRemoteAddress = null;
        public String mPairPin = null;
        public String mPairPasskey = null;
        public String mInstructions = null;

        public String getTestMetricsName() {
            if (mTestName == null) {
                return null;
            }
            return String.format("bt_%s_stress", mTestName);
        }

        @Override
        public String toString() {
            String perfMetrics = null;
            if (mPerfMetrics != null) {
                perfMetrics = mPerfMetrics.toString();
            }
            return String.format("TestInfo: method(%s) iters(%s) metrics(%s)", mTestMethod,
                    mIterCount, perfMetrics);
        }
    }

    private class HcidumpCommand {
        LargeOutputReceiver mReceiver = null;
        BackgroundDeviceAction mDeviceAction = null;

        public HcidumpCommand() {
            mReceiver = new  LargeOutputReceiver(HCIDUMP_DESC, mTestDevice.getSerialNumber(),
                    HCIDUMP_LOG_SIZE);
            mDeviceAction = new BackgroundDeviceAction(HCIDUMP_CMD, HCIDUMP_DESC, mTestDevice,
                    mReceiver, 0);
        }

        public void startHcidump() {
            mDeviceAction.start();
        }

        public void stopHcidump() {
            mDeviceAction.cancel();
            mReceiver.cancel();
            mReceiver.delete();
        }

        public InputStreamSource getHcidump() {
            return mReceiver.getData();
        }
    }
    private HcidumpCommand mHcidumpCommand = null;

    @Option(name="test-class-name",
            description="The test class name of the Bluetooth stress tests")
    private String mTestClassName = "android.bluetooth.BluetoothStressTest";

    @Option(name="test-package-name",
            description="The test package name of the Bluetooth stress tests")
    private String mTestPackageName = "com.android.bluetooth.tests";

    @Option(name="discoverable-iterations",
            description="Number of iterations to run for the 'discoverable' test.")
    private Integer mDiscoverableIterations = null;

    @Option(name="enable-iterations",
            description="Number of iterations to run for the 'enable' test.")
    private Integer mEnableIterations = null;

    @Option(name="scan-iterations",
            description="Number of iterations to run for the 'scan' test.")
    private Integer mScanIterations = null;

    @Option(name="enable-pan-iterations",
            description="Number of iterations to run for the 'enable_pan' test.")
    private Integer mEnablePanIterations = null;

    @Option(name="pair-iterations",
            description="Number of iterations to run for the 'pair' test.")
    private Integer mPairIterations = 0;

    @Option(name="accept-pair-iterations",
            description="Number of iterations to run for the 'accept_pair' test.")
    private Integer mAcceptPairIterations = 0;

    @Option(name="connect-headset-iterations",
            description="Number of iterations to run for the 'connect_headset' test.")
    private Integer mConnectHeadsetIterations = 0;

    @Option(name="connect-a2dp-iterations",
            description="Number of iterations to run for the 'connect_a2dp' test.")
    private Integer mConnectA2dpIterations = 0;

    @Option(name="connect-input-iterations",
            description="Number of iterations to run for the 'connect_input' test.")
    private Integer mConnectInputIterations = 0;

    @Option(name="connect-pan-iterations",
            description="Number of iterations to run for the 'connect_pan' test")
    private Integer mConnectPanIterations = 0;

    @Option(name="incoming-pan-connection-iterations",
            description="Number of iterations to run for the 'incoming_pan_connection' test.")
    private Integer mIncomingPanConnectionIterations = 0;

    @Option(name="start-stop-sco-iterations",
            description="Number of iterations to run for the 'start_stop_sco' test.")
    private Integer mStartStopScoIterations = 0;

    @Option(name="local-address",
            description="Address for the local Android device.")
    private String mLocalAddress = null;

    @Option(name="device-serial",
            description="Serial number for the remote Android device.")
    private String mDeviceSerial = null;

    @Option(name="device-address",
            description="Address for the remote Android device.")
    private String mDeviceAddress = null;

    @Option(name="device-pair-pin",
            description="Pair pin for the remote Android device.")
    private String mDevicePairPin = null;

    @Option(name="device-pair-passkey",
            description="Pair passkey for the remote Android device.")
    private String mDevicePairPasskey = null;

    @Option(name="headset-address",
            description="Address for the headset device.")
    private String mHeadsetAddress = null;

    @Option(name="headset-pair-pin",
            description="Pair pin for the headset device.")
    private String mHeadsetPairPin = null;

    @Option(name="headset-pair-passkey",
            description="Pair passkey for the headset device.")
    private String mHeadsetPairPasskey = null;

    @Option(name="a2dp-address",
            description="Remote device address for the A2DP device.")
    private String mA2dpAddress = null;

    @Option(name="a2dp-pair-pin",
            description="Pair pin for the A2DP device.")
    private String mA2dpPairPin = null;

    @Option(name="a2dp-pair-passkey",
            description="Pair passkey for the A2DP device.")
    private String mA2dpPairPasskey = null;

    @Option(name="input-address",
            description="Remote device address for the input device.")
    private String mInputAddress = null;

    @Option(name="input-pair-pin",
            description="Pair pin for the input device.")
    private String mInputPairPin = null;

    @Option(name="input-pair-passkey",
            description="Pair passkey for the input device.")
    private String mInputPairPasskey = null;

    @Option(name="log-btsnoop", description="Record the btsnoop trace. Works with the bluedroid " +
            "stack.")
    private boolean mLogBtsnoop = true;

    @Option(name="log-hcidump", description="Record the hcidump data. Works with the bluez stack.")
    private boolean mLogHcidump = false;

    @Option(name = "test-timeout", description =
            "Maximum time in ms that an individual test is allowed to run.")
    private int mTestTimeout = 20 * 60 * 1000; // 20 minutes

    private void setupTests() {
        if (mTestCases != null) {
            // assume already set up
            return;
        }
        // Allocate enough space for all of the TestInfo instances below
        mTestCases = new ArrayList<>(12);

        TestInfo t = new TestInfo();
        t.mTestName = "discoverable";
        t.mTestMethod = "testDiscoverable";
        t.mIterKey = "discoverable_iterations";
        t.mIterCount = mDiscoverableIterations;
        t.mPerfMetrics.add("discoverable");
        t.mPerfMetrics.add("undiscoverable");
        mTestCases.add(t);

        t = new TestInfo();
        t.mTestName = "enable";
        t.mTestMethod = "testEnable";
        t.mIterKey = "enable_iterations";
        t.mIterCount = mEnableIterations;
        t.mPerfMetrics.add("enable");
        t.mPerfMetrics.add("disable");
        mTestCases.add(t);

        t = new TestInfo();
        t.mTestName = "scan";
        t.mTestMethod = "testScan";
        t.mIterKey = "scan_iterations";
        t.mIterCount = mScanIterations;
        t.mPerfMetrics.add("startScan");
        t.mPerfMetrics.add("stopScan");
        mTestCases.add(t);

        t = new TestInfo();
        t.mTestName = "enable_pan";
        t.mTestMethod = "testEnablePan";
        t.mIterKey = "enable_pan_iterations";
        t.mIterCount = mEnablePanIterations;
        t.mPerfMetrics.add("enablePan");
        t.mPerfMetrics.add("disablePan");
        mTestCases.add(t);

        // Remote device tests
        t = new TestInfo();
        t.mTestName = "pair";
        t.mTestMethod = "testPair";
        t.mIterKey = "pair_iterations";
        t.mIterCount = mPairIterations;
        t.mPerfMetrics.add("pair");
        t.mPerfMetrics.add("unpair");
        t.mRemoteAddress = mDeviceAddress;
        t.mPairPin = mDevicePairPin;
        t.mPairPasskey = mDevicePairPasskey;
        t.mInstructions = String.format("Start the testAcceptPair instrumentation on the remote "
                + "Android device with the following command:\n\n%s\n\nHit Enter when done.",
                String.format(INSTRUCTIONS_INSTRUMENT_CMD, mDeviceSerial, mLocalAddress,
                        "pair_iterations", mPairIterations, mTestClassName, "testAcceptPair",
                        mTestPackageName, TEST_RUNNER_NAME));
        mTestCases.add(t);

        t = new TestInfo();
        t.mTestName = "accept_pair";
        t.mTestMethod = "testAcceptPair";
        t.mIterKey = "pair_iterations";
        t.mIterCount = mAcceptPairIterations;
        t.mPerfMetrics.add("acceptPair");
        t.mPerfMetrics.add("unpair");
        t.mRemoteAddress = mDeviceAddress;
        t.mPairPin = mDevicePairPin;
        t.mPairPasskey = mDevicePairPasskey;
        t.mInstructions = String.format("Start the testPair instrumentation on the remote "
                + "Android device with the following command:\n\n%s\n\nHit Enter when done.",
                String.format(INSTRUCTIONS_INSTRUMENT_CMD, mDeviceSerial, mLocalAddress,
                        "pair_iterations", mAcceptPairIterations, mTestClassName, "testPair",
                        mTestPackageName, TEST_RUNNER_NAME));
        mTestCases.add(t);

        t = new TestInfo();
        t.mTestName = "connect_headset";
        t.mTestMethod = "testConnectHeadset";
        t.mIterKey = "connect_headset_iterations";
        t.mIterCount = mConnectHeadsetIterations;
        t.mPerfMetrics.add("connectHeadset");
        t.mPerfMetrics.add("disconnectHeadset");
        t.mRemoteAddress = mHeadsetAddress;
        t.mPairPin = mHeadsetPairPin;
        t.mPairPasskey = mHeadsetPairPasskey;
        t.mInstructions = "Put the remote headset device in pairing mode. Hit Enter when done.";
        mTestCases.add(t);

        t = new TestInfo();
        t.mTestName = "connect_a2dp";
        t.mTestMethod = "testConnectA2dp";
        t.mIterKey = "connect_a2dp_iterations";
        t.mIterCount = mConnectA2dpIterations;
        t.mPerfMetrics.add("connectA2dp");
        t.mPerfMetrics.add("disconnectA2dp");
        t.mRemoteAddress = mA2dpAddress;
        t.mPairPin = mA2dpPairPin;
        t.mPairPasskey = mA2dpPairPasskey;
        t.mInstructions = "Put the remote A2DP device in pairing mode. Hit Enter when done.";
        mTestCases.add(t);

        t = new TestInfo();
        t.mTestName = "connect_input";
        t.mTestMethod = "testConnectInput";
        t.mIterKey = "connect_input_iterations";
        t.mIterCount = mConnectInputIterations;
        t.mPerfMetrics.add("connectInput");
        t.mPerfMetrics.add("disconnectInput");
        t.mRemoteAddress = mInputAddress;
        t.mPairPin = mInputPairPin;
        t.mPairPasskey = mInputPairPasskey;
        t.mInstructions = "Put the remote input device in pairing mode. Hit Enter when done.";
        mTestCases.add(t);

        t = new TestInfo();
        t.mTestName = "connect_pan";
        t.mTestMethod = "testConnectPan";
        t.mIterKey = "connect_pan_iterations";
        t.mIterCount = mConnectPanIterations;
        t.mPerfMetrics.add("connectPan");
        t.mPerfMetrics.add("disconnectPan");
        t.mRemoteAddress = mDeviceAddress;
        t.mPairPin = mDevicePairPin;
        t.mPairPasskey = mDevicePairPasskey;
        t.mInstructions = String.format("Start the testIncomingPanConnection instrumentation on "
                + "the remote Android device with the following command:\n\n%s\n\nHit Enter when "
                + "done.",
                String.format(INSTRUCTIONS_INSTRUMENT_CMD, mDeviceSerial, mLocalAddress,
                        "connect_pan_iterations", mConnectPanIterations, mTestClassName,
                        "testIncomingPanConnection", mTestPackageName, TEST_RUNNER_NAME));
        mTestCases.add(t);

        t = new TestInfo();
        t.mTestName = "incoming_pan_connection";
        t.mTestMethod = "testIncomingPanConnection";
        t.mIterKey = "connect_pan_iterations";
        t.mIterCount = mIncomingPanConnectionIterations;
        t.mPerfMetrics.add("incomingPanConnection");
        t.mPerfMetrics.add("incomingPanDisconnection");
        t.mRemoteAddress = mDeviceAddress;
        t.mPairPin = mDevicePairPin;
        t.mPairPasskey = mDevicePairPasskey;
        t.mInstructions = ("Start the testConnectPan instrumentation on the remote Android " +
                "device. Hit Enter when done.");
        t.mInstructions = String.format("Start the testConnectPan instrumentation on the remote "
                + "Android device with the following command:\n\n%s\n\nHit Enter when done.",
                String.format(INSTRUCTIONS_INSTRUMENT_CMD, mDeviceSerial, mLocalAddress,
                        "connect_pan_iterations",  mIncomingPanConnectionIterations, mTestClassName,
                        "testConnectPan", mTestPackageName, TEST_RUNNER_NAME));
        mTestCases.add(t);

        t = new TestInfo();
        t.mTestName = "start_stop_sco";
        t.mTestMethod = "testStartStopSco";
        t.mIterKey = "start_stop_sco_iterations";
        t.mIterCount = mStartStopScoIterations;
        t.mPerfMetrics.add("startSco");
        t.mPerfMetrics.add("stopSco");
        t.mRemoteAddress = mHeadsetAddress;
        t.mPairPin = mHeadsetPairPin;
        t.mPairPasskey = mHeadsetPairPasskey;
        t.mInstructions = "Put the remote headset device in pairing mode. Hit Enter when done.";
        mTestCases.add(t);
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        setupTests();

        if (mLogBtsnoop) {
            if (!BluetoothUtils.enableBtsnoopLogging(mTestDevice)) {
                CLog.e("Unable to enable btsnoop trace logging");
                throw new DeviceNotAvailableException();
            }
        }

        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(mTestPackageName,
                TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.setClassName(mTestClassName);
        runner.setMaxTimeToOutputResponse(mTestTimeout, TimeUnit.MILLISECONDS);
        BugreportCollector bugListener = new BugreportCollector(listener, mTestDevice);
        bugListener.addPredicate(BugreportCollector.AFTER_FAILED_TESTCASES);

        for (TestInfo test : mTestCases) {
            String testName = test.mTestName;
            TestInfo t = test;

            if (t.mIterCount != null && t.mIterCount <= 0) {
                CLog.i("Cancelled '%s' test case with iter count %s", testName, t.mIterCount);
                continue;
            }

            // For semi-manual tests, print instructions and wait for user to continue.
            if (t.mInstructions != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                System.out.println("========================================");
                System.out.println(t.mInstructions);
                System.out.println("========================================");
                try {
                    br.readLine();
                } catch (IOException e) {
                    CLog.e("Continuing after IOException while waiting for confirmation: %s",
                            e.getMessage());
                }
            }

            // Run the test
            cleanOutputFile();
            if (mLogHcidump) {
                mHcidumpCommand = new HcidumpCommand();
                mHcidumpCommand.startHcidump();
            }

            if (t.mIterKey != null && t.mIterCount != null) {
                runner.addInstrumentationArg(t.mIterKey, t.mIterCount.toString());
            }
            if (t.mRemoteAddress != null) {
                runner.addInstrumentationArg("device_address", t.mRemoteAddress);
                if (t.mPairPasskey != null) {
                    runner.addInstrumentationArg("device_pair_passkey", t.mPairPasskey);
                }
                if (t.mPairPin != null) {
                    runner.addInstrumentationArg("device_pair_pin", t.mPairPin);
                }
            }
            runner.setMethodName(mTestClassName, t.mTestMethod);
            bugListener.setDescriptiveName(testName);
            mTestDevice.runInstrumentationTests(runner, bugListener);
            if (t.mIterKey != null && t.mIterCount != null) {
                runner.removeInstrumentationArg(t.mIterKey);
            }
            if (t.mRemoteAddress != null) {
                runner.removeInstrumentationArg("device_address");
                if (t.mPairPasskey != null) {
                    runner.removeInstrumentationArg("device_pair_passkey");
                }
                if (t.mPairPin != null) {
                    runner.removeInstrumentationArg("device_pair_pin");
                }
            }

            // Log the output file
            logOutputFile(t, listener);
            if (mLogBtsnoop) {
                BluetoothUtils.uploadLogFiles(listener, getDevice(), t.mTestName, 0);
            }
            if (mLogHcidump) {
                listener.testLog(String.format("%s_hcidump", t.mTestName), LogDataType.TEXT,
                        mHcidumpCommand.getHcidump());
                mHcidumpCommand.stopHcidump();
                mHcidumpCommand = null;
            }
            cleanOutputFile();
        }
    }

    /**
     * Clean up the tmp output file from previous test runs
     */
    private void cleanOutputFile() throws DeviceNotAvailableException {
        mTestDevice.executeShellCommand(String.format("rm ${EXTERNAL_STORAGE}/%s", OUTPUT_PATH));
        if (mLogBtsnoop) {
            BluetoothUtils.cleanLogFile(mTestDevice);
        }
    }

    /**
     * Pull the output file from the device, add it to the logs, and also parse out the relevant
     * test metrics and report them.
     */
    private void logOutputFile(TestInfo testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        File outputFile = null;
        InputStreamSource outputSource = null;
        try {
            outputFile = mTestDevice.pullFileFromExternal(OUTPUT_PATH);

            if (outputFile != null) {
                CLog.d("Sending %d byte file %s into the logosphere!",
                        outputFile.length(), outputFile);
                outputSource = new FileInputStreamSource(outputFile);
                listener.testLog(String.format("%s_output", testInfo.mTestName),
                        LogDataType.TEXT, outputSource);
                parseOutputFile(testInfo, new FileInputStream(outputFile), listener);
            }
        } catch (IOException e) {
            CLog.e(e);
        } finally {
            FileUtil.deleteFile(outputFile);
            StreamUtil.cancel(outputSource);
        }
    }

    /**
     * Parse the relevant metrics from the Instrumentation test output file
     */
    private void parseOutputFile(TestInfo testInfo, InputStream dataStream,
            ITestInvocationListener listener) {
        // Read output file contents into memory
        String contents;
        try {
            dataStream = new BufferedInputStream(dataStream);
            contents = StreamUtil.getStringFromStream(dataStream);
        } catch (IOException e) {
            CLog.e("IOException while parsing the output file:");
            CLog.e(e);
            return;
        }

        List<String> lines = Arrays.asList(contents.split("\n"));
        ListIterator<String> lineIter = lines.listIterator();
        String line;
        Integer iterCount = null;
        Map<String, List<Integer>> perfData = new HashMap<>();
        Map<String, Integer> iterData = new HashMap<>();

        // Iterate through each line of output
        while (lineIter.hasNext()) {
            line = lineIter.next();

            Matcher m = ITERATION_PATTERN.matcher(line);
            if (m.matches()) {
                iterCount = Integer.parseInt(m.group(1));
                continue;
            }

            if (iterCount == null) {
                continue;
            }

            m = PERF_PATTERN.matcher(line);
            if (m.matches()) {
                String method = m.group(1);
                int time = Integer.parseInt(m.group(2));
                if (!perfData.containsKey(method)) {
                    perfData.put(method, new LinkedList<Integer>());
                }
                perfData.get(method).add(time);
            }

            m = METHOD_PATTERN.matcher(line);
            if (m.matches()) {
                String method = m.group(1);
                if (!iterData.containsKey(method)) {
                    iterData.put(method, 1);
                } else {
                    iterData.put(method, iterData.get(method).intValue() + 1);
                }
            }
        }

        // Coalesce the parsed values into metrics that we can report
        if (iterCount == null) {
            iterCount = 0;
        }

        Map<String, String> runMetrics = new HashMap<>();
        for (String metric : testInfo.mPerfMetrics) {
            if (perfData.containsKey(metric)) {
                List<Integer> values = perfData.get(metric);
                String key = String.format("performance_%s_mean", metric);
                runMetrics.put(key, Float.toString(mean(values)));
            }

            if (iterData.containsKey(metric)) {
                Integer iters = iterData.get(metric);
                iterCount = min(iterCount, iters);
            } else {
                iterCount = 0;
            }
        }
        runMetrics.put("iterations", iterCount.toString());

        // And finally, report the coalesced metrics
        reportMetrics(listener, testInfo, runMetrics);
    }

    private static Integer min(Integer x, Integer y) {
        if (x == null || x.compareTo(y) > 0) {
            return y;
        } else {
            return x;
        }
    }

    private static float mean(List<Integer> values) {
        int sum = 0;
        for (Integer n : values) {
            sum += n;
        }
        return (float)sum / values.size();
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     * <p />
     * Exposed for unit testing
     */
    void reportMetrics(ITestInvocationListener listener, TestInfo test,
            Map<String, String> metrics) {
        // Create an empty testRun to report the parsed runMetrics
        CLog.d("About to report metrics to %s: %s", test.getTestMetricsName(), metrics);
        listener.testRunStarted(test.getTestMetricsName(), 0);
        listener.testRunEnded(0, metrics);
    }


    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    /**
     * A meta-test to ensure that bits of the BluetoothStressTest are working properly
     */
    public static class MetaTest extends TestCase {
        private BluetoothStressTest mTestInstance = null;

        private TestInfo mScanInfo = null;

        private TestInfo mReportedTestInfo = null;
        private Map<String, String> mReportedMetrics = null;

        private static String join(String... pieces) {
            StringBuilder sb = new StringBuilder();
            for (String piece : pieces) {
                sb.append(piece);
                sb.append("\n");
            }
            return sb.toString();
        }

        @Override
        public void setUp() throws Exception {
            mTestInstance = new BluetoothStressTest() {
                @Override
                void reportMetrics(ITestInvocationListener l, TestInfo test,
                        Map<String, String> metrics) {
                    mReportedTestInfo = test;
                    mReportedMetrics = metrics;
                }
            };
            mScanInfo = new TestInfo();
            mScanInfo.mTestName = "scan";
            mScanInfo.mTestMethod = "testScan";
            mScanInfo.mIterCount = 1;
            mScanInfo.mPerfMetrics.add("startScan");
            mScanInfo.mPerfMetrics.add("stopScan");
        }

        /**
         * Make sure that parsing works in the expected case
         */
        public void testParse() throws Exception {
            String output = join(
                    "enable() completed in 5759 ms",
                    "scan iteration 1 of 3",
                    "startScan() completed in 102 ms",
                    "stopScan() completed in 104 ms",
                    "scan iteration 2 of 3",
                    "startScan() completed in 103 ms",
                    "stopScan() completed in 106 ms",
                    "scan iteration 3 of 3",
                    "startScan() completed in 107 ms",
                    "stopScan() completed in 103 ms",
                    "disable() completed in 3763 ms");

            InputStream iStream = new ByteArrayInputStream(output.getBytes());
            mTestInstance.parseOutputFile(mScanInfo, iStream, null);
            assertEquals(mScanInfo, mReportedTestInfo);
            assertNotNull(mReportedMetrics);
            assertEquals(3, mReportedMetrics.size());
            assertEquals("3", mReportedMetrics.get("iterations"));
            assertEquals("104.333",
                    mReportedMetrics.get("performance_stopScan_mean").substring(0, 7));
            assertEquals("104.0", mReportedMetrics.get("performance_startScan_mean"));
        }

        /**
         * Check parsing when the output is missing entire iterations
         */
        public void testParse_short() throws Exception {
            String output = join(
                    "enable() completed in 5759 ms",
                    "scan iteration 3 of 3",  // only one iteration reported
                    "startScan() completed in 107 ms",
                    "stopScan() completed in 103 ms",
                    "disable() completed in 3763 ms");

            InputStream iStream = new ByteArrayInputStream(output.getBytes());
            mTestInstance.parseOutputFile(mScanInfo, iStream, null);
            assertEquals(mScanInfo, mReportedTestInfo);
            assertNotNull(mReportedMetrics);
            assertEquals(3, mReportedMetrics.size());
            // Parser should realize that there was only 1 iteration reported
            assertEquals("1", mReportedMetrics.get("iterations"));
            assertEquals("103.0", mReportedMetrics.get("performance_stopScan_mean"));
            assertEquals("107.0", mReportedMetrics.get("performance_startScan_mean"));
        }

        /**
         * Check parsing when the output is missing optional datums
         */
        public void testParse_missingDatums() throws Exception {
            // stopScan is missing datums but completes successfully
            String output = join(
                    "enable() completed in 5759 ms",
                    "scan iteration 1 of 3",
                    "startScan() completed in 102 ms",
                    "stopScan() completed",
                    "scan iteration 2 of 3",
                    "startScan() completed in 103 ms",
                    "stopScan() completed",
                    "scan iteration 3 of 3",
                    "startScan() completed in 107 ms",
                    "stopScan() completed",
                    "disable() completed in 3763 ms");

            InputStream iStream = new ByteArrayInputStream(output.getBytes());
            mTestInstance.parseOutputFile(mScanInfo, iStream, null);
            assertEquals(mScanInfo, mReportedTestInfo);
            assertNotNull(mReportedMetrics);
            assertEquals(2, mReportedMetrics.size());
            // Parser should realize that one of the optional datums is missing
            assertEquals("3", mReportedMetrics.get("iterations"));
            assertEquals("104.0", mReportedMetrics.get("performance_startScan_mean"));
        }

        /**
         * Check parsing when the output is missing mandatory methods
         */
        public void testParse_missingMethods() throws Exception {
            String output = join(
                    "enable() completed in 5759 ms",
                    "scan iteration 1 of 3",
                    "startScan() completed in 102 ms",
                    //"stopScan() completed in 104 ms",
                    "scan iteration 2 of 3",
                    "startScan() completed in 103 ms",
                    //"stopScan() completed in 106 ms",
                    "scan iteration 3 of 3",
                    "startScan() completed in 107 ms",
                    //"stopScan() completed in 103 ms",
                    "disable() completed in 3763 ms");

            InputStream iStream = new ByteArrayInputStream(output.getBytes());
            mTestInstance.parseOutputFile(mScanInfo, iStream, null);
            assertEquals(mScanInfo, mReportedTestInfo);
            assertNotNull(mReportedMetrics);
            assertEquals(2, mReportedMetrics.size());
            // Parser should realize that one of the mandatory methods is missing
            assertEquals("0", mReportedMetrics.get("iterations"));
            assertEquals("104.0", mReportedMetrics.get("performance_startScan_mean"));
        }

        /**
         * Make sure that parsing works with additional information added.
         */
        public void testParse_extras() throws Exception {
            String output = join(
                    "enable() completed in 5759 ms",
                    "scan iteration 1 of 3",
                    "startScan(profile=1, device=12:34:45:78:9A:BC) completed in 102 ms",
                    "stopScan(profile=1, device=12:34:45:78:9A:BC) completed in 104 ms",
                    "scan iteration 2 of 3",
                    "startScan(profile=1, device=12:34:45:78:9A:BC) completed in 103 ms",
                    "stopScan(profile=1, device=12:34:45:78:9A:BC) completed in 106 ms",
                    "scan iteration 3 of 3",
                    "startScan(profile=1, device=12:34:45:78:9A:BC) completed in 107 ms",
                    "stopScan(profile=1, device=12:34:45:78:9A:BC) completed in 103 ms",
                    "disable() completed in 3763 ms");

            InputStream iStream = new ByteArrayInputStream(output.getBytes());
            mTestInstance.parseOutputFile(mScanInfo, iStream, null);
            assertEquals(mScanInfo, mReportedTestInfo);
            assertNotNull(mReportedMetrics);
            assertEquals(3, mReportedMetrics.size());
            assertEquals("3", mReportedMetrics.get("iterations"));
            assertEquals("104.333",
                    mReportedMetrics.get("performance_stopScan_mean").substring(0, 7));
            assertEquals("104.0", mReportedMetrics.get("performance_startScan_mean"));
        }

        /**
         * Make sure that parsing ignores anything before the first iterations
         */
        public void testParse_ignoreSetup() throws Exception {
            String output = join(
                    "enable() completed in 5759 ms",
                    "stopScan() completed in 10000 ms", // Should not be counted
                    "scan iteration 1 of 3",
                    "startScan() completed in 102 ms",
                    "stopScan() completed in 104 ms",
                    "scan iteration 2 of 3",
                    "startScan() completed in 103 ms",
                    "stopScan() completed in 106 ms",
                    "scan iteration 3 of 3",
                    "startScan() completed in 107 ms",
                    "stopScan() completed in 103 ms",
                    "disable() completed in 3763 ms");

            InputStream iStream = new ByteArrayInputStream(output.getBytes());
            mTestInstance.parseOutputFile(mScanInfo, iStream, null);
            assertEquals(mScanInfo, mReportedTestInfo);
            assertNotNull(mReportedMetrics);
            assertEquals(3, mReportedMetrics.size());
            assertEquals("3", mReportedMetrics.get("iterations"));
            assertEquals("104.333",
                    mReportedMetrics.get("performance_stopScan_mean").substring(0, 7));
            assertEquals("104.0", mReportedMetrics.get("performance_startScan_mean"));
        }
    }
}
