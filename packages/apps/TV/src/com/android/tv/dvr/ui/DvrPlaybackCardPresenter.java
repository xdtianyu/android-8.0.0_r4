/*
 * Copyright (c) 2016 The Android Open Source Project
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
import android.content.Intent;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.android.tv.R;
import com.android.tv.dvr.RecordedProgram;
import com.android.tv.dvr.DvrPlaybackActivity;
import com.android.tv.util.Utils;

/**
 * This class is used to generate Views and bind Objects for related recordings in DVR playback.
 */
public class DvrPlaybackCardPresenter extends RecordedProgramPresenter {
    private static final String TAG = "DvrPlaybackCardPresenter";
    private static final boolean DEBUG = false;

    private final int mRelatedRecordingCardWidth;
    private final int mRelatedRecordingCardHeight;

    DvrPlaybackCardPresenter(Context context) {
        super(context);
        mRelatedRecordingCardWidth =
                context.getResources().getDimensionPixelSize(R.dimen.dvr_related_recordings_width);
        mRelatedRecordingCardHeight =
                context.getResources().getDimensionPixelSize(R.dimen.dvr_related_recordings_height);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Resources res = parent.getResources();
        RecordingCardView view = new RecordingCardView(
                getContext(), mRelatedRecordingCardWidth, mRelatedRecordingCardHeight);
        return new ViewHolder(view);
    }

    @Override
    protected OnClickListener onCreateOnClickListener() {
        return new OnClickListener() {
            @Override
            public void onClick(View v) {
                long programId = ((RecordedProgram) v.getTag()).getId();
                if (DEBUG) Log.d(TAG, "Play Related Recording:" + programId);
                Intent intent = new Intent(getContext(), DvrPlaybackActivity.class);
                intent.putExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_ID, programId);
                getContext().startActivity(intent);
            }
        };
    }

    @Override
    protected String getDescription(RecordedProgram program) {
        String description = program.getDescription();
        if (TextUtils.isEmpty(description)) {
            description =
                    getContext().getResources().getString(R.string.dvr_msg_no_program_description);
        }
        return description;
    }
}