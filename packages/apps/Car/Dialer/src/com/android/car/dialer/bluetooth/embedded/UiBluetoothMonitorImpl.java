/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.car.dialer.bluetooth.embedded;

import com.android.car.dialer.bluetooth.UiBluetoothMonitor;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;

/**
 * An implementation of {@link UiBluetoothMonitor} that uses {@link BluetoothAdapter}.
 */
public class UiBluetoothMonitorImpl extends UiBluetoothMonitor {

    @Override
    public boolean isBluetoothEnabled() {
        BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter == null) {
            return false;
        }
        return adapter.isEnabled();
    }

    @Override
    public boolean isHfpConnected() {
        BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter == null) {
            return false;
        }
        int hfpState = adapter.getProfileConnectionState(BluetoothProfile.HEADSET_CLIENT);
        return hfpState == BluetoothProfile.STATE_CONNECTED;
    }

    @Override
    public boolean isBluetoothPaired() {
        BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter == null) {
            return false;
        }
        return !adapter.getBondedDevices().isEmpty();
    }

    private BluetoothAdapter getBluetoothAdapter() {
        return BluetoothAdapter.getDefaultAdapter();
    }
}
