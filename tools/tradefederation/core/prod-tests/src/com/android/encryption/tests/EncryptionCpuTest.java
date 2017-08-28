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

package com.android.encryption.tests;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.CpuStatsCollector;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TopHelper;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs the encryption CPU benchmarks
 * <p>
 * Runs various disk intensive actions on the device while measuring the CPU usage with the top
 * command.  This test can be run with an encrypted device or with an unencrypted device, and it is
 * important to run both so that the difference between encrypted and unecrypted CPU usage can be
 * derived.
 * </p>
 */
public class EncryptionCpuTest implements IDeviceTest, IRemoteTest {
    /** The amount to trim from either side of the top samples. */
    private final static int TOP_TRIM = 5;

    /** The block size in bytes for the dd command */
    private final static int BLOCK_SIZE = 1024;

    private final static int TEST_TIMEOUT = 10 * 60 * 1000; // 10 minutes

    @Option(name="use-cpustats")
    private boolean mUseCpuStats = false;

    /**
     * Class used for tests.  Includes fields such as name post key and the method for running the
     * test.
     */
    private class CpuTest {
        public String mTestName = null;
        public String mKey = null;

        private TopHelper mTopHelper = null;
        private CpuStatsCollector mCpuStatsCollector = null;
        private File mLogFile = null;

        private Map<String, String> mMetrics = new HashMap<>();

        /**
         * Run the test.
         *
         * @param listener an {@link ITestInvocationListener} where to report results
         * @throws DeviceNotAvailableException If the device is not available.
         */
        public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
            // Override for the test run.
        }

        /**
         * Helper method for adding all the top statistics to the test metrics.
         *
         * @param top The {@link TopHelper} object used to measure the CPU usage via the top
         * command.
         */
        protected void addTopStats(TopHelper top) {
            String keySuffix = getKeySuffix();

            List<TopHelper.TopStats> stats = top.getTopStats();
            if (stats.size() > TOP_TRIM * 2) {
                stats = stats.subList(TOP_TRIM, stats.size() - TOP_TRIM);

                addMetric("total_mean" + keySuffix, TopHelper.getTotalAverage(stats).toString());
                addMetric("user_mean" + keySuffix, TopHelper.getUserAverage(stats).toString());
                addMetric("system_mean" + keySuffix, TopHelper.getSystemAverage(stats).toString());
                addMetric("iow_mean" + keySuffix, TopHelper.getIowAverage(stats).toString());
                addMetric("irq_mean" + keySuffix, TopHelper.getIrqAverage(stats).toString());
            }
        }

        /**
         * Helper method for adding all the cpustats statistics to the test metrics.
         *
         * @param helper The {@link CpuStatsCollector} object used to measure the CPU usage via the
         * cpustats command.
         */
        protected void addCpuStats(CpuStatsCollector helper) {
            String keySuffix = getKeySuffix();

            Map<String, List<CpuStatsCollector.CpuStats>> cpuStats = helper.getCpuStats();
            if (cpuStats.containsKey("Total") || cpuStats.get("Total").size() > TOP_TRIM * 2) {
                List<CpuStatsCollector.CpuStats> totalStats = cpuStats.get("Total");

                totalStats = totalStats.subList(TOP_TRIM, totalStats.size() - TOP_TRIM);

                addMetric("total_mean" + keySuffix,
                        CpuStatsCollector.getTotalPercentageMean(totalStats).toString());
                addMetric("user_mean" + keySuffix,
                        CpuStatsCollector.getUserPercentageMean(totalStats).toString());
                addMetric("system_mean" + keySuffix,
                        CpuStatsCollector.getSystemPercentageMean(totalStats).toString());
                addMetric("iow_mean" + keySuffix,
                        CpuStatsCollector.getIowPercentageMean(totalStats).toString());
                addMetric("irq_mean" + keySuffix,
                        CpuStatsCollector.getIrqPercentageMean(totalStats).toString());

                Double estimatedMhz = CpuStatsCollector.getEstimatedMhzMean(totalStats);
                if (estimatedMhz != null) {
                    addMetric("estimated_mhz_mean" + keySuffix, estimatedMhz.toString());
                }
                Double usedMhz = CpuStatsCollector.getUsedMhzPercentageMean(totalStats);
                if (usedMhz != null) {
                    addMetric("used_mhz_mean" + keySuffix, usedMhz.toString());
                }
            }
        }

