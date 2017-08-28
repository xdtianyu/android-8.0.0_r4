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
package com.android.car.dialer.bluetooth;

import com.android.car.dialer.ClassFactory;

import android.util.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Class that responsible for getting status of bluetooth connections.
 */
public abstract class UiBluetoothMonitor {
    private List<Listener> mListeners = new CopyOnWriteArrayList<>();

    private static String TAG = "Em.BtMonitor";

    private static UiBluetoothMonitor sInstance;
    private static Object sInstanceLock = new Object();

    public abstract boolean isBluetoothEnabled();
    public abstract boolean isHfpConnected();
    public abstract boolean isBluetoothPaired();

    public static UiBluetoothMonitor getInstance() {
        if (sInstance == null) {
            synchronized (sInstanceLock) {
                if (sInstance == null) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Creating an instance of UiBluetoothMonitor");
                    }
                    sInstance = ClassFactory.getFactory().createBluetoothMonitor();
                }
            }
        }
        return sInstance;
    }

    public void addListener(Listener listener) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "addListener: " + listener);
        }
        mListeners.add(listener);
    }

    protected void notifyListeners() {
        for (Listener listener : mListeners) {
            listener.onStateChanged();
        }
    }

    public void removeListener(Listener listener) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "removeListener: " + listener);
        }
        mListeners.remove(listener);
    }

    public interface Listener {
        /**
         * Calls when state of Bluetooth was changed, for example when Bluetooth was turned off or
         * on, connection state was changed.
         */
        void onStateChanged();
    }
}
