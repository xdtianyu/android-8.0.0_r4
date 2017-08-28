/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tradefed.result;

/**
 * Class to hold the metadata for a saved log file.
 */
public class LogFile {
    private final String mPath;
    private final String mUrl;
    private final boolean mIsText;
    private final boolean mIsCompressed;

    /**
     * Construct a {@link LogFile} with filepath and URL metadata.
     *
     * @param path The absolute path to the saved file.
     * @param url The URL where the saved file can be accessed.
     * @deprecated use {@link #LogFile(String, String, boolean, boolean)} instead.
     */
    @Deprecated
    public LogFile(String path, String url) {
        this(path, url, false, false);
    }

    /**
     * Construct a {@link LogFile} with filepath, URL, and {@link LogDataType} metadata.
     *
     * @param path The absolute path to the saved file.
     * @param url The URL where the saved file can be accessed.
     * @param compressed Whether the saved file is compressed.
     * @param text Whether the saved file can be displayed as text.
     */
    public LogFile(String path, String url, boolean compressed, boolean text) {
        mPath = path;
        mUrl = url;
        mIsCompressed = compressed;
        mIsText = text;
    }

    /**
     * Get the absolute path.
     */
    public String getPath() {
        return mPath;
    }

    /**
     * Get the URL where the saved file can be accessed.
     */
    public String getUrl() {
        return mUrl;
    }

    /**
     * Get whether the file can be displayed as text.
     */
    public boolean isText() {
        return mIsText;
    }

    /**
     * Get whether the file is compressed.
     */
    public boolean isCompressed() {
        return mIsCompressed;
    }
}
