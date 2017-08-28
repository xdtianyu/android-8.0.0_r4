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

import android.car.Car;
import android.car.annotation.FutureFeature;
import android.car.vms.IVmsSubscriberClient;
import android.car.vms.IVmsSubscriberService;
import android.car.vms.VmsLayer;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.car.hal.VmsHalService;
import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * + Receives HAL updates by implementing VmsHalService.VmsHalListener.
 * + Offers subscriber/publisher services by implementing IVmsService.Stub.
 */
@FutureFeature
public class VmsSubscriberService extends IVmsSubscriberService.Stub
        implements CarServiceBase, VmsHalService.VmsHalSubscriberListener {
    private static final boolean DBG = true;
    private static final String PERMISSION = Car.PERMISSION_VMS_SUBSCRIBER;
    private static final String TAG = "VmsSubscriberService";

    private final Context mContext;
    private final VmsHalService mHal;

    @GuardedBy("mSubscriberServiceLock")
    private final VmsListenerManager mMessageReceivedManager = new VmsListenerManager();
    private final Object mSubscriberServiceLock = new Object();

    /**
     * Keeps track of listeners of this service.
     */
    class VmsListenerManager {
        /**
         * Allows to modify mListenerMap and mListenerDeathRecipientMap as a single unit.
         */
        private final Object mListenerManagerLock = new Object();
        @GuardedBy("mListenerManagerLock")
        private final Map<IBinder, ListenerDeathRecipient> mListenerDeathRecipientMap =
                new HashMap<>();
        @GuardedBy("mListenerManagerLock")
        private final Map<IBinder, IVmsSubscriberClient> mListenerMap = new HashMap<>();

        class ListenerDeathRecipient implements IBinder.DeathRecipient {
            private IBinder mListenerBinder;

            ListenerDeathRecipient(IBinder listenerBinder) {
                mListenerBinder = listenerBinder;
            }

            /**
             * Listener died. Remove it from this service.
             */
            @Override
            public void binderDied() {
                if (DBG) {
                    Log.d(TAG, "binderDied " + mListenerBinder);
                }

                // Get the Listener from the Binder
                IVmsSubscriberClient listener = mListenerMap.get(mListenerBinder);

                // Remove the listener subscriptions.
                if (listener != null) {
                    Log.d(TAG, "Removing subscriptions for dead listener: " + listener);
                    mHal.removeDeadListener(listener);
                } else {
                    Log.d(TAG, "Handling dead binder with no matching listener");

                }

                // Remove binder
                VmsListenerManager.this.removeListener(mListenerBinder);
            }

            void release() {
                mListenerBinder.unlinkToDeath(this, 0);
            }
        }

        public void release() {
            for (ListenerDeathRecipient recipient : mListenerDeathRecipientMap.values()) {
                recipient.release();
            }
            mListenerDeathRecipientMap.clear();
            mListenerMap.clear();
        }

        /**
         * Adds the listener and a death recipient associated to it.
         *
         * @param listener to be added.
         * @throws IllegalArgumentException if the listener is null.
         * @throws IllegalStateException    if it was not possible to link a death recipient to the
         *                                  listener.
         */
        public void add(IVmsSubscriberClient listener) {
            ICarImpl.assertPermission(mContext, PERMISSION);
            if (listener == null) {
                Log.e(TAG, "register: listener is null.");
                throw new IllegalArgumentException("listener cannot be null.");
            }
            if (DBG) {
                Log.d(TAG, "register: " + listener);
            }
            IBinder listenerBinder = listener.asBinder();
            synchronized (mListenerManagerLock) {
                if (mListenerMap.containsKey(listenerBinder)) {
                    // Already registered, nothing to do.
                    return;
                }
                ListenerDeathRecipient deathRecipient = new ListenerDeathRecipient(listenerBinder);
                try {
                    listenerBinder.linkToDeath(deathRecipient, 0);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to link death for recipient. ", e);
                    throw new IllegalStateException(Car.CAR_NOT_CONNECTED_EXCEPTION_MSG);
                }
                mListenerDeathRecipientMap.put(listenerBinder, deathRecipient);
                mListenerMap.put(listenerBinder, listener);
            }
        }

        /**
         * Removes the listener and associated death recipient.
         *
         * @param listener to be removed.
         * @throws IllegalArgumentException if listener is null.
         */
        public void remove(IVmsSubscriberClient listener) {
            if (DBG) {
                Log.d(TAG, "unregisterListener");
            }
            ICarImpl.assertPermission(mContext, PERMISSION);
            if (listener == null) {
                Log.e(TAG, "unregister: listener is null.");
                throw new IllegalArgumentException("Listener is null");
            }
            IBinder listenerBinder = listener.asBinder();
            removeListener(listenerBinder);
        }

        // Removes the listenerBinder from the current state.
        // The function assumes that binder will exist both in listeners and death recipients list.
        private void removeListener(IBinder listenerBinder) {
            synchronized (mListenerManagerLock) {
                boolean found = mListenerMap.remove(listenerBinder) != null;
                if (found) {
                    mListenerDeathRecipientMap.get(listenerBinder).release();
                    mListenerDeathRecipientMap.remove(listenerBinder);
                } else {
                    Log.e(TAG, "removeListener: listener was not previously registered.");
                }
            }
        }

        /**
         * Returns list of listeners currently registered.
         *
         * @return list of listeners.
         */
        public List<IVmsSubscriberClient> getListeners() {
            synchronized (mListenerManagerLock) {
                return new ArrayList<>(mListenerMap.values());
            }
        }
    }

    public VmsSubscriberService(Context context, VmsHalService hal) {
        mContext = context;
        mHal = hal;
    }

    // Implements CarServiceBase interface.
    @Override
    public void init() {
        mHal.addSubscriberListener(this);
    }

    @Override
    public void release() {
        mMessageReceivedManager.release();
        mHal.removeSubscriberListener(this);
    }

    @Override
    public void dump(PrintWriter writer) {
    }

    // Implements IVmsService interface.
    @Override
    public void addVmsSubscriberClientListener(IVmsSubscriberClient listener, VmsLayer layer) {
        synchronized (mSubscriberServiceLock) {
            // Add the listener so it can subscribe.
            mMessageReceivedManager.add(listener);

            // Add the subscription for the layer.
            mHal.addSubscription(listener, layer);
        }
    }

    @Override
    public void removeVmsSubscriberClientListener(IVmsSubscriberClient listener, VmsLayer layer) {
        synchronized (mSubscriberServiceLock) {
            // Remove the subscription.
            mHal.removeSubscription(listener, layer);

            // Remove the listener if it has no more subscriptions.
            if (!mHal.containsListener(listener)) {
                mMessageReceivedManager.remove(listener);
            }
        }
    }

    @Override
    public void addVmsSubscriberClientPassiveListener(IVmsSubscriberClient listener) {
        synchronized (mSubscriberServiceLock) {
            mMessageReceivedManager.add(listener);
            mHal.addSubscription(listener);
        }
    }

    @Override
    public void removeVmsSubscriberClientPassiveListener(IVmsSubscriberClient listener) {
        synchronized (mSubscriberServiceLock) {
            // Remove the subscription.
            mHal.removeSubscription(listener);

            // Remove the listener if it has no more subscriptions.
            if (!mHal.containsListener(listener)) {
                mMessageReceivedManager.remove(listener);
            }
        }
    }

    @Override
    public List<VmsLayer> getAvailableLayers() {
        //TODO(asafro): return the list of available layers once logic is implemented.
        return Collections.emptyList();
    }

    // Implements VmsHalSubscriberListener interface
    @Override
    public void onChange(VmsLayer layer, byte[] payload) {
        if(DBG) {
            Log.d(TAG, "Publishing a message for layer: " + layer);
        }

        Set<IVmsSubscriberClient> listeners = mHal.getListeners(layer);

        // If there are no listeners we're done.
        if ((listeners == null)) {
            return;
        }

        for (IVmsSubscriberClient subscriber : listeners) {
            try {
                subscriber.onVmsMessageReceived(layer, payload);
            } catch (RemoteException e) {
                // If we could not send a record, its likely the connection snapped. Let the binder
                // death handle the situation.
                Log.e(TAG, "onVmsMessageReceived calling failed: ", e);
            }
        }

    }
}
