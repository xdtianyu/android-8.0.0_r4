/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.Context;
import android.renderscript.RenderScript;

public class UT_rstime extends UnitTest {

    protected UT_rstime(RSTestCore rstc, Context ctx) {
        super(rstc, "rsTime", ctx);
    }

    public void run() {
        RenderScript pRS = RenderScript.create(mCtx);
        ScriptC_rstime s = new ScriptC_rstime(pRS);
        pRS.setMessageHandler(mRsMessage);
        s.setTimeZone("America/Los_Angeles");
        s.invoke_test_rstime(0, 0);
        pRS.finish();
        waitForMessage();
        s.destroy();
        pRS.destroy();
    }
}
