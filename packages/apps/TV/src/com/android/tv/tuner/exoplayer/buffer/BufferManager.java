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

import android.media.MediaFormat;
import android.os.ConditionVariable;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;

import com.google.android.exoplayer.SampleHolder;
import com.android.tv.tuner.exoplayer.SampleExtractor;
import com.android.tv.util.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Manages {@link SampleChunk} objects.
 * <p>
 * The buffer manager can be disabled, while running, if the write throughput to the associated
 * external storage is detected to be lower than a threshold {@code MINIMUM_DISK_WRITE_SPEED_MBPS}".
 * This leads to restarting playback flow.
 */
public class BufferManager {
    private static final String TAG = "BufferManager";
    private static final boolean DEBUG = false;

    // Constants for the disk write speed checking
    private static final long MINIMUM_WRITE_SIZE_FOR_SPEED_CHECK =
            10L * 1024 * 1024;  // Checks for every 10M disk write
    private static final int MINIMUM_SAMPLE_SIZE_FOR_SPEED_CHECK = 15 * 1024;
    private static final int MAXIMUM_SPEED_CHECK_COUNT = 5;  // Checks only 5 times
    private static final int MINIMUM_DISK_WRITE_SPEED_MBPS = 3;  // 3 Megabytes per second

    private final SampleChunk.SampleChunkCreator mSampleChunkCreator;
    // Maps from track name to a map which maps from starting position to {@link SampleChunk}.
    private final Map<String, SortedMap<Long, SampleChunk>> mChunkMap = new ArrayMap<>();
    private final Map<String, Long> mStartPositionMap = new ArrayMap<>();
    private final Map<String, ChunkEvictedListener> mEvictListeners = new ArrayMap<>();
    private final StorageManager mStorageManager;
    private long mBufferSize = 0;
    private final EvictChunkQueueMap mPendingDelete = new EvictChunkQueueMap();
    private final SampleChunk.ChunkCallback mChunkCallback = new SampleChunk.ChunkCallback() {
        @Override
        public void onChunkWrite(SampleChunk chunk) {
            mBufferSize += chunk.getSize();
        }

        @Override
        public void onChunkDelete(SampleChunk chunk) {
            mBufferSize -= chunk.getSize();
        }
    };

    private volatile boolean mClosed = false;
    private int mMinSampleSizeForSpeedCheck = MINIMUM_SAMPLE_SIZE_FOR_SPEED_CHECK;
    private long mTotalWriteSize;
    private long mTotalWriteTimeNs;
    private float mWriteBandwidth = 0.0f;
    private volatile int mSpeedCheckCount;
    private boolean mDisabled = false;

    public interface ChunkEvictedListener {
        void onChunkEvicted(String id, long createdTimeMs);
    }
    /**
     * Handles I/O
     * between BufferManager and {@link SampleExtractor}.
     */
    public interface SampleBuffer {

        /**
         * Initializes SampleBuffer.
         * @param Ids track identifiers for storage read/write.
         * @param mediaFormats meta-data for each track.
         * @throws IOException
         */
        void init(@NonNull List<String> Ids,
                  @NonNull List<com.google.android.exoplayer.MediaFormat> mediaFormats)
                throws IOException;

        /**
         * Selects the track {@code index} for reading sample data.
         */
        void selectTrack(int index);

        /**
         * Deselects the track at {@code index},
         * so that no more samples will be read from the track.
         */
        void deselectTrack(int index);

        /**
         * Writes sample to storage.
         *
         * @param index track index
         * @param sample sample to write at storage
         * @param conditionVariable notifies the completion of writing sample.
         * @throws IOException
         */
        void writeSample(int index, SampleHolder sample, ConditionVariable conditionVariable)
                throws IOException;

        /**
         * Checks whether storage write speed is slow.
         */
        boolean isWriteSpeedSlow(int sampleSize, long writeDurationNs);

        /**
         * Handles when write speed is slow.
         * @throws IOException
         */
        void handleWriteSpeedSlow() throws IOException;

        /**
         * Sets the flag when EoS was reached.
         */
        void setEos();

