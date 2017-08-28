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

package android.system.helpers;

import android.app.KeyguardManager;
import android.content.Context;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.system.helpers.ActivityHelper;

import junit.framework.Assert;

import java.io.IOException;
/**
 * Implement common helper methods for Lockscreen.
 */
public class LockscreenHelper {
    private static final String LOG_TAG = LockscreenHelper.class.getSimpleName();
    public static final int SHORT_TIMEOUT = 200;
    public static final int LONG_TIMEOUT = 2000;
    public static final String EDIT_TEXT_CLASS_NAME = "android.widget.EditText";
    public static final String CAMERA2_PACKAGE = "com.android.camera2";
    public static final String CAMERA_PACKAGE = "com.google.android.GoogleCamera";
    public static final String MODE_PIN = "PIN";
    private static final int SWIPE_MARGIN = 5;
    private static final int DEFAULT_FLING_STEPS = 5;
    private static final int DEFAULT_SCROLL_STEPS = 15;

    private static final String SET_PIN_COMMAND = "locksettings set-pin %s";
    private static final String CLEAR_COMMAND = "locksettings clear --old %s";

    private static LockscreenHelper sInstance = null;
    private Context mContext = null;
    private UiDevice mDevice = null;
    private final ActivityHelper mActivityHelper;
    private final CommandsHelper mCommandsHelper;
    private final DeviceHelper mDeviceHelper;
    private boolean mIsRyuDevice = false;

    public LockscreenHelper() {
        mContext = InstrumentationRegistry.getTargetContext();
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mActivityHelper = ActivityHelper.getInstance();
        mCommandsHelper = CommandsHelper.getInstance(InstrumentationRegistry.getInstrumentation());
        mDeviceHelper = DeviceHelper.getInstance();
        mIsRyuDevice = mDeviceHelper.isRyuDevice();

    }

    public static LockscreenHelper getInstance() {
        if (sInstance == null) {
            sInstance = new LockscreenHelper();
        }
        return sInstance;
    }

    /**
     * Launch Camera on LockScreen
     * @return true/false
     */
    public boolean launchCameraOnLockScreen() {
        String CameraPackage = mIsRyuDevice ? CAMERA2_PACKAGE : CAMERA_PACKAGE;
        int w = mDevice.getDisplayWidth();
        int h = mDevice.getDisplayHeight();
        // Load camera on LockScreen and take a photo
        mDevice.drag((w - 25), (h - 25), (int) (w * 0.5), (int) (w * 0.5), 40);
        mDevice.waitForIdle();
        return mDevice.wait(Until.hasObject(
                By.res(CameraPackage, "activity_root_view")),
                LONG_TIMEOUT * 2);
    }

     /**
     * Sets the screen lock pin or password
     * @param pwd text of Password or Pin for lockscreen
     * @param mode indicate if its password or PIN
     * @throws InterruptedException
     */
    public void setScreenLock(String pwd, String mode, boolean mIsNexusDevice) throws InterruptedException {
        navigateToScreenLock();
        mDevice.wait(Until.findObject(By.text(mode)), LONG_TIMEOUT * 2).click();
        // set up Secure start-up page
        if (!mIsNexusDevice) {
            mDevice.wait(Until.findObject(By.text("No thanks")), LONG_TIMEOUT).click();
        }
        UiObject2 pinField = mDevice.wait(Until.findObject(By.clazz(EDIT_TEXT_CLASS_NAME)),
                LONG_TIMEOUT);
        pinField.setText(pwd);
        // enter and verify password
        mDevice.pressEnter();
        pinField.setText(pwd);
        mDevice.pressEnter();
        mDevice.wait(Until.findObject(By.text("DONE")), LONG_TIMEOUT).click();
    }

    /**
     * check if Emergency Call page exists
     * @throws InterruptedException
     */
    public void checkEmergencyCallOnLockScreen() throws InterruptedException {
        mDevice.pressMenu();
        mDevice.wait(Until.findObject(By.text("EMERGENCY")), LONG_TIMEOUT).click();
        Thread.sleep(LONG_TIMEOUT);
        UiObject2 dialButton = mDevice.wait(Until.findObject(By.desc("dial")),
                LONG_TIMEOUT);
        Assert.assertNotNull("Can't reach emergency call page", dialButton);
        mDevice.pressBack();
        Thread.sleep(LONG_TIMEOUT);
    }

    /**
     * remove Screen Lock
     * @throws InterruptedException
     */
    public void removeScreenLock(String pwd)
            throws InterruptedException {
        navigateToScreenLock();
        UiObject2 pinField = mDevice.wait(Until.findObject(By.clazz(EDIT_TEXT_CLASS_NAME)),
                LONG_TIMEOUT);
        pinField.setText(pwd);
        mDevice.pressEnter();
        mDevice.wait(Until.findObject(By.text("Swipe")), LONG_TIMEOUT).click();
        mDevice.waitForIdle();
        mDevice.wait(Until.findObject(By.text("YES, REMOVE")), LONG_TIMEOUT).click();
    }

    /**
     * unlock screen
     * @throws InterruptedException, IOException
     */
    public void unlockScreen(String pwd)
            throws InterruptedException, IOException {
        String command = String.format(" %s %s %s", "input", "keyevent", "82");
        mDevice.executeShellCommand(command);
        Thread.sleep(SHORT_TIMEOUT);
        Thread.sleep(SHORT_TIMEOUT);
        // enter password to unlock screen
        command = String.format(" %s %s %s", "input", "text", pwd);
        mDevice.executeShellCommand(command);
        mDevice.waitForIdle();
        Thread.sleep(SHORT_TIMEOUT);
        mDevice.pressEnter();
    }

    /**
     * navigate to screen lock setting page
     * @throws InterruptedException
     */
    public void navigateToScreenLock()
            throws InterruptedException {
        mActivityHelper.launchIntent(Settings.ACTION_SECURITY_SETTINGS);
        mDevice.wait(Until.findObject(By.text("Screen lock")), LONG_TIMEOUT).click();
    }

    /**
     * check if Lock Screen is enabled
     */
    public boolean isLockScreenEnabled() {
        KeyguardManager km = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        return km.isKeyguardSecure();
    }

    /**
     * Sets a screen lock via shell.
     */
    public void setScreenLockViaShell(String pwd, String mode) throws Exception {
        switch (mode) {
            case MODE_PIN:
                mCommandsHelper.executeShellCommand(String.format(SET_PIN_COMMAND, pwd));
                break;
            default:
                throw new IllegalArgumentException("Unsupported mode: " + mode);
        }
    }

    /**
     * Removes the screen lock via shell.
     */
    public void removeScreenLockViaShell(String pwd) throws Exception {
        mCommandsHelper.executeShellCommand(String.format(CLEAR_COMMAND, pwd));
    }

    /**
     * swipe up to unlock the screen
     */
    public void unlockScreenSwipeUp() throws Exception {
        mDevice.wakeUp();
        mDevice.waitForIdle();
        mDevice.swipe(mDevice.getDisplayWidth() / 2,
                mDevice.getDisplayHeight() - SWIPE_MARGIN,
                mDevice.getDisplayWidth() / 2,
                SWIPE_MARGIN,
                DEFAULT_SCROLL_STEPS);
        mDevice.waitForIdle();
    }
}