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

import android.car.annotation.FutureFeature;
import android.car.vms.IVmsSubscriberClient;
import android.car.vms.VmsLayer;
import android.car.vms.VmsSubscriptionState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.android.internal.annotations.GuardedBy;

/**
 * Manages all the VMS subscriptions:
 * + Subscriptions to data messages of individual layer + version.
 * + Subscriptions to all data messages.
 * + HAL subscriptions to layer + version.
 */
@FutureFeature
public class VmsRouting {
    private final Object mLock = new Object();
    // A map of Layer + Version to listeners.
    @GuardedBy("mLock")
    private Map<VmsLayer, Set<IVmsSubscriberClient>> mLayerSubscriptions =
        new HashMap<>();
    // A set of listeners that are interested in any layer + version.
    @GuardedBy("mLock")
    private Set<IVmsSubscriberClient> mPromiscuousSubscribers =
        new HashSet<>();
    // A set of all the layers + versions the HAL is subscribed to.
    @GuardedBy("mLock")
    private Set<VmsLayer> mHalSubscriptions = new HashSet<>();
    // A sequence number that is increased every time the subscription state is modified. Note that
    // modifying the list of promiscuous subscribers does not affect the subscription state.
    @GuardedBy("mLock")
    private int mSequenceNumber = 0;

    /**
     * Add a listener subscription to a data messages from layer + version.
     *
     * @param listener a VMS subscriber.
     * @param layer the layer subscribing to.
     */
    public void addSubscription(IVmsSubscriberClient listener, VmsLayer layer) {
        synchronized (mLock) {
            ++mSequenceNumber;
            // Get or create the list of listeners for layer and version.
            Set<IVmsSubscriberClient> listeners = mLayerSubscriptions.get(layer);

            if (listeners == null) {
                listeners = new HashSet<>();
                mLayerSubscriptions.put(layer, listeners);
            }
            // Add the listener to the list.
            listeners.add(listener);
        }
    }

    /**
     * Add a listener subscription to all data messages.
     *
     * @param listener a VMS subscriber.
     */
    public void addSubscription(IVmsSubscriberClient listener) {
        synchronized (mLock) {
            ++mSequenceNumber;
            mPromiscuousSubscribers.add(listener);
        }
    }

    /**
     * Remove a subscription for a layer + version and make sure to remove the key if there are no
     * more subscribers.
     *
     * @param listener to remove.
     * @param layer of the subscription.
     */
    public void removeSubscription(IVmsSubscriberClient listener, VmsLayer layer) {
        synchronized (mLock) {
            ++mSequenceNumber;
            Set<IVmsSubscriberClient> listeners = mLayerSubscriptions.get(layer);

            // If there are no listeners we are done.
            if (listeners == null) {
                return;
            }
            listeners.remove(listener);

            // If there are no more listeners then remove the list.
            if (listeners.isEmpty()) {
                mLayerSubscriptions.remove(layer);
            }
        }
    }

    /**
     * Remove a listener subscription to all data messages.
     *
     * @param listener a VMS subscriber.
     */
    public void removeSubscription(IVmsSubscriberClient listener) {
        synchronized (mLock) {
            ++mSequenceNumber;
            mPromiscuousSubscribers.remove(listener);
        }
    }

    /**
     * Remove a subscriber from all routes (optional operation).
     *
     * @param listener a VMS subscriber.
     */
    public void removeDeadListener(IVmsSubscriberClient listener) {
        synchronized (mLock) {
            // Remove the listener from all the routes.
            for (VmsLayer layer : mLayerSubscriptions.keySet()) {
                removeSubscription(listener, layer);
            }
            // Remove the listener from the loggers.
            removeSubscription(listener);
        }
    }

    /**
     * Returns all the listeners for a layer and version. This include the subscribers which
     * explicitly subscribed to this layer and version and the promiscuous subscribers.
     *
     * @param layer to get listeners to.
     * @return a list of the listeners.
     */
    public Set<IVmsSubscriberClient> getListeners(VmsLayer layer) {
        Set<IVmsSubscriberClient> listeners = new HashSet<>();
        synchronized (mLock) {
            // Add the subscribers which explicitly subscribed to this layer and version
            if (mLayerSubscriptions.containsKey(layer)) {
                listeners.addAll(mLayerSubscriptions.get(layer));
            }
            // Add the promiscuous subscribers.
            listeners.addAll(mPromiscuousSubscribers);
        }
        return listeners;
    }

    /**
     * Checks if a listener is subscribed to any messages.
     * @param listener that may have subscription.
     * @return true if the listener uis subscribed to messages.
     */
    public boolean containsListener(IVmsSubscriberClient listener) {
        synchronized (mLock) {
            // Check if listener is subscribed to a layer.
            for (Set<IVmsSubscriberClient> layerListeners: mLayerSubscriptions.values()) {
                if (layerListeners.contains(listener)) {
                    return true;
                }
            }
            // Check is listener is subscribed to all data messages.
            return mPromiscuousSubscribers.contains(listener);
        }
    }

    /**
     * Add a layer and version to the HAL subscriptions.
     * @param layer the HAL subscribes to.
     */
    public void addHalSubscription(VmsLayer layer) {
        synchronized (mLock) {
            ++mSequenceNumber;
            mHalSubscriptions.add(layer);
        }
    }

    /**
     * remove a layer and version to the HAL subscriptions.
     * @param layer the HAL unsubscribes from.
     */
    public void removeHalSubscription(VmsLayer layer) {
        synchronized (mLock) {
            ++mSequenceNumber;
            mHalSubscriptions.remove(layer);
        }
    }

    /**
     * checks if the HAL is subscribed to a layer.
     * @param layer
     * @return true if the HAL is subscribed to layer.
     */
    public boolean isHalSubscribed(VmsLayer layer) {
        synchronized (mLock) {
            return mHalSubscriptions.contains(layer);
        }
    }

    /**
     * checks if there are subscribers to a layer.
     * @param layer
     * @return true if there are subscribers to layer.
     */
    public boolean hasLayerSubscriptions(VmsLayer layer) {
        synchronized (mLock) {
            return mLayerSubscriptions.containsKey(layer) || mHalSubscriptions.contains(layer);
        }
    }

    /**
     * @return a Set of layers and versions which VMS clients are subscribed to.
     */
    public VmsSubscriptionState getSubscriptionState() {
        synchronized (mLock) {
            List<VmsLayer> layers = new ArrayList<>();
            layers.addAll(mLayerSubscriptions.keySet());
            layers.addAll(mHalSubscriptions);
            return new VmsSubscriptionState(mSequenceNumber, layers);
        }
    }
}