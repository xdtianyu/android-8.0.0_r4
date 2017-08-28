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

import android.car.vms.IVmsSubscriberClient;
import android.car.vms.VmsLayer;
import android.car.vms.VmsSubscriptionState;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SmallTest
public class VmsRoutingTest extends AndroidTestCase {
    private static VmsLayer LAYER_WITH_SUBSCRIPTION_1= new VmsLayer(1, 2);
    private static VmsLayer LAYER_WITH_SUBSCRIPTION_2= new VmsLayer(1, 3);
    private static VmsLayer LAYER_WITHOUT_SUBSCRIPTION= new VmsLayer(1, 4);
    private VmsRouting mRouting;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mRouting = new VmsRouting();
    }

    public void testAddingSubscribersAndHalLayersNoOverlap() throws Exception {
        // Add a subscription to a layer.
        MockVmsListener listener = new MockVmsListener();
        mRouting.addSubscription(listener, LAYER_WITH_SUBSCRIPTION_1);

        // Add a HAL subscription.
        mRouting.addHalSubscription(LAYER_WITH_SUBSCRIPTION_2);

        // Verify expected subscriptions are in routing manager.
        Set<VmsLayer> expectedSubscriptions = new HashSet<>();
        expectedSubscriptions.add(LAYER_WITH_SUBSCRIPTION_1);
        expectedSubscriptions.add(LAYER_WITH_SUBSCRIPTION_2);
        VmsSubscriptionState subscriptionState = mRouting.getSubscriptionState();
        assertEquals(2, subscriptionState.getSequenceNumber());
        assertEquals(expectedSubscriptions, new HashSet<>(subscriptionState.getLayers()));

        // Verify there is only a single listener.
        assertEquals(1,
            mRouting.getListeners(LAYER_WITH_SUBSCRIPTION_1).size());
    }

    public void testAddingSubscribersAndHalLayersWithOverlap() throws Exception {
        // Add a subscription to a layer.
        MockVmsListener listener = new MockVmsListener();
        mRouting.addSubscription(listener, LAYER_WITH_SUBSCRIPTION_1);
        mRouting.addSubscription(listener, LAYER_WITH_SUBSCRIPTION_2);

        // Add a HAL subscription to a layer there is already another subscriber for.
        mRouting.addHalSubscription(LAYER_WITH_SUBSCRIPTION_2);

        // Verify expected subscriptions are in routing manager.
        Set<VmsLayer> expectedSubscriptions = new HashSet<>();
        expectedSubscriptions.add(LAYER_WITH_SUBSCRIPTION_1);
        expectedSubscriptions.add(LAYER_WITH_SUBSCRIPTION_2);
        VmsSubscriptionState subscriptionState = mRouting.getSubscriptionState();
        assertEquals(3, subscriptionState.getSequenceNumber());
        assertEquals(expectedSubscriptions, new HashSet<>(subscriptionState.getLayers()));
    }

    public void testAddingAndRemovingLayers() throws Exception {
        // Add a subscription to a layer.
        MockVmsListener listener = new MockVmsListener();
        mRouting.addSubscription(listener, LAYER_WITH_SUBSCRIPTION_1);

        // Add a HAL subscription.
        mRouting.addHalSubscription(LAYER_WITH_SUBSCRIPTION_2);

        // Remove a subscription to a layer.
        mRouting.removeSubscription(listener, LAYER_WITH_SUBSCRIPTION_1);

        // Update the HAL subscription
        mRouting.removeHalSubscription(LAYER_WITH_SUBSCRIPTION_2);

        // Verify there are no subscribers in the routing manager.
        VmsSubscriptionState subscriptionState = mRouting.getSubscriptionState();
        assertEquals(4, subscriptionState.getSequenceNumber());
        assertTrue(subscriptionState.getLayers().isEmpty());
    }

    public void testAddingBothTypesOfSubscribers() throws Exception {
        // Add a subscription to a layer.
        MockVmsListener listenerForLayer = new MockVmsListener();
        mRouting.addSubscription(listenerForLayer, LAYER_WITH_SUBSCRIPTION_1);

        // Add a subscription without a layer.
        MockVmsListener listenerWithoutLayer = new MockVmsListener();
        mRouting.addSubscription(listenerWithoutLayer );

        // Verify 2 subscribers for the layer.
        assertEquals(2,
            mRouting.getListeners(LAYER_WITH_SUBSCRIPTION_1).size());

        // Add the listener with layer as also a listener without layer
        mRouting.addSubscription(listenerForLayer);

        // The number of listeners for the layer should remain the same as before.
        assertEquals(2,
            mRouting.getListeners(LAYER_WITH_SUBSCRIPTION_1).size());
    }

    public void testOnlyRelevantSubscribers() throws Exception {
        // Add a subscription to a layer.
        MockVmsListener listenerForLayer = new MockVmsListener();
        mRouting.addSubscription(listenerForLayer, LAYER_WITH_SUBSCRIPTION_1);

        // Add a subscription without a layer.
        MockVmsListener listenerWithoutLayer = new MockVmsListener();
        mRouting.addSubscription(listenerWithoutLayer);

        // Verify that only the subscriber without layer is returned.
        Set<MockVmsListener> expectedListeneres = new HashSet<MockVmsListener>();
        expectedListeneres.add(listenerWithoutLayer);
        assertEquals(expectedListeneres,
            mRouting.getListeners(LAYER_WITHOUT_SUBSCRIPTION));
    }

    class MockVmsListener extends IVmsSubscriberClient.Stub {
        @Override
        public void onVmsMessageReceived(VmsLayer layer, byte[] payload) {}

        @Override
        public void onLayersAvailabilityChange(List<VmsLayer> availableLayers) {}
    }
}