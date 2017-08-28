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

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.content.Context;
import android.support.car.ui.PagedListView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.ViewGroup;

import com.android.car.settings.R;

import java.lang.annotation.Retention;
import java.util.ArrayList;

/**
 * Renders all types of LineItem to a view to be displayed as a row in a list.
 */
public class TypedPagedListAdapter
        extends RecyclerView.Adapter<ViewHolder>
        implements PagedListView.ItemCap {
    private static final String TAG = "TypedPagedListAdapter";

    private final Context mContext;
    private final ArrayList<LineItem> mContentList;

    public TypedPagedListAdapter(@NonNull Context context, ArrayList<LineItem> contentList) {
        mContext = context;
        mContentList = contentList;
    }

    public boolean isEmpty() {
        return mContentList.isEmpty();
    }

    public static abstract class LineItem<VH extends ViewHolder> {
        @Retention(SOURCE)
        @IntDef({TEXT_TYPE,
                TOGGLE_TYPE,
                ICON_TEXT_TYPE,
                SEEKBAR_TYPE,
                ICON_TOGGLE_TYPE,
                SIMPLE_ICON_TEXT_TYPE})
        public @interface LineItemType {}

        // with one title and one description
        static final int TEXT_TYPE = 1;

        // with one tile, one description, and a toggle on the right.
        static final int TOGGLE_TYPE = 2;

        // with one icon, one tile and one description.
        static final int ICON_TEXT_TYPE = 3;

        // with one tile and one seekbar.
        static final int SEEKBAR_TYPE = 4;

        // with one icon, title, description and a toggle.
        static final int ICON_TOGGLE_TYPE = 5;

        // similar to ICON_TEXT_TYPE, but with a different layout.
        static final int SIMPLE_ICON_TEXT_TYPE = 6;

        @LineItemType
        abstract int getType();

        abstract void bindViewHolder(VH holder);

        public abstract CharSequence getDesc();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case LineItem.TEXT_TYPE:
                return TextLineItem.createViewHolder(parent);
            case LineItem.TOGGLE_TYPE:
                return ToggleLineItem.createViewHolder(parent);
            case LineItem.ICON_TEXT_TYPE:
                return IconTextLineItem.createViewHolder(parent);
            case LineItem.SEEKBAR_TYPE:
                return SeekbarLineItem.createViewHolder(parent);
            case LineItem.ICON_TOGGLE_TYPE:
                return IconToggleLineItem.createViewHolder(parent);
            case LineItem.SIMPLE_ICON_TEXT_TYPE:
                return SimpleIconLineItem.createViewHolder(parent);
            default:
                throw new IllegalStateException("ViewType not supported: " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        mContentList.get(position).bindViewHolder(holder);
    }

    @Override
    @LineItem.LineItemType
    public int getItemViewType(int position) {
        return mContentList.get(position).getType();
    }

    @Override
    public int getItemCount() {
        return mContentList.size();
    }

    @Override
    public void setMaxItems(int maxItems) {
        // no limit in this list.
    }
}
