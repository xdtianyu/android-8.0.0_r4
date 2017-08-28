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
package com.android.car.hal;

import static com.android.car.CarServiceUtils.toByteArray;
import static java.lang.Integer.toHexString;

import android.car.VehicleAreaType;
import android.car.annotation.FutureFeature;
import android.car.vms.IVmsSubscriberClient;
import android.car.vms.VmsLayer;
import android.car.vms.VmsSubscriptionState;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_1.VehicleProperty;
import android.hardware.automotive.vehicle.V2_1.VmsMessageIntegerValuesIndex;
import android.hardware.automotive.vehicle.V2_1.VmsMessageType;
import android.os.SystemClock;
import android.util.Log;
import com.android.car.CarLog;
import com.android.car.VmsRouting;
import com.android.internal.annotations.GuardedBy;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This is a glue layer between the VehicleHal and the VmsService. It sends VMS properties back and
 * forth.
 */
@FutureFeature
public class VmsHalService extends HalServiceBase {
    private static final boolean DBG = true;
    private static final int HAL_PROPERTY_ID = VehicleProperty.VEHICLE_MAP_SERVICE;
    private static final String TAG = "VmsHalService";
    private static final Set<Integer> SUPPORTED_MESSAGE_TYPES =
        new HashSet<Integer>(
            Arrays.asList(
                VmsMessageType.SUBSCRIBE,
                VmsMessageType.UNSUBSCRIBE,
                VmsMessageType.DATA));

    private boolean mIsSupported = false;
    private CopyOnWriteArrayList<VmsHalPublisherListener> mPublisherListeners =
        new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<VmsHalSubscriberListener> mSubscriberListeners =
        new CopyOnWriteArrayList<>();
    private final VehicleHal mVehicleHal;
    @GuardedBy("mLock")
    private VmsRouting mRouting = new VmsRouting();
    private final Object mLock = new Object();

    /**
     * The VmsPublisherService implements this interface to receive data from the HAL.
     */
    public interface VmsHalPublisherListener {
        void onChange(VmsSubscriptionState subscriptionState);
    }

    /**
     * The VmsSubscriberService implements this interface to receive data from the HAL.
     */
    public interface VmsHalSubscriberListener {
        void onChange(VmsLayer layer, byte[] payload);
    }

    /**
     * The VmsService implements this interface to receive data from the HAL.
     */
    protected VmsHalService(VehicleHal vehicleHal) {
        mVehicleHal = vehicleHal;
        if (DBG) {
            Log.d(TAG, "started VmsHalService!");
        }
    }

    public void addPublisherListener(VmsHalPublisherListener listener) {
        mPublisherListeners.add(listener);
    }

    public void addSubscriberListener(VmsHalSubscriberListener listener) {
        mSubscriberListeners.add(listener);
    }

    public void removePublisherListener(VmsHalPublisherListener listener) {
        mPublisherListeners.remove(listener);
    }

    public void removeSubscriberListener(VmsHalSubscriberListener listener) {
        mSubscriberListeners.remove(listener);
    }

    public void addSubscription(IVmsSubscriberClient listener, VmsLayer layer) {
        synchronized (mLock) {
            // Check if publishers need to be notified about this change in subscriptions.
            boolean firstSubscriptionForLayer = !mRouting.hasLayerSubscriptions(layer);

            // Add the listeners subscription to the layer
            mRouting.addSubscription(listener, layer);

            // Notify the publishers
            if (firstSubscriptionForLayer) {
                notifyPublishers(layer, true);
            }
        }
    }

    public void removeSubscription(IVmsSubscriberClient listener, VmsLayer layer) {
        synchronized (mLock) {
            if (!mRouting.hasLayerSubscriptions(layer)) {
                Log.i(TAG, "Trying to remove a layer with no subscription: " + layer);
                return;
            }

            // Remove the listeners subscription to the layer
            mRouting.removeSubscription(listener, layer);

            // Check if publishers need to be notified about this change in subscriptions.
            boolean layerHasSubscribers = mRouting.hasLayerSubscriptions(layer);

            // Notify the publishers
            if (!layerHasSubscribers) {
                notifyPublishers(layer, false);
            }
        }
    }

