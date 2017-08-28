/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.networkrecommendation;

import android.net.RecommendationRequest;

import java.lang.reflect.Method;

public class Example {

    public Example() {}

    public void buildRecommendationRequest() {
        new RecommendationRequest.Builder().build();
    }

    @SuppressWarnings("unchecked")
    public RecommendationRequest reflectRecommendationRequest() {
        try {
            Class builder = RecommendationRequest.class.getClasses()[0];
            Object builderObj = builder.getConstructor().newInstance();
            Method build = builder.getDeclaredMethod("build");
            return (RecommendationRequest) build.invoke(builderObj);
        } catch (Exception e) {
            throw new RuntimeException("Noooo", e);
        }
    }
}
