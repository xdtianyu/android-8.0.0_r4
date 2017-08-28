/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.dvr;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityOptionsCompat;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.data.Channel;
import com.android.tv.data.Program;
import com.android.tv.dvr.ui.DvrDetailsActivity;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrAlreadyRecordedDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrAlreadyScheduledDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrChannelRecordDurationOptionDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrChannelWatchConflictDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrInsufficientSpaceErrorDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrMissingStorageErrorDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrProgramConflictDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrScheduleDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrSmallSizedStorageErrorDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrStopRecordingDialogFragment;
import com.android.tv.dvr.ui.DvrSchedulesActivity;
import com.android.tv.dvr.ui.DvrSeriesDeletionActivity;
import com.android.tv.dvr.ui.DvrSeriesScheduledDialogActivity;
import com.android.tv.dvr.ui.DvrSeriesSettingsActivity;
import com.android.tv.dvr.ui.DvrStopRecordingFragment;
import com.android.tv.dvr.ui.DvrStopSeriesRecordingDialogFragment;
import com.android.tv.dvr.ui.DvrStopSeriesRecordingFragment;
import com.android.tv.dvr.ui.HalfSizedDialogFragment;
import com.android.tv.dvr.ui.list.DvrSchedulesFragment;
import com.android.tv.dvr.ui.list.DvrSeriesSchedulesFragment;
import com.android.tv.util.Utils;

import java.util.Collections;
import java.util.List;

/**
 * A helper class for DVR UI.
 */
@MainThread
@TargetApi(Build.VERSION_CODES.N)
public class DvrUiHelper {
    /**
     * Handles the action to create the new schedule. It returns {@code true} if the schedule is
     * added and there's no additional UI, otherwise {@code false}.
     */
    public static boolean handleCreateSchedule(MainActivity activity, Program program) {
        if (program == null) {
            return false;
        }
        DvrManager dvrManager = TvApplication.getSingletons(activity).getDvrManager();
        if (!program.isEpisodic()) {
            // One time recording.
            dvrManager.addSchedule(program);
            if (!dvrManager.getConflictingSchedules(program).isEmpty()) {
                DvrUiHelper.showScheduleConflictDialog(activity, program);
                return false;
            }
        } else {
            SeriesRecording seriesRecording = dvrManager.getSeriesRecording(program);
            if (seriesRecording == null || seriesRecording.isStopped()) {
                DvrUiHelper.showScheduleDialog(activity, program);
                return false;
            } else {
                // Show recorded program rather than the schedule.
                RecordedProgram recordedProgram = dvrManager.getRecordedProgram(program.getTitle(),
                        program.getSeasonNumber(), program.getEpisodeNumber());
                if (recordedProgram != null) {
                    DvrUiHelper.showAlreadyRecordedDialog(activity, program);
                    return false;
                }
                ScheduledRecording duplicate = dvrManager.getScheduledRecording(program.getTitle(),
                        program.getSeasonNumber(), program.getEpisodeNumber());
                if (duplicate != null
                        && (duplicate.getState() == ScheduledRecording.STATE_RECORDING_NOT_STARTED
                        || duplicate.getState()
                        == ScheduledRecording.STATE_RECORDING_IN_PROGRESS)) {
                    DvrUiHelper.showAlreadyScheduleDialog(activity, program);
                    return false;
                }
                // Just add the schedule.
                dvrManager.addSchedule(program);
            }
        }
        return true;

    }

    /**
     * Checks if the storage status is good for recording and shows error messages if needed.
     *
     * @return true if the storage status is fine to be recorded for {@code inputId}.
     */
    public static boolean checkStorageStatusAndShowErrorMessage(Activity activity, String inputId) {
        if (!Utils.isBundledInput(inputId)) {
            return true;
        }
        DvrStorageStatusManager dvrStorageStatusManager =
                TvApplication.getSingletons(activity).getDvrStorageStatusManager();
        int status = dvrStorageStatusManager.getDvrStorageStatus();
        if (status == DvrStorageStatusManager.STORAGE_STATUS_TOTAL_CAPACITY_TOO_SMALL) {
            showDvrSmallSizedStorageErrorDialog(activity);
            return false;
        } else if (status == DvrStorageStatusManager.STORAGE_STATUS_MISSING) {
            showDvrMissingStorageErrorDialog(activity, inputId);
            return false;
        } else if (status == DvrStorageStatusManager.STORAGE_STATUS_FREE_SPACE_INSUFFICIENT) {
            // TODO: handle insufficient storage case.
            return true;
        } else {
            return true;
        }
    }

