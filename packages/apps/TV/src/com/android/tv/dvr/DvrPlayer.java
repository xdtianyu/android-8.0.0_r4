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

package com.android.tv.dvr;

import android.media.PlaybackParams;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.media.session.PlaybackState;
import android.util.Log;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class DvrPlayer {
    private static final String TAG = "DvrPlayer";
    private static final boolean DEBUG = false;

    /**
     * The max rewinding speed supported by DVR player.
     */
    public static final int MAX_REWIND_SPEED = 256;
    /**
     * The max fast-forwarding speed supported by DVR player.
     */
    public static final int MAX_FAST_FORWARD_SPEED = 256;

    private static final long SEEK_POSITION_MARGIN_MS = TimeUnit.SECONDS.toMillis(2);
    private static final long REWIND_POSITION_MARGIN_MS = 32;  // Workaround value. b/29994826

    private RecordedProgram mProgram;
    private long mInitialSeekPositionMs;
    private final TvView mTvView;
    private DvrPlayerCallback mCallback;
    private AspectRatioChangedListener mAspectRatioChangedListener;
    private ContentBlockedListener mContentBlockedListener;
    private float mAspectRatio = Float.NaN;
    private int mPlaybackState = PlaybackState.STATE_NONE;
    private long mTimeShiftCurrentPositionMs;
    private boolean mPauseOnPrepared;
    private final PlaybackParams mPlaybackParams = new PlaybackParams();
    private final DvrPlayerCallback mEmptyCallback = new DvrPlayerCallback();
    private long mStartPositionMs = TvInputManager.TIME_SHIFT_INVALID_TIME;
    private boolean mTimeShiftPlayAvailable;

    public static class DvrPlayerCallback {
        /**
         * Called when the playback position is changed. The normal updating frequency is
         * around 1 sec., which is restricted to the implementation of
         * {@link android.media.tv.TvInputService}.
         */
        public void onPlaybackPositionChanged(long positionMs) { }
        /**
         * Called when the playback state or the playback speed is changed.
         */
        public void onPlaybackStateChanged(int playbackState, int playbackSpeed) { }
        /**
         * Called when the playback toward the end.
         */
        public void onPlaybackEnded() { }
    }

    public interface AspectRatioChangedListener {
        /**
         * Called when the Video's aspect ratio is changed.
         */
        void onAspectRatioChanged(float videoAspectRatio);
    }

    public interface ContentBlockedListener {
        /**
         * Called when the Video's aspect ratio is changed.
         */
        void onContentBlocked(TvContentRating rating);
    }

    public DvrPlayer(TvView tvView) {
        mTvView = tvView;
        mPlaybackParams.setSpeed(1.0f);
        setTvViewCallbacks();
        setCallback(null);
    }

    /**
     * Prepares playback.
     *
     * @param doPlay indicates DVR player do or do not start playback after media is prepared.
     */
    public void prepare(boolean doPlay) throws IllegalStateException {
        if (DEBUG) Log.d(TAG, "prepare()");
        if (mProgram == null) {
            throw new IllegalStateException("Recorded program not set");
        } else if (mPlaybackState != PlaybackState.STATE_NONE) {
            throw new IllegalStateException("Playback is already prepared");
        }
        mTvView.timeShiftPlay(mProgram.getInputId(), mProgram.getUri());
        mPlaybackState = PlaybackState.STATE_CONNECTING;
        mPauseOnPrepared = !doPlay;
        mCallback.onPlaybackStateChanged(mPlaybackState, 1);
    }

    /**
     * Resumes playback.
     */
    public void play() throws IllegalStateException {
        if (DEBUG) Log.d(TAG, "play()");
        if (!isPlaybackPrepared()) {
            throw new IllegalStateException("Recorded program not set or video not ready yet");
        }
        switch (mPlaybackState) {
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
                setPlaybackSpeed(1);
                break;
            default:
                mTvView.timeShiftResume();
        }
        mPlaybackState = PlaybackState.STATE_PLAYING;
        mCallback.onPlaybackStateChanged(mPlaybackState, 1);
    }

    /**
     * Pauses playback.
     */
    public void pause() throws IllegalStateException {
        if (DEBUG) Log.d(TAG, "pause()");
        if (!isPlaybackPrepared()) {
            throw new IllegalStateException("Recorded program not set or playback not started yet");
        }
        switch (mPlaybackState) {
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
                setPlaybackSpeed(1);
                // falls through
            case PlaybackState.STATE_PLAYING:
                mTvView.timeShiftPause();
                mPlaybackState = PlaybackState.STATE_PAUSED;
                break;
            default:
                break;
        }
        mCallback.onPlaybackStateChanged(mPlaybackState, 1);
    }

    /**
     * Fast-forwards playback with the given speed. If the given speed is larger than
     * {@value #MAX_FAST_FORWARD_SPEED}, uses {@value #MAX_FAST_FORWARD_SPEED}.
     */
    public void fastForward(int speed) throws IllegalStateException {
        if (DEBUG) Log.d(TAG, "fastForward()");
        if (!isPlaybackPrepared()) {
            throw new IllegalStateException("Recorded program not set or playback not started yet");
        }
        if (speed <= 0) {
            throw new IllegalArgumentException("Speed cannot be negative or 0");
        }
        if (mTimeShiftCurrentPositionMs >= mProgram.getDurationMillis() - SEEK_POSITION_MARGIN_MS) {
            return;
        }
        speed = Math.min(speed, MAX_FAST_FORWARD_SPEED);
        if (DEBUG) Log.d(TAG, "Let's play with speed: " + speed);
        setPlaybackSpeed(speed);
        mPlaybackState = PlaybackState.STATE_FAST_FORWARDING;
        mCallback.onPlaybackStateChanged(mPlaybackState, speed);
    }

    /**
     * Rewinds playback with the given speed. If the given speed is larger than
     * {@value #MAX_REWIND_SPEED}, uses {@value #MAX_REWIND_SPEED}.
     */
    public void rewind(int speed) throws IllegalStateException {
        if (DEBUG) Log.d(TAG, "rewind()");
        if (!isPlaybackPrepared()) {
            throw new IllegalStateException("Recorded program not set or playback not started yet");
        }
        if (speed <= 0) {
            throw new IllegalArgumentException("Speed cannot be negative or 0");
        }
        if (mTimeShiftCurrentPositionMs <= REWIND_POSITION_MARGIN_MS) {
            return;
        }
        speed = Math.min(speed, MAX_REWIND_SPEED);
        if (DEBUG) Log.d(TAG, "Let's play with speed: " + speed);
        setPlaybackSpeed(-speed);
        mPlaybackState = PlaybackState.STATE_REWINDING;
        mCallback.onPlaybackStateChanged(mPlaybackState, speed);
    }

    /**
     * Seeks playback to the specified position.
     */
    public void seekTo(long positionMs) throws IllegalStateException {
        if (DEBUG) Log.d(TAG, "seekTo()");
        if (!isPlaybackPrepared()) {
            throw new IllegalStateException("Recorded program not set or playback not started yet");
        }
        if (mProgram == null || mPlaybackState == PlaybackState.STATE_NONE) {
            return;
        }
        positionMs = getRealSeekPosition(positionMs, SEEK_POSITION_MARGIN_MS);
        if (DEBUG) Log.d(TAG, "Now: " + getPlaybackPosition() + ", shift to: " + positionMs);
        mTvView.timeShiftSeekTo(positionMs + mStartPositionMs);
        if (mPlaybackState == PlaybackState.STATE_FAST_FORWARDING ||
                mPlaybackState == PlaybackState.STATE_REWINDING) {
            mPlaybackState = PlaybackState.STATE_PLAYING;
            mTvView.timeShiftResume();
            mCallback.onPlaybackStateChanged(mPlaybackState, 1);
        }
    }

    /**
     * Resets playback.
     */
    public void reset() {
        if (DEBUG) Log.d(TAG, "reset()");
        mCallback.onPlaybackStateChanged(PlaybackState.STATE_NONE, 1);
        mPlaybackState = PlaybackState.STATE_NONE;
        mTvView.reset();
        mTimeShiftPlayAvailable = false;
        mStartPositionMs = TvInputManager.TIME_SHIFT_INVALID_TIME;
        mTimeShiftCurrentPositionMs = 0;
        mPlaybackParams.setSpeed(1.0f);
        mProgram = null;
    }

    /**
     * Sets callbacks for playback.
     */
    public void setCallback(DvrPlayerCallback callback) {
        if (callback != null) {
            mCallback = callback;
        } else {
            mCallback = mEmptyCallback;
        }
    }

    /**
     * Sets listener to aspect ratio changing.
     */
    public void setAspectRatioChangedListener(AspectRatioChangedListener listener) {
        mAspectRatioChangedListener = listener;
    }

    /**
     * Sets listener to content blocking.
     */
    public void setContentBlockedListener(ContentBlockedListener listener) {
        mContentBlockedListener = listener;
    }

    /**
     * Sets recorded programs for playback. If the player is playing another program, stops it.
     */
    public void setProgram(RecordedProgram program, long initialSeekPositionMs) {
        if (mProgram != null && mProgram.equals(program)) {
            return;
        }
        if (mPlaybackState != PlaybackState.STATE_NONE) {
            reset();
        }
        mInitialSeekPositionMs = initialSeekPositionMs;
        mProgram = program;
    }

    /**
     * Returns the recorded program now playing.
     */
    public RecordedProgram getProgram() {
        return mProgram;
    }

    /**
     * Returns the currrent playback posistion in msecs.
     */
    public long getPlaybackPosition() {
        return mTimeShiftCurrentPositionMs;
    }

    /**
     * Returns the playback speed currently used.
     */
    public int getPlaybackSpeed() {
        return (int) mPlaybackParams.getSpeed();
    }

    /**
     * Returns the playback state defined in {@link android.media.session.PlaybackState}.
     */
    public int getPlaybackState() {
        return mPlaybackState;
    }

    /**
     * Returns if playback of the recorded program is started.
     */
    public boolean isPlaybackPrepared() {
        return mPlaybackState != PlaybackState.STATE_NONE
                && mPlaybackState != PlaybackState.STATE_CONNECTING;
    }

    private void setPlaybackSpeed(int speed) {
        mPlaybackParams.setSpeed(speed);
        mTvView.timeShiftSetPlaybackParams(mPlaybackParams);
    }

    private long getRealSeekPosition(long seekPositionMs, long endMarginMs) {
        return Math.max(0, Math.min(seekPositionMs, mProgram.getDurationMillis() - endMarginMs));
    }

    private void setTvViewCallbacks() {
        mTvView.setTimeShiftPositionCallback(new TvView.TimeShiftPositionCallback() {
            @Override
            public void onTimeShiftStartPositionChanged(String inputId, long timeMs) {
                if (DEBUG) Log.d(TAG, "onTimeShiftStartPositionChanged:" + timeMs);
                mStartPositionMs = timeMs;
                if (mTimeShiftPlayAvailable) {
                    resumeToWatchedPositionIfNeeded();
                }
            }

            @Override
            public void onTimeShiftCurrentPositionChanged(String inputId, long timeMs) {
                if (DEBUG) Log.d(TAG, "onTimeShiftCurrentPositionChanged: " + timeMs);
                if (!mTimeShiftPlayAvailable) {
                    // Workaround of b/31436263
                    return;
                }
                // Workaround of b/32211561, TIF won't report start position when TIS report
                // its start position as 0. In that case, we have to do the prework of playback
                // on the first time we get current position, and the start position should be 0
                // at that time.
                if (mStartPositionMs == TvInputManager.TIME_SHIFT_INVALID_TIME) {
                    mStartPositionMs = 0;
                    resumeToWatchedPositionIfNeeded();
                }
                timeMs -= mStartPositionMs;
                if (mPlaybackState == PlaybackState.STATE_REWINDING
                        && timeMs <= REWIND_POSITION_MARGIN_MS) {
                    play();
                } else {
                    mTimeShiftCurrentPositionMs = getRealSeekPosition(timeMs, 0);
                    mCallback.onPlaybackPositionChanged(mTimeShiftCurrentPositionMs);
                    if (timeMs >= mProgram.getDurationMillis()) {
                        pause();
                        mCallback.onPlaybackEnded();
                    }
                }
            }
        });
        mTvView.setCallback(new TvView.TvInputCallback() {
            @Override
            public void onTimeShiftStatusChanged(String inputId, int status) {
                if (DEBUG) Log.d(TAG, "onTimeShiftStatusChanged:" + status);
                if (status == TvInputManager.TIME_SHIFT_STATUS_AVAILABLE
                        && mPlaybackState == PlaybackState.STATE_CONNECTING) {
                    mTimeShiftPlayAvailable = true;
                }
            }

            @Override
            public void onTrackSelected(String inputId, int type, String trackId) {
                if (trackId == null || type != TvTrackInfo.TYPE_VIDEO
                        || mAspectRatioChangedListener == null) {
                    return;
                }
                List<TvTrackInfo> trackInfos = mTvView.getTracks(TvTrackInfo.TYPE_VIDEO);
                if (trackInfos != null) {
                    for (TvTrackInfo trackInfo : trackInfos) {
                        if (trackInfo.getId().equals(trackId)) {
                            float videoAspectRatio = trackInfo.getVideoPixelAspectRatio()
                                    * trackInfo.getVideoWidth() / trackInfo.getVideoHeight();
                            if (DEBUG) Log.d(TAG, "Aspect Ratio: " + videoAspectRatio);
                            if (!Float.isNaN(videoAspectRatio)
                                    && mAspectRatio != videoAspectRatio) {
                                mAspectRatioChangedListener
                                        .onAspectRatioChanged(videoAspectRatio);
                                mAspectRatio = videoAspectRatio;
                                return;
                            }
                        }
                    }
                }
            }

            @Override
            public void onContentBlocked(String inputId, TvContentRating rating) {
                if (mContentBlockedListener != null) {
                    mContentBlockedListener.onContentBlocked(rating);
                }
            }
        });
    }

    private void resumeToWatchedPositionIfNeeded() {
        if (mInitialSeekPositionMs != TvInputManager.TIME_SHIFT_INVALID_TIME) {
            mTvView.timeShiftSeekTo(getRealSeekPosition(mInitialSeekPositionMs,
                    SEEK_POSITION_MARGIN_MS) + mStartPositionMs);
            mInitialSeekPositionMs = TvInputManager.TIME_SHIFT_INVALID_TIME;
        }
        if (mPauseOnPrepared) {
            mTvView.timeShiftPause();
            mPlaybackState = PlaybackState.STATE_PAUSED;
            mPauseOnPrepared = false;
        } else {
            mTvView.timeShiftResume();
            mPlaybackState = PlaybackState.STATE_PLAYING;
        }
        mCallback.onPlaybackStateChanged(mPlaybackState, 1);
    }
}