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
import android.text.TextUtils;
import android.view.ViewGroup;

import com.android.tv.ApplicationSingletons;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrDataManager.RecordedProgramListener;
import com.android.tv.dvr.DvrDataManager.ScheduledRecordingListener;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.DvrWatchedPositionManager;
import com.android.tv.dvr.DvrWatchedPositionManager.WatchedPositionChangedListener;
import com.android.tv.dvr.RecordedProgram;
import com.android.tv.dvr.ScheduledRecording;
import com.android.tv.dvr.SeriesRecording;

import java.util.List;

/**
 * Presents a {@link SeriesRecording} in {@link DvrBrowseFragment}.
 */
public class SeriesRecordingPresenter extends DvrItemPresenter {
    private final ChannelDataManager mChannelDataManager;
    private final DvrDataManager mDvrDataManager;
    private final DvrManager mDvrManager;
    private final DvrWatchedPositionManager mWatchedPositionManager;

    private static final class SeriesRecordingViewHolder extends ViewHolder implements
            WatchedPositionChangedListener, ScheduledRecordingListener, RecordedProgramListener {
        private SeriesRecording mSeriesRecording;
        private RecordingCardView mCardView;
        private DvrDataManager mDvrDataManager;
        private DvrManager mDvrManager;
        private DvrWatchedPositionManager mWatchedPositionManager;

        SeriesRecordingViewHolder(RecordingCardView view, DvrDataManager dvrDataManager,
                DvrManager dvrManager, DvrWatchedPositionManager watchedPositionManager) {
            super(view);
            mCardView = view;
            mDvrDataManager = dvrDataManager;
            mDvrManager = dvrManager;
            mWatchedPositionManager = watchedPositionManager;
        }

        @Override
        public void onWatchedPositionChanged(long recordedProgramId, long positionMs) {
            if (positionMs != TvInputManager.TIME_SHIFT_INVALID_TIME) {
                mWatchedPositionManager.removeListener(this, recordedProgramId);
                updateCardViewContent();
            }
        }

        @Override
        public void onScheduledRecordingAdded(ScheduledRecording... scheduledRecordings) {
            for (ScheduledRecording scheduledRecording : scheduledRecordings) {
                if (scheduledRecording.getSeriesRecordingId() == mSeriesRecording.getId()) {
                    updateCardViewContent();
                    return;
                }
            }
        }

        @Override
        public void onScheduledRecordingRemoved(ScheduledRecording... scheduledRecordings) {
            for (ScheduledRecording scheduledRecording : scheduledRecordings) {
                if (scheduledRecording.getSeriesRecordingId() == mSeriesRecording.getId()) {
                    updateCardViewContent();
                    return;
                }
            }
        }

        @Override
        public void onRecordedProgramsAdded(RecordedProgram... recordedPrograms) {
            boolean needToUpdateCardView = false;
            for (RecordedProgram recordedProgram : recordedPrograms) {
                if (TextUtils.equals(recordedProgram.getSeriesId(),
                        mSeriesRecording.getSeriesId())) {
                    mDvrDataManager.removeScheduledRecordingListener(this);
                    mWatchedPositionManager.addListener(this, recordedProgram.getId());
                    needToUpdateCardView = true;
                }
            }
            if (needToUpdateCardView) {
                updateCardViewContent();
            }
        }

        @Override
        public void onRecordedProgramsRemoved(RecordedProgram... recordedPrograms) {
            boolean needToUpdateCardView = false;
            for (RecordedProgram recordedProgram : recordedPrograms) {
                if (TextUtils.equals(recordedProgram.getSeriesId(),
                        mSeriesRecording.getSeriesId())) {
                    if (mWatchedPositionManager.getWatchedPosition(recordedProgram.getId())
                            == TvInputManager.TIME_SHIFT_INVALID_TIME) {
                        mWatchedPositionManager.removeListener(this, recordedProgram.getId());
                    }
                    needToUpdateCardView = true;
                }
            }
            if (needToUpdateCardView) {
                updateCardViewContent();
            }
        }

        @Override
        public void onRecordedProgramsChanged(RecordedProgram... recordedPrograms) {
            // Do nothing
        }

        @Override
        public void onScheduledRecordingStatusChanged(ScheduledRecording... scheduledRecordings) {
            // Do nothing
        }

        public void onBound(SeriesRecording seriesRecording) {
            mSeriesRecording = seriesRecording;
            mDvrDataManager.addScheduledRecordingListener(this);
            mDvrDataManager.addRecordedProgramListener(this);
            for (RecordedProgram recordedProgram :
                    mDvrDataManager.getRecordedPrograms(mSeriesRecording.getId())) {
                if (mWatchedPositionManager.getWatchedPosition(recordedProgram.getId())
                        == TvInputManager.TIME_SHIFT_INVALID_TIME) {
                    mWatchedPositionManager.addListener(this, recordedProgram.getId());
                }
            }
            updateCardViewContent();
        }

        public void onUnbound() {
            mDvrDataManager.removeScheduledRecordingListener(this);
            mDvrDataManager.removeRecordedProgramListener(this);
            mWatchedPositionManager.removeListener(this);
        }

        private void updateCardViewContent() {
            int count = 0;
            int quantityStringID;
            List<RecordedProgram> recordedPrograms =
                    mDvrDataManager.getRecordedPrograms(mSeriesRecording.getId());
            if (recordedPrograms.size() == 0) {
                count = mDvrManager.getAvailableScheduledRecording(mSeriesRecording.getId()).size();
                quantityStringID = R.plurals.dvr_count_scheduled_recordings;
            } else {
                for (RecordedProgram recordedProgram : recordedPrograms) {
                    if (mWatchedPositionManager.getWatchedPosition(recordedProgram.getId())
                            == TvInputManager.TIME_SHIFT_INVALID_TIME) {
                        count++;
                    }
                }
                if (count == 0) {
                    count = recordedPrograms.size();
                    quantityStringID = R.plurals.dvr_count_recordings;
                } else {
                    quantityStringID = R.plurals.dvr_count_new_recordings;
                }
            }
            mCardView.setContent(mCardView.getResources()
                    .getQuantityString(quantityStringID, count, count), null);
        }
    }

