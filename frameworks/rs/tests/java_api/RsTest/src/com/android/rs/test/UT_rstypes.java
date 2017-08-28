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

public class UT_rstypes extends UnitTest {

    protected UT_rstypes(RSTestCore rstc, Context ctx) {
        super(rstc, "rsTypes", ctx);
    }

    public void run() {
        RenderScript pRS = RenderScript.create(mCtx);
        ScriptC_rstypes s = new ScriptC_rstypes(pRS);
        pRS.setMessageHandler(mRsMessage);
        s.invoke_test_rstypes(0, 0);
        pRS.finish();
        waitForMessage();
        s.destroy();
        pRS.destroy();
    }
}
