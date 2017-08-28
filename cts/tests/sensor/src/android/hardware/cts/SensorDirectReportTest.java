/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.hardware.cts;

import android.content.Context;
import android.hardware.HardwareBuffer;
import android.hardware.Sensor;
import android.hardware.SensorAdditionalInfo;
import android.hardware.SensorDirectChannel;
import android.hardware.SensorEventCallback;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorCtsHelper;
import android.os.MemoryFile;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Checks Sensor Direct Report functionality
 *
 * This testcase tests operation of:
 *   - SensorManager.createDirectChannel()
 *   - SensorDirectChannel.*
 *   - Sensor.getHighestDirectReportRateLevel()
 *   - Sensor.isDirectChannelTypeSupported()
 */
public class SensorDirectReportTest extends SensorTestCase {
    private static final String TAG = "SensorDirectReportTest";
    // nominal rates of each rate level supported
    private static final float RATE_NORMAL_NOMINAL = 50;
    private static final float RATE_FAST_NOMINAL = 200;
    private static final float RATE_VERY_FAST_NOMINAL = 800;

    // actuall value is allowed to be 55% to 220% of nominal value
    private static final float FREQ_LOWER_BOUND = 0.55f;
    private static final float FREQ_UPPER_BOUND = 2.2f;

    // sensor reading assumption
    private static final float GRAVITY_MIN = 9.81f - 0.5f;
    private static final float GRAVITY_MAX = 9.81f + 0.5f;
    private static final float GYRO_NORM_MAX = 0.1f;

    // test constants
    private static final int REST_PERIOD_BEFORE_TEST_MILLISEC = 3000;
    private static final int TEST_RUN_TIME_PERIOD_MILLISEC = 5000;
    private static final int ALLOWED_SENSOR_INIT_TIME_MILLISEC = 500;
    private static final int SENSORS_EVENT_SIZE = 104;
    private static final int SENSORS_EVENT_COUNT = 10240; // 800Hz * 2.2 * 5 sec + extra
    private static final int SHARED_MEMORY_SIZE = SENSORS_EVENT_COUNT * SENSORS_EVENT_SIZE;
    private static final float MERCY_FACTOR = 0.1f;

    private static native boolean nativeReadHardwareBuffer(HardwareBuffer hardwareBuffer,
            byte[] buffer, int srcOffset, int destOffset, int count);

    private boolean mNeedMemoryFile;
    private MemoryFile mMemoryFile;
    private boolean mNeedHardwareBuffer;
    private HardwareBuffer mHardwareBuffer;
    private byte[] mBuffer = new byte[SHARED_MEMORY_SIZE];

    private SensorManager mSensorManager;
    private SensorDirectChannel mChannel;

    static {
        System.loadLibrary("cts-sensors-ndk-jni");
    }

    @Override
    protected void setUp() throws Exception {
        mSensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);

        mNeedMemoryFile = isMemoryTypeNeeded(SensorDirectChannel.TYPE_MEMORY_FILE);
        mNeedHardwareBuffer = isMemoryTypeNeeded(SensorDirectChannel.TYPE_HARDWARE_BUFFER);

        if (mNeedMemoryFile) {
            mMemoryFile = allocateMemoryFile();
        }

        if (mNeedHardwareBuffer) {
            mHardwareBuffer = allocateHardwareBuffer();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mChannel != null) {
            mChannel.close();
            mChannel = null;
        }

        if (mMemoryFile != null) {
            mMemoryFile.close();
            mMemoryFile = null;
        }

