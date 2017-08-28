/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.server.cts.device.batterystats;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Used by BatteryStatsValidationTest.
 */
@RunWith(AndroidJUnit4.class)
public class BatteryStatsJobDurationTests extends BatteryStatsDeviceTestBase {

    private static final ComponentName JOB_COMPONENT_NAME =
            new ComponentName("com.android.server.cts.device.batterystats",
                    SimpleJobService.class.getName());
    public static final String TAG = "BatteryStatsJobDurationTests";

    private JobInfo createJobInfo(int id) {
        JobInfo.Builder builder = new JobInfo.Builder(id, JOB_COMPONENT_NAME);
        builder.setOverrideDeadline(0);
        return builder.build();
    }

    @Test
    public void testJobDuration() throws Exception {
        JobScheduler js = mContext.getSystemService(JobScheduler.class);
        assertTrue("JobScheduler service not available", js != null);
        final JobInfo job = createJobInfo(1);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            CountDownLatch latch = SimpleJobService.resetCountDownLatch();
            js.schedule(job);
            if (!latch.await(5, TimeUnit.SECONDS)) {
                Log.e(TAG, "Job didn't finish in 5 seconds");
                fail("Job didn't finish in 5 seconds");
            }
        }
    }
}
