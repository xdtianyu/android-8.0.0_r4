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
package com.android.compatibility.common.util;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

/**
 * Host-side utility class for reading properties and gathering information for testing
 * Android device compatibility.
 */
public class PropertyUtil {

    /**
     * Name of read-only property detailing the first API level for which the product was
     * shipped. Property should be undefined for factory ROM products.
     */
    public static final String FIRST_API_LEVEL = "ro.product.first_api_level";

    /** Returns whether the device build is the factory ROM */
    public static boolean isFactoryROM(ITestDevice device) throws DeviceNotAvailableException {
        // first API level property should be undefined if and only if the product is factory ROM.
        return device.getProperty(FIRST_API_LEVEL) == null;
    }

    /**
     * Return the first API level for this product. If the read-only property is unset,
     * this means the first API level is the current API level, and the current API level
     * is returned.
     */
    public static int getFirstApiLevel(ITestDevice device) throws DeviceNotAvailableException {
        String propString = device.getProperty(FIRST_API_LEVEL);
        return (propString == null) ? device.getApiLevel() : Integer.parseInt(propString);
    }
}
