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

import static android.media.cts.MediaSessionTestHelperConstants.MEDIA_SESSION_TEST_HELPER_PKG;

import android.content.Context;
import android.content.ComponentName;
import android.test.AndroidTestCase;
import android.media.session.MediaSessionManager;
import android.media.session.MediaController;

import java.util.List;

/**
 * Tests {@link MediaSessionManager} with the multi-user environment.
 * <p>Don't run tests here directly. They aren't stand-alone tests and each test will be run
 * indirectly by the host-side test CtsMediaHostTestCases after the proper device setup.
 */
public class MediaSessionManagerTest extends AndroidTestCase {
    private MediaSessionManager mMediaSessionManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mMediaSessionManager = (MediaSessionManager) getContext().getSystemService(
                Context.MEDIA_SESSION_SERVICE);
    }

    /**
     * Tests if the MediaSessionTestHelper doesn't have an active media session.
     */
    public void testGetActiveSessions_noMediaSessionFromMediaSessionTestHelper() throws Exception {
        List<MediaController> controllers = mMediaSessionManager.getActiveSessions(
                createFakeNotificationListener());
        for (MediaController controller : controllers) {
            if (controller.getPackageName().equals(MEDIA_SESSION_TEST_HELPER_PKG)) {
                fail("Media session for the media session app shouldn't be available");
                return;
            }
        }
    }

    /**
     * Tests if the MediaSessionTestHelper has an active media session.
     */
    public void testGetActiveSessions_hasMediaSessionFromMediaSessionTestHelper() throws Exception {
        List<MediaController> controllers = mMediaSessionManager.getActiveSessions(
                createFakeNotificationListener());
        for (MediaController controller : controllers) {
            if (controller.getPackageName().equals(MEDIA_SESSION_TEST_HELPER_PKG)) {
                // Test success
                return;
            }
        }
        fail("Media session for the media session app is expected");
    }

    /**
     * Tests if there's no media session.
     */
    public void testGetActiveSessions_noMediaSession() throws Exception {
        List<MediaController> controllers = mMediaSessionManager.getActiveSessions(
                createFakeNotificationListener());
        assertTrue(controllers.isEmpty());
    }

    /**
     * Returns the ComponentName of the notification listener for this test.
     * <p>Notification listener will be enabled by the host-side test.
     */
    private ComponentName createFakeNotificationListener() {
        return new ComponentName(getContext(), MediaSessionManagerTest.class);
    }
}

