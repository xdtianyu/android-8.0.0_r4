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

package com.android.wearable.sysapp.janktests;

import android.os.Bundle;
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.util.Log;

import junit.framework.Assert;

import java.util.List;

/**
 * Jank tests for watchFace picker on clockwork device
 */
public class WatchFacePickerJankTest extends JankTestBase {

    private static final String WEARABLE_APP_PACKAGE = "com.google.android.wearable.app";
    private static final String WATCHFACE_PREVIEW_NAME = "preview_image";
    private static final String WATCHFACE_SHOW_ALL_BTN_NAME = "show_all_btn";
    private static final String WATCHFACE_PICKER_ALL_LIST_NAME = "watch_face_picker_all_list";
    private static final String WATCHFACE_PICKER_FAVORITE_LIST_NAME = "watch_face_picker_list";
    private static final String TAG = "WatchFacePickerJankTest";
    private UiDevice mDevice;
    private SysAppTestHelper mHelper;

    /*
     * (non-Javadoc)
     * @see junit.framework.TestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        mDevice = UiDevice.getInstance(getInstrumentation());
        mHelper = SysAppTestHelper.getInstance(mDevice, this.getInstrumentation());
        mDevice.wakeUp();
        super.setUp();
    }

    /**
     * Test the jank by open watchface picker (favorites)
     */
    @JankTest(beforeLoop = "startFromHome", afterTest = "goBackHome",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES_WATCHFACE_PICKER_TEST)
    @GfxMonitor(processName = WEARABLE_APP_PACKAGE)
    public void testOpenWatchFacePicker() {
        openPicker();
    }

    /**
     * Test the jank by adding watch face to favorites.
     */
    @JankTest(beforeLoop = "startFromWatchFacePickerFull",
            afterTest = "removeAllButOneWatchFace",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES_WATCHFACE_PICKER_TEST_ADD_FAVORITE)
    @GfxMonitor(processName = WEARABLE_APP_PACKAGE)
    public void testSelectWatchFaceFromFullList() {
        selectWatchFaceFromFullList(0);
    }

    /**
     * Test the jank by removing watch face from favorites.
     */
    @JankTest(beforeLoop = "startWithTwoWatchFaces", afterLoop = "resetToTwoWatchFaces",
            afterTest = "removeAllButOneWatchFace",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES_WATCHFACE_PICKER_TEST)
    @GfxMonitor(processName = WEARABLE_APP_PACKAGE)
    public void testRemoveWatchFaceFromFavorites() {
        removeWatchFaceFromFavorites();
    }

    /**
     * Test the jank on flinging watch face picker.
     */
    @JankTest(beforeTest = "startWithFourWatchFaces", beforeLoop = "resetToWatchFacePicker",
            afterTest = "removeAllButOneWatchFace",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES_WATCHFACE_PICKER_TEST)
    @GfxMonitor(processName = WEARABLE_APP_PACKAGE)
    public void testWatchFacePickerFling() {
        flingWatchFacePicker(5);
    }

    /**
     * Test the jank of flinging watch face full list picker.
     */
    @JankTest(beforeLoop = "startFromWatchFacePickerFull",
            afterLoop = "resetToWatchFacePickerFull",
            afterTest = "removeAllButOneWatchFace",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES_WATCHFACE_PICKER_TEST)
    @GfxMonitor(processName = WEARABLE_APP_PACKAGE)
    public void testWatchFacePickerFullListFling() {
        flingWatchFacePickerFullList(5);
    }

    public void startFromHome() {
        mHelper.goBackHome();
    }

    public void startFromWatchFacePicker() {
        Log.v(TAG, "Starting from watchface picker ...");
        startFromHome();
        openPicker();
    }

    public void startFromWatchFacePickerFull() {
        Log.v(TAG, "Starting from watchface picker full list ...");
        startFromHome();
        openPicker();
        openPickerAllList();
    }

    public void startWithTwoWatchFaces() {
        Log.v(TAG, "Starting with two watchfaces ...");
        for (int i = 0; i < 2; ++i) {
            startFromHome();
            openPicker();
            openPickerAllList();
            selectWatchFaceFromFullList(i);
        }
    }

    public void startWithFourWatchFaces() {
        Log.v(TAG, "Starting with four watchfaces ...");
        for (int i = 0; i < 4; ++i) {
            startFromHome();
            openPicker();
            openPickerAllList();
            selectWatchFaceFromFullList(i);
        }
    }

    // Ensuring that we head back to the first screen before launching the app again
    public void goBackHome(Bundle metrics) {
        Log.v(TAG, "Going back Home ...");
        startFromHome();
        super.afterTest(metrics);
    }

