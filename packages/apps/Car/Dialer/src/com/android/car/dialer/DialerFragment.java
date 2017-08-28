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

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.support.car.ui.FabDrawable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.android.car.dialer.telecom.TelecomUtils;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.dialer.telecom.UiCallManager.CallListener;

/**
 * Fragment that controls the dialpad.
 */
public class DialerFragment extends Fragment {
    private static final String TAG = "Em.DialerFragment";
    private static final String INPUT_ACTIVE_KEY = "INPUT_ACTIVE_KEY";
    private static final int TONE_RELATIVE_VOLUME = 80;
    private static final int ABANDON_AUDIO_FOCUS_DELAY_MS = 250;
    private static final int MAX_DIAL_NUMBER = 20;
    private static final int NO_TONE = -1;

    private static final ArrayMap<Integer, Integer> mToneMap = new ArrayMap<>();

    static {
        mToneMap.put(KeyEvent.KEYCODE_1, ToneGenerator.TONE_DTMF_1);
        mToneMap.put(KeyEvent.KEYCODE_2, ToneGenerator.TONE_DTMF_2);
        mToneMap.put(KeyEvent.KEYCODE_3, ToneGenerator.TONE_DTMF_3);
        mToneMap.put(KeyEvent.KEYCODE_4, ToneGenerator.TONE_DTMF_4);
        mToneMap.put(KeyEvent.KEYCODE_5, ToneGenerator.TONE_DTMF_5);
        mToneMap.put(KeyEvent.KEYCODE_6, ToneGenerator.TONE_DTMF_6);
        mToneMap.put(KeyEvent.KEYCODE_7, ToneGenerator.TONE_DTMF_7);
        mToneMap.put(KeyEvent.KEYCODE_8, ToneGenerator.TONE_DTMF_8);
        mToneMap.put(KeyEvent.KEYCODE_9, ToneGenerator.TONE_DTMF_9);
        mToneMap.put(KeyEvent.KEYCODE_0, ToneGenerator.TONE_DTMF_0);
        mToneMap.put(KeyEvent.KEYCODE_STAR, ToneGenerator.TONE_DTMF_S);
        mToneMap.put(KeyEvent.KEYCODE_POUND, ToneGenerator.TONE_DTMF_P);
    }

    private Context mContext;
    private final StringBuffer mNumber = new StringBuffer(MAX_DIAL_NUMBER);
    private AudioManager mAudioManager;
    private ToneGenerator mToneGenerator;
    private final Handler mHandler = new Handler();
    private final Object mToneGeneratorLock = new Object();
    private TextView mNumberView;
    private boolean mShowInput = true;
    private Runnable mPendingRunnable;

    private DialerBackButtonListener mBackListener;

    /**
     * Interface for a class that will be notified when the back button of the dialer has been
     * clicked.
     */
    public interface DialerBackButtonListener {
        /**
         * Called when the back button has been clicked on the dialer. This action should dismiss
         * the dialer fragment.
         */
        void onDialerBackClick();
    }

