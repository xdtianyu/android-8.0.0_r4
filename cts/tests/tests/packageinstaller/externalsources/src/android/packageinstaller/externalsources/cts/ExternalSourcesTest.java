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
package android.packageinstaller.externalsources.cts;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.support.v4.content.FileProvider;
import android.util.Log;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class ExternalSourcesTest {

    private static final String TAG = ExternalSourcesTest.class.getSimpleName();
    private static final String TEST_APK_NAME = "CtsEmptyTestApp.apk";
    private static final String TEST_APK_EXTERNAL_LOCATION = "/data/local/tmp/cts/externalsources";
    private static final String CONTENT_AUTHORITY =
            "android.packageinstaller.externalsources.cts.fileprovider";
    private static final String PACKAGE_INSTALLER_PACKAGE_NAME = "com.android.packageinstaller";
    private static final String INSTALL_CONFIRM_TEXT_ID = "install_confirm_question";
    private static final String WM_DISMISS_KEYGUARD_COMMAND = "wm dismiss-keyguard";
    private static final String ALERT_DIALOG_TITLE_ID = "android:id/alertTitle";

    private static final long WAIT_FOR_UI_TIMEOUT = 5000;

    private Context mContext;
    private PackageManager mPm;
    private Instrumentation mInstrumentation;
    private String mPackageName;
    private File mApkFile;
    private UiDevice mUiDevice;

    @BeforeClass
    public static void setUpOnce() throws IOException {
        File srcApkFile = new File(TEST_APK_EXTERNAL_LOCATION, TEST_APK_NAME);
        File destApkFile = new File(InstrumentationRegistry.getTargetContext().getFilesDir(),
                TEST_APK_NAME);
        copyFile(srcApkFile, destApkFile);
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mPm = mContext.getPackageManager();
        mPackageName = mContext.getPackageName();
        mApkFile = new File(mContext.getFilesDir(), TEST_APK_NAME);
        mUiDevice = UiDevice.getInstance(mInstrumentation);
        if (!mUiDevice.isScreenOn()) {
            mUiDevice.wakeUp();
        }
        mUiDevice.executeShellCommand(WM_DISMISS_KEYGUARD_COMMAND);
    }

    private static void copyFile(File srcFile, File destFile) throws IOException {
        if (destFile.exists()) {
            destFile.delete();
        }
        FileInputStream inputStream = new FileInputStream(srcFile);
        FileOutputStream out = new FileOutputStream(destFile);
        try {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) >= 0) {
                out.write(buffer, 0, bytesRead);
            }
            Log.d(TAG, "copied file " + srcFile + " to " + destFile);
        } finally {
            out.flush();
            try {
                out.getFD().sync();
            } catch (IOException e) {
            }
            out.close();
            inputStream.close();
        }
    }

    private void launchPackageInstaller() {
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setData(FileProvider.getUriForFile(mContext, CONTENT_AUTHORITY, mApkFile));
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Log.d(TAG, "Starting intent with uri " + intent.getDataString());
        mContext.startActivity(intent);
    }

    private void assertInstallAllowed(String errorMessage) {
        BySelector selector = By.res(PACKAGE_INSTALLER_PACKAGE_NAME, INSTALL_CONFIRM_TEXT_ID);
        UiObject2 uiObject = mUiDevice.wait(Until.findObject(selector), WAIT_FOR_UI_TIMEOUT);
        Assert.assertNotNull(errorMessage, uiObject);
        mUiDevice.pressBack();
    }

    private void assertInstallBlocked(String errorMessage) {
        BySelector selector = By.res(ALERT_DIALOG_TITLE_ID);
        UiObject2 settingsButton = mUiDevice.wait(Until.findObject(selector), WAIT_FOR_UI_TIMEOUT);
        Assert.assertNotNull(errorMessage, settingsButton);
        mUiDevice.pressBack();
    }

    private void setAppOpsMode(String mode) throws IOException {
        final StringBuilder commandBuilder = new StringBuilder("appops set");
        commandBuilder.append(" " + mPackageName);
        commandBuilder.append(" REQUEST_INSTALL_PACKAGES");
        commandBuilder.append(" " + mode);
        mUiDevice.executeShellCommand(commandBuilder.toString());
    }

    @Test
    public void blockedSourceTest() throws Exception {
        setAppOpsMode("deny");
        boolean isTrusted = mPm.canRequestPackageInstalls();
        Assert.assertFalse("Package " + mPackageName
                + " allowed to install packages after setting app op to errored", isTrusted);
        launchPackageInstaller();
        assertInstallBlocked("Install blocking dialog not shown when app op set to errored");
    }

    @Test
    public void allowedSourceTest() throws Exception {
        setAppOpsMode("allow");
        boolean isTrusted = mPm.canRequestPackageInstalls();
        Assert.assertTrue("Package " + mPackageName
                + " blocked from installing packages after setting app op to allowed", isTrusted);
        launchPackageInstaller();
        assertInstallAllowed("Install confirmation not shown when app op set to allowed");
    }

    @Test
    public void defaultSourceTest() throws Exception {
        boolean isTrusted = mPm.canRequestPackageInstalls();
        Assert.assertFalse("Package " + mPackageName
                + " with default app ops state allowed to install packages", isTrusted);
        launchPackageInstaller();
        assertInstallBlocked("Install blocking dialog not shown when app op set to default");
    }

    @After
    public void tearDown() throws Exception {
        mUiDevice.pressHome();
        setAppOpsMode("default");
    }
}
