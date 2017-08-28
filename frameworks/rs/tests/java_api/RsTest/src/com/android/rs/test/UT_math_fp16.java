/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.renderscript.Allocation;
import android.renderscript.RenderScript;

// IMPORTANT: This whole test fails on 32-bit x86.
public class UT_math_fp16 extends UnitTest {
    private Allocation testAllocation1, testAllocation2;

    protected UT_math_fp16(RSTestCore rstc, Context ctx) {
        super(rstc, "Math_fp16", ctx);
    }

    public void run() {
        RenderScript pRS = RenderScript.create(mCtx);
        ScriptC_math_fp16 s = new ScriptC_math_fp16(pRS);
        pRS.setMessageHandler(mRsMessage);

        s.invoke_testFp16Math();

        pRS.finish();
        waitForMessage();
        s.destroy();
        pRS.destroy();
    }
}
