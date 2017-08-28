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

import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.leanback.widget.GuidedActionsStylist;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.Program;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.DvrUiHelper;
import com.android.tv.dvr.EpisodicProgramLoadTask;
import com.android.tv.dvr.SeriesRecording;
import com.android.tv.dvr.SeriesRecording.ChannelOption;
import com.android.tv.dvr.SeriesRecordingScheduler;
import com.android.tv.dvr.SeriesRecordingScheduler.OnSeriesRecordingUpdatedListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fragment for DVR series recording settings.
 */
public class SeriesSettingsFragment extends GuidedStepFragment
        implements DvrDataManager.SeriesRecordingListener {
    private static final String TAG = "SeriesSettingsFragment";
    private static final boolean DEBUG = false;

    private static final long ACTION_ID_PRIORITY = 10;
    private static final long ACTION_ID_CHANNEL = 11;

    private static final long SUB_ACTION_ID_CHANNEL_ALL = 102;
    // Each channel's action id = SUB_ACTION_ID_CHANNEL_ONE_BASE + channel id
    private static final long SUB_ACTION_ID_CHANNEL_ONE_BASE = 500;

    private DvrDataManager mDvrDataManager;
    private ChannelDataManager mChannelDataManager;
    private DvrManager mDvrManager;
    private SeriesRecording mSeriesRecording;
    private long mSeriesRecordingId;
    @ChannelOption int mChannelOption;
    private Comparator<Channel> mChannelComparator;
    private long mSelectedChannelId;
    private int mBackStackCount;
    private boolean mShowViewScheduleOptionInDialog;

    private String mFragmentTitle;
    private String mProrityActionTitle;
    private String mProrityActionHighestText;
    private String mProrityActionLowestText;
    private String mChannelsActionTitle;
    private String mChannelsActionAllText;
    private LongSparseArray<Channel> mId2Channel = new LongSparseArray<>();
    private List<Channel> mChannels = new ArrayList<>();
    private EpisodicProgramLoadTask mEpisodicProgramLoadTask;

    private GuidedAction mPriorityGuidedAction;
    private GuidedAction mChannelsGuidedAction;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mBackStackCount = getFragmentManager().getBackStackEntryCount();
        mDvrDataManager = TvApplication.getSingletons(context).getDvrDataManager();
        mSeriesRecordingId = getArguments().getLong(DvrSeriesSettingsActivity.SERIES_RECORDING_ID);
        mSeriesRecording = mDvrDataManager.getSeriesRecording(mSeriesRecordingId);
        if (mSeriesRecording == null) {
            getActivity().finish();
            return;
        }
        mDvrManager = TvApplication.getSingletons(context).getDvrManager();
        mShowViewScheduleOptionInDialog = getArguments().getBoolean(
                DvrSeriesSettingsActivity.SHOW_VIEW_SCHEDULE_OPTION_IN_DIALOG);
        mDvrDataManager.addSeriesRecordingListener(this);
        long[] channelIds = getArguments().getLongArray(DvrSeriesSettingsActivity.CHANNEL_ID_LIST);
        mChannelDataManager = TvApplication.getSingletons(context).getChannelDataManager();
        if (channelIds == null) {
            Channel channel = mChannelDataManager.getChannel(mSeriesRecording.getChannelId());
            if (channel != null) {
                mId2Channel.put(channel.getId(), channel);
                mChannels.add(channel);
            }
            collectChannelsInBackground();
        } else {
            for (long channelId : channelIds) {
                Channel channel = mChannelDataManager.getChannel(channelId);
                if (channel != null) {
                    mId2Channel.put(channel.getId(), channel);
                    mChannels.add(channel);
                }
            }
        }
        mChannelOption = mSeriesRecording.getChannelOption();
        mSelectedChannelId = Channel.INVALID_ID;
        if (mChannelOption == SeriesRecording.OPTION_CHANNEL_ONE) {
            Channel channel = mChannelDataManager.getChannel(mSeriesRecording.getChannelId());
            if (channel != null) {
                mSelectedChannelId = channel.getId();
            } else {
                mChannelOption = SeriesRecording.OPTION_CHANNEL_ALL;
            }
        }
        mChannelComparator = new Channel.DefaultComparator(context,
                TvApplication.getSingletons(context).getTvInputManagerHelper());
        mChannels.sort(mChannelComparator);
        mFragmentTitle = getString(R.string.dvr_series_settings_title);
        mProrityActionTitle = getString(R.string.dvr_series_settings_priority);
        mProrityActionHighestText = getString(R.string.dvr_series_settings_priority_highest);
        mProrityActionLowestText = getString(R.string.dvr_series_settings_priority_lowest);
        mChannelsActionTitle = getString(R.string.dvr_series_settings_channels);
        mChannelsActionAllText = getString(R.string.dvr_series_settings_channels_all);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mDvrDataManager.removeSeriesRecordingListener(this);
        if (mEpisodicProgramLoadTask != null) {
            mEpisodicProgramLoadTask.cancel(true);
            mEpisodicProgramLoadTask = null;
        }
    }

    @Override
    public void onDestroy() {
        DvrManager dvrManager = TvApplication.getSingletons(getActivity()).getDvrManager();
        if (getFragmentManager().getBackStackEntryCount() == mBackStackCount
                && getArguments()
                        .getBoolean(DvrSeriesSettingsActivity.REMOVE_EMPTY_SERIES_RECORDING)
                && dvrManager.canRemoveSeriesRecording(mSeriesRecordingId)) {
            dvrManager.removeSeriesRecording(mSeriesRecordingId);
        }
        super.onDestroy();
    }

    @Override
    public Guidance onCreateGuidance(Bundle savedInstanceState) {
        String breadcrumb = mSeriesRecording.getTitle();
        String title = mFragmentTitle;
        return new Guidance(title, null, breadcrumb, null);
    }

    @Override
    public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
        mPriorityGuidedAction = new GuidedAction.Builder(getActivity())
                .id(ACTION_ID_PRIORITY)
                .title(mProrityActionTitle)
                .build();
        updatePriorityGuidedAction(false);
        actions.add(mPriorityGuidedAction);

        mChannelsGuidedAction = new GuidedAction.Builder(getActivity())
                .id(ACTION_ID_CHANNEL)
                .title(mChannelsActionTitle)
                .subActions(buildChannelSubAction())
                .build();
        actions.add(mChannelsGuidedAction);
        updateChannelsGuidedAction(false);
    }

    @Override
    public void onCreateButtonActions(List<GuidedAction> actions, Bundle savedInstanceState) {
        actions.add(new GuidedAction.Builder(getActivity())
                .clickAction(GuidedAction.ACTION_ID_OK)
                .build());
        actions.add(new GuidedAction.Builder(getActivity())
                .clickAction(GuidedAction.ACTION_ID_CANCEL)
                .build());
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        long actionId = action.getId();
        if (actionId == GuidedAction.ACTION_ID_OK) {
            if (mEpisodicProgramLoadTask != null) {
                mEpisodicProgramLoadTask.cancel(true);
                mEpisodicProgramLoadTask = null;
            }
            if (mChannelOption != mSeriesRecording.getChannelOption()
                    || mSeriesRecording.isStopped()
                    || (mChannelOption == SeriesRecording.OPTION_CHANNEL_ONE
                            && mSeriesRecording.getChannelId() != mSelectedChannelId)) {
                SeriesRecording.Builder builder = SeriesRecording.buildFrom(mSeriesRecording)
                        .setChannelOption(mChannelOption)
                        .setState(SeriesRecording.STATE_SERIES_NORMAL);
                if (mSelectedChannelId != Channel.INVALID_ID) {
                    builder.setChannelId(mSelectedChannelId);
                }
                TvApplication.getSingletons(getContext()).getDvrManager()
                        .updateSeriesRecording(builder.build());
                SeriesRecordingScheduler scheduler =
                        SeriesRecordingScheduler.getInstance(getContext());
                // Since dialog is used even after the fragment is closed, we should
                // use application context.
                ProgressDialog dialog = ProgressDialog.show(getContext(), null, getString(
                                R.string.dvr_series_schedules_progress_message_updating_programs));
                scheduler.addOnSeriesRecordingUpdatedListener(
                        new OnSeriesRecordingUpdatedListener() {
                    @Override
                    public void onSeriesRecordingUpdated(SeriesRecording... seriesRecordings) {
                        for (SeriesRecording seriesRecording : seriesRecordings) {
                            if (seriesRecording.getId() == mSeriesRecordingId) {
                                dialog.dismiss();
                                scheduler.removeOnSeriesRecordingUpdatedListener(this);
                                showConfirmDialog();
                                return;
                            }
                        }
                    }
                });
            } else {
                showConfirmDialog();
            }
        } else if (actionId == GuidedAction.ACTION_ID_CANCEL) {
            finishGuidedStepFragments();
        } else if (actionId == ACTION_ID_PRIORITY) {
            FragmentManager fragmentManager = getFragmentManager();
            PrioritySettingsFragment fragment = new PrioritySettingsFragment();
            Bundle args = new Bundle();
            args.putLong(PrioritySettingsFragment.COME_FROM_SERIES_RECORDING_ID,
                    mSeriesRecording.getId());
            fragment.setArguments(args);
            GuidedStepFragment.add(fragmentManager, fragment, R.id.dvr_settings_view_frame);
        }
    }

    @Override
    public boolean onSubGuidedActionClicked(GuidedAction action) {
        long actionId = action.getId();
        if (actionId == SUB_ACTION_ID_CHANNEL_ALL) {
            mChannelOption = SeriesRecording.OPTION_CHANNEL_ALL;
            mSelectedChannelId = Channel.INVALID_ID;
            updateChannelsGuidedAction(true);
            return true;
        } else if (actionId > SUB_ACTION_ID_CHANNEL_ONE_BASE) {
            mChannelOption = SeriesRecording.OPTION_CHANNEL_ONE;
            mSelectedChannelId = actionId - SUB_ACTION_ID_CHANNEL_ONE_BASE;
            updateChannelsGuidedAction(true);
            return true;
        }
        return false;
    }

    @Override
    public GuidedActionsStylist onCreateButtonActionsStylist() {
        return new DvrGuidedActionsStylist(true);
    }

    private void updateChannelsGuidedAction(boolean notifyActionChanged) {
        if (mChannelOption == SeriesRecording.OPTION_CHANNEL_ALL) {
            mChannelsGuidedAction.setDescription(mChannelsActionAllText);
        } else {
            mChannelsGuidedAction.setDescription(mId2Channel.get(mSelectedChannelId)
                    .getDisplayText());
        }
        if (notifyActionChanged) {
            notifyActionChanged(findActionPositionById(ACTION_ID_CHANNEL));
        }
    }

    private void updatePriorityGuidedAction(boolean notifyActionChanged) {
        int totalSeriesCount = 0;
        int priorityOrder = 0;
        for (SeriesRecording seriesRecording : mDvrDataManager.getSeriesRecordings()) {
            if (seriesRecording.getState() == SeriesRecording.STATE_SERIES_NORMAL
                    || seriesRecording.getId() == mSeriesRecording.getId()) {
                ++totalSeriesCount;
            }
            if (seriesRecording.getState() == SeriesRecording.STATE_SERIES_NORMAL
                    && seriesRecording.getId() != mSeriesRecording.getId()
                    && seriesRecording.getPriority() > mSeriesRecording.getPriority()) {
                ++priorityOrder;
            }
        }
        if (priorityOrder == 0) {
            mPriorityGuidedAction.setDescription(mProrityActionHighestText);
        } else if (priorityOrder >= totalSeriesCount - 1) {
            mPriorityGuidedAction.setDescription(mProrityActionLowestText);
        } else {
            mPriorityGuidedAction.setDescription(getString(
                    R.string.dvr_series_settings_priority_rank, priorityOrder + 1));
        }
        if (notifyActionChanged) {
            notifyActionChanged(findActionPositionById(ACTION_ID_PRIORITY));
        }
    }

    private void collectChannelsInBackground() {
        if (mEpisodicProgramLoadTask != null) {
            mEpisodicProgramLoadTask.cancel(true);
        }
        mEpisodicProgramLoadTask = new EpisodicProgramLoadTask(getContext(), mSeriesRecording) {
            @Override
            protected void onPostExecute(List<Program> programs) {
                mEpisodicProgramLoadTask = null;
                Set<Long> channelIds = new HashSet<>();
                for (Program program : programs) {
                    channelIds.add(program.getChannelId());
                }
                boolean channelAdded = false;
                for (Long channelId : channelIds) {
                    if (mId2Channel.get(channelId) != null) {
                        continue;
                    }
                    Channel channel = mChannelDataManager.getChannel(channelId);
                    if (channel != null) {
                        channelAdded = true;
                        mId2Channel.put(channelId, channel);
                        mChannels.add(channel);
                        if (DEBUG) Log.d(TAG, "Added channel: " + channel);
                    }
                }
                if (!channelAdded) {
                    return;
                }
                mChannels.sort(mChannelComparator);
                mChannelsGuidedAction.setSubActions(buildChannelSubAction());
                notifyActionChanged(findActionPositionById(ACTION_ID_CHANNEL));
                if (DEBUG) Log.d(TAG, "Complete EpisodicProgramLoadTask");
            }
        }.setLoadCurrentProgram(true)
                .setLoadDisallowedProgram(true)
                .setLoadScheduledEpisode(true)
                .setIgnoreChannelOption(true);
        mEpisodicProgramLoadTask.execute();
    }

    private List<GuidedAction> buildChannelSubAction() {
        List<GuidedAction> channelSubActions = new ArrayList<>();
        channelSubActions.add(new GuidedAction.Builder(getActivity())
                .id(SUB_ACTION_ID_CHANNEL_ALL)
                .title(mChannelsActionAllText)
                .build());
        for (Channel channel : mChannels) {
            channelSubActions.add(new GuidedAction.Builder(getActivity())
                    .id(SUB_ACTION_ID_CHANNEL_ONE_BASE + channel.getId())
                    .title(channel.getDisplayText())
                    .build());
        }
        return channelSubActions;
    }

    private void showConfirmDialog() {
        DvrUiHelper.StartSeriesScheduledDialogActivity(
                getContext(), mSeriesRecording, mShowViewScheduleOptionInDialog);
        finishGuidedStepFragments();
    }

    @Override
    public void onSeriesRecordingAdded(SeriesRecording... seriesRecordings) { }

    @Override
    public void onSeriesRecordingRemoved(SeriesRecording... seriesRecordings) { }

    @Override
    public void onSeriesRecordingChanged(SeriesRecording... seriesRecordings) {
        for (SeriesRecording seriesRecording : seriesRecordings) {
            if (seriesRecording.getId() == mSeriesRecordingId) {
                mSeriesRecording = seriesRecording;
                updatePriorityGuidedAction(true);
                return;
            }
        }
    }
}
