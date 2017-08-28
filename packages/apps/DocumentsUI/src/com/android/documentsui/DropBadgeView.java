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

package com.android.documentsui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;

/**
 * Provides a way to encapsulate droppable badge toggling logic into a single class.
 */
public final class DropBadgeView extends ImageView {
    private static final int[] STATE_DROPPABLE = {R.attr.state_droppable};
    private static final int[] STATE_DROP_HOVERED = {R.attr.state_drop_hovered};

    private boolean mDroppable = false;
    private boolean mDropHovered = false;
    private LayerDrawable mBackground;

    public DropBadgeView(Context context, AttributeSet attrs) {
        super(context, attrs);

        final int badgeHeight = context.getResources()
                .getDimensionPixelSize(R.dimen.drop_icon_height);
        final int badgeWidth = context.getResources()
                .getDimensionPixelSize(R.dimen.drop_icon_width);
        final int iconSize = context.getResources().getDimensionPixelSize(R.dimen.root_icon_size);

        Drawable okBadge = context.getResources().getDrawable(R.drawable.drop_badge_states, null);
        Drawable defaultIcon = context.getResources()
                .getDrawable(R.drawable.ic_doc_generic, null);

        Drawable[] list = {defaultIcon, okBadge};
        mBackground = new LayerDrawable(list);

        mBackground.setLayerGravity(1, Gravity.BOTTOM | Gravity.RIGHT);
        mBackground.setLayerGravity(0, Gravity.TOP | Gravity.LEFT);
        mBackground.setLayerSize(1, badgeWidth, badgeHeight);
        mBackground.setLayerSize(0, iconSize, iconSize);

        setBackground(mBackground);
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 2);

        if (mDroppable) {
            mergeDrawableStates(drawableState, STATE_DROPPABLE);
        }

        if (mDropHovered) {
            mergeDrawableStates(drawableState, STATE_DROP_HOVERED);
        }

        return drawableState;
    }

    public void setDroppable(boolean droppable) {
        mDroppable = droppable;
        refreshDrawableState();
    }

    public void setDropHovered(boolean hovered) {
        mDropHovered = hovered;
        refreshDrawableState();
    }

    public void updateIcon(Drawable icon) {
        mBackground.setDrawable(0, icon);
    }
}