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

package com.android.car.settings.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.support.v7.widget.RecyclerView;

import com.android.car.settings.common.ToggleLineItem;

import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothProfile;
import com.android.settingslib.bluetooth.MapProfile;
import com.android.settingslib.bluetooth.PanProfile;
import com.android.settingslib.bluetooth.PbapServerProfile;

/**
 * Represents a line item for a Bluetooth mProfile.
 */
public class BluetoothProfileLineItem extends ToggleLineItem {
    private final LocalBluetoothProfile mProfile;
    private final CachedBluetoothDevice mCachedDevice;
    private ToggleLineItemViewHolder mViewHolder;
    private DataChangedListener mDataChangedListener;

    public interface DataChangedListener {
        void onDataChanged();
    }

    public BluetoothProfileLineItem(Context context, LocalBluetoothProfile profile,
            CachedBluetoothDevice cachedBluetoothDevice, DataChangedListener listener) {
        super(context.getText(profile.getNameResource(cachedBluetoothDevice.getDevice())));
        mCachedDevice = cachedBluetoothDevice;
        mProfile = profile;
        mDataChangedListener = listener;
    }

    @Override
    public void onClick(boolean isChecked) {
        if (mProfile instanceof PbapServerProfile) {
            mCachedDevice.setPhonebookPermissionChoice(isChecked
                    ? CachedBluetoothDevice.ACCESS_REJECTED : CachedBluetoothDevice.ACCESS_ALLOWED);
        } else if (mProfile instanceof MapProfile) {
            mCachedDevice.setMessagePermissionChoice(isChecked
                    ? CachedBluetoothDevice.ACCESS_REJECTED : CachedBluetoothDevice.ACCESS_ALLOWED);
        } else if (isChecked) {
            mCachedDevice.disconnect(mProfile);
            mProfile.setPreferred(mCachedDevice.getDevice(), false);
        } else if (mProfile.isPreferred(mCachedDevice.getDevice())) {
            if (mProfile instanceof PanProfile) {
                mCachedDevice.connectProfile(mProfile);
            } else {
                mProfile.setPreferred(mCachedDevice.getDevice(), false);
            }
        } else {
            mProfile.setPreferred(mCachedDevice.getDevice(), true);
            mCachedDevice.connectProfile(mProfile);
        }
        mDataChangedListener.onDataChanged();
    }

    @Override
    public void bindViewHolder(ToggleLineItemViewHolder holder) {
        super.bindViewHolder(holder);
        mViewHolder = holder;
    }

    @Override
    public CharSequence getDesc() {
        return null;
    }

    @Override
    public boolean isChecked() {
        BluetoothDevice device = mCachedDevice.getDevice();

        if (mProfile instanceof MapProfile) {
            return mCachedDevice.getMessagePermissionChoice()
                    == CachedBluetoothDevice.ACCESS_ALLOWED;

        } else if (mProfile instanceof PbapServerProfile) {
            return mCachedDevice.getPhonebookPermissionChoice()
                    == CachedBluetoothDevice.ACCESS_ALLOWED;

        } else if (mProfile instanceof PanProfile) {
            return mProfile.getConnectionStatus(device) == BluetoothProfile.STATE_CONNECTED;

        } else {
            return mProfile.isPreferred(device);
        }
    }
}
