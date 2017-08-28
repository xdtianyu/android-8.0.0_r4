/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v7.graphics.Palette;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.data.Channel;
import com.android.tv.util.BitmapUtils;
import com.android.tv.util.ImageLoader;
import com.android.tv.util.TvInputManagerHelper;

/**
 * A view to render an app link card.
 */
public class AppLinkCardView extends BaseCardView<Channel> {
    private static final String TAG = MenuView.TAG;
    private static final boolean DEBUG = MenuView.DEBUG;

    private final int mCardImageWidth;
    private final int mCardImageHeight;
    private final int mIconWidth;
    private final int mIconHeight;
    private final int mIconPadding;
    private final int mIconColorFilter;

    private ImageView mImageView;
    private View mGradientView;
    private TextView mAppInfoView;
    private View mMetaViewHolder;
    private Channel mChannel;
    private Intent mIntent;
    private final PackageManager mPackageManager;
    private final TvInputManagerHelper mTvInputManagerHelper;

    public AppLinkCardView(Context context) {
        this(context, null);
    }

    public AppLinkCardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLinkCardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mCardImageWidth = getResources().getDimensionPixelSize(R.dimen.card_image_layout_width);
        mCardImageHeight = getResources().getDimensionPixelSize(R.dimen.card_image_layout_height);
        mIconWidth = getResources().getDimensionPixelSize(R.dimen.app_link_card_icon_width);
        mIconHeight = getResources().getDimensionPixelSize(R.dimen.app_link_card_icon_height);
        mIconPadding = getResources().getDimensionPixelOffset(R.dimen.app_link_card_icon_padding);
        mPackageManager = context.getPackageManager();
        mTvInputManagerHelper = ((MainActivity) context).getTvInputManagerHelper();
        mIconColorFilter = getResources().getColor(R.color.app_link_card_icon_color_filter, null);
    }

    /**
     * Returns the intent which will be started once this card is clicked.
     */
    public Intent getIntent() {
        return mIntent;
    }

    @Override
    public void onBind(Channel channel, boolean selected) {
        if (DEBUG) {
            Log.d(TAG, "onBind(channelName=" + channel.getDisplayName() + ", selected=" + selected
                    + ")");
        }
        mChannel = channel;
        ApplicationInfo appInfo = mTvInputManagerHelper.getTvInputAppInfo(mChannel.getInputId());
        int linkType = mChannel.getAppLinkType(getContext());
        mIntent = mChannel.getAppLinkIntent(getContext());

        switch (linkType) {
            case Channel.APP_LINK_TYPE_CHANNEL:
                setText(mChannel.getAppLinkText());
                mAppInfoView.setVisibility(VISIBLE);
                mGradientView.setVisibility(VISIBLE);
                mAppInfoView.setCompoundDrawablePadding(mIconPadding);
                mAppInfoView.setCompoundDrawables(null, null, null, null);
                mAppInfoView.setText(mPackageManager.getApplicationLabel(appInfo));
                if (!TextUtils.isEmpty(mChannel.getAppLinkIconUri())) {
                    mChannel.loadBitmap(getContext(), Channel.LOAD_IMAGE_TYPE_APP_LINK_ICON,
                            mIconWidth, mIconHeight, createChannelLogoCallback(this, mChannel,
                                    Channel.LOAD_IMAGE_TYPE_APP_LINK_ICON));
                } else if (appInfo.icon != 0) {
                    Drawable appIcon = mPackageManager.getApplicationIcon(appInfo);
                    BitmapUtils.setColorFilterToDrawable(mIconColorFilter, appIcon);
                    appIcon.setBounds(0, 0, mIconWidth, mIconHeight);
                    mAppInfoView.setCompoundDrawables(appIcon, null, null, null);
                }
                break;
            case Channel.APP_LINK_TYPE_APP:
                setText(getContext().getString(
                        R.string.channels_item_app_link_app_launcher,
                        mPackageManager.getApplicationLabel(appInfo)));
                mAppInfoView.setVisibility(GONE);
                mGradientView.setVisibility(GONE);
                break;
            default:
                mAppInfoView.setVisibility(GONE);
                mGradientView.setVisibility(GONE);
                Log.d(TAG, "Should not be here.");
        }

        if (mChannel.getAppLinkColor() == 0) {
            mMetaViewHolder.setBackgroundResource(R.color.channel_card_meta_background);
        } else {
            mMetaViewHolder.setBackgroundColor(mChannel.getAppLinkColor());
        }

        if (!TextUtils.isEmpty(mChannel.getAppLinkPosterArtUri())) {
            mImageView.setImageResource(R.drawable.ic_recent_thumbnail_default);
            mChannel.loadBitmap(getContext(), Channel.LOAD_IMAGE_TYPE_APP_LINK_POSTER_ART,
                    mCardImageWidth, mCardImageHeight, createChannelLogoCallback(this, mChannel,
                            Channel.LOAD_IMAGE_TYPE_APP_LINK_POSTER_ART));
        } else {
            setCardImageWithBanner(appInfo);
        }
        // Call super.onBind() at the end intentionally. In order to correctly handle extension of
        // text view, text should be set before calling super.onBind.
        super.onBind(channel, selected);
    }

    private static ImageLoader.ImageLoaderCallback<AppLinkCardView> createChannelLogoCallback(
            AppLinkCardView cardView, final Channel channel, final int type) {
        return new ImageLoader.ImageLoaderCallback<AppLinkCardView>(cardView) {
            @Override
            public void onBitmapLoaded(AppLinkCardView cardView, @Nullable Bitmap bitmap) {
                // mChannel can be changed before the image load finished.
                if (!cardView.mChannel.hasSameReadOnlyInfo(channel)) {
                    return;
                }
                cardView.updateChannelLogo(bitmap, type);
            }
        };
    }

    private void updateChannelLogo(@Nullable Bitmap bitmap, int type) {
        if (type == Channel.LOAD_IMAGE_TYPE_APP_LINK_ICON) {
            BitmapDrawable drawable = null;
            if (bitmap != null) {
                drawable = new BitmapDrawable(getResources(), bitmap);
                if (bitmap.getWidth() > bitmap.getHeight()) {
                    drawable.setBounds(0, 0, mIconWidth,
                            mIconWidth * bitmap.getHeight() / bitmap.getWidth());
                } else {
                    drawable.setBounds(0, 0,
                            mIconHeight * bitmap.getWidth() / bitmap.getHeight(),
                            mIconHeight);
                }
            }
            BitmapUtils.setColorFilterToDrawable(mIconColorFilter, drawable);
            mAppInfoView.setCompoundDrawables(drawable, null, null, null);
        } else if (type == Channel.LOAD_IMAGE_TYPE_APP_LINK_POSTER_ART) {
            if (bitmap == null) {
                setCardImageWithBanner(
                        mTvInputManagerHelper.getTvInputAppInfo(mChannel.getInputId()));
            } else {
                mImageView.setImageBitmap(bitmap);
                if (mChannel.getAppLinkColor() == 0) {
                    extractAndSetMetaViewBackgroundColor(bitmap);
                }
            }
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mImageView = (ImageView) findViewById(R.id.image);
        mGradientView = findViewById(R.id.image_gradient);
        mAppInfoView = (TextView) findViewById(R.id.app_info);
        mMetaViewHolder = findViewById(R.id.app_link_text_holder);
    }

    // Try to set the card image with following order:
    // 1) Provided poster art image, 2) Activity banner, 3) Activity icon, 4) Application banner,
    // 5) Application icon, and 6) default image.
    private void setCardImageWithBanner(ApplicationInfo appInfo) {
        Drawable banner = null;
        if (mIntent != null) {
            try {
                banner = mPackageManager.getActivityBanner(mIntent);
                if (banner == null) {
                    banner = mPackageManager.getActivityIcon(mIntent);
                }
            } catch (PackageManager.NameNotFoundException e) {
                // do nothing.
            }
        }

        if (banner == null && appInfo != null) {
            if (appInfo.banner != 0) {
                banner = mPackageManager.getApplicationBanner(appInfo);
            }
            if (banner == null && appInfo.icon != 0) {
                banner = mPackageManager.getApplicationIcon(appInfo);
            }
        }

        if (banner == null) {
            mImageView.setImageResource(R.drawable.ic_recent_thumbnail_default);
            mImageView.setBackgroundResource(R.color.channel_card);
        } else {
            Bitmap bitmap =
                    Bitmap.createBitmap(mCardImageWidth, mCardImageHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            banner.setBounds(0, 0, mCardImageWidth, mCardImageHeight);
            banner.draw(canvas);
            mImageView.setImageDrawable(banner);
            if (mChannel.getAppLinkColor() == 0) {
                extractAndSetMetaViewBackgroundColor(bitmap);
            }
        }
    }

    private void extractAndSetMetaViewBackgroundColor(Bitmap bitmap) {
        new Palette.Builder(bitmap).generate(new Palette.PaletteAsyncListener() {
            @Override
            public void onGenerated(Palette palette) {
                mMetaViewHolder.setBackgroundColor(palette.getDarkVibrantColor(
                        getResources().getColor(R.color.channel_card_meta_background, null)));
            }
        });
    }
}
