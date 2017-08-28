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

package com.android.networkrecommendation;

import android.net.NetworkKey;
import android.net.RecommendationRequest;
import android.net.RecommendationResult;
import android.net.ScoredNetwork;

/**
 * Provider to return {@link ScoredNetwork} from cached scores in NetworkRecommendationProvider.
 */
public interface SynchronousNetworkRecommendationProvider {

    /** Returns a {@link ScoredNetwork} if present in the cache. Otherwise, return null. */
    ScoredNetwork getCachedScoredNetwork(NetworkKey networkKey);

    /** Returns a {@link RecommendationResult} using the internal NetworkRecommendationProvider. */
    RecommendationResult requestRecommendation(RecommendationRequest request);
}
