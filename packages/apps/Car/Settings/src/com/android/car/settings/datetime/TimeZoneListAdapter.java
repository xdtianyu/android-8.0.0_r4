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
package com.android.car.settings.datetime;

import android.annotation.NonNull;
import android.app.AlarmManager;
import android.content.Context;
import android.support.car.ui.PagedListView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;
import com.android.car.settings.R;
import com.android.settingslib.datetime.ZoneGetter;

import java.util.List;
import java.util.Map;

/**
 * Renders Timezone to a view to be displayed as a row in a list, also sets the timezone to the
 * picked one.
 */
public class TimeZoneListAdapter
        extends RecyclerView.Adapter<TimeZoneListAdapter.ViewHolder>
        implements PagedListView.ItemCap {
    private static final String TAG = "TimeZoneListAdapter";

    private final TimeZoneChangeListener mListener;
    private final Context mContext;
    private final List<Map<String, Object>> mZoneList;

    public TimeZoneListAdapter(@NonNull Context context,
            @NonNull TimeZoneChangeListener listener) {
        mContext = context;
        mListener = listener;
        mZoneList = ZoneGetter.getZonesList(mContext);
    }

    public boolean isEmpty() {
        return mZoneList.isEmpty();
    }

    public interface TimeZoneChangeListener {
        void onTimeZoneChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView mTitle;
        private final TextView mDesc;

        public ViewHolder(View view) {
            super(view);
            mTitle = (TextView) view.findViewById(R.id.title);
            mDesc = (TextView) view.findViewById(R.id.desc);
            mDesc.setVisibility(View.VISIBLE);
        }
    }

    private class TimeZoneClickListener implements OnClickListener {
        private final int mPosition;

        public TimeZoneClickListener(int position) {
            mPosition = position;
        }

        @Override
        public void onClick(View v) {
            Map<String, Object> timeZone = mZoneList.get(mPosition);
            AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
            am.setTimeZone((String) timeZone.get(ZoneGetter.KEY_ID));
            mListener.onTimeZoneChanged();
        }
    };

    @Override
    public TimeZoneListAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
            int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.time_zone_list_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Map<String, Object> timeZone = mZoneList.get(position);
        holder.mTitle.setText((String) timeZone.get(ZoneGetter.KEY_DISPLAYNAME));
        holder.mDesc.setText((String) timeZone.get(ZoneGetter.KEY_GMT));
        holder.itemView.setOnClickListener(new TimeZoneClickListener(position));
    }

    @Override
    public int getItemCount() {
        return mZoneList.size();
    }

    @Override
    public void setMaxItems(int maxItems) {
        // no limit in this list.
    }
}
