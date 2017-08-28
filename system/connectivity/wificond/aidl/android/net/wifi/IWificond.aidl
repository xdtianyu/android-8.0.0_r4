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

package android.net.wifi;

import android.net.wifi.IApInterface;
import android.net.wifi.IClientInterface;
import android.net.wifi.IInterfaceEventCallback;
import android.net.wifi.IRttClient;
import android.net.wifi.IRttController;

// Service interface that exposes primitives for controlling the WiFi
// subsystems of a device.
interface IWificond {

    // Create a network interface suitable for use as an AP.
    @nullable IApInterface createApInterface();

    // Create a network interface suitable for use as a WiFi client.
    @nullable IClientInterface createClientInterface();

    // Tear down all existing interfaces.  This should enable clients to create
    // future interfaces immediately after this method returns.
    void tearDownInterfaces();

    // @return list of the currently configured IClientInterface instances.
    List<IBinder> GetClientInterfaces();

    // @return list of the currently configured IApInterface instances.
    List<IBinder> GetApInterfaces();

    // Register a callback to receive interface status updates.
    //
    // Multiple callbacks can be registered simultaneously.
    // Duplicate registrations of the same callback will be ignored.
    //
    // @param callback object to add to the set of registered callbacks.
    oneway void RegisterCallback(IInterfaceEventCallback callback);

    // Remove a callback from the set of registered callbacks.
    //
    // This must be the same instance as previously registered.
    // Requests to remove unknown callbacks will be ignored.
    //
    // @param callback object to remove from the set of registered callbacks.
    oneway void UnregisterCallback(IInterfaceEventCallback callback);

    // Obtain a reference to a IRttController that can be used to
    // request ranging information.
    // Results will be returned via the registered IRttClient.
    IRttController registerRttClient(IRttClient rttClient);

    // Remove an IRttClient from the set of registered IRttClient callbacks.
    // @param rttClient object to remove from the set of registered
    // IRttClient callbacks.
    void unregisterRttClient(IRttClient rttClient);
}
