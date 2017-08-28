/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.media.tests;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RunUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A harness that launches Audio Loopback tool and reports result.
 */
public class AudioLoopbackTest implements IDeviceTest, IRemoteTest {

    private static final long TIMEOUT_MS = 5 * 60 * 1000; // 5 min
    private static final long DEVICE_SYNC_MS = 5 * 60 * 1000; // 5 min
    private static final long POLLING_INTERVAL_MS = 5 * 1000;
    private static final int MAX_ATTEMPTS = 3;
    private static final String TESTTYPE_LATENCY = "222";
    private static final String TESTTYPE_BUFFER = "223";

    private static final Map<String, String> METRICS_KEY_MAP = createMetricsKeyMap();

    private ITestDevice mDevice;

    @Option(name = "run-key", description = "Run key for the test")
    private String mRunKey = "AudioLoopback";

    @Option(name = "sampling-freq", description = "Sampling Frequency for Loopback app")
    private String mSamplingFreq = "48000";

    @Option(name = "mic-source", description = "Mic Source for Loopback app")
    private String mMicSource = "3";

    @Option(name = "audio-thread", description = "Audio Thread for Loopback app")
    private String mAudioThread = "1";

    @Option(name = "audio-level", description = "Audio Level for Loopback app. " +
        "A device specific param which makes waveform in loopback test hit 60% to 80% range")
    private String mAudioLevel = "12";

    @Option(name = "test-type", description = "Test type to be executed")
    private String mTestType = TESTTYPE_LATENCY;

    @Option(name = "buffer-test-duration", description = "Buffer test duration in seconds")
    private String mBufferTestDuration = "10";

    @Option(name = "key-prefix", description = "Key Prefix for reporting")
    private String mKeyPrefix = "48000_Mic3_";

    private static final String DEVICE_TEMP_DIR_PATH = "/sdcard/";
    private static final String OUTPUT_FILENAME = "output_" + System.currentTimeMillis();
    private static final String OUTPUT_RESULT_TXT_PATH =
            DEVICE_TEMP_DIR_PATH + OUTPUT_FILENAME + ".txt";
    private static final String OUTPUT_PNG_PATH = DEVICE_TEMP_DIR_PATH + OUTPUT_FILENAME + ".png";
    private static final String OUTPUT_WAV_PATH = DEVICE_TEMP_DIR_PATH + OUTPUT_FILENAME + ".wav";
    private static final String OUTPUT_PLAYER_BUFFER_PATH =
            DEVICE_TEMP_DIR_PATH + OUTPUT_FILENAME + "_playerBufferPeriod.txt";
    private static final String OUTPUT_PLAYER_BUFFER_PNG_PATH =
            DEVICE_TEMP_DIR_PATH + OUTPUT_FILENAME + "_playerBufferPeriod.png";
    private static final String OUTPUT_RECORDER_BUFFER_PATH =
            DEVICE_TEMP_DIR_PATH + OUTPUT_FILENAME + "_recorderBufferPeriod.txt";
    private static final String OUTPUT_RECORDER_BUFFER_PNG_PATH =
            DEVICE_TEMP_DIR_PATH + OUTPUT_FILENAME + "_recorderBufferPeriod.png";
    private static final String OUTPUT_GLITCH_PATH =
            DEVICE_TEMP_DIR_PATH + OUTPUT_FILENAME + "_glitchMillis.txt";
    private static final String AM_CMD =
            "am start -n org.drrickorang.loopback/.LoopbackActivity"
                    + " --ei SF %s --es FileName %s --ei MicSource %s --ei AudioThread %s"
                    + " --ei AudioLevel %s --ei TestType %s --ei BufferTestDuration %s";

    private static Map<String, String> createMetricsKeyMap() {
        Map<String, String> result = new HashMap<>();
        result.put("LatencyMs", "latency_ms");
        result.put("LatencyConfidence", "latency_confidence");
        result.put("Recorder Benchmark", "recorder_benchmark");
        result.put("Recorder Number of Outliers", "recorder_outliers");
        result.put("Player Benchmark", "player_benchmark");
        result.put("Player Number of Outliers", "player_outliers");
        result.put("Total Number of Glitches", "number_of_glitches");
        result.put("kth% Late Recorder Buffer Callbacks", "late_recorder_callbacks");
        result.put("kth% Late Player Buffer Callbacks", "late_player_callbacks");
        result.put("Glitches Per Hour", "glitches_per_hour");
        return Collections.unmodifiableMap(result);
    }

    /**
     * {@inheritDoc}
     */
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
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        TestIdentifier testId = new TestIdentifier(getClass().getCanonicalName(), mRunKey);
        ITestDevice device = getDevice();
        // Wait device to settle
        RunUtil.getDefault().sleep(DEVICE_SYNC_MS);

        listener.testRunStarted(mRunKey, 0);
        listener.testStarted(testId);

        long testStartTime = System.currentTimeMillis();
        Map<String, String> metrics = new HashMap<>();