    public SeriesRecordingPresenter(Context context) {
        ApplicationSingletons singletons = TvApplication.getSingletons(context);
        mChannelDataManager = singletons.getChannelDataManager();
        mDvrDataManager = singletons.getDvrDataManager();
        mDvrManager = singletons.getDvrManager();
        mWatchedPositionManager = singletons.getDvrWatchedPositionManager();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();
        RecordingCardView view = new RecordingCardView(context);
        return new SeriesRecordingViewHolder(view, mDvrDataManager, mDvrManager,
                mWatchedPositionManager);
    }

    @Override
    public void onBindViewHolder(ViewHolder baseHolder, Object o) {
        final SeriesRecordingViewHolder viewHolder = (SeriesRecordingViewHolder) baseHolder;
        final SeriesRecording seriesRecording = (SeriesRecording) o;
        final RecordingCardView cardView = (RecordingCardView) viewHolder.view;
        viewHolder.onBound(seriesRecording);
        setTitleAndImage(cardView, seriesRecording);
        super.onBindViewHolder(baseHolder, o);
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        ((RecordingCardView) viewHolder.view).reset();
        ((SeriesRecordingViewHolder) viewHolder).onUnbound();
        super.onUnbindViewHolder(viewHolder);
    }

    private void setTitleAndImage(RecordingCardView cardView, SeriesRecording recording) {
        cardView.setTitle(recording.getTitle());
        if (recording.getPosterUri() != null) {
            cardView.setImageUri(recording.getPosterUri(), false);
        } else {
            Channel channel = mChannelDataManager.getChannel(recording.getChannelId());
            String imageUri = null;
            if (channel != null) {
                imageUri = TvContract.buildChannelLogoUri(channel.getId()).toString();
            }
            cardView.setImageUri(imageUri, true);
        }
    }
}
