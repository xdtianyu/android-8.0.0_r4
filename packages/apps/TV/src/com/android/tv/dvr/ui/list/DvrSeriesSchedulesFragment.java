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

package com.android.tv.dvr.ui.list;

import android.annotation.TargetApi;
import android.database.ContentObserver;
import android.media.tv.TvContract.Programs;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.transition.Fade;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.ApplicationSingletons;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.Program;
import com.android.tv.dvr.DvrDataManager.SeriesRecordingListener;
import com.android.tv.dvr.EpisodicProgramLoadTask;
import com.android.tv.dvr.SeriesRecording;
import com.android.tv.dvr.ui.list.SchedulesHeaderRowPresenter.SeriesRecordingHeaderRowPresenter;

import java.util.List;

/**
 * A fragment to show the list of series schedule recordings.
 */
@TargetApi(Build.VERSION_CODES.N)
public class DvrSeriesSchedulesFragment extends BaseDvrSchedulesFragment {
    private static final String TAG = "DvrSeriesSchedulesFragment";
    /**
     * The key for series recording whose scheduled recording list will be displayed.
     */
    public static final String SERIES_SCHEDULES_KEY_SERIES_RECORDING =
            "series_schedules_key_series_recording";
    /**
     * The key for programs belong to the series recording whose scheduled recording
     * list will be displayed.
     */
    public static final String SERIES_SCHEDULES_KEY_SERIES_PROGRAMS =
            "series_schedules_key_series_programs";

    private ChannelDataManager mChannelDataManager;
    private SeriesRecording mSeriesRecording;
    private List<Program> mPrograms;
    private EpisodicProgramLoadTask mProgramLoadTask;

    private final SeriesRecordingListener mSeriesRecordingListener =
            new SeriesRecordingListener() {
                @Override
                public void onSeriesRecordingAdded(SeriesRecording... seriesRecordings) { }

                @Override
                public void onSeriesRecordingRemoved(SeriesRecording... seriesRecordings) {
                    for (SeriesRecording r : seriesRecordings) {
                        if (r.getId() == mSeriesRecording.getId()) {
                            getActivity().finish();
                            return;
                        }
                    }
                }

                @Override
                public void onSeriesRecordingChanged(SeriesRecording... seriesRecordings) {
                    for (SeriesRecording r : seriesRecordings) {
                        if (r.getId() == mSeriesRecording.getId()
                                && getRowsAdapter() instanceof SeriesScheduleRowAdapter) {
                            ((SeriesScheduleRowAdapter) getRowsAdapter())
                                    .onSeriesRecordingUpdated(r);
                            return;
                        }
                    }
                }
            };

    private final ContentObserver mContentObserver =
            new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    super.onChange(selfChange, uri);
                    executeProgramLoadingTask();
                }
            };

    private final ChannelDataManager.Listener mChannelListener = new ChannelDataManager.Listener() {
        @Override
        public void onLoadFinished() { }

        @Override
        public void onChannelListUpdated() {
            executeProgramLoadingTask();
        }

        @Override
        public void onChannelBrowsableChanged() { }
    };

    public DvrSeriesSchedulesFragment() {
        setEnterTransition(new Fade(Fade.IN));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args != null) {
            mSeriesRecording = args.getParcelable(SERIES_SCHEDULES_KEY_SERIES_RECORDING);
            mPrograms = args.getParcelableArrayList(SERIES_SCHEDULES_KEY_SERIES_PROGRAMS);
        }
        super.onCreate(savedInstanceState);
        ApplicationSingletons singletons = TvApplication.getSingletons(getContext());
        singletons.getDvrDataManager().addSeriesRecordingListener(mSeriesRecordingListener);
        mChannelDataManager = singletons.getChannelDataManager();
        mChannelDataManager.addListener(mChannelListener);
        getContext().getContentResolver().registerContentObserver(Programs.CONTENT_URI, true,
                mContentObserver);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        onProgramsUpdated();
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    private void onProgramsUpdated() {
        ((SeriesScheduleRowAdapter) getRowsAdapter()).setPrograms(mPrograms);
        if (mPrograms == null || mPrograms.isEmpty()) {
            showEmptyMessage(R.string.dvr_series_schedules_empty_state);
        } else {
            hideEmptyMessage();
        }
    }

    @Override
    public void onDestroy() {
        if (mProgramLoadTask != null) {
            mProgramLoadTask.cancel(true);
            mProgramLoadTask = null;
        }
        getContext().getContentResolver().unregisterContentObserver(mContentObserver);
        mChannelDataManager.removeListener(mChannelListener);
        TvApplication.getSingletons(getContext()).getDvrDataManager()
                .removeSeriesRecordingListener(mSeriesRecordingListener);
        super.onDestroy();
    }

    @Override
    public SchedulesHeaderRowPresenter onCreateHeaderRowPresenter() {
        return new SeriesRecordingHeaderRowPresenter(getContext());
    }

    @Override
    public ScheduleRowPresenter onCreateRowPresenter() {
        return new SeriesScheduleRowPresenter(getContext());
    }

    @Override
    public ScheduleRowAdapter onCreateRowsAdapter(ClassPresenterSelector presenterSelector) {
        return new SeriesScheduleRowAdapter(getContext(), presenterSelector, mSeriesRecording);
    }

    @Override
    protected int getFirstItemPosition() {
        if (mSeriesRecording != null
                && mSeriesRecording.getState() == SeriesRecording.STATE_SERIES_STOPPED) {
            return 0;
        }
        return super.getFirstItemPosition();
    }

    private void executeProgramLoadingTask() {
        if (mProgramLoadTask != null) {
            mProgramLoadTask.cancel(true);
        }
        mProgramLoadTask = new EpisodicProgramLoadTask(getContext(), mSeriesRecording) {
            @Override
            protected void onPostExecute(List<Program> programs) {
                mPrograms = programs;
                onProgramsUpdated();
            }
        };
        mProgramLoadTask.setLoadCurrentProgram(true)
                .setLoadDisallowedProgram(true)
                .setLoadScheduledEpisode(true)
                .setIgnoreChannelOption(true)
                .execute();
    }
}