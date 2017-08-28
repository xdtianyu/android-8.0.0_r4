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

import com.android.car.app.CarDrawerActivity;
import com.android.car.app.CarDrawerAdapter;
import com.android.car.app.DrawerItemViewHolder;

/**
 * Subclass of CarDrawerAdapter used by the Media app.
 * <p>
 * This adapter delegates actual fetching of items (and other operations) to a
 * {@link MediaItemsFetcher}. The current fetcher being used can be updated at runtime.
 */
class MediaDrawerAdapter extends CarDrawerAdapter {
    private final CarDrawerActivity mActivity;
    private MediaItemsFetcher mCurrentFetcher;

    MediaDrawerAdapter(CarDrawerActivity activity, boolean useSmallLayout) {
        super(activity, true /* showDisabledListOnEmpty */, useSmallLayout);
        mActivity = activity;
    }

    /**
     * Switch the {@link MediaItemsFetcher} being used to fetch items. The new fetcher is kicked-off
     * and the drawer's content's will be updated to show newly loaded items. Any old fetcher is
     * cleaned up and released.
     *
     * @param fetcher New {@link MediaItemsFetcher} to use for display Drawer items.
     */
    void setFetcher(MediaItemsFetcher fetcher) {
        if (mCurrentFetcher != null) {
            mCurrentFetcher.cleanup();
        }
        mCurrentFetcher = fetcher;
        mCurrentFetcher.start(() -> {
            mActivity.showLoadingProgressBar(false);
            notifyDataSetChanged();
        });
        // Initially there will be no items and we don't want to show empty-list indicator briefly
        // until items are fetched.
        mActivity.showLoadingProgressBar(true);
    }

    @Override
    protected int getActualItemCount() {
        return mCurrentFetcher != null ? mCurrentFetcher.getItemCount() : 0;
    }

    @Override
    protected void populateViewHolder(DrawerItemViewHolder holder, int position) {
        if (mCurrentFetcher != null) {
            mCurrentFetcher.populateViewHolder(holder, position);
        }
    }

    @Override
    public void onItemClick(int position) {
        if (mCurrentFetcher != null) {
            mCurrentFetcher.onItemClick(position);
        }
    }
}
