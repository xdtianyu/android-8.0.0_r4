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

import com.android.tradefed.build.StubBuildProvider;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link ConfigurationDef}
 */
public class ConfigurationDefTest extends TestCase {

    private static final String CONFIG_NAME = "name";
    private static final String CONFIG_DESCRIPTION = "config description";
    private static final String OPTION_KEY = "key";
    private static final String OPTION_KEY2 = "key2";
    private static final String OPTION_VALUE = "val";
    private static final String OPTION_VALUE2 = "val2";
    private static final String OPTION_NAME = "option";
    private static final String COLLECTION_OPTION_NAME = "collection";
    private static final String MAP_OPTION_NAME = "map";
    private static final String OPTION_DESCRIPTION = "option description";

    public static class OptionTest extends StubBuildProvider {
        @Option(name=COLLECTION_OPTION_NAME, description=OPTION_DESCRIPTION)
        private Collection<String> mCollectionOption = new ArrayList<String>();

        @Option(name=MAP_OPTION_NAME, description=OPTION_DESCRIPTION)
        private Map<String, String> mMapOption = new HashMap<String, String>();

        @Option(name=OPTION_NAME, description=OPTION_DESCRIPTION)
        private String mOption;
    }

    private ConfigurationDef mConfigDef;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mConfigDef = new ConfigurationDef(CONFIG_NAME);
        mConfigDef.setDescription(CONFIG_DESCRIPTION);
        mConfigDef.addConfigObjectDef(Configuration.BUILD_PROVIDER_TYPE_NAME,
                OptionTest.class.getName());
    }

    /**
     * Test {@link ConfigurationDef#createConfiguration()} for a map option.
     */
    public void testCreateConfiguration_optionMap() throws ConfigurationException {
        mConfigDef.addOptionDef(MAP_OPTION_NAME, OPTION_KEY, OPTION_VALUE, CONFIG_NAME);
        mConfigDef.addOptionDef(MAP_OPTION_NAME, OPTION_KEY2, OPTION_VALUE2, CONFIG_NAME);
        IConfiguration config = mConfigDef.createConfiguration();
        OptionTest test = (OptionTest)config.getBuildProvider();
        assertNotNull(test.mMapOption);
        assertEquals(2, test.mMapOption.size());
        assertEquals(OPTION_VALUE, test.mMapOption.get(OPTION_KEY));
        assertEquals(OPTION_VALUE2, test.mMapOption.get(OPTION_KEY2));
    }

    /**
     * Test {@link ConfigurationDef#createConfiguration()} for a collection option.
     */
    public void testCreateConfiguration_optionCollection() throws ConfigurationException {
        mConfigDef.addOptionDef(COLLECTION_OPTION_NAME, null, OPTION_VALUE, CONFIG_NAME);
        mConfigDef.addOptionDef(COLLECTION_OPTION_NAME, null, OPTION_VALUE2, CONFIG_NAME);
        IConfiguration config = mConfigDef.createConfiguration();
        OptionTest test = (OptionTest)config.getBuildProvider();
        assertTrue(test.mCollectionOption.contains(OPTION_VALUE));
        assertTrue(test.mCollectionOption.contains(OPTION_VALUE2));
    }

    /**
     * Test {@link ConfigurationDef#createConfiguration()} for a String field.
     */
    public void testCreateConfiguration() throws ConfigurationException {
        mConfigDef.addOptionDef(OPTION_NAME, null, OPTION_VALUE, CONFIG_NAME);
        IConfiguration config = mConfigDef.createConfiguration();
        OptionTest test = (OptionTest)config.getBuildProvider();
        assertEquals(OPTION_VALUE, test.mOption);
    }

    /**
     * Test {@link ConfigurationDef#createConfiguration()} for a String field.
     */
    public void testCreateConfiguration_withDeviceHolder() throws ConfigurationException {
        mConfigDef = new ConfigurationDef(CONFIG_NAME);
        mConfigDef.setMultiDeviceMode(true);
        mConfigDef.setDescription(CONFIG_DESCRIPTION);
        mConfigDef.addConfigObjectDef("device1:" + Configuration.BUILD_PROVIDER_TYPE_NAME,
                OptionTest.class.getName());
        mConfigDef.addOptionDef(OPTION_NAME, null, OPTION_VALUE, CONFIG_NAME);
        IConfiguration config = mConfigDef.createConfiguration();
        OptionTest test = (OptionTest)config.getDeviceConfigByName("device1")
                .getBuildProvider();
        assertEquals(OPTION_VALUE, test.mOption);
    }
}
