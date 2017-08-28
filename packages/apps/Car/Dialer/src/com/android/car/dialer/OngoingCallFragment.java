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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.car.ui.CircleBitmapDrawable;
import android.support.car.ui.FabDrawable;
import android.support.v4.app.Fragment;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.car.dialer.bluetooth.UiBluetoothMonitor;
import com.android.car.dialer.telecom.TelecomUtils;
import com.android.car.dialer.telecom.UiCall;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.dialer.telecom.UiCallManager.CallListener;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class OngoingCallFragment extends Fragment {
    private static final String TAG = "Em.OngoingCall";
    private static final HashMap<Integer, Character> mDialpadButtonMap = new HashMap<>();

    static {
        mDialpadButtonMap.put(R.id.one, '1');
        mDialpadButtonMap.put(R.id.two, '2');
        mDialpadButtonMap.put(R.id.three, '3');
        mDialpadButtonMap.put(R.id.four, '4');
        mDialpadButtonMap.put(R.id.five, '5');
        mDialpadButtonMap.put(R.id.six, '6');
        mDialpadButtonMap.put(R.id.seven, '7');
        mDialpadButtonMap.put(R.id.eight, '8');
        mDialpadButtonMap.put(R.id.nine, '9');
        mDialpadButtonMap.put(R.id.zero, '0');
        mDialpadButtonMap.put(R.id.star, '*');
        mDialpadButtonMap.put(R.id.pound, '#');
    }

    private UiCall mPrimaryCall;
    private UiCall mSecondaryCall;
    private UiCall mLastRemovedCall;
    private UiCallManager mUiCallManager;
    private Handler mHandler;
    private View mRingingCallControls;
    private View mActiveCallControls;
    private ImageButton mEndCallButton;
    private ImageButton mUnholdCallButton;
    private ImageButton mMuteButton;
    private ImageButton mToggleDialpadButton;
    private ImageButton mSwapButton;
    private ImageButton mMergeButton;
    private ImageButton mAnswerCallButton;
    private ImageButton mRejectCallButton;
    private TextView mNameTextView;
    private TextView mSecondaryNameTextView;
    private TextView mStateTextView;
    private TextView mSecondaryStateTextView;
    private ImageView mLargeContactPhotoView;
    private ImageView mSmallContactPhotoView;
    private View mDialpadContainer;
    private View mSecondaryCallContainer;
    private View mSecondaryCallControls;
    private LinearLayout mRotaryDialpad;
    private List<View> mDialpadViews;
    private String mLoadedNumber;
    private CharSequence mCallInfoLabel;
    private boolean mIsHfpConnected;
    private UiBluetoothMonitor mUiBluetoothMonitor;

    private final Interpolator
            mAccelerateDecelerateInterpolator = new AccelerateDecelerateInterpolator();
    private final Interpolator mAccelerateInterpolator = new AccelerateInterpolator(10);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUiCallManager = UiCallManager.getInstance(getContext());
        mUiBluetoothMonitor = UiBluetoothMonitor.getInstance();
        mHandler = new Handler();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mUpdateDurationRunnable);
        mHandler.removeCallbacks(mStopDtmfToneRunnable);
        mHandler = null;
        mUiCallManager = null;
        mLoadedNumber = null;
        mUiBluetoothMonitor = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ongoing_call, container, false);
        mRingingCallControls = view.findViewById(R.id.ringing_call_controls);
        mActiveCallControls = view.findViewById(R.id.active_call_controls);
        mEndCallButton = (ImageButton) view.findViewById(R.id.end_call);
        mUnholdCallButton = (ImageButton) view.findViewById(R.id.unhold_call);
        mMuteButton = (ImageButton) view.findViewById(R.id.mute);
        mToggleDialpadButton = (ImageButton) view.findViewById(R.id.toggle_dialpad);
        mDialpadContainer = view.findViewById(R.id.dialpad_container);
        mNameTextView = (TextView) view.findViewById(R.id.name);
        mSecondaryNameTextView = (TextView) view.findViewById(R.id.name_secondary);
        mStateTextView = (TextView) view.findViewById(R.id.info);
        mSecondaryStateTextView = (TextView) view.findViewById(R.id.info_secondary);
        mLargeContactPhotoView = (ImageView) view.findViewById(R.id.large_contact_photo);
        mSmallContactPhotoView = (ImageView) view.findViewById(R.id.small_contact_photo);
        mSecondaryCallContainer = view.findViewById(R.id.secondary_call_container);
        mSecondaryCallControls = view.findViewById(R.id.secondary_call_controls);
        mRotaryDialpad = (LinearLayout) view.findViewById(R.id.rotary_dialpad);
        mSwapButton = (ImageButton) view.findViewById(R.id.swap);
        mMergeButton = (ImageButton) view.findViewById(R.id.merge);
        mAnswerCallButton = (ImageButton) view.findViewById(R.id.answer_call_button);
        mRejectCallButton = (ImageButton) view.findViewById(R.id.reject_call_button);

        boolean hasTouch = getResources().getBoolean(R.bool.has_touch);
        View dialPadContainer = hasTouch ? mDialpadContainer : mRotaryDialpad;
        mDialpadViews = Arrays.asList(
                dialPadContainer.findViewById(R.id.one),
                dialPadContainer.findViewById(R.id.two),
                dialPadContainer.findViewById(R.id.three),
                dialPadContainer.findViewById(R.id.four),
                dialPadContainer.findViewById(R.id.five),
                dialPadContainer.findViewById(R.id.six),
                dialPadContainer.findViewById(R.id.seven),
                dialPadContainer.findViewById(R.id.eight),
                dialPadContainer.findViewById(R.id.nine),
                dialPadContainer.findViewById(R.id.zero),
                dialPadContainer.findViewById(R.id.pound),
                dialPadContainer.findViewById(R.id.star)
        );
        if (hasTouch) {
            // In touch screen, we need to adjust the InCall card for the narrow screen to show the
            // full dial pad.
            for (View dialpadView : mDialpadViews) {
                dialpadView.setOnTouchListener(mDialpadTouchListener);
                dialpadView.setOnKeyListener(mDialpadKeyListener);
            }
        } else {
            for (View dialpadView : mDialpadViews) {
                dialpadView.setOnKeyListener(mDialpadKeyListener);
            }
            mToggleDialpadButton.setImageResource(R.drawable.ic_rotary_dialpad);
        }
        setDialPadFocusability(!hasTouch);
        setInCallControllerFocusability(!hasTouch);

        mAnswerCallButton.setOnClickListener((unusedView) -> {
            UiCall call = mUiCallManager.getCallWithState(Call.STATE_RINGING);
            if (call == null) {
                Log.w(TAG, "There is no incoming call to answer.");
                return;
            }
            mUiCallManager.answerCall(call);
        });
        Context context = getContext();
        FabDrawable answerCallDrawable = new FabDrawable(context);
        answerCallDrawable.setFabAndStrokeColor(getResources().getColor(R.color.phone_call));
        mAnswerCallButton.setBackground(answerCallDrawable);

        mRejectCallButton.setOnClickListener((unusedView) -> {
            UiCall call = mUiCallManager.getCallWithState(Call.STATE_RINGING);
            if (call == null) {
                Log.w(TAG, "There is no incoming call to reject.");
                return;
            }
            mUiCallManager.rejectCall(call, false, null);
        });

        mEndCallButton.setOnClickListener((unusedView) -> {
            UiCall call = mUiCallManager.getPrimaryCall();
            if (call == null) {
                Log.w(TAG, "There is no active call to end.");
                return;
            }
            mUiCallManager.disconnectCall(call);
        });
        FabDrawable endCallDrawable = new FabDrawable(context);
        endCallDrawable.setFabAndStrokeColor(getResources().getColor(R.color.phone_end_call));
        mEndCallButton.setBackground(endCallDrawable);

        mUnholdCallButton.setOnClickListener((unusedView) -> {
            UiCall call = mUiCallManager.getPrimaryCall();
            if (call == null) {
                Log.w(TAG, "There is no active call to unhold.");
                return;
            }
            mUiCallManager.unholdCall(call);
        });
        FabDrawable unholdCallDrawable = new FabDrawable(context);
        unholdCallDrawable.setFabAndStrokeColor(getResources().getColor(R.color.phone_call));
        mUnholdCallButton.setBackground(unholdCallDrawable);

        mMuteButton.setOnClickListener((unusedView) -> {
            if (mUiCallManager.getMuted()) {
                mUiCallManager.setMuted(false);
            } else {
                mUiCallManager.setMuted(true);
            }
        });

        mSwapButton.setOnClickListener((unusedView) -> {
            UiCall call = mUiCallManager.getPrimaryCall();
            if (call == null) {
                Log.w(TAG, "There is no active call to hold.");
                return;
            }
            if (call.getState() == Call.STATE_HOLDING) {
                mUiCallManager.unholdCall(call);
            } else {
                mUiCallManager.holdCall(call);
            }
        });

        mMergeButton.setOnClickListener((unusedView) -> {
            UiCall call = mUiCallManager.getPrimaryCall();
            UiCall secondarycall = mUiCallManager.getSecondaryCall();
            if (call == null || mSecondaryCall == null) {
                Log.w(TAG, "There aren't two call to merge.");
                return;
            }

            mUiCallManager.conference(call, secondarycall);
        });

        mToggleDialpadButton.setOnClickListener((unusedView) -> {
            if (mToggleDialpadButton.isActivated()) {
                closeDialpad();
            } else {
                openDialpad(true /*animate*/);
            }
        });

        mUiCallManager.addListener(mCallListener);

        // These must be called after the views are inflated because they have the side affect
        // of updating the ui.
        mUiBluetoothMonitor.addListener(mBluetoothListener);
        mBluetoothListener.onStateChanged(); // Trigger state change to set initial state.

        updateCalls();
        updateRotaryFocus();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mUiCallManager.removeListener(mCallListener);
        mUiBluetoothMonitor.removeListener(mBluetoothListener);
    }

    @Override
    public void onStart() {
        super.onStart();
        trySpeakerAudioRouteIfNecessary();
    }

    private void rebindViews() {
        mHandler.removeCallbacks(mUpdateDurationRunnable);

        // Toggle the visibility between the active call controls, ringing call controls,
        // and no controls.
        CharSequence disconnectCauseLabel = mLastRemovedCall == null ?
                null : mLastRemovedCall.getDisconnectClause();
        if (mPrimaryCall == null && !TextUtils.isEmpty(disconnectCauseLabel)) {
            closeDialpad();
            setStateText(disconnectCauseLabel);
            return;
        } else if (mPrimaryCall == null || mPrimaryCall.getState() == Call.STATE_DISCONNECTED) {
            closeDialpad();
            setStateText(getString(R.string.call_state_call_ended));
            mRingingCallControls.setVisibility(View.GONE);
            mActiveCallControls.setVisibility(View.GONE);
            return;
        } else if (mPrimaryCall.getState() == Call.STATE_RINGING) {
            mRingingCallControls.setVisibility(View.VISIBLE);
            mActiveCallControls.setVisibility(View.GONE);
        } else {
            mRingingCallControls.setVisibility(View.GONE);
            mActiveCallControls.setVisibility(View.VISIBLE);
        }

        // Show the primary contact photo in the large ImageView on the right if there is no
        // secondary call. Otherwise, show it in the small ImageView that is inside the card.
        Context context = getContext();
        final ContentResolver cr = context.getContentResolver();
        final String primaryNumber = mPrimaryCall.getNumber();
        // Don't reload the image if the number is the same.
        if ((primaryNumber != null && !primaryNumber.equals(mLoadedNumber))
                || (primaryNumber == null && mLoadedNumber != null)) {
            BitmapWorkerTask.BitmapRunnable runnable = new BitmapWorkerTask.BitmapRunnable() {
                @Override
                public void run() {
                    if (mBitmap != null) {
                        Resources r = mSmallContactPhotoView.getResources();
                        mSmallContactPhotoView.setImageDrawable(
                                new CircleBitmapDrawable(r, mBitmap));
                        mLargeContactPhotoView.setImageBitmap(mBitmap);
                        mLargeContactPhotoView.clearColorFilter();
                    } else {
                        mSmallContactPhotoView.setImageResource(R.drawable.logo_avatar);
                        mLargeContactPhotoView.setImageResource(R.drawable.ic_avatar_bg);
                    }

                    if (mSecondaryCall != null) {
                        BitmapWorkerTask.BitmapRunnable secondCallContactPhotoHandler =
                                new BitmapWorkerTask.BitmapRunnable() {
                                    @Override
                                    public void run() {
                                        if (mBitmap != null) {
                                            mLargeContactPhotoView.setImageBitmap(mBitmap);
                                        } else {
                                            mLargeContactPhotoView.setImageResource(
                                                    R.drawable.logo_avatar);
                                        }
                                    }
                                };

                        BitmapWorkerTask.loadBitmap(
                                cr, mLargeContactPhotoView, mSecondaryCall.getNumber(),
                                secondCallContactPhotoHandler);

                        int scrimColor = getResources().getColor(
                                R.color.phone_secondary_call_scrim);
                        mLargeContactPhotoView.setColorFilter(scrimColor);
                    }
                    mLoadedNumber = primaryNumber;
                }
            };
            BitmapWorkerTask.loadBitmap(cr, mLargeContactPhotoView, primaryNumber, runnable);
        }

        if (mSecondaryCall != null) {
            mSecondaryCallContainer.setVisibility(View.VISIBLE);
            if (mPrimaryCall.getState() == Call.STATE_ACTIVE
                    && mSecondaryCall.getState() == Call.STATE_HOLDING) {
                mSecondaryCallControls.setVisibility(View.VISIBLE);
            } else {
                mSecondaryCallControls.setVisibility(View.GONE);
            }
        } else {
            mSecondaryCallContainer.setVisibility(View.GONE);
            mSecondaryCallControls.setVisibility(View.GONE);
        }

        String displayName = TelecomUtils.getDisplayName(context, mPrimaryCall);
        mNameTextView.setText(displayName);
        mNameTextView.setVisibility(TextUtils.isEmpty(displayName) ? View.GONE : View.VISIBLE);

        if (mSecondaryCall != null) {
            mSecondaryNameTextView.setText(
                    TelecomUtils.getDisplayName(context, mSecondaryCall));
        }

        switch (mPrimaryCall.getState()) {
            case Call.STATE_NEW:
                // Since the content resolver call is only cached when a contact is found,
                // this should only be called once on a new call to avoid jank.
                // TODO: consider moving TelecomUtils.getTypeFromNumber into a CursorLoader
                String number = mPrimaryCall.getNumber();
                mCallInfoLabel = TelecomUtils.getTypeFromNumber(context, number);
            case Call.STATE_CONNECTING:
            case Call.STATE_DIALING:
            case Call.STATE_SELECT_PHONE_ACCOUNT:
            case Call.STATE_HOLDING:
            case Call.STATE_DISCONNECTED:
                mHandler.removeCallbacks(mUpdateDurationRunnable);
                String callInfoText = TelecomUtils.getCallInfoText(context,
                        mPrimaryCall, mCallInfoLabel);
                setStateText(callInfoText);
                break;
            case Call.STATE_ACTIVE:
                if (mIsHfpConnected) {
                    mHandler.post(mUpdateDurationRunnable);
                }
                break;
            case Call.STATE_RINGING:
                Log.w(TAG, "There should not be a ringing call in the ongoing call fragment.");
                break;
            default:
                Log.w(TAG, "Unhandled call state: " + mPrimaryCall.getState());
        }

        if (mSecondaryCall != null) {
            mSecondaryStateTextView.setText(
                    TelecomUtils.callStateToUiString(context, mSecondaryCall.getState()));
        }

        // If it is a voicemail call, open the dialpad (with no animation).
        if (primaryNumber != null && primaryNumber.equals(
                TelecomUtils.getVoicemailNumber(context))) {
            if (getResources().getBoolean(R.bool.has_touch)) {
                openDialpad(false /*animate*/);
                mToggleDialpadButton.setVisibility(View.GONE);
            } else {
                mToggleDialpadButton.setVisibility(View.VISIBLE);
                mToggleDialpadButton.requestFocus();
            }
        } else {
            mToggleDialpadButton.setVisibility(View.VISIBLE);
        }

        // Handle the holding case.
        if (mPrimaryCall.getState() == Call.STATE_HOLDING) {
            mEndCallButton.setVisibility(View.GONE);
            mUnholdCallButton.setVisibility(View.VISIBLE);
            mMuteButton.setVisibility(View.INVISIBLE);
            mToggleDialpadButton.setVisibility(View.INVISIBLE);
        } else {
            mEndCallButton.setVisibility(View.VISIBLE);
            mUnholdCallButton.setVisibility(View.GONE);
            mMuteButton.setVisibility(View.VISIBLE);
            mToggleDialpadButton.setVisibility(View.VISIBLE);
        }
    }

    private void setStateText(CharSequence stateText) {
        mStateTextView.setText(stateText);
        mStateTextView.setVisibility(TextUtils.isEmpty(stateText) ? View.GONE : View.VISIBLE);
    }

    private void updateCalls() {
        mPrimaryCall = mUiCallManager.getPrimaryCall();
        if (mPrimaryCall != null && mPrimaryCall.getState() == Call.STATE_RINGING) {
            // TODO: update when notifications will work
        }
        mSecondaryCall = mUiCallManager.getSecondaryCall();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Primary call: " + mPrimaryCall + "\tSecondary call:" + mSecondaryCall);
        }
        rebindViews();
    }

    /**
     * If the phone is using bluetooth:
     *     * Do nothing
     * If the phone is not using bluetooth:
     *     * If the phone supports bluetooth, use it.
     *     * If the phone doesn't support bluetooth and support speaker, use speaker
     *     * Otherwise, do nothing. Hopefully no phones won't have bt or speaker.
     */
    private void trySpeakerAudioRouteIfNecessary() {
        if (mUiCallManager == null) {
            return;
        }

        int supportedAudioRouteMask = mUiCallManager.getSupportedAudioRouteMask();
        boolean supportsBluetooth = (supportedAudioRouteMask & CallAudioState.ROUTE_BLUETOOTH) != 0;
        boolean supportsSpeaker = (supportedAudioRouteMask & CallAudioState.ROUTE_SPEAKER) != 0;
        boolean isUsingBluetooth =
                mUiCallManager.getAudioRoute() == CallAudioState.ROUTE_BLUETOOTH;

        if (supportsBluetooth && !isUsingBluetooth) {
            mUiCallManager.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
        } else if (!supportsBluetooth && supportsSpeaker) {
            mUiCallManager.setAudioRoute(CallAudioState.ROUTE_SPEAKER);
        }
    }

    private void openDialpad(boolean animate) {
        if (mToggleDialpadButton.isActivated()) {
            return;
        }
        mToggleDialpadButton.setActivated(true);
        if (getResources().getBoolean(R.bool.has_touch)) {
            // This array of of size 2 because getLocationOnScreen returns (x,y) coordinates.
            int[] location = new int[2];
            mToggleDialpadButton.getLocationOnScreen(location);

            // The dialpad should be aligned with the right edge of mToggleDialpadButton.
            int startingMargin = location[1] + mToggleDialpadButton.getWidth();

            ViewGroup.MarginLayoutParams layoutParams =
                    (ViewGroup.MarginLayoutParams) mDialpadContainer.getLayoutParams();

            if (layoutParams.getMarginStart() != startingMargin) {
                layoutParams.setMarginStart(startingMargin);
                mDialpadContainer.setLayoutParams(layoutParams);
            }

            Animation anim = new DialpadAnimation(getContext(), false /* reverse */, animate);
            mDialpadContainer.startAnimation(anim);
        } else {
            final int toggleButtonImageOffset = getResources().getDimensionPixelSize(
                    R.dimen.in_call_toggle_button_image_offset);
            final int muteButtonLeftMargin =
                    ((LinearLayout.LayoutParams) mMuteButton.getLayoutParams()).leftMargin;

            mEndCallButton.animate()
                    .alpha(0)
                    .setStartDelay(0)
                    .setDuration(384)
                    .setInterpolator(mAccelerateDecelerateInterpolator)
                    .withEndAction(() -> {
                            mEndCallButton.setVisibility(View.INVISIBLE);
                            mEndCallButton.setFocusable(false);
                        }).start();
            mMuteButton.animate()
                    .alpha(0)
                    .setStartDelay(0)
                    .setDuration(240)
                    .setInterpolator(mAccelerateDecelerateInterpolator)
                    .withEndAction(() -> {
                            mMuteButton.setVisibility(View.INVISIBLE);
                            mMuteButton.setFocusable(false);
                        }).start();
            mToggleDialpadButton.animate()
                    .setStartDelay(0)
                    .translationX(-(mEndCallButton.getWidth() + muteButtonLeftMargin
                            + mMuteButton.getWidth() + toggleButtonImageOffset))
                    .setDuration(480)
                    .setInterpolator(mAccelerateDecelerateInterpolator)
                    .start();

            mRotaryDialpad.setTranslationX(
                    -(mEndCallButton.getWidth() + muteButtonLeftMargin + toggleButtonImageOffset));
            mRotaryDialpad.animate()
                    .translationX(-(mEndCallButton.getWidth() + muteButtonLeftMargin
                            + mMuteButton.getWidth() + toggleButtonImageOffset))
                    .setDuration(320)
                    .setInterpolator(mAccelerateDecelerateInterpolator)
                    .setStartDelay(240)
                    .withStartAction(() -> {
                            mRotaryDialpad.setVisibility(View.VISIBLE);
                            int delay = 0;
                            for (View dialpadView : mDialpadViews) {
                                dialpadView.setAlpha(0);
                                dialpadView.animate()
                                        .alpha(1)
                                        .setDuration(160)
                                        .setStartDelay(delay)
                                        .setInterpolator(mAccelerateInterpolator)
                                        .start();
                                delay += 10;
                            }
                        }).start();
        }
    }

    private void closeDialpad() {
        if (!mToggleDialpadButton.isActivated()) {
            return;
        }
        mToggleDialpadButton.setActivated(false);
        if (getResources().getBoolean(R.bool.has_touch)) {
            Animation anim = new DialpadAnimation(getContext(), true /* reverse */);
            mDialpadContainer.startAnimation(anim);
        } else {
            final int toggleButtonImageOffset = getResources().getDimensionPixelSize(
                    R.dimen.in_call_toggle_button_image_offset);
            final int muteButtonLeftMargin =
                    ((LinearLayout.LayoutParams) mMuteButton.getLayoutParams()).leftMargin;

            mRotaryDialpad.animate()
                    .setStartDelay(0)
                    .translationX(-(mEndCallButton.getWidth()
                            + muteButtonLeftMargin + toggleButtonImageOffset))
                    .setDuration(320)
                    .setInterpolator(mAccelerateDecelerateInterpolator)
                    .withStartAction(() -> {
                            int delay = 0;
                            for (int i = mDialpadViews.size() - 1; i >= 0; i--) {
                                View dialpadView = mDialpadViews.get(i);
                                dialpadView.animate()
                                        .alpha(0)
                                        .setDuration(160)
                                        .setStartDelay(delay)
                                        .setInterpolator(mAccelerateInterpolator)
                                        .start();
                                delay += 10;
                            }
                        }).withEndAction(() -> {
                            mRotaryDialpad.setVisibility(View.GONE);
                            mRotaryDialpad.setTranslationX(0);
                        }).start();
            mToggleDialpadButton.animate()
                    .translationX(0)
                    .setDuration(480)
                    .setStartDelay(80)
                    .setInterpolator(mAccelerateDecelerateInterpolator)
                    .start();
            mMuteButton.animate()
                    .alpha(1)
                    .setDuration(176)
                    .setInterpolator(mAccelerateDecelerateInterpolator)
                    .setStartDelay(384)
                    .withStartAction(() -> {
                            mMuteButton.setVisibility(View.VISIBLE);
                            mMuteButton.setFocusable(true);
                        }).start();
            mEndCallButton.animate()
                    .alpha(1)
                    .setDuration(320)
                    .setInterpolator(mAccelerateDecelerateInterpolator)
                    .setStartDelay(240)
                    .withStartAction(() -> {
                            mEndCallButton.setVisibility(View.VISIBLE);
                            mEndCallButton.setFocusable(true);
                        }).start();
        }
    }

    private void updateRotaryFocus() {
        boolean hasTouch = getResources().getBoolean(R.bool.has_touch);
        if (mPrimaryCall != null && !hasTouch) {
            if (mPrimaryCall.getState() == Call.STATE_RINGING) {
                mRingingCallControls.requestFocus();
            } else {
                mActiveCallControls.requestFocus();
            }
        }
    }

    private void setInCallControllerFocusability(boolean focusable) {
        mSwapButton.setFocusable(focusable);
        mMergeButton.setFocusable(focusable);

        mAnswerCallButton.setFocusable(focusable);
        mRejectCallButton.setFocusable(focusable);

        mEndCallButton.setFocusable(focusable);
        mUnholdCallButton.setFocusable(focusable);
        mMuteButton.setFocusable(focusable);
        mToggleDialpadButton.setFocusable(focusable);
    }

    private void setDialPadFocusability(boolean focusable) {
        for (View dialPadView : mDialpadViews) {
            dialPadView.setFocusable(focusable);
        }
    }

    private final View.OnTouchListener mDialpadTouchListener = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            Character digit = mDialpadButtonMap.get(v.getId());
            if (digit == null) {
                Log.w(TAG, "Unknown dialpad button pressed.");
                return false;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.setPressed(true);
                mUiCallManager.playDtmfTone(mPrimaryCall, digit);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                v.setPressed(false);
                v.performClick();
                mUiCallManager.stopDtmfTone(mPrimaryCall);
                return true;
            }

            return false;
        }
    };

    private final View.OnKeyListener mDialpadKeyListener = new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            Character digit = mDialpadButtonMap.get(v.getId());
            if (digit == null) {
                Log.w(TAG, "Unknown dialpad button pressed.");
                return false;
            }

            if (event.getKeyCode() != KeyEvent.KEYCODE_DPAD_CENTER) {
                return false;
            }

            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                v.setPressed(true);
                mUiCallManager.playDtmfTone(mPrimaryCall, digit);
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                v.setPressed(false);
                mUiCallManager.stopDtmfTone(mPrimaryCall);
                return true;
            }

            return false;
        }
    };

    private final Runnable mUpdateDurationRunnable = new Runnable() {
        @Override
        public void run() {
            if (mPrimaryCall.getState() != Call.STATE_ACTIVE) {
                return;
            }
            String callInfoText = TelecomUtils.getCallInfoText(getContext(),
                    mPrimaryCall, mCallInfoLabel);
            setStateText(callInfoText);
            mHandler.postDelayed(this /* runnable */, DateUtils.SECOND_IN_MILLIS);
        }
    };

    private final Runnable mStopDtmfToneRunnable = new Runnable() {
        @Override
        public void run() {
            mUiCallManager.stopDtmfTone(mPrimaryCall);
        }
    };

    private final class DialpadAnimation extends Animation {
        private static final int DURATION = 300;
        private static final float MAX_SCRIM_ALPHA = 0.6f;

        private final int mStartingTranslation;
        private final int mScrimColor;
        private final boolean mReverse;

        public DialpadAnimation(Context context, boolean reverse) {
            this(context, reverse, true);
        }

        public DialpadAnimation(Context context, boolean reverse, boolean animate) {
            setDuration(animate ? DURATION : 0);
            setInterpolator(new AccelerateDecelerateInterpolator());
            Resources res = context.getResources();
            mStartingTranslation =
                    res.getDimensionPixelOffset(R.dimen.in_call_card_dialpad_translation_x);
            mScrimColor = res.getColor(R.color.phone_theme);
            mReverse = reverse;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            if (mReverse) {
                interpolatedTime = 1f - interpolatedTime;
            }
            int translationX = (int) (mStartingTranslation * (1f - interpolatedTime));
            mDialpadContainer.setTranslationX(translationX);
            mDialpadContainer.setAlpha(interpolatedTime);
            if (interpolatedTime == 0f) {
                mDialpadContainer.setVisibility(View.GONE);
            } else {
                mDialpadContainer.setVisibility(View.VISIBLE);
            }
            float alpha = 255f * interpolatedTime * MAX_SCRIM_ALPHA;
            mLargeContactPhotoView.setColorFilter(Color.argb((int) alpha, Color.red(mScrimColor),
                    Color.green(mScrimColor), Color.blue(mScrimColor)));

            mSecondaryNameTextView.setAlpha(1f - interpolatedTime);
            mSecondaryStateTextView.setAlpha(1f - interpolatedTime);
        }
    }

    private final CallListener mCallListener = new CallListener() {

        @Override
        public void onCallAdded(UiCall call) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "on call added");
            }
            updateCalls();
            trySpeakerAudioRouteIfNecessary();
        }

        @Override
        public void onCallRemoved(UiCall call) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "on call removed");
            }
            mLastRemovedCall = call;
            updateCalls();
        }

        @Override
        public void onAudioStateChanged(boolean isMuted, int audioRoute,
                int supportedAudioRouteMask) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "on audio state changed");
            }
            mMuteButton.setActivated(isMuted);
            trySpeakerAudioRouteIfNecessary();
        }

        @Override
        public void onStateChanged(UiCall call, int state) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onStateChanged");
            }
            updateCalls();
            //  this will reset the focus if any state of any call changes on pure rotary devices.
            updateRotaryFocus();
        }

        @Override
        public void onCallUpdated(UiCall call) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCallUpdated");
            }
            updateCalls();
        }
    };

    private final UiBluetoothMonitor.Listener mBluetoothListener = () -> {
        OngoingCallFragment.this.mIsHfpConnected =
                UiBluetoothMonitor.getInstance().isHfpConnected();
    };
}
