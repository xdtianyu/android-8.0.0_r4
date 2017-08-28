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

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.SimpleStats;

import org.junit.Assert;

import java.util.HashMap;
import java.util.Map;

/**
 * Runs the encryption stress tests.
 * <p>
 * Repeatedly encrypts the device inplace, and unlocks the device, and then unencrypts the device.
 * The time taken to encrypt the device is measured, and each step is verified.
 * </p>
 */
public class EncryptionStressTest implements IDeviceTest, IRemoteTest {

    SimpleStats mEncryptionStats = new SimpleStats();
    ITestDevice mTestDevice = null;

    @Option(name="iterations", description="The number of iterations to run")
    private int mIterations = 10;

    /**
     * Encrypts the device inplace, unlocks the device, and then unencrypts the device.
     *
     * @throws DeviceNotAvailableException If the device is not available or if there is an error
     * when encrypting the device.
     */
    private void encryptDevice() throws DeviceNotAvailableException {
        // unencrypt the device and verify that it is not encrypted
        if (!(mTestDevice.unencryptDevice() && !mTestDevice.isDeviceEncrypted())) {
            String message = String.format("Failed to unencrypt device %s",
                    mTestDevice.getSerialNumber());
            CLog.e(message);
            throw new DeviceNotAvailableException(message, mTestDevice.getSerialNumber());
        }

        long start = System.currentTimeMillis();

        // encrypt the device and verify that it is encrypted
        if (!(mTestDevice.encryptDevice(true) && mTestDevice.isDeviceEncrypted())) {
            String message = String.format("Failed to unencrypt device %s",
                    mTestDevice.getSerialNumber());
            CLog.e(message);
            throw new DeviceNotAvailableException(message, mTestDevice.getSerialNumber());
        }

        mEncryptionStats.add((System.currentTimeMillis() - start) / 1000.0);

        if (!mTestDevice.unlockDevice()) {
            String message = String.format("Failed to unencrypt device %s",
                    mTestDevice.getSerialNumber());
            CLog.e(message);
            throw new DeviceNotAvailableException(message, mTestDevice.getSerialNumber());
        }

        mTestDevice.waitForDeviceAvailable();
        mTestDevice.postBootSetup();

        // unencrypt the device and verify that it is not encrypted
        if (!(mTestDevice.unencryptDevice() && !mTestDevice.isDeviceEncrypted())) {
            String message = String.format("Failed to unencrypt device %s",
                    mTestDevice.getSerialNumber());
            CLog.e(message);
            throw new DeviceNotAvailableException(message, mTestDevice.getSerialNumber());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);

        listener.testRunStarted("EncryptionStressTest", 0);

        for (int i = 0; i < mIterations; i++) {
            CLog.i("Starting encryption stress iteration %d of %d on device %s", i + 1, mIterations,
                    mTestDevice.getSerialNumber());
            encryptDevice();
        }

        Map<String, String> metrics = new HashMap<String, String>(2);
        metrics.put(
                mTestDevice.getSerialNumber() + "_iterations",
                Integer.valueOf(mEncryptionStats.size()).toString());
        metrics.put(mTestDevice.getSerialNumber() + "_mean", mEncryptionStats.mean().toString());

        listener.testRunEnded(0, metrics);
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
