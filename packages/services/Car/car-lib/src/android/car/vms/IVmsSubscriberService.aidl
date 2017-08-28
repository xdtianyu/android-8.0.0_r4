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

package android.car.vms;

import android.car.vms.IVmsSubscriberClient;
import android.car.vms.VmsLayer;

/**
 * @hide
 */
interface IVmsSubscriberService {
    /**
     * Subscribes the listener to receive messages from layer/version.
     */
    void addVmsSubscriberClientListener(
            in IVmsSubscriberClient listener,
            in VmsLayer layer) = 0;

    /**
     * Subscribes the listener to receive messages from all published layer/version. The
     * service will not send any subscription notifications to publishers (i.e. this is a passive
     * subscriber).
     */
    void addVmsSubscriberClientPassiveListener(in IVmsSubscriberClient listener) = 1;

    /**
     * Tells the VmsSubscriberService a client unsubscribes to layer messages.
     */
    void removeVmsSubscriberClientListener(
            in IVmsSubscriberClient listener,
            in VmsLayer layer) = 2;

    /**
     * Tells the VmsSubscriberService a passive client unsubscribes. This will not unsubscribe
     * the listener from any specific layer it has subscribed to.
     */
    void removeVmsSubscriberClientPassiveListener(
            in IVmsSubscriberClient listener) = 3;

    /**
     * Tells the VmsSubscriberService a client requests the list of available layers.
     * The service should call the client's onLayersAvailabilityChange in response.
     */
    List<VmsLayer> getAvailableLayers() = 4;
}
