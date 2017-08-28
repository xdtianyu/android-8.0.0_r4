/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.sensor.tests;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import org.junit.Assert;

import java.io.File;

/**
 * Run the sensor test instrumentation.
 */
public class SingleSensorTests extends InstrumentationTest {

    @Option(name = "output-file-prefix", description = "The files that the test generates. These"
            + "files will be pulled, uploaded, and deleted from the device.")
    private String mOutputPrefix = "single_sensor_";

    @Option(name = "screen-off", description = "Whether to run the tests with the screen off")
    private boolean mScreenOff = false;

    /**
     * Run the instrumentation tests, pull the test generated files, and clean up.
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(getDevice());

        try {
            if (mScreenOff) {
                turnScreenOff();
            }
            super.run(listener);
            pullFiles(listener);
        } finally {
            cleanUp();
        }
    }

    /**
     * Helper method to turn screen off.
     */
    private void turnScreenOff() throws DeviceNotAvailableException {
        getDevice().executeShellCommand("svc power stayon false");
        getDevice().executeShellCommand("input keyevent 26");
    }

    /**
     * Helper method to pull the test files.
     */
    private void pullFiles(ITestInvocationListener listener) throws DeviceNotAvailableException {
        String rawFileList = getDevice().executeShellCommand(
                String.format("ls ${EXTERNAL_STORAGE}/%s*", mOutputPrefix));
        if (rawFileList != null && !rawFileList.contains("No such file or directory")) {
            String[] filePaths = rawFileList.split("\r?\n");
            for (String filePath : filePaths) {
                pullFile(listener, filePath);
            }
        }
    }

    /**
     * Helper method to pull a single file.
     */
    private void pullFile(ITestInvocationListener listener, String filePath)
            throws DeviceNotAvailableException {
        String fileName = new File(filePath).getName();
        String report_key = FileUtil.getBaseName(fileName);

        File outputFile = null;
        InputStreamSource outputSource = null;
        try {
            outputFile = getDevice().pullFile(filePath);
            if (outputFile != null) {
                outputSource = new FileInputStreamSource(outputFile);
                listener.testLog(report_key, LogDataType.TEXT, outputSource);
            }
        } finally {
            FileUtil.deleteFile(outputFile);
            StreamUtil.cancel(outputSource);
        }
    }

    /**
     * Helper method to clean up.
     */
    private void cleanUp() throws DeviceNotAvailableException {
        getDevice().executeShellCommand(String.format("rm ${EXTERNAL_STORAGE}/%s*", mOutputPrefix));
    }
}
