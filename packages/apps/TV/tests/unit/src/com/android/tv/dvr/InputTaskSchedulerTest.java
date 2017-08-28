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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.media.tv.TvInputInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.test.filters.SdkSuppress;
import android.support.test.filters.SmallTest;
import android.test.AndroidTestCase;

import com.android.tv.InputSessionManager;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.dvr.InputTaskScheduler.RecordingTaskFactory;
import com.android.tv.testing.FakeClock;
import com.android.tv.testing.dvr.RecordingTestUtils;
import com.android.tv.util.Clock;
import com.android.tv.util.TestUtils;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link InputTaskScheduler}.
 */
@SmallTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
public class InputTaskSchedulerTest extends AndroidTestCase {
    private static final String INPUT_ID = "input_id";
    private static final int CHANNEL_ID = 1;
    private static final long LISTENER_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(1);
    private static final int TUNER_COUNT_ONE = 1;
    private static final int TUNER_COUNT_TWO = 2;
    private static final long LOW_PRIORITY = 1;
    private static final long HIGH_PRIORITY = 2;

    private FakeClock mFakeClock;
    private InputTaskScheduler mScheduler;
    @Mock private DvrManager mDvrManager;
    @Mock private WritableDvrDataManager mDataManager;
    @Mock private InputSessionManager mSessionManager;
    @Mock private AlarmManager mMockAlarmManager;
    @Mock private ChannelDataManager mChannelDataManager;
    private List<RecordingTask> mRecordingTasks;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        Handler fakeMainHandler = new Handler();
        Handler workerThreadHandler = new Handler();
        mRecordingTasks = new ArrayList();
        MockitoAnnotations.initMocks(this);
        mFakeClock = FakeClock.createWithCurrentTime();
        TvInputInfo input = createTvInputInfo(TUNER_COUNT_ONE);
        mScheduler = new InputTaskScheduler(getContext(), input, Looper.myLooper(),
                mChannelDataManager, mDvrManager, mDataManager, mSessionManager, mFakeClock,
                fakeMainHandler, workerThreadHandler, new RecordingTaskFactory() {
                    @Override
                    public RecordingTask createRecordingTask(ScheduledRecording scheduledRecording,
                            Channel channel, DvrManager dvrManager,
                            InputSessionManager sessionManager, WritableDvrDataManager dataManager,
                            Clock clock) {
                        RecordingTask task = mock(RecordingTask.class);
                        when(task.getPriority()).thenReturn(scheduledRecording.getPriority());
                        when(task.getEndTimeMs()).thenReturn(scheduledRecording.getEndTimeMs());
                        mRecordingTasks.add(task);
                        return task;
                    }
                });
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testAddSchedule_past() throws Exception {
        ScheduledRecording r = RecordingTestUtils.createTestRecordingWithPeriod(INPUT_ID,
                CHANNEL_ID, 0L, 1L);
        when(mDataManager.getScheduledRecording(anyLong())).thenReturn(r);
        mScheduler.handleAddSchedule(r);
        mScheduler.handleBuildSchedule();
        verify(mDataManager, timeout((int) LISTENER_TIMEOUT_MS).times(1))
                .changeState(any(ScheduledRecording.class),
                        eq(ScheduledRecording.STATE_RECORDING_FAILED));
    }

