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
package com.android.car.media.drawer;

import android.content.ComponentName;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.DrawerLayout;
import android.view.View;

import com.android.car.app.CarDrawerActivity;
import com.android.car.app.CarDrawerAdapter;
import com.android.car.media.MediaManager;
import com.android.car.media.MediaPlaybackModel;
import com.android.car.media.R;

/**
 * Manages overall Drawer functionality.
 * <p>
 * Maintains separate MediaPlaybackModel for media browsing and control. Sets up root Drawer
 * adapter with root of media-browse tree (using MediaBrowserItemsFetcher). Supports switching the
 * rootAdapter to show the queue-items (using MediaQueueItemsFetcher).
 */
public class MediaDrawerController {
    private static final String TAG = "MediaDrawerController";
    private static final String EXTRA_ICON_SIZE =
            "com.google.android.gms.car.media.BrowserIconSize";

    private final CarDrawerActivity mActivity;
    private final MediaPlaybackModel mMediaPlaybackModel;
    private MediaDrawerAdapter mRootAdapter;

    public MediaDrawerController(CarDrawerActivity activity) {
        mActivity = activity;
        Bundle extras = new Bundle();
        extras.putInt(EXTRA_ICON_SIZE,
                mActivity.getResources().getDimensionPixelSize(R.dimen.car_list_item_icon_size));
        mMediaPlaybackModel = new MediaPlaybackModel(mActivity, extras);
        mMediaPlaybackModel.addListener(mModelListener);

        // TODO(sriniv): Needs smallLayout below. But breaks when showing queue items (b/36573125).
        mRootAdapter = new MediaDrawerAdapter(mActivity, false /* useSmallLayout */);
        // Start with a empty title since we depend on the mMediaManagerListener callback to
        // know which app is being used and set the actual title there.
        mRootAdapter.setTitle("");

        // Kick off MediaBrowser/MediaController connection.
        mMediaPlaybackModel.start();
    }

    public void cleanup() {
        mRootAdapter.cleanup();
        mMediaPlaybackModel.stop();
    }

    /**
     * @return Adapter to display root items of MediaBrowse tree. {@link #showQueueInDrawer()} can
     *      be used to display items from the queue.
     */
    public CarDrawerAdapter getRootAdapter() {
        return mRootAdapter;
    }

    private MediaItemsFetcher createRootMediaItemsFetcher() {
        return new MediaBrowserItemsFetcher(mActivity, mMediaPlaybackModel,
                mMediaPlaybackModel.getMediaBrowser().getRoot(), true /* showQueueItem */);
    }

    private final MediaPlaybackModel.Listener mModelListener =
            new MediaPlaybackModel.AbstractListener() {
        @Override
        public void onMediaAppChanged(@Nullable ComponentName currentName,
                @Nullable ComponentName newName) {
            // Only store MediaManager instance to a local variable when it is short lived.
            MediaManager mediaManager = MediaManager.getInstance(mActivity);
            mRootAdapter.setTitle(mediaManager.getMediaClientName());
        }

        @Override
        public void onMediaConnected() {
            mRootAdapter.setFetcher(createRootMediaItemsFetcher());
        }
    };

    /**
     * Switch the rootAdapter to show items from the currently playing Queue and open the drawer.
     * When the drawer is closed, the adapter items are switched back to media-browse root.
     */
    public void showQueueInDrawer() {
        mRootAdapter.setFetcher(new MediaQueueItemsFetcher(mActivity, mMediaPlaybackModel));
        mRootAdapter.setTitle(mMediaPlaybackModel.getQueueTitle());
        mActivity.openDrawer();
        mActivity.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerClosed(View drawerView) {
                mRootAdapter.setFetcher(createRootMediaItemsFetcher());
                mActivity.removeDrawerListener(this);
                mRootAdapter.setTitle(
                        MediaManager.getInstance(mActivity).getMediaClientName());
            }

            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {}
            @Override
            public void onDrawerOpened(View drawerView) {}
            @Override
            public void onDrawerStateChanged(int newState) {}
        });
    }
}