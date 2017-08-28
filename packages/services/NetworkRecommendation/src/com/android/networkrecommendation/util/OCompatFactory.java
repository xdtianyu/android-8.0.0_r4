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
package com.android.networkrecommendation.util;

import android.net.NetworkKey;
import android.net.RssiCurve;
import android.net.ScoredNetwork;
import android.os.Bundle;

/**
 * Encapsulates the constructors for O-only objects to make unit testing possible, since those APIs
 * are not supported by Robolectric yet. Once Robolectric supports O, we can remove this class.
 */
public class OCompatFactory {

    /** Create a ScoredNetwork with the provided parameters. */
    public static ScoredNetwork createScoredNetworkOCompat(
            NetworkKey networkToScore,
            RssiCurve rssiCurve,
            boolean meteredHint,
            Bundle attributesBundle) {
        return new ScoredNetwork(networkToScore, rssiCurve, meteredHint, attributesBundle);
    }

    /** Create an RssiCurve with the provided parameters. */
    public static RssiCurve createRssiCurveOCompat(
            int start, int bucketWidth, byte[] rssiBuckets, int activeNetworkRssiBoost) {
        return new RssiCurve(start, bucketWidth, rssiBuckets, activeNetworkRssiBoost);
    }
}
