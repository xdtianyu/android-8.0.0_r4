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

import static com.android.networkrecommendation.Constants.TAG;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.NetworkInfo;
import android.net.NetworkScoreManager;
import android.net.RecommendationRequest;
import android.net.RecommendationResult;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.UserManager;
import android.provider.Settings;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import com.android.networkrecommendation.R;
import com.android.networkrecommendation.SynchronousNetworkRecommendationProvider;
import com.android.networkrecommendation.util.Blog;
import com.android.networkrecommendation.util.RoboCompatUtil;
import com.android.networkrecommendation.util.ScanResultUtil;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Takes care of handling the "open wi-fi network available" notification */
public class WifiNotificationController {
    /** The unique ID of the Notification given to the NotificationManager. */
    private static final int NOTIFICATION_ID = R.string.wifi_available_title;

    /** When a notification is shown, we wait this amount before possibly showing it again. */
    private final long mNotificationRepeatDelayMs;

    /** Whether the user has set the setting to show the 'available networks' notification. */
    private boolean mNotificationEnabled;

    /** Whether the user has {@link UserManager#DISALLOW_CONFIG_WIFI} restriction. */
    private boolean mWifiConfigRestricted;

    /** Observes the user setting to keep {@link #mNotificationEnabled} in sync. */
    private final NotificationEnabledSettingObserver mNotificationEnabledSettingObserver;

    /**
     * The {@link System#currentTimeMillis()} must be at least this value for us to show the
     * notification again.
     */
    private long mNotificationRepeatTime;

