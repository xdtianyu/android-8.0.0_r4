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


import android.content.Intent;
import android.os.Bundle;
import android.support.car.ui.PagedListView;
import android.support.v7.widget.RecyclerView;
import com.android.car.settings.common.CarSettingActivity;
import com.android.car.settings.R;
import com.android.car.settings.common.NoDividerItemDecoration;

/**
 * Lists all time zone and its offset from GMT.
 */
public class TimeZonePickerActivity extends CarSettingActivity implements
        TimeZoneListAdapter.TimeZoneChangeListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.list);

        PagedListView listView = (PagedListView) findViewById(android.R.id.list);
        listView.setDefaultItemDecoration(new NoDividerItemDecoration(this /* context */));
        listView.setDarkMode();
        TimeZoneListAdapter adapter = new TimeZoneListAdapter(
                this /* context */, this /* TimeZoneChangeListener */);
        listView.setAdapter(adapter);
    }

    @Override
    public void onTimeZoneChanged() {
        sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));
        finish();
    }
}