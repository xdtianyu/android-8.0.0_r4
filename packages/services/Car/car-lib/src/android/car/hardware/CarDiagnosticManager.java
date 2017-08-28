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

import android.car.Car;
import android.car.CarApiUtil;
import android.car.CarLibLog;
import android.car.CarManagerBase;
import android.car.CarNotConnectedException;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import com.android.car.internal.CarPermission;
import com.android.car.internal.CarRatedListeners;
import com.android.car.internal.SingleMessageHandler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** API for monitoring car diagnostic data. */
/** @hide */
public final class CarDiagnosticManager implements CarManagerBase {
    public static final int FRAME_TYPE_FLAG_LIVE = 0;
    public static final int FRAME_TYPE_FLAG_FREEZE = 1;

    private static final int MSG_DIAGNOSTIC_EVENTS = 0;

    private final ICarDiagnostic mService;
    private final SparseArray<CarDiagnosticListeners> mActiveListeners = new SparseArray<>();

    /** Handles call back into clients. */
    private final SingleMessageHandler<CarDiagnosticEvent> mHandlerCallback;

    private CarDiagnosticEventListenerToService mListenerToService;

    private final CarPermission mVendorExtensionPermission;

    public CarDiagnosticManager(IBinder service, Context context, Handler handler) {
        mService = ICarDiagnostic.Stub.asInterface(service);
        mHandlerCallback = new SingleMessageHandler<CarDiagnosticEvent>(handler.getLooper(),
            MSG_DIAGNOSTIC_EVENTS) {
            @Override
            protected void handleEvent(CarDiagnosticEvent event) {
                CarDiagnosticListeners listeners;
                synchronized (mActiveListeners) {
                    listeners = mActiveListeners.get(event.frameType);
                }
                if (listeners != null) {
                    listeners.onDiagnosticEvent(event);
                }
            }
        };
        mVendorExtensionPermission = new CarPermission(context, Car.PERMISSION_VENDOR_EXTENSION);
    }

    @Override
    public void onCarDisconnected() {
        synchronized(mActiveListeners) {
            mActiveListeners.clear();
            mListenerToService = null;
        }
    }

    /** Listener for diagnostic events. Callbacks are called in the Looper context. */
    public interface OnDiagnosticEventListener {
        /**
         * Called when there is a diagnostic event from the car.
         *
         * @param carDiagnosticEvent
         */
        void onDiagnosticEvent(final CarDiagnosticEvent carDiagnosticEvent);
    }

    // OnDiagnosticEventListener registration

    private void assertFrameType(int frameType) {
        switch(frameType) {
            case FRAME_TYPE_FLAG_FREEZE:
            case FRAME_TYPE_FLAG_LIVE:
                return;
            default:
                throw new IllegalArgumentException(String.format(
                            "%d is not a valid diagnostic frame type", frameType));
        }
    }

