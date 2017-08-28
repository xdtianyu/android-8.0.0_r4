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

package com.android.cellbroadcastreceiver;

import android.content.Context;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.ServiceManager;
import android.telephony.SmsManager;
import android.util.Log;
import android.telephony.CarrierConfigManager;
import android.util.SparseArray;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public abstract class CellBroadcastTest {

    protected static String TAG;

    private SparseArray<PersistableBundle> mBundles = new SparseArray<>();

    MockedServiceManager mMockedServiceManager;

    @Mock
    Context mContext;
    @Mock
    CarrierConfigManager mCarrierConfigManager;

    protected void setUp(String tag) throws Exception {
        TAG = tag;
        MockitoAnnotations.initMocks(this);
        mMockedServiceManager = new MockedServiceManager();
        initContext();
    }

    private void initContext() {
        doReturn(mCarrierConfigManager).when(mContext).
                getSystemService(eq(Context.CARRIER_CONFIG_SERVICE));
    }

    void carrierConfigSetStringArray(int subId, String key, String[] values) {
        if (mBundles.get(subId) == null) {
            mBundles.put(subId, new PersistableBundle());
        }
        mBundles.get(subId).putStringArray(key, values);
        doReturn(mBundles.get(subId)).when(mCarrierConfigManager).getConfigForSubId(eq(subId));
    }

    protected void tearDown() throws Exception {
        mMockedServiceManager.restoreAllServices();
    }

    protected static void logd(String s) {
        Log.d(TAG, s);
    }
}