        /**
         * Reads the next sample in the track at index {@code track} into {@code sampleHolder},
         * returning {@link com.google.android.exoplayer.SampleSource#SAMPLE_READ}
         * if it is available.
         * If the next sample is not available,
         * returns {@link com.google.android.exoplayer.SampleSource#NOTHING_READ}.
         */
        int readSample(int index, SampleHolder outSample);

        /**
         * Seeks to the specified time in microseconds.
         */
        void seekTo(long positionUs);

        /**
         * Returns an estimate of the position up to which data is buffered.
         */
        long getBufferedPositionUs();

        /**
         * Returns whether there is buffered data.
         */
        boolean continueBuffering(long positionUs);

        /**
         * Cleans up and releases everything.
         * @throws IOException
         */
        void release() throws IOException;
    }

    /**
     * Storage configuration and policy manager for {@link BufferManager}
     */
    public interface StorageManager {

        /**
         * Provides eligible storage directory for {@link BufferManager}.
         *
         * @return a directory to save buffer(chunks) and meta files
         */
        File getBufferDir();

        /**
         * Cleans up storage.
         */
        void clearStorage();

        /**
         * Informs whether the storage is used for persistent use. (eg. dvr recording/play)
         *
         * @return {@code true} if stored files are persistent
         */
        boolean isPersistent();

        /**
         * Informs whether the storage usage exceeds pre-determined size.
         *
         * @param bufferSize the current total usage of Storage in bytes.
         * @param pendingDelete the current storage usage which will be deleted in near future by
         *                      bytes
         * @return {@code true} if it reached pre-determined max size
         */
        boolean reachedStorageMax(long bufferSize, long pendingDelete);

        /**
         * Informs whether the storage has enough remained space.
         *
         * @param pendingDelete the current storage usage which will be deleted in near future by
         *                      bytes
         * @return {@code true} if it has enough space
         */
        boolean hasEnoughBuffer(long pendingDelete);

        /**
         * Reads track name & {@link MediaFormat} from storage.
         *
         * @param isAudio {@code true} if it is for audio track
         * @return {@link Pair} of track name & {@link MediaFormat}
         * @throws IOException
         */
        Pair<String, MediaFormat> readTrackInfoFile(boolean isAudio) throws IOException;

        /**
         * Reads sample indexes for each written sample from storage.
         *
         * @param trackId track name
         * @return indexes of the specified track
         * @throws IOException
         */
        ArrayList<Long> readIndexFile(String trackId) throws IOException;

        /**
         * Writes track information to storage.
         *
         * @param trackId track name
         * @param format {@link android.media.MediaFormat} of the track
         * @param isAudio {@code true} if it is for audio track
         * @throws IOException
         */
        void writeTrackInfoFile(String trackId, MediaFormat format, boolean isAudio)
                throws IOException;

        /**
         * Writes index file to storage.
         *
         * @param trackName track name
         * @param index {@link SampleChunk} container
         * @throws IOException
         */
        void writeIndexFile(String trackName, SortedMap<Long, SampleChunk> index)
                throws IOException;
    }

    private static class EvictChunkQueueMap {
        private final Map<String, LinkedList<SampleChunk>> mEvictMap = new ArrayMap<>();
        private long mSize;

        private void init(String key) {
            mEvictMap.put(key, new LinkedList<>());
        }

        private void add(String key, SampleChunk chunk) {
            LinkedList<SampleChunk> queue = mEvictMap.get(key);
            if (queue != null) {
                mSize += chunk.getSize();
                queue.add(chunk);
            }
        }

        private SampleChunk poll(String key, long startPositionUs) {
            LinkedList<SampleChunk> queue = mEvictMap.get(key);
            if (queue != null) {
                SampleChunk chunk = queue.peek();
                if (chunk != null && chunk.getStartPositionUs() < startPositionUs) {
                    mSize -= chunk.getSize();
                    return queue.poll();
                }
            }
            return null;
        }

        private long getSize() {
            return mSize;
        }

        private void release() {
            for (Map.Entry<String, LinkedList<SampleChunk>> entry : mEvictMap.entrySet()) {
                for (SampleChunk chunk : entry.getValue()) {
                    SampleChunk.IoState.release(chunk, true);
                }
            }
            mEvictMap.clear();
            mSize = 0;
        }
    }

