/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.networkrecommendation;

import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.net.NetworkScoreManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.UserManager;

import com.android.networkrecommendation.notify.WifiNotificationController;
import com.android.networkrecommendation.notify.WifiNotificationHelper;
import com.android.networkrecommendation.util.NotificationChannelUtil;
import com.android.networkrecommendation.wakeup.WifiWakeupController;
import com.android.networkrecommendation.wakeup.WifiWakeupHelper;
import com.android.networkrecommendation.wakeup.WifiWakeupNetworkSelector;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Provides network recommendations for the platform.
 */
public class NetworkRecommendationService extends Service {

    private HandlerThread mProviderHandlerThread;
    private Handler mProviderHandler;
    private HandlerThread mControllerHandlerThread;
    private Handler mControllerHandler;
    private DefaultNetworkRecommendationProvider mProvider;
    private WifiNotificationController mWifiNotificationController;
    private WifiWakeupController mWifiWakeupController;

    @Override
    public void onCreate() {
        mProviderHandlerThread = new HandlerThread("RecommendationProvider");
        mProviderHandlerThread.start();
        mProviderHandler = new Handler(mProviderHandlerThread.getLooper());
        NetworkScoreManager networkScoreManager = getSystemService(NetworkScoreManager.class);
        mProvider = new DefaultNetworkRecommendationProvider(this, mProviderHandler::post,
                networkScoreManager, new DefaultNetworkRecommendationProvider.ScoreStorage());

        mControllerHandlerThread = new HandlerThread("RecommendationProvider");
        mControllerHandlerThread.start();
        mControllerHandler = new Handler(mControllerHandlerThread.getLooper());
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        NotificationChannelUtil.configureNotificationChannels(notificationManager, this);

        WifiManager wifiManager = getSystemService(WifiManager.class);
        PowerManager powerManager = getSystemService(PowerManager.class);
        UserManager userManager = getSystemService(UserManager.class);
        Resources resources = getResources();
        ContentResolver contentResolver = getContentResolver();
        mWifiNotificationController = new WifiNotificationController(
                this, contentResolver, mControllerHandler, mProvider,
                wifiManager, notificationManager, userManager, new WifiNotificationHelper(this));
        WifiWakeupNetworkSelector wifiWakeupNetworkSelector =
                new WifiWakeupNetworkSelector(resources, mProvider);
        WifiWakeupHelper wifiWakeupHelper = new WifiWakeupHelper(this, resources, mControllerHandler,
                notificationManager, wifiManager);
        mWifiWakeupController =
                new WifiWakeupController(this, getContentResolver(), mControllerHandler, wifiManager,
                        powerManager, userManager, wifiWakeupNetworkSelector, wifiWakeupHelper);
    }

    @Override
    public IBinder onBind(Intent intent) {
        mWifiWakeupController.start();
        mWifiNotificationController.start();
        return mProvider.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mWifiWakeupController.stop();
        mWifiNotificationController.stop();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        mProviderHandlerThread.quit();
        super.onDestroy();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        mProvider.dump(fd, writer, args);
        mWifiNotificationController.dump(fd, writer, args);
        mWifiWakeupController.dump(fd, writer, args);
    }
}
