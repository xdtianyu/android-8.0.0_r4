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

import android.os.Build;

import java.io.IOException;
import java.util.Scanner;

/**
 * Device-side utility class for reading properties and gathering information for testing
 * Android device compatibility.
 */
public class PropertyUtil {

    /**
     * Name of read-only property detailing the first API level for which the product was
     * shipped. Property should be undefined for factory ROM products.
     */
    public static String FIRST_API_LEVEL = "ro.product.first_api_level";

    /** Value to be returned by getPropertyInt() if property is not found */
    public static int INT_VALUE_IF_UNSET = -1;

    /** Returns whether the device build is the factory ROM */
    public static boolean isFactoryROM() {
        // property should be undefined if and only if the product is factory ROM.
        return getPropertyInt(FIRST_API_LEVEL) == INT_VALUE_IF_UNSET;
    }

    /**
     * Return the first API level for this product. If the read-only property is unset,
     * this means the first API level is the current API level, and the current API level
     * is returned.
     */
    public static int getFirstApiLevel() {
        int firstApiLevel = getPropertyInt(FIRST_API_LEVEL);
        return (firstApiLevel == INT_VALUE_IF_UNSET) ? Build.VERSION.SDK_INT : firstApiLevel;
    }

    /**
     * Retrieves the desired integer property, returning INT_VALUE_IF_UNSET if not found.
     */
    public static int getPropertyInt(String property) {
        Scanner scanner = null;
        int val = INT_VALUE_IF_UNSET;
        try {
            Process process = new ProcessBuilder("getprop", property).start();
            scanner = new Scanner(process.getInputStream());
            val = Integer.parseInt(scanner.nextLine().trim());
        } catch (IOException | NumberFormatException e) {
            return val = INT_VALUE_IF_UNSET;
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return val;
    }
}
