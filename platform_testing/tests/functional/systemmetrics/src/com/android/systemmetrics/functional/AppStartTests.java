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

package com.android.systemmetrics.functional;

import android.content.Context;
import android.content.Intent;
import android.metrics.LogMaker;
import android.metrics.MetricsReader;
import android.os.SystemClock;
import android.support.test.metricshelper.MetricsAsserts;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.telecom.Log;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.text.TextUtils;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import java.util.Queue;

/**
 * runtest --path platform_testing/tests/functional/systemmetrics/
 */
public class AppStartTests extends InstrumentationTestCase {
    private static final String LOG_TAG = AppStartTests.class.getSimpleName();
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final int LONG_TIMEOUT_MS = 2000;
    private UiDevice mDevice = null;
    private Context mContext;
    private MetricsReader mMetricsReader;
    private int mPreUptime;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mContext = getInstrumentation().getContext();
        mDevice.setOrientationNatural();
        mMetricsReader = new MetricsReader();
        mMetricsReader.checkpoint(); // clear out old logs
        mPreUptime = (int) (SystemClock.uptimeMillis() / 1000);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mDevice.unfreezeRotation();
        mDevice.pressHome();
    }

    @MediumTest
    public void testStartApp() throws Exception {
        Context context = getInstrumentation().getContext();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(SETTINGS_PACKAGE);

        // Clear out any previous instances
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);

        assertNotNull("component name is null", intent.getComponent());
        String className = intent.getComponent().getClassName();
        String packageName = intent.getComponent().getPackageName();
        assertTrue("className is empty", !TextUtils.isEmpty(className));
        assertTrue("packageName is empty", !TextUtils.isEmpty(packageName));


        context.startActivity(intent);
        mDevice.wait(Until.hasObject(By.pkg(SETTINGS_PACKAGE).depth(0)), LONG_TIMEOUT_MS);

        int postUptime = (int) (SystemClock.uptimeMillis() / 1000);

        Queue<LogMaker> startLogs = MetricsAsserts.findMatchingLogs(mMetricsReader,
                new LogMaker(MetricsEvent.APP_TRANSITION));
        boolean found = false;
        for (LogMaker log : startLogs) {
            String actualClassName = (String) log.getTaggedData(
                    MetricsEvent.FIELD_CLASS_NAME);
            String actualPackageName = log.getPackageName();
            if (className.equals(actualClassName) && packageName.equals(actualPackageName)) {
                found = true;
                int startUptime = ((Number)
                        log.getTaggedData(MetricsEvent.APP_TRANSITION_DEVICE_UPTIME_SECONDS))
                        .intValue();
                assertTrue("must be either cold or warm launch",
                        MetricsEvent.TYPE_TRANSITION_COLD_LAUNCH == log.getType()
                                || MetricsEvent.TYPE_TRANSITION_WARM_LAUNCH == log.getType());
                assertTrue("reported uptime should be after the app was started",
                        mPreUptime <= startUptime);
                assertTrue("reported uptime should be before assertion time",
                        startUptime <= postUptime);
                assertNotNull("log should have delay",
                        log.getTaggedData(MetricsEvent.APP_TRANSITION_DELAY_MS));
                assertEquals("transition should be started because of starting window",
                        1 /* APP_TRANSITION_STARTING_WINDOW */, log.getSubtype());
                assertNotNull("log should have starting window delay",
                        log.getTaggedData(MetricsEvent.APP_TRANSITION_STARTING_WINDOW_DELAY_MS));
                assertNotNull("log should have windows drawn delay",
                        log.getTaggedData(MetricsEvent.APP_TRANSITION_WINDOWS_DRAWN_DELAY_MS));
            }
        }
        assertTrue("did not find the app start start log for: "
                + intent.getComponent().flattenToShortString(), found);
    }
}
