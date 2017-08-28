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

package android.support.test.launcherhelper;

import android.app.Instrumentation;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.RemoteException;
import android.os.SystemClock;
import android.platform.test.utils.DPadUtil;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;
import android.view.KeyEvent;

import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;


public class TvLauncherStrategy implements ILeanbackLauncherStrategy {

    private static final String LOG_TAG = TvLauncherStrategy.class.getSimpleName();
    private static final String PACKAGE_LAUNCHER = "com.google.android.tvlauncher";

    private static final int APP_LAUNCH_TIMEOUT = 10000;
    private static final int SHORT_WAIT_TIME = 5000;    // 5 sec
    private static final int UI_TRANSITION_WAIT_TIME = 1000;

    // Note that the selector specifies criteria for matching an UI element from/to a focused item
    private static final BySelector SELECTOR_TOP_ROW = By.res(PACKAGE_LAUNCHER, "top_row");
    private static final BySelector SELECTOR_APPS_ROW = By.res(PACKAGE_LAUNCHER, "apps_row");
    private static final BySelector SELECTOR_ALL_APPS_VIEW =
            By.res(PACKAGE_LAUNCHER, "row_list_view");
    private static final BySelector SELECTOR_ALL_APPS_LOGO =
            By.res(PACKAGE_LAUNCHER, "channel_logo").focused(true).descContains("Apps");
    private static final BySelector SELECTOR_CONFIG_CHANNELS_ROW =
            By.res(PACKAGE_LAUNCHER, "configure_channels_row");

    protected UiDevice mDevice;
    protected DPadUtil mDPadUtil;
    private Instrumentation mInstrumentation;

