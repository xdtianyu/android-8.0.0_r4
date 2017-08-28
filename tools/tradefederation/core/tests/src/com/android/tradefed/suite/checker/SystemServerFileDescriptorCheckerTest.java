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
package com.android.tradefed.suite.checker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.device.ITestDevice;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SystemServerFileDescriptorChecker} */
@RunWith(JUnit4.class)
public class SystemServerFileDescriptorCheckerTest {

    private SystemServerFileDescriptorChecker mChecker;
    private ITestDevice mMockDevice;

    @Before
    public void setUp() {
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mChecker = new SystemServerFileDescriptorChecker();
    }

    @Test
    public void testFailToGetPid() throws Exception {
        EasyMock.expect(mMockDevice.getProperty(EasyMock.eq("ro.build.type")))
                .andReturn("userdebug");
        EasyMock.expect(mMockDevice.executeShellCommand(EasyMock.eq("pidof system_server")))
                .andReturn("not found\n");
        EasyMock.replay(mMockDevice);
        assertTrue(mChecker.preExecutionCheck(mMockDevice));
        assertTrue(mChecker.postExecutionCheck(mMockDevice));
        EasyMock.verify(mMockDevice);
    }

    @Test
    public void testFailToGetFdCount() throws Exception {
        EasyMock.expect(mMockDevice.getProperty(EasyMock.eq("ro.build.type")))
                .andReturn("userdebug");
        EasyMock.expect(mMockDevice.executeShellCommand(EasyMock.eq("pidof system_server")))
                .andReturn("1024\n");
        EasyMock.expect(
                        mMockDevice.executeShellCommand(
                                EasyMock.eq("su root ls /proc/1024/fd | wc -w")))
                .andReturn("not found\n");
        EasyMock.replay(mMockDevice);
        assertTrue(mChecker.preExecutionCheck(mMockDevice));
        assertTrue(mChecker.postExecutionCheck(mMockDevice));
        EasyMock.verify(mMockDevice);
    }

    @Test
    public void testAcceptableFdCount() throws Exception {
        EasyMock.expect(mMockDevice.getProperty(EasyMock.eq("ro.build.type")))
                .andReturn("userdebug");
        EasyMock.expect(mMockDevice.executeShellCommand(EasyMock.eq("pidof system_server")))
                .andReturn("914\n");
        EasyMock.expect(
                        mMockDevice.executeShellCommand(
                                EasyMock.eq("su root ls /proc/914/fd | wc -w")))
                .andReturn("382  \n");
        EasyMock.replay(mMockDevice);
        assertTrue(mChecker.preExecutionCheck(mMockDevice));
        assertTrue(mChecker.postExecutionCheck(mMockDevice));
        EasyMock.verify(mMockDevice);
    }

    @Test
    public void testUnacceptableFdCount() throws Exception {
        EasyMock.expect(mMockDevice.getProperty(EasyMock.eq("ro.build.type")))
                .andReturn("userdebug");
        EasyMock.expect(mMockDevice.executeShellCommand(EasyMock.eq("pidof system_server")))
                .andReturn("914\n");
        EasyMock.expect(
                        mMockDevice.executeShellCommand(
                                EasyMock.eq("su root ls /proc/914/fd | wc -w")))
                .andReturn("1002  \n");
        EasyMock.replay(mMockDevice);
        assertTrue(mChecker.preExecutionCheck(mMockDevice));
        assertFalse(mChecker.postExecutionCheck(mMockDevice));
        EasyMock.verify(mMockDevice);
    }

    @Test
    public void testUserBuild() throws Exception {
        EasyMock.expect(mMockDevice.getProperty(EasyMock.eq("ro.build.type"))).andReturn("user");
        // no further calls should happen after above
        EasyMock.replay(mMockDevice);
        assertTrue(mChecker.preExecutionCheck(mMockDevice));
        assertTrue(mChecker.postExecutionCheck(mMockDevice));
        EasyMock.verify(mMockDevice);
    }
}
