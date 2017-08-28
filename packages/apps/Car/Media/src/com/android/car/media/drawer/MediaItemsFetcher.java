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

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaDescription;

import com.android.car.app.DrawerItemViewHolder;
import com.android.car.apps.common.BitmapDownloader;
import com.android.car.apps.common.BitmapWorkerOptions;
import com.android.car.apps.common.UriUtils;
import com.android.car.media.R;

/**
 * Component that handles fetching of items for {@link MediaDrawerAdapter}.
 * <p>
 * It also handles ViewHolder population and item clicks.
 */
interface MediaItemsFetcher {
    /**
     * Used to inform owning {@link MediaDrawerAdapter} that items have changed.
     */
    interface ItemsUpdatedCallback {
        void onItemsUpdated();
    }

    /**
     * Kick-off fetching/monitoring of items.
     *
     * @param callback Callback that is invoked when items are first loaded ar if they change
     *                 subsequently.
     */
    void start(ItemsUpdatedCallback callback);

    /**
     * @return Number of items currently fetched.
     */
    int getItemCount();

    /**
     * Used by owning {@link MediaDrawerAdapter} to populate views.
     *
     * @param holder View-holder to populate.
     * @param position Item position.
     */
    void populateViewHolder(DrawerItemViewHolder holder, int position);

    /**
     * Used by owning {@link MediaDrawerAdapter} to handle clicks.
     *
     * @param position Item position.
     */
    void onItemClick(int position);

    /**
     * Used when this instance is going to be released. Subclasses should release resources.
     */
    void cleanup();

    /**
     * Utility method to populate {@code holder} with details from {@code description}. It populates
     * title, text and icon at most.
     */
    static void populateViewHolderFrom(DrawerItemViewHolder holder, MediaDescription description) {
        Context context = holder.itemView.getContext();
        // TODO(sriniv): Once we use smallLayout, text and rightIcon fields may be unavailable.
        // Related to b/36573125.
        holder.getTitle().setText(description.getTitle());
        holder.getText().setText(description.getSubtitle());
        Bitmap iconBitmap = description.getIconBitmap();
        holder.getIcon().setImageBitmap(iconBitmap);    // Ok to set null here for clearing.
        if (iconBitmap == null && description.getIconUri() != null) {
            int bitmapSize =
                    context.getResources().getDimensionPixelSize(R.dimen.car_list_item_icon_size);
            // We don't want to cache android resources as they are needed to be refreshed after
            // configuration changes.
            int cacheFlag = UriUtils.isAndroidResourceUri(description.getIconUri())
                    ? (BitmapWorkerOptions.CACHE_FLAG_DISK_DISABLED
                    | BitmapWorkerOptions.CACHE_FLAG_MEM_DISABLED)
                    : 0;
            BitmapWorkerOptions options = new BitmapWorkerOptions.Builder(context)
                    .resource(description.getIconUri())
                    .height(bitmapSize)
                    .width(bitmapSize)
                    .cacheFlag(cacheFlag)
                    .build();
            BitmapDownloader.getInstance(context).loadBitmap(options, holder.getIcon());
        }
    }
}