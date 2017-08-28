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

import static com.android.networkrecommendation.Constants.TAG;
import static com.android.networkrecommendation.util.SsidUtil.quoteSsid;

import android.net.NetworkKey;
import android.net.WifiKey;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.android.networkrecommendation.config.G;

/**
 * Scan result utility for any {@link ScanResult} related operations. TODO(b/34125341): Delete this
 * class once exposed as a SystemApi
 */
public class ScanResultUtil {

    /**
     * Helper method to check if the provided |scanResult| corresponds to a PSK network or not. This
     * checks if the provided capabilities string contains PSK encryption type or not.
     */
    public static boolean isScanResultForPskNetwork(ScanResult scanResult) {
        return scanResult.capabilities.contains("PSK");
    }

    /**
     * Helper method to check if the provided |scanResult| corresponds to a EAP network or not. This
     * checks if the provided capabilities string contains EAP encryption type or not.
     */
    public static boolean isScanResultForEapNetwork(ScanResult scanResult) {
        return scanResult.capabilities.contains("EAP");
    }

    /**
     * Helper method to check if the provided |scanResult| corresponds to a WEP network or not. This
     * checks if the provided capabilities string contains WEP encryption type or not.
     */
    public static boolean isScanResultForWepNetwork(ScanResult scanResult) {
        return scanResult.capabilities.contains("WEP");
    }

    /**
     * Helper method to check if the provided |scanResult| corresponds to an open network or not.
     * This checks if the provided capabilities string does not contain either of WEP, PSK or EAP
     * encryption types or not.
     */
    public static boolean isScanResultForOpenNetwork(ScanResult scanResult) {
        return !(isScanResultForWepNetwork(scanResult)
                || isScanResultForPskNetwork(scanResult)
                || isScanResultForEapNetwork(scanResult));
    }

    /** Create a {@link NetworkKey} from a ScanResult, properly quoting the SSID. */
    @Nullable
    public static final NetworkKey createNetworkKey(ScanResult scanResult) {
        WifiKey wifiKey = createWifiKey(scanResult);
        if (wifiKey == null) {
            return null;
        }
        return new NetworkKey(wifiKey);
    }

    /**
     * Helper method to quote the SSID in Scan result to use for comparing/filling SSID stored in
     * WifiConfiguration object.
     */
    @Nullable
    public static WifiKey createWifiKey(ScanResult result) {
        if (result == null) {
            Blog.e(TAG, "Couldn't create WifiKey, provided scan result is null.");
            return null;
        }
        try {
            return new WifiKey(quoteSsid(result.SSID), result.BSSID);
        } catch (IllegalArgumentException | NullPointerException e) {
            // Expect IllegalArgumentException only in Android O.
            Blog.e(
                    TAG,
                    e,
                    "Couldn't make a wifi key from %s/%s",
                    Blog.pii(result.SSID, G.Netrec.enableSensitiveLogging.get()),
                    Blog.pii(result.BSSID, G.Netrec.enableSensitiveLogging.get()));
            return null;
        }
    }

    /** @return {@code true} if the result is for a 2.4GHz network. */
    public static boolean is24GHz(ScanResult result) {
        return is24GHz(result.frequency);
    }

    /** @return {@code true} if the frequency is for a 2.4GHz network. */
    public static boolean is24GHz(int freq) {
        return freq > 2400 && freq < 2500;
    }

    /** @return {@code true} if the result is for a 5GHz network. */
    public static boolean is5GHz(ScanResult result) {
        return is5GHz(result.frequency);
    }

    /** @return {@code true} if the frequency is for a 5GHz network. */
    public static boolean is5GHz(int freq) {
        return freq > 4900 && freq < 5900;
    }

    /**
     * Checks if the provided |scanResult| match with the provided |config|. Essentially checks if
     * the network config and scan result have the same SSID and encryption type.
     */
    public static boolean doesScanResultMatchWithNetwork(
            ScanResult scanResult, WifiConfiguration config) {
        // Add the double quotes to the scan result SSID for comparison with the network configs.
        String configSSID = quoteSsid(scanResult.SSID);
        if (TextUtils.equals(config.SSID, configSSID)) {
            if (ScanResultUtil.isScanResultForPskNetwork(scanResult)
                    && WifiConfigurationUtil.isConfigForPskNetwork(config)) {
                return true;
            }
            if (ScanResultUtil.isScanResultForEapNetwork(scanResult)
                    && WifiConfigurationUtil.isConfigForEapNetwork(config)) {
                return true;
            }
            if (ScanResultUtil.isScanResultForWepNetwork(scanResult)
                    && WifiConfigurationUtil.isConfigForWepNetwork(config)) {
                return true;
            }
            if (ScanResultUtil.isScanResultForOpenNetwork(scanResult)
                    && WifiConfigurationUtil.isConfigForOpenNetwork(config)) {
                return true;
            }
        }
        return false;
    }
}
