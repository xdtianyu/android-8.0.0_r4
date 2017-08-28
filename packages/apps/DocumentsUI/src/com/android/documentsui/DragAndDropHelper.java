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

package com.android.documentsui;

import static com.android.documentsui.base.Shared.DEBUG;

import android.util.Log;

import com.android.documentsui.base.DocumentInfo;

import java.util.List;

/**
 * A helper class for drag and drop operations
 */
public final class DragAndDropHelper {

    private static final String TAG = "DragAndDropHelper";

    private DragAndDropHelper() {}

    /**
     * Helper method to see whether an item can be dropped/copied into a particular destination.
     * Don't copy from the cwd into a provided list of prohibited directories. (ie. into cwd, into a
     * selected directory). Note: this currently doesn't work for multi-window drag, because
     * localState isn't carried over from one process to another.
     */
    public static boolean canCopyTo(Object dragLocalState, DocumentInfo dst) {
        if (dst == null) {
            if (DEBUG) Log.d(TAG, "Invalid destination. Ignoring.");
            return false;
        }

        if (dragLocalState == null || !(dragLocalState instanceof List<?>)) {
            if (DEBUG) Log.d(TAG, "Invalid local state object. Will allow copy.");
            return true;
        }

        List<?> src = (List<?>) dragLocalState;
        if (src.contains(dst)) {
            if (DEBUG) Log.d(TAG, "Drop target same as source. Ignoring.");
            return false;
        }
        return true;
    }
}
