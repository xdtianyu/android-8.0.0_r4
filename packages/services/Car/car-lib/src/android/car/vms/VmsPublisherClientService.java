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


import android.app.Service;
import android.car.annotation.FutureFeature;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.annotation.Nullable;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Services that need VMS publisher services need to inherit from this class and also need to be
 * declared in the array vmsPublisherClients located in
 * packages/services/Car/service/res/values/config.xml (most likely, this file will be in an overlay
 * of the target product.
 *
 * The {@link com.android.car.VmsPublisherService} will start this service. The callback
 * {@link #onVmsPublisherServiceReady()} notifies when VMS publisher services (i.e.
 * {@link #publish(int, int, byte[])} and {@link #getSubscribers()}) can be used.
 *
 * SystemApi candidate.
 *
 * @hide
 */
@FutureFeature
public abstract class VmsPublisherClientService extends Service {
    private static final boolean DBG = true;
    private static final String TAG = "VmsPublisherClient";

    private final Object mLock = new Object();

    private Handler mHandler = new VmsEventHandler(this);
    private final VmsPublisherClientBinder mVmsPublisherClient = new VmsPublisherClientBinder(this);
    private volatile IVmsPublisherService mVmsPublisherService = null;
    @GuardedBy("mLock")
    private IBinder mToken = null;

    @Override
    public final IBinder onBind(Intent intent) {
        if (DBG) {
            Log.d(TAG, "onBind, intent: " + intent);
        }
        return mVmsPublisherClient.asBinder();
    }

    @Override
    public final boolean onUnbind(Intent intent) {
        if (DBG) {
            Log.d(TAG, "onUnbind, intent: " + intent);
        }
        stopSelf();
        return super.onUnbind(intent);
    }

    public void setToken(IBinder token) {
        synchronized (mLock) {
            mToken = token;
        }
    }

    /**
     * Notifies that the publisher services are ready.
     */
    public abstract void onVmsPublisherServiceReady();

    /**
     * Publishers need to implement this method to receive notifications of subscription changes.
     *
     * @param subscriptionState  layers with subscribers and a sequence number.
     */
    public abstract void onVmsSubscriptionChange(VmsSubscriptionState subscriptionState);

    /**
     * Uses the VmsPublisherService binder to publish messages.
     *
     * @param layer   the layer to publish to.
     * @param payload the message to be sent.
     * @return if the call to the method VmsPublisherService.publish was successful.
     */
    public final boolean publish(VmsLayer layer, byte[] payload) {
        if (DBG) {
            Log.d(TAG, "Publishing for layer : " + layer);
        }
        if (mVmsPublisherService == null) {
            throw new IllegalStateException("VmsPublisherService not set.");
        }

        IBinder token;
        synchronized (mLock) {
            token = mToken;
        }
        if (token == null) {
            throw new IllegalStateException("VmsPublisherService does not have a valid token.");
        }
        try {
            mVmsPublisherService.publish(token, layer, payload);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "unable to publish message: " + payload, e);
        }
        return false;
    }

    /**
     * Uses the VmsPublisherService binder to get the list of layer/version that have any
     * subscribers.
     *
     * @return list of layer/version or null in case of error.
     */
    public final @Nullable VmsSubscriptionState getSubscriptions() {
        if (mVmsPublisherService == null) {
            throw new IllegalStateException("VmsPublisherService not set.");
        }
        try {
            return mVmsPublisherService.getSubscriptions();
        } catch (RemoteException e) {
            Log.e(TAG, "unable to invoke binder method.", e);
        }
        return null;
    }

    private void setVmsPublisherService(IVmsPublisherService service) {
        mVmsPublisherService = service;
        onVmsPublisherServiceReady();
    }

    /**
     * Implements the interface that the VMS service uses to communicate with this client.
     */
    private static class VmsPublisherClientBinder extends IVmsPublisherClient.Stub {
        private final WeakReference<VmsPublisherClientService> mVmsPublisherClientService;
        @GuardedBy("mSequenceLock")
        private long mSequence = -1;
        private final Object mSequenceLock = new Object();

        public VmsPublisherClientBinder(VmsPublisherClientService vmsPublisherClientService) {
            mVmsPublisherClientService = new WeakReference<>(vmsPublisherClientService);
        }

        @Override
        public void setVmsPublisherService(IBinder token, IVmsPublisherService service)
                throws RemoteException {
            VmsPublisherClientService vmsPublisherClientService = mVmsPublisherClientService.get();
            if (vmsPublisherClientService == null) return;
            if (DBG) {
                Log.d(TAG, "setting VmsPublisherService.");
            }
            Handler handler = vmsPublisherClientService.mHandler;
            handler.sendMessage(
                    handler.obtainMessage(VmsEventHandler.SET_SERVICE_CALLBACK, service));
            vmsPublisherClientService.setToken(token);
        }

        @Override
        public void onVmsSubscriptionChange(VmsSubscriptionState subscriptionState)
                throws RemoteException {
            VmsPublisherClientService vmsPublisherClientService = mVmsPublisherClientService.get();
            if (vmsPublisherClientService == null) return;
            if (DBG) {
                Log.d(TAG, "subscription event: " + subscriptionState);
            }
            synchronized (mSequenceLock) {
                if (subscriptionState.getSequenceNumber() <= mSequence) {
                    Log.w(TAG, "Sequence out of order. Current sequence = " + mSequence
                            + "; expected new sequence = " + subscriptionState.getSequenceNumber());
                    // Do not propagate old notifications.
                    return;
                } else {
                    mSequence = subscriptionState.getSequenceNumber();
                }
            }
            Handler handler = vmsPublisherClientService.mHandler;
            handler.sendMessage(
                    handler.obtainMessage(VmsEventHandler.ON_SUBSCRIPTION_CHANGE_EVENT,
                            subscriptionState));
        }
    }

    /**
     * Receives events from the binder thread and dispatches them.
     */
    private final static class VmsEventHandler extends Handler {
        /** Constants handled in the handler */
        private static final int ON_SUBSCRIPTION_CHANGE_EVENT = 0;
        private static final int SET_SERVICE_CALLBACK = 1;

        private final WeakReference<VmsPublisherClientService> mVmsPublisherClientService;

        VmsEventHandler(VmsPublisherClientService service) {
            super(Looper.getMainLooper());
            mVmsPublisherClientService = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            VmsPublisherClientService service = mVmsPublisherClientService.get();
            if (service == null) return;
            switch (msg.what) {
                case ON_SUBSCRIPTION_CHANGE_EVENT:
                    VmsSubscriptionState subscriptionState = (VmsSubscriptionState) msg.obj;
                    service.onVmsSubscriptionChange(subscriptionState);
                    break;
                case SET_SERVICE_CALLBACK:
                    service.setVmsPublisherService((IVmsPublisherService) msg.obj);
                    break;
                default:
                    Log.e(TAG, "Event type not handled:  " + msg.what);
                    break;
            }
        }
    }
}
