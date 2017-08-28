/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.tradefed.build;

import java.io.File;
import java.io.IOException;

/**
 * A {@link IBuildInfo} that represents a complete Android device build and (optionally) its tests.
 */
public class DeviceBuildInfo extends BuildInfo implements IDeviceBuildInfo {

    private static final String DEVICE_IMAGE_NAME = "device";
    private static final String USERDATA_IMAGE_NAME = "userdata";
    private static final String TESTDIR_IMAGE_NAME = "testsdir";
    private static final String BASEBAND_IMAGE_NAME = "baseband";
    private static final String BOOTLOADER_IMAGE_NAME = "bootloader";
    private static final String OTA_IMAGE_NAME = "ota";
    private static final String MKBOOTIMG_IMAGE_NAME = "mkbootimg";
    private static final String RAMDISK_IMAGE_NAME = "ramdisk";

    public DeviceBuildInfo() {
        super();
    }

    public DeviceBuildInfo(String buildId, String buildTargetName) {
        super(buildId, buildTargetName);
    }

    /**
     * @deprecated use the constructor without test-tag instead. test-tag is no longer a mandatory
     * option for build info.
     */
    @Deprecated
    public DeviceBuildInfo(String buildId, String testTag, String buildTargetName) {
        super(buildId, testTag, buildTargetName);
    }

    public DeviceBuildInfo(BuildInfo buildInfo) {
        super(buildInfo);
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link #getDeviceImageVersion()} if not {@code null}, else
     * {@link IBuildInfo#UNKNOWN_BUILD_ID}
     *
     * @see #getDeviceImageVersion()
     */
    @Override
    public String getDeviceBuildId() {
        String buildId = getDeviceImageVersion();
        return buildId == null ? UNKNOWN_BUILD_ID : buildId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDeviceBuildFlavor() {
        return getBuildFlavor();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getDeviceImageFile() {
        return getFile(DEVICE_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDeviceImageVersion() {
        return getVersion(DEVICE_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceImageFile(File deviceImageFile, String version) {
        setFile(DEVICE_IMAGE_NAME, deviceImageFile, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getUserDataImageFile() {
        return getFile(USERDATA_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserDataImageVersion() {
        return getVersion(USERDATA_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUserDataImageFile(File userDataFile, String version) {
        setFile(USERDATA_IMAGE_NAME, userDataFile, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getTestsDir() {
        return getFile(TESTDIR_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTestsDirVersion() {
        return getVersion(TESTDIR_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTestsDir(File testsDir, String version) {
        setFile(TESTDIR_IMAGE_NAME, testsDir, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getBasebandImageFile() {
        return getFile(BASEBAND_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBasebandVersion() {
        return getVersion(BASEBAND_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBasebandImage(File basebandFile, String version) {
        setFile(BASEBAND_IMAGE_NAME, basebandFile, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getBootloaderImageFile() {
        return getFile(BOOTLOADER_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBootloaderVersion() {
        return getVersion(BOOTLOADER_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBootloaderImageFile(File bootloaderImgFile, String version) {
        setFile(BOOTLOADER_IMAGE_NAME, bootloaderImgFile, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getOtaPackageFile() {
        return getFile(OTA_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOtaPackageVersion() {
        return getVersion(OTA_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOtaPackageFile(File otaFile, String version) {
        setFile(OTA_IMAGE_NAME, otaFile, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getMkbootimgFile() {
        return getFile(MKBOOTIMG_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMkbootimgVersion() {
        return getVersion(MKBOOTIMG_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMkbootimgFile(File mkbootimg, String version) {
        setFile(MKBOOTIMG_IMAGE_NAME, mkbootimg, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getRamdiskFile() {
        return getFile(RAMDISK_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRamdiskVersion() {
        return getVersion(RAMDISK_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRamdiskFile(File ramdisk, String version) {
        setFile(RAMDISK_IMAGE_NAME, ramdisk, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo clone() {
        DeviceBuildInfo copy = new DeviceBuildInfo(getBuildId(), getBuildTargetName());
        copy.addAllBuildAttributes(this);
        try {
            copy.addAllFiles(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        copy.setBuildBranch(getBuildBranch());
        copy.setBuildFlavor(getBuildFlavor());

        return copy;
    }
}
