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

package android.media.session.cts;

import static android.media.cts.MediaSessionTestHelperConstants.FLAG_CREATE_MEDIA_SESSION;
import static android.media.cts.MediaSessionTestHelperConstants.FLAG_SET_MEDIA_SESSION_ACTIVE;
import static android.media.cts.MediaSessionTestHelperConstants.MEDIA_SESSION_TEST_HELPER_APK;
import static android.media.cts.MediaSessionTestHelperConstants.MEDIA_SESSION_TEST_HELPER_PKG;

import android.media.cts.BaseMultiUserTest;
import android.media.cts.MediaSessionTestHelperConstants;

import android.platform.test.annotations.RequiresDevice;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.StringTokenizer;

/**
 * Host-side test for the media session manager that installs and runs device-side tests after the
 * proper device setup.
 * <p>Corresponding device-side tests are written in the {@link #DEVICE_SIDE_TEST_CLASS}
 * which is in the {@link #DEVICE_SIDE_TEST_APK}.
 */
public class MediaSessionManagerHostTest extends BaseMultiUserTest {
    /**
     * Package name of the device-side tests.
     */
    private static final String DEVICE_SIDE_TEST_PKG = "android.media.session.cts";
    /**
     * Package file name (.apk) for the device-side tests.
     */
    private static final String DEVICE_SIDE_TEST_APK = "CtsMediaSessionHostTestApp.apk";
    /**
     * Fully qualified class name for the device-side tests.
     */
    private static final String DEVICE_SIDE_TEST_CLASS =
            "android.media.session.cts.MediaSessionManagerTest";

    private static final String SETTINGS_NOTIFICATION_LISTENER_NAMESPACE = "secure";
    private static final String SETTINGS_NOTIFICATION_LISTENER_NAME =
            "enabled_notification_listeners";

    // Keep the original notification listener list to clean up.
    private Map<Integer, String> mNotificationListeners;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mNotificationListeners = new HashMap<>();
    }

    @Override
    public void tearDown() throws Exception {
        if (!mHasManagedUsersFeature) {
            return;
        }

        // Cleanup
        for (int userId : mNotificationListeners.keySet()) {
            String notificationListener = mNotificationListeners.get(userId);
            putSettings(SETTINGS_NOTIFICATION_LISTENER_NAMESPACE,
                    SETTINGS_NOTIFICATION_LISTENER_NAME, notificationListener, userId);
        }
        super.tearDown();
    }

    /**
     * Tests {@link MediaSessionManager#getActiveSessions} with the multi-users environment.
     */
    @RequiresDevice
    public void testGetActiveSessions() throws Exception {
        if (!mHasManagedUsersFeature) {
            return;
        }

        // Ensure that the previously running media session test helper app doesn't exist.
        getDevice().uninstallPackage(MEDIA_SESSION_TEST_HELPER_PKG);

        int primaryUserId = getDevice().getPrimaryUserId();

        allowGetActiveSessionForTest(primaryUserId);
        installAppAsUser(DEVICE_SIDE_TEST_APK, primaryUserId);
        runTest("testGetActiveSessions_noMediaSessionFromMediaSessionTestHelper");

        installAppAsUser(MEDIA_SESSION_TEST_HELPER_APK, primaryUserId);
        sendControlCommand(primaryUserId, FLAG_CREATE_MEDIA_SESSION);
        runTest("testGetActiveSessions_noMediaSessionFromMediaSessionTestHelper");

        sendControlCommand(primaryUserId, FLAG_SET_MEDIA_SESSION_ACTIVE);
        runTest("testGetActiveSessions_hasMediaSessionFromMediaSessionTestHelper");

        if (!canCreateAdditionalUsers(1)) {
            CLog.w("Cannot create a new user. Skipping multi-user test cases.");
            return;
        }

        // Test if another user can get the session.
        int newUser = createAndStartUser();
        installAppAsUser(DEVICE_SIDE_TEST_APK, newUser);
        allowGetActiveSessionForTest(newUser);
        runTestAsUser("testGetActiveSessions_noMediaSession", newUser);
        removeUser(newUser);

        // Test if another managed profile can get the session.
        // Remove the created user first not to exceed system's user number limit.
        newUser = createAndStartManagedProfile(primaryUserId);
        installAppAsUser(DEVICE_SIDE_TEST_APK, newUser);
        allowGetActiveSessionForTest(newUser);
        runTestAsUser("testGetActiveSessions_noMediaSession", newUser);
        removeUser(newUser);

        // Test if another restricted profile can get the session.
        // Remove the created user first not to exceed system's user number limit.
        newUser = createAndStartRestrictedProfile(primaryUserId);
        installAppAsUser(DEVICE_SIDE_TEST_APK, newUser);
        allowGetActiveSessionForTest(newUser);
        runTestAsUser("testGetActiveSessions_noMediaSession", newUser);
        removeUser(newUser);
    }

    private void runTest(String testMethodName) throws DeviceNotAvailableException {
        runTestAsUser(testMethodName, getDevice().getPrimaryUserId());
    }

    private void runTestAsUser(String testMethodName, int userId)
            throws DeviceNotAvailableException {
        runDeviceTestsAsUser(DEVICE_SIDE_TEST_PKG, DEVICE_SIDE_TEST_CLASS,
                testMethodName, userId);
    }

    /**
     * Allows the {@link #DEVICE_SIDE_TEST_CLASS} to call
     * {@link MediaSessionManager#getActiveSessions} for testing.
     * <p>{@link MediaSessionManager#getActiveSessions} bypasses the permission check if the
     * caller is the enabled notification listener. This method uses the behavior by making
     * {@link #DEVICE_SIDE_TEST_CLASS} as the notification listener. So any change in this
     * should be also applied to the class.
     * <p>Note that the device-side test {@link android.media.cts.MediaSessionManagerTest} already
     * covers the test for failing {@link MediaSessionManager#getActiveSessions} without the
     * permission nor the notification listener.
     */
    private void allowGetActiveSessionForTest(int userId) throws Exception {
        final String NOTIFICATION_LISTENER_DELIM = ":";
        if (mNotificationListeners.get(userId) != null) {
            // Already enabled.
            return;
        }
        String list = getSettings(SETTINGS_NOTIFICATION_LISTENER_NAMESPACE,
                SETTINGS_NOTIFICATION_LISTENER_NAME, userId);

        String notificationListener = DEVICE_SIDE_TEST_PKG + "/" + DEVICE_SIDE_TEST_CLASS;
        // Ensure that the list doesn't contain notificationListener already.
        // This can happen if the test is killed while running.
        StringTokenizer tokenizer = new StringTokenizer(list, NOTIFICATION_LISTENER_DELIM);
        StringJoiner joiner = new StringJoiner(NOTIFICATION_LISTENER_DELIM);
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (!token.isEmpty() && !token.equals(notificationListener)) {
                joiner.add(token);
            }
        }
        list = joiner.toString();
        // Preserve the original list.
        mNotificationListeners.put(userId, list);
        // Allow get active sessions by setting notification listener.
        joiner.add(notificationListener);
        putSettings(SETTINGS_NOTIFICATION_LISTENER_NAMESPACE,
                SETTINGS_NOTIFICATION_LISTENER_NAME, joiner.toString(), userId);
    }

    private void sendControlCommand(int userId, int flag) throws Exception {
        executeShellCommand(MediaSessionTestHelperConstants.buildControlCommand(userId, flag));
    }
}
