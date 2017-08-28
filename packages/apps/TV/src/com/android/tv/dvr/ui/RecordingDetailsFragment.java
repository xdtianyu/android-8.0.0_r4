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

import android.os.Bundle;
import android.support.v17.leanback.app.DetailsFragment;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.data.Channel;
import com.android.tv.dvr.ScheduledRecording;

/**
 * {@link DetailsFragment} for recordings in DVR.
 */
abstract class RecordingDetailsFragment extends DvrDetailsFragment {
    private ScheduledRecording mRecording;

    @Override
    protected void onCreateInternal() {
        setDetailsOverviewRow(createDetailsContent());
    }

    @Override
    protected boolean onLoadRecordingDetails(Bundle args) {
        long scheduledRecordingId = args.getLong(DvrDetailsActivity.RECORDING_ID);
        mRecording = TvApplication.getSingletons(getContext()).getDvrDataManager()
                .getScheduledRecording(scheduledRecordingId);
        return mRecording != null;
    }

    /**
     * Returns {@link ScheduledRecording} for the current fragment.
     */
    public ScheduledRecording getRecording() {
        return mRecording;
    }

    private DetailsContent createDetailsContent() {
        Channel channel = TvApplication.getSingletons(getContext()).getChannelDataManager()
                .getChannel(mRecording.getChannelId());
        SpannableString title = mRecording.getProgramTitleWithEpisodeNumber(getContext()) == null ?
                null : new SpannableString(mRecording
                .getProgramTitleWithEpisodeNumber(getContext()));
        if (TextUtils.isEmpty(title)) {
            title = new SpannableString(channel != null ? channel.getDisplayName()
                    : getContext().getResources().getString(
                    R.string.no_program_information));
        } else {
            String programTitle = mRecording.getProgramTitle();
            title.setSpan(new TextAppearanceSpan(getContext(),
                    R.style.text_appearance_card_view_episode_number), programTitle == null ? 0
                    : programTitle.length(), title.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        String description = !TextUtils.isEmpty(mRecording.getProgramDescription()) ?
                mRecording.getProgramDescription() :  mRecording.getProgramLongDescription();
        if (TextUtils.isEmpty(description)) {
            description = channel != null ? channel.getDescription() : null;
        }
        return new DetailsContent.Builder()
                .setTitle(title)
                .setStartTimeUtcMillis(mRecording.getStartTimeMs())
                .setEndTimeUtcMillis(mRecording.getEndTimeMs())
                .setDescription(description)
                .setImageUris(mRecording.getProgramPosterArtUri(),
                        mRecording.getProgramThumbnailUri(), channel)
                .build();
    }
}
