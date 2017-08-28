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

package android.server.cts;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsServicesHostTestCases android.server.cts.SplashscreenTests
 */
public class SplashscreenTests extends ActivityManagerTestBase {

    public void testSplashscreenContent() throws Exception {
        launchActivityNoWait("SplashscreenActivity");
        mAmWmState.waitForAppTransitionIdle(mDevice);
        mAmWmState.getWmState().getStableBounds();
        final BufferedImage image = takeScreenshot();
        assertAllColor(image, mAmWmState.getWmState().getStableBounds(), Color.RED.getRGB());
    }

    private void assertAllColor(BufferedImage img, Rectangle bounds, int expectedColor) {
        for (int x = bounds.x; x < bounds.x + bounds.width; x++) {
            for (int y = bounds.y; y < bounds.y + bounds.height; y++) {
                assertTrue(x < img.getWidth());
                assertTrue(y < img.getHeight());
                final int color = img.getRGB(x, y);
                assertEquals("Colors must match at x=" + x + " y=" + y, expectedColor, color);
            }
        }
    }
}
