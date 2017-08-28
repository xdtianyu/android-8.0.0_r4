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

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.util.Log;

import com.android.car.app.CarDrawerActivity;
import com.android.car.app.DrawerItemViewHolder;
import com.android.car.media.MediaPlaybackModel;
import com.android.car.media.R;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link MediaItemsFetcher} implementation that fetches items from a specific {@link MediaBrowser}
 * node.
 * <p>
 * It optionally supports surfacing the Media app's queue as the last item.
 */
class MediaBrowserItemsFetcher implements MediaItemsFetcher {
    private static final String TAG = "Media.BrowserFetcher";

    private final CarDrawerActivity mActivity;
    private final MediaPlaybackModel mMediaPlaybackModel;
    private final String mMediaId;
    private final boolean mShowQueueItem;
    private ItemsUpdatedCallback mCallback;
    private List<MediaBrowser.MediaItem> mItems = new ArrayList<>();
    private boolean mQueueAvailable;

    MediaBrowserItemsFetcher(CarDrawerActivity activity, MediaPlaybackModel model, String mediaId,
            boolean showQueueItem) {
        mActivity = activity;
        mMediaPlaybackModel = model;
        mMediaId = mediaId;
        mShowQueueItem = showQueueItem;
    }

    @Override
    public void start(ItemsUpdatedCallback callback) {
        mCallback = callback;
        updateQueueAvailability();
        mMediaPlaybackModel.getMediaBrowser().subscribe(mMediaId, mSubscriptionCallback);
        mMediaPlaybackModel.addListener(mModelListener);
    }

    private final MediaPlaybackModel.Listener mModelListener =
            new MediaPlaybackModel.AbstractListener() {
        @Override
        public void onQueueChanged(List<MediaSession.QueueItem> queue) {
            updateQueueAvailability();
        }
        @Override
        public void onSessionDestroyed(CharSequence destroyedMediaClientName) {
            updateQueueAvailability();
        }
    };

    private final MediaBrowser.SubscriptionCallback mSubscriptionCallback =
        new MediaBrowser.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> children) {
                mItems.clear();
                mItems.addAll(children);
                mCallback.onItemsUpdated();
            }

            @Override
            public void onError(String parentId) {
                Log.e(TAG, "Error loading children of: " + mMediaId);
                mItems.clear();
                mCallback.onItemsUpdated();
            }
        };

    private void updateQueueAvailability() {
        if (mShowQueueItem && !mMediaPlaybackModel.getQueue().isEmpty()) {
            mQueueAvailable = true;
        }
    }

    @Override
    public int getItemCount() {
        int size = mItems.size();
        if (mQueueAvailable) {
            size++;
        }
        return size;
    }

    @Override
    public void populateViewHolder(DrawerItemViewHolder holder, int position) {
        if (mQueueAvailable && position == mItems.size()) {
            holder.getTitle().setText(mMediaPlaybackModel.getQueueTitle());
            return;
        }
        MediaBrowser.MediaItem item = mItems.get(position);
        MediaItemsFetcher.populateViewHolderFrom(holder, item.getDescription());

        // TODO(sriniv): Once we use smallLayout, text and rightIcon fields may be unavailable.
        // Related to b/36573125.
        if (item.isBrowsable()) {
            int iconColor = mActivity.getColor(R.color.car_tint);
            Drawable drawable = mActivity.getDrawable(R.drawable.ic_chevron_right);
            drawable.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
            holder.getRightIcon().setImageDrawable(drawable);
        } else {
            holder.getRightIcon().setImageDrawable(null);
        }
    }

    @Override
    public void onItemClick(int position) {
        if (mQueueAvailable && position == mItems.size()) {
            MediaItemsFetcher fetcher = new MediaQueueItemsFetcher(mActivity, mMediaPlaybackModel);
            setupAdapterAndSwitch(fetcher, mMediaPlaybackModel.getQueueTitle());
            return;
        }

        MediaBrowser.MediaItem item = mItems.get(position);
        if (item.isBrowsable()) {
            MediaItemsFetcher fetcher = new MediaBrowserItemsFetcher(
                    mActivity, mMediaPlaybackModel, item.getMediaId(), false /* showQueueItem */);
            setupAdapterAndSwitch(fetcher, item.getDescription().getTitle());
        } else if (item.isPlayable()) {
            MediaController.TransportControls controls = mMediaPlaybackModel.getTransportControls();
            if (controls != null) {
                controls.pause();
                controls.playFromMediaId(item.getMediaId(), item.getDescription().getExtras());
            }
            mActivity.closeDrawer();
        } else {
            Log.w(TAG, "Unknown item type; don't know how to handle!");
        }
    }

    private void setupAdapterAndSwitch(MediaItemsFetcher fetcher, CharSequence title) {
        MediaDrawerAdapter subAdapter = new MediaDrawerAdapter(mActivity, false /* smallLayout */);
        subAdapter.setFetcher(fetcher);
        subAdapter.setTitle(title);
        mActivity.switchToAdapter(subAdapter);
    }

    @Override
    public void cleanup() {
        mMediaPlaybackModel.removeListener(mModelListener);
        mMediaPlaybackModel.getMediaBrowser().unsubscribe(mMediaId);
        mCallback = null;
    }
}