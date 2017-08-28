/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.car.messenger;

import android.content.Context;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Component that wraps platform TTS engine and supports queued playout.
 * <p>
 * It takes care of initializing the TTS engine. TTS requests made are queued up and played when the
 * engine is setup. It only supports one queued requests; any new requests will cause the existing
 * one to be dropped. Similarly, if a new one is queued while an existing message is already playing
 * the existing one will be stopped/interrupted and the new one will start playing.
 */
class TTSHelper {
    interface Listener {
        // Called when playout is about to start.
        void onTTSStarted();

        // The following two are terminal callbacks and no further callbacks should be expected.
        // Called when playout finishes or playout is cancelled/never started because another TTS
        // request was made.
        void onTTSStopped();
        // Called when there's an internal error.
        void onTTSError();
    }

    private static final String TAG = "Messenger.TTSHelper";
    private static final boolean DBG = MessengerService.DBG;

    private final Handler mHandler = new Handler();
    private final TextToSpeech mTextToSpeech;
    private int mInitStatus;
    private SpeechRequest mPendingRequest;
    private final Map<String, Listener> mListeners = new HashMap<>();

    TTSHelper(Context context) {
        // OnInitListener will only set to SUCCESS/ERROR. So we initialize to STOPPED.
        mInitStatus = TextToSpeech.STOPPED;
        // TODO(sriniv): Init this only when needed and shutdown to free resources.
        mTextToSpeech = new TextToSpeech(context, this::handleInitCompleted);
        mTextToSpeech.setOnUtteranceProgressListener(mProgressListener);
    }

    private void handleInitCompleted(int initStatus) {
        if (DBG) {
            Log.d(TAG, "init completed: " + initStatus);
        }
        mInitStatus = initStatus;
        if (mPendingRequest != null) {
            playInternal(mPendingRequest.mTextToSpeak, mPendingRequest.mListener);
            mPendingRequest = null;
        }
    }

    void requestPlay(CharSequence textToSpeak, Listener listener) {
        // Check if its still initializing.
        if (mInitStatus == TextToSpeech.STOPPED) {
            // Squash any already queued request.
            if (mPendingRequest != null) {
                mPendingRequest.mListener.onTTSStopped();
            }
            mPendingRequest = new SpeechRequest(textToSpeak, listener);
        } else {
            playInternal(textToSpeak, listener);
        }
    }

    void requestStop() {
        mTextToSpeech.stop();
    }

    private void playInternal(CharSequence textToSpeak, Listener listener) {
        if (mInitStatus == TextToSpeech.ERROR) {
            Log.e(TAG, "TTS setup failed!");
            mHandler.post(listener::onTTSError);
            return;
        }

        String id = Integer.toString(listener.hashCode());
        if (DBG) {
            Log.d(TAG, String.format("Queueing text in TTS: [%s], id=%s", textToSpeak, id));
        }
        if (mTextToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, id)
                != TextToSpeech.SUCCESS) {
            Log.e(TAG, "Queuing text failed!");
            mHandler.post(listener::onTTSError);
            return;
        }
        mListeners.put(id, listener);
    }

    void cleanup() {
        mTextToSpeech.stop();
        mTextToSpeech.shutdown();
    }

    // The TTS engine will invoke onStart and then invoke either onDone, onStop or onError.
    // Since these callbacks can come on other threads, we push updates back on to the TTSHelper's
    // Handler.
    private final UtteranceProgressListener mProgressListener = new UtteranceProgressListener() {
        private void safeInvokeAsync(String id, boolean cleanup,
                Consumer<Listener> callbackCaller) {
            mHandler.post(() -> {
                Listener listener = mListeners.get(id);
                if (listener == null) {
                    Log.e(TAG, "No listener found for: " + id);
                    return;
                }
                callbackCaller.accept(listener);
                if (cleanup) {
                    mListeners.remove(id);
                }
            });
        }

        @Override
        public void onStart(String id) {
            if (DBG) {
                Log.d(TAG, "TTS engine onStart: " + id);
            }
            safeInvokeAsync(id, false, Listener::onTTSStarted);
        }

        @Override
        public void onDone(String id) {
            if (DBG) {
                Log.d(TAG, "TTS engine onDone: " + id);
            }
            safeInvokeAsync(id, true, Listener::onTTSStopped);
        }

        @Override
        public void onStop(String id, boolean interrupted) {
            if (DBG) {
                Log.d(TAG, "TTS engine onStop: " + id);
            }
            safeInvokeAsync(id, true, Listener::onTTSStopped);
        }

        @Override
        public void onError(String id) {
            if (DBG) {
                Log.d(TAG, "TTS engine onError: " + id);
            }
            safeInvokeAsync(id, true, Listener::onTTSError);
        }
   };

    private static class SpeechRequest {
        final CharSequence mTextToSpeak;
        final Listener mListener;

        public SpeechRequest(CharSequence textToSpeak, Listener listener) {
            mTextToSpeak = textToSpeak;
            mListener = listener;
        }
    };
}
