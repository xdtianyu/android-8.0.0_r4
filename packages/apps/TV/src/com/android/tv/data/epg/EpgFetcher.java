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

package com.android.tv.data.epg;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Address;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Programs;
import android.media.tv.TvContract.Programs.Genres;
import android.media.tv.TvInputInfo;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.os.BuildCompat;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.TvApplication;
import com.android.tv.common.WeakHandler;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.InternalDataUtils;
import com.android.tv.data.Lineup;
import com.android.tv.data.Program;
import com.android.tv.util.LocationUtils;
import com.android.tv.util.RecurringRunner;
import com.android.tv.util.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * An utility class to fetch the EPG. This class isn't thread-safe.
 */
public class EpgFetcher {
    private static final String TAG = "EpgFetcher";
    private static final boolean DEBUG = false;

    private static final int MSG_FETCH_EPG = 1;

    private static final long EPG_PREFETCH_RECURRING_PERIOD_MS = TimeUnit.HOURS.toMillis(4);
    private static final long EPG_READER_INIT_WAIT_MS = TimeUnit.MINUTES.toMillis(1);
    private static final long LOCATION_INIT_WAIT_MS = TimeUnit.SECONDS.toMillis(10);
    private static final long LOCATION_ERROR_WAIT_MS = TimeUnit.HOURS.toMillis(1);
    private static final long PROGRAM_QUERY_DURATION = TimeUnit.DAYS.toMillis(30);

    private static final int BATCH_OPERATION_COUNT = 100;

    private static final String SUPPORTED_COUNTRY_CODE = Locale.US.getCountry();
    private static final String CONTENT_RATING_SEPARATOR = ",";

    // Value: Long
    private static final String KEY_LAST_UPDATED_EPG_TIMESTAMP =
            "com.android.tv.data.epg.EpgFetcher.LastUpdatedEpgTimestamp";
    // Value: String
    private static final String KEY_LAST_LINEUP_ID =
            "com.android.tv.data.epg.EpgFetcher.LastLineupId";

    private static EpgFetcher sInstance;

    private final Context mContext;
    private final ChannelDataManager mChannelDataManager;
    private final EpgReader mEpgReader;
    private EpgFetcherHandler mHandler;
    private RecurringRunner mRecurringRunner;
    private boolean mStarted;

    private long mLastEpgTimestamp = -1;
    private String mLineupId;

