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
package com.android.car.dialer.telecom.embedded;

import com.android.car.dialer.telecom.UiCall;
import com.android.car.dialer.telecom.UiCallList;

import android.telecom.Call;
import android.telecom.DisconnectCause;
import android.telecom.GatewayInfo;
import android.util.Log;

/**
 * Represents a list of {@link UiCall} for underlying {@link android.telecom.Call}.
 */
public class TelecomUiCallList extends UiCallList<Call> {
    private final static String TAG = "Em.UiCallList";

    private volatile static int nextCarPhoneCallId = 0;

    private int getNewCarPhoneCallId() {
        return nextCarPhoneCallId++;
    }

    @Override
    protected UiCall createUiCall(Call telecomCall) {
        int id = getNewCarPhoneCallId();

        UiCall uiCall = new UiCall(id);
        updateCallContainerFromTelecom(uiCall, telecomCall);
        return uiCall;
    }

    static void updateCallContainerFromTelecom(UiCall uiCall, Call telecomCall) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "updateCallContainerFromTelecom: call: " + uiCall + ", telecomCall: "
                    + telecomCall);
        }

        uiCall.setState(telecomCall.getState());
        uiCall.setHasChildren(!telecomCall.getChildren().isEmpty());
        uiCall.setHasParent(telecomCall.getParent() != null);

        Call.Details details = telecomCall.getDetails();
        if (details != null) {
            uiCall.setConnectTimeMillis(details.getConnectTimeMillis());

            DisconnectCause cause = details.getDisconnectCause();
            CharSequence causeLabel = cause == null ? null : cause.getLabel();
            uiCall.setDisconnectClause(causeLabel == null ? null : causeLabel.toString());

            GatewayInfo gatewayInfo = details.getGatewayInfo();
            uiCall.setGatewayInfoOriginalAddress(
                    gatewayInfo == null ? null : gatewayInfo.getOriginalAddress());

            String number = "";
            if (gatewayInfo != null) {
                number = gatewayInfo.getOriginalAddress().getSchemeSpecificPart();
            } else if (details.getHandle() != null) {
                number = details.getHandle().getSchemeSpecificPart();
            }
            uiCall.setNumber(number);
        }
    }

    public Call getTelecomCall(UiCall uiCall) {
        return uiCall != null ? getCall(uiCall.getId()) : null;
    }
}