    /**
     * Register a new listener for events of a given frame type and rate.
     * @param listener
     * @param frameType
     * @param rate
     * @return true if the registration was successful; false otherwise
     * @throws CarNotConnectedException
     * @throws IllegalArgumentException
     */
    public boolean registerListener(OnDiagnosticEventListener listener, int frameType, int rate)
                throws CarNotConnectedException, IllegalArgumentException {
        assertFrameType(frameType);
        synchronized(mActiveListeners) {
            if (null == mListenerToService) {
                mListenerToService = new CarDiagnosticEventListenerToService(this);
            }
            boolean needsServerUpdate = false;
            CarDiagnosticListeners listeners = mActiveListeners.get(frameType);
            if (listeners == null) {
                listeners = new CarDiagnosticListeners(rate);
                mActiveListeners.put(frameType, listeners);
                needsServerUpdate = true;
            }
            if (listeners.addAndUpdateRate(listener, rate)) {
                needsServerUpdate = true;
            }
            if (needsServerUpdate) {
                if (!registerOrUpdateDiagnosticListener(frameType, rate)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Unregister a listener, causing it to stop receiving all diagnostic events.
     * @param listener
     */
    public void unregisterListener(OnDiagnosticEventListener listener) {
        synchronized(mActiveListeners) {
            for(int i = 0; i < mActiveListeners.size(); i++) {
                doUnregisterListenerLocked(listener, mActiveListeners.keyAt(i));
            }
        }
    }

    private void doUnregisterListenerLocked(OnDiagnosticEventListener listener, int sensor) {
        CarDiagnosticListeners listeners = mActiveListeners.get(sensor);
        if (listeners != null) {
            boolean needsServerUpdate = false;
            if (listeners.contains(listener)) {
                needsServerUpdate = listeners.remove(listener);
            }
            if (listeners.isEmpty()) {
                try {
                    mService.unregisterDiagnosticListener(sensor,
                        mListenerToService);
                } catch (RemoteException e) {
                    //ignore
                }
                mActiveListeners.remove(sensor);
            } else if (needsServerUpdate) {
                try {
                    registerOrUpdateDiagnosticListener(sensor, listeners.getRate());
                } catch (CarNotConnectedException e) {
                    // ignore
                }
            }
        }
    }

    private boolean registerOrUpdateDiagnosticListener(int frameType, int rate)
        throws CarNotConnectedException {
        try {
            return mService.registerOrUpdateDiagnosticListener(frameType, rate, mListenerToService);
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return false;
    }

    // ICarDiagnostic forwards

    /**
     * Retrieve the most-recently acquired live frame data from the car.
     * @return
     * @throws CarNotConnectedException
     */
    public CarDiagnosticEvent getLatestLiveFrame() throws CarNotConnectedException {
        try {
            return mService.getLatestLiveFrame();
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return null;
    }

    /**
     * Return the list of the timestamps for which a freeze frame is currently stored.
     * @return
     * @throws CarNotConnectedException
     */
    public long[] getFreezeFrameTimestamps() throws CarNotConnectedException {
        try {
            return mService.getFreezeFrameTimestamps();
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return new long[]{};
    }

    /**
     * Retrieve the freeze frame event data for a given timestamp, if available.
     * @param timestamp
     * @return
     * @throws CarNotConnectedException
     */
    public CarDiagnosticEvent getFreezeFrame(long timestamp) throws CarNotConnectedException {
        try {
            return mService.getFreezeFrame(timestamp);
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return null;
    }

    /**
     * Clear the freeze frame information from vehicle memory at the given timestamps.
     * @param timestamps
     * @return
     * @throws CarNotConnectedException
     */
    public boolean clearFreezeFrames(long... timestamps) throws CarNotConnectedException {
        try {
            return mService.clearFreezeFrames(timestamps);
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return false;
    }

    private static class CarDiagnosticEventListenerToService
            extends ICarDiagnosticEventListener.Stub {
        private final WeakReference<CarDiagnosticManager> mManager;

        public CarDiagnosticEventListenerToService(CarDiagnosticManager manager) {
            mManager = new WeakReference<>(manager);
        }

        private void handleOnDiagnosticEvents(CarDiagnosticManager manager,
            List<CarDiagnosticEvent> events) {
            manager.mHandlerCallback.sendEvents(events);
        }

        @Override
        public void onDiagnosticEvents(List<CarDiagnosticEvent> events) {
            CarDiagnosticManager manager = mManager.get();
            if (manager != null) {
                handleOnDiagnosticEvents(manager, events);
            }
        }
    }

    private class CarDiagnosticListeners extends CarRatedListeners<OnDiagnosticEventListener> {
        CarDiagnosticListeners(int rate) {
            super(rate);
        }

        void onDiagnosticEvent(final CarDiagnosticEvent event) {
            // throw away old sensor data as oneway binder call can change order.
            long updateTime = event.timestamp;
            if (updateTime < mLastUpdateTime) {
                Log.w(CarLibLog.TAG_DIAGNOSTIC, "dropping old sensor data");
                return;
            }
            mLastUpdateTime = updateTime;
            final boolean hasVendorExtensionPermission = mVendorExtensionPermission.checkGranted();
            final CarDiagnosticEvent eventToDispatch = hasVendorExtensionPermission ?
                    event :
                    event.withVendorSensorsRemoved();
            List<OnDiagnosticEventListener> listeners;
            synchronized (mActiveListeners) {
                listeners = new ArrayList<>(getListeners());
            }
            listeners.forEach(new Consumer<OnDiagnosticEventListener>() {

                @Override
                public void accept(OnDiagnosticEventListener listener) {
                    listener.onDiagnosticEvent(eventToDispatch);
                }
            });
        }
    }
}
