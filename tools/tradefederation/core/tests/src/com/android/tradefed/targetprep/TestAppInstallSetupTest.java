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

package com.android.tradefed.targetprep;

import static org.junit.Assert.*;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.FileUtil;

import org.easymock.EasyMock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

/** Unit tests for {@link TestAppInstallSetup} */
@RunWith(JUnit4.class)
public class TestAppInstallSetupTest {

    private static final String SERIAL = "SERIAL";
    private static final String PACKAGE_NAME = "PACKAGE_NAME";
    private File fakeApk;
    private TestAppInstallSetup mPrep;
    private IDeviceBuildInfo mMockBuildInfo;
    private ITestDevice mMockTestDevice;
    private File testDir;
    private OptionSetter setter;

    @Before
    public void setUp() throws Exception {
        testDir = FileUtil.createTempDir("TestAppSetupTest");
        // fake hierarchy of directory and files
        fakeApk = FileUtil.createTempFile("fakeApk", ".apk", testDir);

        mPrep =
                new TestAppInstallSetup() {
                    @Override
                    protected String parsePackageName(
                            File testAppFile, DeviceDescriptor deviceDescriptor) {
                        return PACKAGE_NAME;
                    }

                    @Override
                    protected File getLocalPathForFilename(
                            IBuildInfo buildInfo, String apkFileName, ITestDevice device)
                            throws TargetSetupError {
                        return fakeApk;
                    }
                };
        mPrep.addTestFileName("fakeApk.apk");

        setter = new OptionSetter(mPrep);
        setter.setOptionValue("cleanup-apks", "true");
        mMockBuildInfo = EasyMock.createMock(IDeviceBuildInfo.class);
        mMockTestDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andStubReturn(SERIAL);
        EasyMock.expect(mMockTestDevice.getDeviceDescriptor()).andStubReturn(null);
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(testDir);
    }

    @Test
    public void testSetupAndTeardown() throws Exception {
        EasyMock.expect(
                        mMockTestDevice.installPackage(
                                (File) EasyMock.anyObject(), EasyMock.eq(true)))
                .andReturn(null);
        EasyMock.replay(mMockBuildInfo, mMockTestDevice);
        mPrep.setUp(mMockTestDevice, mMockBuildInfo);
        EasyMock.verify(mMockBuildInfo, mMockTestDevice);
    }

    @Test
    public void testInstallFailure() throws Exception {
        final String failure = "INSTALL_PARSE_FAILED_MANIFEST_MALFORMED";
        EasyMock.expect(
                        mMockTestDevice.installPackage(
                                (File) EasyMock.anyObject(), EasyMock.eq(true)))
                .andReturn(failure);
        EasyMock.replay(mMockBuildInfo, mMockTestDevice);
        try {
            mPrep.setUp(mMockTestDevice, mMockBuildInfo);
            fail("Expected TargetSetupError");
        } catch (TargetSetupError e) {
            String expected =
                    String.format(
                            "Failed to install %s on %s. Reason: '%s' " + "null",
                            "fakeApk.apk", SERIAL, failure);
            assertEquals(expected, e.getMessage());
        }
        EasyMock.verify(mMockBuildInfo, mMockTestDevice);
    }

    @Test
    public void testInstallFailedUpdateIncompatible() throws Exception {
        final String failure = "INSTALL_FAILED_UPDATE_INCOMPATIBLE";
        EasyMock.expect(
                        mMockTestDevice.installPackage(
                                (File) EasyMock.anyObject(), EasyMock.eq(true)))
                .andReturn(failure);
        EasyMock.expect(mMockTestDevice.uninstallPackage(PACKAGE_NAME)).andReturn(null);
        EasyMock.expect(
                        mMockTestDevice.installPackage(
                                (File) EasyMock.anyObject(), EasyMock.eq(true)))
                .andReturn(null);
        EasyMock.replay(mMockBuildInfo, mMockTestDevice);
        mPrep.setUp(mMockTestDevice, mMockBuildInfo);
        EasyMock.verify(mMockBuildInfo, mMockTestDevice);
    }

    /** Test {@link TestAppInstallSetup#setUp()} with a missing apk. TargetSetupError expected. */
    @Test
    public void testMissingApk() throws Exception {
        fakeApk = null; // Apk doesn't exist

        try {
            mPrep.setUp(mMockTestDevice, mMockBuildInfo);
            fail("TestAppInstallSetup#setUp() did not raise TargetSetupError with missing apk.");
        } catch (TargetSetupError e) {
            assertTrue(e.getMessage().contains("not found"));
        }
    }

    /**
     * Test {@link TestAppInstallSetup#setUp()} with an unreadable apk. TargetSetupError expected.
     */
    @Test
    public void testUnreadableApk() throws Exception {
        fakeApk = new File("/not/a/real/path"); // Apk cannot be read

        try {
            mPrep.setUp(mMockTestDevice, mMockBuildInfo);
            fail("TestAppInstallSetup#setUp() did not raise TargetSetupError with unreadable apk.");
        } catch (TargetSetupError e) {
            assertTrue(e.getMessage().contains("not read"));
        }
    }

    /**
     * Test {@link TestAppInstallSetup#setUp()} with a missing apk and ThrowIfNoFile=False. Silent
     * skip expected.
     */
    @Test
    public void testMissingApk_silent() throws Exception {
        fakeApk = null; // Apk doesn't exist
        setter.setOptionValue("throw-if-not-found", "false");

        mPrep.setUp(mMockTestDevice, mMockBuildInfo);
    }

    /**
     * Test {@link TestAppInstallsetup#setUp()} with an unreadable apk and ThrowIfNoFile=False.
     * Silent skip expected.
     */
    @Test
    public void testUnreadableApk_silent() throws Exception {
        fakeApk = new File("/not/a/real/path"); // Apk cannot be read
        setter.setOptionValue("throw-if-not-found", "false");

        mPrep.setUp(mMockTestDevice, mMockBuildInfo);
    }

}
