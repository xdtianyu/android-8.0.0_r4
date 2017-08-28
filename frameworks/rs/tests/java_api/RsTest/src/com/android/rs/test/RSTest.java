/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.rs.test;

import android.app.ListActivity;
import android.os.Bundle;
import android.renderscript.RenderScript;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StrictMode;
import android.provider.Settings.System;
import android.util.Log;

public class RSTest extends ListActivity {

    private static final String LOG_TAG = "RSTest";
    private static final boolean DEBUG = false;
    private static final boolean LOG_ENABLED = false;

    private RenderScript mRS;
    private RSTestCore RSTC;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                               .detectLeakedClosableObjects()
                               .penaltyLog()
                               .build());

        mRS = RenderScript.create(this);

        RSTC = new RSTestCore(this);
        RSTC.init(mRS);
    }

    static void log(String message) {
        if (LOG_ENABLED) {
            Log.v(LOG_TAG, message);
        }
    }


}
