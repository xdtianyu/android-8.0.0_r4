/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.tradefed.device;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.IDevice;

/**
 * A wrapper that directs {@link IAndroidDebugBridge} calls to the 'real'
 * {@link AndroidDebugBridge}.
 */
class AndroidDebugBridgeWrapper implements IAndroidDebugBridge {

    private AndroidDebugBridge mAdbBridge = null;

    /**
     * Creates a {@link AndroidDebugBridgeWrapper}.
     */
    AndroidDebugBridgeWrapper() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDevice[] getDevices() {
        if (mAdbBridge == null) {
            throw new IllegalStateException("getDevices called before init");
        }
        return mAdbBridge.getDevices();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDeviceChangeListener(IDeviceChangeListener listener) {
        AndroidDebugBridge.addDeviceChangeListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeDeviceChangeListener(IDeviceChangeListener listener) {
        AndroidDebugBridge.removeDeviceChangeListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(boolean clientSupport, String adbOsLocation) {
        AndroidDebugBridge.init(clientSupport);
        mAdbBridge = AndroidDebugBridge.createBridge(adbOsLocation, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void terminate() {
        AndroidDebugBridge.terminate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disconnectBridge() {
        AndroidDebugBridge.disconnectBridge();
    }
}
