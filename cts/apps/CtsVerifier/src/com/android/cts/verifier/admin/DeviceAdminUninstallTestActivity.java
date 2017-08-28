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

package com.android.cts.verifier.admin;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Test that checks that active device admins can be easily uninstalled via the app details screen
 */
public class DeviceAdminUninstallTestActivity extends PassFailButtons.Activity implements
        View.OnClickListener {

    private static final String TAG = DeviceAdminUninstallTestActivity.class.getSimpleName();
    private static final String ADMIN_PACKAGE_NAME = "com.android.cts.emptydeviceadmin";
    private static final String ADMIN_RECEIVER_CLASS_NAME =
            "com.android.cts.emptydeviceadmin.EmptyDeviceAdmin";
    private static final String TEST_APK_ASSET_LOCATION = "apk/CtsEmptyDeviceAdmin.apk";
    private static final String TEST_APK_FILES_LOCATION = "apk/CtsEmptyDeviceAdmin.apk";
    private static final String ADMIN_INSTALLED_BUNDLE_KEY = "admin_installed";
    private static final String ADMIN_ACTIVATED_BUNDLE_KEY = "admin_activated";
    private static final String ADMIN_REMOVED_BUNDLE_KEY = "admin_removed";

    private static final int REQUEST_INSTALL_ADMIN = 0;
    private static final int REQUEST_ENABLE_ADMIN = 1;
    private static final int REQUEST_UNINSTALL_ADMIN = 2;

    private DevicePolicyManager mDevicePolicyManager;

    private Button mInstallAdminButton;
    private ImageView mInstallStatus;
    private Button mAddDeviceAdminButton;
    private ImageView mEnableStatus;
    private Button mUninstallAdminButton;
    private ImageView mUninstallStatus;
    private File mApkFile;
    private boolean mAdminInstalled;
    private boolean mAdminActivated;
    private boolean mAdminRemoved;
    private ComponentName mAdmin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.da_uninstall_test_main);
        setInfoResources(R.string.da_uninstall_test, R.string.da_uninstall_test_info, -1);
        setPassFailButtonClickListeners();

        mAdmin = new ComponentName(ADMIN_PACKAGE_NAME, ADMIN_RECEIVER_CLASS_NAME);
        mDevicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);

        mApkFile = new File(getExternalFilesDir(null), TEST_APK_FILES_LOCATION);
        copyApkToFile(mApkFile);

        if (savedInstanceState != null) {
            mAdminInstalled = savedInstanceState.getBoolean(ADMIN_INSTALLED_BUNDLE_KEY, false);
            mAdminActivated = savedInstanceState.getBoolean(ADMIN_ACTIVATED_BUNDLE_KEY, false);
            mAdminRemoved = savedInstanceState.getBoolean(ADMIN_REMOVED_BUNDLE_KEY, false);
        } else {
            mAdminInstalled = isPackageInstalled(ADMIN_PACKAGE_NAME);
            mAdminActivated = mDevicePolicyManager.isAdminActive(mAdmin);
        }

        mInstallStatus = (ImageView) findViewById(R.id.install_admin_status);
        mInstallAdminButton = (Button) findViewById(R.id.install_device_admin_button);
        mInstallAdminButton.setOnClickListener(this);

        mEnableStatus = (ImageView) findViewById(R.id.enable_admin_status);
        mAddDeviceAdminButton = (Button) findViewById(R.id.enable_device_admin_button);
        mAddDeviceAdminButton.setOnClickListener(this);

        mUninstallStatus = (ImageView) findViewById(R.id.uninstall_admin_status);
        mUninstallAdminButton = (Button) findViewById(R.id.open_app_details_button);
        mUninstallAdminButton.setOnClickListener(this);
    }


    private void copyApkToFile(File destFile) {
        destFile.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(destFile);
             InputStream in = getAssets().open(TEST_APK_ASSET_LOCATION,
                     AssetManager.ACCESS_BUFFER)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) >= 0) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
            Log.d(TAG, "successfully copied apk to " + destFile.getAbsolutePath());
        } catch (IOException exc) {
            Log.e(TAG, "Exception while copying device admin apk", exc);
        }
    }

    private boolean isPackageInstalled(String packageName) {
        PackageInfo packageInfo = null;
        try {
            packageInfo = getPackageManager().getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException exc) {
            // Expected.
        }
        return packageInfo != null;
    }

    @Override
    public void onClick(View v) {
        if (v == mInstallAdminButton) {
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setData(DeviceAdminApkFileProvider.getUriForFile(this,
                    DeviceAdminApkFileProvider.CONTENT_AUTHORITY, mApkFile));
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            startActivityForResult(intent, REQUEST_INSTALL_ADMIN);
        } else if (v == mAddDeviceAdminButton) {
            Intent securitySettingsIntent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            securitySettingsIntent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mAdmin);
            startActivityForResult(securitySettingsIntent, REQUEST_ENABLE_ADMIN);
        } else if (v == mUninstallAdminButton) {
            Intent appDetails = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            appDetails.setData(Uri.parse("package:" + ADMIN_PACKAGE_NAME));
            startActivityForResult(appDetails, REQUEST_UNINSTALL_ADMIN);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_INSTALL_ADMIN) {
            mAdminInstalled = resultCode == RESULT_OK;
        } else if (requestCode == REQUEST_ENABLE_ADMIN) {
            mAdminActivated = mDevicePolicyManager.isAdminActive(mAdmin);
        } else if (requestCode == REQUEST_UNINSTALL_ADMIN) {
            mAdminRemoved = !mDevicePolicyManager.isAdminActive(mAdmin) && !isPackageInstalled(
                    ADMIN_PACKAGE_NAME);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateWidgets();
    }

    @Override
    public void onSaveInstanceState(Bundle icicle) {
        icicle.putBoolean(ADMIN_INSTALLED_BUNDLE_KEY, mAdminInstalled);
        icicle.putBoolean(ADMIN_ACTIVATED_BUNDLE_KEY, mAdminActivated);
        icicle.putBoolean(ADMIN_REMOVED_BUNDLE_KEY, mAdminRemoved);
    }

    private void updateWidgets() {
        mInstallAdminButton.setEnabled(mApkFile.exists() && !mAdminInstalled);
        mInstallStatus.setImageResource(
                mAdminInstalled ? R.drawable.fs_good : R.drawable.fs_indeterminate);
        mInstallStatus.invalidate();

        mAddDeviceAdminButton.setEnabled(mAdminInstalled && !mAdminActivated);
        mEnableStatus.setImageResource(
                mAdminActivated ? R.drawable.fs_good : R.drawable.fs_indeterminate);
        mEnableStatus.invalidate();

        mUninstallAdminButton.setEnabled(mAdminActivated && !mAdminRemoved);
        mUninstallStatus.setImageResource(
                mAdminRemoved ? R.drawable.fs_good : R.drawable.fs_indeterminate);
        mUninstallStatus.invalidate();

        getPassButton().setEnabled(mAdminRemoved);
    }

    public static class DeviceAdminApkFileProvider extends FileProvider {
        static final String CONTENT_AUTHORITY = "com.android.cts.verifier.admin.fileprovider";
    }
}
