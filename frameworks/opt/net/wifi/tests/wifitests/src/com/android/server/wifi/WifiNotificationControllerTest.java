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

package com.android.server.wifi;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.test.TestLooper;
import android.provider.Settings;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link com.android.server.wifi.WifiScanningServiceImpl}.
 */
@SmallTest
public class WifiNotificationControllerTest {
    public static final String TAG = "WifiScanningServiceTest";

    @Mock private Context mContext;
    @Mock private Resources mResources;
    @Mock private FrameworkFacade mFrameworkFacade;
    @Mock private NotificationManager mNotificationManager;
    @Mock private UserManager mUserManager;
    @Mock private WifiInjector mWifiInjector;
    @Mock private WifiScanner mWifiScanner;
    WifiNotificationController mWifiNotificationController;

    /**
     * Internal BroadcastReceiver that WifiNotificationController uses to listen for broadcasts
     * this is initialized by calling startServiceAndLoadDriver
     */
    BroadcastReceiver mBroadcastReceiver;

    /** Initialize objects before each test run. */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemService(Context.NOTIFICATION_SERVICE))
                .thenReturn(mNotificationManager);

        when(mFrameworkFacade.getIntegerSetting(mContext,
                Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON, 1)).thenReturn(1);

        when(mContext.getSystemService(Context.USER_SERVICE))
                .thenReturn(mUserManager);
        when(mContext.getResources()).thenReturn(mResources);

        when(mWifiInjector.getWifiScanner()).thenReturn(mWifiScanner);

        TestLooper mock_looper = new TestLooper();
        mWifiNotificationController = new WifiNotificationController(
                mContext, mock_looper.getLooper(), mFrameworkFacade,
                mock(Notification.Builder.class), mWifiInjector);
        ArgumentCaptor<BroadcastReceiver> broadcastReceiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);

        verify(mContext)
                .registerReceiver(broadcastReceiverCaptor.capture(), any(IntentFilter.class));
        mBroadcastReceiver = broadcastReceiverCaptor.getValue();
    }

    private void setOpenAccessPoint() {
        List<ScanResult> scanResults = new ArrayList<>();
        ScanResult scanResult = new ScanResult();
        scanResult.capabilities = "[ESS]";
        scanResults.add(scanResult);
        when(mWifiScanner.getSingleScanResults()).thenReturn(scanResults);
    }

    /** Verifies that a notification is displayed (and retracted) given system events. */
    @Test
    public void verifyNotificationDisplayed() throws Exception {
        TestUtil.sendWifiStateChanged(mBroadcastReceiver, mContext, WifiManager.WIFI_STATE_ENABLED);
        TestUtil.sendNetworkStateChanged(mBroadcastReceiver, mContext,
                NetworkInfo.DetailedState.DISCONNECTED);
        setOpenAccessPoint();

        // The notification should not be displayed after only two scan results.
        TestUtil.sendScanResultsAvailable(mBroadcastReceiver, mContext);
        TestUtil.sendScanResultsAvailable(mBroadcastReceiver, mContext);
        verify(mNotificationManager, never())
                .notifyAsUser(any(), anyInt(), any(), any(UserHandle.class));

        // Changing to and from "SCANNING" state should not affect the counter.
        TestUtil.sendNetworkStateChanged(mBroadcastReceiver, mContext,
                NetworkInfo.DetailedState.SCANNING);
        TestUtil.sendNetworkStateChanged(mBroadcastReceiver, mContext,
                NetworkInfo.DetailedState.DISCONNECTED);

        // The third scan result notification will trigger the notification.
        TestUtil.sendScanResultsAvailable(mBroadcastReceiver, mContext);
        verify(mNotificationManager)
                .notifyAsUser(any(), anyInt(), any(), any(UserHandle.class));
        verify(mNotificationManager, never())
                .cancelAsUser(any(), anyInt(), any(UserHandle.class));

        // Changing network state should cause the notification to go away.
        TestUtil.sendNetworkStateChanged(mBroadcastReceiver, mContext,
                NetworkInfo.DetailedState.CONNECTED);
        verify(mNotificationManager)
                .cancelAsUser(any(), anyInt(), any(UserHandle.class));
    }

    @Test
    public void verifyNotificationNotDisplayed_userHasDisallowConfigWifiRestriction() {
        when(mUserManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_WIFI, UserHandle.CURRENT))
                .thenReturn(true);

        TestUtil.sendWifiStateChanged(mBroadcastReceiver, mContext, WifiManager.WIFI_STATE_ENABLED);
        TestUtil.sendNetworkStateChanged(mBroadcastReceiver, mContext,
                NetworkInfo.DetailedState.DISCONNECTED);
        setOpenAccessPoint();

        // The notification should be displayed after three scan results.
        TestUtil.sendScanResultsAvailable(mBroadcastReceiver, mContext);
        TestUtil.sendScanResultsAvailable(mBroadcastReceiver, mContext);
        TestUtil.sendScanResultsAvailable(mBroadcastReceiver, mContext);

        verify(mNotificationManager, never())
                .notifyAsUser(any(), anyInt(), any(), any(UserHandle.class));
    }
}
