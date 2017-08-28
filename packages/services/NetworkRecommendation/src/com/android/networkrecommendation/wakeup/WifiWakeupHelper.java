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
import static com.android.networkrecommendation.util.NotificationChannelUtil.CHANNEL_ID_WAKEUP;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.ArraySet;
import com.android.networkrecommendation.R;
import com.android.networkrecommendation.config.G;
import com.android.networkrecommendation.config.Preferences;
import com.android.networkrecommendation.scoring.util.HashUtil;
import com.android.networkrecommendation.util.Blog;
import com.android.networkrecommendation.util.NotificationChannelUtil;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for logging Wi-Fi Wakeup sessions and showing showing notifications for {@link
 * WifiWakeupController}.
 */
public class WifiWakeupHelper {
    /** Unique ID used for the Wi-Fi Enabled notification. */
    private static final int NOTIFICATION_ID = R.string.wifi_wakeup_enabled_notification_title;

    @VisibleForTesting
    static final String ACTION_WIFI_SETTINGS =
            "com.android.networkrecommendation.wakeup.ACTION_WIFI_SETTINGS";

    @VisibleForTesting
    static final String ACTION_DISMISS_WIFI_ENABLED_NOTIFICATION =
            "com.android.networkrecommendation.wakeup.ACTION_DISMISS_WIFI_ENABLED_NOTIFICATION";

    private static final long NETWORK_CONNECTED_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final IntentFilter INTENT_FILTER = new IntentFilter();

    static {
        INTENT_FILTER.addAction(ACTION_DISMISS_WIFI_ENABLED_NOTIFICATION);
        INTENT_FILTER.addAction(ACTION_WIFI_SETTINGS);
        INTENT_FILTER.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
    }

    private final Context mContext;
    private final Resources mResources;
    private final NotificationManager mNotificationManager;
    private final Handler mHandler;
    private final WifiManager mWifiManager;

    /** Whether the wakeup notification is currently displayed. */
    private boolean mNotificationShown;
    /** True when the device is still connected to the first connected ssid since wakeup. */
    private boolean mWifiSessionStarted;
    /** The first connected ssid after wakeup enabled wifi. */
    private String mConnectedSsid;

    private final BroadcastReceiver mBroadcastReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        if (ACTION_WIFI_SETTINGS.equals(intent.getAction())) {
                            mContext.startActivity(
                                    new Intent(Settings.ACTION_WIFI_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } else if (ACTION_DISMISS_WIFI_ENABLED_NOTIFICATION.equals(
                                intent.getAction())) {
                            cancelNotificationIfNeeded();
                        } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(
                                intent.getAction())) {
                            networkStateChanged();
                        }
                    } catch (RuntimeException re) {
                        // TODO(b/35044022) Remove try/catch after a couple of releases when we are confident
                        // this is not going to throw.
                        Blog.e(TAG, re, "RuntimeException in broadcast receiver.");
                    }
                }
            };

    public WifiWakeupHelper(
            Context context,
            Resources resources,
            Handler handler,
            NotificationManager notificationManager,
            WifiManager wifiManager) {
        mContext = context;
        mResources = resources;
        mNotificationManager = notificationManager;
        mHandler = handler;
        mWifiManager = wifiManager;
        mWifiSessionStarted = false;
        mNotificationShown = false;
        mConnectedSsid = null;
    }

    /**
     * Start tracking a wifi wakeup session. Optionally show a notification that Wi-Fi has been
     * enabled by Wi-Fi Wakeup if one has not been displayed for this {@link WifiConfiguration}.
     *
     * @param wifiConfiguration the {@link WifiConfiguration} that triggered Wi-Fi to wakeup
     */
    public void startWifiSession(@NonNull WifiConfiguration wifiConfiguration) {
        mContext.registerReceiver(
                mBroadcastReceiver, INTENT_FILTER, null /* broadcastPermission*/, mHandler);
        mWifiSessionStarted = true;
        mHandler.postDelayed(
                () -> {
                    if (mWifiSessionStarted && mConnectedSsid == null) {
                        endWifiSession();
                    }
                },
                NETWORK_CONNECTED_TIMEOUT_MILLIS);

        Set<String> hashedSsidSet = Preferences.ssidsForWakeupShown.get();
        String hashedSsid = HashUtil.getSsidHash(wifiConfiguration.SSID);
        if (hashedSsidSet.isEmpty()) {
            hashedSsidSet = new ArraySet<>();
        } else if (hashedSsidSet.contains(hashedSsid)) {
            Blog.i(
                    TAG,
                    "Already showed Wi-Fi Enabled notification for ssid: %s",
                    Blog.pii(wifiConfiguration.SSID, G.Netrec.enableSensitiveLogging.get()));
            return;
        }
        hashedSsidSet.add(hashedSsid);
        Preferences.ssidsForWakeupShown.put(hashedSsidSet);

        String title = mResources.getString(R.string.wifi_wakeup_enabled_notification_title);
        String summary =
                mResources.getString(
                        R.string.wifi_wakeup_enabled_notification_context, wifiConfiguration.SSID);
        PendingIntent savedNetworkSettingsPendingIntent =
                PendingIntent.getBroadcast(
                        mContext,
                        0,
                        new Intent(ACTION_WIFI_SETTINGS),
                        PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent deletePendingIntent =
                PendingIntent.getBroadcast(
                        mContext,
                        0,
                        new Intent(ACTION_DISMISS_WIFI_ENABLED_NOTIFICATION),
                        PendingIntent.FLAG_UPDATE_CURRENT);
        Bundle extras = new Bundle();
        extras.putString(
                Notification.EXTRA_SUBSTITUTE_APP_NAME,
                mResources.getString(R.string.notification_channel_group_name));
        int smallIcon = R.drawable.ic_signal_wifi_statusbar_not_connected;
        Notification.Builder notificationBuilder =
                new Notification.Builder(mContext)
                        .setContentTitle(title)
                        .setSmallIcon(smallIcon)
                        .setColor(mContext.getColor(R.color.color_tint))
                        .setStyle(new Notification.BigTextStyle().bigText(summary))
                        .setAutoCancel(true)
                        .setShowWhen(false)
                        .setDeleteIntent(deletePendingIntent)
                        .setPriority(Notification.PRIORITY_LOW)
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setCategory(Notification.CATEGORY_STATUS)
                        .setContentIntent(savedNetworkSettingsPendingIntent)
                        .setLocalOnly(true)
                        .addExtras(extras);
        NotificationChannelUtil.setChannel(notificationBuilder, CHANNEL_ID_WAKEUP);
        mNotificationManager.notify(TAG, NOTIFICATION_ID, notificationBuilder.build());
        mNotificationShown = true;
    }

    private void networkStateChanged() {
        if (!mWifiManager.isWifiEnabled()) {
            endWifiSession();
            return;
        }

        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        String ssid = wifiInfo == null ? null : wifiInfo.getSSID();
        if (mConnectedSsid == null) {
            if (!TextUtils.isEmpty(ssid)) {
                mConnectedSsid = ssid;
            }
        } else if (!TextUtils.equals(ssid, mConnectedSsid)) {
            endWifiSession();
        }
    }

    private void endWifiSession() {
        if (mWifiSessionStarted) {
            mWifiSessionStarted = false;
            cancelNotificationIfNeeded();
            mConnectedSsid = null;
            mContext.unregisterReceiver(mBroadcastReceiver);
        }
    }

    private void cancelNotificationIfNeeded() {
        if (mNotificationShown) {
            mNotificationShown = false;
            mNotificationManager.cancel(TAG, NOTIFICATION_ID);
        }
    }
}
