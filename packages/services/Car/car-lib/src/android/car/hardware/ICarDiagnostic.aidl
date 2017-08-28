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

package android.car.hardware;

import android.car.hardware.CarDiagnosticEvent;
import android.car.hardware.ICarDiagnosticEventListener;

/** @hide */
interface ICarDiagnostic {
    /**
     * Register a callback (or update registration) for diagnostic events.
     */
    boolean registerOrUpdateDiagnosticListener(int frameType, int rate,
        in ICarDiagnosticEventListener listener) = 1;

    /**
     * Get the value for the most recent live frame data available.
     */
    CarDiagnosticEvent getLatestLiveFrame() = 2;

    /**
     * Get the list of timestamps for which there exist a freeze frame stored.
     */
    long[] getFreezeFrameTimestamps() = 3;

    /**
     * Get the value for the freeze frame stored given a timestamp.
     */
    CarDiagnosticEvent getFreezeFrame(long timestamp) = 4;

    /**
     * Erase freeze frames given timestamps (or all, if no timestamps).
     */
     boolean clearFreezeFrames(in long[] timestamps) = 5;

    /**
     * Stop receiving diagnostic events for a given callback.
     */
     void unregisterDiagnosticListener(int frameType,
         in ICarDiagnosticEventListener callback) = 6;
}