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

import android.car.Car;
import android.car.CarManagerBase;
import android.car.CarNotConnectedException;
import android.car.annotation.FutureFeature;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import com.android.internal.annotations.GuardedBy;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * API for interfacing with the VmsSubscriberService. It supports a single listener that can
 * (un)subscribe to different layers. After getting an instance of this manager, the first step
 * must be to call #setListener. After that, #subscribe and #unsubscribe methods can be invoked.
 * SystemApi candidate
 *
 * @hide
 */
@FutureFeature
public final class VmsSubscriberManager implements CarManagerBase {
    private static final boolean DBG = true;
    private static final String TAG = "VmsSubscriberManager";

    private final Handler mHandler;
    private final IVmsSubscriberService mVmsSubscriberService;
    private final IVmsSubscriberClient mIListener;
    private final Object mListenerLock = new Object();
    @GuardedBy("mListenerLock")
    private VmsSubscriberClientListener mListener;

    /** Interface exposed to VMS subscribers: it is a wrapper of IVmsSubscriberClient. */
    public interface VmsSubscriberClientListener {
        /** Called when the property is updated */
        void onVmsMessageReceived(VmsLayer layer, byte[] payload);

        /** Called when layers availability change */
        void onLayersAvailabilityChange(List<VmsLayer> availableLayers);
    }

    /**
     * Allows to asynchronously dispatch onVmsMessageReceived events.
     */
    private final static class VmsEventHandler extends Handler {
        /** Constants handled in the handler */
        private static final int ON_RECEIVE_MESSAGE_EVENT = 0;
        private static final int ON_AVAILABILITY_CHANGE_EVENT = 1;

        private final WeakReference<VmsSubscriberManager> mMgr;

        VmsEventHandler(VmsSubscriberManager mgr, Looper looper) {
            super(looper);
            mMgr = new WeakReference<>(mgr);
        }

        @Override
        public void handleMessage(Message msg) {
            VmsSubscriberManager mgr = mMgr.get();
            switch (msg.what) {
                case ON_RECEIVE_MESSAGE_EVENT:
                    if (mgr != null) {
                        // Parse the message
                        VmsDataMessage vmsDataMessage = (VmsDataMessage) msg.obj;

                        // Dispatch the parsed message
                        mgr.dispatchOnReceiveMessage(vmsDataMessage.getLayer(),
                                                     vmsDataMessage.getPayload());
                    }
                    break;
                case ON_AVAILABILITY_CHANGE_EVENT:
                    if (mgr != null) {
                        // Parse the message
                        List<VmsLayer> vmsAvailabilityChangeMessage = (List<VmsLayer>) msg.obj;

                        // Dispatch the parsed message
                        mgr.dispatchOnAvailabilityChangeMessage(vmsAvailabilityChangeMessage);
                    }
                    break;

                default:
                    Log.e(VmsSubscriberManager.TAG, "Event type not handled:  " + msg.what);
                    break;
            }
        }
    }

    public VmsSubscriberManager(IBinder service, Handler handler) {
        mVmsSubscriberService = IVmsSubscriberService.Stub.asInterface(service);
        mHandler = new VmsEventHandler(this, handler.getLooper());
        mIListener = new IVmsSubscriberClient.Stub() {
            @Override
            public void onVmsMessageReceived(VmsLayer layer, byte[] payload)
                throws RemoteException {
                // Create the data message
                VmsDataMessage vmsDataMessage = new VmsDataMessage(layer, payload);
                mHandler.sendMessage(
                        mHandler.obtainMessage(
                            VmsEventHandler.ON_RECEIVE_MESSAGE_EVENT,
                            vmsDataMessage));
            }

            @Override
            public void onLayersAvailabilityChange(List<VmsLayer> availableLayers) {
                mHandler.sendMessage(
                    mHandler.obtainMessage(
                        VmsEventHandler.ON_AVAILABILITY_CHANGE_EVENT,
                        availableLayers));
            }
        };
    }

    /**
     * Sets the listener ({@link #mListener}) this manager is linked to. Subscriptions to the
     * {@link com.android.car.VmsSubscriberService} are done through the {@link #mIListener}.
     * Therefore, notifications from the {@link com.android.car.VmsSubscriberService} are received
     * by the {@link #mIListener} and then forwarded to the {@link #mListener}.
     *
     * @param listener subscriber listener that will handle onVmsMessageReceived events.
     * @throws IllegalStateException if the listener was already set.
     */
    public void setListener(VmsSubscriberClientListener listener) {
        if (DBG) {
            Log.d(TAG, "Setting listener.");
        }
        synchronized (mListenerLock) {
            if (mListener != null) {
                throw new IllegalStateException("Listener is already configured.");
            }
            mListener = listener;
        }
    }

