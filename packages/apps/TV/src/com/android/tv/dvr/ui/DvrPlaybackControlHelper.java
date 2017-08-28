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
 * limitations under the License
 */

package com.android.tv.dvr.ui;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaController.TransportControls;
import android.media.session.PlaybackState;
import android.support.v17.leanback.app.PlaybackControlGlue;
import android.support.v17.leanback.widget.AbstractDetailsDescriptionPresenter;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.PlaybackControlsRow;
import android.support.v17.leanback.widget.PlaybackControlsRowPresenter;
import android.support.v17.leanback.widget.RowPresenter;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import com.android.tv.R;
import com.android.tv.util.TimeShiftUtils;

/**
 * A helper class to assist {@link DvrPlaybackOverlayFragment} to manage its controls row and
 * send command to the media controller. It also helps to update playback states displayed in the
 * fragment according to information the media session provides.
 */
public class DvrPlaybackControlHelper extends PlaybackControlGlue {
    private static final String TAG = "DvrPlaybackControlHelper";
    private static final boolean DEBUG = false;

    /**
     * Indicates the ID of the media under playback is unknown.
     */
    public static int UNKNOWN_MEDIA_ID = -1;

    private int mPlaybackState = PlaybackState.STATE_NONE;
    private int mPlaybackSpeedLevel;
    private int mPlaybackSpeedId;
    private boolean mReadyToControl;

    private final MediaController mMediaController;
    private final MediaController.Callback mMediaControllerCallback = new MediaControllerCallback();
    private final TransportControls mTransportControls;
    private final int mExtraPaddingTopForNoDescription;

    public DvrPlaybackControlHelper(Activity activity, DvrPlaybackOverlayFragment overlayFragment) {
        super(activity, overlayFragment, new int[TimeShiftUtils.MAX_SPEED_LEVEL + 1]);
        mMediaController = activity.getMediaController();
        mMediaController.registerCallback(mMediaControllerCallback);
        mTransportControls = mMediaController.getTransportControls();
        mExtraPaddingTopForNoDescription = activity.getResources()
                .getDimensionPixelOffset(R.dimen.dvr_playback_controls_extra_padding_top);
    }

