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
package com.android.car.settings.applications;

import android.os.Bundle;
import android.support.car.ui.PagedListView;
import android.support.v7.widget.RecyclerView;
import com.android.car.settings.common.CarSettingActivity;
import com.android.car.settings.R;
import com.android.car.settings.common.NoDividerItemDecoration;

/**
 * Lists all installed applications and their summary.
 */
public class ApplicationSettingsActivity extends CarSettingActivity {
    private static final String TAG = "ApplicationSettingsActivity";

    private PagedListView mListView;
    private ApplicationListAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.paged_list);

        mListView = (PagedListView) findViewById(R.id.list);
        mListView.setDefaultItemDecoration(new NoDividerItemDecoration(this));
        mListView.setDarkMode();
        mAdapter = new ApplicationListAdapter(this /* context */, getPackageManager());
        mListView.setAdapter(mAdapter);
    }

}