    /**
     * Removes the listener and unsubscribes from all the layer/version.
     */
    public void clearListener() {
        synchronized (mListenerLock) {
            mListener = null;
        }
        // TODO(antoniocortes): logic to unsubscribe from all the layer/version pairs.
    }

    /**
     * Subscribes to listen to the layer specified.
     *
     * @param layer the layer to subscribe to.
     * @throws IllegalStateException if the listener was not set via {@link #setListener}.
     */
    public void subscribe(VmsLayer layer)
            throws CarNotConnectedException {
        if (DBG) {
            Log.d(TAG, "Subscribing to layer: " + layer);
        }
        VmsSubscriberClientListener listener;
        synchronized (mListenerLock) {
            listener = mListener;
        }
        if (listener == null) {
            Log.w(TAG, "subscribe: listener was not set, " +
                    "setListener must be called first.");
            throw new IllegalStateException("Listener was not set.");
        }
        try {
            mVmsSubscriberService.addVmsSubscriberClientListener(mIListener, layer);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not connect: ", e);
            throw new CarNotConnectedException(e);
        } catch (IllegalStateException ex) {
            Car.checkCarNotConnectedExceptionFromCarService(ex);
        }
    }

    public void subscribeAll()
        throws CarNotConnectedException {
        if (DBG) {
            Log.d(TAG, "Subscribing passively to all data messages");
        }
        VmsSubscriberClientListener listener;
        synchronized (mListenerLock) {
            listener = mListener;
        }
        if (listener == null) {
            Log.w(TAG, "subscribe: listener was not set, " +
                "setListener must be called first.");
            throw new IllegalStateException("Listener was not set.");
        }
        try {
            mVmsSubscriberService.addVmsSubscriberClientPassiveListener(mIListener);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not connect: ", e);
            throw new CarNotConnectedException(e);
        } catch (IllegalStateException ex) {
            Car.checkCarNotConnectedExceptionFromCarService(ex);
        }
    }

    /**
     * Unsubscribes from the layer/version specified.
     *
     * @param layer   the layer to unsubscribe from.
     * @throws IllegalStateException if the listener was not set via {@link #setListener}.
     */
    public void unsubscribe(VmsLayer layer) {
        if (DBG) {
            Log.d(TAG, "Unsubscribing from layer: " + layer);
        }
        VmsSubscriberClientListener listener;
        synchronized (mListenerLock) {
            listener = mListener;
        }
        if (listener == null) {
            Log.w(TAG, "unsubscribe: listener was not set, " +
                    "setListener must be called first.");
            throw new IllegalStateException("Listener was not set.");
        }
        try {
            mVmsSubscriberService.removeVmsSubscriberClientListener(mIListener, layer);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to unregister subscriber", e);
            // ignore
        } catch (IllegalStateException ex) {
            Car.hideCarNotConnectedExceptionFromCarService(ex);
        }
    }

    private void dispatchOnReceiveMessage(VmsLayer layer, byte[] payload) {
        VmsSubscriberClientListener listener;
        synchronized (mListenerLock) {
            listener = mListener;
        }
        if (listener == null) {
            Log.e(TAG, "Listener died, not dispatching event.");
            return;
        }
        listener.onVmsMessageReceived(layer, payload);
    }

    private void dispatchOnAvailabilityChangeMessage(List<VmsLayer> availableLayers) {
        VmsSubscriberClientListener listener;
        synchronized (mListenerLock) {
            listener = mListener;
        }
        if (listener == null) {
            Log.e(TAG, "Listener died, not dispatching event.");
            return;
        }
        listener.onLayersAvailabilityChange(availableLayers);
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        clearListener();
    }

    private static final class VmsDataMessage {
        private final VmsLayer mLayer;
        private final byte[] mPayload;

        public VmsDataMessage(VmsLayer layer, byte[] payload) {
            mLayer = layer;
            mPayload = payload;
        }

        public VmsLayer getLayer() {
            return mLayer;
        }
        public byte[] getPayload() {
            return mPayload;
        }
    }
}