    /**
     * Shows the schedule dialog.
     */
    public static void showScheduleDialog(MainActivity activity, Program program) {
        if (SoftPreconditions.checkNotNull(program) == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putParcelable(DvrHalfSizedDialogFragment.KEY_PROGRAM, program);
        showDialogFragment(activity, new DvrScheduleDialogFragment(), args, true, true);
    }

    /**
     * Shows the recording duration options dialog.
     */
    public static void showChannelRecordDurationOptions(MainActivity activity, Channel channel) {
        if (SoftPreconditions.checkNotNull(channel) == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong(DvrHalfSizedDialogFragment.KEY_CHANNEL_ID, channel.getId());
        showDialogFragment(activity, new DvrChannelRecordDurationOptionDialogFragment(), args);
    }

    /**
     * Shows the dialog which says that the new schedule conflicts with others.
     */
    public static void showScheduleConflictDialog(MainActivity activity, Program program) {
        if (program == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putParcelable(DvrHalfSizedDialogFragment.KEY_PROGRAM, program);
        showDialogFragment(activity, new DvrProgramConflictDialogFragment(), args, false, true);
    }

    /**
     * Shows the conflict dialog for the channel watching.
     */
    public static void showChannelWatchConflictDialog(MainActivity activity, Channel channel) {
        if (channel == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong(DvrHalfSizedDialogFragment.KEY_CHANNEL_ID, channel.getId());
        showDialogFragment(activity, new DvrChannelWatchConflictDialogFragment(), args);
    }

    /**
     * Shows DVR insufficient space error dialog.
     */
    public static void showDvrInsufficientSpaceErrorDialog(MainActivity activity) {
        showDialogFragment(activity, new DvrInsufficientSpaceErrorDialogFragment(), null);
        Utils.clearRecordingFailedReason(activity,
                TvInputManager.RECORDING_ERROR_INSUFFICIENT_SPACE);
    }

    /**
     * Shows DVR missing storage error dialog.
     */
    private static void showDvrMissingStorageErrorDialog(Activity activity, String inputId) {
        SoftPreconditions.checkArgument(!TextUtils.isEmpty(inputId));
        Bundle args = new Bundle();
        args.putString(DvrHalfSizedDialogFragment.KEY_INPUT_ID, inputId);
        showDialogFragment(activity, new DvrMissingStorageErrorDialogFragment(), args);
    }

    /**
     * Shows DVR small sized storage error dialog.
     */
    public static void showDvrSmallSizedStorageErrorDialog(Activity activity) {
        showDialogFragment(activity, new DvrSmallSizedStorageErrorDialogFragment(), null);
    }

    /**
     * Shows stop recording dialog.
     */
    public static void showStopRecordingDialog(Activity activity, long channelId, int reason,
            HalfSizedDialogFragment.OnActionClickListener listener) {
        Bundle args = new Bundle();
        args.putLong(DvrHalfSizedDialogFragment.KEY_CHANNEL_ID, channelId);
        args.putInt(DvrStopRecordingFragment.KEY_REASON, reason);
        DvrHalfSizedDialogFragment fragment = new DvrStopRecordingDialogFragment();
        fragment.setOnActionClickListener(listener);
        showDialogFragment(activity, fragment, args);
    }

    /**
     * Shows "already scheduled" dialog.
     */
    public static void showAlreadyScheduleDialog(MainActivity activity, Program program) {
        if (program == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putParcelable(DvrHalfSizedDialogFragment.KEY_PROGRAM, program);
        showDialogFragment(activity, new DvrAlreadyScheduledDialogFragment(), args, false, true);
    }

    /**
     * Shows "already recorded" dialog.
     */
    public static void showAlreadyRecordedDialog(MainActivity activity, Program program) {
        if (program == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putParcelable(DvrHalfSizedDialogFragment.KEY_PROGRAM, program);
        showDialogFragment(activity, new DvrAlreadyRecordedDialogFragment(), args, false, true);
    }

    private static void showDialogFragment(Activity activity,
            DvrHalfSizedDialogFragment dialogFragment, Bundle args) {
        showDialogFragment(activity, dialogFragment, args, false, false);
    }

    private static void showDialogFragment(Activity activity,
            DvrHalfSizedDialogFragment dialogFragment, Bundle args, boolean keepSidePanelHistory,
            boolean keepProgramGuide) {
        dialogFragment.setArguments(args);
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).getOverlayManager()
                    .showDialogFragment(DvrHalfSizedDialogFragment.DIALOG_TAG, dialogFragment,
                            keepSidePanelHistory, keepProgramGuide);
        } else {
            dialogFragment.show(activity.getFragmentManager(),
                    DvrHalfSizedDialogFragment.DIALOG_TAG);
        }
    }

    /**
     * Checks whether channel watch conflict dialog is open or not.
     */
    public static boolean isChannelWatchConflictDialogShown(MainActivity activity) {
        return activity.getOverlayManager().getCurrentDialog() instanceof
                DvrChannelWatchConflictDialogFragment;
    }

    private static ScheduledRecording getEarliestScheduledRecording(List<ScheduledRecording>
            recordings) {
        ScheduledRecording earlistScheduledRecording = null;
        if (!recordings.isEmpty()) {
            Collections.sort(recordings,
                    ScheduledRecording.START_TIME_THEN_PRIORITY_THEN_ID_COMPARATOR);
            earlistScheduledRecording = recordings.get(0);
        }
        return earlistScheduledRecording;
    }

    /**
     * Shows the schedules activity to resolve the tune conflict.
     */
    public static void startSchedulesActivityForTuneConflict(Context context, Channel channel) {
        if (channel == null) {
            return;
        }
        List<ScheduledRecording> conflicts = TvApplication.getSingletons(context).getDvrManager()
                .getConflictingSchedulesForTune(channel.getId());
        startSchedulesActivity(context, getEarliestScheduledRecording(conflicts));
    }

    /**
     * Shows the schedules activity to resolve the one time recording conflict.
     */
    public static void startSchedulesActivityForOneTimeRecordingConflict(Context context,
            List<ScheduledRecording> conflicts) {
        startSchedulesActivity(context, getEarliestScheduledRecording(conflicts));
    }

    /**
     * Shows the schedules activity with full schedule.
     */
    public static void startSchedulesActivity(Context context, ScheduledRecording
            focusedScheduledRecording) {
        Intent intent = new Intent(context, DvrSchedulesActivity.class);
        intent.putExtra(DvrSchedulesActivity.KEY_SCHEDULES_TYPE,
                DvrSchedulesActivity.TYPE_FULL_SCHEDULE);
        if (focusedScheduledRecording != null) {
            intent.putExtra(DvrSchedulesFragment.SCHEDULES_KEY_SCHEDULED_RECORDING,
                    focusedScheduledRecording);
        }
        context.startActivity(intent);
    }

    /**
     * Shows the schedules activity for series recording.
     */
    public static void startSchedulesActivityForSeries(Context context,
            SeriesRecording seriesRecording) {
        Intent intent = new Intent(context, DvrSchedulesActivity.class);
        intent.putExtra(DvrSchedulesActivity.KEY_SCHEDULES_TYPE,
                DvrSchedulesActivity.TYPE_SERIES_SCHEDULE);
        intent.putExtra(DvrSeriesSchedulesFragment.SERIES_SCHEDULES_KEY_SERIES_RECORDING,
                seriesRecording);
        context.startActivity(intent);
    }

    /**
     * Shows the series settings activity.
     *
     * @param channelIds Channel ID list which has programs belonging to the series.
     */
    public static void startSeriesSettingsActivity(Context context, long seriesRecordingId,
            @Nullable long[] channelIds, boolean removeEmptySeriesSchedule,
            boolean isWindowTranslucent, boolean showViewScheduleOptionInDialog) {
        Intent intent = new Intent(context, DvrSeriesSettingsActivity.class);
        intent.putExtra(DvrSeriesSettingsActivity.SERIES_RECORDING_ID, seriesRecordingId);
        intent.putExtra(DvrSeriesSettingsActivity.CHANNEL_ID_LIST, channelIds);
        intent.putExtra(DvrSeriesSettingsActivity.REMOVE_EMPTY_SERIES_RECORDING,
                removeEmptySeriesSchedule);
        intent.putExtra(DvrSeriesSettingsActivity.IS_WINDOW_TRANSLUCENT, isWindowTranslucent);
        intent.putExtra(DvrSeriesSettingsActivity.SHOW_VIEW_SCHEDULE_OPTION_IN_DIALOG,
                showViewScheduleOptionInDialog);
        context.startActivity(intent);
    }

    /**
     * Shows "series recording scheduled" dialog activity.
     */
    public static void StartSeriesScheduledDialogActivity(Context context,
            SeriesRecording seriesRecording, boolean showViewScheduleOptionInDialog) {
        if (seriesRecording == null) {
            return;
        }
        Intent intent = new Intent(context, DvrSeriesScheduledDialogActivity.class);
        intent.putExtra(DvrSeriesScheduledDialogActivity.SERIES_RECORDING_ID,
                seriesRecording.getId());
        intent.putExtra(DvrSeriesScheduledDialogActivity.SHOW_VIEW_SCHEDULE_OPTION,
                showViewScheduleOptionInDialog);
        context.startActivity(intent);
    }

    /**
     * Shows the details activity for the DVR items. The type of DVR items may be
     * {@link ScheduledRecording}, {@link RecordedProgram}, or {@link SeriesRecording}.
     */
    public static void startDetailsActivity(Activity activity, Object dvrItem,
            @Nullable ImageView imageView, boolean hideViewSchedule) {
        if (dvrItem == null) {
            return;
        }
        Intent intent = new Intent(activity, DvrDetailsActivity.class);
        long recordingId;
        int viewType;
        if (dvrItem instanceof ScheduledRecording) {
            ScheduledRecording schedule = (ScheduledRecording) dvrItem;
            recordingId = schedule.getId();
            if (schedule.getState() == ScheduledRecording.STATE_RECORDING_NOT_STARTED) {
                viewType = DvrDetailsActivity.SCHEDULED_RECORDING_VIEW;
            } else if (schedule.getState() == ScheduledRecording.STATE_RECORDING_IN_PROGRESS) {
                viewType = DvrDetailsActivity.CURRENT_RECORDING_VIEW;
            } else {
                return;
            }
        } else if (dvrItem instanceof RecordedProgram) {
            recordingId = ((RecordedProgram) dvrItem).getId();
            viewType = DvrDetailsActivity.RECORDED_PROGRAM_VIEW;
        } else if (dvrItem instanceof SeriesRecording) {
            recordingId = ((SeriesRecording) dvrItem).getId();
            viewType = DvrDetailsActivity.SERIES_RECORDING_VIEW;
        } else {
            return;
        }
        intent.putExtra(DvrDetailsActivity.RECORDING_ID, recordingId);
        intent.putExtra(DvrDetailsActivity.DETAILS_VIEW_TYPE, viewType);
        intent.putExtra(DvrDetailsActivity.HIDE_VIEW_SCHEDULE, hideViewSchedule);
        Bundle bundle = null;
        if (imageView != null) {
            bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(activity, imageView,
                    DvrDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
        }
        activity.startActivity(intent, bundle);
    }

    /**
     * Shows the cancel all dialog for series schedules list.
     */
    public static void showCancelAllSeriesRecordingDialog(DvrSchedulesActivity activity,
            SeriesRecording seriesRecording) {
        DvrStopSeriesRecordingDialogFragment dvrStopSeriesRecordingDialogFragment =
                new DvrStopSeriesRecordingDialogFragment();
        Bundle arguments = new Bundle();
        arguments.putParcelable(DvrStopSeriesRecordingFragment.KEY_SERIES_RECORDING,
                seriesRecording);
        dvrStopSeriesRecordingDialogFragment.setArguments(arguments);
        dvrStopSeriesRecordingDialogFragment.show(activity.getFragmentManager(),
                DvrStopSeriesRecordingDialogFragment.DIALOG_TAG);
    }

    /**
     * Shows the series deletion activity.
     */
    public static void startSeriesDeletionActivity(Context context, long seriesRecordingId) {
        Intent intent = new Intent(context, DvrSeriesDeletionActivity.class);
        intent.putExtra(DvrSeriesDeletionActivity.SERIES_RECORDING_ID, seriesRecordingId);
        context.startActivity(intent);
    }

    public static void showAddScheduleToast(Context context,
            String title, long startTimeMs, long endTimeMs) {
        String msg = (startTimeMs > System.currentTimeMillis()) ?
            context.getString(R.string.dvr_msg_program_scheduled, title)
            : context.getString(R.string.dvr_msg_current_program_scheduled, title,
                    Utils.toTimeString(endTimeMs, false));
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }
}