    public void addSubscription(IVmsSubscriberClient listener) {
        synchronized (mLock) {
            mRouting.addSubscription(listener);
        }
    }

    public void removeSubscription(IVmsSubscriberClient listener) {
        synchronized (mLock) {
            mRouting.removeSubscription(listener);
        }
    }

    public void removeDeadListener(IVmsSubscriberClient listener) {
        synchronized (mLock) {
            mRouting.removeDeadListener(listener);
        }
    }

    public Set<IVmsSubscriberClient> getListeners(VmsLayer layer) {
        synchronized (mLock) {
            return mRouting.getListeners(layer);
        }
    }

    public boolean isHalSubscribed(VmsLayer layer) {
        synchronized (mLock) {
            return mRouting.isHalSubscribed(layer);
        }
    }

    public VmsSubscriptionState getSubscriptionState() {
        synchronized (mLock) {
            return mRouting.getSubscriptionState();
        }
    }

    public void addHalSubscription(VmsLayer layer) {
        synchronized (mLock) {
            // Check if publishers need to be notified about this change in subscriptions.
            boolean firstSubscriptionForLayer = !mRouting.hasLayerSubscriptions(layer);

            // Add the listeners subscription to the layer
            mRouting.addHalSubscription(layer);

            if (firstSubscriptionForLayer) {
                notifyPublishers(layer, true);
            }
        }
    }

    public void removeHalSubscription(VmsLayer layer) {
        synchronized (mLock) {
            if (!mRouting.hasLayerSubscriptions(layer)) {
                Log.i(TAG, "Trying to remove a layer with no subscription: " + layer);
                return;
            }

            // Remove the listeners subscription to the layer
            mRouting.removeHalSubscription(layer);

            // Check if publishers need to be notified about this change in subscriptions.
            boolean layerHasSubscribers = mRouting.hasLayerSubscriptions(layer);

            // Notify the publishers
            if (!layerHasSubscribers) {
                notifyPublishers(layer, false);
            }
        }
    }

    public boolean containsListener(IVmsSubscriberClient listener) {
        synchronized (mLock) {
            return mRouting.containsListener(listener);
        }
    }

    /**
     * Notify all the publishers and the HAL on subscription changes regardless of who triggered
     * the change.
     *
     * @param layer          layer which is being subscribed to or unsubscribed from.
     * @param hasSubscribers indicates if the notification is for subscription or unsubscription.
     */
    public void notifyPublishers(VmsLayer layer, boolean hasSubscribers) {
        synchronized (mLock) {
            // notify the HAL
            setSubscriptionRequest(layer, hasSubscribers);

            // Notify the App publishers
            for (VmsHalPublisherListener listener : mPublisherListeners) {
                // Besides the list of layers, also a timestamp is provided to the clients.
                // They should ignore any notification with a timestamp that is older than the most
                // recent timestamp they have seen.
                listener.onChange(getSubscriptionState());
            }
        }
    }

    @Override
    public void init() {
        if (DBG) {
            Log.d(TAG, "init()");
        }
        if (mIsSupported) {
            mVehicleHal.subscribeProperty(this, HAL_PROPERTY_ID);
        }
    }

    @Override
    public void release() {
        if (DBG) {
            Log.d(TAG, "release()");
        }
        if (mIsSupported) {
            mVehicleHal.unsubscribeProperty(this, HAL_PROPERTY_ID);
        }
        mPublisherListeners.clear();
        mSubscriberListeners.clear();
    }

    @Override
    public Collection<VehiclePropConfig> takeSupportedProperties(
            Collection<VehiclePropConfig> allProperties) {
        List<VehiclePropConfig> taken = new LinkedList<>();
        for (VehiclePropConfig p : allProperties) {
            if (p.prop == HAL_PROPERTY_ID) {
                taken.add(p);
                mIsSupported = true;
                if (DBG) {
                    Log.d(TAG, "takeSupportedProperties: " + toHexString(p.prop));
                }
                break;
            }
        }
        return taken;
    }