    public void testAddSchedule_start() throws Exception {
        mScheduler.handleAddSchedule(RecordingTestUtils.createTestRecordingWithPeriod(INPUT_ID,
                CHANNEL_ID, mFakeClock.currentTimeMillis(),
                mFakeClock.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)));
        mScheduler.handleBuildSchedule();
        verify(mRecordingTasks.get(0), timeout((int) LISTENER_TIMEOUT_MS).times(1)).start();
    }

    public void testAddSchedule_consecutiveNoStop() throws Exception {
        long startTimeMs = mFakeClock.currentTimeMillis();
        long endTimeMs = startTimeMs + TimeUnit.SECONDS.toMillis(1);
        long id = 0;
        mScheduler.handleAddSchedule(
                RecordingTestUtils.createTestRecordingWithIdAndPriorityAndPeriod(++id, CHANNEL_ID,
                        LOW_PRIORITY, startTimeMs, endTimeMs));
        mScheduler.handleBuildSchedule();
        startTimeMs = endTimeMs;
        endTimeMs = startTimeMs + TimeUnit.SECONDS.toMillis(1);
        mScheduler.handleAddSchedule(
                RecordingTestUtils.createTestRecordingWithIdAndPriorityAndPeriod(++id, CHANNEL_ID,
                        HIGH_PRIORITY, startTimeMs, endTimeMs));
        mScheduler.handleBuildSchedule();
        verify(mRecordingTasks.get(0), timeout((int) LISTENER_TIMEOUT_MS).times(1)).start();
        // The first schedule should not be stopped because the second one should wait for the end
        // of the first schedule.
        verify(mRecordingTasks.get(0), after((int) LISTENER_TIMEOUT_MS).never()).stop();
    }

    public void testAddSchedule_consecutiveNoFail() throws Exception {
        long startTimeMs = mFakeClock.currentTimeMillis();
        long endTimeMs = startTimeMs + TimeUnit.SECONDS.toMillis(1);
        long id = 0;
        when(mDataManager.getScheduledRecording(anyLong())).thenReturn(ScheduledRecording
                .builder(INPUT_ID, CHANNEL_ID, 0L, 0L).build());
        mScheduler.handleAddSchedule(
                RecordingTestUtils.createTestRecordingWithIdAndPriorityAndPeriod(++id, CHANNEL_ID,
                        HIGH_PRIORITY, startTimeMs, endTimeMs));
        mScheduler.handleBuildSchedule();
        startTimeMs = endTimeMs;
        endTimeMs = startTimeMs + TimeUnit.SECONDS.toMillis(1);
        mScheduler.handleAddSchedule(
                RecordingTestUtils.createTestRecordingWithIdAndPriorityAndPeriod(++id, CHANNEL_ID,
                        LOW_PRIORITY, startTimeMs, endTimeMs));
        mScheduler.handleBuildSchedule();
        verify(mRecordingTasks.get(0), timeout((int) LISTENER_TIMEOUT_MS).times(1)).start();
        verify(mRecordingTasks.get(0), after((int) LISTENER_TIMEOUT_MS).never()).stop();
        // The second schedule should not fail because it can starts after the first one finishes.
        verify(mDataManager, after((int) LISTENER_TIMEOUT_MS).never())
                .changeState(any(ScheduledRecording.class),
                        eq(ScheduledRecording.STATE_RECORDING_FAILED));
    }

    public void testAddSchedule_consecutiveUseLessSession() throws Exception {
        TvInputInfo input = createTvInputInfo(TUNER_COUNT_TWO);
        mScheduler.updateTvInputInfo(input);
        long startTimeMs = mFakeClock.currentTimeMillis();
        long endTimeMs = startTimeMs + TimeUnit.SECONDS.toMillis(1);
        long id = 0;
        mScheduler.handleAddSchedule(
                RecordingTestUtils.createTestRecordingWithIdAndPriorityAndPeriod(++id, CHANNEL_ID,
                        LOW_PRIORITY, startTimeMs, endTimeMs));
        mScheduler.handleBuildSchedule();
        startTimeMs = endTimeMs;
        endTimeMs = startTimeMs + TimeUnit.SECONDS.toMillis(1);
        mScheduler.handleAddSchedule(
                RecordingTestUtils.createTestRecordingWithIdAndPriorityAndPeriod(++id, CHANNEL_ID,
                        HIGH_PRIORITY, startTimeMs, endTimeMs));
        mScheduler.handleBuildSchedule();
        verify(mRecordingTasks.get(0), timeout((int) LISTENER_TIMEOUT_MS).times(1)).start();
        verify(mRecordingTasks.get(0), after((int) LISTENER_TIMEOUT_MS).never()).stop();
        // The second schedule should wait until the first one finishes rather than creating a new
        // session even though there are available tuners.
        assertTrue(mRecordingTasks.size() == 1);
    }

    public void testUpdateSchedule_noCancel() throws Exception {
        ScheduledRecording r = RecordingTestUtils.createTestRecordingWithPeriod(INPUT_ID,
                CHANNEL_ID, mFakeClock.currentTimeMillis(),
                mFakeClock.currentTimeMillis() + TimeUnit.HOURS.toMillis(1));
        mScheduler.handleAddSchedule(r);
        mScheduler.handleBuildSchedule();
        mScheduler.handleUpdateSchedule(r);
        verify(mRecordingTasks.get(0), after((int) LISTENER_TIMEOUT_MS).never()).cancel();
    }

    public void testUpdateSchedule_cancel() throws Exception {
        ScheduledRecording r = RecordingTestUtils.createTestRecordingWithPeriod(INPUT_ID,
                CHANNEL_ID, mFakeClock.currentTimeMillis(),
                mFakeClock.currentTimeMillis() + TimeUnit.HOURS.toMillis(2));
        mScheduler.handleAddSchedule(r);
        mScheduler.handleBuildSchedule();
        mScheduler.handleUpdateSchedule(ScheduledRecording.buildFrom(r)
                .setStartTimeMs(mFakeClock.currentTimeMillis() + TimeUnit.HOURS.toMillis(1))
                .build());
        verify(mRecordingTasks.get(0), timeout((int) LISTENER_TIMEOUT_MS).times(1)).cancel();
    }

    private TvInputInfo createTvInputInfo(int tunerCount) throws Exception {
        return TestUtils.createTvInputInfo(null, null, null, 0, false, true, tunerCount);
    }
}
