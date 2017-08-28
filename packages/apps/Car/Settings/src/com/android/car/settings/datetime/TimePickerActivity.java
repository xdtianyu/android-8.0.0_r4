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
 * limitations under the License
 */

package com.android.car.settings.datetime;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TimePicker;

import com.android.car.settings.common.CarSettingActivity;
import com.android.car.settings.R;

import java.util.Calendar;

/**
 * Sets the system time.
 */
public class TimePickerActivity extends CarSettingActivity {
    private static final int MILLIS_IN_SECOND = 1000;

    private TimePicker mTimePicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.time_picker);

        mTimePicker = (TimePicker) findViewById(R.id.time_picker);

        findViewById(R.id.confirm).setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();

            c.set(Calendar.HOUR_OF_DAY, mTimePicker.getHour());
            c.set(Calendar.MINUTE, mTimePicker.getMinute());
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            long when = Math.max(c.getTimeInMillis(), DatetimeSettingsActivity.MIN_DATE);
            if (when / MILLIS_IN_SECOND < Integer.MAX_VALUE) {
                ((AlarmManager) getSystemService(Context.ALARM_SERVICE)).setTime(when);
                sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));
            }
            finish();
        });
    }
}
