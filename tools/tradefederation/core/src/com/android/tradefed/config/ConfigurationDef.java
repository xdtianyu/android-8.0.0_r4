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

package com.android.tradefed.config;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holds a record of a configuration, its associated objects and their options.
 */
public class ConfigurationDef {

    /**
     * a map of object type names to config object class name(s). Use LinkedHashMap to keep objects
     * in the same order they were added.
     */
    private final Map<String, List<ConfigObjectDef>> mObjectClassMap = new LinkedHashMap<>();

    /** a list of option name/value pairs. */
    private final List<OptionDef> mOptionList = new ArrayList<>();

    /** a cache of the frequency of every classname */
    private final Map<String, Integer> mClassFrequency = new HashMap<>();

    /** The set of files (and modification times) that were used to load this config */
    private final Map<File, Long> mSourceFiles = new HashMap<>();

    public static class OptionDef {
        final String name;
        final String key;
        final String value;
        final String source;

        OptionDef(String optionName, String optionValue, String source) {
            this(optionName, null, optionValue, source);
        }

        OptionDef(String optionName, String optionKey, String optionValue, String source) {
            this.name = optionName;
            this.key = optionKey;
            this.value = optionValue;
            this.source = source;
        }
    }

    /**
     * Object to hold info for a className and the appearance number it has (e.g. if a config has
     * the same object twice, the first one will have the first appearance number).
     */
    public static class ConfigObjectDef {
        final String mClassName;
        final Integer mAppearanceNum;

        ConfigObjectDef(String className, Integer appearance) {
            mClassName = className;
            mAppearanceNum = appearance;
        }
    }

    private boolean mMultiDeviceMode = false;
    private Set<String> mExpectedDevices = new HashSet<>();
    private static final Pattern MULTI_PATTERN = Pattern.compile("(.*)(:)(.*)");
    public static final String DEFAULT_DEVICE_NAME = "DEFAULT_DEVICE";

    /** the unique name of the configuration definition */
    private final String mName;

    /** a short description of the configuration definition */
    private String mDescription = "";

    public ConfigurationDef(String name) {
        mName = name;
    }

    /**
     * Returns a short description of the configuration
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * Sets the configuration definition description
     */
    void setDescription(String description) {
        mDescription = description;
    }

    /**
     * Adds a config object to the definition
     *
     * @param typeName the config object type name
     * @param className the class name of the config object
     * @return the number of times this className has appeared in this {@link ConfigurationDef},
     *         including this time.  Because all {@link ConfigurationDef} methods return these
     *         classes with a constant ordering, this index can serve as a unique identifier for the
     *         just-added instance of <code>clasName</code>.
     */
    int addConfigObjectDef(String typeName, String className) {
        List<ConfigObjectDef> classList = mObjectClassMap.get(typeName);
        if (classList == null) {
            classList = new ArrayList<ConfigObjectDef>();
            mObjectClassMap.put(typeName, classList);
        }

        // Increment and store count for this className
        Integer freq = mClassFrequency.get(className);
        freq = freq == null ? 1 : freq + 1;
        mClassFrequency.put(className, freq);
        classList.add(new ConfigObjectDef(className, freq));

        return freq;
    }

    /**
     * Adds option to the definition
     *
     * @param optionName the name of the option
     * @param optionValue the option value
     */
    void addOptionDef(String optionName, String optionKey, String optionValue,
            String optionSource) {
        mOptionList.add(new OptionDef(optionName, optionKey, optionValue, optionSource));
    }

    /**
     * Registers a source file that was used while loading this {@link ConfigurationDef}.
     */
    void registerSource(File source) {
        mSourceFiles.put(source, source.lastModified());
    }

