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
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;
import android.text.TextUtils;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrManager;

import java.util.List;

public class DvrForgetStorageErrorFragment extends DvrGuidedStepFragment {
    private static final int ACTION_CANCEL = 1;
    private static final int ACTION_FORGET_STORAGE = 2;
    private String mInputId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args != null) {
            mInputId = args.getString(DvrHalfSizedDialogFragment.KEY_INPUT_ID);
        }
        SoftPreconditions.checkArgument(!TextUtils.isEmpty(mInputId));
        super.onCreate(savedInstanceState);
    }

    @NonNull
    @Override
    public Guidance onCreateGuidance(Bundle savedInstanceState) {
        String title = getResources().getString(R.string.dvr_error_forget_storage_title);
        String description = getResources().getString(
                R.string.dvr_error_forget_storage_description);
        return new Guidance(title, description, null, null);
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        Activity activity = getActivity();
        actions.add(new GuidedAction.Builder(activity)
                .id(ACTION_CANCEL)
                .title(getResources().getString(R.string.dvr_action_error_cancel))
                .build());
        actions.add(new GuidedAction.Builder(activity)
                .id(ACTION_FORGET_STORAGE)
                .title(getResources().getString(R.string.dvr_action_error_forget_storage))
                .build());
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (action.getId() != ACTION_FORGET_STORAGE) {
            dismissDialog();
            return;
        }
        DvrManager dvrManager = TvApplication.getSingletons(getContext()).getDvrManager();
        dvrManager.forgetStorage(mInputId);
        Activity activity = getActivity();
        if (activity instanceof DvrDetailsActivity) {
            // Since we removed everything, just finish the activity.
            activity.finish();
        } else {
            dismissDialog();
        }
    }
}
