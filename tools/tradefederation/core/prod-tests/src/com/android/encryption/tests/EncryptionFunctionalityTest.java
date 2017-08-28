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

package com.android.encryption.tests;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import org.junit.Assert;

import java.util.HashMap;
import java.util.Map;

/**
 * Runs the encryption func tests.
 * <p>
 * The steps and the stage number:
 * 1 Encrypts the device inplace. 0->1->2
 * 2 Checks the password and boot into the system 2->3->4
 * 3 Changes the password 4->5
 * 4 Checks if can still access sdcard(Should not) 5
 * 5 Checks the password and boot into the system 5->6
 * 6 Change the password to DEFAULT(no password),make sure it boots into the system directly 6->7
 * The time of every stage is measured.
 * </p>
 */
public class EncryptionFunctionalityTest implements IDeviceTest, IRemoteTest {

    private static final int BOOT_TIMEOUT = 2 * 60 * 1000;

    ITestDevice mTestDevice = null;

    static final String[] STAGE_NAME = {
        "encryption", "online", "bootcomplete", "decryption",
        "bootcomplete", "pwchanged", "bootcomplete", "nopassword"
    };
    Map<String, String> metrics = new HashMap<String, String>();
    int stage = 0;
    long stageEndTime;
    long stageStartTime;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        listener.testRunStarted("EncryptionFunc", 0);
        mTestDevice.unencryptDevice();
        mTestDevice.waitForDeviceAvailable();
        stageStartTime = System.currentTimeMillis();
        mTestDevice.executeShellCommand("vdc cryptfs enablecrypto inplace password \"abcd\"");
        try {
            mTestDevice.waitForDeviceOnline();
            stageEnd(); // stage 1

            mTestDevice.waitForBootComplete(BOOT_TIMEOUT);
            stageEnd(); // stage 2
            mTestDevice.enableAdbRoot();
            mTestDevice.executeShellCommand("vdc cryptfs checkpw \"abcd\"");
            mTestDevice.executeShellCommand("vdc cryptfs restart");
            stageEnd(); // stage 3

            mTestDevice.waitForDeviceAvailable();
            stageEnd(); // stage 4

            mTestDevice.enableAdbRoot();
            mTestDevice.executeShellCommand("vdc cryptfs changepw password \"dcba\"");
            mTestDevice.nonBlockingReboot();
            mTestDevice.waitForBootComplete(BOOT_TIMEOUT);
            stageEnd(false); // stage 5

            if (!mTestDevice.executeShellCommand("ls /mnt/shell/emulated").trim().isEmpty()) {
                listener.testRunFailed("Still can access sdcard after password is changed");
            } else {
                mTestDevice.enableAdbRoot();
                mTestDevice.executeShellCommand("vdc cryptfs checkpw \"dcba\"");
                mTestDevice.executeShellCommand("vdc cryptfs restart");
                mTestDevice.waitForDeviceAvailable();
                stageEnd(false); // stage 6
                mTestDevice.executeShellCommand("vdc cryptfs changepw default");
                mTestDevice.nonBlockingReboot();
                mTestDevice.waitForBootComplete(BOOT_TIMEOUT * 3);
                stageEnd(false); // stage 7
            }
        } catch (DeviceNotAvailableException e) {
            listener.testRunFailed(String.format("Device not avaible after %s before %s.",
                    STAGE_NAME[stage], STAGE_NAME[stage + 1]));
        }
        metrics.put("SuccessStage", Integer.toString(stage));
        listener.testRunEnded(0, metrics);
    }

    // measure the time between stages.
    void stageEnd(boolean postTime) {
        stageEndTime = System.currentTimeMillis();
        if (postTime) {
            metrics.put(String.format("between%sAnd%s",
                    capitalize(STAGE_NAME[stage]),capitalize(STAGE_NAME[stage + 1])),
                    Long.toString(stageEndTime - stageStartTime));
        }
        stageStartTime = stageEndTime;
        stage++;
    }

    void stageEnd() {
        stageEnd(true);
    }

    private String capitalize(String line) {
        return Character.toUpperCase(line.charAt(0)) + line.substring(1);
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
}
