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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.testtype.suite.ITestSuite;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.UniqueMultiMap;

import java.util.List;
import java.util.Map;

/**
 * Holds information about the Invocation for the tests to access if needed.
 * Tests should not modify the context contained here so only getters will be available, except for
 * the context attributes for reporting purpose.
 */
public interface IInvocationContext {

    /**
     * Return the number of devices allocated for the invocation.
     */
    public int getNumDevicesAllocated();

    /**
     * Add a ITestDevice to be tracked by the meta data when the device is allocated.
     * will set the build info to null in the map.
     *
     * @param deviceName the device configuration name to associate with the {@link ITestDevice}
     * @param testDevice to be added to the allocated devices.
     */
    public void addAllocatedDevice(String deviceName, ITestDevice testDevice);

    /**
     * Track a map of configuration device name associated to a {@link ITestDevice}. Doesn't clear
     * the previous tracking before adding.
     *
     * @param deviceWithName the {@link Map} of additional device to track
     */
    public void addAllocatedDevice(Map<String, ITestDevice> deviceWithName);

    /**
     * Return the map of Device/build info association
     */
    public Map<ITestDevice, IBuildInfo> getDeviceBuildMap();

    /**
     * Return all the allocated device tracked for this invocation.
     */
    public List<ITestDevice> getDevices();

    /**
     * Return all the {@link IBuildInfo} tracked for this invocation.
     */
    public List<IBuildInfo> getBuildInfos();

    /**
     * Return the list of serials of the device tracked in this invocation
     */
    public List<String> getSerials();

    /**
     * Return the list of device config names of the device tracked in this invocation
     */
    public List<String> getDeviceConfigNames();

    /**
     * Return the {@link ITestDevice} associated with the device configuration name provided.
     */
    public ITestDevice getDevice(String deviceName);

    /**
     * Returns the {@link ITestDevice} associated with the serial provided.
     * Refrain from using too much as it's not the fastest lookup.
     */
    public ITestDevice getDeviceBySerial(String serial);

    /**
     * Returns the name of the device set in the xml configuration from the {@link ITestDevice}.
     * Returns null, if ITestDevice cannot be matched.
     */
    public String getDeviceName(ITestDevice device);

    /** Return the {@link IBuildInfo} associated with the device configuration name provided. */
    public IBuildInfo getBuildInfo(String deviceName);

    /**
     * Return the {@link IBuildInfo} associated with the {@link ITestDevice}
     */
    public IBuildInfo getBuildInfo(ITestDevice testDevice);

    /**
     * Add a {@link IBuildInfo} to be tracked with the device configuration name.
     *
     * @param deviceName the device configuration name
     * @param buildinfo a {@link IBuildInfo} associated to the device configuration name.
     */
    public void addDeviceBuildInfo(String deviceName, IBuildInfo buildinfo);

    /**
     * Add an Invocation attribute.
     */
    public void addInvocationAttribute(String attributeName, String attributeValue);

    /** Add several invocation attributes at once through a {@link UniqueMultiMap}. */
    public void addInvocationAttributes(UniqueMultiMap<String, String> attributesMap);

    /** Returns the map of invocation attributes. */
    public MultiMap<String, String> getAttributes();

    /** Sets the descriptor associated with the test configuration that launched the invocation */
    public void setConfigurationDescriptor(ConfigurationDescriptor configurationDescriptor);

    /**
     * Returns the descriptor associated with the test configuration that launched the invocation
     */
    public ConfigurationDescriptor getConfigurationDescriptor();

    /**
     * Sets the invocation context of module while being executed as part of a {@link ITestSuite}
     */
    public void setModuleInvocationContext(IInvocationContext invocationContext);

    /**
     * Returns the invocation context of module while being executed as part of a {@link ITestSuite}
     */
    public IInvocationContext getModuleInvocationContext();

    /** Returns the invocation test-tag. */
    public String getTestTag();

    /**
     * Sets the invocation test-tag.
     */
    public void setTestTag(String testTag);

    /**
     * Sets the {@link RecoveryMode} of all the devices part of the context
     */
    public void setRecoveryModeForAllDevices(RecoveryMode mode);
}