        // start measurement and wait for result file
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        device.unlockDevice();
        String loopbackCmd = String.format(
                AM_CMD, mSamplingFreq, OUTPUT_FILENAME, mMicSource, mAudioThread,
                mAudioLevel, mTestType, mBufferTestDuration);
        CLog.i("Running cmd: " + loopbackCmd);
        device.executeShellCommand(loopbackCmd, receiver,
                TIMEOUT_MS, TimeUnit.MILLISECONDS, MAX_ATTEMPTS);
        long timeout = Long.parseLong(mBufferTestDuration) * 1000 + TIMEOUT_MS;
        long loopbackStartTime = System.currentTimeMillis();
        boolean isTimedOut = false;
        boolean isResultGenerated = false;
        File loopbackReport = null;
        while (!isResultGenerated && !isTimedOut) {
            RunUtil.getDefault().sleep(POLLING_INTERVAL_MS);
            isTimedOut = (System.currentTimeMillis() - loopbackStartTime >= timeout);
            boolean isResultFileFound = device.doesFileExist(OUTPUT_RESULT_TXT_PATH);
            if (isResultFileFound) {
                loopbackReport = device.pullFile(OUTPUT_RESULT_TXT_PATH);
                if (loopbackReport.length() > 0) {
                    isResultGenerated = true;
                }
            }
        }

        if (isTimedOut) {
            reportFailure(listener, testId, "Loopback result not found, timed out.");
            return;
        }
        // TODO: fail the test or rerun if the confidence level is too low
        // parse result
        CLog.i("== Loopback result ==");
        try {
            Map<String, String> loopbackResult = parseResult(loopbackReport);
            if (loopbackResult == null || loopbackResult.size() == 0) {
                reportFailure(listener, testId, "Failed to parse Loopback result.");
                return;
            }
            metrics = loopbackResult;
            listener.testLog(
                    mKeyPrefix + "result",
                    LogDataType.TEXT,
                    new FileInputStreamSource(loopbackReport));
            File loopbackGraphFile = device.pullFile(OUTPUT_PNG_PATH);
            listener.testLog(
                    mKeyPrefix + "graph",
                    LogDataType.PNG,
                    new FileInputStreamSource(loopbackGraphFile));
            File loopbackWaveFile = device.pullFile(OUTPUT_WAV_PATH);
            listener.testLog(
                    mKeyPrefix + "wave",
                    LogDataType.UNKNOWN,
                    new FileInputStreamSource(loopbackWaveFile));
            if (mTestType.equals(TESTTYPE_BUFFER)) {
                File loopbackPlayerBuffer = device.pullFile(OUTPUT_PLAYER_BUFFER_PATH);
                listener.testLog(
                        mKeyPrefix + "player_buffer",
                        LogDataType.TEXT,
                        new FileInputStreamSource(loopbackPlayerBuffer));
                File loopbackPlayerBufferPng = device.pullFile(OUTPUT_PLAYER_BUFFER_PNG_PATH);
                listener.testLog(
                        mKeyPrefix + "player_buffer_histogram",
                        LogDataType.PNG,
                        new FileInputStreamSource(loopbackPlayerBufferPng));

                File loopbackRecorderBuffer = device.pullFile(OUTPUT_RECORDER_BUFFER_PATH);
                listener.testLog(
                        mKeyPrefix + "recorder_buffer",
                        LogDataType.TEXT,
                        new FileInputStreamSource(loopbackRecorderBuffer));
                File loopbackRecorderBufferPng = device.pullFile(OUTPUT_RECORDER_BUFFER_PNG_PATH);
                listener.testLog(
                        mKeyPrefix + "recorder_buffer_histogram",
                        LogDataType.PNG,
                        new FileInputStreamSource(loopbackRecorderBufferPng));

                File loopbackGlitch = device.pullFile(OUTPUT_GLITCH_PATH);
                listener.testLog(
                        mKeyPrefix + "glitches_millis",
                        LogDataType.TEXT,
                        new FileInputStreamSource(loopbackGlitch));
            }
        } catch (IOException ioe) {
            CLog.e(ioe.getMessage());
            reportFailure(listener, testId, "I/O error while parsing Loopback result.");
            return;
        }

        long durationMs = System.currentTimeMillis() - testStartTime;
        listener.testEnded(testId, metrics);
        listener.testRunEnded(durationMs, metrics);
    }

    /**
     * Report failure with error message specified and fail the test.
     *
     * @param listener
     * @param testId
     * @param errMsg
     */
    private void reportFailure(ITestInvocationListener listener, TestIdentifier testId,
            String errMsg) {
        CLog.e(errMsg);
        listener.testFailed(testId, errMsg);
        listener.testEnded(testId, new HashMap<String, String>());
        listener.testRunFailed(errMsg);
    }

    /**
     * Parse result.
     * Format: key = value
     *
     * @param result Loopback app result file
     * @return a {@link HashMap} that contains metrics keys and results
     * @throws IOException
     */
    private Map<String, String> parseResult(File result) throws IOException {
        Map<String, String> resultMap = new HashMap<>();
        BufferedReader br = new BufferedReader(new FileReader(result));
        try {
            String line = br.readLine();
            while (line != null) {
                line = line.trim().replaceAll(" +", " ");
                String[] tokens = line.split("=");
                if (tokens.length >= 2) {
                    String metricName = tokens[0].trim();
                    String metricValue = tokens[1].trim();
                    if (METRICS_KEY_MAP.containsKey(metricName)) {
                        CLog.i(String.format("%s: %s", metricName, metricValue));
                        resultMap.put(mKeyPrefix + METRICS_KEY_MAP.get(metricName), metricValue);
                    }
                }
                line = br.readLine();
            }
        } finally {
            br.close();
        }
        return resultMap;
    }
}