    /**
     * A TvLauncherUnsupportedOperationException is an exception specific to TV Launcher. This will
     * be thrown when the feature/method is not available on the TV Launcher.
     */
    class TvLauncherUnsupportedOperationException extends UnsupportedOperationException {
        TvLauncherUnsupportedOperationException() {
            super();
        }
        TvLauncherUnsupportedOperationException(String msg) {
            super(msg);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSupportedLauncherPackage() {
        return PACKAGE_LAUNCHER;
    }

    /**
     * {@inheritDoc}
     */
    // TODO(hyungtaekim): Move this common implementation to abstract class for TV launchers
    @Override
    public void setUiDevice(UiDevice uiDevice) {
        mDevice = uiDevice;
        mDPadUtil = new DPadUtil(mDevice);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void open() {
        // if we see main list view, assume at home screen already
        if (!mDevice.hasObject(getWorkspaceSelector())) {
            mDPadUtil.pressHome();
            // ensure launcher is shown
            if (!mDevice.wait(Until.hasObject(getWorkspaceSelector()), SHORT_WAIT_TIME)) {
                // HACK: dump hierarchy to logcat
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    mDevice.dumpWindowHierarchy(baos);
                    baos.flush();
                    baos.close();
                    String[] lines = baos.toString().split("\\r?\\n");
                    for (String line : lines) {
                        Log.d(LOG_TAG, line.trim());
                    }
                } catch (IOException ioe) {
                    Log.e(LOG_TAG, "error dumping XML to logcat", ioe);
                }
                throw new RuntimeException("Failed to open TV launcher");
            }
            mDevice.waitForIdle();
        }
    }

    /**
     * {@inheritDoc}
     * There are two different ways to open All Apps view. If longpress is true, it will long press
     * the HOME key to open it. Otherwise it will navigate to the "APPS" logo on the Apps row.
     */
    @Override
    public UiObject2 openAllApps(boolean longpress) {
        if (longpress) {
            mDPadUtil.longPressKeyCode(KeyEvent.KEYCODE_HOME);
        } else {
            Assert.assertNotNull("Could not find all apps logo", selectAppsLogo());
            mDPadUtil.pressDPadCenter();
        }
        return mDevice.wait(Until.findObject(getAllAppsSelector()), SHORT_WAIT_TIME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getWorkspaceSelector() {
        return By.res(getSupportedLauncherPackage(), "home_view_container");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getSearchRowSelector() {
        return  SELECTOR_TOP_ROW;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getAppsRowSelector() {
        return SELECTOR_APPS_ROW;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getGamesRowSelector() {
        // Note that the apps and games are now in the same row on new TV Launcher.
        return getAppsRowSelector();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Direction getAllAppsScrollDirection() {
        return Direction.DOWN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getAllAppsSelector() {
        return SELECTOR_ALL_APPS_VIEW;
    }

    public BySelector getAllAppsLogoSelector() {
        return SELECTOR_ALL_APPS_LOGO;
    }

    /**
     * Returns a {@link BySelector} describing a given favorite app
     */
    public BySelector getFavoriteAppSelector(String appName) {
        return By.res(getSupportedLauncherPackage(), "favorite_app_banner").text(appName);
    }

    /**
     * Returns a {@link BySelector} describing a given app in Apps View
     */
    public BySelector getAppInAppsViewSelector(String appName) {
        return By.res(getSupportedLauncherPackage(), "app_title").text(appName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long launch(String appName, String packageName) {
        return launchApp(this, appName, packageName, isGame(packageName));
    }

    /**
     * {@inheritDoc}
     * <p>
     * This function must be called before any UI test runs on TV.
     * </p>
     */
    @Override
    public void setInstrumentation(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiObject2 selectSearchRow() {
        // The Search orb is now on top row on TV Launcher
        return selectTopRow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiObject2 selectAppsRow() {
        return selectBidirect(getAppsRowSelector().hasDescendant(By.focused(true)),
                Direction.DOWN);
    }

    public UiObject2 selectChannelsRow(String channelName) {
        // TODO:
        return null;
    }

    public UiObject2 selectAppsLogo() {
        Assert.assertNotNull("Could not find all apps row", selectAppsRow());
        return selectBidirect(getAllAppsLogoSelector().hasDescendant(By.focused(true)),
                Direction.LEFT);
    }

    /**
     * Returns a {@link UiObject2} describing the Top row on TV Launcher
     * @return
     */
    public UiObject2 selectTopRow() {
        return select(getSearchRowSelector().hasDescendant(By.focused(true)),
                Direction.UP, UI_TRANSITION_WAIT_TIME);
    }

    /**
     * Returns a {@link UiObject2} describing the Config Channels row on TV Launcher
     * @return
     */
    public UiObject2 selectConfigChannelsRow() {
        return select(SELECTOR_CONFIG_CHANNELS_ROW.hasDescendant(By.focused(true)),
                Direction.DOWN, UI_TRANSITION_WAIT_TIME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiObject2 selectGamesRow() {
        return selectAppsRow();
    }

    /**
     * Select the given app in All Apps activity.
     * When the All Apps opens, the focus is always at the top right.
     * Search from left to right, and down to the next row, from right to left, and
     * down to the next row like a zigzag pattern until the app is found.
     */
    protected UiObject2 selectAppInAllApps(BySelector appSelector, String packageName) {
        openAllApps(true);

        // Assume that the focus always starts at the top left of the Apps view.
        final int maxScrollAttempts = 20;
        final int margin = 10;
        int attempts = 0;
        UiObject2 focused = null;
        UiObject2 expected = null;
        while (attempts++ < maxScrollAttempts) {
            focused = mDevice.wait(Until.findObject(By.focused(true)), SHORT_WAIT_TIME);
            expected = mDevice.wait(Until.findObject(appSelector), SHORT_WAIT_TIME);

            if (expected == null) {
                mDPadUtil.pressDPadDown();
                continue;
            } else if (focused.getVisibleCenter().equals(expected.getVisibleCenter())) {
                // The app icon is on the screen, and selected.
                Log.i(LOG_TAG, String.format("The app %s is selected", packageName));
                break;
            } else {
                // The app icon is on the screen, but not selected yet
                // Move one step closer to the app icon
                Point currentPosition = focused.getVisibleCenter();
                Point targetPosition = expected.getVisibleCenter();
                int dx = targetPosition.x - currentPosition.x;
                int dy = targetPosition.y - currentPosition.y;
                if (dy > margin) {
                    mDPadUtil.pressDPadDown();
                    continue;
                }
                if (dx > margin) {
                    mDPadUtil.pressDPadRight();
                    continue;
                }
                if (dy < -margin) {
                    mDPadUtil.pressDPadUp();
                    continue;
                }
                if (dx < -margin) {
                    mDPadUtil.pressDPadLeft();
                    continue;
                }
                throw new RuntimeException(
                        "Failed to navigate to the app icon on screen: " + packageName);
            }
        }
        return expected;
    }

    /**
     * Select the given app in All Apps activity in zigzag manner.
     * When the All Apps opens, the focus is always at the top left.
     * Search from left to right, and down to the next row, from right to left, and
     * down to the next row like a zigzag pattern until it founds a given app.
     */
    public UiObject2 selectAppInAllAppsZigZag(BySelector appSelector, String packageName) {
        Direction direction = Direction.RIGHT;
        UiObject2 app = select(appSelector, direction, UI_TRANSITION_WAIT_TIME);
        while (app == null && move(Direction.DOWN)) {
            direction = Direction.reverse(direction);
            app = select(appSelector, direction, UI_TRANSITION_WAIT_TIME);
        }
        if (app != null) {
            Log.i(LOG_TAG, String.format("The app %s is selected", packageName));
        }
        return app;
    }

    protected long launchApp(ILauncherStrategy launcherStrategy, String appName,
            String packageName, boolean isGame) {
        unlockDeviceIfAsleep();

        if (isAppOpen(packageName)) {
            // Application is already open
            return 0;
        }

        // Go to the home page, and select the Apps row
        launcherStrategy.open();
        selectAppsRow();

        // Search for the app in the Favorite Apps row first.
        // If not exists, open the 'All Apps' and search for the app there
        UiObject2 app = null;
        BySelector favAppSelector = getFavoriteAppSelector(appName);
        if (mDevice.hasObject(favAppSelector)) {
            app = selectBidirect(By.focused(true).hasDescendant(favAppSelector), Direction.RIGHT);
        } else {
            openAllApps(true);
            // Find app in Apps View in zigzag mode with app selector for Apps View
            // because the app title no longer appears until focused.
            app = selectAppInAllAppsZigZag(getAppInAppsViewSelector(appName), packageName);
        }
        if (app == null) {
            throw new RuntimeException(
                    "Failed to navigate to the app icon on screen: " + packageName);
        }

        // The app icon is already found and focused. Then wait for it to open.
        long ready = SystemClock.uptimeMillis();
        mDPadUtil.pressDPadCenter();
        if (packageName != null) {
            if (!mDevice.wait(Until.hasObject(By.pkg(packageName).depth(0)), APP_LAUNCH_TIMEOUT)) {
                Log.w(LOG_TAG, String.format(
                    "No UI element with package name %s detected.", packageName));
                return ILauncherStrategy.LAUNCH_FAILED_TIMESTAMP;
            }
        }
        return ready;
    }

    protected boolean isTopRowSelected() {
        UiObject2 row = mDevice.findObject(getSearchRowSelector());
        if (row == null) {
            return false;
        }
        return row.hasObject(By.focused(true));
    }

    protected boolean isAppsRowSelected() {
        UiObject2 row = mDevice.findObject(getAppsRowSelector());
        if (row == null) {
            return false;
        }
        return row.hasObject(By.focused(true));
    }

    protected boolean isGamesRowSelected() {
        return isAppsRowSelected();
    }

    // TODO(hyungtaekim): Move in the common helper
    protected boolean isAppOpen(String appPackage) {
        return mDevice.hasObject(By.pkg(appPackage).depth(0));
    }

    // TODO(hyungtaekim): Move in the common helper
    protected void unlockDeviceIfAsleep() {
        // Turn screen on if necessary
        try {
            if (!mDevice.isScreenOn()) {
                mDevice.wakeUp();
            }
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "Failed to unlock the screen-off device.", e);
        }
    }

    private boolean isGame(String packageName) {
        boolean isGame = false;
        if (mInstrumentation != null) {
            try {
                ApplicationInfo appInfo =
                        mInstrumentation.getTargetContext().getPackageManager().getApplicationInfo(
                                packageName, 0);
                // TV game apps should use the "isGame" tag added since the L release. They are
                // listed on the Games row on the TV Launcher.
                isGame = (appInfo.metaData != null && appInfo.metaData.getBoolean("isGame", false))
                        || ((appInfo.flags & ApplicationInfo.FLAG_IS_GAME) != 0);
                Log.i(LOG_TAG, String.format("The package %s isGame: %b", packageName, isGame));
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(LOG_TAG,
                        String.format("No package found: %s, error:%s", packageName, e.toString()));
                return false;
            }
        }
        return isGame;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void search(String query) {
        // TODO: Implement this method when the feature is available
        throw new UnsupportedOperationException("search is not yet implemented");
    }

    public void selectRestrictedProfile() {
        // TODO: Implement this method when the feature is available
        throw new UnsupportedOperationException(
                "The Restricted Profile is not yet available on TV Launcher.");
    }


    // Convenient methods for UI actions

    /**
     * Select an UI element with given {@link BySelector}. This action keeps moving a focus
     * in a given {@link Direction} until it finds a matched element.
     * @param selector the search criteria to match an element
     * @param direction the direction to find
     * @param timeoutMs timeout in milliseconds to select
     * @return a UiObject2 which represents the matched element
     */
    public UiObject2 select(BySelector selector, Direction direction, long timeoutMs) {
        UiObject2 focus = mDevice.wait(Until.findObject(By.focused(true)), SHORT_WAIT_TIME);
        while (!mDevice.wait(Until.hasObject(selector), timeoutMs)) {
            Log.d(LOG_TAG, String.format("select: moving a focus from %s to %s", focus, direction));
            UiObject2 focused = focus;
            mDPadUtil.pressDPad(direction);
            focus = mDevice.wait(Until.findObject(By.focused(true)), SHORT_WAIT_TIME);
            // Hack: A focus might be lost in some UI. Take one more step forward.
            if (focus == null) {
                mDPadUtil.pressDPad(direction);
                focus = mDevice.wait(Until.findObject(By.focused(true)), SHORT_WAIT_TIME);
            }
            // Check if it reaches to an end where it no longer moves a focus to next element
            if (focused.equals(focus)) {
                Log.d(LOG_TAG, "select: not found until it reaches to an end.");
                return null;
            }
        }
        Log.i(LOG_TAG, String.format("select: %s is selected", focus));
        return focus;
    }

    /**
     * Select an element with a given {@link BySelector} in both given direction and reverse.
     */
    public UiObject2 selectBidirect(BySelector selector, Direction direction) {
        Log.d(LOG_TAG, String.format("selectBidirect [direction]%s", direction));
        UiObject2 object = select(selector, direction, UI_TRANSITION_WAIT_TIME);
        if (object == null) {
            object = select(selector, Direction.reverse(direction), UI_TRANSITION_WAIT_TIME);
        }
        return object;
    }

    /**
     * Simulate a move pressing a key code.
     * Return true if a focus is shifted on TV UI, otherwise false.
     */
    public boolean move(Direction direction) {
        int keyCode = KeyEvent.KEYCODE_UNKNOWN;
        switch (direction) {
            case LEFT:
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT;
                break;
            case RIGHT:
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
                break;
            case UP:
                keyCode = KeyEvent.KEYCODE_DPAD_UP;
                break;
            case DOWN:
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN;
                break;
            default:
                throw new RuntimeException(String.format("This direction %s is not supported.",
                    direction));
        }
        UiObject2 focus = mDevice.wait(Until.findObject(By.focused(true)),
                UI_TRANSITION_WAIT_TIME);
        mDPadUtil.pressKeyCodeAndWait(keyCode);
        return !focus.equals(mDevice.wait(Until.findObject(By.focused(true)),
                UI_TRANSITION_WAIT_TIME));
    }


    // Unsupported methods

    @SuppressWarnings("unused")
    @Override
    public BySelector getNotificationRowSelector() {
        throw new TvLauncherUnsupportedOperationException("No Notification row");
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getSettingsRowSelector() {
        throw new TvLauncherUnsupportedOperationException("No Settings row");
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getAppWidgetSelector() {
        throw new TvLauncherUnsupportedOperationException();
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getNowPlayingCardSelector() {
        throw new TvLauncherUnsupportedOperationException("No Now Playing Card");
    }

    @SuppressWarnings("unused")
    @Override
    public UiObject2 selectNotificationRow() {
        throw new TvLauncherUnsupportedOperationException("No Notification row");
    }

    @SuppressWarnings("unused")
    @Override
    public UiObject2 selectSettingsRow() {
        throw new TvLauncherUnsupportedOperationException("No Settings row");
    }

    @SuppressWarnings("unused")
    @Override
    public boolean hasAppWidgetSelector() {
        throw new TvLauncherUnsupportedOperationException();
    }

    @SuppressWarnings("unused")
    @Override
    public boolean hasNowPlayingCard() {
        throw new TvLauncherUnsupportedOperationException("No Now Playing Card");
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getAllAppsButtonSelector() {
        throw new TvLauncherUnsupportedOperationException("No All Apps button");
    }

    @SuppressWarnings("unused")
    @Override
    public UiObject2 openAllWidgets(boolean reset) {
        throw new TvLauncherUnsupportedOperationException("No All Widgets");
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getAllWidgetsSelector() {
        throw new TvLauncherUnsupportedOperationException("No All Widgets");
    }

    @SuppressWarnings("unused")
    @Override
    public Direction getAllWidgetsScrollDirection() {
        throw new TvLauncherUnsupportedOperationException("No All Widgets");
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getHotSeatSelector() {
        throw new TvLauncherUnsupportedOperationException("No Hot seat");
    }

    @SuppressWarnings("unused")
    @Override
    public Direction getWorkspaceScrollDirection() {
        throw new TvLauncherUnsupportedOperationException("No Workspace");
    }
}
