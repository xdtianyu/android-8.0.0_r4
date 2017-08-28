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

import android.app.Notification.Builder;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import com.android.networkrecommendation.R;

/**
 * Util class for managing {@link android.app.NotificationChannel}s for our notifications.
 * TODO(netrec): b/36486429 Add tests for this class once relevant class constructors are supported
 * for Robolectric.
 */
public class NotificationChannelUtil {

    private static final String CHANNEL_GROUP_ID =
            "com.android.networkrecommendation.Notifications.WifiMessageGroup";
    public static final String CHANNEL_ID_NETWORK_AVAILABLE =
            "com.android.networkrecommendation.Notifications.WifiMessageGroup.NetworkAvailableChannel";
    public static final String CHANNEL_ID_WAKEUP =
            "com.android.networkrecommendation.Notifications.WifiMessageGroup.WakeupChannel";

    /** Configures the {@link android.app.NotificationChannel}s for our module. */
    public static void configureNotificationChannels(
            NotificationManager notificationManager, Context context) {
        NotificationChannelGroup notificationChannelGroup =
                new NotificationChannelGroup(
                        CHANNEL_GROUP_ID,
                        context.getString(R.string.notification_channel_group_name));
        notificationManager.createNotificationChannelGroup(notificationChannelGroup);

        NotificationChannel networkAvailableChannel =
                new NotificationChannel(
                        CHANNEL_ID_NETWORK_AVAILABLE,
                        context.getString(R.string.notification_channel_network_available),
                        NotificationManager.IMPORTANCE_LOW);
        networkAvailableChannel.setGroup(CHANNEL_GROUP_ID);
        notificationManager.createNotificationChannel(networkAvailableChannel);

        NotificationChannel wakeupChannel =
                new NotificationChannel(
                        CHANNEL_ID_WAKEUP,
                        context.getString(R.string.notification_channel_wakeup_name),
                        NotificationManager.IMPORTANCE_LOW);
        wakeupChannel.setGroup(CHANNEL_GROUP_ID);
        notificationManager.createNotificationChannel(wakeupChannel);
    }

    /** Wraps Notification.Builder.setChannel. */
    public static Builder setChannel(Builder notificationBuilder, String channelId) {
        return notificationBuilder.setChannelId(channelId);
    }
}
