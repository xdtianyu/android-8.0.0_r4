/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.car.dialer;

import com.android.car.dialer.bluetooth.UiBluetoothMonitor;
import com.android.car.dialer.bluetooth.embedded.UiBluetoothMonitorImpl;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.dialer.telecom.embedded.TelecomUiCallManager;

/**
 * Class factory that should create objects for different Dialer App implementations.
 */
public class ClassFactory {

    public static Factory getFactory() {
        return new FactoryForEmbeddedMode();
    }

    public interface Factory {
        UiCallManager createCarTelecomManager();
        UiBluetoothMonitor createBluetoothMonitor();
    }

    private static class FactoryForEmbeddedMode implements Factory {

        @Override
        public UiCallManager createCarTelecomManager() {
            return new TelecomUiCallManager();
        }

        @Override
        public UiBluetoothMonitor createBluetoothMonitor() {
            return new UiBluetoothMonitorImpl();
        }
    }
}