    @Override
    public PlaybackControlsRowPresenter createControlsRowAndPresenter() {
        PlaybackControlsRow controlsRow = new PlaybackControlsRow(this);
        setControlsRow(controlsRow);
        AbstractDetailsDescriptionPresenter detailsPresenter =
                new AbstractDetailsDescriptionPresenter() {
            @Override
            protected void onBindDescription(
                    AbstractDetailsDescriptionPresenter.ViewHolder viewHolder, Object object) {
                PlaybackControlGlue glue = (PlaybackControlGlue) object;
                if (glue.hasValidMedia()) {
                    viewHolder.getTitle().setText(glue.getMediaTitle());
                    viewHolder.getSubtitle().setText(glue.getMediaSubtitle());
                } else {
                    viewHolder.getTitle().setText("");
                    viewHolder.getSubtitle().setText("");
                }
                if (TextUtils.isEmpty(viewHolder.getSubtitle().getText())) {
                    viewHolder.view.setPadding(viewHolder.view.getPaddingLeft(),
                            mExtraPaddingTopForNoDescription,
                            viewHolder.view.getPaddingRight(), viewHolder.view.getPaddingBottom());
                }
            }
        };
        PlaybackControlsRowPresenter presenter =
                new PlaybackControlsRowPresenter(detailsPresenter) {
            @Override
            protected void onBindRowViewHolder(RowPresenter.ViewHolder vh, Object item) {
                super.onBindRowViewHolder(vh, item);
                vh.setOnKeyListener(DvrPlaybackControlHelper.this);
            }

            @Override
            protected void onUnbindRowViewHolder(RowPresenter.ViewHolder vh) {
                super.onUnbindRowViewHolder(vh);
                vh.setOnKeyListener(null);
            }
        };
        presenter.setProgressColor(getContext().getResources()
                .getColor(R.color.play_controls_progress_bar_watched));
        presenter.setBackgroundColor(getContext().getResources()
                .getColor(R.color.play_controls_body_background_enabled));
        presenter.setOnActionClickedListener(new OnActionClickedListener() {
            @Override
            public void onActionClicked(Action action) {
                if (mReadyToControl) {
                    DvrPlaybackControlHelper.super.onActionClicked(action);
                }
            }
        });
        return presenter;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (mReadyToControl) {
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE && event.getAction() == KeyEvent.ACTION_DOWN
                    && (mPlaybackState == PlaybackState.STATE_FAST_FORWARDING
                    || mPlaybackState == PlaybackState.STATE_REWINDING)) {
                // Workaround of b/31489271. Clicks play/pause button first to reset play controls
                // to "play" state. Then we can pass MEDIA_PAUSE to let playback be paused.
                onActionClicked(getControlsRow().getActionForKeyCode(keyCode));
            }
            return super.onKey(v, keyCode, event);
        }
        return false;
    }

    @Override
    public boolean hasValidMedia() {
        PlaybackState playbackState = mMediaController.getPlaybackState();
        return playbackState != null;
    }

    @Override
    public boolean isMediaPlaying() {
        PlaybackState playbackState = mMediaController.getPlaybackState();
        if (playbackState == null) {
            return false;
        }
        int state = playbackState.getState();
        return state != PlaybackState.STATE_NONE && state != PlaybackState.STATE_CONNECTING
                && state != PlaybackState.STATE_PAUSED;
    }

    /**
     * Returns the ID of the media under playback.
     */
    public long getMediaId() {
        MediaMetadata mediaMetadata = mMediaController.getMetadata();
        return mediaMetadata == null ? UNKNOWN_MEDIA_ID
                : mediaMetadata.getLong(MediaMetadata.METADATA_KEY_MEDIA_ID);
    }

    @Override
    public CharSequence getMediaTitle() {
        MediaMetadata mediaMetadata = mMediaController.getMetadata();
        return mediaMetadata == null ? ""
                : mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE);
    }

    @Override
    public CharSequence getMediaSubtitle() {
        MediaMetadata mediaMetadata = mMediaController.getMetadata();
        return mediaMetadata == null ? ""
                : mediaMetadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
    }

    @Override
    public int getMediaDuration() {
        MediaMetadata mediaMetadata = mMediaController.getMetadata();
        return mediaMetadata == null ? 0
                : (int) mediaMetadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
    }

    @Override
    public Drawable getMediaArt() {
        // Do not show the poster art on control row.
        return null;
    }

    @Override
    public long getSupportedActions() {
        return ACTION_PLAY_PAUSE | ACTION_FAST_FORWARD | ACTION_REWIND;
    }

    @Override
    public int getCurrentSpeedId() {
        return mPlaybackSpeedId;
    }

    @Override
    public int getCurrentPosition() {
        PlaybackState playbackState = mMediaController.getPlaybackState();
        if (playbackState == null) {
            return 0;
        }
        return (int) playbackState.getPosition();
    }

    /**
     * Unregister media controller's callback.
     */
    public void unregisterCallback() {
        mMediaController.unregisterCallback(mMediaControllerCallback);
    }

    @Override
    protected void startPlayback(int speedId) {
        if (getCurrentSpeedId() == speedId) {
            return;
        }
        if (speedId == PLAYBACK_SPEED_NORMAL) {
            mTransportControls.play();
        } else if (speedId <= -PLAYBACK_SPEED_FAST_L0) {
            mTransportControls.rewind();
        } else if (speedId >= PLAYBACK_SPEED_FAST_L0){
            mTransportControls.fastForward();
        }
    }

    @Override
    protected void pausePlayback() {
        mTransportControls.pause();
    }

    @Override
    protected void skipToNext() {
        // Do nothing.
    }

    @Override
    protected void skipToPrevious() {
        // Do nothing.
    }

    @Override
    protected void onRowChanged(PlaybackControlsRow row) {
        // Do nothing.
    }

    private void onStateChanged(int state, long positionMs, int speedLevel) {
        if (DEBUG) Log.d(TAG, "onStateChanged");
        getControlsRow().setCurrentTime((int) positionMs);
        if (state == mPlaybackState && mPlaybackSpeedLevel == speedLevel) {
            // Only position is changed, no need to update controls row
            return;
        }
        // NOTICE: The below two variables should only be used in this method.
        // The only usage of them is to confirm if the state is changed or not.
        mPlaybackState = state;
        mPlaybackSpeedLevel = speedLevel;
        switch (state) {
            case PlaybackState.STATE_PLAYING:
                mPlaybackSpeedId = PLAYBACK_SPEED_NORMAL;
                setFadingEnabled(true);
                mReadyToControl = true;
                break;
            case PlaybackState.STATE_PAUSED:
                mPlaybackSpeedId = PLAYBACK_SPEED_PAUSED;
                setFadingEnabled(true);
                mReadyToControl = true;
                break;
            case PlaybackState.STATE_FAST_FORWARDING:
                mPlaybackSpeedId = PLAYBACK_SPEED_FAST_L0 + speedLevel;
                setFadingEnabled(false);
                mReadyToControl = true;
                break;
            case PlaybackState.STATE_REWINDING:
                mPlaybackSpeedId = -PLAYBACK_SPEED_FAST_L0 - speedLevel;
                setFadingEnabled(false);
                mReadyToControl = true;
                break;
            case PlaybackState.STATE_CONNECTING:
                setFadingEnabled(false);
                mReadyToControl = false;
                break;
            case PlaybackState.STATE_NONE:
                mReadyToControl = false;
                break;
            default:
                setFadingEnabled(true);
                break;
        }
        onStateChanged();
    }

    private class MediaControllerCallback extends MediaController.Callback {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            if (DEBUG) Log.d(TAG, "Playback state changed: " + state.getState());
            onStateChanged(state.getState(), state.getPosition(), (int) state.getPlaybackSpeed());
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            DvrPlaybackControlHelper.this.onMetadataChanged();
            ((DvrPlaybackOverlayFragment) getFragment()).onMediaControllerUpdated();
        }
    }
}