    /**
     * Sets the given {@link DialerBackButtonListener} to be notified whenever the back button
     * on the dialer has been clicked. Passing {@code null} to this method will clear all listeners.
     */
    public void setDialerBackButtonListener(DialerBackButtonListener listener) {
        mBackListener = listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAudioManager =
                (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        if (savedInstanceState != null && savedInstanceState.containsKey(INPUT_ACTIVE_KEY)) {
            mShowInput = savedInstanceState.getBoolean(INPUT_ACTIVE_KEY);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onCreateView");
        }

        mContext = getContext();
        View view = inflater.inflate(R.layout.dialer_fragment, container, false);

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onCreateView: inflated successfully");
        }

        view.findViewById(R.id.exit_dialer_button).setOnClickListener((unusedView) -> {
            if (mBackListener != null) {
                mBackListener.onDialerBackClick();
            }
        });

        mNumberView = (TextView) view.findViewById(R.id.number);
        final boolean hasTouch = getResources().getBoolean(R.bool.has_touch);

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "hasTouch: " + hasTouch + ", mShowInput: " + mShowInput);
        }

        if (hasTouch) {
            // Only show the delete and call buttons in touch mode.
            // Buttons are in rotary input itself.
            View callButton = view.findViewById(R.id.call);
            FabDrawable answerCallDrawable = new FabDrawable(mContext);
            answerCallDrawable.setFabAndStrokeColor(getResources().getColor(R.color.phone_call));
            callButton.setBackground(answerCallDrawable);
            callButton.setVisibility(View.VISIBLE);
            callButton.setOnClickListener((unusedView) -> {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Call button clicked, placing a call: " + mNumber.toString());
                }

                if (!TextUtils.isEmpty(mNumber.toString())) {
                    getUiCallManager().safePlaceCall(mNumber.toString(), false);
                }
            });
            View deleteButton = view.findViewById(R.id.delete);
            deleteButton.setVisibility(View.VISIBLE);
            deleteButton.setOnClickListener((unusedView) -> {
                if (mNumber.length() != 0) {
                    mNumber.deleteCharAt(mNumber.length() - 1);
                    mNumberView.setText(getFormattedNumber(mNumber.toString()));
                }
            });
        }

        setupKeypad(view);

        return view;
    }

    /**
     * The default click listener for all dialpad buttons. This click listener will append its
     * associated value to {@link #mNumber}.
     */
    private class DialpadClickListener implements View.OnClickListener {
        private String mValue;

        public DialpadClickListener(String value) {
            mValue = value;
        }

        @Override
        public void onClick(View v) {
            mNumber.append(mValue);
            mNumberView.setText(getFormattedNumber(mNumber.toString()));
        }
    };

    /**
     * Sets up the click listeners for all the dialpad buttons.
     */
    private void setupKeypad(View parent) {
        parent.findViewById(R.id.zero).setOnClickListener(new DialpadClickListener("0"));
        parent.findViewById(R.id.one).setOnClickListener(new DialpadClickListener("1"));
        parent.findViewById(R.id.two).setOnClickListener(new DialpadClickListener("2"));
        parent.findViewById(R.id.three).setOnClickListener(new DialpadClickListener("3"));
        parent.findViewById(R.id.four).setOnClickListener(new DialpadClickListener("4"));
        parent.findViewById(R.id.five).setOnClickListener(new DialpadClickListener("5"));
        parent.findViewById(R.id.six).setOnClickListener(new DialpadClickListener("6"));
        parent.findViewById(R.id.seven).setOnClickListener(new DialpadClickListener("7"));
        parent.findViewById(R.id.eight).setOnClickListener(new DialpadClickListener("8"));
        parent.findViewById(R.id.nine).setOnClickListener(new DialpadClickListener("9"));
        parent.findViewById(R.id.star).setOnClickListener(new DialpadClickListener("*"));
        parent.findViewById(R.id.pound).setOnClickListener(new DialpadClickListener("#"));
    }

    @Override
    public void onResume() {
        super.onResume();
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                mToneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, TONE_RELATIVE_VOLUME);
            }
        }
        UiCallManager.getInstance(mContext).addListener(mCallListener);

        if (mPendingRunnable != null) {
            mPendingRunnable.run();
            mPendingRunnable = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        UiCallManager.getInstance(mContext).removeListener(mCallListener);
        stopTone();
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator != null) {
                mToneGenerator.release();
                mToneGenerator = null;
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mContext = null;
        mNumberView = null;
    }

    public void setDialNumber(final String number) {
        if (TextUtils.isEmpty(number)) {
            return;
        }

        if (mContext != null && mNumberView != null) {
            setDialNumberInternal(number);
        } else {
            mPendingRunnable = () -> setDialNumberInternal(number);
        }
    }

    private void setDialNumberInternal(final String number) {
        // Clear existing content in mNumber.
        mNumber.setLength(0);
        mNumber.append(number);
        mNumberView.setText(getFormattedNumber(mNumber.toString()));
    }

    private void stopTone() {
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                Log.w(TAG, "stopTone: mToneGenerator == null");
                return;
            }
            mToneGenerator.stopTone();
            mHandler.postDelayed(mDelayedAbandonAudioFocusRunnable, ABANDON_AUDIO_FOCUS_DELAY_MS);
        }
    }

    private final Runnable mDelayedAbandonAudioFocusRunnable = new Runnable() {
        @Override
        public void run() {
            mAudioManager.abandonAudioFocus(null);
        }
    };

    private UiCallManager getUiCallManager() {
        return UiCallManager.getInstance(mContext);
    }

    private String getFormattedNumber(String number) {
        return TelecomUtils.getFormattedNumber(mContext, number);
    }

    private final CallListener mCallListener = new CallListener() {
        @Override
        public void dispatchPhoneKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_CALL &&
                    event.getAction() == KeyEvent.ACTION_UP &&
                    !TextUtils.isEmpty(mNumber.toString())) {
                getUiCallManager().safePlaceCall(mNumber.toString(), false);
            }
        }
    };
}
