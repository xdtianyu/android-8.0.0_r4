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

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.media.tv.TvContentRating;
import android.os.Bundle;
import android.media.session.PlaybackState;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.support.v17.leanback.app.PlaybackOverlayFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.PlaybackControlsRow;
import android.support.v17.leanback.widget.PlaybackControlsRowPresenter;
import android.support.v17.leanback.widget.SinglePresenterSelector;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.data.BaseProgram;
import com.android.tv.dvr.RecordedProgram;
import com.android.tv.dialog.PinDialogFragment;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrPlayer;
import com.android.tv.dvr.DvrPlaybackMediaSessionHelper;
import com.android.tv.parental.ContentRatingsManager;
import com.android.tv.util.Utils;

public class DvrPlaybackOverlayFragment extends PlaybackOverlayFragment {
    // TODO: Handles audio focus. Deals with block and ratings.
    private static final String TAG = "DvrPlaybackOverlayFragment";
    private static final boolean DEBUG = false;

    private static final String MEDIA_SESSION_TAG = "com.android.tv.dvr.mediasession";
    private static final float DISPLAY_ASPECT_RATIO_EPSILON = 0.01f;

    // mProgram is only used to store program from intent. Don't use it elsewhere.
    private RecordedProgram mProgram;
    private DvrPlaybackMediaSessionHelper mMediaSessionHelper;
    private DvrPlaybackControlHelper mPlaybackControlHelper;
    private ArrayObjectAdapter mRowsAdapter;
    private SortedArrayAdapter<BaseProgram> mRelatedRecordingsRowAdapter;
    private DvrPlaybackCardPresenter mRelatedRecordingCardPresenter;
    private DvrDataManager mDvrDataManager;
    private ContentRatingsManager mContentRatingsManager;
    private TvView mTvView;
    private View mBlockScreenView;
    private ListRow mRelatedRecordingsRow;
    private int mExtraPaddingNoRelatedRow;
    private int mWindowWidth;
    private int mWindowHeight;
    private float mAppliedAspectRatio;
    private float mWindowAspectRatio;
    private boolean mPinChecked;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        mExtraPaddingNoRelatedRow = getActivity().getResources()
                .getDimensionPixelOffset(R.dimen.dvr_playback_fragment_extra_padding_top);
        mDvrDataManager = TvApplication.getSingletons(getActivity()).getDvrDataManager();
        mContentRatingsManager = TvApplication.getSingletons(getContext())
                .getTvInputManagerHelper().getContentRatingsManager();
        mProgram = getProgramFromIntent(getActivity().getIntent());
        if (mProgram == null) {
            Toast.makeText(getActivity(), getString(R.string.dvr_program_not_found),
                    Toast.LENGTH_SHORT).show();
            getActivity().finish();
            return;
        }
        Point size = new Point();
        ((DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE))
                .getDisplay(Display.DEFAULT_DISPLAY).getSize(size);
        mWindowWidth = size.x;
        mWindowHeight = size.y;
        mWindowAspectRatio = mAppliedAspectRatio = (float) mWindowWidth / mWindowHeight;
        setBackgroundType(PlaybackOverlayFragment.BG_LIGHT);
        setFadingEnabled(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mTvView = (TvView) getActivity().findViewById(R.id.dvr_tv_view);
        mBlockScreenView = getActivity().findViewById(R.id.block_screen);
        mMediaSessionHelper = new DvrPlaybackMediaSessionHelper(
                getActivity(), MEDIA_SESSION_TAG, new DvrPlayer(mTvView), this);
        mPlaybackControlHelper = new DvrPlaybackControlHelper(getActivity(), this);
        setUpRows();
        preparePlayback(getActivity().getIntent());
        DvrPlayer dvrPlayer = mMediaSessionHelper.getDvrPlayer();
        dvrPlayer.setAspectRatioChangedListener(new DvrPlayer.AspectRatioChangedListener() {
            @Override
            public void onAspectRatioChanged(float videoAspectRatio) {
                updateAspectRatio(videoAspectRatio);
            }
        });
        mPinChecked = getActivity().getIntent()
                .getBooleanExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_PIN_CHECKED, false);
        dvrPlayer.setContentBlockedListener(new DvrPlayer.ContentBlockedListener() {
            @Override
            public void onContentBlocked(TvContentRating rating) {
                if (mPinChecked) {
                    mTvView.unblockContent(rating);
                    return;
                }
                mBlockScreenView.setVisibility(View.VISIBLE);
                getActivity().getMediaController().getTransportControls().pause();
                new PinDialogFragment(PinDialogFragment.PIN_DIALOG_TYPE_UNLOCK_DVR,
                        new PinDialogFragment.ResultListener() {
                            @Override
                            public void done(boolean success) {
                                if (success) {
                                    mPinChecked = true;
                                    mTvView.unblockContent(rating);
                                    mBlockScreenView.setVisibility(View.GONE);
                                    getActivity().getMediaController()
                                            .getTransportControls().play();
                                }
                            }
                        }, mContentRatingsManager.getDisplayNameForRating(rating))
                        .show(getActivity().getFragmentManager(), PinDialogFragment.DIALOG_TAG);
                }
            });
    }

    @Override
    public void onPause() {
        if (DEBUG) Log.d(TAG, "onPause");
        super.onPause();
        if (mMediaSessionHelper.getPlaybackState() == PlaybackState.STATE_FAST_FORWARDING
                || mMediaSessionHelper.getPlaybackState() == PlaybackState.STATE_REWINDING) {
            getActivity().getMediaController().getTransportControls().pause();
        }
        if (mMediaSessionHelper.getPlaybackState() == PlaybackState.STATE_NONE) {
            getActivity().requestVisibleBehind(false);
        } else {
            getActivity().requestVisibleBehind(true);
        }
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy");
        mPlaybackControlHelper.unregisterCallback();
        mMediaSessionHelper.release();
        mRelatedRecordingCardPresenter.unbindAllViewHolders();
        super.onDestroy();
    }

    /**
     * Passes the intent to the fragment.
     */
    public void onNewIntent(Intent intent) {
        mProgram = getProgramFromIntent(intent);
        if (mProgram == null) {
            Toast.makeText(getActivity(), getString(R.string.dvr_program_not_found),
                    Toast.LENGTH_SHORT).show();
            // Continue playing the original program
            return;
        }
        preparePlayback(intent);
    }

    /**
     * Should be called when windows' size is changed in order to notify DVR player
     * to update it's view width/height and position.
     */
    public void onWindowSizeChanged(final int windowWidth, final int windowHeight) {
        mWindowWidth = windowWidth;
        mWindowHeight = windowHeight;
        mWindowAspectRatio = (float) mWindowWidth / mWindowHeight;
        updateAspectRatio(mAppliedAspectRatio);
    }

    public RecordedProgram getNextEpisode(RecordedProgram program) {
        int position = mRelatedRecordingsRowAdapter.findInsertPosition(program);
        if (position == mRelatedRecordingsRowAdapter.size()) {
            return null;
        } else {
            return (RecordedProgram) mRelatedRecordingsRowAdapter.get(position);
        }
    }

    void onMediaControllerUpdated() {
        mRowsAdapter.notifyArrayItemRangeChanged(0, 1);
    }

    private void updateAspectRatio(float videoAspectRatio) {
        if (Math.abs(mAppliedAspectRatio - videoAspectRatio) < DISPLAY_ASPECT_RATIO_EPSILON) {
            // No need to change
            return;
        }
        if (videoAspectRatio < mWindowAspectRatio) {
            int newPadding = (mWindowWidth - Math.round(mWindowHeight * videoAspectRatio)) / 2;
            ((ViewGroup) mTvView.getParent()).setPadding(newPadding, 0, newPadding, 0);
        } else {
            int newPadding = (mWindowHeight - Math.round(mWindowWidth / videoAspectRatio)) / 2;
            ((ViewGroup) mTvView.getParent()).setPadding(0, newPadding, 0, newPadding);
        }
        mAppliedAspectRatio = videoAspectRatio;
    }

    private void preparePlayback(Intent intent) {
        mMediaSessionHelper.setupPlayback(mProgram, getSeekTimeFromIntent(intent));
        getActivity().getMediaController().getTransportControls().prepare();
        updateRelatedRecordingsRow();
    }

    private void updateRelatedRecordingsRow() {
        boolean wasEmpty = (mRelatedRecordingsRowAdapter.size() == 0);
        mRelatedRecordingsRowAdapter.clear();
        long programId = mProgram.getId();
        String seriesId = mProgram.getSeriesId();
        if (!TextUtils.isEmpty(seriesId)) {
            if (DEBUG) Log.d(TAG, "Update related recordings with:" + seriesId);
            for (RecordedProgram program : mDvrDataManager.getRecordedPrograms()) {
                if (seriesId.equals(program.getSeriesId()) && programId != program.getId()) {
                    mRelatedRecordingsRowAdapter.add(program);
                }
            }
        }
        View view = getView();
        if (mRelatedRecordingsRowAdapter.size() == 0) {
            mRowsAdapter.remove(mRelatedRecordingsRow);
            view.setPadding(view.getPaddingLeft(), mExtraPaddingNoRelatedRow,
                    view.getPaddingRight(), view.getPaddingBottom());
        } else if (wasEmpty){
            mRowsAdapter.add(mRelatedRecordingsRow);
            view.setPadding(view.getPaddingLeft(), 0,
                    view.getPaddingRight(), view.getPaddingBottom());
        }
    }

    private void setUpRows() {
        PlaybackControlsRowPresenter controlsRowPresenter =
                mPlaybackControlHelper.createControlsRowAndPresenter();

        ClassPresenterSelector selector = new ClassPresenterSelector();
        selector.addClassPresenter(PlaybackControlsRow.class, controlsRowPresenter);
        selector.addClassPresenter(ListRow.class, new ListRowPresenter());

        mRowsAdapter = new ArrayObjectAdapter(selector);
        mRowsAdapter.add(mPlaybackControlHelper.getControlsRow());
        mRelatedRecordingsRow = getRelatedRecordingsRow();
        setAdapter(mRowsAdapter);
    }

    private ListRow getRelatedRecordingsRow() {
        mRelatedRecordingCardPresenter = new DvrPlaybackCardPresenter(getActivity());
        mRelatedRecordingsRowAdapter = new RelatedRecordingsAdapter(mRelatedRecordingCardPresenter);
        HeaderItem header = new HeaderItem(0,
                getActivity().getString(R.string.dvr_playback_related_recordings));
        return new ListRow(header, mRelatedRecordingsRowAdapter);
    }

    private RecordedProgram getProgramFromIntent(Intent intent) {
        long programId = intent.getLongExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_ID, -1);
        return mDvrDataManager.getRecordedProgram(programId);
    }

    private long getSeekTimeFromIntent(Intent intent) {
        return intent.getLongExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_SEEK_TIME,
                TvInputManager.TIME_SHIFT_INVALID_TIME);
    }

    private class RelatedRecordingsAdapter extends SortedArrayAdapter<BaseProgram> {
        RelatedRecordingsAdapter(DvrPlaybackCardPresenter presenter) {
            super(new SinglePresenterSelector(presenter), BaseProgram.EPISODE_COMPARATOR);
        }

        @Override
        long getId(BaseProgram item) {
            return item.getId();
        }
    }
}