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

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.dvr.ui.DvrPlaybackOverlayFragment;

/**
 * Activity to play a {@link RecordedProgram}.
 */
public class DvrPlaybackActivity extends Activity {
    private static final String TAG = "DvrPlaybackActivity";
    private static final boolean DEBUG = false;

    private DvrPlaybackOverlayFragment mOverlayFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        TvApplication.setCurrentRunningProcess(this, true);
        if (DEBUG) Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dvr_playback);
        mOverlayFragment = (DvrPlaybackOverlayFragment) getFragmentManager()
                .findFragmentById(R.id.dvr_playback_controls_fragment);
    }

    @Override
    public void onVisibleBehindCanceled() {
        if (DEBUG) Log.d(TAG, "onVisibleBehindCanceled");
        super.onVisibleBehindCanceled();
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        mOverlayFragment.onNewIntent(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        float density = getResources().getDisplayMetrics().density;
        mOverlayFragment.onWindowSizeChanged((int) (newConfig.screenWidthDp * density),
                (int) (newConfig.screenHeightDp * density));
    }
}