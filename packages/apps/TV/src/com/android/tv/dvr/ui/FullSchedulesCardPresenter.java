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

import android.content.Context;
import android.support.v17.leanback.widget.Presenter;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.dvr.DvrUiHelper;
import com.android.tv.dvr.ScheduledRecording;
import com.android.tv.util.Utils;

import java.util.Collections;
import java.util.List;

/**
 * Presents a {@link ScheduledRecording} in the {@link DvrBrowseFragment}.
 */
public class FullSchedulesCardPresenter extends Presenter {
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();
        RecordingCardView view = new RecordingCardView(context);
        return new ScheduledRecordingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder baseHolder, Object o) {
        final ScheduledRecordingViewHolder viewHolder = (ScheduledRecordingViewHolder) baseHolder;
        final RecordingCardView cardView = (RecordingCardView) viewHolder.view;
        final Context context = viewHolder.view.getContext();

        cardView.setImage(context.getDrawable(R.drawable.dvr_full_schedule));
        cardView.setTitle(context.getString(R.string.dvr_full_schedule_card_view_title));
        List<ScheduledRecording> scheduledRecordings = TvApplication.getSingletons(context)
                .getDvrDataManager().getAvailableScheduledRecordings();
        int fullDays = 0;
        if (!scheduledRecordings.isEmpty()) {
            fullDays = Utils.computeDateDifference(System.currentTimeMillis(),
                    Collections.max(scheduledRecordings, ScheduledRecording.START_TIME_COMPARATOR)
                    .getStartTimeMs()) + 1;
        }
        cardView.setContent(context.getResources().getQuantityString(
                R.plurals.dvr_full_schedule_card_view_content, fullDays, fullDays), null);

        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DvrUiHelper.startSchedulesActivity(context, null);
            }
        };
        baseHolder.view.setOnClickListener(clickListener);
    }

    @Override
    public void onUnbindViewHolder(ViewHolder baseHolder) {
        ScheduledRecordingViewHolder viewHolder = (ScheduledRecordingViewHolder) baseHolder;
        final RecordingCardView cardView = (RecordingCardView) viewHolder.view;
        cardView.reset();
    }

    private static final class ScheduledRecordingViewHolder extends ViewHolder {
        ScheduledRecordingViewHolder(RecordingCardView view) {
            super(view);
        }
    }
}
