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

import android.content.Context;
import android.database.Cursor;
import android.provider.CallLog;
import android.telecom.Call;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import com.android.car.dialer.ClassFactory;
import com.android.car.dialer.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The entry point for all interactions between UI and telecom.
 */
public abstract class UiCallManager {
    private static String TAG = "Em.TelecomMgr";

    private static Object sInstanceLock = new Object();
    private static UiCallManager sInstance;

    private long mLastPlacedCallTimeMs = 0;

    // Rate limit how often you can place outgoing calls.
    private static final long MIN_TIME_BETWEEN_CALLS_MS = 3000;

    private static final List<Integer> sCallStateRank = new ArrayList<>();

    protected Context mContext;

    protected TelephonyManager mTelephonyManager;

    static {
        // States should be added from lowest rank to highest
        sCallStateRank.add(Call.STATE_DISCONNECTED);
        sCallStateRank.add(Call.STATE_DISCONNECTING);
        sCallStateRank.add(Call.STATE_NEW);
        sCallStateRank.add(Call.STATE_CONNECTING);
        sCallStateRank.add(Call.STATE_SELECT_PHONE_ACCOUNT);
        sCallStateRank.add(Call.STATE_HOLDING);
        sCallStateRank.add(Call.STATE_ACTIVE);
        sCallStateRank.add(Call.STATE_DIALING);
        sCallStateRank.add(Call.STATE_RINGING);
    }

