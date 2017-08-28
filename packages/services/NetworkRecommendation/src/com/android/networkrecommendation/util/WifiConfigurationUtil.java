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

package com.android.networkrecommendation.util;

import android.net.wifi.WifiConfiguration;

/**
 * WifiConfiguration utility for any {@link WifiConfiguration} related operations..
 * TODO(b/34125341): Delete this class once exposed as a SystemApi
 */
public class WifiConfigurationUtil {
    /**
     * Checks if the provided |wepKeys| array contains any non-null value;
     */
    public static boolean hasAnyValidWepKey(String[] wepKeys) {
        for (int i = 0; i < wepKeys.length; i++) {
            if (wepKeys[i] != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper method to check if the provided |config| corresponds to a PSK network or not.
     */
    public static boolean isConfigForPskNetwork(WifiConfiguration config) {
        return config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK);
    }

    /**
     * Helper method to check if the provided |config| corresponds to a EAP network or not.
     */
    public static boolean isConfigForEapNetwork(WifiConfiguration config) {
        return (config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP)
                || config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.IEEE8021X));
    }

    /**
     * Helper method to check if the provided |config| corresponds to a WEP network or not.
     */
    public static boolean isConfigForWepNetwork(WifiConfiguration config) {
        return (config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE)
                && hasAnyValidWepKey(config.wepKeys));
    }

    /**
     * Helper method to check if the provided |config| corresponds to an open network or not.
     */
    public static boolean isConfigForOpenNetwork(WifiConfiguration config) {
        return !(isConfigForWepNetwork(config) || isConfigForPskNetwork(config)
                || isConfigForEapNetwork(config));
    }

    /** @return a ssid that can be shown to the user. */
    public static String getPrintableSsid(WifiConfiguration config) {
        if (config.SSID == null) return "";
        final int length = config.SSID.length();
        if (length > 2 && (config.SSID.charAt(0) == '"') && config.SSID.charAt(length - 1) == '"') {
            return config.SSID.substring(1, length - 1);
        }
        return config.SSID;
    }

    /** Removes " from the ssid in a wifi configuration (to match against a ScanResult). */
    public static String removeDoubleQuotes(WifiConfiguration config) {
        if (config.SSID == null) return null;
        final int length = config.SSID.length();
        if ((length > 1) && (config.SSID.charAt(0) == '"')
                && (config.SSID.charAt(length - 1) == '"')) {
            return config.SSID.substring(1, length - 1);
        }
        return config.SSID;
    }
}
