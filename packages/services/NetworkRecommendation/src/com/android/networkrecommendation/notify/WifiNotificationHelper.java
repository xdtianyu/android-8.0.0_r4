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
package com.android.networkrecommendation.notify;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static com.android.networkrecommendation.util.NotificationChannelUtil.CHANNEL_ID_NETWORK_AVAILABLE;

import android.app.Notification;
import android.app.Notification.Action;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;
import com.android.networkrecommendation.R;
import com.android.networkrecommendation.util.NotificationChannelUtil;

/** Helper class that creates notifications for {@link WifiNotificationController}. */
public class WifiNotificationHelper {
    private final Context mContext;

    public WifiNotificationHelper(Context context) {
        mContext = context;
    }

    /**
     * Creates the main open networks notification with two actions. "Options" link to the Wi-Fi
     * picker activity, and "Connect" prompts {@link WifiNotificationController} to connect to the
     * recommended network. Tapping on the content body connects to the recommended network and
     * opens the wifi picker
     */
    public Notification createMainNotification(WifiConfiguration config) {
        PendingIntent allNetworksIntent =
                PendingIntent.getBroadcast(
                        mContext,
                        0,
                        new Intent(WifiNotificationController.ACTION_PICK_WIFI_NETWORK),
                        FLAG_UPDATE_CURRENT);
        Action allNetworksAction =
                new Action.Builder(
                                null /* icon */,
                                mContext.getText(R.string.wifi_available_action_all_networks),
                                allNetworksIntent)
                        .build();
        PendingIntent connectIntent =
                PendingIntent.getBroadcast(
                        mContext,
                        0,
                        new Intent(
                                WifiNotificationController.ACTION_CONNECT_TO_RECOMMENDED_NETWORK),
                        FLAG_UPDATE_CURRENT);
        Action connectAction =
                new Action.Builder(
                                null /* icon */,
                                mContext.getText(R.string.wifi_available_action_connect),
                                connectIntent)
                        .build();
        PendingIntent connectAndOpenPickerIntent =
                PendingIntent.getBroadcast(
                        mContext,
                        0,
                        new Intent(
                                WifiNotificationController
                                        .ACTION_CONNECT_TO_RECOMMENDED_NETWORK_AND_OPEN_SETTINGS),
                        FLAG_UPDATE_CURRENT);
        return createNotificationBuilder(R.string.wifi_available_title, config.SSID)
                .setContentIntent(connectAndOpenPickerIntent)
                .addAction(connectAction)
                .addAction(allNetworksAction)
                .build();
    }

    /**
     * Creates the notification that indicates the controller is attempting to connect to the
     * recommended network.
     */
    public Notification createConnectingNotification(WifiConfiguration config) {
        return createNotificationBuilder(R.string.wifi_available_title_connecting, config.SSID)
                .setProgress(0 /* max */, 0 /* progress */, true /* indeterminate */)
                .build();
    }

    /**
     * Creates the notification that indicates the controller successfully connected to the
     * recommended network.
     */
    public Notification createConnectedNotification(WifiConfiguration config) {
        return createNotificationBuilder(R.string.wifi_available_title_connected, config.SSID)
                .setAutoCancel(true)
                .build();
    }

    /**
     * Creates the notification that indicates the controller failed to connect to the recommended
     * network. Tapping this notification opens the wifi picker.
     */
    public Notification createFailedToConnectNotification() {
        PendingIntent openWifiPickerAfterFailure =
                PendingIntent.getBroadcast(
                        mContext,
                        0,
                        new Intent(
                                WifiNotificationController
                                        .ACTION_PICK_WIFI_NETWORK_AFTER_CONNECT_FAILURE),
                        FLAG_UPDATE_CURRENT);
        return createNotificationBuilder(
                        R.string.wifi_available_title_failed,
                        mContext.getString(R.string.wifi_available_content_failed))
                .setContentIntent(openWifiPickerAfterFailure)
                .setAutoCancel(true)
                .build();
    }

    private Notification.Builder createNotificationBuilder(int titleRid, String content) {
        CharSequence title = mContext.getText(titleRid);
        PendingIntent deleteIntent =
                PendingIntent.getBroadcast(
                        mContext,
                        0,
                        new Intent(WifiNotificationController.ACTION_NOTIFICATION_DELETED),
                        FLAG_UPDATE_CURRENT);
        int smallIcon = R.drawable.ic_signal_wifi_statusbar_not_connected;
        Notification.Builder builder =
                new Notification.Builder(mContext)
                        .setDeleteIntent(deleteIntent)
                        .setSmallIcon(smallIcon)
                        .setTicker(title)
                        .setContentTitle(title)
                        .setColor(mContext.getColor(R.color.color_tint))
                        .setContentText(content)
                        .setShowWhen(false)
                        .setLocalOnly(true)
                        .addExtras(getOverrideLabelExtras());
        return NotificationChannelUtil.setChannel(builder, CHANNEL_ID_NETWORK_AVAILABLE);
    }

    private Bundle getOverrideLabelExtras() {
        Bundle extras = new Bundle();
        extras.putString(
                Notification.EXTRA_SUBSTITUTE_APP_NAME,
                mContext.getString(R.string.notification_channel_group_name));
        return extras;
    }
}
