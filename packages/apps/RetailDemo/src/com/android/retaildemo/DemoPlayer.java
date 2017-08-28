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

package com.android.retaildemo;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.VideoView;

import java.io.File;

/**
 * This is the activity for playing the retail demo video. This will also try to keep
 * the screen on.
 *
 * This will check for the demo video in {@link Environment#getDataPreloadsDemoDirectory()} or
 * {@link Context#getObbDir()}. If the demo video is not present, it will run a task to download it
 * from the specified url.
 */
public class DemoPlayer extends Activity implements DownloadVideoTask.ResultListener {

    private static final String TAG = "DemoPlayer";
    private static final boolean DEBUG = false;

    /**
     * Maximum amount of time to wait for demo user to set up.
     * After it the user can tap the screen to exit
     */
    private static final long READY_TO_TAP_MAX_DELAY_MS = 60 * 1000; // 1 min

    private PowerManager mPowerManager;

    private VideoView mVideoView;
    private String mDownloadPath;
    private boolean mUsingDownloadedVideo;
    private Handler mHandler;
    private boolean mReadyToTap;
    private SettingsObserver mSettingsObserver;
    private File mPreloadedVideoFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        setContentView(R.layout.retail_video);

        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mHandler = new Handler();
        final String preloadedFileName = getString(R.string.retail_demo_video_file_name);
        mPreloadedVideoFile = new File(Environment.getDataPreloadsDemoDirectory(),
                preloadedFileName);
        mDownloadPath = getObbDir().getPath() + File.separator + preloadedFileName;
        mVideoView = (VideoView) findViewById(R.id.video_content);

