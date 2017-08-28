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
package com.android.networkrecommendation.wakeup;

import static com.android.networkrecommendation.Constants.TAG;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.PowerManager;
import android.os.UserManager;
import android.provider.Settings;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import com.android.networkrecommendation.config.G;
import com.android.networkrecommendation.config.Preferences;
import com.android.networkrecommendation.config.WideAreaNetworks;
import com.android.networkrecommendation.scoring.util.HashUtil;
import com.android.networkrecommendation.util.Blog;
import com.android.networkrecommendation.util.RoboCompatUtil;
import com.android.networkrecommendation.util.WifiConfigurationUtil;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles enabling Wi-Fi for the Wi-Fi Wakeup feature.
 *
 * <p>This class enables Wi-Fi when the user is near a network that would would autojoined if Wi-Fi
 * were enabled. When a user disables Wi-Fi, Wi-Fi Wakeup will not enable Wi-Fi until the user's
 * context has changed. For saved networks, this context change is defined by the user leaving the
 * range of the saved SSIDs that were in range when the user disabled Wi-Fi.
 *
 * @hide
 */
public class WifiWakeupController {
    /** Number of scans to ensure that a previously in range AP is now out of range. */
    private static final int NUM_SCANS_TO_CONFIRM_AP_LOSS = 3;

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final WifiManager mWifiManager;
    private final PowerManager mPowerManager;
    private final UserManager mUserManager;
    private final WifiWakeupNetworkSelector mWifiWakeupNetworkSelector;
    private final Handler mHandler;
    private final WifiWakeupHelper mWifiWakeupHelper;
    private final AtomicBoolean mStarted;
    @VisibleForTesting final ContentObserver mContentObserver;

    private final Map<String, WifiConfiguration> mSavedNetworks = new ArrayMap<>();
    private final Set<String> mSavedSsidsInLastScan = new ArraySet<>();
    private final Set<String> mSavedSsids = new ArraySet<>();
    private final Map<String, Integer> mSavedSsidsOnDisable = new ArrayMap<>();
    private final SavedNetworkCounts mSavedNetworkCounts = new SavedNetworkCounts();
    private int mWifiState;
    private int mWifiApState;
    private boolean mWifiWakeupEnabled;
    private boolean mAirplaneModeEnabled;
    private boolean mAutopilotEnabledWifi;
    private boolean mPowerSaverModeOn;
    private boolean mWifiConfigRestricted;

    public WifiWakeupController(
            Context context,
            ContentResolver contentResolver,
            Handler handler,
            WifiManager wifiManager,
            PowerManager powerManager,
            UserManager userManager,
            WifiWakeupNetworkSelector wifiWakeupNetworkSelector,
            WifiWakeupHelper wifiWakeupHelper) {
        mContext = context;
        mContentResolver = contentResolver;
        mHandler = handler;
        mWifiWakeupHelper = wifiWakeupHelper;
        mStarted = new AtomicBoolean(false);
        mWifiManager = wifiManager;
        mPowerManager = powerManager;
        mUserManager = userManager;
        mWifiWakeupNetworkSelector = wifiWakeupNetworkSelector;
        mContentObserver =
                new ContentObserver(mHandler) {
                    @Override
                    public void onChange(boolean selfChange) {
                        mWifiWakeupEnabled =
                                Settings.Global.getInt(
                                                mContentResolver,
                                                Settings.Global.WIFI_WAKEUP_ENABLED,
                                                0)
                                        == 1;
                        mAirplaneModeEnabled =
                                Settings.Global.getInt(
                                                mContentResolver,
                                                Settings.Global.AIRPLANE_MODE_ON,
                                                0)
                                        == 1;
                        Blog.d(
                                TAG,
                                "onChange: [mWifiWakeupEnabled=%b,mAirplaneModeEnabled=%b]",
                                mWifiWakeupEnabled,
                                mAirplaneModeEnabled);
                    }
                };
    }

