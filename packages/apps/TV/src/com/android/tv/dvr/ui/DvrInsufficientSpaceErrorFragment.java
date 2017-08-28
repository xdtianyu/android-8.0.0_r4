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
import android.content.Intent;
import android.os.Bundle;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.dvr.DvrDataManager;

import java.util.List;

public class DvrInsufficientSpaceErrorFragment extends DvrGuidedStepFragment {
    private static final int ACTION_DONE = 1;
    private static final int ACTION_OPEN_DVR = 2;

    @Override
    public Guidance onCreateGuidance(Bundle savedInstanceState) {
        String title = getResources().getString(R.string.dvr_error_insufficient_space_title);
        String description = getResources()
                .getString(R.string.dvr_error_insufficient_space_description);
        return new Guidance(title, description, null, null);
    }

    @Override
    public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
        Activity activity = getActivity();
        actions.add(new GuidedAction.Builder(activity)
                .id(ACTION_DONE)
                .title(getResources().getString(R.string.dvr_action_error_done))
                .build());
        DvrDataManager dvrDataManager = TvApplication.getSingletons(getContext())
                .getDvrDataManager();
        if (!(dvrDataManager.getRecordedPrograms().isEmpty()
                && dvrDataManager.getStartedRecordings().isEmpty()
                && dvrDataManager.getNonStartedScheduledRecordings().isEmpty()
                && dvrDataManager.getSeriesRecordings().isEmpty())) {
                    actions.add(new GuidedAction.Builder(activity)
                            .id(ACTION_OPEN_DVR)
                            .title(getResources().getString(R.string.dvr_action_error_open_dvr))
                            .build());
        }
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (action.getId() == ACTION_OPEN_DVR) {
            Intent intent = new Intent(getActivity(), DvrActivity.class);
            getActivity().startActivity(intent);
        }
        dismissDialog();
    }
}