    public static synchronized EpgFetcher getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new EpgFetcher(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Creates and returns {@link EpgReader}.
     */
    public static EpgReader createEpgReader(Context context) {
        return new StubEpgReader(context);
    }

    private EpgFetcher(Context context) {
        mContext = context;
        mEpgReader = new StubEpgReader(mContext);
        mChannelDataManager = TvApplication.getSingletons(context).getChannelDataManager();
        mChannelDataManager.addListener(new ChannelDataManager.Listener() {
            @Override
            public void onLoadFinished() {
                if (DEBUG) Log.d(TAG, "ChannelDataManager.onLoadFinished()");
                handleChannelChanged();
            }

            @Override
            public void onChannelListUpdated() {
                if (DEBUG) Log.d(TAG, "ChannelDataManager.onChannelListUpdated()");
                handleChannelChanged();
            }

            @Override
            public void onChannelBrowsableChanged() {
                if (DEBUG) Log.d(TAG, "ChannelDataManager.onChannelBrowsableChanged()");
                handleChannelChanged();
            }
        });
    }

    private void handleChannelChanged() {
        if (mStarted) {
            if (needToStop()) {
                stop();
            }
        } else {
            start();
        }
    }

    private boolean needToStop() {
        return !canStart();
    }

    private boolean canStart() {
        if (DEBUG) Log.d(TAG, "canStart()");
        boolean hasInternalTunerChannel = false;
        for (TvInputInfo input : TvApplication.getSingletons(mContext).getTvInputManagerHelper()
                .getTvInputInfos(true, true)) {
            String inputId = input.getId();
            if (Utils.isInternalTvInput(mContext, inputId)
                    && mChannelDataManager.getChannelCountForInput(inputId) > 0) {
                hasInternalTunerChannel = true;
                break;
            }
        }
        if (!hasInternalTunerChannel) {
            if (DEBUG) Log.d(TAG, "No internal tuner channels.");
            return false;
        }

        if (!TextUtils.isEmpty(getLastLineupId())) {
            return true;
        }
        if (mContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (DEBUG) Log.d(TAG, "No permission to check the current location.");
            return false;
        }

        try {
            Address address = LocationUtils.getCurrentAddress(mContext);
            if (address != null
                    && !TextUtils.equals(address.getCountryCode(), SUPPORTED_COUNTRY_CODE)) {
                if (DEBUG) Log.d(TAG, "Country not supported: " + address.getCountryCode());
                return false;
            }
        } catch (SecurityException e) {
            Log.w(TAG, "No permission to get the current location", e);
            return false;
        } catch (IOException e) {
            Log.w(TAG, "IO Exception when getting the current location", e);
        }
        return true;
    }

    /**
     * Starts fetching EPG.
     */
    @MainThread
    public void start() {
        if (DEBUG) Log.d(TAG, "start()");
        if (mStarted) {
            if (DEBUG) Log.d(TAG, "EpgFetcher thread already started.");
            return;
        }
        if (!canStart()) {
            return;
        }
        mStarted = true;
        if (DEBUG) Log.d(TAG, "Starting EpgFetcher thread.");
        HandlerThread handlerThread = new HandlerThread("EpgFetcher");
        handlerThread.start();
        mHandler = new EpgFetcherHandler(handlerThread.getLooper(), this);
        mRecurringRunner = new RecurringRunner(mContext, EPG_PREFETCH_RECURRING_PERIOD_MS,
                new EpgRunner(), null);
        mRecurringRunner.start();
        if (DEBUG) Log.d(TAG, "EpgFetcher thread started successfully.");
    }

    /**
     * Starts fetching EPG immediately if possible without waiting for the timer.
     */
    @MainThread
    public void startImmediately() {
        start();
        if (mStarted) {
            if (DEBUG) Log.d(TAG, "Starting fetcher immediately");
            fetchEpg();
        }
    }

    /**
     * Stops fetching EPG.
     */
    @MainThread
    public void stop() {
        if (DEBUG) Log.d(TAG, "stop()");
        if (!mStarted) {
            return;
        }
        mStarted = false;
        mRecurringRunner.stop();
        mHandler.removeCallbacksAndMessages(null);
        mHandler.getLooper().quit();
    }

    private void fetchEpg() {
        fetchEpg(0);
    }

    private void fetchEpg(long delay) {
        mHandler.removeMessages(MSG_FETCH_EPG);
        mHandler.sendEmptyMessageDelayed(MSG_FETCH_EPG, delay);
    }

    private void onFetchEpg() {
        if (DEBUG) Log.d(TAG, "Start fetching EPG.");
        if (!mEpgReader.isAvailable()) {
            if (DEBUG) Log.d(TAG, "EPG reader is not temporarily available.");
            fetchEpg(EPG_READER_INIT_WAIT_MS);
            return;
        }
        String lineupId = getLastLineupId();
        if (lineupId == null) {
            Address address;
            try {
                address = LocationUtils.getCurrentAddress(mContext);
            } catch (IOException e) {
                if (DEBUG) Log.d(TAG, "Couldn't get the current location.", e);
                fetchEpg(LOCATION_ERROR_WAIT_MS);
                return;
            } catch (SecurityException e) {
                Log.w(TAG, "No permission to get the current location.");
                return;
            }
            if (address == null) {
                if (DEBUG) Log.d(TAG, "Null address returned.");
                fetchEpg(LOCATION_INIT_WAIT_MS);
                return;
            }
            if (DEBUG) Log.d(TAG, "Current location is " + address);

            lineupId = getLineupForAddress(address);
            if (lineupId != null) {
                if (DEBUG) Log.d(TAG, "Saving lineup " + lineupId + "found for " + address);
                setLastLineupId(lineupId);
            } else {
                if (DEBUG) Log.d(TAG, "No lineup found for " + address);
                return;
            }
        }

        // Check the EPG Timestamp.
        long epgTimestamp = mEpgReader.getEpgTimestamp();
        if (epgTimestamp <= getLastUpdatedEpgTimestamp()) {
            if (DEBUG) Log.d(TAG, "No new EPG.");
            return;
        }

        boolean updated = false;
        List<Channel> channels = mEpgReader.getChannels(lineupId);
        for (Channel channel : channels) {
            List<Program> programs = new ArrayList<>(mEpgReader.getPrograms(channel.getId()));
            Collections.sort(programs);
            if (DEBUG) {
                Log.d(TAG, "Fetched " + programs.size() + " programs for channel " + channel);
            }
            if (updateEpg(channel.getId(), programs)) {
                updated = true;
            }
        }

        final boolean epgUpdated = updated;
        setLastUpdatedEpgTimestamp(epgTimestamp);
        mHandler.removeMessages(MSG_FETCH_EPG);
        if (DEBUG) Log.d(TAG, "Fetching EPG is finished.");
    }

    @Nullable
    private String getLineupForAddress(Address address) {
        String lineup = null;
        if (TextUtils.equals(address.getCountryCode(), SUPPORTED_COUNTRY_CODE)) {
            String postalCode = address.getPostalCode();
            if (!TextUtils.isEmpty(postalCode)) {
                lineup = getLineupForPostalCode(postalCode);
            }
        }
        return lineup;
    }

    @Nullable
    private String getLineupForPostalCode(String postalCode) {
        List<Lineup> lineups = mEpgReader.getLineups(postalCode);
        for (Lineup lineup : lineups) {
            // TODO(EPG): handle more than OTA digital
            if (lineup.type == Lineup.LINEUP_BROADCAST_DIGITAL) {
                if (DEBUG) Log.d(TAG, "Setting lineup to " + lineup.name  + "("  + lineup.id + ")");
                return lineup.id;
            }
        }
        return null;
    }

    private long getLastUpdatedEpgTimestamp() {
        if (mLastEpgTimestamp < 0) {
            mLastEpgTimestamp = PreferenceManager.getDefaultSharedPreferences(mContext).getLong(
                    KEY_LAST_UPDATED_EPG_TIMESTAMP, 0);
        }
        return mLastEpgTimestamp;
    }

    private void setLastUpdatedEpgTimestamp(long timestamp) {
        mLastEpgTimestamp = timestamp;
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().putLong(
                KEY_LAST_UPDATED_EPG_TIMESTAMP, timestamp).commit();
    }

    private String getLastLineupId() {
        if (mLineupId == null) {
            mLineupId = PreferenceManager.getDefaultSharedPreferences(mContext)
                    .getString(KEY_LAST_LINEUP_ID, null);
        }
        if (DEBUG) Log.d(TAG, "Last lineup_id " + mLineupId);
        return mLineupId;
    }

    private void setLastLineupId(String lineupId) {
        mLineupId = lineupId;
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(KEY_LAST_LINEUP_ID, lineupId).commit();
    }

    private boolean updateEpg(long channelId, List<Program> newPrograms) {
        final int fetchedProgramsCount = newPrograms.size();
        if (fetchedProgramsCount == 0) {
            return false;
        }
        boolean updated = false;
        long startTimeMs = System.currentTimeMillis();
        long endTimeMs = startTimeMs + PROGRAM_QUERY_DURATION;
        List<Program> oldPrograms = queryPrograms(channelId, startTimeMs, endTimeMs);
        Program currentOldProgram = oldPrograms.size() > 0 ? oldPrograms.get(0) : null;
        int oldProgramsIndex = 0;
        int newProgramsIndex = 0;
        // Skip the past programs. They will be automatically removed by the system.
        if (currentOldProgram != null) {
            long oldStartTimeUtcMillis = currentOldProgram.getStartTimeUtcMillis();
            for (Program program : newPrograms) {
                if (program.getEndTimeUtcMillis() > oldStartTimeUtcMillis) {
                    break;
                }
                newProgramsIndex++;
            }
        }
        // Compare the new programs with old programs one by one and update/delete the old one
        // or insert new program if there is no matching program in the database.
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        while (newProgramsIndex < fetchedProgramsCount) {
            // TODO: Extract to method and make test.
            Program oldProgram = oldProgramsIndex < oldPrograms.size()
                    ? oldPrograms.get(oldProgramsIndex) : null;
            Program newProgram = newPrograms.get(newProgramsIndex);
            boolean addNewProgram = false;
            if (oldProgram != null) {
                if (oldProgram.equals(newProgram)) {
                    // Exact match. No need to update. Move on to the next programs.
                    oldProgramsIndex++;
                    newProgramsIndex++;
                } else if (isSameTitleAndOverlap(oldProgram, newProgram)) {
                    // Partial match. Update the old program with the new one.
                    // NOTE: Use 'update' in this case instead of 'insert' and 'delete'. There
                    // could be application specific settings which belong to the old program.
                    ops.add(ContentProviderOperation.newUpdate(
                            TvContract.buildProgramUri(oldProgram.getId()))
                            .withValues(toContentValues(newProgram))
                            .build());
                    oldProgramsIndex++;
                    newProgramsIndex++;
                } else if (oldProgram.getEndTimeUtcMillis()
                        < newProgram.getEndTimeUtcMillis()) {
                    // No match. Remove the old program first to see if the next program in
                    // {@code oldPrograms} partially matches the new program.
                    ops.add(ContentProviderOperation.newDelete(
                            TvContract.buildProgramUri(oldProgram.getId()))
                            .build());
                    oldProgramsIndex++;
                } else {
                    // No match. The new program does not match any of the old programs. Insert
                    // it as a new program.
                    addNewProgram = true;
                    newProgramsIndex++;
                }
            } else {
                // No old programs. Just insert new programs.
                addNewProgram = true;
                newProgramsIndex++;
            }
            if (addNewProgram) {
                ops.add(ContentProviderOperation
                        .newInsert(TvContract.Programs.CONTENT_URI)
                        .withValues(toContentValues(newProgram))
                        .build());
            }
            // Throttle the batch operation not to cause TransactionTooLargeException.
            if (ops.size() > BATCH_OPERATION_COUNT || newProgramsIndex >= fetchedProgramsCount) {
                try {
                    if (DEBUG) {
                        int size = ops.size();
                        Log.d(TAG, "Running " + size + " operations for channel " + channelId);
                        for (int i = 0; i < size; ++i) {
                            Log.d(TAG, "Operation(" + i + "): " + ops.get(i));
                        }
                    }
                    mContext.getContentResolver().applyBatch(TvContract.AUTHORITY, ops);
                    updated = true;
                } catch (RemoteException | OperationApplicationException e) {
                    Log.e(TAG, "Failed to insert programs.", e);
                    return updated;
                }
                ops.clear();
            }
        }
        if (DEBUG) {
            Log.d(TAG, "Updated " + fetchedProgramsCount + " programs for channel " + channelId);
        }
        return updated;
    }

    private List<Program> queryPrograms(long channelId, long startTimeMs, long endTimeMs) {
        try (Cursor c = mContext.getContentResolver().query(
                TvContract.buildProgramsUriForChannel(channelId, startTimeMs, endTimeMs),
                Program.PROJECTION, null, null, Programs.COLUMN_START_TIME_UTC_MILLIS)) {
            if (c == null) {
                return Collections.emptyList();
            }
            ArrayList<Program> programs = new ArrayList<>();
            while (c.moveToNext()) {
                programs.add(Program.fromCursor(c));
            }
            return programs;
        }
    }

    /**
     * Returns {@code true} if the {@code oldProgram} program needs to be updated with the
     * {@code newProgram} program.
     */
    private boolean isSameTitleAndOverlap(Program oldProgram, Program newProgram) {
        // NOTE: Here, we update the old program if it has the same title and overlaps with the
        // new program. The test logic is just an example and you can modify this. E.g. check
        // whether the both programs have the same program ID if your EPG supports any ID for
        // the programs.
        return Objects.equals(oldProgram.getTitle(), newProgram.getTitle())
                && oldProgram.getStartTimeUtcMillis() <= newProgram.getEndTimeUtcMillis()
                && newProgram.getStartTimeUtcMillis() <= oldProgram.getEndTimeUtcMillis();
    }

    @SuppressLint("InlinedApi")
    @SuppressWarnings("deprecation")
    private static ContentValues toContentValues(Program program) {
        ContentValues values = new ContentValues();
        values.put(TvContract.Programs.COLUMN_CHANNEL_ID, program.getChannelId());
        putValue(values, TvContract.Programs.COLUMN_TITLE, program.getTitle());
        putValue(values, TvContract.Programs.COLUMN_EPISODE_TITLE, program.getEpisodeTitle());
        if (BuildCompat.isAtLeastN()) {
            putValue(values, TvContract.Programs.COLUMN_SEASON_DISPLAY_NUMBER,
                    program.getSeasonNumber());
            putValue(values, TvContract.Programs.COLUMN_EPISODE_DISPLAY_NUMBER,
                    program.getEpisodeNumber());
        } else {
            putValue(values, TvContract.Programs.COLUMN_SEASON_NUMBER, program.getSeasonNumber());
            putValue(values, TvContract.Programs.COLUMN_EPISODE_NUMBER, program.getEpisodeNumber());
        }
        putValue(values, TvContract.Programs.COLUMN_SHORT_DESCRIPTION, program.getDescription());
        putValue(values, TvContract.Programs.COLUMN_POSTER_ART_URI, program.getPosterArtUri());
        putValue(values, TvContract.Programs.COLUMN_THUMBNAIL_URI, program.getThumbnailUri());
        String[] canonicalGenres = program.getCanonicalGenres();
        if (canonicalGenres != null && canonicalGenres.length > 0) {
            putValue(values, TvContract.Programs.COLUMN_CANONICAL_GENRE,
                    Genres.encode(canonicalGenres));
        } else {
            putValue(values, TvContract.Programs.COLUMN_CANONICAL_GENRE, "");
        }
        TvContentRating[] ratings = program.getContentRatings();
        if (ratings != null && ratings.length > 0) {
            StringBuilder sb = new StringBuilder(ratings[0].flattenToString());
            for (int i = 1; i < ratings.length; ++i) {
                sb.append(CONTENT_RATING_SEPARATOR);
                sb.append(ratings[i].flattenToString());
            }
            putValue(values, TvContract.Programs.COLUMN_CONTENT_RATING, sb.toString());
        } else {
            putValue(values, TvContract.Programs.COLUMN_CONTENT_RATING, "");
        }
        values.put(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
                program.getStartTimeUtcMillis());
        values.put(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS, program.getEndTimeUtcMillis());
        putValue(values, TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA,
                InternalDataUtils.serializeInternalProviderData(program));
        return values;
    }

    private static void putValue(ContentValues contentValues, String key, String value) {
        if (TextUtils.isEmpty(value)) {
            contentValues.putNull(key);
        } else {
            contentValues.put(key, value);
        }
    }

    private static void putValue(ContentValues contentValues, String key, byte[] value) {
        if (value == null || value.length == 0) {
            contentValues.putNull(key);
        } else {
            contentValues.put(key, value);
        }
    }

    private static class EpgFetcherHandler extends WeakHandler<EpgFetcher> {
        public EpgFetcherHandler (@NonNull Looper looper, EpgFetcher ref) {
            super(looper, ref);
        }

        @Override
        public void handleMessage(Message msg, @NonNull EpgFetcher epgFetcher) {
            switch (msg.what) {
                case MSG_FETCH_EPG:
                    epgFetcher.onFetchEpg();
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }

    private class EpgRunner implements Runnable {
        @Override
        public void run() {
            fetchEpg();
        }
    }
}
