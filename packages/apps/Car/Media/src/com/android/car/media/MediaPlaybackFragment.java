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
package com.android.car.media;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.BadParcelableException;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.car.ui.ColorChecker;
import android.support.v4.app.Fragment;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.car.apps.common.BitmapDownloader;
import com.android.car.apps.common.BitmapWorkerOptions;
import com.android.car.apps.common.util.Assert;
import com.android.car.media.util.widgets.MusicPanelLayout;
import com.android.car.media.util.widgets.PlayPauseStopImageView;

import java.util.List;
import java.util.Objects;

/**
 * Fragment that displays the media playback UI.
 */
public class MediaPlaybackFragment extends Fragment implements MediaPlaybackModel.Listener {
    private static final String TAG = "MediaPlayback";

    private static final String[] PREFERRED_BITMAP_ORDER = {
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON
    };

    private static final String[] PREFERRED_URI_ORDER = {
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI
    };

    private static final long SEEK_BAR_UPDATE_TIME_INTERVAL_MS = 500;
    private static final long DELAY_CLOSE_OVERFLOW_MS = 3500;
    // delay showing the no content view for 3 second -- when the media app cold starts, it
    // usually takes a moment to load the last played song from database. So we will wait for
    // 3 sec, before we show the no content view, instead of showing it and immediately
    // switch to playback view when the metadata loads.
    private static final long DELAY_SHOW_NO_CONTENT_VIEW_MS = 3000;
    private static final long FEEDBACK_MESSAGE_DISPLAY_TIME_MS = 6000;

    private MediaActivity mActivity;
    private MediaPlaybackModel mMediaPlaybackModel;
    private final Handler mHandler = new Handler();

    private TextView mTitleView;
    private TextView mArtistView;
    private ImageButton mPrevButton;
    private PlayPauseStopImageView mPlayPauseStopButton;
    private ImageButton mNextButton;
    private ImageButton mPlayQueueButton;
    private MusicPanelLayout mMusicPanel;
    private LinearLayout mControlsView;
    private LinearLayout mOverflowView;
    private ImageButton mOverflowOnButton;
    private ImageButton mOverflowOffButton;
    private final ImageButton[] mCustomActionButtons = new ImageButton[4];
    private SeekBar mSeekBar;
    private ProgressBar mSpinner;
    private boolean mOverflowVisibility;
    private long mStartProgress;
    private long mStartTime;
    private MediaDescription mCurrentTrack;
    private boolean mShowingMessage;
    private View mInitialNoContentView;
    private View mMetadata;
    private ImageView mMusicErrorIcon;
    private TextView mTapToSelectText;
    private ProgressBar mAppConnectingSpinner;
    private boolean mDelayedResetTitleInProgress;
    private int mAlbumArtWidth = 800;
    private int mAlbumArtHeight = 400;
    private int mShowTitleDelayMs = 250;
    private TelephonyManager mTelephonyManager;
    private boolean mInCall = false;
    private BitmapDownloader mDownloader;
    private boolean mReturnFromOnStop = false;

    private enum ViewType {
        NO_CONTENT_VIEW,
        PLAYBACK_CONTROLS_VIEW,
        LOADING_VIEW,
    }

    private ViewType mCurrentView;