    private final BroadcastReceiver mBroadcastReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                            handleWifiApStateChanged();
                        } else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(
                                intent.getAction())) {
                            handleWifiStateChanged(false);
                        } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(
                                intent.getAction())) {
                            handleScanResultsAvailable();
                        } else if (WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION.equals(
                                intent.getAction())) {
                            handleConfiguredNetworksChanged();
                        } else if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(
                                intent.getAction())) {
                            handlePowerSaverModeChanged();
                        } else if (RoboCompatUtil.ACTION_USER_RESTRICTIONS_CHANGED.equals(
                                intent.getAction())) {
                            handleUserRestrictionsChanged();
                        }
                    } catch (RuntimeException re) {
                        // TODO(b/35044022) Remove try/catch after a couple of releases when we are confident
                        // this is not going to throw.
                        Blog.e(TAG, re, "RuntimeException in broadcast receiver.");
                    }
                }
            };

    /** Starts {@link WifiWakeupController}. */
    public void start() {
        if (!mStarted.compareAndSet(false, true)) {
            return;
        }
        Blog.d(TAG, "Starting WifiWakeupController.");

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        filter.addAction(RoboCompatUtil.ACTION_USER_RESTRICTIONS_CHANGED);
        // TODO(b/33695273): conditionally register this receiver based on wifi enabled setting
        mContext.registerReceiver(mBroadcastReceiver, filter, null, mHandler);
        mContentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.WIFI_WAKEUP_ENABLED),
                true,
                mContentObserver);
        mContentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON),
                true,
                mContentObserver);
        mContentObserver.onChange(true);
        handlePowerSaverModeChanged();
        handleUserRestrictionsChanged();
        handleWifiApStateChanged();
        handleConfiguredNetworksChanged();
        handleWifiStateChanged(true);
        handleScanResultsAvailable();
    }

    /** Stops {@link WifiWakeupController}. */
    public void stop() {
        if (!mStarted.compareAndSet(true, false)) {
            return;
        }
        Blog.d(TAG, "Stopping WifiWakeupController.");
        mContext.unregisterReceiver(mBroadcastReceiver);
        mContentResolver.unregisterContentObserver(mContentObserver);
    }

    private void handlePowerSaverModeChanged() {
        mPowerSaverModeOn = mPowerManager.isPowerSaveMode();
        Blog.v(TAG, "handlePowerSaverModeChanged: %b", mPowerSaverModeOn);
    }

    private void handleWifiApStateChanged() {
        mWifiApState = mWifiManager.getWifiApState();
        Blog.v(TAG, "handleWifiApStateChanged: %d", mWifiApState);
    }

    private void handleUserRestrictionsChanged() {
        mWifiConfigRestricted = mUserManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_WIFI);
        Blog.v(TAG, "handleUserRestrictionsChanged: %b", mWifiConfigRestricted);
    }

    private void handleConfiguredNetworksChanged() {
        List<WifiConfiguration> wifiConfigurations = mWifiManager.getConfiguredNetworks();
        if (wifiConfigurations == null) {
            return;
        }
        Blog.v(TAG, "handleConfiguredNetworksChanged: %d", wifiConfigurations.size());

        mSavedNetworkCounts.clear();
        mSavedNetworkCounts.total = wifiConfigurations.size();
        mSavedNetworks.clear();
        mSavedSsids.clear();
        for (int i = 0; i < wifiConfigurations.size(); i++) {
            WifiConfiguration wifiConfiguration = wifiConfigurations.get(i);
            if (wifiConfiguration.status != WifiConfiguration.Status.ENABLED
                    && wifiConfiguration.status != WifiConfiguration.Status.CURRENT) {
                continue; // Ignore networks that are not connected or enabled.
            }
            mSavedNetworkCounts.enabled++;
            if (RoboCompatUtil.getInstance().hasNoInternetAccess(wifiConfiguration)) {
                mSavedNetworkCounts.noInternetAccess++;
                continue; // Ignore networks that do not have verified internet access.
            }
            if (RoboCompatUtil.getInstance().isNoInternetAccessExpected(wifiConfiguration)) {
                mSavedNetworkCounts.noInternetAccessExpected++;
                continue; // Ignore networks that are expected not to have internet access.
            }
            String ssid = WifiConfigurationUtil.removeDoubleQuotes(wifiConfiguration);
            if (TextUtils.isEmpty(ssid)) {
                continue;
            }
            if (WideAreaNetworks.contains(ssid)) {
                mSavedNetworkCounts.blacklisted++;
                continue; // Ignore wide area networks.
            }
            mSavedNetworks.put(ssid, wifiConfiguration);
            mSavedSsids.add(ssid);

            if (WifiConfigurationUtil.isConfigForOpenNetwork(wifiConfiguration)) {
                mSavedNetworkCounts.open++;
            }
            if (RoboCompatUtil.getInstance().useExternalScores(wifiConfiguration)) {
                mSavedNetworkCounts.useExternalScores++;
            }
        }
        mSavedSsidsInLastScan.retainAll(mSavedSsids);
    }

    private void handleWifiStateChanged(boolean calledOnStart) {
        mWifiState = mWifiManager.getWifiState();
        Blog.v(TAG, "handleWifiStateChanged: %d", mWifiState);

        switch (mWifiState) {
            case WifiManager.WIFI_STATE_ENABLED:
                mSavedSsidsOnDisable.clear();
                if (!mAutopilotEnabledWifi) {}
                break;
            case WifiManager.WIFI_STATE_DISABLED:
                if (calledOnStart) {
                    readDisabledSsidsFromSharedPreferences();
                } else {
                    for (String ssid : mSavedSsidsInLastScan) {
                        mSavedSsidsOnDisable.put(ssid, NUM_SCANS_TO_CONFIRM_AP_LOSS);
                    }
                    writeDisabledSsidsToSharedPreferences();
                }
                Blog.d(TAG, "Disabled ssid set: %s", mSavedSsidsOnDisable);

                mAutopilotEnabledWifi = false;
                break;
            default: // Only handle ENABLED and DISABLED states
        }
    }

    private void readDisabledSsidsFromSharedPreferences() {
        Set<String> ssidsOnDisable = Preferences.savedSsidsOnDisable.get();
        for (String ssid : mSavedSsids) {
            if (ssidsOnDisable.contains(HashUtil.getSsidHash(ssid))) {
                mSavedSsidsOnDisable.put(ssid, NUM_SCANS_TO_CONFIRM_AP_LOSS);
            }
        }
    }

    private void writeDisabledSsidsToSharedPreferences() {
        Set<String> ssids = new ArraySet<>();
        for (String ssid : mSavedSsidsOnDisable.keySet()) {
            ssids.add(HashUtil.getSsidHash(ssid));
        }
        Preferences.savedSsidsOnDisable.put(ssids);
    }

    private void handleScanResultsAvailable() {
        if (!mWifiWakeupEnabled || mWifiConfigRestricted) {
            return;
        }
        List<ScanResult> scanResults = mWifiManager.getScanResults();
        if (scanResults == null) {
            return;
        }
        Blog.v(TAG, "handleScanResultsAvailable: %d", scanResults.size());

        mSavedSsidsInLastScan.clear();
        for (int i = 0; i < scanResults.size(); i++) {
            String ssid = scanResults.get(i).SSID;
            if (mSavedSsids.contains(ssid)) {
                mSavedSsidsInLastScan.add(ssid);
            }
        }

        if (mAirplaneModeEnabled
                || mWifiState != WifiManager.WIFI_STATE_DISABLED
                || mWifiApState != WifiManager.WIFI_AP_STATE_DISABLED
                || mPowerSaverModeOn) {
            return;
        }

        // Update mSavedSsidsOnDisable to remove ssids that the user has moved away from.
        for (Map.Entry<String, Integer> entry : mSavedSsidsOnDisable.entrySet()) {
            if (mSavedSsidsInLastScan.contains(entry.getKey())) {
                mSavedSsidsOnDisable.put(entry.getKey(), NUM_SCANS_TO_CONFIRM_AP_LOSS);
            } else {
                if (entry.getValue() > 1) {
                    mSavedSsidsOnDisable.put(entry.getKey(), entry.getValue() - 1);
                } else {
                    mSavedSsidsOnDisable.remove(entry.getKey());
                }
            }
        }

        if (!mSavedSsidsOnDisable.isEmpty()) {
            Blog.d(
                    TAG,
                    "Scan results contain ssids from the disabled set: %s",
                    mSavedSsidsOnDisable);
            return;
        }

        if (mSavedSsidsInLastScan.isEmpty()) {
            Blog.v(TAG, "Scan results do not contain any saved ssids.");
            return;
        }

        WifiConfiguration selectedNetwork =
                mWifiWakeupNetworkSelector.selectNetwork(mSavedNetworks, scanResults);
        if (selectedNetwork != null) {
            Blog.d(
                    TAG,
                    "Enabling wifi for ssid: %s",
                    Blog.pii(selectedNetwork.SSID, G.Netrec.enableSensitiveLogging.get()));

            mAutopilotEnabledWifi = true;
            mWifiManager.setWifiEnabled(true /* enabled */);
            mWifiWakeupHelper.startWifiSession(selectedNetwork);
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("mStarted " + mStarted.get());
        pw.println("mWifiWakeupEnabled: " + mWifiWakeupEnabled);
        pw.println("mSavedSsids: " + mSavedSsids);
        pw.println("mSavedSsidsInLastScan: " + mSavedSsidsInLastScan);
        pw.println("mSavedSsidsOnDisable: " + mSavedSsidsOnDisable);
    }

    /** Class to track counts for saved networks for logging. */
    private static class SavedNetworkCounts {
        int total;
        int open;
        int enabled;
        int noInternetAccess;
        int noInternetAccessExpected;
        int useExternalScores;
        int blacklisted;

        void clear() {
            total = 0;
            open = 0;
            enabled = 0;
            noInternetAccess = 0;
            noInternetAccessExpected = 0;
            useExternalScores = 0;
            blacklisted = 0;
        }
    }
}