    public void removeAllButOneWatchFace(Bundle metrics) {
        int count = 0;
        do {
            Log.v(TAG, "Removing all but one watch faces ...");
            startFromWatchFacePicker();
            removeWatchFaceFromFavorites();
        } while (isOnlyOneWatchFaceInFavorites() && count++ < 5);
        super.afterTest(metrics);
    }

    public void resetToWatchFacePicker() {
        Log.v(TAG, "Resetting to watchface picker screen ...");
        startFromWatchFacePicker();
    }

    public void resetToWatchFacePickerFull() {
        Log.v(TAG, "Resetting to watchface picker full list screen ...");
        startFromWatchFacePickerFull();
    }

    public void resetToTwoWatchFaces() {
        Log.v(TAG, "Resetting to two watchfaces in favorites ...");
        startWithTwoWatchFaces();
    }

    /**
     * Check if there is only one watch face in favorites list.
     *
     * @return True is +id/show_all_btn is on the screen. False otherwise.
     */
    private boolean isOnlyOneWatchFaceInFavorites() {
        // Make sure we are not at the last watch face.
        mHelper.swipeRight();
        return mHelper.waitForSysAppUiObject2(WATCHFACE_SHOW_ALL_BTN_NAME) != null;
    }

    private void openPicker() {
        // Try 5 times in case WFP is not ready
        for (int i = 0; i < 5; i ++) {
            mHelper.swipeLeft();
            if (mHelper.waitForSysAppUiObject2(WATCHFACE_PREVIEW_NAME) != null) {
                return;
            }
        }
        Assert.fail("Still cannot open WFP after several attempts");
    }

    private void openPickerAllList() {
        // Assume the screen is on WatchFace picker for favorites.
        // Swipe to the end of the list.
        while (mHelper.waitForSysAppUiObject2(WATCHFACE_SHOW_ALL_BTN_NAME) == null) {
            Log.v(TAG, "Swiping to the end of favorites list ...");
            mHelper.flingLeft();
        }

        UiObject2 showAllButton = mHelper.waitForSysAppUiObject2(WATCHFACE_SHOW_ALL_BTN_NAME);
        Assert.assertNotNull(showAllButton);
        Log.v(TAG, "Tapping to show all watchfaces ...");
        showAllButton.click();
        UiObject2 watchFacePickerAllList =
                mHelper.waitForSysAppUiObject2(WATCHFACE_PICKER_ALL_LIST_NAME);
        Assert.assertNotNull(watchFacePickerAllList);
    }

    private void selectWatchFaceFromFullList(int index) {
        // Assume the screen is on watch face picker for all faces.
        UiObject2 watchFacePickerAllList = mHelper.waitForSysAppUiObject2(
                WATCHFACE_PICKER_ALL_LIST_NAME);
        Assert.assertNotNull(watchFacePickerAllList);
        int swipes = index / 4; // Showing 4 for each scroll.
        for (int i = 0; i < swipes; ++i) {
            mHelper.swipeDown();
        }
        List<UiObject2> watchFaces = watchFacePickerAllList.getChildren();
        Assert.assertNotNull(watchFaces);
        int localIndex = index % 4;
        if (watchFaces.size() <= localIndex) {
            mDevice.pressBack();
            return;
        }

        Log.v(TAG, "Tapping the " + localIndex + " watchface on screen ...");
        watchFaces.get(localIndex).click();
        // Verify the watchface is selected properly.
        UiObject2 watchFacePickerList =
                mHelper.waitForSysAppUiObject2(WATCHFACE_PICKER_FAVORITE_LIST_NAME);
        Assert.assertNotNull(watchFacePickerList);
    }

    private void removeWatchFaceFromFavorites() {
        mHelper.flingRight();

        // Assume the favorites list has at least 2 watch faces.
        UiObject2 watchFacePicker =
                mHelper.waitForSysAppUiObject2(WATCHFACE_PICKER_FAVORITE_LIST_NAME);
        Assert.assertNotNull(watchFacePicker);
        List<UiObject2> watchFaces = watchFacePicker.getChildren();
        Assert.assertNotNull(watchFaces);
        if (isOnlyOneWatchFaceInFavorites()) {
            return;
        }

        Log.v(TAG, "Removing first watch face from favorites ...");
        watchFaces.get(0).swipe(Direction.DOWN, 1.0f);
    }

    private void flingWatchFacePicker(int iterations) {
        for (int i = 0; i < iterations; ++i) {
            // Start fling to right, then left, alternatively.
            if (i % 2 == 0) {
                mHelper.flingRight();
            } else {
                mHelper.flingLeft();
            }
        }
    }

    private void flingWatchFacePickerFullList(int iterations) {
        for (int i = 0; i < iterations; ++i) {
            // Start fling up, then down, alternatively.
            if (i % 2 == 0) {
                mHelper.flingUp();
            } else {
                mHelper.flingDown();
            }
        }
    }
}
