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
package android.content.pm.cts.shortcutmanager;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertWith;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.retryUntil;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.setDefaultLauncher;

import android.content.Intent;
import android.content.pm.LauncherApps.ShortcutQuery;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.pm.cts.shortcutmanager.common.Constants;
import android.content.pm.cts.shortcutmanager.common.ReplyUtil;
import android.os.PersistableBundle;
import android.util.Log;

import java.util.List;

public class ShortcutManagerRequestPinTest extends ShortcutManagerCtsTestsBase {
    private static final String TAG = "ShortcutMRPT";

    public void testIsRequestPinShortcutSupported() {

        // Launcher 1 supports it.
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().isRequestPinShortcutSupported());
        });

        // Launcher 4 does *not* supports it.
        setDefaultLauncher(getInstrumentation(), mLauncherContext4);

        runWithCaller(mPackageContext1, () -> {
            assertFalse(getManager().isRequestPinShortcutSupported());
        });
    }

    /**
     * A test for {@link ShortcutManager#requestPinShortcut}, a very simple case.
     */
    public void testRequestPinShortcut() {
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        final String SHORTCUT_ID = "s12345";

        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().isRequestPinShortcutSupported());

            ReplyUtil.invokeAndWaitForReply(getTestContext(), (replyAction) -> {
                final PersistableBundle extras = new PersistableBundle();
                extras.putString(Constants.EXTRA_REPLY_ACTION, replyAction);
                extras.putString(Constants.LABEL, "label1");

                final ShortcutInfo shortcut = makeShortcutBuilder(SHORTCUT_ID)
                        .setShortLabel("label1")
                        .setIntent(new Intent(Intent.ACTION_MAIN))
                        .setExtras(extras)
                        .build();

                Log.i(TAG, "Calling requestPinShortcut...");
                assertTrue(getManager().requestPinShortcut(shortcut, /* intent sender */ null));
                Log.i(TAG, "Done.");
            });
        });
        runWithCaller(mLauncherContext1, () -> {
            final ShortcutQuery query = new ShortcutQuery()
                    .setPackage(mPackageContext1.getPackageName())
                    .setShortcutIds(list(SHORTCUT_ID))
                    .setQueryFlags(ShortcutQuery.FLAG_MATCH_DYNAMIC
                            | ShortcutQuery.FLAG_MATCH_PINNED | ShortcutQuery.FLAG_MATCH_MANIFEST);
            Log.i(TAG, "Waiting for shortcut to be visible to launcher...");
            retryUntil(() -> {
                final List<ShortcutInfo> shortcuts = getLauncherApps().getShortcuts(query,
                        android.os.Process.myUserHandle());
                if (shortcuts == null) {
                    // Launcher not responded yet.
                    return false;
                }
                assertWith(shortcuts)
                        .haveIds(SHORTCUT_ID)
                        .areAllPinned()
                        .areAllNotDynamic()
                        .areAllNotManifest();
                return true;
            }, "Shortcut still not pinned");
        });
    }

    // TODO Various other cases (already pinned, etc)
    // TODO Various error cases (missing mandatory fields, etc)
}
