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
package com.android.tradefed.invoker;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.suite.ITestSuite;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.UniqueMultiMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic implementation of a {@link IInvocationContext}.
 */
public class InvocationContext implements IInvocationContext {

    private Map<ITestDevice, IBuildInfo> mAllocatedDeviceAndBuildMap;
    /** Map of the configuration device name and the actual {@link ITestDevice} **/
    private Map<String, ITestDevice> mNameAndDeviceMap;
    private Map<String, IBuildInfo> mNameAndBuildinfoMap;
    private final UniqueMultiMap<String, String> mInvocationAttributes =
            new UniqueMultiMap<String, String>();
    /** Invocation test-tag **/
    private String mTestTag;
    /** configuration descriptor */
    private ConfigurationDescriptor mConfigurationDescriptor;
    /** module invocation context (when running as part of a {@link ITestSuite} */
    private IInvocationContext mModuleContext;

    /**
     * Creates a {@link BuildInfo} using default attribute values.
     */
    public InvocationContext() {
        mAllocatedDeviceAndBuildMap = new HashMap<ITestDevice, IBuildInfo>();
        // Use LinkedHashMap to ensure key ordering by insertion order
        mNameAndDeviceMap = new LinkedHashMap<String, ITestDevice>();
        mNameAndBuildinfoMap = new LinkedHashMap<String, IBuildInfo>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumDevicesAllocated() {
        return mAllocatedDeviceAndBuildMap.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllocatedDevice(String devicename, ITestDevice testDevice) {
        mNameAndDeviceMap.put(devicename, testDevice);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllocatedDevice(Map<String, ITestDevice> deviceWithName) {
        mNameAndDeviceMap.putAll(deviceWithName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<ITestDevice, IBuildInfo> getDeviceBuildMap() {
        return mAllocatedDeviceAndBuildMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ITestDevice> getDevices() {
        return new ArrayList<ITestDevice>(mNameAndDeviceMap.values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IBuildInfo> getBuildInfos() {
        return new ArrayList<IBuildInfo>(mNameAndBuildinfoMap.values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getSerials() {
        List<String> listSerials = new ArrayList<String>();
        for (ITestDevice testDevice : mNameAndDeviceMap.values()) {
            listSerials.add(testDevice.getSerialNumber());
        }
        return listSerials;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getDeviceConfigNames() {
        List<String> listNames = new ArrayList<String>();
        listNames.addAll(mNameAndDeviceMap.keySet());
        return listNames;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice(String deviceName) {
        return mNameAndDeviceMap.get(deviceName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuildInfo(String deviceName) {
        return mNameAndBuildinfoMap.get(deviceName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDeviceBuildInfo(String deviceName, IBuildInfo buildinfo) {
        mNameAndBuildinfoMap.put(deviceName, buildinfo);
        mAllocatedDeviceAndBuildMap.put(getDevice(deviceName), buildinfo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuildInfo(ITestDevice testDevice) {
        return mAllocatedDeviceAndBuildMap.get(testDevice);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInvocationAttribute(String attributeName, String attributeValue) {
        mInvocationAttributes.put(attributeName, attributeValue);
    }

    /** {@inheritDoc} */
    @Override
    public void addInvocationAttributes(UniqueMultiMap<String, String> attributesMap) {
        mInvocationAttributes.putAll(attributesMap);
    }

    /** {@inheritDoc} */
    @Override
    public MultiMap<String, String> getAttributes() {
        return mInvocationAttributes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDeviceBySerial(String serial) {
        for (ITestDevice testDevice : mNameAndDeviceMap.values()) {
            if (testDevice.getSerialNumber().equals(serial)) {
                return testDevice;
            }
        }
        CLog.d("Device with serial '%s', not found in the metadata", serial);
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public String getDeviceName(ITestDevice device) {
        for (String name : mNameAndDeviceMap.keySet()) {
            if (device.equals(getDevice(name))) {
                return name;
            }
        }
        CLog.d(
                "Device with serial '%s' doesn't match a name in the metadata",
                device.getSerialNumber());
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public String getTestTag() {
        return mTestTag;
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
    public void setRecoveryModeForAllDevices(RecoveryMode mode) {
        for (ITestDevice device : getDevices()) {
            device.setRecoveryMode(mode);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setConfigurationDescriptor(ConfigurationDescriptor configurationDescriptor) {
        mConfigurationDescriptor = configurationDescriptor;
    }

    /** {@inheritDoc} */
    @Override
    public ConfigurationDescriptor getConfigurationDescriptor() {
        return mConfigurationDescriptor;
    }

    /** {@inheritDoc} */
    @Override
    public void setModuleInvocationContext(IInvocationContext invocationContext) {
        mModuleContext = invocationContext;
    }

    /** {@inheritDoc} */
    @Override
    public IInvocationContext getModuleInvocationContext() {
        return mModuleContext;
    }
}
