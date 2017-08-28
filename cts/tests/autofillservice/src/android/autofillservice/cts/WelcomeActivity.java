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
package android.autofillservice.cts;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

/**
 * Activity that displays a "Welcome USER" message after login.
 */
public class WelcomeActivity extends AbstractAutoFillActivity {

    private static WelcomeActivity sInstance;

    private static final String TAG = "WelcomeActivity";

    static final String EXTRA_MESSAGE = "message";

    private TextView mOutput;

    public WelcomeActivity() {
        sInstance = this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.welcome_activity);

        mOutput = (TextView) findViewById(R.id.output);

        final Intent intent = getIntent();
        final String message = intent.getStringExtra(EXTRA_MESSAGE);

        if (!TextUtils.isEmpty(message)) {
            mOutput.setText(message);
        }

        Log.d(TAG, "Output: " + mOutput.getText());
    }

    static void finishIt() {
        if (sInstance != null) {
            Log.d(TAG, "So long and thanks for all the fish!");
            sInstance.finish();
        }
    }
}
