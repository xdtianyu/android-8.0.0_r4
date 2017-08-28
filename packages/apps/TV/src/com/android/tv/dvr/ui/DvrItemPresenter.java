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

import android.app.Activity;
import android.support.annotation.CallSuper;
import android.support.v17.leanback.widget.Presenter;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.tv.dvr.DvrUiHelper;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * An abstract class to present DVR items in {@link RecordingCardView}, which is mainly used in
 * {@link DvrBrowseFragment}. DVR items might include: {@link ScheduledRecording},
 * {@link RecordedProgram}, and {@link SeriesRecording}.
 */
public abstract class DvrItemPresenter extends Presenter {
    private final Set<ViewHolder> mBoundViewHolders = new HashSet<>();
    private final OnClickListener mOnClickListener = onCreateOnClickListener();

    @Override
    @CallSuper
    public void onBindViewHolder(ViewHolder viewHolder, Object o) {
        viewHolder.view.setTag(o);
        viewHolder.view.setOnClickListener(mOnClickListener);
        mBoundViewHolders.add(viewHolder);
    }

    @Override
    @CallSuper
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        mBoundViewHolders.remove(viewHolder);
    }

    /**
     * Unbinds all bound view holders.
     */
    public void unbindAllViewHolders() {
        // When browse fragments are destroyed, RecyclerView would not call presenters'
        // onUnbindViewHolder(). We should handle it by ourselves to prevent resources leaks.
        for (ViewHolder viewHolder : new HashSet<>(mBoundViewHolders)) {
            onUnbindViewHolder(viewHolder);
        }
    }

    /**
     * Creates {@link OnClickListener} for DVR library's card views.
     */
    protected OnClickListener onCreateOnClickListener() {
        return new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (view instanceof RecordingCardView) {
                    RecordingCardView v = (RecordingCardView) view;
                    DvrUiHelper.startDetailsActivity((Activity) v.getContext(),
                            v.getTag(), v.getImageView(), false);
                }
            }
        };
    }
}