        // Start playing the video when it is ready
        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mediaPlayer.setLooping(true);
                mVideoView.start();
            }
        });

        mVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                if (mUsingDownloadedVideo && mPreloadedVideoFile.exists()) {
                    if (DEBUG) Log.d(TAG, "Error using the downloaded video, "
                            + "falling back to the preloaded video at " + mPreloadedVideoFile);
                    mUsingDownloadedVideo = false;
                    setVideoPath(mPreloadedVideoFile.getPath());
                    // And delete the downloaded video so that we don't try to use it
                    // again next time.
                    new File(mDownloadPath).delete();
                } else {
                    displayFallbackView();
                }
                return true;
            }
        });

        mReadyToTap = isUserSetupComplete();
        if (!mReadyToTap) {
            // Wait for setup to finish
            mSettingsObserver = new SettingsObserver();
            mSettingsObserver.register();
            // Allow user to exit the demo player if setup takes too long
            mHandler.postDelayed(() -> {
                mReadyToTap = true;
            }, READY_TO_TAP_MAX_DELAY_MS);
        }

        loadVideo();
    }

    private void displayFallbackView() {
        if (DEBUG) Log.d(TAG, "Showing the fallback view");
        findViewById(R.id.fallback_layout).setVisibility(View.VISIBLE);
        findViewById(R.id.video_layout).setVisibility(View.GONE);
    }

    private void displayVideoView() {
        if (DEBUG) Log.d(TAG, "Showing the video view");
        findViewById(R.id.video_layout).setVisibility(View.VISIBLE);
        findViewById(R.id.fallback_layout).setVisibility(View.GONE);
    }

    private void loadVideo() {
        // If the video is already downloaded, then use that and check for an update.
        // Otherwise check if the video is preloaded, if not download the video from the
        // specified url.
        boolean isVideoSet = false;
        if (new File(mDownloadPath).exists()) {
            if (DEBUG) Log.d(TAG, "Using the already existing video at " + mDownloadPath);
            setVideoPath(mDownloadPath);
            isVideoSet = true;
        } else if (mPreloadedVideoFile.exists()) {
            if (DEBUG) Log.d(TAG, "Using the preloaded video at " + mPreloadedVideoFile);
            setVideoPath(mPreloadedVideoFile.getPath());
            isVideoSet = true;
        }

        final String downloadUrl = getString(R.string.retail_demo_video_download_url);
        // If the download url is empty, then no need to start the download task.
        if (TextUtils.isEmpty(downloadUrl)) {
            if (!isVideoSet) {
                displayFallbackView();
            }
            return;
        }
        if (!checkIfDownloadingAllowed()) {
            if (DEBUG) Log.d(TAG, "Downloading not allowed, neither starting download nor checking"
                    + " for an update.");
            if (!isVideoSet) {
                displayFallbackView();
            }
            return;
        }
        new DownloadVideoTask(this, mDownloadPath, mPreloadedVideoFile, this).run();
    }

    private boolean checkIfDownloadingAllowed() {
        final int lastBootCount = DataReaderWriter.readLastBootCount(this);
        final int bootCount = Settings.Global.getInt(getContentResolver(),
                Settings.Global.BOOT_COUNT, -1);
        // Something went wrong, don't do anything.
        if (bootCount == -1) {
            return false;
        }
        // Error reading the last boot count, just write the current boot count.
        if (lastBootCount == -1) {
            DataReaderWriter.writeLastBootCount(this, bootCount);
            return false;
        }
        // We need to download the video atmost once after every boot.
        if (lastBootCount != bootCount) {
            DataReaderWriter.writeLastBootCount(this, bootCount);
            return true;
        }
        return false;
    }

    @Override
    public void onFileDownloaded(final String filePath) {
        mUsingDownloadedVideo = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setVideoPath(filePath);
            }
        });
    }

    @Override
    public void onError() {
        displayFallbackView();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mReadyToTap && getSystemService(UserManager.class).isDemoUser()) {
            disableSelf();
        }
        return true;
    }

    private void disableSelf() {
        final String componentName = getString(R.string.demo_overlay_app_component);
        if (!TextUtils.isEmpty(componentName)) {
            ComponentName component = ComponentName.unflattenFromString(componentName);
            if (component != null) {
                Intent intent = new Intent();
                intent.setComponent(component);
                ResolveInfo resolveInfo = getPackageManager().resolveService(intent, 0);
                if (resolveInfo != null) {
                    startService(intent);
                } else {
                    resolveInfo = getPackageManager().resolveActivity(intent,
                            PackageManager.MATCH_DEFAULT_ONLY);
                    if (resolveInfo != null) {
                        startActivity(intent);
                    } else {
                        Log.w(TAG, "Component " + componentName + " cannot be resolved");
                    }
                }
            }
        }
        getPackageManager().setComponentEnabledSetting(getComponentName(),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
    }

    @Override
    public void onPause() {
        if (mVideoView != null) {
            mVideoView.pause();
        }
        // If power key is pressed to turn screen off, turn screen back on
        if (!mPowerManager.isInteractive()) {
            forceTurnOnScreen();
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Resume video playing
        if (mVideoView != null) {
            mVideoView.start();
        }
    }

    @Override
    protected void onDestroy() {
        if (mSettingsObserver != null) {
            mSettingsObserver.unregister();
            mSettingsObserver = null;
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            // Make view fullscreen.
            // And since flags SYSTEM_UI_FLAG_HIDE_NAVIGATION and SYSTEM_UI_FLAG_HIDE_NAVIGATION
            // might get cleared on user interaction, we do this here.
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.STATUS_BAR_DISABLE_BACK);
        }
    }

    private void setVideoPath(String videoPath) {
        // Load the video from resource
        try {
            mVideoView.setVideoPath(videoPath);
            displayVideoView();
        } catch (Exception e) {
            Log.e(TAG, "Exception setting video uri! " + e.getMessage());
            displayFallbackView();
        }
    }

    private void forceTurnOnScreen() {
        final PowerManager.WakeLock wakeLock = mPowerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
        wakeLock.acquire();
        // Device woken up, release the wake-lock
        wakeLock.release();
    }

    private class SettingsObserver extends ContentObserver {
        private final Uri mDemoModeSetupComplete = Settings.Secure.getUriFor(
                Settings.Secure.DEMO_USER_SETUP_COMPLETE);

        SettingsObserver() {
            super(mHandler);
        }

        void register() {
            ContentResolver cr = getContentResolver();
            cr.registerContentObserver(mDemoModeSetupComplete, false, this);
        }

        void unregister() {
            getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (mDemoModeSetupComplete.equals(uri)) {
                mReadyToTap = true;
            }
        }
    }

    private boolean isUserSetupComplete() {
        return "1".equals(Settings.Secure.getString(getContentResolver(),
                Settings.Secure.DEMO_USER_SETUP_COMPLETE));
    }
}
