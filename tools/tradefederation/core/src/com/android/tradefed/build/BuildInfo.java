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

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.UniqueMultiMap;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;

/**
 * Generic implementation of a {@link IBuildInfo} that should be associated
 * with a {@link ITestDevice}.
 */
public class BuildInfo implements IBuildInfo {
    private static final String BUILD_ALIAS_KEY = "build_alias";

    private String mBuildId = UNKNOWN_BUILD_ID;
    private String mTestTag = "stub";
    private String mBuildTargetName = "stub";
    private final UniqueMultiMap<String, String> mBuildAttributes =
            new UniqueMultiMap<String, String>();
    private Map<String, VersionedFile> mVersionedFileMap;
    private String mBuildFlavor = null;
    private String mBuildBranch = null;
    private String mDeviceSerial = null;

    /**
     * Creates a {@link BuildInfo} using default attribute values.
     */
    public BuildInfo() {
        mVersionedFileMap = new Hashtable<String, VersionedFile>();
    }

    /**
     * Creates a {@link BuildInfo}
     *
     * @param buildId the build id
     * @param buildTargetName the build target name
     */
    public BuildInfo(String buildId, String buildTargetName) {
        mBuildId = buildId;
        mBuildTargetName = buildTargetName;
        mVersionedFileMap = new Hashtable<String, VersionedFile>();
    }

    /**
     * Creates a {@link BuildInfo}
     *
     * @param buildId the build id
     * @param testTag the test tag name
     * @param buildTargetName the build target name
     * @deprecated use {@link #BuildInfo(String, String)} instead. test-tag should not be mandatory
     * when instantiating the build info.
     */
    @Deprecated
    public BuildInfo(String buildId, String testTag, String buildTargetName) {
        mBuildId = buildId;
        mTestTag = testTag;
        mBuildTargetName = buildTargetName;
        mVersionedFileMap = new Hashtable<String, VersionedFile>();
    }

    /**
     * Creates a {@link BuildInfo}, populated with attributes given in another build.
     *
     * @param buildToCopy
     */
    BuildInfo(BuildInfo buildToCopy) {
        this(buildToCopy.getBuildId(), buildToCopy.getBuildTargetName());
        addAllBuildAttributes(buildToCopy);
        try {
            addAllFiles(buildToCopy);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildId() {
        return mBuildId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuildId(String buildId) {
        mBuildId = buildId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTestTag(String testTag) {
        mTestTag = testTag;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTestTag() {
        return mTestTag;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDeviceSerial() {
        return mDeviceSerial;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getBuildAttributes() {
        return mBuildAttributes.getUniqueMap();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildTargetName() {
        return mBuildTargetName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addBuildAttribute(String attributeName, String attributeValue) {
        mBuildAttributes.put(attributeName, attributeValue);
    }

    /**
     * Helper method to copy build attributes, branch, and flavor from other build.
     */
    protected void addAllBuildAttributes(BuildInfo build) {
        mBuildAttributes.putAll(build.getAttributesMultiMap());
        setBuildFlavor(build.getBuildFlavor());
        setBuildBranch(build.getBuildBranch());
        setTestTag(build.getTestTag());
    }

    protected MultiMap<String, String> getAttributesMultiMap() {
        return mBuildAttributes;
    }

    /**
     * Helper method to copy all files from the other build.
     * <p>
     * Creates new hardlinks to the files so that each build will have a unique file path to the
     * file.
     * </p>
     *
     * @throws IOException if an exception is thrown when creating the hardlinks.
     */
    protected void addAllFiles(BuildInfo build) throws IOException {
        for (Map.Entry<String, VersionedFile> fileEntry : build.getVersionedFileMap().entrySet()) {
            File origFile = fileEntry.getValue().getFile();
            File copyFile;
            if (origFile.isDirectory()) {
                copyFile = FileUtil.createTempDir(fileEntry.getKey());
                FileUtil.recursiveHardlink(origFile, copyFile);
            } else {
                // Only using createTempFile to create a unique dest filename
                copyFile = FileUtil.createTempFile(fileEntry.getKey(),
                        FileUtil.getExtension(origFile.getName()));
                copyFile.delete();
                FileUtil.hardlinkFile(origFile, copyFile);
            }
            setFile(fileEntry.getKey(), copyFile, fileEntry.getValue().getVersion());
        }
    }

    protected Map<String, VersionedFile> getVersionedFileMap() {
        return mVersionedFileMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getFile(String name) {
        VersionedFile fileRecord = mVersionedFileMap.get(name);
        if (fileRecord != null) {
            return fileRecord.getFile();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<VersionedFile> getFiles() {
        return mVersionedFileMap.values();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getVersion(String name) {
        VersionedFile fileRecord = mVersionedFileMap.get(name);
        if (fileRecord != null) {
            return fileRecord.getVersion();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFile(String name, File file, String version) {
        if (mVersionedFileMap.containsKey(name)) {
            CLog.e("Device build already contains a file for %s in thread %s", name,
                    Thread.currentThread().getName());
            return;
        }
        mVersionedFileMap.put(name, new VersionedFile(file, version));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp() {
        for (VersionedFile fileRecord : mVersionedFileMap.values()) {
            FileUtil.recursiveDelete(fileRecord.getFile());
        }
        mVersionedFileMap.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo clone() {
        BuildInfo copy = new BuildInfo(mBuildId, mBuildTargetName);
        copy.addAllBuildAttributes(this);
        try {
            copy.addAllFiles(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        copy.setBuildBranch(mBuildBranch);
        copy.setBuildFlavor(mBuildFlavor);

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildFlavor() {
        return mBuildFlavor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuildFlavor(String buildFlavor) {
        mBuildFlavor = buildFlavor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildBranch() {
        return mBuildBranch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuildBranch(String branch) {
        mBuildBranch = branch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceSerial(String serial) {
        mDeviceSerial = serial;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(mBuildAttributes, mBuildBranch, mBuildFlavor, mBuildId,
                mBuildTargetName, mTestTag, mDeviceSerial);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        BuildInfo other = (BuildInfo) obj;
        return Objects.equal(mBuildAttributes, other.mBuildAttributes) &&
                Objects.equal(mBuildBranch, other.mBuildBranch) &&
                Objects.equal(mBuildFlavor, other.mBuildFlavor) &&
                Objects.equal(mBuildId, other.mBuildId) &&
                Objects.equal(mBuildTargetName, other.mBuildTargetName) &&
                Objects.equal(mTestTag, other.mTestTag) &&
                Objects.equal(mDeviceSerial, other.mDeviceSerial);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this.getClass())
                .omitNullValues()
                .add("build_alias", getBuildAttributes().get(BUILD_ALIAS_KEY))
                .add("bid", mBuildId)
                .add("target", mBuildTargetName)
                .add("build_flavor", mBuildFlavor)
                .add("branch", mBuildBranch)
                .add("serial", mDeviceSerial)
                .toString();
    }
}