    @Override
    public void handleHalEvents(List<VehiclePropValue> values) {
        if (DBG) {
            Log.d(TAG, "Handling a VMS property change");
        }
        for (VehiclePropValue v : values) {
            ArrayList<Integer> vec = v.value.int32Values;
            int messageType = vec.get(VmsMessageIntegerValuesIndex.VMS_MESSAGE_TYPE);
            int layerId = vec.get(VmsMessageIntegerValuesIndex.VMS_LAYER_ID);
            int layerVersion = vec.get(VmsMessageIntegerValuesIndex.VMS_LAYER_VERSION);

            // Check if message type is supported.
            if (!SUPPORTED_MESSAGE_TYPES.contains(messageType)) {
                throw new IllegalArgumentException("Unexpected message type. " +
                    "Expecting: " + SUPPORTED_MESSAGE_TYPES +
                    ". Got: " + messageType);

            }

            if (DBG) {
                Log.d(TAG,
                    "Received message for Type: " + messageType +
                        " Layer Id: " + layerId +
                        "Version: " + layerVersion);
            }
            // This is a data message intended for subscribers.
            if (messageType == VmsMessageType.DATA) {
                // Get the payload.
                byte[] payload = toByteArray(v.value.bytes);

                // Send the message.
                for (VmsHalSubscriberListener listener : mSubscriberListeners) {
                    listener.onChange(new VmsLayer(layerId, layerVersion), payload);
                }
            } else if (messageType == VmsMessageType.SUBSCRIBE) {
                addHalSubscription(new VmsLayer(layerId, layerVersion));
            } else {
                // messageType == VmsMessageType.UNSUBSCRIBE
                removeHalSubscription(new VmsLayer(layerId, layerVersion));
            }
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println(TAG);
        writer.println("VmsProperty " + (mIsSupported ? "" : "not") + " supported.");
    }

    /**
     * Updates the VMS HAL property with the given value.
     *
     * @param layer          layer data to update the hal property.
     * @param hasSubscribers if it is a subscribe or unsubscribe message.
     * @return true if the call to the HAL to update the property was successful.
     */
    public boolean setSubscriptionRequest(VmsLayer layer, boolean hasSubscribers) {
        VehiclePropValue vehiclePropertyValue = toVehiclePropValue(
                hasSubscribers ? VmsMessageType.SUBSCRIBE : VmsMessageType.UNSUBSCRIBE, layer);
        return setPropertyValue(vehiclePropertyValue);
    }

    public boolean setDataMessage(VmsLayer layer, byte[] payload) {
        VehiclePropValue vehiclePropertyValue = toVehiclePropValue(VmsMessageType.DATA,
                layer,
                payload);
        return setPropertyValue(vehiclePropertyValue);
    }

    public boolean setPropertyValue(VehiclePropValue vehiclePropertyValue) {
        try {
            mVehicleHal.set(vehiclePropertyValue);
            return true;
        } catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_PROPERTY, "set, property not ready 0x" + toHexString(HAL_PROPERTY_ID));
        }
        return false;
    }

    /** Creates a {@link VehiclePropValue} */
    private static VehiclePropValue toVehiclePropValue(int messageType, VmsLayer layer) {
        VehiclePropValue vehicleProp = new VehiclePropValue();
        vehicleProp.prop = HAL_PROPERTY_ID;
        vehicleProp.areaId = VehicleAreaType.VEHICLE_AREA_TYPE_NONE;
        VehiclePropValue.RawValue v = vehicleProp.value;

        v.int32Values.add(messageType);
        v.int32Values.add(layer.getId());
        v.int32Values.add(layer.getVersion());
        return vehicleProp;
    }

    /** Creates a {@link VehiclePropValue} with payload */
    private static VehiclePropValue toVehiclePropValue(int messageType,
            VmsLayer layer,
            byte[] payload) {
        VehiclePropValue vehicleProp = toVehiclePropValue(messageType, layer);
        VehiclePropValue.RawValue v = vehicleProp.value;
        v.bytes.ensureCapacity(payload.length);
        for (byte b : payload) {
            v.bytes.add(b);
        }
        return vehicleProp;
    }
}