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
package com.android.car.cluster;

import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.car.CarAppFocusManager;
import android.car.cluster.renderer.IInstrumentCluster;
import android.car.cluster.renderer.IInstrumentClusterNavigation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import com.android.car.AppFocusService;
import com.android.car.AppFocusService.FocusOwnershipCallback;
import com.android.car.CarInputService;
import com.android.car.CarInputService.KeyEventListener;
import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.R;
import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;

/**
 * Service responsible for interaction with car's instrument cluster.
 *
 * @hide
 */
@SystemApi
public class InstrumentClusterService implements CarServiceBase,
        FocusOwnershipCallback, KeyEventListener {

    private static final String TAG = CarLog.TAG_CLUSTER;
    private static final Boolean DBG = false;

    private final Context mContext;
    private final AppFocusService mAppFocusService;
    private final CarInputService mCarInputService;
    private final Object mSync = new Object();

    @GuardedBy("mSync")
    private ContextOwner mNavContextOwner;
    @GuardedBy("mSync")
    private IInstrumentCluster mRendererService;

    private boolean mRendererBound = false;

    private final ServiceConnection mRendererServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (DBG) {
                Log.d(TAG, "onServiceConnected, name: " + name + ", binder: " + binder);
            }
            IInstrumentCluster service = IInstrumentCluster.Stub.asInterface(binder);
            ContextOwner navContextOwner;
            synchronized (mSync) {
                mRendererService = service;
                navContextOwner = mNavContextOwner;
            }
            if (navContextOwner !=  null && service != null) {
                notifyNavContextOwnerChanged(service, navContextOwner.uid, navContextOwner.pid);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected, name: " + name);
            mRendererService = null;
            // Try to rebind with instrument cluster.
            mRendererBound = bindInstrumentClusterRendererService();
        }
    };

    public InstrumentClusterService(Context context, AppFocusService appFocusService,
            CarInputService carInputService) {
        mContext = context;
        mAppFocusService = appFocusService;
        mCarInputService = carInputService;
    }

    @Override
    public void init() {
        if (DBG) {
            Log.d(TAG, "init");
        }

        mAppFocusService.registerContextOwnerChangedCallback(this /* FocusOwnershipCallback */);
        mCarInputService.setInstrumentClusterKeyListener(this /* KeyEventListener */);
        mRendererBound = bindInstrumentClusterRendererService();
    }

    @Override
    public void release() {
        if (DBG) {
            Log.d(TAG, "release");
        }

        mAppFocusService.unregisterContextOwnerChangedCallback(this);
        if (mRendererBound) {
            mContext.unbindService(mRendererServiceConnection);
            mRendererBound = false;
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("**" + getClass().getSimpleName() + "**");
        writer.println("bound with renderer: " + mRendererBound);
        writer.println("renderer service: " + mRendererService);
    }

    @Override
    public void onFocusAcquired(int appType, int uid, int pid) {
        if (appType != CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION) {
            return;
        }

        IInstrumentCluster service;
        synchronized (mSync) {
            mNavContextOwner = new ContextOwner(uid, pid);
            service = mRendererService;
        }

        if (service != null) {
            notifyNavContextOwnerChanged(service, uid, pid);
        }
    }

    @Override
    public void onFocusAbandoned(int appType, int uid, int pid) {
        if (appType != CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION) {
            return;
        }

        IInstrumentCluster service;
        synchronized (mSync) {
            if (mNavContextOwner == null
                    || mNavContextOwner.uid != uid
                    || mNavContextOwner.pid != pid) {
                return;  // Nothing to do here, no active focus or not owned by this client.
            }

            mNavContextOwner = null;
            service = mRendererService;
        }

        if (service != null) {
            notifyNavContextOwnerChanged(service, 0, 0);
        }
    }

    private static void notifyNavContextOwnerChanged(IInstrumentCluster service, int uid, int pid) {
        try {
            service.setNavigationContextOwner(uid, pid);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to call setNavigationContextOwner", e);
        }
    }

    private boolean bindInstrumentClusterRendererService() {
        String rendererService = mContext.getString(R.string.instrumentClusterRendererService);
        if (TextUtils.isEmpty(rendererService)) {
            Log.i(TAG, "Instrument cluster renderer was not configured");
            return false;
        }

        Log.d(TAG, "bindInstrumentClusterRendererService, component: " + rendererService);

        Intent intent = new Intent();
        intent.setComponent(ComponentName.unflattenFromString(rendererService));
        return mContext.bindService(intent, mRendererServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Nullable
    public IInstrumentClusterNavigation getNavigationService() {
        IInstrumentCluster service;
        synchronized (mSync) {
            service = mRendererService;
        }

        try {
            return service == null ? null : service.getNavigationService();
        } catch (RemoteException e) {
            Log.e(TAG, "getNavigationServiceBinder" , e);
            return null;
        }
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (DBG) {
            Log.d(TAG, "InstrumentClusterService#onKeyEvent: " + event);
        }

        IInstrumentCluster service;
        synchronized (mSync) {
            service = mRendererService;
        }

        if (service != null) {
            try {
                service.onKeyEvent(event);
            } catch (RemoteException e) {
                Log.e(TAG, "onKeyEvent", e);
            }
        }
        return true;
    }

    private static class ContextOwner {
        final int uid;
        final int pid;

        ContextOwner(int uid, int pid) {
            this.uid = uid;
            this.pid = pid;
        }
    }
}