    public BufferManager(StorageManager storageManager) {
        this(storageManager, new SampleChunk.SampleChunkCreator());
    }

    public BufferManager(StorageManager storageManager,
            SampleChunk.SampleChunkCreator sampleChunkCreator) {
        mStorageManager = storageManager;
        mSampleChunkCreator = sampleChunkCreator;
        clearBuffer(true);
    }

    public void registerChunkEvictedListener(String id, ChunkEvictedListener listener) {
        mEvictListeners.put(id, listener);
    }

    public void unregisterChunkEvictedListener(String id) {
        mEvictListeners.remove(id);
    }

    private void clearBuffer(boolean deleteFiles) {
        mChunkMap.clear();
        if (deleteFiles) {
            mStorageManager.clearStorage();
        }
        mBufferSize = 0;
    }

    private static String getFileName(String id, long positionUs) {
        return String.format(Locale.ENGLISH, "%s_%016x.chunk", id, positionUs);
    }

    /**
     * Creates a new {@link SampleChunk} for caching samples.
     *
     * @param id the name of the track
     * @param positionUs starting position of the {@link SampleChunk} in micro seconds.
     * @param samplePool {@link SamplePool} for the fast creation of samples.
     * @return returns the created {@link SampleChunk}.
     * @throws IOException
     */
    public SampleChunk createNewWriteFile(String id, long positionUs,
            SamplePool samplePool) throws IOException {
        if (!maybeEvictChunk()) {
            throw new IOException("Not enough storage space");
        }
        SortedMap<Long, SampleChunk> map = mChunkMap.get(id);
        if (map == null) {
            map = new TreeMap<>();
            mChunkMap.put(id, map);
            mStartPositionMap.put(id, positionUs);
            mPendingDelete.init(id);
        }
        File file = new File(mStorageManager.getBufferDir(), getFileName(id, positionUs));
        SampleChunk sampleChunk = mSampleChunkCreator.createSampleChunk(samplePool, file,
                positionUs, mChunkCallback);
        map.put(positionUs, sampleChunk);
        return sampleChunk;
    }

    /**
     * Loads a track using {@link BufferManager.StorageManager}.
     *
     * @param trackId the name of the track.
     * @param samplePool {@link SamplePool} for the fast creation of samples.
     * @throws IOException
     */
    public void loadTrackFromStorage(String trackId, SamplePool samplePool) throws IOException {
        ArrayList<Long> keyPositions = mStorageManager.readIndexFile(trackId);
        long startPositionUs = keyPositions.size() > 0 ? keyPositions.get(0) : 0;

        SortedMap<Long, SampleChunk> map = mChunkMap.get(trackId);
        if (map == null) {
            map = new TreeMap<>();
            mChunkMap.put(trackId, map);
            mStartPositionMap.put(trackId, startPositionUs);
            mPendingDelete.init(trackId);
        }
        SampleChunk chunk = null;
        for (long positionUs: keyPositions) {
            chunk = mSampleChunkCreator.loadSampleChunkFromFile(samplePool,
                    mStorageManager.getBufferDir(), getFileName(trackId, positionUs), positionUs,
                    mChunkCallback, chunk);
            map.put(positionUs, chunk);
        }
    }

    /**
     * Finds a {@link SampleChunk} for the specified track name and the position.
     *
     * @param id the name of the track.
     * @param positionUs the position.
     * @return returns the found {@link SampleChunk}.
     */
    public SampleChunk getReadFile(String id, long positionUs) {
        SortedMap<Long, SampleChunk> map = mChunkMap.get(id);
        if (map == null) {
            return null;
        }
        SampleChunk sampleChunk;
        SortedMap<Long, SampleChunk> headMap = map.headMap(positionUs + 1);
        if (!headMap.isEmpty()) {
            sampleChunk = headMap.get(headMap.lastKey());
        } else {
            sampleChunk = map.get(map.firstKey());
        }
        return sampleChunk;
    }

