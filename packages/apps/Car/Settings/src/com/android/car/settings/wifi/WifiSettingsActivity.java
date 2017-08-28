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
 * limitations under the License.
 */
package com.android.car.settings.wifi;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.car.ui.PagedListView;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import android.annotation.StringRes;

import com.android.car.settings.common.CarSettingActivity;
import com.android.car.settings.R;

import com.android.settingslib.wifi.AccessPoint;


/**
 * Main page to host Wifi related preferences.
 */
public class WifiSettingsActivity extends CarSettingActivity implements CarWifiManager.Listener {
    private static final String TAG = "WifiSettingsActivity";

    private CarWifiManager mCarWifiManager;
    private AccessPointListAdapter mAdapter;
    private Switch mWifiSwitch;
    private ProgressBar mProgressBar;
    private PagedListView mListView;
    private LinearLayout mWifiListContainer;
    private TextView mMessageView;
    private ViewSwitcher mViewSwitcher;
    private TextView mAddWifiTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCarWifiManager = new CarWifiManager(this /* context */ , this /* listener */);
        setContentView(R.layout.wifi_list);

        ((TextView) findViewById(R.id.action_bar_title)).setText(R.string.wifi_settings);
        mProgressBar = (ProgressBar) findViewById(R.id.wifi_search_progress);
        mListView = (PagedListView) findViewById(R.id.list);
        mMessageView = (TextView) findViewById(R.id.message);
        mViewSwitcher = (ViewSwitcher) findViewById(R.id.view_switcher);
        mAddWifiTextView = (TextView) findViewById(R.id.add_wifi);
        mWifiListContainer = (LinearLayout) findViewById(R.id.wifi_list_container);
        mAddWifiTextView.setOnClickListener(v -> {
            Intent intent = new Intent(this /* context */, AddWifiActivity.class);
            intent.putExtra(AddWifiActivity.ADD_NETWORK_MODE, true);
            startActivity(intent);
        });
        setupWifiSwitch();
        if (mCarWifiManager.isWifiEnabled()) {
            showList();
        } else {
            showMessage(R.string.wifi_disabled);
        }
        mListView.setDefaultItemDecoration(new ItemDecoration(this));
        // Set this to light mode, since the scroll bar buttons always appear
        // on top of a dark scrim.
        mListView.setDarkMode();
        mAdapter = new AccessPointListAdapter(
                this, mCarWifiManager, mCarWifiManager.getAccessPoints());
        mListView.setAdapter(mAdapter);
    }

    @Override
    public void setupActionBar() {
        getActionBar().setCustomView(R.layout.action_bar_with_toggle);
        getActionBar().setDisplayShowCustomEnabled(true);
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        mCarWifiManager.start();
    }

    @Override
    public void onStop() {
        super.onStop();
        mCarWifiManager.stop();
    }

    @Override
    public void onAccessPointsChanged() {
        refreshData();
    }

    @Override
    public void onWifiStateChanged(int state) {
        mWifiSwitch.setChecked(mCarWifiManager.isWifiEnabled());
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLING:
                showList();
                setProgressBarVisible(true);
                break;
            case WifiManager.WIFI_STATE_DISABLED:
                setProgressBarVisible(false);
                showMessage(R.string.wifi_disabled);
                break;
            default:
                showList();
        }
    }

    private  void setProgressBarVisible(boolean visible) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void refreshData() {
        if (mAdapter != null) {
            mAdapter.updateAccessPoints(mCarWifiManager.getAccessPoints());
            // if the list is empty, keep showing the progress bar, the list should refresh
            // every couple seconds.
            // TODO: Consider show a message in the list view place.
            if (!mAdapter.isEmpty()) {
                setProgressBarVisible(false);
            }
        }
        mWifiSwitch.setChecked(mCarWifiManager.isWifiEnabled());
    }

    /**
     * Default {@link android.support.car.ui.PagedListView.Decoration} for the {@link PagedListView}
     * that removes the dividing lines between items.
     */
    private static class ItemDecoration extends PagedListView.Decoration {
        public ItemDecoration(Context context) {
            super(context);
        }

        @Override
        public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {}
    }

    private void showMessage(@StringRes int resId) {
        if (mViewSwitcher.getCurrentView() != mMessageView) {
            mViewSwitcher.showNext();
        }
        mMessageView.setText(getResources().getString(resId));
    }

    private void showList() {
        if (mViewSwitcher.getCurrentView() != mWifiListContainer) {
            mViewSwitcher.showPrevious();
        }
    }

    private void setupWifiSwitch() {
        mWifiSwitch = (Switch) findViewById(R.id.toggle_switch);
        mWifiSwitch.setChecked(mCarWifiManager.isWifiEnabled());
        mWifiSwitch.setOnClickListener(v -> {
            mCarWifiManager.setWifiEnabled(mWifiSwitch.isChecked());
        });
    }
}
