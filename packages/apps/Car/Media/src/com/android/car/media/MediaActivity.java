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
package com.android.car.media;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.View;

import com.android.car.app.CarDrawerActivity;
import com.android.car.app.CarDrawerAdapter;
import com.android.car.media.drawer.MediaDrawerController;

/**
 * This activity controls the UI of media. It also updates the connection status for the media app
 * by broadcast. Drawer menu is controlled by {@link MediaDrawerController}.
 */
public class MediaActivity extends CarDrawerActivity {
    private static final String ACTION_MEDIA_APP_STATE_CHANGE
            = "android.intent.action.MEDIA_APP_STATE_CHANGE";
    private static final String EXTRA_MEDIA_APP_FOREGROUND
            = "android.intent.action.MEDIA_APP_STATE";

    private static final String TAG = "MediaActivity";

    /**
     * Whether or not {@link #onResume()} has been called.
     */
    private static boolean sIsRunning = false;

    /**
     * Whether or not {@link #onStart()} has been called.
     */
    private boolean mIsStarted;

    /**
     * {@code true} if there was a request to change the content fragment of this Activity when
     * it is not started. Then, when onStart() is called, the content fragment will be added.
     *
     * <p>This prevents a bug where the content fragment is added when the app is not running,
     * causing a StateLossException.
     */
    private boolean mContentFragmentChangeQueued;

    private MediaDrawerController mDrawerController;
    private View mScrimView;
    private CrossfadeImageView mAlbumArtView;
    private MediaPlaybackFragment mMediaPlaybackFragment;

    @Override
    protected void onStart() {
        super.onStart();
        Intent i = new Intent(ACTION_MEDIA_APP_STATE_CHANGE);
        i.putExtra(EXTRA_MEDIA_APP_FOREGROUND, true);
        sendBroadcast(i);

        mIsStarted = true;

        if (mContentFragmentChangeQueued) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Content fragment queued. Attaching now.");
            }
            attachContentFragment();
            mContentFragmentChangeQueued = false;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Intent i = new Intent(ACTION_MEDIA_APP_STATE_CHANGE);
        i.putExtra(EXTRA_MEDIA_APP_FOREGROUND, false);
        sendBroadcast(i);

        mIsStarted = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mDrawerController = new MediaDrawerController(this);
        super.onCreate(savedInstanceState);

        setMainContent(R.layout.media_activity);
        mScrimView = findViewById(R.id.scrim);
        mAlbumArtView = (CrossfadeImageView) findViewById(R.id.album_art);
        setBackgroundColor(getColor(R.color.music_default_artwork));
        MediaManager.getInstance(this).addListener(mListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Send the broadcast to let the current connected media app know it is disconnected now.
        sendMediaConnectionStatusBroadcast(
                MediaManager.getInstance(this).getCurrentComponent(),
                MediaConstants.MEDIA_DISCONNECTED);
        mDrawerController.cleanup();
        MediaManager.getInstance(this).removeListener(mListener);
    }

    @Override
    protected CarDrawerAdapter getRootAdapter() {
        return mDrawerController.getRootAdapter();
    }