        /**
         * Helper method for adding a metric to the test metrics.
         *
         * @param key The test metric key.
         * @param value The value.
         */
        protected void addMetric(String key, String value) {
            mMetrics.put(key, value);
        }

        /**
         * Gets the test metrics for the test.
         *
         * @return A mapping of metric key to value pairs for the test.
         */
        public Map<String, String> getMetrics() {
            return mMetrics;
        }

        /**
         * Creates the {@link TopHelper} and sets up the logging to file.
         */
        protected void setupLogging() {
            try {
                mLogFile = FileUtil.createTempFile("stats_", ".txt");
            } catch (IOException e) {
                CLog.e("Error creating log file: %s", e.getMessage());
            }

            if (mUseCpuStats) {
                mCpuStatsCollector = new CpuStatsCollector(mTestDevice);
                mCpuStatsCollector.logToFile(mLogFile);
            } else {
                mTopHelper = new TopHelper(mTestDevice);
                mTopHelper.logToFile(mLogFile);
            }
        }

        /**
         * Starts the {@link TopHelper}.
         */
        protected void startLogging() {
            if (mUseCpuStats) {
                mCpuStatsCollector.start();
            } else {
                mTopHelper.start();
            }
        }

        /**
         * Stops the {@link TopHelper} and adds the log file and metrics to the test results.
         * @param listener
         * @throws DeviceNotAvailableException
         */
        protected void stopLogging(ITestInvocationListener listener)
                throws DeviceNotAvailableException {
            if (mUseCpuStats) {
                mCpuStatsCollector.cancel();
            } else {
                mTopHelper.cancel();
            }
            if (mLogFile != null) {
                InputStreamSource source = new FileInputStreamSource(mLogFile, true /* delete */);
                try {
                    listener.testLog(String.format("stats_%s", mKey), LogDataType.TEXT, source);
                } finally {
                    StreamUtil.cancel(source);
                }
                mLogFile = null;
            }
            InputStreamSource bugreport = mTestDevice.getBugreport();
            listener.testLog(String.format("bugreport_%s", mKey), LogDataType.BUGREPORT, bugreport);
            if (mUseCpuStats) {
                addCpuStats(mCpuStatsCollector);
            } else {
                addTopStats(mTopHelper);
            }
        }
    }

    /**
     * CPU test for measuring CPU usage while pushing a file to the device.
     */
    private class PushTest extends CpuTest {
        private String mDeviceFilePath = new File(
                mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE), "testFile"
                ).getAbsolutePath();

        /**
         * {@inheritDoc}
         */
        @Override
        public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
            File hostFile = null;

            mTestDevice.executeShellCommand(String.format("rm %s", mDeviceFilePath));
            try {
                hostFile = File.createTempFile("temp", ".dat");
                createFileHost(hostFile.getAbsolutePath(), mPushFileSize);
            } catch (IOException e) {
                CLog.e("Error creating file on host, skipping test.");
                FileUtil.deleteFile(hostFile);
                return;
            }

            setupLogging();
            try {
                CLog.d("Pushing file");
                startLogging();
                long startTime = System.currentTimeMillis();
                mTestDevice.pushFile(hostFile, mDeviceFilePath);
                long elapsedTime = System.currentTimeMillis() - startTime;

                CLog.d("Pushing %dkB file to device %s took %d ms", mPushFileSize,
                        mTestDevice.getSerialNumber(), elapsedTime);
                addMetric(
                        "push_bw" + getKeySuffix(),
                        Double.valueOf(1000.0 * mPushFileSize / elapsedTime).toString());
            } finally {
                stopLogging(listener);
                FileUtil.deleteFile(hostFile);
                mTestDevice.executeShellCommand(String.format("rm %s", mDeviceFilePath));
            }
        }
    }

    /**
     * CPU test for measuring CPU usage while pulling a file from the device.
     */
    private class PullTest extends CpuTest {
        private String mDeviceFilePath = new File(
                mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE), "testFile"
                ).getAbsolutePath();

        /**
         * {@inheritDoc}
         */
        @Override
        public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
            File hostFile = null;

            mTestDevice.executeShellCommand(String.format("rm %s", mDeviceFilePath));
            if (!createFileDevice(mDeviceFilePath, mPullFileSize)) {
                CLog.e("Error creating file on device %s, skipping test",
                        mTestDevice.getSerialNumber());
                return;
            }

            setupLogging();
            try {
                CLog.d("Pulling file");
                startLogging();
                long startTime = System.currentTimeMillis();
                hostFile = mTestDevice.pullFile(mDeviceFilePath);
                long elapsedTime = System.currentTimeMillis() - startTime;

                CLog.d("pulling %dkB file from device %s took %d ms", mPullFileSize,
                        mTestDevice.getSerialNumber(), elapsedTime);
                addMetric(
                        "pull_bw" + getKeySuffix(),
                        Double.valueOf(1000.0 * mPullFileSize / elapsedTime).toString());
            } finally {
                stopLogging(listener);
                FileUtil.deleteFile(hostFile);
                mTestDevice.executeShellCommand(String.format("rm %s", mDeviceFilePath));
            }
        }
    }

    /**
     * CPU test for measuring CPU usage while playing back a video.
     */
    private class VideoPlaybackTest extends CpuTest {
        /**
         * {@inheritDoc}
         */
        @Override
        public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
            setupLogging();
            try {
                IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
                        "com.android.mediaframeworktest", ".MediaPlayerStressTestRunner",
                        mTestDevice.getIDevice());
                runner.setClassName("com.android.mediaframeworktest.stress.MediaPlayerStressTest");
                runner.setMaxTimeToOutputResponse(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

                CLog.d("Running video playback instrumentation");
                startLogging();
                mTestDevice.runInstrumentationTests(runner);
            } finally {
                stopLogging(listener);
            }
        }
    }

    /**
     * CPU test for measuring CPU usage while capturing a video.
     */
    private class VideoCaptureTest extends CpuTest {

        /**
         * {@inheritDoc}
         */
        @Override
        public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
            setupLogging();
            try {
                IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
                        mVideoRecordPackage, mVideoRecordTestRunner, mTestDevice.getIDevice());
                runner.setMethodName(mVideoRecordClass, mVideoRecordMethod);
                runner.setMaxTimeToOutputResponse(TEST_TIMEOUT, TimeUnit.MILLISECONDS);
                runner.addInstrumentationArg("video_iterations", Integer.toString(1));
                runner.addInstrumentationArg("video_duration", Integer.toString(3 * 60 * 1000));

                CLog.d("Running video capture instrumentation");
                startLogging();
                mTestDevice.runInstrumentationTests(runner);
            } finally {
                stopLogging(listener);
                mTestDevice.executeShellCommand("rm -r ${EXTERNAL_STORAGE}/DCIM/*");
            }
        }
    }

    private List<CpuTest> mTestCases = null;

    ITestDevice mTestDevice = null;

    private boolean mIsEncrypted;

    @Option(name="run-push-test", description="Whether to run push test.")
    private boolean mRunPush = false;

    @Option(name="run-pull-test", description="Whether to run push test.")
    private boolean mRunPull = false;

    @Option(name="run-video-playback-test", description="Whether to run push test.")
    private boolean mRunVideoPlayback = true;

    @Option(name="run-video-record-test", description="Whether to run push test.")
    private boolean mRunVideoRecord = true;

    @Option(name="video-record-package")
    private String mVideoRecordPackage = "com.google.android.camera.tests";

    @Option(name="video-record-test-runner")
    private String mVideoRecordTestRunner = "com.android.camera.stress.CameraStressTestRunner";

    @Option(name="video-record-class")
    private String mVideoRecordClass = "com.android.camera.stress.VideoCapture";

    @Option(name="video-record-method")
    private String mVideoRecordMethod = "testBackVideoCapture";

    @Option(name="pull-file-size",
            description="The size in kB of the file used in the pull test")
    private int mPullFileSize = 256 * 1024;

    @Option(name="push-file-size",
            description="The size in kB of the file used in the push test")
    private int mPushFileSize = 256 * 1024;

    /**
     * Adds the tests to be run as part of the CPU usage suite.
     */
    private void setupTests() {
        if (mTestCases != null) {
            // assume already set up
            return;
        }

        // Allocate enough space for all AbstractEncryptionCpuTest instances below
        mTestCases = new ArrayList<>(4);
        CpuTest test;

        if (mRunPush) {
            test = new PushTest();
            test.mTestName = "PushTest";
            test.mKey = "encryption_push_test";
            mTestCases.add(test);
        }

        if (mRunPull) {
            test = new PullTest();
            test.mTestName = "PullTest";
            test.mKey = "encryption_pull_test";
            mTestCases.add(test);
        }

        if (mRunVideoPlayback) {
            test = new VideoPlaybackTest();
            test.mTestName = "VideoPlaybackTest";
            test.mKey = "encryption_video_playback_test";
            mTestCases.add(test);
        }

        if (mRunVideoRecord) {
            test = new VideoCaptureTest();
            test.mTestName = "VideoCaptureTest";
            test.mKey = "encryption_video_record_test";
            mTestCases.add(test);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);

        mIsEncrypted = mTestDevice.isDeviceEncrypted();
        CLog.d("Running tests on %s device", (mIsEncrypted ? "encrypted" : "unencrypted"));

        setupTests();

        for (CpuTest test : mTestCases) {
            CLog.d("Running %s", test.mTestName);
            listener.testRunStarted(test.mKey, 0);

            test.run(listener);

            CLog.d("About to report metrics to %s: %s", test.mKey, test.getMetrics());
            listener.testRunEnded(0, test.getMetrics());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    /**
     * Creates a file on the host of a certain size at a specified path.
     *
     * @param filePath the path to create the file at.
     * @param size the size of the file in kB.
     * @throws IOException if there was an IOException.
     */
    private void createFileHost(String filePath, int size) throws IOException {
        CLog.d("Create %d kB file %s", size, filePath);
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(constructDdCommand(filePath, size));
            p.waitFor();
            if (0 != p.exitValue()) {
                CLog.e("dd exited with error code %d", p.exitValue());
                throw new IOException(String.format("File %s could not be created. dd exited " +
                        "with error code %d", filePath, p.exitValue()));
            }
        } catch (InterruptedException e) {
            if (p != null) {
                p.destroy();
            }
            CLog.e("dd interrupted");
            throw new IOException(String.format("File %s could not be created. dd  was interrupted",
                    filePath));
        }
    }

    /**
     * Creates a file on the device of a certain size at a specified path.
     *
     * @param filePath the path to create the file at.
     * @param size the size of the file in kB.
     * @return If the file was created.
     * @throws DeviceNotAvailableException if the device was not available.
     */
    private boolean createFileDevice(String filePath, int size) throws DeviceNotAvailableException {
        CLog.d("Create %d kb file %s on device %s", size, filePath, mTestDevice.getSerialNumber());

        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        int timeout = size * 2 * 1000; // Timeout is 2 seconds per kB.
        mTestDevice.executeShellCommand(constructDdCommand(filePath, size),
                receiver, timeout, TimeUnit.MILLISECONDS, 2);
        return (receiver.getOutput().contains(
                String.format("%d bytes transferred", size * BLOCK_SIZE)));
    }

    /**
     * Constructs the dd command used to create the file, both on the host and on the device.
     *
     * @param filePath the path to create the file at.
     * @param size the size of the file in kB.
     * @return the dd command.
     */
    private String constructDdCommand(String filePath, int size) {
        return String.format("dd if=/dev/urandom of=%s bs=%d count=%d", filePath, BLOCK_SIZE, size);
    }

    /**
     * Returns the key suffix based on the encrypted status of the device.
     *
     * @return {@code _encrypted} if the device is encrypted or {@code _unencrypted} if it is not.
     */
    private String getKeySuffix() {
        return mIsEncrypted ? "_encrypted" : "_unencrypted";
    }
}
