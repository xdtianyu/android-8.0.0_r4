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

import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.telecom.Call;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.android.car.app.CarDrawerActivity;
import com.android.car.app.CarDrawerAdapter;
import com.android.car.app.DrawerItemViewHolder;
import com.android.car.dialer.bluetooth.UiBluetoothMonitor;
import com.android.car.dialer.telecom.PhoneLoader;
import com.android.car.dialer.telecom.UiCall;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.dialer.telecom.UiCallManager.CallListener;

import java.util.List;

/**
 * Main activity for the Dialer app. Displays different fragments depending on call and
 * connectivity status:
 * <ul>
 * <li>OngoingCallFragment
 * <li>NoHfpFragment
 * <li>DialerFragment
 * <li>StrequentFragment
 * </ul>
 */
public class TelecomActivity extends CarDrawerActivity implements
        DialerFragment.DialerBackButtonListener {
    private static final String TAG = "Em.TelecomActivity";

    private static final String ACTION_ANSWER_CALL = "com.android.car.dialer.ANSWER_CALL";
    private static final String ACTION_END_CALL = "com.android.car.dialer.END_CALL";
    private static final String DIALER_BACKSTACK = "DialerBackstack";
    private static final String FRAGMENT_CLASS_KEY = "FRAGMENT_CLASS_KEY";

    private final UiBluetoothMonitor.Listener mBluetoothListener = this::updateCurrentFragment;

    private UiCallManager mUiCallManager;
    private UiBluetoothMonitor mUiBluetoothMonitor;

    private Fragment mCurrentFragment;
    private String mCurrentFragmentName;

    private int mLastNoHfpMessageId;
    private StrequentsFragment mSpeedDialFragment;
    private Fragment mOngoingCallFragment;

    private DialerFragment mDialerFragment;
    private boolean mDialerFragmentOpened;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (vdebug()) {
            Log.d(TAG, "onCreate");
        }
        getWindow().getDecorView().setBackgroundColor(getColor(R.color.phone_theme));
        setTitle(getString(R.string.phone_app_name));

        mUiCallManager = UiCallManager.getInstance(this);
        mUiBluetoothMonitor = UiBluetoothMonitor.getInstance();

        if (savedInstanceState != null) {
            mCurrentFragmentName = savedInstanceState.getString(FRAGMENT_CLASS_KEY);
        }

        if (vdebug()) {
            Log.d(TAG, "onCreate done, mCurrentFragmentName:  " + mCurrentFragmentName);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (vdebug()) {
            Log.d(TAG, "onDestroy");
        }
        mUiCallManager = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mUiCallManager.removeListener(mCarCallListener);
        mUiBluetoothMonitor.removeListener(mBluetoothListener);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mCurrentFragment != null) {
            outState.putString(FRAGMENT_CLASS_KEY, mCurrentFragmentName);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
    }

    @Override
    protected void onResume() {
        if (vdebug()) {
            Log.d(TAG, "onResume");
        }
        super.onResume();

        // Update the current fragment before handling the intent so that any UI updates in
        // handleIntent() is not overridden by updateCurrentFragment().
        updateCurrentFragment();
        handleIntent();

        mUiCallManager.addListener(mCarCallListener);
        mUiBluetoothMonitor.addListener(mBluetoothListener);
    }

    // TODO: move to base class.
    private void setContentFragmentWithAnimations(Fragment fragment, int enter, int exit) {
        if (vdebug()) {
            Log.d(TAG, "setContentFragmentWithAnimations: " + fragment);
        }

        maybeHideDialer();

        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(enter, exit)
                .replace(getContentContainerId(), fragment)
                .commitAllowingStateLoss();

        mCurrentFragmentName = fragment.getClass().getSimpleName();
        mCurrentFragment = fragment;

        if (vdebug()) {
            Log.d(TAG, "setContentFragmentWithAnimations, fragmentName:" + mCurrentFragmentName);
        }
    }

    private void handleIntent() {
        Intent intent = getIntent();
        String action = intent != null ? intent.getAction() : null;

        if (vdebug()) {
            Log.d(TAG, "handleIntent, intent: " + intent + ", action: " + action);
        }

        if (action == null || action.length() == 0) {
            return;
        }

        UiCall ringingCall;
        switch (action) {
            case ACTION_ANSWER_CALL:
                ringingCall = mUiCallManager.getCallWithState(Call.STATE_RINGING);
                if (ringingCall == null) {
                    Log.e(TAG, "Unable to answer ringing call. There is none.");
                } else {
                    mUiCallManager.answerCall(ringingCall);
                }
                break;

            case ACTION_END_CALL:
                ringingCall = mUiCallManager.getCallWithState(Call.STATE_RINGING);
                if (ringingCall == null) {
                    Log.e(TAG, "Unable to end ringing call. There is none.");
                } else {
                    mUiCallManager.disconnectCall(ringingCall);
                }
                break;

            case Intent.ACTION_DIAL:
                String number = PhoneNumberUtils.getNumberFromIntent(intent, this);
                if (!(mCurrentFragment instanceof NoHfpFragment)) {
                    showDialerWithNumber(number);
                }
                break;

            default:
                // Do nothing.
        }

        setIntent(null);
    }

    /**
     * Will switch to the drawer or no-hfp fragment as necessary.
     */
    private void updateCurrentFragment() {
        if (vdebug()) {
            Log.d(TAG, "updateCurrentFragment");
        }

        // TODO: do nothing when activity isFinishing() == true.

        boolean callEmpty = mUiCallManager.getCalls().isEmpty();
        if (!mUiBluetoothMonitor.isBluetoothEnabled() && callEmpty) {
            showNoHfpFragment(R.string.bluetooth_disabled);
        } else if (!mUiBluetoothMonitor.isBluetoothPaired() && callEmpty) {
            showNoHfpFragment(R.string.bluetooth_unpaired);
        } else if (!mUiBluetoothMonitor.isHfpConnected() && callEmpty) {
            showNoHfpFragment(R.string.no_hfp);
        } else {
            UiCall ongoingCall = mUiCallManager.getPrimaryCall();

            if (vdebug()) {
                Log.d(TAG, "ongoingCall: " + ongoingCall + ", mCurrentFragment: "
                        + mCurrentFragment);
            }

            if (ongoingCall == null && mCurrentFragment instanceof OngoingCallFragment) {
                showSpeedDialFragment();
            } else if (ongoingCall != null) {
                showOngoingCallFragment();
            } else if (DialerFragment.class.getSimpleName().equals(mCurrentFragmentName)) {
                showDialer();
            } else {
                showSpeedDialFragment();
            }
        }

        if (vdebug()) {
            Log.d(TAG, "updateCurrentFragment: done");
        }
    }

    private void showSpeedDialFragment() {
        if (vdebug()) {
            Log.d(TAG, "showSpeedDialFragment");
        }

        if (mCurrentFragment instanceof StrequentsFragment) {
            return;
        }

        if (mSpeedDialFragment == null) {
            mSpeedDialFragment = new StrequentsFragment();
            Bundle args = new Bundle();
            mSpeedDialFragment.setArguments(args);
        }

        if (mCurrentFragment instanceof DialerFragment) {
            setContentFragmentWithSlideAndDelayAnimation(mSpeedDialFragment);
        } else {
            setContentFragmentWithFadeAnimation(mSpeedDialFragment);
        }
    }

    private void showOngoingCallFragment() {
        if (vdebug()) {
            Log.d(TAG, "showOngoingCallFragment");
        }
        if (mCurrentFragment instanceof OngoingCallFragment) {
            closeDrawer();
            return;
        }

        if (mOngoingCallFragment == null) {
            mOngoingCallFragment = new OngoingCallFragment();
        }

        setContentFragmentWithFadeAnimation(mOngoingCallFragment);
        closeDrawer();
    }

    /**
     * Displays the {@link DialerFragment} on top of the contents of the TelecomActivity.
     */
    private void showDialer() {
        if (vdebug()) {
            Log.d(TAG, "showDialer");
        }

        if (mDialerFragmentOpened) {
            return;
        }

        if (mDialerFragment == null) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "showDialer: creating dialer");
            }

            mDialerFragment = new DialerFragment();
            mDialerFragment.setDialerBackButtonListener(this);
        }

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "adding dialer to fragment backstack");
        }

        // Add the dialer fragment to the backstack so that it can be popped off to dismiss it.
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.telecom_slide_in, R.anim.telecom_slide_out,
                        R.anim.telecom_slide_in, R.anim.telecom_slide_out)
                .add(getContentContainerId(), mDialerFragment)
                .addToBackStack(DIALER_BACKSTACK)
                .commitAllowingStateLoss();

        mDialerFragmentOpened = true;

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "done adding fragment to backstack");
        }
    }

    /**
     * Checks if the dialpad fragment is opened and hides it if it is.
     */
    private void maybeHideDialer() {
        if (mDialerFragmentOpened) {
            // Dismiss the dialer by removing it from the back stack.
            getSupportFragmentManager().popBackStack();
            mDialerFragmentOpened = false;
        }
    }

    @Override
    public void onDialerBackClick() {
        maybeHideDialer();
    }

    private void showDialerWithNumber(String number) {
        showDialer();
        mDialerFragment.setDialNumber(number);
    }

    private void showNoHfpFragment(int stringResId) {
        if (mCurrentFragment instanceof NoHfpFragment && stringResId == mLastNoHfpMessageId) {
            return;
        }

        mLastNoHfpMessageId = stringResId;
        String errorMessage = getString(stringResId);
        NoHfpFragment frag = new NoHfpFragment();
        frag.setErrorMessage(errorMessage);
        setContentFragment(frag);
        mCurrentFragment = frag;
    }

    private void setContentFragmentWithSlideAndDelayAnimation(Fragment fragment) {
        if (vdebug()) {
            Log.d(TAG, "setContentFragmentWithSlideAndDelayAnimation, fragment: " + fragment);
        }
        setContentFragmentWithAnimations(fragment,
                R.anim.telecom_slide_in_with_delay, R.anim.telecom_slide_out);
    }

    private void setContentFragmentWithFadeAnimation(Fragment fragment) {
        if (vdebug()) {
            Log.d(TAG, "setContentFragmentWithFadeAnimation, fragment: " + fragment);
        }
        setContentFragmentWithAnimations(fragment,
                R.anim.telecom_fade_in, R.anim.telecom_fade_out);
    }

    private final CallListener mCarCallListener = new UiCallManager.CallListener() {
        @Override
        public void onCallAdded(UiCall call) {
            if (vdebug()) {
                Log.d(TAG, "onCallAdded");
            }
            updateCurrentFragment();
        }

        @Override
        public void onCallRemoved(UiCall call) {
            if (vdebug()) {
                Log.d(TAG, "onCallRemoved");
            }
            updateCurrentFragment();
        }

        @Override
        public void onStateChanged(UiCall call, int state) {
            if (vdebug()) {
                Log.d(TAG, "onStateChanged");
            }
            updateCurrentFragment();
        }

        @Override
        public void onCallUpdated(UiCall call) {
            if (vdebug()) {
                Log.d(TAG, "onCallUpdated");
            }
            updateCurrentFragment();
        }
    };

    private void setContentFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(getContentContainerId(), fragment)
                .commit();
    }

    private static boolean vdebug() {
        return Log.isLoggable(TAG, Log.DEBUG);
    }

    @Override
    protected CarDrawerAdapter getRootAdapter() {
        return new DialerRootAdapter();
    }

    class CallLogAdapter extends CarDrawerAdapter {
        private List<CallLogListingTask.CallLogItem> mItems;

        public CallLogAdapter(int titleResId, List<CallLogListingTask.CallLogItem> items) {
            super(TelecomActivity.this,
                    true  /* showDisabledListOnEmpty */,
                    false /* useSmallLayout */);
            setTitle(getString(titleResId));
            mItems = items;
        }

        @Override
        protected int getActualItemCount() {
            return mItems.size();
        }

        @Override
        public void populateViewHolder(DrawerItemViewHolder holder, int position) {
            holder.getTitle().setText(mItems.get(position).mTitle);
            holder.getText().setText(mItems.get(position).mText);
            holder.getIcon().setImageBitmap(mItems.get(position).mIcon);
        }

        @Override
        public void onItemClick(int position) {
            closeDrawer();
            mUiCallManager.safePlaceCall(mItems.get(position).mNumber, false);
        }
    }

    private class DialerRootAdapter extends CarDrawerAdapter {
        private static final int ITEM_DIAL = 0;
        private static final int ITEM_CALLLOG_ALL = 1;
        private static final int ITEM_CALLLOG_MISSED = 2;
        private static final int ITEM_MAX = 3;

        DialerRootAdapter() {
            super(TelecomActivity.this,
                    false /* showDisabledListOnEmpty */,
                    true  /* useSmallLayout */);
            setTitle(getString(R.string.phone_app_name));
        }

        @Override
        protected int getActualItemCount() {
            return ITEM_MAX;
        }

        @Override
        public void populateViewHolder(DrawerItemViewHolder holder, int position) {
            final int iconColor = getResources().getColor(R.color.car_tint);
            int textResId, iconResId;
            switch (position) {
                case ITEM_DIAL:
                    textResId = R.string.calllog_dial_number;
                    iconResId = R.drawable.ic_drawer_dialpad;
                    break;
                case ITEM_CALLLOG_ALL:
                    textResId = R.string.calllog_all;
                    iconResId = R.drawable.ic_drawer_history;
                    break;
                case ITEM_CALLLOG_MISSED:
                    textResId = R.string.calllog_missed;
                    iconResId = R.drawable.ic_drawer_call_missed;
                    break;
                default:
                    Log.wtf(TAG, "Unexpected position: " + position);
                    return;
            }
            holder.getTitle().setText(textResId);
            Drawable drawable = getDrawable(iconResId);
            drawable.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
            holder.getIcon().setImageDrawable(drawable);
            if (position > 0) {
                drawable = getDrawable(R.drawable.ic_chevron_right);
                drawable.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
                holder.getRightIcon().setImageDrawable(drawable);
            }
        }

        @Override
        public void onItemClick(int position) {
            switch (position) {
                case ITEM_DIAL:
                    closeDrawer();
                    showDialer();
                    break;
                case ITEM_CALLLOG_ALL:
                    loadCallHistoryAsync(PhoneLoader.CALL_TYPE_ALL, R.string.calllog_all);
                    break;
                case ITEM_CALLLOG_MISSED:
                    loadCallHistoryAsync(PhoneLoader.CALL_TYPE_MISSED, R.string.calllog_missed);
                    break;
                default:
                    Log.w(TAG, "Invalid position in ROOT menu! " + position);
            }
        }
    }

    private void loadCallHistoryAsync(final int callType, final int titleResId) {
        showLoadingProgressBar(true);
        // Warning: much callbackiness!
        // First load up the call log cursor using the PhoneLoader so that happens in a
        // background thread. TODO: Why isn't PhoneLoader using a LoaderManager?
        PhoneLoader.registerCallObserver(callType, this,
            (loader, data) -> {
                // This callback runs on the thread that created the loader which is
                // the ui thread so spin off another async task because we still need
                // to pull together all the data along with the contact photo.
                CallLogListingTask task = new CallLogListingTask(TelecomActivity.this, data,
                    (items) -> {
                            showLoadingProgressBar(false);
                            switchToAdapter(new CallLogAdapter(titleResId, items));
                        });
                task.execute();
            });
    }
}
