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

package com.android.tv.tuner.exoplayer;

import android.net.Uri;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.extractor.ExtractorSampleSource.EventListener;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.android.tv.tuner.exoplayer.buffer.BufferManager;
import com.android.tv.tuner.exoplayer.buffer.RecordingSampleBuffer;
import com.android.tv.tuner.exoplayer.buffer.SimpleSampleBuffer;
import com.android.tv.tuner.tvinput.PlaybackBufferListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A class that extracts samples from a live broadcast stream while storing the sample on the disk.
 * For demux, this class relies on {@link com.google.android.exoplayer.extractor.ts.TsExtractor}.
 */
public class ExoPlayerSampleExtractor implements SampleExtractor {
    private static final String TAG = "ExoPlayerSampleExtracto";

    // Buffer segment size for memory allocator. Copied from demo implementation of ExoPlayer.
    private static final int BUFFER_SEGMENT_SIZE_IN_BYTES = 64 * 1024;
    // Buffer segment count for sample source. Copied from demo implementation of ExoPlayer.
    private static final int BUFFER_SEGMENT_COUNT = 256;

    private final HandlerThread mSourceReaderThread;
    private final long mId;

    private final Handler.Callback mSourceReaderWorker;

    private BufferManager.SampleBuffer mSampleBuffer;
    private Handler mSourceReaderHandler;
    private volatile boolean mPrepared;
    private AtomicBoolean mOnCompletionCalled = new AtomicBoolean();
    private IOException mExceptionOnPrepare;
    private List<MediaFormat> mTrackFormats;
    private HashMap<Integer, Long> mLastExtractedPositionUsMap = new HashMap<>();
    private OnCompletionListener mOnCompletionListener;
    private Handler mOnCompletionListenerHandler;
    private IOException mError;

    public ExoPlayerSampleExtractor(Uri uri, DataSource source, BufferManager bufferManager,
            PlaybackBufferListener bufferListener, boolean isRecording) {
        // It'll be used as a timeshift file chunk name's prefix.
        mId = System.currentTimeMillis();
        Allocator allocator = new DefaultAllocator(BUFFER_SEGMENT_SIZE_IN_BYTES);

        EventListener eventListener = new EventListener() {

            @Override
            public void onLoadError(int sourceId, IOException e) {
                mError = e;
            }
        };

        mSourceReaderThread = new HandlerThread("SourceReaderThread");
        mSourceReaderWorker = new SourceReaderWorker(new ExtractorSampleSource(uri, source,
                allocator, BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE_IN_BYTES,
                // Do not create a handler if we not on a looper. e.g. test.
                Looper.myLooper() != null ? new Handler() : null,
                eventListener, 0));
        if (isRecording) {
            mSampleBuffer = new RecordingSampleBuffer(bufferManager, bufferListener, false,
                    RecordingSampleBuffer.BUFFER_REASON_RECORDING);
        } else {
            if (bufferManager == null || bufferManager.isDisabled()) {
                mSampleBuffer = new SimpleSampleBuffer(bufferListener);
            } else {
                mSampleBuffer = new RecordingSampleBuffer(bufferManager, bufferListener, true,
                        RecordingSampleBuffer.BUFFER_REASON_LIVE_PLAYBACK);
            }
        }
    }

    @Override
    public void setOnCompletionListener(OnCompletionListener listener, Handler handler) {
        mOnCompletionListener = listener;
        mOnCompletionListenerHandler = handler;
    }

    private class SourceReaderWorker implements Handler.Callback {
        public static final int MSG_PREPARE = 1;
        public static final int MSG_FETCH_SAMPLES = 2;
        public static final int MSG_RELEASE = 3;
        private static final int RETRY_INTERVAL_MS = 50;

        private final SampleSource mSampleSource;
        private SampleSource.SampleSourceReader mSampleSourceReader;
        private boolean[] mTrackMetEos;
        private boolean mMetEos = false;
        private long mCurrentPosition;

        public SourceReaderWorker(SampleSource sampleSource) {
            mSampleSource = sampleSource;
        }

