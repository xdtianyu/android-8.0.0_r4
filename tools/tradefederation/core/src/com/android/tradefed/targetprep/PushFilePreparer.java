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

package com.android.tradefed.targetprep;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.SystemUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * A {@link ITargetPreparer} that attempts to push any number of files from any host path to any
 * device path.
 * <p />
 * Should be performed *after* a new build is flashed, and *after* DeviceSetup is run (if enabled)
 */
@OptionClass(alias = "push-file")
public class PushFilePreparer implements ITargetCleaner {
    private static final String LOG_TAG = "PushFilePreparer";
    private static final String MEDIA_SCAN_INTENT =
            "am broadcast -a android.intent.action.MEDIA_MOUNTED -d file://%s "
                    + "--receiver-include-background";
    private static final String HOST_TESTCASES = "host/testcases";
    private static final String TARGET_TESTCASES = "target/testcases";

    @Option(name="push", description=
            "A push-spec, formatted as '/path/to/srcfile.txt->/path/to/destfile.txt' or " +
            "'/path/to/srcfile.txt->/path/to/destdir/'. May be repeated.")
    private Collection<String> mPushSpecs = new ArrayList<>();

    @Option(name="post-push", description=
            "A command to run on the device (with `adb shell (yourcommand)`) after all pushes " +
            "have been attempted.  Will not be run if a push fails with abort-on-push-failure " +
            "enabled.  May be repeated.")
    private Collection<String> mPostPushCommands = new ArrayList<>();

    @Option(name="abort-on-push-failure", description=
            "If false, continue if pushes fail.  If true, abort the Invocation on any failure.")
    private boolean mAbortOnFailure = true;

    @Option(name="trigger-media-scan", description=
            "After pushing files, trigger a media scan of external storage on device.")
    private boolean mTriggerMediaScan = false;

    @Option(name="cleanup", description = "Whether files pushed onto device should be cleaned up "
            + "after test. Note that the preparer does not verify that files/directories have "
            + "been deleted.")
    private boolean mCleanup = false;

    @Option(name="remount-system", description="Remounts system partition to be writable "
            + "so that files could be pushed there too")
    private boolean mRemount = false;

    private Collection<String> mFilesPushed = null;

    /**
     * Helper method to only throw if mAbortOnFailure is enabled.  Callers should behave as if this
     * method may return.
     */
    private void fail(String message, ITestDevice device) throws TargetSetupError {
        if (mAbortOnFailure) {
            throw new TargetSetupError(message, device.getDeviceDescriptor());
        } else {
            // Log the error and return
            Log.w(LOG_TAG, message);
        }
    }

    /**
     * Get a list of {@link File} of the test cases directories
     *
     * <p>The wrapper function is for unit test to mock the system calls.
     *
     * @return a list of {@link File} of directories of the test cases folder of build output, based
     *     on the value of environment variables.
     */
    List<File> getTestCasesDirs() {
        return SystemUtil.getTestCasesDirs();
    }

    /**
     * Resolve relative file path via {@link IBuildInfo} and test cases directories.
     *
     * @param buildInfo the build artifact information
     * @param fileName relative file path to be resolved
     * @return the file from the build info or test cases directories
     */
    public File resolveRelativeFilePath(IBuildInfo buildInfo, String fileName) {
        File src = null;
        if (buildInfo != null) {
            src = buildInfo.getFile(fileName);
            if (src != null && src.exists()) {
                return src;
            }
        }
        List<File> testCasesDirs = getTestCasesDirs();

        // Search for source file in tests directory if buildInfo is IDeviceBuildInfo.
        if (buildInfo instanceof IDeviceBuildInfo) {
            IDeviceBuildInfo deviceBuildInfo = (IDeviceBuildInfo) buildInfo;
            File testsDir = deviceBuildInfo.getTestsDir();
            // Add all possible paths to the testcases directory list.
            if (testsDir != null) {
                testCasesDirs.addAll(
                        Arrays.asList(
                                testsDir,
                                FileUtil.getFileForPath(testsDir, HOST_TESTCASES),
                                FileUtil.getFileForPath(testsDir, TARGET_TESTCASES)));
            }
        }
        for (File dir : testCasesDirs) {
            src = FileUtil.getFileForPath(dir, fileName);
            if (src != null && src.exists()) {
                return src;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError, BuildError,
            DeviceNotAvailableException {
        mFilesPushed = new ArrayList<>();
        if (mRemount) {
            device.remountSystemWritable();
        }
        for (String pushspec : mPushSpecs) {
            String[] pair = pushspec.split("->");
            if (pair.length != 2) {
                fail(String.format("Invalid pushspec: '%s'", Arrays.asList(pair)), device);
                continue;
            }
            Log.d(LOG_TAG, String.format("Trying to push local '%s' to remote '%s'", pair[0],
                    pair[1]));

            File src = new File(pair[0]);
            if (!src.isAbsolute()) {
                src = resolveRelativeFilePath(buildInfo, pair[0]);
            }
            if (src == null || !src.exists()) {
                fail(String.format("Local source file '%s' does not exist", pair[0]), device);
                continue;
            }
            if (src.isDirectory()) {
                if (!device.pushDir(src, pair[1])) {
                    fail(String.format("Failed to push local '%s' to remote '%s'", pair[0],
                            pair[1]), device);
                    continue;
                } else {
                    mFilesPushed.add(pair[1]);
                }
            } else {
                if (!device.pushFile(src, pair[1])) {
                    fail(String.format("Failed to push local '%s' to remote '%s'", pair[0],
                            pair[1]), device);
                    continue;
                } else {
                    mFilesPushed.add(pair[1]);
                }
            }
        }

        for (String command : mPostPushCommands) {
            device.executeShellCommand(command);
        }

        if (mTriggerMediaScan) {
            String mountPoint = device.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
            device.executeShellCommand(String.format(MEDIA_SCAN_INTENT, mountPoint));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (!(e instanceof DeviceNotAvailableException) && mCleanup && mFilesPushed != null) {
            if (mRemount) {
                device.remountSystemWritable();
            }
            for (String devicePath : mFilesPushed) {
                device.executeShellCommand("rm -r " + devicePath);
            }
        }
    }
}
