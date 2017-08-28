/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.tuner.exoplayer.buffer;

import android.content.Context;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.os.Looper;
import android.provider.Settings;
import android.util.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.SortedMap;

/**
 * Manages Trickplay storage.
 */
public class TrickplayStorageManager implements BufferManager.StorageManager {
    private static final String BUFFER_DIR = "timeshift";

    // Copied from android.provider.Settings.Global (hidden fields)
    private static final String
            SYS_STORAGE_THRESHOLD_PERCENTAGE = "sys_storage_threshold_percentage";
    private static final String
            SYS_STORAGE_THRESHOLD_MAX_BYTES = "sys_storage_threshold_max_bytes";

    // Copied from android.os.StorageManager
    private static final int DEFAULT_THRESHOLD_PERCENTAGE = 10;
    private static final long DEFAULT_THRESHOLD_MAX_BYTES = 500L * 1024 * 1024;

    private final File mBufferDir;
    private final long mMaxBufferSize;
    private final long mStorageBufferBytes;

    private static long getStorageBufferBytes(Context context, File path) {
        long lowPercentage = Settings.Global.getInt(context.getContentResolver(),
                SYS_STORAGE_THRESHOLD_PERCENTAGE, DEFAULT_THRESHOLD_PERCENTAGE);
        long lowBytes = path.getTotalSpace() * lowPercentage / 100;
        long maxLowBytes = Settings.Global.getLong(context.getContentResolver(),
                SYS_STORAGE_THRESHOLD_MAX_BYTES, DEFAULT_THRESHOLD_MAX_BYTES);
        return Math.min(lowBytes, maxLowBytes);
    }

    public TrickplayStorageManager(Context context, File baseDir, long maxBufferSize) {
        mBufferDir = new File(baseDir, BUFFER_DIR);
        mBufferDir.mkdirs();
        mMaxBufferSize = maxBufferSize;
        clearStorage();
        mStorageBufferBytes = getStorageBufferBytes(context, mBufferDir);
    }

    @Override
    public void clearStorage() {
        File files[] = mBufferDir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    for (File file : files) {
                        file.delete();
                    }
                    return null;
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            for (File file : files) {
                file.delete();
            }
        }
    }

    @Override
    public File getBufferDir() {
        return mBufferDir;
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public boolean reachedStorageMax(long bufferSize, long pendingDelete) {
        return bufferSize - pendingDelete > mMaxBufferSize;
    }

    @Override
    public boolean hasEnoughBuffer(long pendingDelete) {
        return mBufferDir.getUsableSpace() + pendingDelete >= mStorageBufferBytes;
    }

    @Override
    public Pair<String, MediaFormat> readTrackInfoFile(boolean isAudio) {
        return null;
    }

    @Override
    public ArrayList<Long> readIndexFile(String trackId) {
        return null;
    }

    @Override
    public void writeTrackInfoFile(String trackId, MediaFormat format, boolean isAudio) {
    }

    @Override
    public void writeIndexFile(String trackName, SortedMap<Long, SampleChunk> index) {
    }

}
