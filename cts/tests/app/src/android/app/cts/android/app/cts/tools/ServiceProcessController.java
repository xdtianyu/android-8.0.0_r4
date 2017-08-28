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

package android.app.cts.android.app.cts.tools;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import com.android.compatibility.common.util.SystemUtil;

import java.io.IOException;

/**
 * Helper for monitoring and controlling the state of a process under test.
 * Primarily currently a convenience for cleanly killing a process and waiting
 * for it to entirely disappear from the system.
 */
public final class ServiceProcessController {
    final Context mContext;
    final Instrumentation mInstrumentation;
    final String mMyPackageName;
    final Intent[] mServiceIntents;
    final String mServicePackage;

    final ActivityManager mAm;
    final Parcel mData;
    final ServiceConnectionHandler[] mConnections;
    final UidImportanceListener mUidForegroundListener;
    final UidImportanceListener mUidGoneListener;

    public ServiceProcessController(Context context, Instrumentation instrumentation,
            String myPackageName, Intent[] serviceIntents)
            throws IOException, PackageManager.NameNotFoundException {
        mContext = context;
        mInstrumentation = instrumentation;
        mMyPackageName = myPackageName;
        mServiceIntents = serviceIntents;
        mServicePackage = mServiceIntents[0].getComponent().getPackageName();
        String cmd = "pm grant " + mMyPackageName + " " + Manifest.permission.PACKAGE_USAGE_STATS;
        String result = SystemUtil.runShellCommand(mInstrumentation, cmd);
        /*
        Log.d("XXXX", "Invoke: " + cmd);
        Log.d("XXXX", "Result: " + result);
        Log.d("XXXX", SystemUtil.runShellCommand(getInstrumentation(), "dumpsys package "
                + STUB_PACKAGE_NAME));
        */

        mAm = mContext.getSystemService(ActivityManager.class);
        mData = Parcel.obtain();
        mConnections = new ServiceConnectionHandler[serviceIntents.length];
        for (int i=0; i<serviceIntents.length; i++) {
            mConnections[i] = new ServiceConnectionHandler(mContext, serviceIntents[i]);
        }

        ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfo(
                mServicePackage, 0);

        mUidForegroundListener = new UidImportanceListener(appInfo.uid);
        mAm.addOnUidImportanceListener(mUidForegroundListener,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE);
        mUidGoneListener = new UidImportanceListener(appInfo.uid);
        mAm.addOnUidImportanceListener(mUidGoneListener,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_EMPTY);
    }

    public void cleanup() {
        mAm.removeOnUidImportanceListener(mUidGoneListener);
        mAm.removeOnUidImportanceListener(mUidForegroundListener);
        mData.recycle();
    }

    public ServiceConnectionHandler getConnection(int index) {
        return mConnections[index];
    }

    public UidImportanceListener getUidForegroundListener() {
        return mUidForegroundListener;
    }

    public UidImportanceListener getUidGoneListener() {
        return mUidGoneListener;
    }

    public void ensureProcessGone(long timeout) {
        for (int i=0; i<mConnections.length; i++) {
            mConnections[i].bind(timeout);
            IBinder serviceBinder = mConnections[i].getServiceIBinder();
            try {
                serviceBinder.transact(IBinder.FIRST_CALL_TRANSACTION, mData, null, 0);
            } catch (RemoteException e) {
            }
            mConnections[i].unbind(timeout);
        }

        // Wait for uid's process to go away.
        mUidGoneListener.waitForValue(ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE, timeout);
        int importance = mAm.getPackageImportance(mServicePackage);
        if (importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE) {
            throw new IllegalStateException("Unexpected importance after killing process: "
                    + importance);
        }
    }
}
