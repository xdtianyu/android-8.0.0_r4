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
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.view.ViewGroup;

import com.android.tv.ApplicationSingletons;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.ScheduledRecording;
import com.android.tv.util.Utils;

import java.util.concurrent.TimeUnit;

/**
 * Presents a {@link ScheduledRecording} in the {@link DvrBrowseFragment}.
 */
public class ScheduledRecordingPresenter extends DvrItemPresenter {
    private static final long PROGRESS_UPDATE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(5);

    private final ChannelDataManager mChannelDataManager;
    private final DvrManager mDvrManager;
    private final Context mContext;
    private final int mProgressBarColor;

    private static final class ScheduledRecordingViewHolder extends ViewHolder {
        private final Handler mHandler = new Handler();
        private ScheduledRecording mScheduledRecording;
        private final Runnable mProgressBarUpdater = new Runnable() {
            @Override
            public void run() {
                updateProgressBar();
                mHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS);
            }
        };

        ScheduledRecordingViewHolder(RecordingCardView view, int progressBarColor) {
            super(view);
            view.setProgressBarColor(progressBarColor);
        }

        private void updateProgressBar() {
            if (mScheduledRecording == null) {
                return;
            }
            int recordingState = mScheduledRecording.getState();
            RecordingCardView cardView = (RecordingCardView) view;
            if (recordingState == ScheduledRecording.STATE_RECORDING_IN_PROGRESS) {
                cardView.setProgressBar(Math.max(0, Math.min((int) (100 *
                        (System.currentTimeMillis() - mScheduledRecording.getStartTimeMs())
                        / mScheduledRecording.getDuration()), 100)));
            } else if (recordingState == ScheduledRecording.STATE_RECORDING_FINISHED) {
                cardView.setProgressBar(100);
            } else {
                // Hides progress bar.
                cardView.setProgressBar(null);
            }
        }

        private void startUpdateProgressBar() {
            mHandler.post(mProgressBarUpdater);
        }

        private void stopUpdateProgressBar() {
            mHandler.removeCallbacks(mProgressBarUpdater);
        }
    }

    public ScheduledRecordingPresenter(Context context) {
        ApplicationSingletons singletons = TvApplication.getSingletons(context);
        mChannelDataManager = singletons.getChannelDataManager();
        mDvrManager = singletons.getDvrManager();
        mContext = context;
        mProgressBarColor = context.getResources()
                .getColor(R.color.play_controls_recording_icon_color_on_focus);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();
        RecordingCardView view = new RecordingCardView(context);
        return new ScheduledRecordingViewHolder(view, mProgressBarColor);
    }

    @Override
    public void onBindViewHolder(ViewHolder baseHolder, Object o) {
        final ScheduledRecordingViewHolder viewHolder = (ScheduledRecordingViewHolder) baseHolder;
        final ScheduledRecording recording = (ScheduledRecording) o;
        final RecordingCardView cardView = (RecordingCardView) viewHolder.view;
        final Context context = viewHolder.view.getContext();

        setTitleAndImage(cardView, recording);
        int dateDifference = Utils.computeDateDifference(System.currentTimeMillis(),
                recording.getStartTimeMs());
        if (dateDifference <= 0) {
            cardView.setContent(mContext.getString(R.string.dvr_date_today_time,
                    Utils.getDurationString(context, recording.getStartTimeMs(),
                            recording.getEndTimeMs(), false, false, true, 0)), null);
        } else if (dateDifference == 1) {
            cardView.setContent(mContext.getString(R.string.dvr_date_tomorrow_time,
                    Utils.getDurationString(context, recording.getStartTimeMs(),
                            recording.getEndTimeMs(), false, false, true, 0)), null);
        } else {
            cardView.setContent(Utils.getDurationString(context, recording.getStartTimeMs(),
                    recording.getStartTimeMs(), false, true, false, 0), null);
        }
        if (mDvrManager.isConflicting(recording)) {
            cardView.setAffiliatedIcon(R.drawable.ic_warning_white_32dp);
        } else {
            cardView.setAffiliatedIcon(0);
        }
        viewHolder.updateProgressBar();
        viewHolder.mScheduledRecording = recording;
        viewHolder.startUpdateProgressBar();
        super.onBindViewHolder(viewHolder, o);
    }

    @Override
    public void onUnbindViewHolder(ViewHolder baseHolder) {
        ScheduledRecordingViewHolder viewHolder = (ScheduledRecordingViewHolder) baseHolder;
        viewHolder.stopUpdateProgressBar();
        final RecordingCardView cardView = (RecordingCardView) viewHolder.view;
        viewHolder.mScheduledRecording = null;
        cardView.reset();
        super.onUnbindViewHolder(viewHolder);
    }

    private void setTitleAndImage(RecordingCardView cardView, ScheduledRecording recording) {
        Channel channel = mChannelDataManager.getChannel(recording.getChannelId());
        SpannableString title = recording.getProgramTitleWithEpisodeNumber(mContext) == null ?
                null : new SpannableString(recording.getProgramTitleWithEpisodeNumber(mContext));
        if (TextUtils.isEmpty(title)) {
            title = new SpannableString(channel != null ? channel.getDisplayName()
                    : mContext.getResources().getString(R.string.no_program_information));
        } else {
            String programTitle = recording.getProgramTitle();
            title.setSpan(new TextAppearanceSpan(mContext,
                    R.style.text_appearance_card_view_episode_number),
                    programTitle == null ? 0 : programTitle.length(), title.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        String imageUri = recording.getProgramPosterArtUri();
        boolean isChannelLogo = false;
        if (TextUtils.isEmpty(imageUri)) {
            imageUri = channel != null ?
                    TvContract.buildChannelLogoUri(channel.getId()).toString() : null;
            isChannelLogo = true;
        }
        cardView.setTitle(title);
        cardView.setImageUri(imageUri, isChannelLogo);
    }
}