        if (mHardwareBuffer != null) {
            mHardwareBuffer.close();
            mHardwareBuffer = null;
        }
    }

    public void testSharedMemoryAllocation() throws AssertionError {
        assertTrue("allocating MemoryFile returned null",
                !mNeedMemoryFile || mMemoryFile != null);
        assertTrue("allocating HardwareBuffer returned null",
                !mNeedHardwareBuffer || mHardwareBuffer != null);
    }

    public void testAccelerometerAshmemNormal() {
        runSensorDirectReportTest(
                Sensor.TYPE_ACCELEROMETER,
                SensorDirectChannel.TYPE_MEMORY_FILE,
                SensorDirectChannel.RATE_NORMAL);
    }

    public void testGyroscopeAshmemNormal() {
        runSensorDirectReportTest(
                Sensor.TYPE_GYROSCOPE,
                SensorDirectChannel.TYPE_MEMORY_FILE,
                SensorDirectChannel.RATE_NORMAL);
    }

    public void testMagneticFieldAshmemNormal() {
        runSensorDirectReportTest(
                Sensor.TYPE_MAGNETIC_FIELD,
                SensorDirectChannel.TYPE_MEMORY_FILE,
                SensorDirectChannel.RATE_NORMAL);
    }

    public void testAccelerometerAshmemFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_ACCELEROMETER,
                SensorDirectChannel.TYPE_MEMORY_FILE,
                SensorDirectChannel.RATE_FAST);

    }

    public void testGyroscopeAshmemFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_GYROSCOPE,
                SensorDirectChannel.TYPE_MEMORY_FILE,
                SensorDirectChannel.RATE_FAST);
    }

    public void testMagneticFieldAshmemFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_MAGNETIC_FIELD,
                SensorDirectChannel.TYPE_MEMORY_FILE,
                SensorDirectChannel.RATE_FAST);
    }

    public void testAccelerometerAshmemVeryFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_ACCELEROMETER,
                SensorDirectChannel.TYPE_MEMORY_FILE,
                SensorDirectChannel.RATE_VERY_FAST);

    }

    public void testGyroscopeAshmemVeryFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_GYROSCOPE,
                SensorDirectChannel.TYPE_MEMORY_FILE,
                SensorDirectChannel.RATE_VERY_FAST);
    }

    public void testMagneticFieldAshmemVeryFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_MAGNETIC_FIELD,
                SensorDirectChannel.TYPE_MEMORY_FILE,
                SensorDirectChannel.RATE_VERY_FAST);
    }

    public void testAccelerometerHardwareBufferNormal() {
        runSensorDirectReportTest(
                Sensor.TYPE_ACCELEROMETER,
                SensorDirectChannel.TYPE_HARDWARE_BUFFER,
                SensorDirectChannel.RATE_NORMAL);
    }

    public void testGyroscopeHardwareBufferNormal() {
        runSensorDirectReportTest(
                Sensor.TYPE_GYROSCOPE,
                SensorDirectChannel.TYPE_HARDWARE_BUFFER,
                SensorDirectChannel.RATE_NORMAL);
    }

    public void testMagneticFieldHardwareBufferNormal() {
        runSensorDirectReportTest(
                Sensor.TYPE_MAGNETIC_FIELD,
                SensorDirectChannel.TYPE_HARDWARE_BUFFER,
                SensorDirectChannel.RATE_NORMAL);
    }

    public void testAccelerometerHardwareBufferFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_ACCELEROMETER,
                SensorDirectChannel.TYPE_HARDWARE_BUFFER,
                SensorDirectChannel.RATE_FAST);
    }

    public void testGyroscopeHardwareBufferFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_GYROSCOPE,
                SensorDirectChannel.TYPE_HARDWARE_BUFFER,
                SensorDirectChannel.RATE_FAST);
    }

    public void testMagneticFieldHardwareBufferFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_MAGNETIC_FIELD,
                SensorDirectChannel.TYPE_HARDWARE_BUFFER,
                SensorDirectChannel.RATE_FAST);
    }

    public void testAccelerometerHardwareBufferVeryFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_ACCELEROMETER,
                SensorDirectChannel.TYPE_HARDWARE_BUFFER,
                SensorDirectChannel.RATE_VERY_FAST);
    }

    public void testGyroscopeHardwareBufferVeryFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_GYROSCOPE,
                SensorDirectChannel.TYPE_HARDWARE_BUFFER,
                SensorDirectChannel.RATE_VERY_FAST);
    }

    public void testMagneticFieldHardwareBufferVeryFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_MAGNETIC_FIELD,
                SensorDirectChannel.TYPE_HARDWARE_BUFFER,
                SensorDirectChannel.RATE_VERY_FAST);
    }

    private void runSensorDirectReportTest(int sensorType, int memType, int rateLevel)
            throws AssertionError {
        Sensor s = mSensorManager.getDefaultSensor(sensorType);
        if (s == null
                || s.getHighestDirectReportRateLevel() < rateLevel
                || !s.isDirectChannelTypeSupported(memType)) {
            return;
        }

        try {
            switch(memType) {
                case SensorDirectChannel.TYPE_MEMORY_FILE:
                    assertTrue("MemoryFile is null", mMemoryFile != null);
                    mChannel = mSensorManager.createDirectChannel(mMemoryFile);
                    break;
                case SensorDirectChannel.TYPE_HARDWARE_BUFFER:
                    assertTrue("HardwareBuffer is null", mHardwareBuffer != null);
                    mChannel = mSensorManager.createDirectChannel(mHardwareBuffer);
                    break;
                default:
                    Log.e(TAG, "Specified illegal memory type " + memType);
                    return;
            }
        } catch (IllegalStateException e) {
            mChannel = null;
        }
        assertTrue("createDirectChannel failed", mChannel != null);

        try {
            assertTrue("Shared memory is not formatted", isSharedMemoryFormatted(memType));
            waitBeforeStartSensor();

            int token = mChannel.configure(s, rateLevel);
            assertTrue("configure direct mChannel failed", token > 0);

            waitSensorCollection();

            //stop sensor and analyze content
            mChannel.configure(s, SensorDirectChannel.RATE_STOP);
            checkSharedMemoryContent(s, memType, rateLevel, token);
        } finally {
            mChannel.close();
            mChannel = null;
        }
    }

    private void waitBeforeStartSensor() {
        // wait for sensor system to come to a rest after previous test to avoid flakiness.
        try {
            SensorCtsHelper.sleep(REST_PERIOD_BEFORE_TEST_MILLISEC, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void waitSensorCollection() {
        // wait for sensor system to come to a rest after previous test to avoid flakiness.
        try {
            SensorCtsHelper.sleep(TEST_RUN_TIME_PERIOD_MILLISEC, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private MemoryFile allocateMemoryFile() {
        MemoryFile memFile = null;
        try {
            memFile = new MemoryFile("Sensor Channel", SHARED_MEMORY_SIZE);
        } catch (IOException e) {
            Log.e(TAG, "IOException when allocating MemoryFile");
        }
        return memFile;
    }

    private HardwareBuffer allocateHardwareBuffer() {
        HardwareBuffer hardwareBuffer;

        hardwareBuffer = HardwareBuffer.create(
                SHARED_MEMORY_SIZE, 1 /* height */, HardwareBuffer.BLOB, 1 /* layer */,
                HardwareBuffer.USAGE_CPU_READ_OFTEN | HardwareBuffer.USAGE_GPU_DATA_BUFFER
                    | HardwareBuffer.USAGE_SENSOR_DIRECT_DATA);
        return hardwareBuffer;
    }

    private boolean isMemoryTypeNeeded(int memType) {
        List<Sensor> sensorList = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor s : sensorList) {
            if (s.isDirectChannelTypeSupported(memType)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSharedMemoryFormatted(int memType) {
        if (memType == SensorDirectChannel.TYPE_MEMORY_FILE) {
            if (!readMemoryFileContent()) {
                Log.e(TAG, "Read MemoryFile content fail");
                return false;
            }
        } else {
            if (!readHardwareBufferContent()) {
                Log.e(TAG, "Read HardwareBuffer content fail");
                return false;
            }
        }

        for (byte b : mBuffer) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private void checkSharedMemoryContent(Sensor s, int memType, int rateLevel, int token) {
        if (memType == SensorDirectChannel.TYPE_MEMORY_FILE) {
            assertTrue("read MemoryFile content failed", readMemoryFileContent());
        } else {
            assertTrue("read HardwareBuffer content failed", readHardwareBufferContent());
        }

        int offset = 0;
        int nextSerial = 1;
        DirectReportSensorEvent e = new DirectReportSensorEvent();
        while (offset <= SHARED_MEMORY_SIZE - SENSORS_EVENT_SIZE) {
            parseSensorEvent(mBuffer, offset, e);

            if (e.serial == 0) {
                // reaches end of events
                break;
            }

            assertTrue("incorrect size " + e.size + "  at offset " + offset,
                    e.size == SENSORS_EVENT_SIZE);
            assertTrue("incorrect token " + e.token + " at offset " + offset,
                    e.token == token);
            assertTrue("incorrect serial " + e.serial + " at offset " + offset,
                    e.serial == nextSerial);
            assertTrue("incorrect type " + e.type + " offset " + offset,
                    e.type == s.getType());

            switch(s.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    double accNorm = Math.sqrt(e.x * e.x + e.y * e.y + e.z * e.z);
                    assertTrue("incorrect gravity norm " + accNorm + " at offset " + offset,
                            accNorm < GRAVITY_MAX && accNorm > GRAVITY_MIN);
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    double gyroNorm = Math.sqrt(e.x * e.x + e.y * e.y + e.z * e.z);
                    assertTrue("gyro norm too large (" + gyroNorm + ") at offset " + offset,
                            gyroNorm < GYRO_NORM_MAX);
                    break;
            }

            ++nextSerial;
            offset += SENSORS_EVENT_SIZE;
        }

        int nEvents = nextSerial - 1;
        float nominalFreq = 0;

        switch (rateLevel) {
            case SensorDirectChannel.RATE_NORMAL:
                nominalFreq = RATE_NORMAL_NOMINAL;
                break;
            case SensorDirectChannel.RATE_FAST:
                nominalFreq = RATE_FAST_NOMINAL;
                break;
            case SensorDirectChannel.RATE_VERY_FAST:
                nominalFreq = RATE_VERY_FAST_NOMINAL;
                break;
        }

        if (nominalFreq != 0) {
            int minEvents;
            int maxEvents;
            minEvents = (int) Math.floor(
                    nominalFreq
                    * FREQ_LOWER_BOUND
                    * (TEST_RUN_TIME_PERIOD_MILLISEC - ALLOWED_SENSOR_INIT_TIME_MILLISEC)
                    * (1 - MERCY_FACTOR)
                    / 1000);
            maxEvents = (int) Math.ceil(
                    nominalFreq
                    * FREQ_UPPER_BOUND
                    * (TEST_RUN_TIME_PERIOD_MILLISEC - ALLOWED_SENSOR_INIT_TIME_MILLISEC)
                    * (1 + MERCY_FACTOR)
                    / 1000);

            assertTrue("nEvent is " + nEvents + " not between " + minEvents + " and " + maxEvents,
                    nEvents >= minEvents && nEvents <=maxEvents);
        }
    }

    private boolean readMemoryFileContent() {
        try {
            if (mMemoryFile.readBytes(mBuffer, 0, 0, SHARED_MEMORY_SIZE)
                    != SHARED_MEMORY_SIZE) {
                Log.e(TAG, "cannot read entire MemoryFile");
                return false;
            }
        } catch (IOException e) {
            Log.e(TAG, "accessing MemoryFile cause IOException");
            return false;
        }
        return true;
    }

    private boolean readHardwareBufferContent() {
        return nativeReadHardwareBuffer(mHardwareBuffer, mBuffer, 0, 0, SHARED_MEMORY_SIZE);
    }

    private class DirectReportSensorEvent {
        int size;
        int token;
        int type;
        int serial;
        long ts;
        float x;
        float y;
        float z;
    };

    // parse sensors_event_t and fill information into DirectReportSensorEvent
    private static void parseSensorEvent(byte [] buf, int offset, DirectReportSensorEvent ev) {
        ByteBuffer b = ByteBuffer.wrap(buf, offset, SENSORS_EVENT_SIZE);
        b.order(ByteOrder.nativeOrder());

        ev.size = b.getInt();
        ev.token = b.getInt();
        ev.type = b.getInt();
        ev.serial = b.getInt();
        ev.ts = b.getLong();
        ev.x = b.getFloat();
        ev.y = b.getFloat();
        ev.z = b.getFloat();
    }
}
