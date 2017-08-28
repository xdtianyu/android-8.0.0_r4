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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.dirlist.IconHelper;
import com.android.documentsui.selection.Selection;

import java.util.List;
import java.util.function.Function;

public final class DragShadowBuilder extends View.DragShadowBuilder {

    private final View mShadowView;
    private final TextView mTitle;
    private final DropBadgeView mIcon;
    private final int mWidth;
    private final int mHeight;
    private final int mShadowRadius;
    private int mPadding;
    private Paint paint;

    public DragShadowBuilder(Context context) {
        mWidth = context.getResources().getDimensionPixelSize(R.dimen.drag_shadow_width);
        mHeight = context.getResources().getDimensionPixelSize(R.dimen.drag_shadow_height);
        mShadowRadius = context.getResources().getDimensionPixelSize(R.dimen.drag_shadow_radius);
        mPadding = context.getResources().getDimensionPixelSize(R.dimen.drag_shadow_padding);

        mShadowView = LayoutInflater.from(context).inflate(R.layout.drag_shadow_layout, null);
        mTitle = (TextView) mShadowView.findViewById(android.R.id.title);
        mIcon = (DropBadgeView) mShadowView.findViewById(android.R.id.icon);

        // Important for certain APIs
        mShadowView.setLayerType(View.LAYER_TYPE_SOFTWARE, paint);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    @Override
    public void onProvideShadowMetrics(
            Point shadowSize, Point shadowTouchPoint) {
        shadowSize.set(mWidth, mHeight);
        shadowTouchPoint.set(mWidth, mHeight);
    }

    @Override
    public void onDrawShadow(Canvas canvas) {
        Rect r = canvas.getClipBounds();
        // Calling measure is necessary in order for all child views to get correctly laid out.
        mShadowView.measure(
                View.MeasureSpec.makeMeasureSpec(r.right- r.left, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(r.bottom - r.top , View.MeasureSpec.EXACTLY));
        mShadowView.layout(r.left, r.top, r.right, r.bottom);

        // Since DragShadow is not an actual view drawn in hardware-accelerated window,
        // android:elevation does not work; we need to draw the shadow ourselves manually.
        paint.setColor(Color.TRANSPARENT);
        // Shadow 1
        int opacity = (int) (255 * 0.1);
        paint.setShadowLayer(mShadowRadius, 0, 0, Color.argb(opacity, 0, 0, 0));
        canvas.drawRect(r.left + mPadding, r.top + mPadding, r.right - mPadding,
                r.bottom - mPadding, paint);
        // Shadow 2
        opacity = (int) (255 * 0.24);
        paint.setShadowLayer(mShadowRadius, 0, mShadowRadius, Color.argb(opacity, 0, 0, 0));
        canvas.drawRect(r.left + mPadding, r.top + mPadding, r.right - mPadding,
                r.bottom - mPadding, paint);
        mShadowView.draw(canvas);
    }

    public void updateTitle(String title) {
        mTitle.setText(title);
    }

    public void updateIcon(Drawable icon) {
        mIcon.updateIcon(icon);
    }

    public void resetBackground() {
        mIcon.setDropHovered(false);
        mIcon.setEnabled(false);
    }

    public void setAppearDroppable(boolean droppable) {
        mIcon.setDropHovered(true);
        mIcon.setDroppable(droppable);
    }

    /**
     * Provides a means of fully isolating the mechanics of building drag shadows (and builders)
     * in support of testing.
     */
    public static final class Updater implements Function<Selection, DragShadowBuilder> {

        private final Context mContext;
        private final IconHelper mIconHelper;
        private final Drawable mDefaultDragIcon;
        private final Model mModel;
        private final DragShadowBuilder mShadowBuilder;

        public Updater(
                Context context, DragShadowBuilder shadowBuilder, Model model,
                IconHelper iconHelper, Drawable defaultDragIcon) {
            mContext = context;
            mShadowBuilder = shadowBuilder;
            mModel = model;
            mIconHelper = iconHelper;
            mDefaultDragIcon = defaultDragIcon;
        }

        @Override
        public DragShadowBuilder apply(Selection selection) {
            mShadowBuilder.updateTitle(getDragTitle(selection));
            mShadowBuilder.updateIcon(getDragIcon(selection));

            return mShadowBuilder;
        }

        private Drawable getDragIcon(Selection selection) {
            if (selection.size() == 1) {
                DocumentInfo doc = getSingleSelectedDocument(selection);
                return mIconHelper.getDocumentIcon(mContext, doc);
            }
            return mDefaultDragIcon;
        }

        private String getDragTitle(Selection selection) {
            assert (!selection.isEmpty());
            if (selection.size() == 1) {
                DocumentInfo doc = getSingleSelectedDocument(selection);
                return doc.displayName;
            }
            return Shared.getQuantityString(mContext, R.plurals.elements_dragged, selection.size());
        }

        private DocumentInfo getSingleSelectedDocument(Selection selection) {
            assert (selection.size() == 1);
            final List<DocumentInfo> docs = mModel.getDocuments(selection);
            assert (docs.size() == 1);
            return docs.get(0);
        }
    }
}
