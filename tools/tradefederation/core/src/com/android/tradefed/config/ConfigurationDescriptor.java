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
package com.android.tradefed.config;

import com.android.tradefed.util.MultiMap;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration Object that describes some aspect of the configuration itself. Like a membership
 * test-suite-tag. This class cannot receive option values via command line. Only directly in the
 * xml.
 */
@OptionClass(alias = "config-descriptor")
public class ConfigurationDescriptor {

    @Option(name = "test-suite-tag", description = "A membership tag to suite. Can be repeated.")
    private List<String> mSuiteTags = new ArrayList<>();

    @Option(name = "metadata", description = "Metadata associated with this configuration, can be "
            + "free formed key value pairs, and a key may be associated with multiple values.")
    private MultiMap<String, String> mMetaData = new MultiMap<>();

    @Option(
        name = "not-shardable",
        description =
                "A metadata that allows a suite configuration to specify that it cannot be "
                        + "sharded. Not because it doesn't support it but because it doesn't make "
                        + "sense."
    )
    private boolean mNotShardable = false;

    /** Returns the list of suite tags the test is part of. */
    public List<String> getSuiteTags() {
        return mSuiteTags;
    }

    /** Sets the list of suite tags the test is part of. */
    public void setSuiteTags(List<String> suiteTags) {
        mSuiteTags = suiteTags;
    }

    /** Retrieves all configured metadata */
    public MultiMap<String, String> getAllMetaData() {
        return mMetaData;
    }

    /** Get the named metadata entries */
    public List<String> getMetaData(String name) {
        return mMetaData.get(name);
    }

    @VisibleForTesting
    public void setMetaData(MultiMap<String, String> metadata) {
        mMetaData = metadata;
    }

    /** Returns if the configuration is shardable or not as part of a suite */
    public boolean isNotShardable() {
        return mNotShardable;
    }
}
