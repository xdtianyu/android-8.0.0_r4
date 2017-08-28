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

package com.android.tv.settings.device.apps.specialaccess;

import android.Manifest;
import android.app.ActivityThread;
import android.app.AppOpsManager;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.TwoStatePreference;
import android.util.Log;

import com.android.internal.util.ArrayUtils;
import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;

import java.util.Comparator;

/**
 * Fragment for controlling if apps can install other apps
 */
@Keep
public class ManageExternalSources extends ManageApplications {
    private static final String TAG = "ManageExternalSources";

    private IPackageManager mIpm;
    private AppOpsManager mAppOpsManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mIpm = ActivityThread.getPackageManager();
        mAppOpsManager = getContext().getSystemService(AppOpsManager.class);
        super.onCreate(savedInstanceState);
    }

    @NonNull
    @Override
    public ApplicationsState.AppFilter getFilter() {
        return new ApplicationsState.AppFilter() {

            @Override
            public void init() {
            }

            @Override
            public boolean filterApp(ApplicationsState.AppEntry entry) {
                if (!(entry.extraInfo instanceof InstallAppsState)) {
                    loadInstallAppsState(entry);
                }
                InstallAppsState state = (InstallAppsState) entry.extraInfo;
                return state.isPotentialAppSource();
            }
        };
    }

    @Nullable
    @Override
    public Comparator<ApplicationsState.AppEntry> getComparator() {
        return ApplicationsState.ALPHA_COMPARATOR;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.manage_external_sources, null);
    }

    @NonNull
    @Override
    public Preference createAppPreference() {
        return new SwitchPreference(getPreferenceManager().getContext());
    }

    @NonNull
    @Override
    public Preference bindPreference(@NonNull Preference preference,
            ApplicationsState.AppEntry entry) {
        final TwoStatePreference switchPref = (SwitchPreference) preference;
        switchPref.setTitle(entry.label);
        switchPref.setKey(entry.info.packageName);
        mApplicationsState.ensureIcon(entry);
        switchPref.setIcon(entry.icon);
        switchPref.setOnPreferenceChangeListener((pref, newValue) -> {
            setCanInstallApps(entry, (Boolean) newValue);
            return true;
        });

        loadInstallAppsState(entry);
        InstallAppsState state = (InstallAppsState) entry.extraInfo;
        switchPref.setChecked(state.canInstallApps());
        switchPref.setSummary(getPreferenceSummary(entry));
        switchPref.setEnabled(canChange(entry));
        return switchPref;
    }

    private boolean canChange(ApplicationsState.AppEntry entry) {
        final UserManager um = UserManager.get(getContext());
        final int userRestrictionSource = um.getUserRestrictionSource(
                UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                UserHandle.getUserHandleForUid(entry.info.uid));
        switch (userRestrictionSource) {
            case UserManager.RESTRICTION_SOURCE_DEVICE_OWNER:
            case UserManager.RESTRICTION_SOURCE_PROFILE_OWNER:
            case UserManager.RESTRICTION_SOURCE_SYSTEM:
                return false;
            default:
                return true;
        }
    }

    private CharSequence getPreferenceSummary(ApplicationsState.AppEntry entry) {
        final UserManager um = UserManager.get(getContext());
        final int userRestrictionSource = um.getUserRestrictionSource(
                UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                UserHandle.getUserHandleForUid(entry.info.uid));
        switch (userRestrictionSource) {
            case UserManager.RESTRICTION_SOURCE_DEVICE_OWNER:
            case UserManager.RESTRICTION_SOURCE_PROFILE_OWNER:
                return getContext().getString(R.string.disabled_by_admin);
            case UserManager.RESTRICTION_SOURCE_SYSTEM:
                return getContext().getString(R.string.disabled);
        }

        final InstallAppsState appsState;
        if (entry.extraInfo instanceof InstallAppsState) {
            appsState = (InstallAppsState) entry.extraInfo;
        } else {
            entry.extraInfo = appsState =
                    createInstallAppsStateFor(entry.info.packageName, entry.info.uid);
        }
        return getContext().getString(appsState.canInstallApps() ? R.string.external_source_trusted
                : R.string.external_source_untrusted);
    }

    private void setCanInstallApps(ApplicationsState.AppEntry entry, boolean newState) {
        mAppOpsManager.setMode(AppOpsManager.OP_REQUEST_INSTALL_PACKAGES,
                entry.info.uid, entry.info.packageName,
                newState ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_ERRORED);
        updateAppList();
    }


    private boolean hasRequestedAppOpPermission(String permission, String packageName) {
        try {
            String[] packages = mIpm.getAppOpPermissionPackages(permission);
            return ArrayUtils.contains(packages, packageName);
        } catch (RemoteException exc) {
            Log.e(TAG, "PackageManager dead. Cannot get permission info");
            return false;
        }
    }

    private boolean hasPermission(String permission, int uid) {
        try {
            int result = mIpm.checkUidPermission(permission, uid);
            return result == PackageManager.PERMISSION_GRANTED;
        } catch (RemoteException e) {
            Log.e(TAG, "PackageManager dead. Cannot get permission info");
            return false;
        }
    }

    private int getAppOpMode(int appOpCode, int uid, String packageName) {
        return mAppOpsManager.checkOpNoThrow(appOpCode, uid, packageName);
    }

    private void loadInstallAppsState(ApplicationsState.AppEntry entry) {
        entry.extraInfo = createInstallAppsStateFor(entry.info.packageName, entry.info.uid);
    }

    private InstallAppsState createInstallAppsStateFor(String packageName, int uid) {
        final InstallAppsState appState = new InstallAppsState();
        appState.permissionRequested = hasRequestedAppOpPermission(
                Manifest.permission.REQUEST_INSTALL_PACKAGES, packageName);
        appState.permissionGranted = hasPermission(Manifest.permission.REQUEST_INSTALL_PACKAGES,
                uid);
        appState.appOpMode = getAppOpMode(AppOpsManager.OP_REQUEST_INSTALL_PACKAGES, uid,
                packageName);
        return appState;
    }

    /**
     * Collection of information to be used as {@link ApplicationsState.AppEntry#extraInfo} objects
     */
    private static class InstallAppsState {
        public boolean permissionRequested;
        public boolean permissionGranted;
        public int appOpMode;

        private InstallAppsState() {
            this.appOpMode = AppOpsManager.MODE_DEFAULT;
        }

        public boolean canInstallApps() {
            if (appOpMode == AppOpsManager.MODE_DEFAULT) {
                return permissionGranted;
            } else {
                return appOpMode == AppOpsManager.MODE_ALLOWED;
            }
        }

        public boolean isPotentialAppSource() {
            return appOpMode != AppOpsManager.MODE_DEFAULT || permissionRequested;
        }

        @Override
        public String toString() {
            return "[permissionGranted: " + permissionGranted
                    + ", permissionRequested: " + permissionRequested
                    + ", appOpMode: " + appOpMode
                    + "]";
        }
    }
}
