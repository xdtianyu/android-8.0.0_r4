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
import com.android.car.dialer.telecom.UiCallManager;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.Call.Details;
import android.telecom.CallAudioState;
import android.telecom.InCallService.VideoCall;
import android.telecom.TelecomManager;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An implementation of {@link UiCallManager} that uses {@code android.telecom.*} stack.
 */
public class TelecomUiCallManager extends UiCallManager {

    private static final String TAG = "Em.TelecomMgrImpl";

    private TelecomManager mTelecomManager;
    private InCallServiceImpl mInCallService;
    private TelecomUiCallList mCallList = new TelecomUiCallList();

    private List<CallListener> mCallListeners = new CopyOnWriteArrayList<>();

    @Override
    protected void setUp(Context context) {
        super.setUp(context);
        mTelecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        Intent intent = new Intent(context, InCallServiceImpl.class);
        intent.setAction(InCallServiceImpl.ACTION_LOCAL_BIND);
        context.bindService(intent, mInCallServiceConnection, Context.BIND_AUTO_CREATE);
    }

    public void tearDown() {
        if (mInCallService != null) {
            mContext.unbindService(mInCallServiceConnection);
            mInCallService = null;
        }
        mCallList.clearCalls();
    }

    @Override
    public void addListener(CallListener listener) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "addListener: " + listener);
        }
        mCallListeners.add(listener);
    }

    @Override
    public void removeListener(CallListener listener) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "removeListener: " + listener);
        }
        mCallListeners.remove(listener);
    }

    @Override
    public void placeCall(String number) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "placeCall: " + number);
        }
        Uri uri = Uri.fromParts("tel", number, null);
        Log.d(TAG, "android.telecom.TelecomManager#placeCall: " + uri);
        mTelecomManager.placeCall(uri, null);
    }

    @Override
    public void answerCall(UiCall uiCall) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "answerCall: " + uiCall);
        }

        Call telecomCall = mCallList.getTelecomCall(uiCall);
        if (telecomCall != null) {
            telecomCall.answer(0);
        }
    }

    @Override
    public void rejectCall(UiCall uiCall, boolean rejectWithMessage, String textMessage) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "rejectCall: " + uiCall + ", rejectWithMessage: " + rejectWithMessage
                    + "textMessage: " + textMessage);
        }

        Call telecomCall = mCallList.getTelecomCall(uiCall);
        if (telecomCall != null) {
            telecomCall.reject(rejectWithMessage, textMessage);
        }
    }

    @Override
    public void disconnectCall(UiCall uiCall) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "disconnectCall: " + uiCall);
        }

        Call telecomCall = mCallList.getTelecomCall(uiCall);
        if (telecomCall != null) {
            telecomCall.disconnect();
        }
    }

    @Override
    public List<UiCall> getCalls() {
        return mCallList.getCalls();
    }

    @Override
    public boolean getMuted() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getMuted");
        }
        if (mInCallService == null) {
            return false;
        }
        CallAudioState audioState = mInCallService.getCallAudioState();
        return audioState != null && audioState.isMuted();
    }

    @Override
    public void setMuted(boolean muted) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "setMuted: " + muted);
        }
        if (mInCallService == null) {
            return;
        }
        mInCallService.setMuted(muted);
    }

    @Override
    public int getSupportedAudioRouteMask() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getSupportedAudioRouteMask");
        }

        CallAudioState audioState = getCallAudioStateOrNull();
        return audioState != null ? audioState.getSupportedRouteMask() : 0;
    }

    @Override
    public int getAudioRoute() {
        CallAudioState audioState = getCallAudioStateOrNull();
        int audioRoute = audioState != null ? audioState.getRoute() : 0;
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getAudioRoute " + audioRoute);
        }
        return audioRoute;
    }

    @Override
    public void setAudioRoute(int audioRoute) {
        // In case of embedded where the CarKitt is always connected to one kind of speaker we
        // should simply ignore any setAudioRoute requests.
        Log.w(TAG, "setAudioRoute ignoring request " + audioRoute);
    }

    @Override
    public void holdCall(UiCall uiCall) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "holdCall: " + uiCall);
        }

        Call telecomCall = mCallList.getTelecomCall(uiCall);
        if (telecomCall != null) {
            telecomCall.hold();
        }
    }

    @Override
    public void unholdCall(UiCall uiCall) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "unholdCall: " + uiCall);
        }

        Call telecomCall = mCallList.getTelecomCall(uiCall);
        if (telecomCall != null) {
            telecomCall.unhold();
        }
    }

    @Override
    public void playDtmfTone(UiCall uiCall, char digit) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "playDtmfTone: call: " + uiCall + ", digit: " + digit);
        }

        Call telecomCall = mCallList.getTelecomCall(uiCall);
        if (telecomCall != null) {
            telecomCall.playDtmfTone(digit);
        }
    }

    @Override
    public void stopDtmfTone(UiCall uiCall) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "stopDtmfTone: call: " + uiCall);
        }

        Call telecomCall = mCallList.getTelecomCall(uiCall);
        if (telecomCall != null) {
            telecomCall.stopDtmfTone();
        }
    }

    @Override
    public void postDialContinue(UiCall uiCall, boolean proceed) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "postDialContinue: call: " + uiCall + ", proceed: " + proceed);
        }

        Call telecomCall = mCallList.getTelecomCall(uiCall);
        if (telecomCall != null) {
            telecomCall.postDialContinue(proceed);
        }
    }

    @Override
    public void conference(UiCall uiCall, UiCall otherUiCall) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "conference: call: " + uiCall + ", otherCall: " + otherUiCall);
        }

        Call telecomCall = mCallList.getTelecomCall(uiCall);
        Call otherTelecomCall = mCallList.getTelecomCall(otherUiCall);
        if (telecomCall != null) {
            telecomCall.conference(otherTelecomCall);
        }
    }

    @Override
    public void splitFromConference(UiCall uiCall) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "splitFromConference: call: " + uiCall);
        }

        Call telecomCall = mCallList.getTelecomCall(uiCall);
        if (telecomCall != null) {
            telecomCall.splitFromConference();
        }
    }

    private UiCall doTelecomCallAdded(final Call telecomCall) {
        Log.d(TAG, "doTelecomCallAdded: " + telecomCall);

        UiCall uiCall = getOrCreateCallContainer(telecomCall);
        telecomCall.registerCallback(new TelecomCallListener(this, uiCall));
        for (CallListener listener : mCallListeners) {
            listener.onCallAdded(uiCall);
        }
        Log.d(TAG, "Call backs registered");

        if (telecomCall.getState() == Call.STATE_SELECT_PHONE_ACCOUNT) {
            // TODO(b/26189994): need to show Phone Account picker to let user choose a phone
            // account. It should be an account from TelecomManager#getCallCapablePhoneAccounts
            // list.
            Log.w(TAG, "Need to select phone account for the given call: " + telecomCall + ", "
                    + "but this feature is not implemented yet.");
            telecomCall.disconnect();
        }
        return uiCall;
    }

    private void doTelecomCallRemoved(Call telecomCall) {
        UiCall uiCall = getOrCreateCallContainer(telecomCall);

        mCallList.remove(uiCall);

        for (CallListener listener : mCallListeners) {
            listener.onCallRemoved(uiCall);
        }
    }

    private void doCallAudioStateChanged(CallAudioState audioState) {
        for (CallListener listener : mCallListeners) {
            listener.onAudioStateChanged(audioState.isMuted(), audioState.getRoute(),
                    audioState.getSupportedRouteMask());
        }
    }

    private void onStateChanged(UiCall uiCall, int state) {
        for (CallListener listener : mCallListeners) {
            listener.onStateChanged(uiCall, state);
        }
    }

    private void onCallUpdated(UiCall uiCall) {
        for (CallListener listener : mCallListeners) {
            listener.onCallUpdated(uiCall);
        }
    }

    private static class TelecomCallListener extends Call.Callback {
        private final WeakReference<TelecomUiCallManager> mCarTelecomMangerRef;
        private final WeakReference<UiCall> mCallContainerRef;

        TelecomCallListener(TelecomUiCallManager carTelecomManager, UiCall uiCall) {
            mCarTelecomMangerRef = new WeakReference<>(carTelecomManager);
            mCallContainerRef = new WeakReference<>(uiCall);
        }

        @Override
        public void onStateChanged(Call telecomCall, int state) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onStateChanged: " + state);
            }
            TelecomUiCallManager manager = mCarTelecomMangerRef.get();
            UiCall call = mCallContainerRef.get();
            if (manager != null && call != null) {
                call.setState(state);
                manager.onStateChanged(call, state);
            }
        }

        @Override
        public void onParentChanged(Call telecomCall, Call parent) {
            doCallUpdated(telecomCall);
        }

        @Override
        public void onCallDestroyed(Call telecomCall) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCallDestroyed");
            }
        }

        @Override
        public void onDetailsChanged(Call telecomCall, Details details) {
            doCallUpdated(telecomCall);
        }

        @Override
        public void onVideoCallChanged(Call telecomCall, VideoCall videoCall) {
            doCallUpdated(telecomCall);
        }

        @Override
        public void onCannedTextResponsesLoaded(Call telecomCall,
                List<String> cannedTextResponses) {
            doCallUpdated(telecomCall);
        }

        @Override
        public void onChildrenChanged(Call telecomCall, List<Call> children) {
            doCallUpdated(telecomCall);
        }

        private void doCallUpdated(Call telecomCall) {
            TelecomUiCallManager manager = mCarTelecomMangerRef.get();
            UiCall uiCall = mCallContainerRef.get();
            if (manager != null && uiCall != null) {
                TelecomUiCallList.updateCallContainerFromTelecom(uiCall, telecomCall);
                manager.onCallUpdated(uiCall);
            }
        }
    }

    private ServiceConnection mInCallServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onServiceConnected: " + name + ", service: " + binder);
            }
            mInCallService = ((InCallServiceImpl.LocalBinder) binder).getService();
            mInCallService.registerCallback(mInCallServiceCallback);

            // The InCallServiceImpl could be bound when we already have some active calls, let's
            // notify UI about these calls.
            for (Call telecomCall : mInCallService.getCalls()) {
                UiCall uiCall = doTelecomCallAdded(telecomCall);
                onStateChanged(uiCall, uiCall.getState());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onServiceDisconnected: " + name);
            }
            mInCallService.unregisterCallback(mInCallServiceCallback);
        }

        private InCallServiceImpl.Callback mInCallServiceCallback =
                new InCallServiceImpl.Callback() {

            @Override
            public void onTelecomCallAdded(Call telecomCall) {
                doTelecomCallAdded(telecomCall);
            }

            @Override
            public void onTelecomCallRemoved(Call telecomCall) {
                doTelecomCallRemoved(telecomCall);
            }

            @Override
            public void onCallAudioStateChanged(CallAudioState audioState) {
                doCallAudioStateChanged(audioState);
            }
        };
    };

    private UiCall getOrCreateCallContainer(Call telecomCall) {
        synchronized (mCallList) {
            return mCallList.getOrCreate(telecomCall);
        }
    }

    private CallAudioState getCallAudioStateOrNull() {
        return mInCallService != null ? mInCallService.getCallAudioState() : null;
    }
}