        @Override
        public boolean handleMessage(Message message) {
            switch (message.what) {
                case MSG_PREPARE:
                    mPrepared = prepare();
                    if (!mPrepared && mExceptionOnPrepare == null) {
                            mSourceReaderHandler
                                    .sendEmptyMessageDelayed(MSG_PREPARE, RETRY_INTERVAL_MS);
                    } else{
                        mSourceReaderHandler.sendEmptyMessage(MSG_FETCH_SAMPLES);
                    }
                    return true;
                case MSG_FETCH_SAMPLES:
                    boolean didSomething = false;
                    SampleHolder sample = new SampleHolder(
                            SampleHolder.BUFFER_REPLACEMENT_MODE_NORMAL);
                    ConditionVariable conditionVariable = new ConditionVariable();
                    int trackCount = mSampleSourceReader.getTrackCount();
                    for (int i = 0; i < trackCount; ++i) {
                        if (!mTrackMetEos[i] && SampleSource.NOTHING_READ
                                != fetchSample(i, sample, conditionVariable)) {
                            if (mMetEos) {
                                // If mMetEos was on during fetchSample() due to an error,
                                // fetching from other tracks is not necessary.
                                break;
                            }
                            didSomething = true;
                        }
                    }
                    if (!mMetEos) {
                        if (didSomething) {
                            mSourceReaderHandler.sendEmptyMessage(MSG_FETCH_SAMPLES);
                        } else {
                            mSourceReaderHandler.sendEmptyMessageDelayed(MSG_FETCH_SAMPLES,
                                    RETRY_INTERVAL_MS);
                        }
                    } else {
                        notifyCompletionIfNeeded(false);
                    }
                    return true;
                case MSG_RELEASE:
                    if (mSampleSourceReader != null) {
                        if (mPrepared) {
                            // ExtractorSampleSource expects all the tracks should be disabled
                            // before releasing.
                            int count = mSampleSourceReader.getTrackCount();
                            for (int i = 0; i < count; ++i) {
                                mSampleSourceReader.disable(i);
                            }
                        }
                        mSampleSourceReader.release();
                        mSampleSourceReader = null;
                    }
                    cleanUp();
                    mSourceReaderHandler.removeCallbacksAndMessages(null);
                    return true;
            }
            return false;
        }

        private boolean prepare() {
            if (mSampleSourceReader == null) {
                mSampleSourceReader = mSampleSource.register();
            }
            if(!mSampleSourceReader.prepare(0)) {
                return false;
            }
            if (mTrackFormats == null) {
                int trackCount = mSampleSourceReader.getTrackCount();
                mTrackMetEos = new boolean[trackCount];
                List<MediaFormat> trackFormats = new ArrayList<>();
                for (int i = 0; i < trackCount; i++) {
                    trackFormats.add(mSampleSourceReader.getFormat(i));
                    mSampleSourceReader.enable(i, 0);

                }
                mTrackFormats = trackFormats;
                List<String> ids = new ArrayList<>();
                for (int i = 0; i < mTrackFormats.size(); i++) {
                    ids.add(String.format(Locale.ENGLISH, "%s_%x", Long.toHexString(mId), i));
                }
                try {
                    mSampleBuffer.init(ids, mTrackFormats);
                } catch (IOException e) {
                    // In this case, we will not schedule any further operation.
                    // mExceptionOnPrepare will be notified to ExoPlayer, and ExoPlayer will
                    // call release() eventually.
                    mExceptionOnPrepare = e;
                    return false;
                }
            }
            return true;
        }

        private int fetchSample(int track, SampleHolder sample,
                ConditionVariable conditionVariable) {
            mSampleSourceReader.continueBuffering(track, mCurrentPosition);

            MediaFormatHolder formatHolder = new MediaFormatHolder();
            sample.clearData();
            int ret = mSampleSourceReader.readData(track, mCurrentPosition, formatHolder, sample);
            if (ret == SampleSource.SAMPLE_READ) {
                if (mCurrentPosition < sample.timeUs) {
                    mCurrentPosition = sample.timeUs;
                }
                try {
                    Long lastExtractedPositionUs = mLastExtractedPositionUsMap.get(track);
                    if (lastExtractedPositionUs == null) {
                        mLastExtractedPositionUsMap.put(track, sample.timeUs);
                    } else {
                        mLastExtractedPositionUsMap.put(track,
                                Math.max(lastExtractedPositionUs, sample.timeUs));
                    }
                    queueSample(track, sample, conditionVariable);
                } catch (IOException e) {
                    mLastExtractedPositionUsMap.clear();
                    mMetEos = true;
                    mSampleBuffer.setEos();
                }
            } else if (ret == SampleSource.END_OF_STREAM) {
                mTrackMetEos[track] = true;
                for (int i = 0; i < mTrackMetEos.length; ++i) {
                    if (!mTrackMetEos[i]) {
                        break;
                    }
                    if (i == mTrackMetEos.length -1) {
                        mMetEos = true;
                        mSampleBuffer.setEos();
                    }
                }
            }
            // TODO: Handle SampleSource.FORMAT_READ for dynamic resolution change. b/28169263
            return ret;
        }
    }

