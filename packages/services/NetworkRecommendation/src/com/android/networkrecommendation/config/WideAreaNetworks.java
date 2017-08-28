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
package com.android.networkrecommendation.config;

import static com.android.networkrecommendation.Constants.TAG;

import android.support.annotation.VisibleForTesting;
import com.android.networkrecommendation.config.G.Netrec;
import com.android.networkrecommendation.util.Blog;
import com.google.common.collect.ImmutableSet;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/** Provides a list of known wide area networks. */
public class WideAreaNetworks {
    private WideAreaNetworks() {}

    private static ImmutableSet<String> sWideAreaNetworks;

    /** Initialize the list of wide area networks from the phenotype flag. */
    public static void init() {
        sWideAreaNetworks = parseFlag();
    }

    /**
     * @param ssid canonical SSID for a network (with quotes removed)
     * @return {@code true} if {@code ssid} is in the set of wide area networks.
     */
    public static boolean contains(String ssid) {
        if (sWideAreaNetworks == null) {
            init();
        }
        return sWideAreaNetworks.contains(ssid);
    }

    @VisibleForTesting
    static ImmutableSet<String> parseFlag() {
        List<String> parts = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new StringReader(Netrec.wideAreaNetworks.get()));
        try {
            Csv.parseLine(reader, parts);
        } catch (IOException ex) {
            Blog.e(TAG, ex, "Error parsing flag");
        }
        return ImmutableSet.copyOf(parts);
    }
}
