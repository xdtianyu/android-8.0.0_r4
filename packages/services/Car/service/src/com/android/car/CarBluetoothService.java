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
package com.android.car;

import android.content.Context;
import android.util.Log;

import java.io.PrintWriter;

/**
 * CarBluetoothService - deals with the automatically connecting to a known device via bluetooth.
 * Interacts with a policy -{@link BluetoothDeviceConnectionPolicy} -to initiate connections and
 * update status.
 * The {@link BluetoothDeviceConnectionPolicy} is responsible for finding the appropriate device to
 * connect for a specific profile.
 */

public class CarBluetoothService implements CarServiceBase {

    private static final String TAG = "CarBluetoothService";
    private final Context mContext;
    private final BluetoothDeviceConnectionPolicy mBluetoothDeviceConnectionPolicy;

    public CarBluetoothService(Context context, CarCabinService carCabinService,
            CarSensorService carSensorService, PerUserCarServiceHelper userSwitchService) {
        mContext = context;
        mBluetoothDeviceConnectionPolicy = BluetoothDeviceConnectionPolicy.create(mContext,
                carCabinService, carSensorService, userSwitchService);
    }

    @Override
    public void init() {
        mBluetoothDeviceConnectionPolicy.init();
    }

    @Override
    public synchronized void release() {
        mBluetoothDeviceConnectionPolicy.release();
    }

    @Override
    public synchronized void dump(PrintWriter writer) {
        mBluetoothDeviceConnectionPolicy.dump(writer);
    }

}
