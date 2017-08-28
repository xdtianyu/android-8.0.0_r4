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

package com.google.android.car.kitchensink.bluetooth;

import android.bluetooth.BluetoothMapClient;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class MapReceiver extends BroadcastReceiver {
    private static final String TAG = "CAR.BLUETOOTH.KS";
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "MAP onReceive");
        String action = intent.getAction();
        if (action.equals(BluetoothMapClient.ACTION_MESSAGE_RECEIVED)) {
            Toast.makeText(context, intent.getStringExtra(android.content.Intent.EXTRA_TEXT),
                    Toast.LENGTH_LONG).show();
        }
    }
}
