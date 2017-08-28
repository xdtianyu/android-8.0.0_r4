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

package com.android.documentsui.base;

import android.util.Log;
import android.util.Pair;

import com.android.documentsui.Injector;
import com.android.documentsui.R;

/**
 * Debug menu tools requested by QA Fred.
 */
public class DebugHelper {
    private static final String TAG = "DebugHelper";
    private static final int[][] code = new int[][] {
            {19, 19, 20, 20, 21, 22, 21, 22, 30, 29},
            {51, 51, 47, 47, 29, 32, 29, 32, 30, 29}
    };
    private static final int[][] colors = new int[][] {
            {0xFFDB3236, 0xFFB71C1C},
            {0xFF3cba54, 0xFF1B5E20},
            {0xFFf4c20d, 0xFFF9A825},
            {0xFF4885ed, 0xFF0D47A1}
    };
    private static final Pair[] messages = new Pair[]{
            new Pair<>("Woof Woof", R.drawable.debug_msg_1),
            new Pair<>("ワンワン", R.drawable.debug_msg_2)
    };

    private boolean debugEnabled = false;
    private long lastTime = 0;
    private int position = 0;
    private int codeIndex = 0;
    private int colorIndex = 0;
    private int messageIndex = 0;
    private Injector<?> mInjector;

    public DebugHelper(Injector<?> injector) {
        mInjector = injector;
    }

    public int[] getNextColors() {
        assert (mInjector.features.isDebugSupportEnabled());

        if (colorIndex == colors.length) {
            colorIndex = 0;
        }

        return colors[colorIndex++];
    }

    public Pair<String, Integer> getNextMessage() {
        assert (mInjector.features.isDebugSupportEnabled());

        if (messageIndex == messages.length) {
            messageIndex = 0;
        }

        return messages[messageIndex++];
    }

    public void debugCheck(long time, int keyCode) {
        if (time == lastTime) {
            return;
        }
        lastTime = time;

        if (position == 0) {
            for (int i = 0; i < code.length; i++) {
                if (keyCode == code[i][0]) {
                    codeIndex = i;
                    break;
                }
            }
        }

        if (keyCode == code[codeIndex][position]) {
            position++;
        } else if (position  > 2 || (position == 2 && keyCode != code[codeIndex][0])) {
            position = 0;
        }

        if (position == code[codeIndex].length) {
            position = 0;
            debugEnabled = !debugEnabled;
            // Actions is content-scope, so it can technically be null, though
            // not likely.
            if (mInjector.actions != null) {
                mInjector.actions.setDebugMode(debugEnabled);
            }

            if (Shared.VERBOSE) {
                Log.v(TAG, "Debug mode " + (debugEnabled ? "on" : "off"));
            }
        }
    }
}
