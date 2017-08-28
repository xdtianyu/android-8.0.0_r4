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

package com.android.tradefed.profiler.recorder;

import java.util.Map;

/**
 * A representation of an output line from /d/tracing/trace.
 */
public class TraceLine {

    public static final String TAG_TASK = "TASK";
    public static final String TAG_CORE_NUM = "CPU#";
    public static final String TAG_IRQS_OFF = "IRQS-OFF";
    public static final String TAG_NEED_RESCHED = "NEED-RESCHED";
    public static final String TAG_HARD_IRQ = "HARDIRQ";
    public static final String TAG_PREEMPT_DELAY = "PREEMPT-DELAY";
    public static final String TAG_TIMESTAMP = "TIMESTAMP";
    public static final String TAG_FUNCTION = "FUNCTION";

    private String mTaskName;
    private int mCoreNum;
    private boolean mIrqsOff;
    private boolean mNeedsResched;
    private boolean mHardIrq;
    private int mPreemptDelay;
    private double mTimestamp;
    private String mFunctionName;
    private Map<String, Long> mFunctionParams;

    public String getTaskName() {
        return mTaskName;
    }

    public int getCoreNum() {
        return mCoreNum;
    }

    public boolean getIrqsOff() {
        return mIrqsOff;
    }

    public boolean getNeedsResched() {
        return mNeedsResched;
    }

    public boolean getHardIrq() {
        return mHardIrq;
    }

    public int getPreemptDelay() {
        return mPreemptDelay;
    }

    public double getTimestamp() {
        return mTimestamp;
    }

    public String getFunctionName() {
        return mFunctionName;
    }

    public Map<String, Long> getFunctionParams() {
        return mFunctionParams;
    }

    public void setTaskName(String taskName) {
        mTaskName = taskName;
    }

    public void setCoreNum(int coreNum) {
        mCoreNum = coreNum;
    }

    public void setIrqsOff(boolean irqsOff) {
        mIrqsOff = irqsOff;
    }

    public void setNeedsResched(boolean needsResched) {
        mNeedsResched = needsResched;
    }

    public void setHardIrq(boolean hardIrq) {
        mHardIrq = hardIrq;
    }

    public void setPreemptDelay(int preemptDelay) {
        mPreemptDelay = preemptDelay;
    }

    public void setTimestamp(double timestamp) {
        mTimestamp = timestamp;
    }

    public void setFunctionName(String functionName) {
        mFunctionName = functionName;
    }

    public void setFunctionParams(Map<String, Long> functionParams) {
        mFunctionParams = functionParams;
    }
}
