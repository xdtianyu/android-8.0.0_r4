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

package com.android.car.settings.common;

import android.support.car.ui.PagedListView;
import android.support.v7.widget.RecyclerView;
import android.content.Context;
import android.graphics.Canvas;

/**
 * Default {@link android.support.car.ui.PagedListView.Decoration} for the {@link PagedListView}
 * that removes the dividing lines between items.
 */
public class NoDividerItemDecoration extends PagedListView.Decoration {
    public NoDividerItemDecoration(Context context) {
        super(context);
    }

    @Override
    public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {}
}