    public MediaPlaybackFragment() {
      super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = (MediaActivity) getHost();
        mShowTitleDelayMs =
                mActivity.getResources().getInteger(R.integer.new_album_art_fade_in_offset);
        mMediaPlaybackModel = new MediaPlaybackModel(mActivity, null /* browserExtras */);
        mMediaPlaybackModel.addListener(this);
        mTelephonyManager =
                (TelephonyManager) mActivity.getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCurrentView = null;
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        mMediaPlaybackModel = null;
        mActivity = null;
        // Calling this with null will clear queue of callbacks and message.
        mHandler.removeCallbacksAndMessages(null);
        mDelayedResetTitleInProgress = false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.now_playing_screen, container, false);
        mTitleView = (TextView) v.findViewById(R.id.title);
        mArtistView = (TextView) v.findViewById(R.id.artist);
        mSeekBar = (SeekBar) v.findViewById(R.id.seek_bar);
        // In L setEnabled(false) will make the tint color wrong, but not in M.
        mSeekBar.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Eat up touch events from users as we set progress programmatically only.
                return true;
            }
        });
        mControlsView = (LinearLayout) v.findViewById(R.id.controls);
        mPlayQueueButton = (ImageButton) v.findViewById(R.id.play_queue);
        mPrevButton = (ImageButton) v.findViewById(R.id.prev);
        mPlayPauseStopButton = (PlayPauseStopImageView) v.findViewById(R.id.play_pause);
        mNextButton = (ImageButton) v.findViewById(R.id.next);
        mOverflowOnButton = (ImageButton) v.findViewById(R.id.overflow_on);
        mOverflowView = (LinearLayout) v.findViewById(R.id.overflow_items);
        mOverflowOffButton = (ImageButton) v.findViewById(R.id.overflow_off);
        setActionDrawable(mOverflowOffButton, R.drawable.ic_overflow_activated, getResources());
        mMusicPanel = (MusicPanelLayout) v.findViewById(R.id.music_panel);
        mMusicPanel.setDefaultFocus(mPlayPauseStopButton);
        mSpinner = (ProgressBar) v.findViewById(R.id.spinner);
        mInitialNoContentView = v.findViewById(R.id.initial_view);
        mMetadata = v.findViewById(R.id.metadata);

        mMusicErrorIcon = (ImageView) v.findViewById(R.id.error_icon);
        mTapToSelectText = (TextView) v.findViewById(R.id.tap_to_select_item);
        mAppConnectingSpinner = (ProgressBar) v.findViewById(R.id.loading_spinner);

        mCustomActionButtons[0] = (ImageButton) v.findViewById(R.id.custom_action_1);
        mCustomActionButtons[1] = (ImageButton) v.findViewById(R.id.custom_action_2);
        mCustomActionButtons[2] = (ImageButton) v.findViewById(R.id.custom_action_3);
        mCustomActionButtons[3] = (ImageButton) v.findViewById(R.id.custom_action_4);

        mPrevButton.setOnClickListener(mControlsClickListener);
        mNextButton.setOnClickListener(mControlsClickListener);
        // Yes they both need it. The layout is not focusable so it will never get the click.
        // You can't make the layout focusable because then the button wont highlight.
        v.findViewById(R.id.play_pause_container).setOnClickListener(mControlsClickListener);
        mPlayPauseStopButton.setOnClickListener(mControlsClickListener);
        mPlayQueueButton.setOnClickListener(mControlsClickListener);
        mOverflowOnButton.setOnClickListener(mControlsClickListener);
        mOverflowOffButton.setOnClickListener(mControlsClickListener);

        // If touch mode is enabled, we disable focus from buttons.
        if (getResources().getBoolean(R.bool.has_touch)) {
            setControlsFocusability(false);
            setOverflowFocusability(false);
        }

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Pair<Integer, Integer> albumArtSize = mActivity.getAlbumArtSize();
        if (albumArtSize != null) {
            if (albumArtSize.first > 0 && albumArtSize.second > 0) {
                mAlbumArtWidth = albumArtSize.first;
                mAlbumArtHeight = albumArtSize.second;
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mMediaPlaybackModel.stop();
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        // When switch apps, onStop() will be called. Mark it and don't show fade in/out title and
        // background animations when come back.
        mReturnFromOnStop = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        mMediaPlaybackModel.start();
        // Note: at registration, TelephonyManager will invoke the callback with the current state.
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    @Override
    public void onMediaAppChanged(@Nullable ComponentName currentName,
            @Nullable ComponentName newName) {
        Assert.isMainThread();
        resetTitle();
        if (Objects.equals(currentName, newName)) {
            return;
        }
        int accentColor = mMediaPlaybackModel.getAccentColor();
        mPlayPauseStopButton.setPrimaryActionColor(accentColor);
        mSeekBar.getProgressDrawable().setColorFilter(accentColor, PorterDuff.Mode.SRC_IN);
        int overflowViewColor = mMediaPlaybackModel.getPrimaryColorDark();
        mOverflowView.getBackground().setColorFilter(overflowViewColor, PorterDuff.Mode.SRC_IN);
        // Tint the overflow actions light or dark depending on contrast.
        int overflowTintColor = ColorChecker.getTintColor(mActivity, overflowViewColor);
        for (ImageView v : mCustomActionButtons) {
            v.setColorFilter(overflowTintColor, PorterDuff.Mode.SRC_IN);
        }
        mOverflowOffButton.setColorFilter(overflowTintColor, PorterDuff.Mode.SRC_IN);
        ColorStateList colorStateList = ColorStateList.valueOf(accentColor);
        mSpinner.setIndeterminateTintList(colorStateList);
        mAppConnectingSpinner.setIndeterminateTintList(ColorStateList.valueOf(accentColor));
        showLoadingView();
        closeOverflowMenu();
    }

    @Override
    public void onMediaAppStatusMessageChanged(@Nullable String message) {
        Assert.isMainThread();
        if (message == null) {
            resetTitle();
        } else {
            showMessage(message);
        }
    }

    @Override
    public void onMediaConnected() {
        Assert.isMainThread();
        onMetadataChanged(mMediaPlaybackModel.getMetadata());
        onQueueChanged(mMediaPlaybackModel.getQueue());
        onPlaybackStateChanged(mMediaPlaybackModel.getPlaybackState());
        mReturnFromOnStop = false;
    }

    @Override
    public void onMediaConnectionSuspended() {
        Assert.isMainThread();
        mReturnFromOnStop = false;
    }

    @Override
    public void onMediaConnectionFailed(CharSequence failedClientName) {
        Assert.isMainThread();
        showInitialNoContentView(getString(R.string.cannot_connect_to_app, failedClientName), true);
        mReturnFromOnStop = false;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    public void onPlaybackStateChanged(@Nullable PlaybackState state) {
        Assert.isMainThread();
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onPlaybackStateChanged; state: "
                    + (state == null ? "<< NULL >>" : state.toString()));
        }
        MediaMetadata metadata = mMediaPlaybackModel.getMetadata();
        if (state == null) {
            return;
        }

        if (state.getState() == PlaybackState.STATE_ERROR) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "ERROR: " + state.getErrorMessage());
            }
            showInitialNoContentView(state.getErrorMessage() != null ?
                    state.getErrorMessage().toString() :
                    mActivity.getString(R.string.unknown_error), true);
            return;
        }

        mStartProgress = state.getPosition();
        mStartTime = System.currentTimeMillis();
        mSeekBar.setProgress((int) mStartProgress);
        if (state.getState() == PlaybackState.STATE_PLAYING) {
            mHandler.post(mSeekBarRunnable);
        } else {
            mHandler.removeCallbacks(mSeekBarRunnable);
        }
        if (!mInCall) {
            int playbackState = state.getState();
            mPlayPauseStopButton.setPlayState(playbackState);
            // Due to the action of PlaybackState will be changed when the state of PlaybackState is
            // changed, we set mode every time onPlaybackStateChanged() is called.
            if (playbackState == PlaybackState.STATE_PLAYING ||
                    playbackState == PlaybackState.STATE_BUFFERING) {
                mPlayPauseStopButton.setMode(((state.getActions() & PlaybackState.ACTION_STOP) != 0)
                        ? PlayPauseStopImageView.MODE_STOP : PlayPauseStopImageView.MODE_PAUSE);
            } else {
                mPlayPauseStopButton.setMode(PlayPauseStopImageView.MODE_PAUSE);
            }
            mPlayPauseStopButton.refreshDrawableState();
        }
        if (state.getState() == PlaybackState.STATE_BUFFERING) {
            mSpinner.setVisibility(View.VISIBLE);
        } else {
            mSpinner.setVisibility(View.GONE);
        }

        updateActions(state.getActions(), state.getCustomActions());

        if (metadata == null) {
            return;
        }
        showMediaPlaybackControlsView();
    }

    @Override
    public void onMetadataChanged(@Nullable MediaMetadata metadata) {
        Assert.isMainThread();
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onMetadataChanged; description: "
                    + (metadata == null ? "<< NULL >>" : metadata.getDescription().toString()));
        }
        if (metadata == null) {
            mHandler.postDelayed(mShowNoContentViewRunnable, DELAY_SHOW_NO_CONTENT_VIEW_MS);
            return;
        } else {
            mHandler.removeCallbacks(mShowNoContentViewRunnable);
        }

        showMediaPlaybackControlsView();
        mCurrentTrack = metadata.getDescription();
        Bitmap icon = getMetadataBitmap(metadata);
        if (!mShowingMessage) {
            mHandler.removeCallbacks(mSetTitleRunnable);
            // Show the title when the new album art starts to fade in, but don't need to show
            // the fade in animation when come back from switching apps.
            mHandler.postDelayed(mSetTitleRunnable,
                    icon == null || mReturnFromOnStop ? 0 : mShowTitleDelayMs);
        }
        Uri iconUri = getMetadataIconUri(metadata);
        if (icon != null) {
            Bitmap scaledIcon = cropAlbumArt(icon);
            if (scaledIcon != icon && !icon.isRecycled()) {
                icon.recycle();
            }
            // Fade out the old background and then fade in the new one when the new album art
            // starts, but don't need to show the fade out and fade in animations when come back
            // from switching apps.
            mActivity.setBackgroundBitmap(scaledIcon, !mReturnFromOnStop /* showAnimation */);
        } else if (iconUri != null) {
            if (mDownloader == null) {
                mDownloader = new BitmapDownloader(mActivity);
            }
            final int flags = BitmapWorkerOptions.CACHE_FLAG_DISK_DISABLED
                    | BitmapWorkerOptions.CACHE_FLAG_MEM_DISABLED;
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Album art size " + mAlbumArtWidth + "x" + mAlbumArtHeight);
            }

            mDownloader.getBitmap(new BitmapWorkerOptions.Builder(mActivity).resource(iconUri)
                            .height(mAlbumArtHeight).width(mAlbumArtWidth).cacheFlag(flags).build(),
                    new BitmapDownloader.BitmapCallback() {
                        @Override
                        public void onBitmapRetrieved(Bitmap bitmap) {
                            if (mActivity != null) {
                                mActivity.setBackgroundBitmap(bitmap, true /* showAnimation */);
                            }
                        }
                    });
        } else {
            mActivity.setBackgroundColor(mMediaPlaybackModel.getPrimaryColorDark());
        }

        mSeekBar.setMax((int) metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
    }

    @Override
    public void onQueueChanged(List<MediaSession.QueueItem> queue) {
        Assert.isMainThread();
        if (queue.isEmpty()) {
            mPlayQueueButton.setVisibility(View.INVISIBLE);
        } else {
            mPlayQueueButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onSessionDestroyed(CharSequence destroyedMediaClientName) {
        Assert.isMainThread();
        mHandler.removeCallbacks(mSeekBarRunnable);
        if (mActivity != null) {
            showInitialNoContentView(
                    getString(R.string.cannot_connect_to_app, destroyedMediaClientName), true);
        }
    }


    public void showMessage(String msg) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "showMessage(); msg: " + msg);
        }
        // New messages will always be displayed regardless of if a feedback message is being shown.
        mHandler.removeCallbacks(mResetTitleRunnable);
        mActivity.darkenScrim(true);
        mTitleView.setSingleLine(false);
        mTitleView.setMaxLines(2);
        mArtistView.setVisibility(View.GONE);
        mTitleView.setText(msg);
        mShowingMessage = true;
    }

    boolean isOverflowMenuVisible() {
        return mOverflowVisibility;
    }

    void closeOverflowMenu() {
        mHandler.removeCallbacks(mCloseOverflowRunnable);
        setOverflowMenuVisibility(false);
    }

    void setOverflowMenuVisibility(boolean visibility) {
        if (mOverflowVisibility == visibility) {
            return;
        }
        mOverflowVisibility = visibility;
        if (visibility) {
            // Make the view invisible to let request focus work. Or else it will make b/23679226.
            mOverflowView.setVisibility(View.INVISIBLE);
            if (!getResources().getBoolean(R.bool.has_touch)) {
                setOverflowFocusability(true);
                setControlsFocusability(false);
            }
            mMusicPanel.setDefaultFocus(mOverflowOffButton);
            mOverflowOffButton.requestFocus();
            // After requesting focus is done, make the view to be visible.
            mOverflowView.setVisibility(View.VISIBLE);
            mOverflowView.animate().alpha(1f).setDuration(250)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            mControlsView.setVisibility(View.GONE);
                        }
                    });

            int tint = ColorChecker.getTintColor(mActivity,
                    mMediaPlaybackModel.getPrimaryColorDark());
            mSeekBar.getProgressDrawable().setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        } else {
            mControlsView.setVisibility(View.INVISIBLE);
            if (!getResources().getBoolean(R.bool.has_touch)) {
                setControlsFocusability(true);
                setOverflowFocusability(false);
            }
            mMusicPanel.setDefaultFocus(mPlayPauseStopButton);
            mOverflowOnButton.requestFocus();
            mControlsView.setVisibility(View.VISIBLE);
            mOverflowView.animate().alpha(0f).setDuration(250)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            mOverflowView.setVisibility(View.GONE);
                        }
                    });
            mSeekBar.getProgressDrawable().setColorFilter(
                    mMediaPlaybackModel.getAccentColor(), PorterDuff.Mode.SRC_IN);
        }
    }

    private void setControlsFocusability(boolean focusable) {
        mPlayQueueButton.setFocusable(focusable);
        mPrevButton.setFocusable(focusable);
        mPlayPauseStopButton.setFocusable(focusable);
        mNextButton.setFocusable(focusable);
        mOverflowOnButton.setFocusable(focusable);
    }

    private void setOverflowFocusability(boolean focusable) {
        mCustomActionButtons[0].setFocusable(focusable);
        mCustomActionButtons[1].setFocusable(focusable);
        mCustomActionButtons[2].setFocusable(focusable);
        mCustomActionButtons[3].setFocusable(focusable);
        mOverflowOffButton.setFocusable(focusable);
    }

    /**
     * For a given drawer slot, set the proper action of the slot's button,
     * based on the slot being reserved and the corresponding action being enabled.
     * If the slot is not reserved and the corresponding action is disabled,
     * then the next available custom action is assigned to the button.
     * @param button The button corresponding to the slot
     * @param originalResId The drawable resource ID for the original button,
     * only used if the original action is not replaced by a custom action.
     * @param slotAlwaysReserved True if the slot should be empty when the
     * corresponding action is disabled. If false, when the action is disabled
     * the slot has its default action replaced by the next custom action, if any.
     * @param isOriginalEnabled True if the original action of this button is
     * enabled.
     * @param customActions A list of custom actions still unassigned to slots.
     */
    private void handleSlot(ImageButton button, int originalResId, boolean slotAlwaysReserved,
            boolean isOriginalEnabled, List<PlaybackState.CustomAction> customActions) {
        if (isOriginalEnabled || slotAlwaysReserved) {
            setActionDrawable(button, originalResId, getResources());
            button.setVisibility(isOriginalEnabled ? View.VISIBLE : View.INVISIBLE);
            button.setTag(null);
        } else {
            if (customActions.isEmpty()) {
                button.setVisibility(View.INVISIBLE);
            } else {
                PlaybackState.CustomAction customAction = customActions.remove(0);
                Bundle extras = customAction.getExtras();
                boolean repeatedAction = false;
                try {
                    repeatedAction = (extras != null && extras.getBoolean(
                            MediaConstants.EXTRA_REPEATED_CUSTOM_ACTION_BUTTON, false));
                } catch (BadParcelableException e) {
                    Log.e(TAG, "custom parcelable in custom action extras.", e);
                }
                if (repeatedAction) {
                    button.setOnTouchListener(mControlsTouchListener);
                } else {
                    button.setOnClickListener(mControlsClickListener);
                }
                setCustomAction(button, customAction);
            }
        }
    }

    /**
     * Takes a list of custom actions and standard actions and displays them in the media
     * controls card (or hides ones that aren't available).
     *
     * @param actions A bit mask of active actions (android.media.session.PlaybackState#ACTION_*).
     * @param customActions A list of custom actions specified by the {@link android.media.session.MediaSession}.
     */
    private void updateActions(long actions, List<PlaybackState.CustomAction> customActions) {
        List<MediaSession.QueueItem> mediaQueue = mMediaPlaybackModel.getQueue();
        handleSlot(
                mPlayQueueButton, R.drawable.ic_tracklist,
                mMediaPlaybackModel.isSlotForActionReserved(
                        MediaConstants.EXTRA_RESERVED_SLOT_QUEUE),
                !mediaQueue.isEmpty(),
                customActions);

        handleSlot(
                mPrevButton, R.drawable.ic_skip_previous,
                mMediaPlaybackModel.isSlotForActionReserved(
                        MediaConstants.EXTRA_RESERVED_SLOT_SKIP_TO_PREVIOUS),
                (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0,
                customActions);

        handleSlot(
                mNextButton, R.drawable.ic_skip_next,
                mMediaPlaybackModel.isSlotForActionReserved(
                        MediaConstants.EXTRA_RESERVED_SLOT_SKIP_TO_NEXT),
                (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0,
                customActions);

        handleSlot(
                mOverflowOnButton, R.drawable.ic_overflow_normal,
                customActions.size() > 1,
                customActions.size() > 1,
                customActions);

        for (ImageButton button: mCustomActionButtons) {
            handleSlot(button, 0, false, false, customActions);
        }
    }

    private void setCustomAction(ImageButton imageButton, PlaybackState.CustomAction customAction) {
        imageButton.setVisibility(View.VISIBLE);
        setActionDrawable(imageButton, customAction.getIcon(),
                mMediaPlaybackModel.getPackageResources());
        imageButton.setTag(customAction);
    }

    private void showInitialNoContentView(String msg, boolean isError) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "showInitialNoContentView()");
        }
        if (!needViewChange(ViewType.NO_CONTENT_VIEW)) {
            return;
        }
        mAppConnectingSpinner.setVisibility(View.GONE);
        mActivity.setScrimVisibility(false);
        if (isError) {
            mActivity.setBackgroundColor(getResources().getColor(R.color.car_error_screen));
            mMusicErrorIcon.setVisibility(View.VISIBLE);
        } else {
            mActivity.setBackgroundColor(getResources().getColor(R.color.car_dark_blue_grey_800));
            mMusicErrorIcon.setVisibility(View.INVISIBLE);
        }
        mTapToSelectText.setVisibility(View.VISIBLE);
        mTapToSelectText.setText(msg);
        mInitialNoContentView.setVisibility(View.VISIBLE);
        mMetadata.setVisibility(View.GONE);
        mMusicPanel.setVisibility(View.GONE);
    }

    private void showMediaPlaybackControlsView() {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "showMediaPlaybackControlsView()");
        }
        if (!needViewChange(ViewType.PLAYBACK_CONTROLS_VIEW)) {
            return;
        }
        if (mPlayPauseStopButton != null && getResources().getBoolean(R.bool.has_wheel)) {
            mPlayPauseStopButton.requestFocusFromTouch();
        }

        if (!mShowingMessage) {
            mActivity.setScrimVisibility(true);
        }
        mInitialNoContentView.setVisibility(View.GONE);
        mMetadata.setVisibility(View.VISIBLE);
        mMusicPanel.setVisibility(View.VISIBLE);
    }

    private void showLoadingView() {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "showLoadingView()");
        }
        if (!needViewChange(ViewType.LOADING_VIEW)) {
            return;
        }
        mActivity.setBackgroundColor(
                getResources().getColor(R.color.music_loading_view_background));
        mAppConnectingSpinner.setVisibility(View.VISIBLE);
        mMusicErrorIcon.setVisibility(View.GONE);
        mTapToSelectText.setVisibility(View.GONE);
        mInitialNoContentView.setVisibility(View.VISIBLE);
        mMetadata.setVisibility(View.GONE);
        mMusicPanel.setVisibility(View.GONE);
    }

    private boolean needViewChange(ViewType newView) {
        if (mCurrentView != null && mCurrentView == newView) {
            return false;
        }
        mCurrentView = newView;
        return true;
    }

    private void resetTitle() {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "resetTitle()");
        }
        if (!mShowingMessage) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "message not currently shown, not resetting title");
            }
            return;
        }
        // Feedback message is currently being displayed, reset will automatically take place when
        // the display interval expires.
        if (mDelayedResetTitleInProgress) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "delay reset title is in progress, not resetting title now");
            }
            return;
        }
        // This will set scrim visible and alpha value back to normal.
        mActivity.setScrimVisibility(true);
        mTitleView.setSingleLine(true);
        mArtistView.setVisibility(View.VISIBLE);
        if (mCurrentTrack != null) {
            mTitleView.setText(mCurrentTrack.getTitle());
            mArtistView.setText(mCurrentTrack.getSubtitle());
        }
        mShowingMessage = false;
    }

    private Bitmap cropAlbumArt(Bitmap icon) {
        if (icon == null) {
            return null;
        }
        int width = icon.getWidth();
        int height = icon.getHeight();
        int startX = width > mAlbumArtWidth ? (width - mAlbumArtWidth) / 2 : 0;
        int startY = height > mAlbumArtHeight ? (height - mAlbumArtHeight) / 2 : 0;
        int newWidth = width > mAlbumArtWidth ? mAlbumArtWidth : width;
        int newHeight = height > mAlbumArtHeight ? mAlbumArtHeight : height;
        return Bitmap.createBitmap(icon, startX, startY, newWidth, newHeight);
    }

    private Bitmap getMetadataBitmap(MediaMetadata metadata) {
        // Get the best art bitmap we can find
        for (int i = 0; i < PREFERRED_BITMAP_ORDER.length; i++) {
            Bitmap bitmap = metadata.getBitmap(PREFERRED_BITMAP_ORDER[i]);
            if (bitmap != null) {
                return bitmap;
            }
        }
        return null;
    }

    private Uri getMetadataIconUri(MediaMetadata metadata) {
        // Get the best Uri we can find
        for (int i = 0; i < PREFERRED_URI_ORDER.length; i++) {
            String iconUri = metadata.getString(PREFERRED_URI_ORDER[i]);
            if (!TextUtils.isEmpty(iconUri)) {
                return Uri.parse(iconUri);
            }
        }
        return null;
    }

    private void setActionDrawable(ImageButton button, int resId, Resources resources) {
        if (resources == null) {
            Log.e(TAG, "Resources is null. Icons will not show up.");
            return;
        }

        Resources myResources = getResources();
        // the resources may be from another package. we need to update the configuration using
        // the context from the activity so we get the drawable from the correct DPI bucket.
        resources.updateConfiguration(
                myResources.getConfiguration(), myResources.getDisplayMetrics());
        try {
            Drawable icon = resources.getDrawable(resId, null);
            int inset = myResources.getDimensionPixelSize(R.dimen.music_action_icon_inset);
            InsetDrawable insetIcon = new InsetDrawable(icon, inset);
            button.setImageDrawable(insetIcon);
        } catch (Resources.NotFoundException e) {
            Log.w(TAG, "Resource not found: " + resId);
        }
    }

    private void checkAndDisplayFeedbackMessage(PlaybackState.CustomAction ca) {
        try {
            Bundle extras = ca.getExtras();
            if (extras != null) {
                String feedbackMessage = extras.getString(
                        MediaConstants.EXTRA_CUSTOM_ACTION_STATUS, "");
                if (!TextUtils.isEmpty(feedbackMessage)) {
                    // Show feedback message that appears for a time interval unless a new
                    // message is shown.
                    showMessage(feedbackMessage);
                    mDelayedResetTitleInProgress = true;
                    mHandler.postDelayed(mResetTitleRunnable, FEEDBACK_MESSAGE_DISPLAY_TIME_MS);
                }
            }
        } catch (BadParcelableException e) {
            Log.e(TAG, "Custom parcelable was added to extras, unable " +
                    "to check for feedback message.", e);
        }
    }

    private final View.OnTouchListener mControlsTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (!mMediaPlaybackModel.isConnected()) {
                Log.e(TAG, "Unable to send action for " + v
                        + ". The MediaPlaybackModel is not connected.");
                return true;
            }
            boolean onDown;
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    onDown = true;
                    break;
                case MotionEvent.ACTION_UP:
                    onDown = false;
                    break;
                default:
                    return true;
            }

            if (v.getTag() != null && v.getTag() instanceof PlaybackState.CustomAction) {
                PlaybackState.CustomAction ca = (PlaybackState.CustomAction) v.getTag();
                checkAndDisplayFeedbackMessage(ca);
                Bundle extras = ca.getExtras();
                try {
                    extras.putBoolean(
                            MediaConstants.EXTRA_REPEATED_CUSTOM_ACTION_BUTTON_ON_DOWN, onDown);
                } catch (BadParcelableException e) {
                    Log.e(TAG, "unable to on down notification for custom action.", e);
                }
                MediaController.TransportControls transportControls =
                        mMediaPlaybackModel.getTransportControls();
                transportControls.sendCustomAction(ca, extras);
                mHandler.removeCallbacks(mCloseOverflowRunnable);
                if (!onDown) {
                    mHandler.postDelayed(mCloseOverflowRunnable, DELAY_CLOSE_OVERFLOW_MS);
                }
            }
            return true;
        }
    };

    private final View.OnClickListener mControlsClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!mMediaPlaybackModel.isConnected()) {
                Log.e(TAG, "Unable to send action for " + v
                        + ". The MediaPlaybackModel is not connected.");
                return;
            }
            MediaController.TransportControls transportControls =
                    mMediaPlaybackModel.getTransportControls();
            if (v.getTag() != null && v.getTag() instanceof PlaybackState.CustomAction) {
                PlaybackState.CustomAction ca = (PlaybackState.CustomAction) v.getTag();
                checkAndDisplayFeedbackMessage(ca);
                transportControls.sendCustomAction(ca, ca.getExtras());
                mHandler.removeCallbacks(mCloseOverflowRunnable);
                mHandler.postDelayed(mCloseOverflowRunnable, DELAY_CLOSE_OVERFLOW_MS);
            } else {
                switch (v.getId()) {
                    case R.id.play_queue:
                        mActivity.showQueueInDrawer();
                        break;
                    case R.id.prev:
                        transportControls.skipToPrevious();
                        break;
                    case R.id.play_pause:
                    case R.id.play_pause_container:
                        PlaybackState playbackState = mMediaPlaybackModel.getPlaybackState();
                        if (playbackState == null) {
                            break;
                        }
                        long transportControlFlags = playbackState.getActions();
                        if (playbackState.getState() == PlaybackState.STATE_PLAYING) {
                            if ((transportControlFlags & PlaybackState.ACTION_PAUSE) != 0) {
                                transportControls.pause();
                            } else if ((transportControlFlags & PlaybackState.ACTION_STOP) != 0) {
                                transportControls.stop();
                            }
                        } else if (playbackState.getState() == PlaybackState.STATE_BUFFERING) {
                            if ((transportControlFlags & PlaybackState.ACTION_STOP) != 0) {
                                transportControls.stop();
                            } else if ((transportControlFlags & PlaybackState.ACTION_PAUSE) != 0) {
                                transportControls.pause();
                            }
                        } else {
                            transportControls.play();
                        }
                        break;
                    case R.id.next:
                        transportControls.skipToNext();
                        break;
                    case R.id.overflow_off:
                        mHandler.removeCallbacks(mCloseOverflowRunnable);
                        setOverflowMenuVisibility(false);
                        break;
                    case R.id.overflow_on:
                        setOverflowMenuVisibility(true);
                        break;
                    default:
                        throw new IllegalStateException("Unknown button press: " + v);
                }
            }
        }
    };

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING: // falls through
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    mPlayPauseStopButton
                            .setPlayState(PlayPauseStopImageView.PLAYBACKSTATE_DISABLED);
                    mPlayPauseStopButton.setMode(PlayPauseStopImageView.MODE_PAUSE);
                    mPlayPauseStopButton.refreshDrawableState();
                    mInCall = true;
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    if (mInCall) {
                        PlaybackState playbackState = mMediaPlaybackModel.getPlaybackState();
                        if (playbackState != null) {
                            mPlayPauseStopButton.setPlayState(playbackState.getState());
                            mPlayPauseStopButton.setMode((
                                    (playbackState.getActions() & PlaybackState.ACTION_STOP) != 0) ?
                                    PlayPauseStopImageView.MODE_STOP :
                                    PlayPauseStopImageView.MODE_PAUSE);
                            mPlayPauseStopButton.refreshDrawableState();
                        }
                        mInCall = false;
                    }
                    break;
                default:
                    Log.w(TAG, "TelephonyManager reports an unknown call state: " + state);
            }
        }
    };

    private final Runnable mSeekBarRunnable = new Runnable() {
        @Override
        public void run() {
            mSeekBar.setProgress((int) (System.currentTimeMillis() - mStartTime + mStartProgress));
            mHandler.postDelayed(this, SEEK_BAR_UPDATE_TIME_INTERVAL_MS);
        }
    };

    private final Runnable mCloseOverflowRunnable = new Runnable() {
        @Override
        public void run() {
            setOverflowMenuVisibility(false);
        }
    };

    private final Runnable mShowNoContentViewRunnable = new Runnable() {
        @Override
        public void run() {
            showInitialNoContentView(getString(R.string.nothing_to_play), false);
        }
    };

    private final Runnable mResetTitleRunnable = new Runnable() {
        @Override
        public void run() {
            mDelayedResetTitleInProgress = false;
            resetTitle();
        }
    };

    private final Runnable mSetTitleRunnable = new Runnable() {
        @Override
        public void run() {
            mTitleView.setText(mCurrentTrack.getTitle());
            mArtistView.setText(mCurrentTrack.getSubtitle());
        }
    };
}
