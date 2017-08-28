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
package com.android.networkrecommendation.wakeup;

import android.content.res.Resources;
import android.net.RecommendationRequest;
import android.net.RecommendationResult;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.support.annotation.Nullable;
import android.util.ArraySet;
import com.android.networkrecommendation.R;
import com.android.networkrecommendation.SynchronousNetworkRecommendationProvider;
import com.android.networkrecommendation.util.RoboCompatUtil;
import com.android.networkrecommendation.util.ScanResultUtil;
import com.android.networkrecommendation.util.WifiConfigurationUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** This class determines which network the framework would connect to if Wi-Fi was enabled. */
public class WifiWakeupNetworkSelector {
    private final int mThresholdQualifiedRssi24;
    private final int mThresholdQualifiedRssi5;
    private final int mRssiScoreSlope;
    private final int mRssiScoreOffset;
    private final int mPasspointSecurityAward;
    private final int mSecurityAward;
    private final int mBand5GHzAward;
    private final int mThresholdSaturatedRssi24;
    private final SynchronousNetworkRecommendationProvider mNetworkRecommendationProvider;

    public WifiWakeupNetworkSelector(
            Resources resources,
            SynchronousNetworkRecommendationProvider networkRecommendationProvider) {
        mThresholdQualifiedRssi24 =
                resources.getInteger(R.integer.config_netrec_wifi_score_low_rssi_threshold_24GHz);
        mThresholdQualifiedRssi5 =
                resources.getInteger(R.integer.config_netrec_wifi_score_low_rssi_threshold_5GHz);
        mRssiScoreSlope = resources.getInteger(R.integer.config_netrec_RSSI_SCORE_SLOPE);
        mRssiScoreOffset = resources.getInteger(R.integer.config_netrec_RSSI_SCORE_OFFSET);
        mPasspointSecurityAward =
                resources.getInteger(R.integer.config_netrec_PASSPOINT_SECURITY_AWARD);
        mSecurityAward = resources.getInteger(R.integer.config_netrec_SECURITY_AWARD);
        mBand5GHzAward = resources.getInteger(R.integer.config_netrec_5GHz_preference_boost_factor);
        mThresholdSaturatedRssi24 =
                resources.getInteger(R.integer.config_netrec_wifi_score_good_rssi_threshold_24GHz);
        mNetworkRecommendationProvider = networkRecommendationProvider;
    }

    /** Returns the network that the framework would most likely connect to if Wi-Fi was enabled. */
    @Nullable
    public WifiConfiguration selectNetwork(
            Map<String, WifiConfiguration> savedNetworks, List<ScanResult> scanResults) {
        Set<WifiConfiguration> openOrExternalConfigs = new ArraySet<>();
        List<ScanResult> openOrExternalScanResults = new ArrayList<>();
        WifiConfiguration candidateWifiConfiguration = null;
        ScanResult candidateScanResult = null;
        int candidateScore = -1;
        for (int i = 0; i < scanResults.size(); i++) {
            ScanResult scanResult = scanResults.get(i);
            WifiConfiguration wifiConfiguration = savedNetworks.get(scanResult.SSID);
            if (wifiConfiguration == null) {
                continue;
            }
            if ((ScanResultUtil.is5GHz(scanResult) && scanResult.level < mThresholdQualifiedRssi5)
                    || (ScanResultUtil.is24GHz(scanResult)
                            && scanResult.level < mThresholdQualifiedRssi24)) {
                continue;
            }
            if (!ScanResultUtil.doesScanResultMatchWithNetwork(scanResult, wifiConfiguration)) {
                continue;
            }
            if (WifiConfigurationUtil.isConfigForOpenNetwork(wifiConfiguration)
                    || RoboCompatUtil.getInstance().useExternalScores(wifiConfiguration)) {
                // All open and externally scored networks should defer to network recommendations.
                openOrExternalConfigs.add(wifiConfiguration);
                openOrExternalScanResults.add(scanResult);
                continue;
            }
            int score = calculateScore(scanResult, wifiConfiguration);
            if (candidateScanResult == null
                    || calculateScore(scanResult, wifiConfiguration) > candidateScore) {
                candidateScanResult = scanResult;
                candidateWifiConfiguration = wifiConfiguration;
                candidateScore = score;
            }
        }
        if (candidateWifiConfiguration == null && !openOrExternalConfigs.isEmpty()) {
            // TODO(netrec): Add connectableConfigs after next SystemApi drop
            RecommendationRequest request =
                    new RecommendationRequest.Builder()
                            .setScanResults(openOrExternalScanResults.toArray(new ScanResult[0]))
                            .build();
            RecommendationResult result =
                    mNetworkRecommendationProvider.requestRecommendation(request);
            return result.getWifiConfiguration();
        }
        return candidateWifiConfiguration;
    }

    private int calculateScore(ScanResult scanResult, WifiConfiguration wifiConfiguration) {
        int score = 0;
        // Calculate the RSSI score.
        int rssi =
                scanResult.level <= mThresholdSaturatedRssi24
                        ? scanResult.level
                        : mThresholdSaturatedRssi24;
        score += (rssi + mRssiScoreOffset) * mRssiScoreSlope;

        // 5GHz band bonus.
        if (ScanResultUtil.is5GHz(scanResult)) {
            score += mBand5GHzAward;
        }

        // Security award.
        if (RoboCompatUtil.getInstance().isPasspoint(wifiConfiguration)) {
            score += mPasspointSecurityAward;
        } else if (!WifiConfigurationUtil.isConfigForOpenNetwork(wifiConfiguration)) {
            score += mSecurityAward;
        }

        return score;
    }
}
