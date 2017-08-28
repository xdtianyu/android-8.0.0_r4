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

import android.content.Context;
import android.content.Intent;

import android.annotation.DrawableRes;
import android.annotation.StringRes;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.car.settings.R;

/**
 * Represents the basic line item with Icon and text.
 */
public class SimpleIconLineItem extends IconTextLineItem {
    private final CharSequence mDesc;
    private final Context mContext;
    private final Class mActivityClass;

    public SimpleIconLineItem(
            @StringRes int title,
            @DrawableRes int iconRes,
            Context context,
            CharSequence desc,
            Class activityClass) {
        super(context.getText(title), iconRes);
        mDesc = desc;
        mContext = context;
        mActivityClass = activityClass;
    }

    @Override
    public int getType() {
        return SIMPLE_ICON_TEXT_TYPE;
    }

    @Override
    public void onClick() {
        Intent intent = new Intent(mContext, mActivityClass);
        mContext.startActivity(intent);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public CharSequence getDesc() {
        return mDesc;
    }

    public static RecyclerView.ViewHolder createViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tile_item, parent, false);
        return new ViewHolder(v);
    }
}