    /**
     * Evicts chunks which are ready to be evicted for the specified track
     *
     * @param id the specified track
     * @param earlierThanPositionUs the start position of the {@link SampleChunk}
     *                   should be earlier than
     */
    public void evictChunks(String id, long earlierThanPositionUs) {
        SampleChunk chunk = null;
        while ((chunk = mPendingDelete.poll(id, earlierThanPositionUs)) != null) {
            SampleChunk.IoState.release(chunk, !mStorageManager.isPersistent())  ;
        }
    }

    /**
     * Returns the start position of the specified track in micro seconds.
     *
     * @param id the specified track
     */
    public long getStartPositionUs(String id) {
        Long ret = mStartPositionMap.get(id);
        return ret == null ? 0 : ret;
    }

    private boolean maybeEvictChunk() {
        long pendingDelete = mPendingDelete.getSize();
        while (mStorageManager.reachedStorageMax(mBufferSize, pendingDelete)
                || !mStorageManager.hasEnoughBuffer(pendingDelete)) {
            if (mStorageManager.isPersistent()) {
                // Since chunks are persistent, we cannot evict chunks.
                return false;
            }
            SortedMap<Long, SampleChunk> earliestChunkMap = null;
            SampleChunk earliestChunk = null;
            String earliestChunkId = null;
            for (Map.Entry<String, SortedMap<Long, SampleChunk>> entry : mChunkMap.entrySet()) {
                SortedMap<Long, SampleChunk> map = entry.getValue();
                if (map.isEmpty()) {
                    continue;
                }
                SampleChunk chunk = map.get(map.firstKey());
                if (earliestChunk == null
                        || chunk.getCreatedTimeMs() < earliestChunk.getCreatedTimeMs()) {
                    earliestChunkMap = map;
                    earliestChunk = chunk;
                    earliestChunkId = entry.getKey();
                }
            }
            if (earliestChunk == null) {
                break;
            }
            mPendingDelete.add(earliestChunkId, earliestChunk);
            earliestChunkMap.remove(earliestChunk.getStartPositionUs());
            if (DEBUG) {
                Log.d(TAG, String.format("bufferSize = %d; pendingDelete = %b; "
                                + "earliestChunk size = %d; %s@%d (%s)",
                        mBufferSize, pendingDelete, earliestChunk.getSize(), earliestChunkId,
                        earliestChunk.getStartPositionUs(),
                        Utils.toIsoDateTimeString(earliestChunk.getCreatedTimeMs())));
            }
            ChunkEvictedListener listener = mEvictListeners.get(earliestChunkId);
            if (listener != null) {
                listener.onChunkEvicted(earliestChunkId, earliestChunk.getCreatedTimeMs());
            }
            pendingDelete = mPendingDelete.getSize();
        }
        for (Map.Entry<String, SortedMap<Long, SampleChunk>> entry : mChunkMap.entrySet()) {
            SortedMap<Long, SampleChunk> map = entry.getValue();
            if (map.isEmpty()) {
                continue;
            }
            mStartPositionMap.put(entry.getKey(), map.firstKey());
        }
        return true;
    }

    /**
     * Reads track information which includes {@link MediaFormat}.
     *
     * @return returns all track information which is found by {@link BufferManager.StorageManager}.
     * @throws IOException
     */
    public ArrayList<Pair<String, MediaFormat>> readTrackInfoFiles() throws IOException {
        ArrayList<Pair<String, MediaFormat>> trackInfos = new ArrayList<>();
        try {
            trackInfos.add(mStorageManager.readTrackInfoFile(false));
        } catch (FileNotFoundException e) {
            // There can be a single track only recording. (eg. audio-only, video-only)
            // So the exception should not stop the read.
        }
        try {
            trackInfos.add(mStorageManager.readTrackInfoFile(true));
        } catch (FileNotFoundException e) {
            // See above catch block.
        }
        return trackInfos;
    }

