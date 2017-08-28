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
package com.android.car.media.util.widgets;

import android.content.Context;
import android.support.v7.widget.CardView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;

public class MusicPanelLayout extends CardView {
    private static final String TAG = "GH.MusicPanelLayout";
    private View mDefaultFocus;

    public MusicPanelLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusMode) {
        if (direction == FOCUS_DOWN && mDefaultFocus != null) {
            // Initial focus case.
            views.add(mDefaultFocus);
        } else {
            super.addFocusables(views, direction, focusMode);
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "addFocusables views=" + views + " dir=" + direction + "  mode = "
                    + focusMode);
        }
    }

    public void setDefaultFocus(View defaultFocus) {
        mDefaultFocus = defaultFocus;
    }
}
