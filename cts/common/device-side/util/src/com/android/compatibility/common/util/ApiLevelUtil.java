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

/**
 * Device-side compatibility utility class for reading device API level.
 */
public class ApiLevelUtil {

    public static boolean isBefore(int version) {
        return Build.VERSION.SDK_INT < version;
    }

    public static boolean isAfter(int version) {
        return Build.VERSION.SDK_INT > version;
    }

    public static boolean isAtLeast(int version) {
        return Build.VERSION.SDK_INT >= version;
    }

    public static boolean isAtMost(int version) {
        return Build.VERSION.SDK_INT <= version;
    }

    public static int getApiLevel() {
        return Build.VERSION.SDK_INT;
    }

    public static boolean codenameEquals(String name) {
        return Build.VERSION.CODENAME.equalsIgnoreCase(name.trim());
    }

    public static boolean codenameStartsWith(String prefix) {
        return Build.VERSION.CODENAME.startsWith(prefix);
    }

    public static String getCodename() {
        return Build.VERSION.CODENAME;
    }
}
