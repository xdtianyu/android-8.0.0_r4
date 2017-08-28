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
package com.android.car.dialer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.Call;

import com.android.car.dialer.telecom.UiCall;
import com.android.car.dialer.telecom.UiCallManager;

/**
 * Performs actions on ongoing calls.
 */
public class CallActionsReceiver extends BroadcastReceiver {
    /**
     * Answer the incoming call. No-op if there isn't one.
     */
    public static final String ACTION_ANSWER_INCOMING_CALL = "action_answer_incoming_call";
    /**
     * Reject the incoming call. No-op if there isn't one.
     */
    public static final String ACTION_REJECT_INCOMING_CALL = "action_reject_incoming_call";
    /**
     * Call a phone number. This will take advantage of any logic in
     * {@link UiCallManager#safePlaceCall(String, boolean)}.
     * However, it will pass null in for CarBluetoothConnectionManager due to the transient
     * lifecycle of broadcast receivers.
     */
    public static final String ACTION_CALL_NUMBER = "action_call_number";
    /**
     * Extra to store the number to call.
     */
    public static final String EXTRA_PHONE_NUMBER = "extra_phone_number";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        }

        UiCallManager uiCallManager = UiCallManager.getInstance(context);

        UiCall call;
        switch(action) {
            case ACTION_ANSWER_INCOMING_CALL:
                call = uiCallManager.getCallWithState(Call.STATE_RINGING);
                if (call != null) {
                    uiCallManager.getInstance(context).answerCall(call);
                }
                break;
            case ACTION_REJECT_INCOMING_CALL:
                call = uiCallManager.getCallWithState(Call.STATE_RINGING);
                if (call != null) {
                    uiCallManager.getInstance(context).rejectCall(call, false, null);
                }
                break;
            case ACTION_CALL_NUMBER:
                uiCallManager.safePlaceCall(intent.getStringExtra(EXTRA_PHONE_NUMBER), false);
                break;
        }
    }
}
