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
package com.android.car.settings.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.support.car.ui.PagedListView;
import android.support.v7.widget.RecyclerView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.android.car.settings.common.CarSettingActivity;
import com.android.car.settings.R;

import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.BluetoothDeviceFilter;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothAdapter;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

/**
 * Activity to host Bluetooth related preferences.
 */
public class BluetoothSettingsActivity extends CarSettingActivity implements BluetoothCallback {
    private static final String TAG = "BluetoothSettingsActivity";

    private Switch mBluetoothSwitch;
    private ProgressBar mProgressBar;
    private PagedListView mDeviceListView;
    private ViewSwitcher mViewSwitcher;
    private TextView mMessageView;
    private BluetoothDeviceListAdapter mDeviceAdapter;
    private LocalBluetoothAdapter mLocalAdapter;
    private LocalBluetoothManager mLocalManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bluetooth_list);

        ((TextView) findViewById(R.id.title)).setText(R.string.bluetooth_settings);
        mBluetoothSwitch = (Switch) findViewById(R.id.toggle_switch);
        mBluetoothSwitch.setOnClickListener(v -> {
                if (mBluetoothSwitch.isChecked()) {
                    // bt scan was turned on at state listener, when state is on.
                    mLocalAdapter.setBluetoothEnabled(true);
                } else {
                    mLocalAdapter.stopScanning();
                    mLocalAdapter.setBluetoothEnabled(false);
                }
            });

        mProgressBar = (ProgressBar) findViewById(R.id.bt_search_progress);
        mDeviceListView = (PagedListView) findViewById(R.id.list);
        mViewSwitcher = (ViewSwitcher) findViewById(R.id.view_switcher);
        mMessageView = (TextView) findViewById(R.id.bt_message);

        mLocalManager = LocalBluetoothManager.getInstance(this /* context */ , null /* listener */);
        if (mLocalManager == null) {
            Log.e(TAG, "Bluetooth is not supported on this device");
            return;
        }
        mLocalAdapter = mLocalManager.getBluetoothAdapter();

        mDeviceListView.setDefaultItemDecoration(new ItemDecoration(this));
        // Set this to light mode, since the scroll bar buttons always appear
        // on top of a dark scrim.
        mDeviceListView.setDarkMode();
        mDeviceAdapter = new BluetoothDeviceListAdapter(this /* context */ , mLocalManager);
        mDeviceListView.setAdapter(mDeviceAdapter);
    }

    @Override
    public void setupActionBar() {
        super.setupActionBar();
        getActionBar().setCustomView(R.layout.action_bar_with_toggle);
        getActionBar().setDisplayShowCustomEnabled(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mLocalManager == null) {
            return;
        }
        mLocalManager.setForegroundActivity(this);
        mLocalManager.getEventManager().registerCallback(this);
        mBluetoothSwitch.setChecked(mLocalAdapter.isEnabled());
        if (mLocalAdapter.isEnabled()) {
            setProgressBarVisible(true);
            mLocalAdapter.startScanning(true);
            if (mViewSwitcher.getCurrentView() != mDeviceListView) {
                mViewSwitcher.showPrevious();
            }
        } else {
            setProgressBarVisible(false);
            if (mViewSwitcher.getCurrentView() != mMessageView) {
                mViewSwitcher.showNext();
            }
        }
        mDeviceAdapter.start();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mLocalManager == null) {
            return;
        }
        mDeviceAdapter.stop();
        mLocalManager.setForegroundActivity(null);
        mLocalAdapter.stopScanning();
        mLocalManager.getEventManager().unregisterCallback(this);
    }

    @Override
    public void onBluetoothStateChanged(int bluetoothState) {
        switch (bluetoothState) {
            case BluetoothAdapter.STATE_OFF:
                setProgressBarVisible(false);
                mBluetoothSwitch.setChecked(false);
                if (mViewSwitcher.getCurrentView() != mMessageView) {
                    mViewSwitcher.showNext();
                }
                break;
            case BluetoothAdapter.STATE_ON:
            case BluetoothAdapter.STATE_TURNING_ON:
                setProgressBarVisible(true);
                mBluetoothSwitch.setChecked(true);
                if (mViewSwitcher.getCurrentView() != mDeviceListView) {
                        mViewSwitcher.showPrevious();
                }
                break;
            case BluetoothAdapter.STATE_TURNING_OFF:
                setProgressBarVisible(true);
                break;
        }
    }

    @Override
    public void onScanningStateChanged(boolean started) {
        if (!started) {
            setProgressBarVisible(false);
        }
    }

    @Override
    public void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice, int bondState) {
        // no-op
    }

    @Override
    public void onDeviceAdded(CachedBluetoothDevice cachedDevice) {
        // no-op
    }

    @Override
    public void onDeviceDeleted(CachedBluetoothDevice cachedDevice) {
        // no-op
    }

    @Override
    public void onConnectionStateChanged(CachedBluetoothDevice cachedDevice, int state) {
        // no-op
    }

    private  void setProgressBarVisible(boolean visible) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
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
}
