/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.verifier.audio;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.android.cts.verifier.audio.peripheralprofile.ListsHelper;
import com.android.cts.verifier.audio.peripheralprofile.PeripheralProfile;

import com.android.cts.verifier.R;  // needed to access resource in CTSVerifier project namespace.

public class USBAudioPeripheralAttributesActivity extends USBAudioPeripheralActivity {
    private static final String TAG = "USBAudioPeripheralAttributesActivity";

    private TextView mTestStatusTx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.uap_attribs_panel);

        connectPeripheralStatusWidgets();

        mTestStatusTx = (TextView)findViewById(R.id.uap_attribsStatusTx);

        setPassFailButtonClickListeners();
        setInfoResources(R.string.usbaudio_attribs_test, R.string.usbaudio_attribs_info, -1);
    }

    //
    // USBAudioPeripheralActivity
    //
    public void updateConnectStatus() {
        boolean outPass = false;
        boolean inPass = false;
        if (mIsPeripheralAttached && mSelectedProfile != null) {
            boolean match = true;
            StringBuilder metaSb = new StringBuilder();

            // Outputs
            if (mOutputDevInfo != null) {
                AudioDeviceInfo deviceInfo = mOutputDevInfo;
                PeripheralProfile.ProfileAttributes attribs =
                    mSelectedProfile.getOutputAttributes();
                StringBuilder sb = new StringBuilder();

                if (!ListsHelper.isMatch(deviceInfo.getChannelCounts(), attribs.mChannelCounts)) {
                    sb.append("Output - Channel Counts Mismatch\n");
                }
                if (!ListsHelper.isMatch(deviceInfo.getChannelIndexMasks(),
                                         attribs.mChannelIndexMasks)) {
                    sb.append("Output - Channel Index Masks Mismatch\n");
                }
                if (!ListsHelper.isMatch(deviceInfo.getChannelMasks(),
                                         attribs.mChannelPositionMasks)) {
                    sb.append("Output - Channel Position Masks Mismatch\n");
                }
                if (!ListsHelper.isMatch(deviceInfo.getEncodings(), attribs.mEncodings)) {
                    sb.append("Output - Encodings Mismatch\n");
                }
                if (!ListsHelper.isMatch(deviceInfo.getSampleRates(), attribs.mSampleRates)) {
                    sb.append("Output - Sample Rates Mismatch\n");
                }

                if (sb.toString().length() == 0){
                    metaSb.append("Output - Match\n");
                    outPass = true;
                } else {
                    metaSb.append(sb.toString());
                }
            }

            // Inputs
            if (mInputDevInfo != null) {
                AudioDeviceInfo deviceInfo = mInputDevInfo;
                PeripheralProfile.ProfileAttributes attribs =
                    mSelectedProfile.getInputAttributes();
                StringBuilder sb = new StringBuilder();

                if (!ListsHelper.isMatch(deviceInfo.getChannelCounts(), attribs.mChannelCounts)) {
                    sb.append("Input - Channel Counts Mismatch\n");
                }
                if (!ListsHelper.isMatch(deviceInfo.getChannelIndexMasks(),
                                         attribs.mChannelIndexMasks)) {
                    sb.append("Input - Channel Index Masks Mismatch\n");
                }
                if (!ListsHelper.isMatch(deviceInfo.getChannelMasks(),
                                         attribs.mChannelPositionMasks)) {
                    sb.append("Input - Channel Position Masks Mismatch\n");
                }
                if (!ListsHelper.isMatch(deviceInfo.getEncodings(), attribs.mEncodings)) {
                    sb.append("Input - Encodings Mismatch\n");
                }
                if (!ListsHelper.isMatch(deviceInfo.getSampleRates(), attribs.mSampleRates)) {
                    sb.append("Input - Sample Rates Mismatch\n");
                }

                if (sb.toString().length() == 0){
                    inPass = true;
                    metaSb.append("Input - Match\n");
                } else {
                    metaSb.append(sb.toString());
                }
            }

            mTestStatusTx.setText(metaSb.toString());
        } else {
            mTestStatusTx.setText("No Peripheral or No Matching Profile.");
        }

        //TODO we need to support output-only and input-only peripherals
        getPassButton().setEnabled(outPass && inPass);
    }
}