    @Override
    public void onResumeFragments() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onResumeFragments");
        }

        super.onResumeFragments();
        handleIntent(getIntent());
        sIsRunning = true;
    }

    @Override
    public void onPause() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onPause");
        }

        super.onPause();
        sIsRunning = false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onNewIntent(); intent: " + (intent == null ? "<< NULL >>" : intent));
        }

        setIntent(intent);
        closeDrawer();
    }

    @Override
    public void onBackPressed() {
        if (mMediaPlaybackFragment.isOverflowMenuVisible()) {
            mMediaPlaybackFragment.closeOverflowMenu();
        }
        super.onBackPressed();
    }

    /**
     * Darken scrim when new messages are displayed in {@link MediaPlaybackFragment}.
     */
    public void darkenScrim(boolean dark) {
        if (!sIsRunning) {
            return;
        }

        if (dark) {
            mScrimView.animate().alpha(0.9f)
                    .setDuration(getResources().getInteger(R.integer.media_scrim_duration_ms));
        } else {
            mScrimView.animate().alpha(0.6f)
                    .setDuration(getResources().getInteger(R.integer.media_scrim_duration_ms));
        }
    }

    /**
     * Don't show scrim when there is no content, else show it.
     */
    public void setScrimVisibility(boolean visible) {
        if (visible) {
            TypedValue scrimAlpha = new TypedValue();
            getResources().getValue(R.dimen.media_scrim_alpha, scrimAlpha, true);
            mScrimView.animate().alpha(scrimAlpha.getFloat())
                    .setDuration(getResources().getInteger(R.integer.media_scrim_duration_ms));
        } else {
            mScrimView.animate().alpha(0.0f)
                    .setDuration(getResources().getInteger(R.integer.media_scrim_duration_ms));

        }
    }

    /**
     * Return the dimension of the background album art in the form of <width, height>.
     */
    public Pair<Integer, Integer> getAlbumArtSize() {
        if (mAlbumArtView != null) {
            return Pair.create(mAlbumArtView.getWidth(), mAlbumArtView.getHeight());
        }
        return null;
    }

    void setBackgroundBitmap(Bitmap bitmap, boolean showAnimation) {
        mAlbumArtView.setImageBitmap(bitmap, showAnimation);
    }

    void setBackgroundColor(int color) {
        mAlbumArtView.setBackgroundColor(color);
    }

    private void handleIntent(Intent intent) {
        Bundle extras = null;
        if (intent != null) {
            extras = intent.getExtras();
        }

        // If the intent has a media component name set, connect to it directly
        if (extras != null && extras.containsKey(MediaManager.KEY_MEDIA_PACKAGE) &&
                extras.containsKey(MediaManager.KEY_MEDIA_CLASS)) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Media component in intent.");
            }

            ComponentName component = new ComponentName(
                    intent.getStringExtra(MediaManager.KEY_MEDIA_PACKAGE),
                    intent.getStringExtra(MediaManager.KEY_MEDIA_CLASS)
            );
            MediaManager.getInstance(this).setMediaClientComponent(component);
        } else {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Launching most recent / default component.");
            }

            // Set it to the default GPM component.
            MediaManager.getInstance(this).connectToMostRecentMediaComponent(
                    new CarClientServiceAdapter(getPackageManager()));
        }

        if (isSearchIntent(intent)) {
            MediaManager.getInstance(this).processSearchIntent(intent);
            setIntent(null);
        }
    }

    private boolean isSearchIntent(Intent intent) {
        return (intent != null && intent.getAction() != null &&
                intent.getAction().equals(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH));
    }

    private void sendMediaConnectionStatusBroadcast(
            ComponentName componentName, String connectionStatus) {
        // It will be no current component if no media app is chosen before.
        if (componentName == null) {
            return;
        }

        Intent intent = new Intent(MediaConstants.ACTION_MEDIA_STATUS);
        intent.setPackage(componentName.getPackageName());
        intent.putExtra(MediaConstants.MEDIA_CONNECTION_STATUS, connectionStatus);
        sendBroadcast(intent);
    }

    void attachContentFragment() {
        if (mMediaPlaybackFragment == null) {
            mMediaPlaybackFragment = new MediaPlaybackFragment();
        }

        setContentFragment(mMediaPlaybackFragment);
    }

    private final MediaManager.Listener mListener = new MediaManager.Listener() {

        @Override
        public void onMediaAppChanged(ComponentName componentName) {
            sendMediaConnectionStatusBroadcast(componentName, MediaConstants.MEDIA_CONNECTED);

            // Since this callback happens asynchronously, ensure that the Activity has been
            // started before changing fragments. Otherwise, the attach fragment will throw
            // an IllegalStateException due to Fragment's checkStateLoss.
            if (mIsStarted) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "onMediaAppChanged: attaching content fragment");
                }
                attachContentFragment();
            } else {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "onMediaAppChanged: queuing content fragment change");
                }
                mContentFragmentChangeQueued = true;
            }
        }

        @Override
        public void onStatusMessageChanged(String msg) {}
    };

    private void setContentFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(getContentContainerId(), fragment)
                .commit();
    }


    void showQueueInDrawer() {
        mDrawerController.showQueueInDrawer();
    }
}