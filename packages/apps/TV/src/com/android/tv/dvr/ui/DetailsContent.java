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

package com.android.tv.dvr.ui;

import android.media.tv.TvContract;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.android.tv.data.BaseProgram;
import com.android.tv.data.Channel;

/**
 * A class for details content.
 */
public class DetailsContent {
    /** Constant for invalid time. */
    public static final long INVALID_TIME = -1;

    private CharSequence mTitle;
    private long mStartTimeUtcMillis;
    private long mEndTimeUtcMillis;
    private String mDescription;
    private String mLogoImageUri;
    private String mBackgroundImageUri;

    private DetailsContent() { }

    /**
     * Returns title.
     */
    public CharSequence getTitle() {
        return mTitle;
    }

    /**
     * Returns start time.
     */
    public long getStartTimeUtcMillis() {
        return mStartTimeUtcMillis;
    }

    /**
     * Returns end time.
     */
    public long getEndTimeUtcMillis() {
        return mEndTimeUtcMillis;
    }

    /**
     * Returns description.
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * Returns Logo image URI as a String.
     */
    public String getLogoImageUri() {
        return mLogoImageUri;
    }

    /**
     * Returns background image URI as a String.
     */
    public String getBackgroundImageUri() {
        return mBackgroundImageUri;
    }

    /**
     * Copies other details content.
     */
    public void copyFrom(DetailsContent other) {
        if (this == other) {
            return;
        }
        mTitle = other.mTitle;
        mStartTimeUtcMillis = other.mStartTimeUtcMillis;
        mEndTimeUtcMillis = other.mEndTimeUtcMillis;
        mDescription = other.mDescription;
        mLogoImageUri = other.mLogoImageUri;
        mBackgroundImageUri = other.mBackgroundImageUri;
    }

    /**
     * A class for building details content.
     */
    public static final class Builder {
        private final DetailsContent mDetailsContent;

        public Builder() {
            mDetailsContent = new DetailsContent();
            mDetailsContent.mStartTimeUtcMillis = INVALID_TIME;
            mDetailsContent.mEndTimeUtcMillis = INVALID_TIME;
        }

        /**
         * Sets title.
         */
        public Builder setTitle(CharSequence title) {
            mDetailsContent.mTitle = title;
            return this;
        }

        /**
         * Sets start time.
         */
        public Builder setStartTimeUtcMillis(long startTimeUtcMillis) {
            mDetailsContent.mStartTimeUtcMillis = startTimeUtcMillis;
            return this;
        }

        /**
         * Sets end time.
         */
        public Builder setEndTimeUtcMillis(long endTimeUtcMillis) {
            mDetailsContent.mEndTimeUtcMillis = endTimeUtcMillis;
            return this;
        }

        /**
         * Sets description.
         */
        public Builder setDescription(String description) {
            mDetailsContent.mDescription = description;
            return this;
        }

        /**
         * Sets logo image URI as a String.
         */
        public Builder setLogoImageUri(String logoImageUri) {
            mDetailsContent.mLogoImageUri = logoImageUri;
            return this;
        }

        /**
         * Sets background image URI as a String.
         */
        public Builder setBackgroundImageUri(String backgroundImageUri) {
            mDetailsContent.mBackgroundImageUri = backgroundImageUri;
            return this;
        }

        /**
         * Sets background image and logo image URI from program and channel.
         */
        public Builder setImageUris(@Nullable BaseProgram program, @Nullable Channel channel) {
            if (program != null) {
                return setImageUris(program.getPosterArtUri(), program.getThumbnailUri(), channel);
            } else {
                return setImageUris(null, null, channel);
            }
        }

        /**
         * Sets background image and logo image URI and channel is used for fallback images.
         */
        public Builder setImageUris(@Nullable String posterArtUri,
                @Nullable String thumbnailUri, @Nullable Channel channel) {
            mDetailsContent.mLogoImageUri = null;
            mDetailsContent.mBackgroundImageUri = null;
            if (!TextUtils.isEmpty(posterArtUri) && !TextUtils.isEmpty(thumbnailUri)) {
                mDetailsContent.mLogoImageUri = posterArtUri;
                mDetailsContent.mBackgroundImageUri = thumbnailUri;
            } else if (!TextUtils.isEmpty(posterArtUri)) {
                // thumbnailUri is empty
                mDetailsContent.mLogoImageUri = posterArtUri;
                mDetailsContent.mBackgroundImageUri = posterArtUri;
            } else if (!TextUtils.isEmpty(thumbnailUri)) {
                // posterArtUri is empty
                mDetailsContent.mLogoImageUri = thumbnailUri;
                mDetailsContent.mBackgroundImageUri = thumbnailUri;
            }
            if (TextUtils.isEmpty(mDetailsContent.mLogoImageUri) && channel != null) {
                String channelLogoUri = TvContract.buildChannelLogoUri(channel.getId())
                        .toString();
                mDetailsContent.mLogoImageUri = channelLogoUri;
                mDetailsContent.mBackgroundImageUri = channelLogoUri;
            }
            return this;
        }

        /**
         * Builds details content.
         */
        public DetailsContent build() {
            DetailsContent detailsContent = new DetailsContent();
            detailsContent.copyFrom(mDetailsContent);
            return detailsContent;
        }
    }
}