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

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.android.car.settings.R;

/**
 * Contains logic for a line item represents text only view of a title and a description.
 */
public abstract class TextLineItem extends TypedPagedListAdapter.LineItem<TextLineItem.ViewHolder> {
    private final CharSequence mTitle;

    private View.OnClickListener mOnClickListener = (v) -> onClick();

    public TextLineItem(CharSequence title) {
        mTitle = title;
    }

    @Override
    public int getType() {
        return TEXT_TYPE;
    }

    @Override
    public void bindViewHolder(ViewHolder viewHolder) {
        viewHolder.titleView.setText(mTitle);
        viewHolder.descView.setText(getDesc());
        viewHolder.itemView.setOnClickListener(mOnClickListener);
        viewHolder.itemView.setEnabled(isEnabled());
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView titleView;
        final TextView descView;

        public ViewHolder(View view) {
            super(view);
            titleView = (TextView) view.findViewById(R.id.title);
            descView = (TextView) view.findViewById(R.id.desc);
        }
    }

    public static RecyclerView.ViewHolder createViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.text_line_item, parent, false);
        return new ViewHolder(v);
    }

    public abstract void onClick();

    public abstract boolean isEnabled();
}
