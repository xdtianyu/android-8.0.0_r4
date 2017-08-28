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
package com.android.networkrecommendation.config;

/**
 * Simple configuration parameters for application behavior.
 * @param <T> A type for a flag value.
 */
public class Flag<T> {

    private final T mDefaultValue;
    private T mOverride;

    public Flag(T defaultValue) {
        mDefaultValue = defaultValue;
        mOverride = null;
    }

    /** Get the currently set flag value. */
    public T get() {
        return mOverride != null ? mOverride : mDefaultValue;
    }

    /** Force a value for testing. */
    public void override(T value) {
        mOverride = value;
    }

    /** Ensure flag state is initialized for tests. */
    public static void initForTest() {
    }
}
