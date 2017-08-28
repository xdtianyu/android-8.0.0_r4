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

package com.android.tv.dvr.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v17.leanback.widget.BaseCardView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.dvr.RecordedProgram;
import com.android.tv.util.ImageLoader;

/**
 * A CardView for displaying info about a {@link com.android.tv.dvr.ScheduledRecording} or
 * {@link RecordedProgram} or
 * {@link com.android.tv.dvr.SeriesRecording}.
 */
class RecordingCardView extends BaseCardView {
    private final ImageView mImageView;
    private final int mImageWidth;
    private final int mImageHeight;
    private String mImageUri;
    private final TextView mTitleView;
    private final TextView mMajorContentView;
    private final TextView mMinorContentView;
    private final ProgressBar mProgressBar;
    private final View mAffiliatedIconContainer;
    private final ImageView mAffiliatedIcon;
    private final Drawable mDefaultImage;

    RecordingCardView(Context context) {
        this(context,
                context.getResources().getDimensionPixelSize(R.dimen.dvr_card_image_layout_width),
                context.getResources().getDimensionPixelSize(R.dimen.dvr_card_image_layout_height));
    }

    RecordingCardView(Context context, int imageWidth, int imageHeight) {
        super(context);
        //TODO(dvr): move these to the layout XML.
        setCardType(BaseCardView.CARD_TYPE_INFO_UNDER_WITH_EXTRA);
        setInfoVisibility(BaseCardView.CARD_REGION_VISIBLE_ALWAYS);
        setFocusable(true);
        setFocusableInTouchMode(true);
        mDefaultImage = getResources().getDrawable(R.drawable.dvr_default_poster, null);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.dvr_recording_card_view, this);
        mImageView = (ImageView) findViewById(R.id.image);
        mImageWidth = imageWidth;
        mImageHeight = imageHeight;
        mProgressBar = (ProgressBar) findViewById(R.id.recording_progress);
        mAffiliatedIconContainer = findViewById(R.id.affiliated_icon_container);
        mAffiliatedIcon = (ImageView) findViewById(R.id.affiliated_icon);
        mTitleView = (TextView) findViewById(R.id.title);
        mMajorContentView = (TextView) findViewById(R.id.content_major);
        mMinorContentView = (TextView) findViewById(R.id.content_minor);
    }

    void setTitle(CharSequence title) {
        mTitleView.setText(title);
    }

    void setContent(CharSequence majorContent, CharSequence minorContent) {
        if (!TextUtils.isEmpty(majorContent)) {
            mMajorContentView.setText(majorContent);
            mMajorContentView.setVisibility(View.VISIBLE);
        } else {
            mMajorContentView.setVisibility(View.GONE);
        }
        if (!TextUtils.isEmpty(minorContent)) {
            mMinorContentView.setText(minorContent);
            mMinorContentView.setVisibility(View.VISIBLE);
        } else {
            mMinorContentView.setVisibility(View.GONE);
        }
    }

    /**
     * Sets progress bar. If progress is {@code null}, hides progress bar.
     */
    void setProgressBar(Integer progress) {
        if (progress == null) {
            mProgressBar.setVisibility(View.GONE);
        } else {
            mProgressBar.setProgress(progress);
            mProgressBar.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Sets the color of progress bar.
     */
    void setProgressBarColor(int color) {
        mProgressBar.getProgressDrawable().setTint(color);
    }

    void setImageUri(String uri, boolean isChannelLogo) {
        if (isChannelLogo) {
            mImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        } else {
            mImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
        mImageUri = uri;
        if (TextUtils.isEmpty(uri)) {
            mImageView.setImageDrawable(mDefaultImage);
        } else {
            ImageLoader.loadBitmap(getContext(), uri, mImageWidth, mImageHeight,
                    new RecordingCardImageLoaderCallback(this, uri));
        }
    }

    /**
     * Set image to card view.
     */
    public void setImage(Drawable image) {
        if (image != null) {
            mImageView.setImageDrawable(image);
        }
    }

    public void setAffiliatedIcon(int imageResId) {
        if (imageResId > 0) {
            mAffiliatedIconContainer.setVisibility(View.VISIBLE);
            mAffiliatedIcon.setImageResource(imageResId);
        } else {
            mAffiliatedIconContainer.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Returns image view.
     */
    public ImageView getImageView() {
        return mImageView;
    }

    private static class RecordingCardImageLoaderCallback
            extends ImageLoader.ImageLoaderCallback<RecordingCardView> {
        private final String mUri;

        RecordingCardImageLoaderCallback(RecordingCardView referent, String uri) {
            super(referent);
            mUri = uri;
        }

        @Override
        public void onBitmapLoaded(RecordingCardView view, @Nullable Bitmap bitmap) {
            if (bitmap == null || !mUri.equals(view.mImageUri)) {
                view.mImageView.setImageDrawable(view.mDefaultImage);
            } else {
                view.mImageView.setImageDrawable(new BitmapDrawable(view.getResources(), bitmap));
            }
        }
    }

    public void reset() {
        mTitleView.setText(null);
        setContent(null, null);
        mImageView.setImageDrawable(mDefaultImage);
    }
}
