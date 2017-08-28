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
package com.android.car.dialer.telecom;

import android.support.v4.util.Pair;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds a list of {@link UiCall} and implements convenient mapping from underlying
 * {@code Call} class to {@link UiCall}.
 */
public abstract class UiCallList<C> {
    private Map<C, UiCall> mCallToCallInfoMap = new HashMap<>();
    private SparseArray<Pair<UiCall, C>> mIdToCallMap = new SparseArray<>();

    /**
     * Creates or gets existing {@code CallInfo} instance for a given key
     */
    public UiCall getOrCreate(C call) {
        if (call == null) {
            return null;
        }
        UiCall uiCall = mCallToCallInfoMap.get(call);
        if (uiCall == null) {
            uiCall = createUiCall(call);
            mCallToCallInfoMap.put(call, uiCall);
            mIdToCallMap.append(uiCall.getId(), new Pair<>(uiCall, call));
        }
        return uiCall;
    }

    /**
     * Returns a list of existing {@code CallInfo}.
     */
    public List<UiCall> getCalls() {
        return new ArrayList<>(mCallToCallInfoMap.values());
    }

    /**
     * Clears a list.
     */
    public void clearCalls() {
        mCallToCallInfoMap.clear();
        mIdToCallMap.clear();
    }

    /**
     * Removes {@code CallInfo} by given key.
     */
    public void remove(UiCall uiCall) {
        int callId = uiCall.getId();
        C call = getCall(callId);
        if (mCallToCallInfoMap.containsKey(call)) {
            mCallToCallInfoMap.remove(call);
        }
        mIdToCallMap.remove(callId);
    }

    protected C getCall(int callId) {
        return mIdToCallMap.get(callId).second;
    }

    public UiCall getUiCall(int callId) {
        return mIdToCallMap.get(callId).first;
    }

    abstract protected UiCall createUiCall(C call);
}
