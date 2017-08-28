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
package com.android.tradefed.testtype.suite;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.IRunUtil;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;

/** Unit tests for {@link com.android.tradefed.testtype.suite.TestFailureListener} */
@RunWith(JUnit4.class)
public class TestFailureListenerTest {

    private TestFailureListener mFailureListener;
    private ITestInvocationListener mMockListener;
    private ITestDevice mMockDevice;

    @Before
    public void setUp() {
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockDevice = EasyMock.createStrictMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn("SERIAL");
        // Create base failure listener with all option ON and default logcat size.
        mFailureListener = new TestFailureListener(mMockListener, mMockDevice,
                true, true, true, true, -1);
    }

    /**
     * Test that on testFailed all the collection are triggered.
     */
    @Test
    public void testTestFailed() throws Exception {
        TestIdentifier testId = new TestIdentifier("com.fake", "methodfake");
        final String trace = "oups it failed";
        final long startDate = 1479917040l; // Wed Nov 23 16:04:00 GMT 2016
        final byte[] fakeData = "fakeData".getBytes();
        InputStreamSource fakeSource = new ByteArrayInputStreamSource(fakeData);
        EasyMock.expect(mMockDevice.getDeviceDate()).andReturn(startDate);
        // Screenshot routine
        EasyMock.expect(mMockDevice.getScreenshot()).andReturn(fakeSource);
        mMockListener.testLog(EasyMock.eq(testId.toString() + "-screenshot"),
                EasyMock.eq(LogDataType.PNG), EasyMock.eq(fakeSource));
        // Bugreport routine
        EasyMock.expect(mMockDevice.getBugreport()).andReturn(fakeSource);
        mMockListener.testLog(EasyMock.eq(testId.toString() + "-bugreport"),
                EasyMock.eq(LogDataType.BUGREPORT), EasyMock.eq(fakeSource));
        // logcat routine
        EasyMock.expect(mMockDevice.getLogcatSince(EasyMock.eq(startDate))).andReturn(fakeSource);
        mMockListener.testLog(EasyMock.eq(testId.toString() + "-logcat"),
                EasyMock.eq(LogDataType.LOGCAT), EasyMock.eq(fakeSource));
        // Reboot routine
        EasyMock.expect(mMockDevice.getProperty(EasyMock.eq("ro.build.type")))
                .andReturn("userdebug");
        mMockDevice.reboot();
        EasyMock.replay(mMockListener, mMockDevice);
        mFailureListener.testStarted(testId);
        mFailureListener.testFailed(testId, trace);
        mFailureListener.testEnded(testId, Collections.emptyMap());
        EasyMock.verify(mMockListener, mMockDevice);
    }

    /**
     * Test that testFailed behave properly when device is unavailable: collection is attempted
     * and properly handled.
     */
    @Test
    public void testTestFailed_notAvailable() throws Exception {
        mFailureListener = new TestFailureListener(mMockListener, mMockDevice,
                false, true, true, true, -1) {
            @Override
            IRunUtil getRunUtil() {
                return EasyMock.createMock(IRunUtil.class);
            }
        };
        TestIdentifier testId = new TestIdentifier("com.fake", "methodfake");
        final String trace = "oups it failed";
        final byte[] fakeData = "fakeData".getBytes();
        InputStreamSource fakeSource = new ByteArrayInputStreamSource(fakeData);
        DeviceNotAvailableException dnae = new DeviceNotAvailableException();
        EasyMock.expect(mMockDevice.getDeviceDate()).andThrow(dnae);
        // Screenshot routine
        EasyMock.expect(mMockDevice.getScreenshot()).andThrow(dnae);
        // logcat routine
        EasyMock.expect(mMockDevice.getLogcat(EasyMock.anyInt())).andReturn(fakeSource);
        mMockListener.testLog(EasyMock.eq(testId.toString() + "-logcat"),
                EasyMock.eq(LogDataType.LOGCAT), EasyMock.eq(fakeSource));
        // Reboot routine
        EasyMock.expect(mMockDevice.getProperty(EasyMock.eq("ro.build.type")))
                .andReturn("userdebug");
        mMockDevice.reboot();
        EasyMock.expectLastCall().andThrow(dnae);
        EasyMock.replay(mMockListener, mMockDevice);
        mFailureListener.testStarted(testId);
        mFailureListener.testFailed(testId, trace);
        mFailureListener.testEnded(testId, Collections.emptyMap());
        EasyMock.verify(mMockListener, mMockDevice);
    }

    /**
     * Test when a test failure occurs and it is a user build, no reboot is attempted.
     */
    @Test
    public void testTestFailed_userBuild() throws Exception {
        mFailureListener = new TestFailureListener(mMockListener, mMockDevice,
                false, false, false, true, -1);
        final String trace = "oups it failed";
        TestIdentifier testId = new TestIdentifier("com.fake", "methodfake");
        EasyMock.expect(mMockDevice.getProperty(EasyMock.eq("ro.build.type"))).andReturn("user");
        EasyMock.replay(mMockListener, mMockDevice);
        mFailureListener.testStarted(testId);
        mFailureListener.testFailed(testId, trace);
        mFailureListener.testEnded(testId, Collections.emptyMap());
        EasyMock.verify(mMockListener, mMockDevice);
    }
}
