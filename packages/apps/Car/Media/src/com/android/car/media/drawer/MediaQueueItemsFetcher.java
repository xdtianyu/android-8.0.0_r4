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
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.support.annotation.Nullable;

import com.android.car.app.CarDrawerActivity;
import com.android.car.app.DrawerItemViewHolder;
import com.android.car.media.MediaPlaybackModel;
import com.android.car.media.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link MediaItemsFetcher} implementation that fetches items from the {@link MediaController}'s
 * currently playing queue.
 */
class MediaQueueItemsFetcher implements MediaItemsFetcher {
    private static final String TAG = "MediaQueueItemsFetcher";

    private final Handler mHandler = new Handler();
    private final CarDrawerActivity mActivity;
    private MediaPlaybackModel mMediaPlaybackModel;
    private ItemsUpdatedCallback mCallback;
    private List<MediaSession.QueueItem> mItems = new ArrayList<>();

    MediaQueueItemsFetcher(CarDrawerActivity activity, MediaPlaybackModel model) {
        mActivity = activity;
        mMediaPlaybackModel = model;
    }

    @Override
    public void start(ItemsUpdatedCallback callback) {
        mCallback = callback;
        if (mMediaPlaybackModel != null) {
            mMediaPlaybackModel.addListener(listener);
            updateItemsFrom(mMediaPlaybackModel.getQueue());
        }
        // Inform client of current items. Invoke async to avoid re-entrancy issues.
        mHandler.post(mCallback::onItemsUpdated);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public void populateViewHolder(DrawerItemViewHolder holder, int position) {
        MediaSession.QueueItem item = mItems.get(position);
        MediaItemsFetcher.populateViewHolderFrom(holder, item.getDescription());

        if (item.getQueueId() == getActiveQueueItemId()) {
            int primaryColor = mMediaPlaybackModel.getPrimaryColor();
            Drawable drawable =
                    mActivity.getResources().getDrawable(R.drawable.ic_music_active);
            drawable.setColorFilter(primaryColor, PorterDuff.Mode.SRC_IN);
            holder.getRightIcon().setImageDrawable(drawable);
        } else {
            holder.getRightIcon().setImageBitmap(null);
        }
    }

    @Override
    public void onItemClick(int position) {
        MediaController.TransportControls controls = mMediaPlaybackModel.getTransportControls();
        if (controls != null) {
            controls.skipToQueueItem(mItems.get(position).getQueueId());
        }
        mActivity.closeDrawer();
    }

    @Override
    public void cleanup() {
        mMediaPlaybackModel.removeListener(listener);
    }

    private void updateItemsFrom(List<MediaSession.QueueItem> queue) {
        mItems.clear();
        // We only show items starting from active-item in the queue.
        final long activeItemId = getActiveQueueItemId();
        boolean activeItemFound = false;
        for (MediaSession.QueueItem item : queue) {
            if (activeItemId == item.getQueueId()) {
                activeItemFound = true;
            }
            if (activeItemFound) {
                mItems.add(item);
            }
        }
    }

    private long getActiveQueueItemId() {
        if (mMediaPlaybackModel != null) {
            PlaybackState playbackState = mMediaPlaybackModel.getPlaybackState();
            if (playbackState != null) {
                return playbackState.getActiveQueueItemId();
            }
        }
        return MediaSession.QueueItem.UNKNOWN_ID;
    }

    private final MediaPlaybackModel.Listener listener = new MediaPlaybackModel.AbstractListener() {
        @Override
        public void onQueueChanged(List<MediaSession.QueueItem> queue) {
            updateItemsFrom(queue);
            mCallback.onItemsUpdated();
        }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            // Since active playing item may have changed, force re-draw of queue items.
            mCallback.onItemsUpdated();
        }

        @Override
        public void onSessionDestroyed(CharSequence destroyedMediaClientName) {
            onQueueChanged(Collections.emptyList());
        }
    };
}