    public static UiCallManager getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Creating an instance of CarTelecomManager");
                }
                sInstance = ClassFactory.getFactory().createCarTelecomManager();
                sInstance.setUp(context.getApplicationContext());
            }
        }
        return sInstance;
    }

    protected UiCallManager() {}

    protected void setUp(Context context) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "SetUp");
        }

        mContext = context;
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
    }

    public abstract void tearDown();

    public abstract void addListener(CallListener listener);

    public abstract void removeListener(CallListener listener);

    protected abstract void placeCall(String number);

    public abstract void answerCall(UiCall call);

    public abstract void rejectCall(UiCall call, boolean rejectWithMessage, String textMessage);

    public abstract void disconnectCall(UiCall call);

    public abstract List<UiCall> getCalls();

    public abstract boolean getMuted();

    public abstract void setMuted(boolean muted);

    public abstract int getSupportedAudioRouteMask();

    public abstract int getAudioRoute();

    public abstract void setAudioRoute(int audioRoute);

    public abstract void holdCall(UiCall call);

    public abstract void unholdCall(UiCall call);

    public abstract void playDtmfTone(UiCall call, char digit);

    public abstract void stopDtmfTone(UiCall call);

    public abstract void postDialContinue(UiCall call, boolean proceed);

    public abstract void conference(UiCall call, UiCall otherCall);

    public abstract void splitFromConference(UiCall call);

    public static class CallListener {
        @SuppressWarnings("unused")
        public void dispatchPhoneKeyEvent(KeyEvent event) {}
        @SuppressWarnings("unused")
        public void onAudioStateChanged(boolean isMuted, int route, int supportedRouteMask) {}
        @SuppressWarnings("unused")
        public void onCallAdded(UiCall call) {}
        @SuppressWarnings("unused")
        public void onStateChanged(UiCall call, int state) {}
        @SuppressWarnings("unused")
        public void onCallUpdated(UiCall call) {}
        @SuppressWarnings("unused")
        public void onCallRemoved(UiCall call) {}
    }

    /** Returns a first call that matches at least one provided call state */
    public UiCall getCallWithState(int... callStates) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getCallWithState: " + callStates);
        }
        for (UiCall call : getCalls()) {
            for (int callState : callStates) {
                if (call.getState() == callState) {
                    return call;
                }
            }
        }
        return null;
    }

    public UiCall getPrimaryCall() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getPrimaryCall");
        }
        List<UiCall> calls = getCalls();
        if (calls.isEmpty()) {
            return null;
        }

        Collections.sort(calls, getCallComparator());
        UiCall uiCall = calls.get(0);
        if (uiCall.hasParent()) {
            return null;
        }
        return uiCall;
    }

    public UiCall getSecondaryCall() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getSecondaryCall");
        }
        List<UiCall> calls = getCalls();
        if (calls.size() < 2) {
            return null;
        }

        Collections.sort(calls, getCallComparator());
        UiCall uiCall = calls.get(1);
        if (uiCall.hasParent()) {
            return null;
        }
        return uiCall;
    }

    public static final int CAN_PLACE_CALL_RESULT_OK = 0;
    public static final int CAN_PLACE_CALL_RESULT_NETWORK_UNAVAILABLE = 1;
    public static final int CAN_PLACE_CALL_RESULT_HFP_UNAVAILABLE = 2;
    public static final int CAN_PLACE_CALL_RESULT_AIRPLANE_MODE = 3;

    public int getCanPlaceCallStatus(String number, boolean bluetoothRequired) {
        // TODO(b/26191392): figure out the logic for projected and embedded modes
        return CAN_PLACE_CALL_RESULT_OK;
    }

    public String getFailToPlaceCallMessage(int canPlaceCallResult) {
        switch (canPlaceCallResult) {
            case CAN_PLACE_CALL_RESULT_OK:
                return "";
            case CAN_PLACE_CALL_RESULT_HFP_UNAVAILABLE:
                return mContext.getString(R.string.error_no_hfp);
            case CAN_PLACE_CALL_RESULT_AIRPLANE_MODE:
                return mContext.getString(R.string.error_airplane_mode);
            case CAN_PLACE_CALL_RESULT_NETWORK_UNAVAILABLE:
            default:
                return mContext.getString(R.string.error_network_not_available);
        }
    }

    /** Places call only if there's no outgoing call right now */
    public void safePlaceCall(String number, boolean bluetoothRequired) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "safePlaceCall: " + number);
        }

        int placeCallStatus = getCanPlaceCallStatus(number, bluetoothRequired);
        if (placeCallStatus != CAN_PLACE_CALL_RESULT_OK) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Unable to place a call: " + placeCallStatus);
            }
            return;
        }

        UiCall outgoingCall = getCallWithState(
                Call.STATE_CONNECTING, Call.STATE_NEW, Call.STATE_DIALING);
        if (outgoingCall == null) {
            long now = Calendar.getInstance().getTimeInMillis();
            if (now - mLastPlacedCallTimeMs > MIN_TIME_BETWEEN_CALLS_MS) {
                placeCall(number);
                mLastPlacedCallTimeMs = now;
            } else {
                if (Log.isLoggable(TAG, Log.INFO)) {
                    Log.i(TAG, "You have to wait " + MIN_TIME_BETWEEN_CALLS_MS
                            + "ms between making calls");
                }
            }
        }
    }

    public void callVoicemail() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "callVoicemail");
        }

        String voicemailNumber = TelecomUtils.getVoicemailNumber(mContext);
        if (TextUtils.isEmpty(voicemailNumber)) {
            Log.w(TAG, "Unable to get voicemail number.");
            return;
        }
        safePlaceCall(voicemailNumber, false);
    }

    /**
     * Returns the call types for the given number of items in the cursor.
     * <p/>
     * It uses the next {@code count} rows in the cursor to extract the types.
     * <p/>
     * Its position in the cursor is unchanged by this function.
     */
    public int[] getCallTypes(Cursor cursor, int count) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getCallTypes: cursor: " + cursor + ", count: " + count);
        }

        int position = cursor.getPosition();
        int[] callTypes = new int[count];
        String voicemailNumber = mTelephonyManager.getVoiceMailNumber();
        int column;
        for (int index = 0; index < count; ++index) {
            column = cursor.getColumnIndex(CallLog.Calls.NUMBER);
            String phoneNumber = cursor.getString(column);
            if (phoneNumber != null && phoneNumber.equals(voicemailNumber)) {
                callTypes[index] = PhoneLoader.VOICEMAIL_TYPE;
            } else {
                column = cursor.getColumnIndex(CallLog.Calls.TYPE);
                callTypes[index] = cursor.getInt(column);
            }
            cursor.moveToNext();
        }
        cursor.moveToPosition(position);
        return callTypes;
    }

    private static Comparator<UiCall> getCallComparator() {
        return new Comparator<UiCall>() {
            @Override
            public int compare(UiCall call, UiCall otherCall) {
                if (call.hasParent() && !otherCall.hasParent()) {
                    return 1;
                } else if (!call.hasParent() && otherCall.hasParent()) {
                    return -1;
                }
                int carCallRank = sCallStateRank.indexOf(call.getState());
                int otherCarCallRank = sCallStateRank.indexOf(otherCall.getState());

                return otherCarCallRank - carCallRank;
            }
        };
    }
}
