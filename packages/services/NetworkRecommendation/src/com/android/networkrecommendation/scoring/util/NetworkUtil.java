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
package com.android.networkrecommendation.scoring.util;

import static android.net.wifi.WifiConfiguration.KeyMgmt.IEEE8021X;
import static android.net.wifi.WifiConfiguration.KeyMgmt.WPA_EAP;
import static android.net.wifi.WifiConfiguration.KeyMgmt.WPA_PSK;
import static com.android.networkrecommendation.util.ScanResultUtil.isScanResultForOpenNetwork;

import android.content.Context;
import android.net.NetworkKey;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.android.networkrecommendation.Constants;
import com.android.networkrecommendation.util.Blog;
import com.android.networkrecommendation.util.ScanResultUtil;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Network Utils. */
public final class NetworkUtil {

    private NetworkUtil() {
        // do not instantiate
    }

    /**
     * Canonicalize the given SSID returned by WifiInfo#getSSID().
     *
     * <p>This method should only be called once on a given SSID! If an SSID contains outer quotes,
     * we will strip them twice and change the SSID to a different one.
     *
     * <p>The SSID should be returned surrounded by double quotation marks if it is valid UTF-8.
     * This behavior was only implemented correctly after
     * https://googleplex-android-review.googlesource.com/#/c/224602/ which went into JB-MR1.
     *
     * <p>This method does not account for non-UTF-8 SSIDs, which are returned as a string of hex
     * digits from getSSID().
     *
     * <p>For more details, see: http://stackoverflow.com/questions/13563032
     */
    public static String canonicalizeSsid(String ssid) {
        if (ssid == null) {
            return null;
        }
        return removeQuotesIfNeeded(ssid);
    }

    /** Remove the leading quote and trailing quote. */
    private static String removeQuotesIfNeeded(String text) {
        if (text.length() > 1 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    /**
     * @return a map from NetworkKey to true if that network is open, and false otherwise, for all
     *     visible networks in the last set of Wi-Fi scan results.
     */
    public static Map<NetworkKey, Boolean> getOpenWifiNetworkKeys(Context context) {
        WifiManager wifiMgr = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        List<ScanResult> scanResults = null;
        if (Util.isScorerActive(context)) {
            try {
                scanResults = wifiMgr.getScanResults();
            } catch (SecurityException e) {
                Blog.w(Constants.TAG, e, "No permission to get scan results");
                scanResults = null;
            }
        }
        if (scanResults == null) {
            return Collections.emptyMap();
        }
        Map<NetworkKey, Boolean> openKeys = new HashMap<>();
        for (int i = 0; i < scanResults.size(); i++) {
            ScanResult scanResult = scanResults.get(i);
            NetworkKey networkKey = ScanResultUtil.createNetworkKey(scanResult);
            if (networkKey != null) {
                openKeys.put(networkKey, isScanResultForOpenNetwork(scanResult));
            }
        }
        return openKeys;
    }

    /** Returns true if the given config is for an "open" network. */
    public static boolean isOpenNetwork(@NonNull WifiConfiguration config) {
        if (config.allowedKeyManagement.get(WPA_PSK) // covers WPA_PSK and WPA2_PSK
                || config.allowedKeyManagement.get(WPA_EAP)
                || config.allowedKeyManagement.get(IEEE8021X)
                || (config.wepKeys != null && config.wepKeys[0] != null)) {
            return false;
        }
        return true;
    }

    @NonNull
    public static String getCurrentWifiSsid(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        return wifiInfo == null ? "" : wifiInfo.getSSID();
    }

    /** Returns the config for the given SSID, or null if one cannot be found. */
    @Nullable
    public static WifiConfiguration getConfig(Context context, String ssid) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
        if (configs == null) {
            return null;
        }

        WifiConfiguration config;
        for (int i = 0; i < configs.size(); i++) {
            config = configs.get(i);
            if (TextUtils.equals(ssid, config.SSID)) {
                return config;
            }
        }
        return null;
    }
}
