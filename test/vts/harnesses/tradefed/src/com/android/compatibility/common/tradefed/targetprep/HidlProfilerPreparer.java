/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.compatibility.common.tradefed.targetprep;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.util.StreamUtil;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.NoSuchElementException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A {@link HidlProfilerPreparer} that attempts to enable and disable HIDL profiling on a target device.
 * <p />
 * This is used when one wants to do such setup and cleanup operations in Java instead of the
 * VTS Python runner, Python test template, or Python test case.
 */
@OptionClass(alias = "push-file")
public class HidlProfilerPreparer implements ITargetCleaner, IAbiReceiver {
    private static final String LOG_TAG = "HidlProfilerPreparer";

    private static final String TARGET_PROFILING_TRACE_PATH = "/data/local/tmp/";
    private static final String TARGET_PROFILING_LIBRARY_PATH = "/data/local/tmp/<bitness>/hw/";
    private static final String HOST_PROFILING_TRACE_PATH = "/tmp/vts-trace/record-replay/";
    private static final String HOST_PROFILING_TRACE_PATH_KEY = "profiling_trace_path";

    private static final String VENDOR_TEST_CONFIG_FILE_PATH =
            "/config/google-tradefed-vts-config.config";

    @Option(name="target-profiling-trace-path", description=
            "The target-side path to store the profiling trace file(s).")
    private String mTargetProfilingTracePath = TARGET_PROFILING_TRACE_PATH;

    @Option(name="target-profiling-library-path", description=
            "The target-side path to store the profiling trace file(s). " +
            "Use <bitness> to auto fill in 32 or 64 depending on the target device bitness.")
    private String mTargetProfilingLibraryPath = TARGET_PROFILING_LIBRARY_PATH;

    @Option(name="copy-generated-trace-files", description=
            "Whether to copy the generated trace files to a host-side, " +
            "designated destination dir")
    private boolean mCopyGeneratedTraceFiles = false;

    @Option(name="host-profiling-trace-path", description=
            "The host-side path to store the profiling trace file(s).")
    private String mHostProfilingTracePath = HOST_PROFILING_TRACE_PATH;

    private IAbi mAbi = null;

    /**
     * Set mTargetProfilingTracePath.  Exposed for testing.
     */
    void setTargetProfilingTracePath(String path) {
        mTargetProfilingTracePath = path;
    }

    /**
     * Set mTargetProfilingLibraryPath.  Exposed for testing.
     */
    void setTargetProfilingLibraryPath(String path) {
        mTargetProfilingLibraryPath = path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAbi(IAbi abi){
        mAbi = abi;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError, BuildError,
            DeviceNotAvailableException, RuntimeException {
        readVtsTradeFedVendorConfig();

        // Cleanup any existing traces
        Log.d(LOG_TAG, String.format("Deleting any existing target-side trace files in %s.",
              mTargetProfilingTracePath));
        device.executeShellCommand(
                String.format("rm %s/*.vts.trace", mTargetProfilingTracePath));

        Log.d(LOG_TAG, String.format("Starting the HIDL profiling (bitness: %s).",
                                     mAbi.getBitness()));
        mTargetProfilingLibraryPath = mTargetProfilingLibraryPath.replace(
                "<bitness>", mAbi.getBitness());
        Log.d(LOG_TAG, String.format("Target Profiling Library Path: %s",
                                     mTargetProfilingLibraryPath));

        String result =
            device.executeShellCommand("setenforce 0");
        Log.d(LOG_TAG, String.format("setenforce: %s", result));

        // profiler runs as a different user and thus 777 is needed.
        result =
            device.executeShellCommand("chmod 777 /data");
        Log.d(LOG_TAG, String.format("chmod: %s", result));
        result =
            device.executeShellCommand("chmod 777 /data/local");
        Log.d(LOG_TAG, String.format("chmod: %s", result));
        result =
            device.executeShellCommand("chmod 777 /data/local/tmp");
        Log.d(LOG_TAG, String.format("chmod: %s", result));

        result =
            device.executeShellCommand("chmod 755 /data/local/tmp/vts_profiling_configure");
        Log.d(LOG_TAG, String.format("chmod: %s", result));
        result = device.executeShellCommand(
            String.format("/data/local/tmp/vts_profiling_configure enable"));
        Log.d(LOG_TAG, String.format("start profiling: %s", result));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        Log.d(LOG_TAG, "Stopping the HIDL Profiling.");
        // Disables VTS Profiling
        device.executeShellCommand("chmod 755 /data/local/tmp/vts_profiling_configure");
        String result = device.executeShellCommand(
            "/data/local/tmp/vts_profiling_configure disable clear");
        Log.d(LOG_TAG, String.format("stop profiling: %s", result));

        // Gets trace files from the target.
        if (!mTargetProfilingTracePath.endsWith("/")) {
            mTargetProfilingTracePath += "/";
        }
        String traceFileListString = device.executeShellCommand(
                String.format("ls %s*.vts.trace", mTargetProfilingTracePath));
        if (!traceFileListString.contains("No such file or directory")) {
            Log.d(LOG_TAG, String.format("Generated trace files: %s",
                                         traceFileListString));
            if (mCopyGeneratedTraceFiles) {
                File destDir = new File(mHostProfilingTracePath);
                if (!destDir.exists() && !destDir.mkdirs()) {
                    Log.e(LOG_TAG, String.format("Couldn't create a dir, %s.",
                                                 mHostProfilingTracePath));
                } else {
                    for (String traceFilePath : traceFileListString.split("\\s+")) {
                        File traceFile = new File(traceFilePath);
                        File destFile = new File(destDir, traceFile.getName());
                        Log.d(LOG_TAG, String.format("Copying a trace file: %s -> %s",
                                                     traceFilePath, destFile));
                        if (device.pullFile(traceFilePath, destFile)) {
                            Log.d(LOG_TAG, "Copying a trace file succeeded.");
                        } else {
                            Log.e(LOG_TAG, "Copying a trace file failed.");
                        }
                    }
                }
            }
        } else {
            Log.d(LOG_TAG, "Generated trace files: none");
        }
    }

    /**
     * Reads HOST_PROFILING_TRACE_PATH_KEY value from VENDOR_TEST_CONFIG_FILE_PATH.
     */
    private void readVtsTradeFedVendorConfig() throws RuntimeException {
        Log.d(LOG_TAG, String.format("Load vendor test config %s",
                                     VENDOR_TEST_CONFIG_FILE_PATH));
        InputStream config = getClass().getResourceAsStream(
                VENDOR_TEST_CONFIG_FILE_PATH);
        if (config == null) {
            Log.d(LOG_TAG,
                  String.format("Vendor test config file %s does not exist.",
                                VENDOR_TEST_CONFIG_FILE_PATH));
            return;
        }

        try {
            String content = StreamUtil.getStringFromStream(config);
            Log.d(LOG_TAG, String.format("Loaded vendor test config %s",
                                         content));
            if (content != null) {
                JSONObject vendorConfigJson = new JSONObject(content);
                try {
                    String tracePath = vendorConfigJson.getString(
                            HOST_PROFILING_TRACE_PATH_KEY);
                    if (tracePath.length() > 0) {
                        mHostProfilingTracePath = tracePath;
                    }
                } catch (NoSuchElementException e) {
                    Log.d(LOG_TAG,
                          String.format(
                                  "Vendor config does not define %s",
                                  HOST_PROFILING_TRACE_PATH_KEY));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to read vendor config json file");
        } catch (JSONException e) {
            throw new RuntimeException(
                    "Failed to build updated vendor config json data");
        }
    }
}
