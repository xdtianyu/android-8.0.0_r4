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

package com.android.tv.dvr.ui;

import android.app.Activity;
import android.content.Context;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.dvr.RecordedProgram;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.dvr.DvrWatchedPositionManager;
import com.android.tv.dvr.DvrWatchedPositionManager.WatchedPositionChangedListener;
import com.android.tv.util.Utils;

import java.util.concurrent.TimeUnit;

/**
 * Presents a {@link RecordedProgram} in the {@link DvrBrowseFragment}.
 */
public class RecordedProgramPresenter extends DvrItemPresenter {
    private final ChannelDataManager mChannelDataManager;
    private final DvrWatchedPositionManager mDvrWatchedPositionManager;
    private final Context mContext;
    private String mTodayString;
    private String mYesterdayString;
    private final int mProgressBarColor;
    private final boolean mShowEpisodeTitle;

    private static final class RecordedProgramViewHolder extends ViewHolder
            implements WatchedPositionChangedListener {
        private RecordedProgram mProgram;

        RecordedProgramViewHolder(RecordingCardView view, int progressColor) {
            super(view);
            view.setProgressBarColor(progressColor);
        }

        private void setProgram(RecordedProgram program) {
            mProgram = program;
        }

        private void setProgressBar(long watchedPositionMs) {
            ((RecordingCardView) view).setProgressBar(
                    (watchedPositionMs == TvInputManager.TIME_SHIFT_INVALID_TIME) ? null
                            : Math.min(100, (int) (100.0f * watchedPositionMs
                                    / mProgram.getDurationMillis())));
        }

        @Override
        public void onWatchedPositionChanged(long programId, long positionMs) {
            if (programId == mProgram.getId()) {
                setProgressBar(positionMs);
            }
        }
    }

    public RecordedProgramPresenter(Context context, boolean showEpisodeTitle) {
        mContext = context;
        mChannelDataManager = TvApplication.getSingletons(context).getChannelDataManager();
        mTodayString = context.getString(R.string.dvr_date_today);
        mYesterdayString = context.getString(R.string.dvr_date_yesterday);
        mDvrWatchedPositionManager =
                TvApplication.getSingletons(context).getDvrWatchedPositionManager();
        mProgressBarColor = context.getResources()
                .getColor(R.color.play_controls_progress_bar_watched);
        mShowEpisodeTitle = showEpisodeTitle;
    }

    public RecordedProgramPresenter(Context context) {
        this(context, false);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        RecordingCardView view = new RecordingCardView(mContext);
        return new RecordedProgramViewHolder(view, mProgressBarColor);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object o) {
        final RecordedProgram program = (RecordedProgram) o;
        final RecordingCardView cardView = (RecordingCardView) viewHolder.view;
        Channel channel = mChannelDataManager.getChannel(program.getChannelId());
        String titleString = mShowEpisodeTitle ? program.getEpisodeDisplayTitle(mContext)
                : program.getTitleWithEpisodeNumber(mContext);
        SpannableString title = titleString == null ? null : new SpannableString(titleString);
        if (TextUtils.isEmpty(title)) {
            title = new SpannableString(channel != null ? channel.getDisplayName()
                    : mContext.getResources().getString(R.string.no_program_information));
        } else if (!mShowEpisodeTitle) {
            // TODO: Some translation may add delimiters in-between program titles, we should use
            // a more robust way to get the span range.
            String programTitle = program.getTitle();
            title.setSpan(new TextAppearanceSpan(mContext,
                    R.style.text_appearance_card_view_episode_number), programTitle == null ? 0
                    : programTitle.length(), title.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        cardView.setTitle(title);
        String imageUri = null;
        boolean isChannelLogo = false;
        if (program.getPosterArtUri() != null) {
            imageUri = program.getPosterArtUri();
        } else if (program.getThumbnailUri() != null) {
            imageUri = program.getThumbnailUri();
        } else if (channel != null) {
            imageUri = TvContract.buildChannelLogoUri(channel.getId()).toString();
            isChannelLogo = true;
        }
        cardView.setImageUri(imageUri, isChannelLogo);
        int durationMinutes =
                Math.max(1, (int) TimeUnit.MILLISECONDS.toMinutes(program.getDurationMillis()));
        String durationString = getContext().getResources().getQuantityString(
                R.plurals.dvr_program_duration, durationMinutes, durationMinutes);
        cardView.setContent(getDescription(program), durationString);
        if (viewHolder instanceof RecordedProgramViewHolder) {
            RecordedProgramViewHolder cardViewHolder = (RecordedProgramViewHolder) viewHolder;
            cardViewHolder.setProgram(program);
            mDvrWatchedPositionManager.addListener(cardViewHolder, program.getId());
            cardViewHolder
                    .setProgressBar(mDvrWatchedPositionManager.getWatchedPosition(program.getId()));
        }
        super.onBindViewHolder(viewHolder, o);
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        if (viewHolder instanceof RecordedProgramViewHolder) {
            mDvrWatchedPositionManager.removeListener((RecordedProgramViewHolder) viewHolder,
                    ((RecordedProgramViewHolder) viewHolder).mProgram.getId());
        }
        ((RecordingCardView) viewHolder.view).reset();
        super.onUnbindViewHolder(viewHolder);
    }

    /**
     * Returns description would be used in its card view.
     */
    protected String getDescription(RecordedProgram recording) {
        int dateDifference = Utils.computeDateDifference(recording.getStartTimeUtcMillis(),
                System.currentTimeMillis());
        if (dateDifference == 0) {
            return mTodayString;
        } else if (dateDifference == 1) {
            return mYesterdayString;
        } else {
            return Utils.getDurationString(mContext, recording.getStartTimeUtcMillis(),
                    recording.getStartTimeUtcMillis(), false, true, false, 0);
        }
    }

    /**
     * Returns context.
     */
    protected Context getContext() {
        return mContext;
    }
}
