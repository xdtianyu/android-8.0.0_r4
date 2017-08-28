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

package com.android.tv.menu;

import android.content.Context;
import android.support.annotation.Nullable;

import com.android.tv.ChannelTuner;
import com.android.tv.data.Channel;
import com.android.tv.ui.TunableTvView;
import com.android.tv.ui.TunableTvView.OnScreenBlockingChangedListener;

/**
 * Update menu items when needed.
 *
 * <p>As the menu is updated when it shows up, this class handles only the dynamic updates.
 */
public class MenuUpdater {
    // Can be null for testing.
    @Nullable
    private final TunableTvView mTvView;
    private final Menu mMenu;
    private ChannelTuner mChannelTuner;

    private final ChannelTuner.Listener mChannelTunerListener = new ChannelTuner.Listener() {
        @Override
        public void onLoadFinished() {}

        @Override
        public void onBrowsableChannelListChanged() {
            mMenu.update();
        }

        @Override
        public void onCurrentChannelUnavailable(Channel channel) {}

        @Override
        public void onChannelChanged(Channel previousChannel, Channel currentChannel) {
            mMenu.update(ChannelsRow.ID);
        }
    };

    public MenuUpdater(Context context, TunableTvView tvView, Menu menu) {
        mTvView = tvView;
        mMenu = menu;
        if (mTvView != null) {
            mTvView.setOnScreenBlockedListener(new OnScreenBlockingChangedListener() {
                    @Override
                    public void onScreenBlockingChanged(boolean blocked) {
                        mMenu.update(PlayControlsRow.ID);
                    }
            });
        }
    }

    /**
     * Sets the instance of {@link ChannelTuner}. Call this method when the channel tuner is ready
     * or not available any more.
     */
    public void setChannelTuner(ChannelTuner channelTuner) {
        if (mChannelTuner != null) {
            mChannelTuner.removeListener(mChannelTunerListener);
        }
        mChannelTuner = channelTuner;
        if (mChannelTuner != null) {
            mChannelTuner.addListener(mChannelTunerListener);
        }
        mMenu.update();
    }

    /**
     * Called at the end of the menu's lifetime.
     */
    public void release() {
        if (mChannelTuner != null) {
            mChannelTuner.removeListener(mChannelTunerListener);
        }
        if (mTvView != null) {
            mTvView.setOnScreenBlockedListener(null);
        }
    }
}
