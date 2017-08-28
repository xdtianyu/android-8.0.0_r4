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
 * limitations under the License
 */
package com.android.car.settings.applications;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.icu.text.ListFormatter;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.car.settings.common.CarSettingActivity;
import com.android.car.settings.R;

import com.android.settingslib.Utils;
import com.android.settingslib.applications.PermissionsSummaryHelper;
import com.android.settingslib.applications.PermissionsSummaryHelper.PermissionsResultCallback;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows details about an application and action associated with that application,
 * like uninstall, forceStop.
 */
public class ApplicationDetailActivity extends CarSettingActivity {
    private static final String TAG = "AppDetailActivity";
    public static final String APPLICATION_INFO_KEY = "APPLICATION_INFO_KEY";

    private ResolveInfo mResolveInfo;
    private TextView mPermissionDetailView;
    private View mPermissionContainer;
    private Button mDisableToggle;
    private Button mForceStopButton;
    private DevicePolicyManager mDpm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.application_details);
        if (getIntent() != null && getIntent().getExtras() != null) {
            mResolveInfo = getIntent().getExtras().getParcelable(APPLICATION_INFO_KEY);
        }
        if (mResolveInfo == null) {
            Log.w(TAG, "No application info set.");
            return;
        }

        mDisableToggle = (Button) findViewById(R.id.disable_toggle);
        mForceStopButton = (Button) findViewById(R.id.force_stop);

        mPermissionDetailView = (TextView) findViewById(R.id.permission_detail);
        mPermissionContainer = findViewById(R.id.permission_container);

        ImageView icon = (ImageView) findViewById(R.id.icon);
        icon.setImageDrawable(mResolveInfo.loadIcon(getPackageManager()));

        TextView appName = (TextView) findViewById(R.id.title);
        appName.setText(mResolveInfo.loadLabel(getPackageManager()));

        PermissionsSummaryHelper.getPermissionSummary(this /* context */,
                mResolveInfo.activityInfo.packageName, mPermissionCallback);
        mDpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        updateForceStopButton();
        mForceStopButton.setOnClickListener(v -> {
            forceStopPackage(mResolveInfo.activityInfo.packageName);
        });
        updateDisableable();
    }

    // fetch the latest ApplicationInfo instead of caching it so it reflects the current state.
    private ApplicationInfo getAppInfo() {
        try {
            return getPackageManager().getApplicationInfo(
                    mResolveInfo.activityInfo.packageName, 0 /* flag */);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "incorrect packagename: " + mResolveInfo.activityInfo.packageName, e);
            throw new IllegalArgumentException(e);
        }
    }

    private PackageInfo getPackageInfo() {
        try {
            return getPackageManager().getPackageInfo(
                    mResolveInfo.activityInfo.packageName, 0 /* flag */);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "incorrect packagename: " + mResolveInfo.activityInfo.packageName, e);
            throw new IllegalArgumentException(e);
        }
    }

    private void updateDisableable() {
        boolean disableable = false;
        boolean disabled = false;
        // Try to prevent the user from bricking their phone
        // by not allowing disabling of apps in the system.
        if (Utils.isSystemPackage(getResources(), getPackageManager(), getPackageInfo())) {
            // Disable button for core system applications.
            mDisableToggle.setText(R.string.disable_text);
            disabled = false;
        } else if (getAppInfo().enabled && !isDisabledUntilUsed()) {
            mDisableToggle.setText(R.string.disable_text);
            disableable = true;
            disabled = false;
        } else {
            mDisableToggle.setText(R.string.enable_text);
            disableable = true;
            disabled = true;
        }
        mDisableToggle.setEnabled(disableable);
        final int enableState = disabled
                ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
        mDisableToggle.setOnClickListener(v -> {
            getPackageManager().setApplicationEnabledSetting(
                    mResolveInfo.activityInfo.packageName,
                    enableState,
                    0);
            updateDisableable();
        });
    }

    private boolean isDisabledUntilUsed() {
        return getAppInfo().enabledSetting
                == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
    }

    private void forceStopPackage(String pkgName) {
        ActivityManager am = (ActivityManager) getSystemService(
                Context.ACTIVITY_SERVICE);
        Log.d(TAG, "Stopping package " + pkgName);
        am.forceStopPackage(pkgName);
        updateForceStopButton();
    }

    // enable or disable the force stop button:
    // - disabled if it's a device admin
    // - if the application is stopped unexplicitly, enabled the button
    // - if there's a reason for the system to restart the application, that indicates the app
    //   can be force stopped.
    private void updateForceStopButton() {
        if (mDpm.packageHasActiveAdmins(mResolveInfo.activityInfo.packageName)) {
            // User can't force stop device admin.
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Disabling button, user can't force stop device admin");
            }
            mForceStopButton.setEnabled(false);
        } else if ((getAppInfo().flags & ApplicationInfo.FLAG_STOPPED) == 0) {
            // If the app isn't explicitly stopped, then always show the
            // force stop button.
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "App is not explicitly stopped");
            }
            mForceStopButton.setEnabled(true);
        } else {
            Intent intent = new Intent(Intent.ACTION_QUERY_PACKAGE_RESTART,
                    Uri.fromParts("package", mResolveInfo.activityInfo.packageName, null));
            intent.putExtra(Intent.EXTRA_PACKAGES, new String[]{
                    mResolveInfo.activityInfo.packageName
            });

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Sending broadcast to query restart for "
                        + mResolveInfo.activityInfo.packageName);
            }
            sendOrderedBroadcastAsUser(intent, UserHandle.CURRENT, null,
                    mCheckKillProcessesReceiver, null, Activity.RESULT_CANCELED, null, null);
        }
    }

    private final BroadcastReceiver mCheckKillProcessesReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final boolean enabled = getResultCode() != Activity.RESULT_CANCELED;
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG,
                        MessageFormat.format("Got broadcast response: Restart status for {0} {1}",
                                mResolveInfo.activityInfo.packageName, enabled));
            }
            mForceStopButton.setEnabled(enabled);
        }
    };

    private final PermissionsResultCallback mPermissionCallback
            = new PermissionsResultCallback() {
        @Override
        public void onPermissionSummaryResult(int standardGrantedPermissionCount,
                int requestedPermissionCount, int additionalGrantedPermissionCount,
                List<CharSequence> grantedGroupLabels) {
            Resources res = getResources();
            CharSequence summary = null;

            if (requestedPermissionCount == 0) {
                summary = res.getString(
                        R.string.runtime_permissions_summary_no_permissions_requested);
                mPermissionContainer.setEnabled(false);
                mPermissionContainer.setOnClickListener(null);
            } else {
                ArrayList<CharSequence> list = new ArrayList<>(grantedGroupLabels);
                if (additionalGrantedPermissionCount > 0) {
                    // N additional permissions.
                    list.add(res.getQuantityString(
                            R.plurals.runtime_permissions_additional_count,
                            additionalGrantedPermissionCount, additionalGrantedPermissionCount));
                }
                if (list.size() == 0) {
                    summary = res.getString(
                            R.string.runtime_permissions_summary_no_permissions_granted);
                } else {
                    summary = ListFormatter.getInstance().format(list);
                }
                mPermissionContainer.setEnabled(true);
                mPermissionContainer.setOnClickListener(mPermissionClickedListener);
            }
            mPermissionDetailView.setText(summary);
        }
    };

    private OnClickListener mPermissionClickedListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            // start new activity to manage app permissions
            Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS);
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, mResolveInfo.activityInfo.packageName);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "No app can handle android.intent.action.MANAGE_APP_PERMISSIONS");
            }
        }
    };
}