    /** These are all of the possible states for the open networks available notification. */
    @IntDef({
        State.NO_RECOMMENDATION,
        State.SHOWING_RECOMMENDATION_NOTIFICATION,
        State.CONNECTING_IN_NOTIFICATION,
        State.CONNECTING_IN_WIFI_PICKER,
        State.CONNECTED,
        State.CONNECT_FAILED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {
        int NO_RECOMMENDATION = 0;
        int SHOWING_RECOMMENDATION_NOTIFICATION = 1;
        int CONNECTING_IN_NOTIFICATION = 2;
        int CONNECTING_IN_WIFI_PICKER = 3;
        int CONNECTED = 4;
        int CONNECT_FAILED = 5;
    }

    /**
     * The {@link System#currentTimeMillis()} must be at least this value to log that open networks
     * are available.
     */
    private long mOpenNetworksLoggingRepeatTime;

    /** Current state of the notification. */
    @State private int mState = State.NO_RECOMMENDATION;

    /**
     * The number of continuous scans that must occur before consider the supplicant in a scanning
     * state. This allows supplicant to associate with remembered networks that are in the scan
     * results.
     */
    private static final int NUM_SCANS_BEFORE_ACTUALLY_SCANNING = 3;

    /**
     * The number of scans since the last network state change. When this exceeds {@link
     * #NUM_SCANS_BEFORE_ACTUALLY_SCANNING}, we consider the supplicant to actually be scanning.
     * When the network state changes to something other than scanning, we reset this to 0.
     */
    private int mNumScansSinceNetworkStateChange;

    /** Time in milliseconds to display the Connecting notification. */
    private static final int TIME_TO_SHOW_CONNECTING_MILLIS = 10000;

    /** Time in milliseconds to display the Connected notification. */
    private static final int TIME_TO_SHOW_CONNECTED_MILLIS = 5000;

    /** Time in milliseconds to display the Failed To Connect notification. */
    private static final int TIME_TO_SHOW_FAILED_MILLIS = 5000;

    /** Try to connect to the recommended WifiConfiguration and also open the wifi picker. */
    static final String ACTION_CONNECT_TO_RECOMMENDED_NETWORK_AND_OPEN_SETTINGS =
            "com.android.networkrecommendation.notify.CONNECT_TO_RECOMMENDED_NETWORK_AND_OPEN_SETTINGS";

    /** Try to connect to the recommended WifiConfiguration. */
    static final String ACTION_CONNECT_TO_RECOMMENDED_NETWORK =
            "com.android.networkrecommendation.notify.CONNECT_TO_RECOMMENDED_NETWORK";

    /** Open wifi picker to see all available networks. */
    static final String ACTION_PICK_WIFI_NETWORK =
            "com.android.networkrecommendation.notify.ACTION_PICK_WIFI_NETWORK";

    /** Open wifi picker to see all networks after connect to the recommended network failed. */
    static final String ACTION_PICK_WIFI_NETWORK_AFTER_CONNECT_FAILURE =
            "com.android.networkrecommendation.notify.ACTION_PICK_WIFI_NETWORK_AFTER_CONNECT_FAILURE";

    /** Handles behavior when notification is deleted. */
    static final String ACTION_NOTIFICATION_DELETED =
            "com.android.networkrecommendation.notify.NOTIFICATION_DELETED";

    /** Network recommended by {@link NetworkScoreManager#requestRecommendation}. */
    private WifiConfiguration mRecommendedNetwork;

    /** Whether {@link WifiNotificationController} has been started. */
    private final AtomicBoolean mStarted;

    private static final String NOTIFICATION_TAG = "WifiNotification";

    private final Context mContext;
    private final Handler mHandler;
    private final ContentResolver mContentResolver;
    private final SynchronousNetworkRecommendationProvider mNetworkRecommendationProvider;
    private final WifiManager mWifiManager;
    private final NotificationManager mNotificationManager;
    private final UserManager mUserManager;
    private final WifiNotificationHelper mWifiNotificationHelper;
    private NetworkInfo mNetworkInfo;
    private NetworkInfo.DetailedState mDetailedState;
    private volatile int mWifiState;

    public WifiNotificationController(
            Context context,
            ContentResolver contentResolver,
            Handler handler,
            SynchronousNetworkRecommendationProvider networkRecommendationProvider,
            WifiManager wifiManager,
            NotificationManager notificationManager,
            UserManager userManager,
            WifiNotificationHelper helper) {
        mContext = context;
        mContentResolver = contentResolver;
        mNetworkRecommendationProvider = networkRecommendationProvider;
        mWifiManager = wifiManager;
        mNotificationManager = notificationManager;
        mUserManager = userManager;
        mHandler = handler;
        mWifiNotificationHelper = helper;
        mStarted = new AtomicBoolean(false);

        // Setting is in seconds
        mNotificationRepeatDelayMs =
                TimeUnit.SECONDS.toMillis(
                        Settings.Global.getInt(
                                contentResolver,
                                Settings.Global.WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY,
                                900));
        mNotificationEnabledSettingObserver = new NotificationEnabledSettingObserver(mHandler);
    }

    /** Starts {@link WifiNotificationController}. */
    public void start() {
        if (!mStarted.compareAndSet(false, true)) {
            return;
        }

        mWifiState = mWifiManager.getWifiState();
        mDetailedState = NetworkInfo.DetailedState.IDLE;

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(RoboCompatUtil.ACTION_USER_RESTRICTIONS_CHANGED);
        filter.addAction(ACTION_CONNECT_TO_RECOMMENDED_NETWORK_AND_OPEN_SETTINGS);
        filter.addAction(ACTION_CONNECT_TO_RECOMMENDED_NETWORK);
        filter.addAction(ACTION_NOTIFICATION_DELETED);
        filter.addAction(ACTION_PICK_WIFI_NETWORK);
        filter.addAction(ACTION_PICK_WIFI_NETWORK_AFTER_CONNECT_FAILURE);

        mContext.registerReceiver(
                mBroadcastReceiver, filter, null /* broadcastPermission */, mHandler);
        mNotificationEnabledSettingObserver.register();

        handleUserRestrictionsChanged();
    }

    /** Stops {@link WifiNotificationController}. */
    public void stop() {
        if (!mStarted.compareAndSet(true, false)) {
            return;
        }
        mContext.unregisterReceiver(mBroadcastReceiver);
        mNotificationEnabledSettingObserver.unregister();
    }

    private final BroadcastReceiver mBroadcastReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        switch (intent.getAction()) {
                            case WifiManager.WIFI_STATE_CHANGED_ACTION:
                                mWifiState = mWifiManager.getWifiState();
                                resetNotification();
                                break;
                            case WifiManager.NETWORK_STATE_CHANGED_ACTION:
                                handleNetworkStateChange(intent);
                                break;
                            case WifiManager.SCAN_RESULTS_AVAILABLE_ACTION:
                                checkAndSetNotification(mNetworkInfo);
                                break;
                            case RoboCompatUtil.ACTION_USER_RESTRICTIONS_CHANGED:
                                handleUserRestrictionsChanged();
                                break;
                            case ACTION_CONNECT_TO_RECOMMENDED_NETWORK_AND_OPEN_SETTINGS:
                                connectToRecommendedNetwork();
                                openWifiPicker();
                                updateOnConnecting(false /* showNotification*/);
                                break;
                            case ACTION_CONNECT_TO_RECOMMENDED_NETWORK:
                                connectToRecommendedNetwork();
                                updateOnConnecting(true /* showNotification*/);
                                break;
                            case ACTION_NOTIFICATION_DELETED:
                                handleNotificationDeleted();
                                break;
                            case ACTION_PICK_WIFI_NETWORK:
                                openWifiPicker();
                                break;
                            case ACTION_PICK_WIFI_NETWORK_AFTER_CONNECT_FAILURE:
                                openWifiPicker();
                                break;
                            default:
                                Blog.e(
                                        TAG,
                                        "Unexpected broadcast. [action=%s]",
                                        intent.getAction());
                        }

                    } catch (RuntimeException re) {
                        // TODO(b/35044022) Remove try/catch after a couple of releases when we are confident
                        // this is not going to throw.
                        Blog.e(TAG, re, "RuntimeException in broadcast receiver.");
                    }
                }
            };

    private void handleNetworkStateChange(Intent intent) {
        mNetworkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
        NetworkInfo.DetailedState detailedState = mNetworkInfo.getDetailedState();
        if (detailedState != NetworkInfo.DetailedState.SCANNING
                && detailedState != mDetailedState) {
            mDetailedState = detailedState;
            switch (mDetailedState) {
                case CONNECTED:
                    updateOnConnect();
                    break;
                case DISCONNECTED:
                case CAPTIVE_PORTAL_CHECK:
                    resetNotification();
                    break;

                    // TODO: figure out if these are failure cases when connecting
                case IDLE:
                case SCANNING:
                case CONNECTING:
                case DISCONNECTING:
                case AUTHENTICATING:
                case OBTAINING_IPADDR:
                case SUSPENDED:
                case FAILED:
                case BLOCKED:
                case VERIFYING_POOR_LINK:
                    break;
            }
        }
    }

    private void handleUserRestrictionsChanged() {
        mWifiConfigRestricted = mUserManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_WIFI);
        Blog.v(TAG, "handleUserRestrictionsChanged: %b", mWifiConfigRestricted);
    }

    private void checkAndSetNotification(NetworkInfo networkInfo) {
        // TODO: unregister broadcast so we do not have to check here
        // If we shouldn't place a notification on available networks, then
        // don't bother doing any of the following
        if (!mNotificationEnabled
                || mWifiConfigRestricted
                || mWifiState != WifiManager.WIFI_STATE_ENABLED
                || mState > State.SHOWING_RECOMMENDATION_NOTIFICATION) {
            return;
        }

        NetworkInfo.State state = NetworkInfo.State.DISCONNECTED;
        if (networkInfo != null) {
            state = networkInfo.getState();
        }

        if (state == NetworkInfo.State.DISCONNECTED || state == NetworkInfo.State.UNKNOWN) {
            maybeLogOpenNetworksAvailable();
            RecommendationResult result = getOpenNetworkRecommendation();
            if (result != null && result.getWifiConfiguration() != null) {
                mRecommendedNetwork = result.getWifiConfiguration();

                if (++mNumScansSinceNetworkStateChange >= NUM_SCANS_BEFORE_ACTUALLY_SCANNING) {
                    /*
                     * We have scanned continuously at least
                     * NUM_SCANS_BEFORE_NOTIFICATION times. The user
                     * probably does not have a remembered network in range,
                     * since otherwise supplicant would have tried to
                     * associate and thus resetting this counter.
                     */
                    displayNotification();
                }
                return;
            }
        }

        // No open networks in range, remove the notification
        removeNotification();
    }

    private void maybeLogOpenNetworksAvailable() {
        long now = System.currentTimeMillis();
        if (now < mOpenNetworksLoggingRepeatTime) {
            return;
        }
        mOpenNetworksLoggingRepeatTime = now + mNotificationRepeatDelayMs;
    }

    /**
     * Uses {@link NetworkScoreManager} to choose a qualified network out of the list of {@link
     * ScanResult}s.
     *
     * @return returns the best qualified open networks, if any.
     */
    @Nullable
    private RecommendationResult getOpenNetworkRecommendation() {
        List<ScanResult> scanResults = mWifiManager.getScanResults();
        if (scanResults == null || scanResults.isEmpty()) {
            return null;
        }

        ArrayList<ScanResult> openNetworks = new ArrayList<>();
        List<WifiConfiguration> configuredNetworks = mWifiManager.getConfiguredNetworks();
        for (ScanResult scanResult : scanResults) {
            //A capability of [ESS] represents an open access point
            //that is available for an STA to connect
            //TODO: potentially handle this within NetworkRecommendationProvider instead.
            if ("[ESS]".equals(scanResult.capabilities)) {
                if (isSavedNetwork(scanResult, configuredNetworks)) {
                    continue;
                }
                openNetworks.add(scanResult);
            }
        }

        Blog.d(TAG, "Sending RecommendationRequest. [num_open_networks=%d]", openNetworks.size());
        RecommendationRequest request =
                new RecommendationRequest.Builder()
                        .setScanResults(openNetworks.toArray(new ScanResult[openNetworks.size()]))
                        .build();
        return mNetworkRecommendationProvider.requestRecommendation(request);
    }

    /** Returns true if scanResult matches the list of saved networks */
    private boolean isSavedNetwork(ScanResult scanResult, List<WifiConfiguration> savedNetworks) {
        if (savedNetworks == null) {
            return false;
        }
        for (int i = 0; i < savedNetworks.size(); i++) {
            if (ScanResultUtil.doesScanResultMatchWithNetwork(scanResult, savedNetworks.get(i))) {
                return true;
            }
        }
        return false;
    }

    /** Display's a notification that there are open Wi-Fi networks. */
    private void displayNotification() {
        // Since we use auto cancel on the notification, when the
        // mNetworksAvailableNotificationShown is true, the notification may
        // have actually been canceled.  However, when it is false we know
        // for sure that it is not being shown (it will not be shown any other
        // place than here)

        // Not enough time has passed to show the notification again
        if (mState == State.NO_RECOMMENDATION
                && System.currentTimeMillis() < mNotificationRepeatTime) {
            return;
        }
        Notification notification =
                mWifiNotificationHelper.createMainNotification(mRecommendedNetwork);
        mNotificationRepeatTime = System.currentTimeMillis() + mNotificationRepeatDelayMs;
        postNotification(notification);
        if (mState != State.SHOWING_RECOMMENDATION_NOTIFICATION) {
            mState = State.SHOWING_RECOMMENDATION_NOTIFICATION;
        }
    }

    /** Opens activity to allow the user to select a wifi network. */
    private void openWifiPicker() {
        // Close notification drawer before opening the picker.
        mContext.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        mContext.startActivity(
                new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        removeNotification();
    }

    /** Attempts to connect to recommended network the recommended network. */
    private void connectToRecommendedNetwork() {
        if (mRecommendedNetwork == null) {
            return;
        }
        mRecommendedNetwork.BSSID = null;

        // Attempts to connect to recommended network.
        RoboCompatUtil.getInstance().connectToWifi(mWifiManager, mRecommendedNetwork);
    }

    private void updateOnConnecting(boolean showNotification) {
        if (showNotification) {
            // Update notification to connecting status.
            Notification notification =
                    mWifiNotificationHelper.createConnectingNotification(mRecommendedNetwork);
            postNotification(notification);
            mState = State.CONNECTING_IN_NOTIFICATION;
        } else {
            mState = State.CONNECTING_IN_WIFI_PICKER;
        }
        mHandler.postDelayed(
                () -> {
                    updateOnFailedToConnect();
                },
                TIME_TO_SHOW_CONNECTING_MILLIS);
    }

    /**
     * When detailed state changes to CONNECTED, show connected notification or reset notification.
     */
    private void updateOnConnect() {
        if (mState == State.CONNECTING_IN_NOTIFICATION) {
            Notification notification =
                    mWifiNotificationHelper.createConnectedNotification(mRecommendedNetwork);
            postNotification(notification);
            mState = State.CONNECTED;
            mHandler.postDelayed(
                    () -> {
                        if (mState == State.CONNECTED) {
                            removeNotification();
                        }
                    },
                    TIME_TO_SHOW_CONNECTED_MILLIS);
        } else if (mState == State.CONNECTING_IN_WIFI_PICKER) {
            removeNotification();
        }
    }

    /**
     * Displays the Failed To Connect notification after the Connecting notification is shown for
     * {@link #TIME_TO_SHOW_CONNECTING_MILLIS} duration.
     */
    private void updateOnFailedToConnect() {
        if (mState == State.CONNECTING_IN_NOTIFICATION) {
            Notification notification = mWifiNotificationHelper.createFailedToConnectNotification();
            postNotification(notification);
            mState = State.CONNECT_FAILED;
            mHandler.postDelayed(
                    () -> {
                        if (mState == State.CONNECT_FAILED) {
                            removeNotification();
                        }
                    },
                    TIME_TO_SHOW_FAILED_MILLIS);
        } else if (mState == State.CONNECTING_IN_WIFI_PICKER) {
            removeNotification();
        }
    }

    /** Handles behavior when notification is dismissed. */
    private void handleNotificationDeleted() {
        mState = State.NO_RECOMMENDATION;
        mRecommendedNetwork = null;
    }

    private void postNotification(Notification notification) {
        mNotificationManager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification);
    }

    /**
     * Clears variables related to tracking whether a notification has been shown recently and
     * clears the current notification.
     */
    private void resetNotification() {
        if (mState != State.NO_RECOMMENDATION) {
            removeNotification();
        }
        mRecommendedNetwork = null;
        mNotificationRepeatTime = 0;
        mNumScansSinceNetworkStateChange = 0;
        mOpenNetworksLoggingRepeatTime = 0;
    }

    private void removeNotification() {
        mNotificationManager.cancel(NOTIFICATION_TAG, NOTIFICATION_ID);
        mState = State.NO_RECOMMENDATION;
        mRecommendedNetwork = null;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("mNotificationEnabled " + mNotificationEnabled);
        pw.println("mNotificationRepeatTime " + mNotificationRepeatTime);
        pw.println("mState " + mState);
        pw.println("mNumScansSinceNetworkStateChange " + mNumScansSinceNetworkStateChange);
    }

    private class NotificationEnabledSettingObserver extends ContentObserver {
        NotificationEnabledSettingObserver(Handler handler) {
            super(handler);
        }

        public void register() {
            mContentResolver.registerContentObserver(
                    Settings.Global.getUriFor(
                            Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON),
                    true,
                    this);
            mNotificationEnabled = getValue();
        }

        public void unregister() {
            mContentResolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            mNotificationEnabled = getValue();
            resetNotification();
        }

        private boolean getValue() {
            return Settings.Global.getInt(
                            mContentResolver,
                            Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                            0)
                    == 1;
        }
    }
}
