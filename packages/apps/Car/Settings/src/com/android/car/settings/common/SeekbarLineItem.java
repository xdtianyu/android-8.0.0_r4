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
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.car.settings.R;

/**
 * Contains logic for a line item represents a description and a seekbar.
 */
public abstract class SeekbarLineItem
        extends TypedPagedListAdapter.LineItem<SeekbarLineItem.ViewHolder> {
    private final CharSequence mTitle;

    private SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener =
            new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            SeekbarLineItem.this.onSeekbarChanged(progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // no-op
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // no-op
        }
    };

    public SeekbarLineItem(CharSequence title) {
        mTitle = title;
    }

    @Override
    public int getType() {
        return SEEKBAR_TYPE;
    }

    @Override
    public void bindViewHolder(ViewHolder viewHolder) {
        viewHolder.titleView.setText(mTitle);
        viewHolder.seekBar.setMax(getMaxSeekbarValue());
        viewHolder.seekBar.setProgress(getInitialSeekbarValue());
        viewHolder.seekBar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView titleView;
        final SeekBar seekBar;

        public ViewHolder(View view) {
            super(view);
            titleView = (TextView) view.findViewById(R.id.title);
            seekBar = (SeekBar) view.findViewById(R.id.seekbar);
        }
    }

    public static RecyclerView.ViewHolder createViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.seekbar_line_item, parent, false);
        return new ViewHolder(v);
    }

    // Seekbar Line item does not have description field for now.
    @Override
    public CharSequence getDesc() {
        return null;
    }

    public abstract int getInitialSeekbarValue();

    public abstract int getMaxSeekbarValue();

    public abstract void onSeekbarChanged(int progress);
}