    private void queueSample(int index, SampleHolder sample, ConditionVariable conditionVariable)
            throws IOException {
        long writeStartTimeNs = SystemClock.elapsedRealtimeNanos();
        mSampleBuffer.writeSample(index, sample, conditionVariable);

        // Checks whether the storage has enough bandwidth for recording samples.
        if (mSampleBuffer.isWriteSpeedSlow(sample.size,
                SystemClock.elapsedRealtimeNanos() - writeStartTimeNs)) {
            mSampleBuffer.handleWriteSpeedSlow();
        }
    }

    @Override
    public void maybeThrowError() throws IOException {
        if (mError != null) {
            IOException e = mError;
            mError = null;
            throw e;
        }
    }

    @Override
    public boolean prepare() throws IOException {
        if (!mSourceReaderThread.isAlive()) {
            mSourceReaderThread.start();
            mSourceReaderHandler = new Handler(mSourceReaderThread.getLooper(),
                    mSourceReaderWorker);
            mSourceReaderHandler.sendEmptyMessage(SourceReaderWorker.MSG_PREPARE);
        }
        if (mExceptionOnPrepare != null) {
            throw mExceptionOnPrepare;
        }
        return mPrepared;
    }

    @Override
    public List<MediaFormat> getTrackFormats() {
        return mTrackFormats;
    }

    @Override
    public void getTrackMediaFormat(int track, MediaFormatHolder outMediaFormatHolder) {
        outMediaFormatHolder.format = mTrackFormats.get(track);
        outMediaFormatHolder.drmInitData = null;
    }

    @Override
    public void selectTrack(int index) {
        mSampleBuffer.selectTrack(index);
    }

    @Override
    public void deselectTrack(int index) {
        mSampleBuffer.deselectTrack(index);
    }

    @Override
    public long getBufferedPositionUs() {
        return mSampleBuffer.getBufferedPositionUs();
    }

    @Override
    public boolean continueBuffering(long positionUs)  {
        return mSampleBuffer.continueBuffering(positionUs);
    }

    @Override
    public void seekTo(long positionUs) {
        mSampleBuffer.seekTo(positionUs);
    }

    @Override
    public int readSample(int track, SampleHolder sampleHolder) {
        return mSampleBuffer.readSample(track, sampleHolder);
    }

    @Override
    public void release() {
        if (mSourceReaderThread.isAlive()) {
            mSourceReaderHandler.removeCallbacksAndMessages(null);
            mSourceReaderHandler.sendEmptyMessage(SourceReaderWorker.MSG_RELEASE);
            mSourceReaderThread.quitSafely();
            // Return early in this case so that session worker can start working on the next
            // request as early as it can. The clean up will be done in the reader thread while
            // handling MSG_RELEASE.
        } else {
            cleanUp();
        }
    }

    private void cleanUp() {
        boolean result = true;
        try {
            if (mSampleBuffer != null) {
                mSampleBuffer.release();
                mSampleBuffer = null;
            }
        } catch (IOException e) {
            result = false;
        }
        notifyCompletionIfNeeded(result);
        setOnCompletionListener(null, null);
    }

    private void notifyCompletionIfNeeded(final boolean result) {
        if (!mOnCompletionCalled.getAndSet(true)) {
            final OnCompletionListener listener = mOnCompletionListener;
            final long lastExtractedPositionUs = getLastExtractedPositionUs();
            if (mOnCompletionListenerHandler != null && mOnCompletionListener != null) {
                mOnCompletionListenerHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onCompletion(result, lastExtractedPositionUs);
                    }
                });
            }
        }
    }

    private long getLastExtractedPositionUs() {
        long lastExtractedPositionUs = Long.MAX_VALUE;
        for (long value : mLastExtractedPositionUsMap.values()) {
            lastExtractedPositionUs = Math.min(lastExtractedPositionUs, value);
        }
        if (lastExtractedPositionUs == Long.MAX_VALUE) {
            lastExtractedPositionUs = C.UNKNOWN_TIME_US;
        }
        return lastExtractedPositionUs;
    }
}