    /**
     * Determine whether any of the source files have changed since this {@link ConfigurationDef}
     * was loaded.
     */
    boolean isStale() {
        for (Map.Entry<File, Long> entry : mSourceFiles.entrySet()) {
            if (entry.getKey().lastModified() > entry.getValue()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the object type name-class map.
     *
     * <p>Exposed for unit testing
     */
    Map<String, List<ConfigObjectDef>> getObjectClassMap() {
        return mObjectClassMap;
    }

    /**
     * Get the option name-value map.
     * <p/>
     * Exposed for unit testing
     */
    List<OptionDef> getOptionList() {
        return mOptionList;
    }

    /**
     * Creates a configuration from the info stored in this definition, and populates its fields
     * with the provided option values.
     *
     * @return the created {@link IConfiguration}
     * @throws ConfigurationException if configuration could not be created
     */
    IConfiguration createConfiguration() throws ConfigurationException {
        IConfiguration config = new Configuration(getName(), getDescription());
        List<IDeviceConfiguration> deviceObjectList = new ArrayList<IDeviceConfiguration>();
        IDeviceConfiguration defaultDeviceConfig =
                new DeviceConfigurationHolder(DEFAULT_DEVICE_NAME);
        if (!mMultiDeviceMode) {
            // We still populate a default device config to avoid special logic in the rest of the
            // harness.
            deviceObjectList.add(defaultDeviceConfig);
        } else {
            for (String name : mExpectedDevices) {
                deviceObjectList.add(new DeviceConfigurationHolder(name));
            }
        }

        for (Map.Entry<String, List<ConfigObjectDef>> objClassEntry : mObjectClassMap.entrySet()) {
            List<Object> objectList = new ArrayList<Object>(objClassEntry.getValue().size());
            String entryName = objClassEntry.getKey();
            boolean shouldAddToFlatConfig = true;

            for (ConfigObjectDef configDef : objClassEntry.getValue()) {
                Object configObject = createObject(objClassEntry.getKey(), configDef.mClassName);
                Matcher matcher = null;
                if (mMultiDeviceMode) {
                    matcher = MULTI_PATTERN.matcher(entryName);
                }
                if (mMultiDeviceMode && matcher.find()) {
                    // If we find the device namespace, fetch the matching device or create it if
                    // it doesn't exists.
                    IDeviceConfiguration multiDev = null;
                    shouldAddToFlatConfig = false;
                    for (IDeviceConfiguration iDevConfig : deviceObjectList) {
                        if (matcher.group(1).equals(iDevConfig.getDeviceName())) {
                            multiDev = iDevConfig;
                            break;
                        }
                    }
                    if (multiDev == null) {
                        multiDev = new DeviceConfigurationHolder(matcher.group(1));
                        deviceObjectList.add(multiDev);
                    }
                    // We reference the original object to the device and not to the flat list.
                    multiDev.addSpecificConfig(configObject);
                    multiDev.addFrequency(configObject, configDef.mAppearanceNum);
                } else {
                    if (Configuration.doesBuiltInObjSupportMultiDevice(entryName)) {
                        defaultDeviceConfig.addSpecificConfig(configObject);
                        defaultDeviceConfig.addFrequency(configObject, configDef.mAppearanceNum);
                    } else {
                        // Only add to flat list if they are not part of multi device config.
                        objectList.add(configObject);
                    }
                }
            }
            if (shouldAddToFlatConfig) {
                config.setConfigurationObjectList(entryName, objectList);
            }
        }
        // We always add the device configuration list so we can rely on it everywhere
        config.setConfigurationObjectList(Configuration.DEVICE_NAME, deviceObjectList);
        config.injectOptionValues(mOptionList);

        return config;
    }

    /**
     * Creates a global configuration from the info stored in this definition, and populates its
     * fields with the provided option values.
     *
     * @return the created {@link IGlobalConfiguration}
     * @throws ConfigurationException if configuration could not be created
     */
    IGlobalConfiguration createGlobalConfiguration() throws ConfigurationException {
        IGlobalConfiguration config = new GlobalConfiguration(getName(), getDescription());

        for (Map.Entry<String, List<ConfigObjectDef>> objClassEntry : mObjectClassMap.entrySet()) {
            List<Object> objectList = new ArrayList<Object>(objClassEntry.getValue().size());
            for (ConfigObjectDef configDef : objClassEntry.getValue()) {
                Object configObject = createObject(objClassEntry.getKey(), configDef.mClassName);
                objectList.add(configObject);
            }
            config.setConfigurationObjectList(objClassEntry.getKey(), objectList);
        }
        for (OptionDef optionEntry : mOptionList) {
            config.injectOptionValue(optionEntry.name, optionEntry.key, optionEntry.value);
        }

        return config;
    }

    /**
     * Gets the name of this configuration definition
     *
     * @return name of this configuration.
     */
    public String getName() {
        return mName;
    }

    public void setMultiDeviceMode(boolean multiDeviceMode) {
        mMultiDeviceMode = multiDeviceMode;
    }

    public void addExpectedDevice(String deviceName) {
        mExpectedDevices.add(deviceName);
    }

    /**
     * Creates a config object associated with this definition.
     *
     * @param objectTypeName the name of the object. Used to generate more descriptive error
     *            messages
     * @param className the class name of the object to load
     * @return the config object
     * @throws ConfigurationException if config object could not be created
     */
    private Object createObject(String objectTypeName, String className)
            throws ConfigurationException {
        try {
            Class<?> objectClass = getClassForObject(objectTypeName, className);
            Object configObject = objectClass.newInstance();
            return configObject;
        } catch (InstantiationException e) {
            throw new ConfigurationException(String.format(
                    "Could not instantiate class %s for config object type %s", className,
                    objectTypeName), e);
        } catch (IllegalAccessException e) {
            throw new ConfigurationException(String.format(
                    "Could not access class %s for config object type %s", className,
                    objectTypeName), e);
        }
    }

    /**
     * Loads the class for the given the config object associated with this definition.
     *
     * @param objectTypeName the name of the config object type. Used to generate more descriptive
     *            error messages
     * @param className the class name of the object to load
     * @return the config object populated with default option values
     * @throws ConfigurationException if config object could not be created
     */
    private Class<?> getClassForObject(String objectTypeName, String className)
            throws ConfigurationException {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new ConfigurationException(
                    String.format("Could not find class %s for config object type %s", className,
                            objectTypeName), e);
        }
    }
}