    /**
     * Writes track information and index information for all tracks.
     *
     * @param audio audio information.
     * @param video video information.
     * @throws IOException
     */
    public void writeMetaFiles(Pair<String, MediaFormat> audio, Pair<String, MediaFormat> video)
            throws IOException {
        if (audio != null) {
            mStorageManager.writeTrackInfoFile(audio.first, audio.second, true);
            SortedMap<Long, SampleChunk> map = mChunkMap.get(audio.first);
            if (map == null) {
                throw new IOException("Audio track index missing");
            }
            mStorageManager.writeIndexFile(audio.first, map);
        }
        if (video != null) {
            mStorageManager.writeTrackInfoFile(video.first, video.second, false);
            SortedMap<Long, SampleChunk> map = mChunkMap.get(video.first);
            if (map == null) {
                throw new IOException("Video track index missing");
            }
            mStorageManager.writeIndexFile(video.first, map);
        }
    }

    /**
     * Marks it is closed and it is not used anymore.
     */
    public void close() {
        // Clean-up may happen after this is called.
        mClosed = true;
    }

    /**
     * Releases all the resources.
     */
    public void release() {
        mPendingDelete.release();
        for (Map.Entry<String, SortedMap<Long, SampleChunk>> entry : mChunkMap.entrySet()) {
            for (SampleChunk chunk : entry.getValue().values()) {
                SampleChunk.IoState.release(chunk, !mStorageManager.isPersistent());
            }
        }
        mChunkMap.clear();
        if (mClosed) {
            clearBuffer(!mStorageManager.isPersistent());
        }
    }

    private void resetWriteStat(float writeBandwidth) {
        mWriteBandwidth = writeBandwidth;
        mTotalWriteSize = 0;
        mTotalWriteTimeNs = 0;
    }

    /**
     * Adds a disk write sample size to calculate the average disk write bandwidth.
     */
    public void addWriteStat(long size, long timeNs) {
        if (size >= mMinSampleSizeForSpeedCheck) {
            mTotalWriteSize += size;
            mTotalWriteTimeNs += timeNs;
        }
    }

    /**
     * Returns if the average disk write bandwidth is slower than
     * threshold {@code MINIMUM_DISK_WRITE_SPEED_MBPS}.
     */
    public boolean isWriteSlow() {
        if (mTotalWriteSize < MINIMUM_WRITE_SIZE_FOR_SPEED_CHECK) {
            return false;
        }

        // Checks write speed for only MAXIMUM_SPEED_CHECK_COUNT times to ignore outliers
        // by temporary system overloading during the playback.
        if (mSpeedCheckCount > MAXIMUM_SPEED_CHECK_COUNT) {
            return false;
        }
        mSpeedCheckCount++;
        float megabytePerSecond = calculateWriteBandwidth();
        resetWriteStat(megabytePerSecond);
        if (DEBUG) {
            Log.d(TAG, "Measured disk write performance: " + megabytePerSecond + "MBps");
        }
        return megabytePerSecond < MINIMUM_DISK_WRITE_SPEED_MBPS;
    }

    /**
     * Returns recent write bandwidth in MBps. If recent bandwidth is not available,
     * returns {float -1.0f}.
     */
    public float getWriteBandwidth() {
        return mWriteBandwidth == 0.0f ? -1.0f : mWriteBandwidth;
    }

    private float calculateWriteBandwidth() {
        if (mTotalWriteTimeNs == 0) {
            return -1;
        }
        return ((float) mTotalWriteSize * 1000 / mTotalWriteTimeNs);
    }

    /**
     * Marks {@link BufferManager} object disabled to prevent it from the future use.
     */
    public void disable() {
        mDisabled = true;
    }

    /**
     * Returns if {@link BufferManager} object is disabled.
     */
    public boolean isDisabled() {
        return mDisabled;
    }

    /**
     * Returns if {@link BufferManager} has checked the write speed,
     * which is suitable for Trickplay.
     */
    @VisibleForTesting
    public boolean hasSpeedCheckDone() {
        return mSpeedCheckCount > 0;
    }

    /**
     * Sets minimum sample size for write speed check.
     * @param sampleSize minimum sample size for write speed check.
     */
    @VisibleForTesting
    public void setMinimumSampleSizeForSpeedCheck(int sampleSize) {
        mMinSampleSizeForSpeedCheck = sampleSize;
    }
}
