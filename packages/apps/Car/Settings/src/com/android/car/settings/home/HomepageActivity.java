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
package com.android.car.settings.home;


import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import com.android.car.settings.R;
import com.android.car.settings.applications.ApplicationSettingsActivity;
import com.android.car.settings.common.ListSettingsActivity;
import com.android.car.settings.common.SimpleIconLineItem;
import com.android.car.settings.common.TypedPagedListAdapter;
import com.android.car.settings.datetime.DatetimeSettingsActivity;
import com.android.car.settings.display.DisplaySettingsActivity;
import com.android.car.settings.sound.SoundSettingsActivity;
import com.android.car.settings.system.SystemSettingsActivity;
import com.android.car.settings.wifi.CarWifiManager;

import java.util.ArrayList;

/**
 * Homepage for settings for car.
 */
public class HomepageActivity extends ListSettingsActivity implements CarWifiManager.Listener {
    private CarWifiManager mCarWifiManager;
    private WifiLineItem mWifiLineItem;
    private BluetoothLineItem mBluetoothLineItem;

    private final BroadcastReceiver mBtStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        // TODO show a different status icon?
                    case BluetoothAdapter.STATE_OFF:
                        mBluetoothLineItem.onBluetoothStateChanged(false);
                        break;
                    default:
                        mBluetoothLineItem.onBluetoothStateChanged(true);
                }
            }
        }
    };
    private final IntentFilter mBtStateChangeFilter =
            new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mCarWifiManager = new CarWifiManager(this /* context */ , this /* listener */);
        mWifiLineItem = new WifiLineItem(this, mCarWifiManager);
        mBluetoothLineItem = new BluetoothLineItem(this);

        // Call super after the wifiLineItem and BluetoothLineItem are setup, because
        // those are needed in super.onCreate().
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAccessPointsChanged() {
        // don't care
    }

    @Override
    public void onWifiStateChanged(int state) {
        mWifiLineItem.onWifiStateChanged(state);
    }

    @Override
    public void onStart() {
        super.onStart();
        mCarWifiManager.start();
        registerReceiver(mBtStateReceiver, mBtStateChangeFilter);
    }

    @Override
    public void onStop() {
        super.onStop();
        mCarWifiManager.stop();
        unregisterReceiver(mBtStateReceiver);
    }

    @Override
    public ArrayList<TypedPagedListAdapter.LineItem> getLineItems() {
        ArrayList<TypedPagedListAdapter.LineItem> lineItems = new ArrayList<>();
        lineItems.add(new SimpleIconLineItem(
                R.string.display_settings,
                R.drawable.ic_settings_display,
                this,
                null,
                DatetimeSettingsActivity.class));
        lineItems.add(new SimpleIconLineItem(
                R.string.sound_settings,
                R.drawable.ic_settings_sound,
                this,
                null,
                SoundSettingsActivity.class));
        lineItems.add(mWifiLineItem);
        lineItems.add(mBluetoothLineItem);
        lineItems.add(new SimpleIconLineItem(
                R.string.applications_settings,
                R.drawable.ic_settings_applications,
                this,
                null,
                ApplicationSettingsActivity.class));
        lineItems.add(new SimpleIconLineItem(
                R.string.display_settings,
                R.drawable.ic_settings_display,
                this,
                null,
                DisplaySettingsActivity.class));
        lineItems.add(new SimpleIconLineItem(
                R.string.system_setting_title,
                R.drawable.ic_settings_about,
                this,
                null,
                SystemSettingsActivity.class));
        return lineItems;
    }

    @Override
    public void setupActionBar() {
        getActionBar().hide();
    }
}
