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

package com.android.tv.tuner;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.MainThread;

import com.android.tv.common.SoftPreconditions;
import com.android.tv.tuner.TunerPreferenceProvider.Preferences;
import com.android.tv.tuner.util.TisConfiguration;

/**
 * A helper class for the USB tuner preferences.
 */
public class TunerPreferences {
    private static final String TAG = "TunerPreferences";

    private static final String PREFS_KEY_CHANNEL_DATA_VERSION = "channel_data_version";
    private static final String PREFS_KEY_SCANNED_CHANNEL_COUNT = "scanned_channel_count";
    private static final String PREFS_KEY_SCAN_DONE = "scan_done";
    private static final String PREFS_KEY_LAUNCH_SETUP = "launch_setup";
    private static final String PREFS_KEY_STORE_TS_STREAM = "store_ts_stream";

    private static final String SHARED_PREFS_NAME = "com.android.tv.tuner.preferences";

    public static final int CHANNEL_DATA_VERSION_NOT_SET = -1;

    private static final Bundle sPreferenceValues = new Bundle();
    private static LoadPreferencesTask sLoadPreferencesTask;
    private static ContentObserver sContentObserver;

    private static boolean sInitialized;

    /**
     * Initializes the USB tuner preferences.
     */
    @MainThread
    public static void initialize(final Context context) {
        if (sInitialized) {
            return;
        }
        sInitialized = true;
        if (useContentProvider(context)) {
            loadPreferences(context);
            sContentObserver = new ContentObserver(new Handler()) {
                @Override
                public void onChange(boolean selfChange) {
                    loadPreferences(context);
                }
            };
            context.getContentResolver().registerContentObserver(
                    TunerPreferenceProvider.Preferences.CONTENT_URI, true, sContentObserver);
        } else {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    getSharedPreferences(context);
                    return null;
                }
            }.execute();
        }
    }

    /**
     * Releases the resources.
     */
    @MainThread
    public static void release(Context context) {
        if (useContentProvider(context) && sContentObserver != null) {
            context.getContentResolver().unregisterContentObserver(sContentObserver);
        }
    }

    /**
     * Loads the preferences from database.
     * <p>
     * This preferences is used across processes, so the preferences should be loaded again when the
     * databases changes.
     */
    public static synchronized void loadPreferences(Context context) {
        if (sLoadPreferencesTask != null
                && sLoadPreferencesTask.getStatus() != AsyncTask.Status.FINISHED) {
            sLoadPreferencesTask.cancel(true);
        }
        sLoadPreferencesTask = new LoadPreferencesTask(context);
        sLoadPreferencesTask.execute();
    }

    private static boolean useContentProvider(Context context) {
        // If TIS is a part of LC, it should use ContentProvider to resolve multiple process access.
        return TisConfiguration.isPackagedWithLiveChannels(context);
    }

    @MainThread
    public static int getChannelDataVersion(Context context) {
        SoftPreconditions.checkState(sInitialized);
        if (useContentProvider(context)) {
            return sPreferenceValues.getInt(PREFS_KEY_CHANNEL_DATA_VERSION,
                    CHANNEL_DATA_VERSION_NOT_SET);
        } else {
            return getSharedPreferences(context)
                    .getInt(TunerPreferences.PREFS_KEY_CHANNEL_DATA_VERSION,
                            CHANNEL_DATA_VERSION_NOT_SET);
        }
    }

    @MainThread
    public static void setChannelDataVersion(Context context, int version) {
        if (useContentProvider(context)) {
            setPreference(context, PREFS_KEY_CHANNEL_DATA_VERSION, version);
        } else {
            getSharedPreferences(context).edit()
                    .putInt(TunerPreferences.PREFS_KEY_CHANNEL_DATA_VERSION, version)
                    .apply();
        }
    }

    @MainThread
    public static int getScannedChannelCount(Context context) {
        SoftPreconditions.checkState(sInitialized);
        if (useContentProvider(context)) {
            return sPreferenceValues.getInt(PREFS_KEY_SCANNED_CHANNEL_COUNT);
        } else {
            return getSharedPreferences(context)
                    .getInt(TunerPreferences.PREFS_KEY_SCANNED_CHANNEL_COUNT, 0);
        }
    }

    @MainThread
    public static void setScannedChannelCount(Context context, int channelCount) {
        if (useContentProvider(context)) {
            setPreference(context, PREFS_KEY_SCANNED_CHANNEL_COUNT, channelCount);
        } else {
            getSharedPreferences(context).edit()
                    .putInt(TunerPreferences.PREFS_KEY_SCANNED_CHANNEL_COUNT, channelCount)
                    .apply();
        }
    }

    @MainThread
    public static boolean isScanDone(Context context) {
        SoftPreconditions.checkState(sInitialized);
        if (useContentProvider(context)) {
            return sPreferenceValues.getBoolean(PREFS_KEY_SCAN_DONE);
        } else {
            return getSharedPreferences(context)
                    .getBoolean(TunerPreferences.PREFS_KEY_SCAN_DONE, false);
        }
    }

    @MainThread
    public static void setScanDone(Context context) {
        if (useContentProvider(context)) {
            setPreference(context, PREFS_KEY_SCAN_DONE, true);
        } else {
            getSharedPreferences(context).edit()
                    .putBoolean(TunerPreferences.PREFS_KEY_SCAN_DONE, true)
                    .apply();
        }
    }

    @MainThread
    public static boolean shouldShowSetupActivity(Context context) {
        SoftPreconditions.checkState(sInitialized);
        if (useContentProvider(context)) {
            return sPreferenceValues.getBoolean(PREFS_KEY_LAUNCH_SETUP);
        } else {
            return getSharedPreferences(context)
                    .getBoolean(TunerPreferences.PREFS_KEY_LAUNCH_SETUP, false);
        }
    }

    @MainThread
    public static void setShouldShowSetupActivity(Context context, boolean need) {
        if (useContentProvider(context)) {
            setPreference(context, PREFS_KEY_LAUNCH_SETUP, need);
        } else {
            getSharedPreferences(context).edit()
                    .putBoolean(TunerPreferences.PREFS_KEY_LAUNCH_SETUP, need)
                    .apply();
        }
    }

    @MainThread
    public static boolean getStoreTsStream(Context context) {
        SoftPreconditions.checkState(sInitialized);
        if (useContentProvider(context)) {
            return sPreferenceValues.getBoolean(PREFS_KEY_STORE_TS_STREAM, false);
        } else {
            return getSharedPreferences(context)
                    .getBoolean(TunerPreferences.PREFS_KEY_STORE_TS_STREAM, false);
        }
    }

    @MainThread
    public static void setStoreTsStream(Context context, boolean shouldStore) {
        if (useContentProvider(context)) {
            setPreference(context, PREFS_KEY_STORE_TS_STREAM, shouldStore);
        } else {
            getSharedPreferences(context).edit()
                    .putBoolean(TunerPreferences.PREFS_KEY_STORE_TS_STREAM, shouldStore)
                    .apply();
        }
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    }

    @MainThread
    private static void setPreference(final Context context, final String key, final String value) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                ContentResolver resolver = context.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(Preferences.COLUMN_KEY, key);
                values.put(Preferences.COLUMN_VALUE, value);
                try {
                    resolver.insert(Preferences.CONTENT_URI, values);
                } catch (Exception e) {
                    SoftPreconditions.warn(TAG, "setPreference", "Error writing preference values",
                            e);
                }
                return null;
            }
        }.execute();
    }

    @MainThread
    private static void setPreference(Context context, String key, int value) {
        sPreferenceValues.putInt(key, value);
        setPreference(context, key, Integer.toString(value));
    }

    @MainThread
    private static void setPreference(Context context, String key, boolean value) {
        sPreferenceValues.putBoolean(key, value);
        setPreference(context, key, Boolean.toString(value));
    }

    private static class LoadPreferencesTask extends AsyncTask<Void, Void, Bundle> {
        private final Context mContext;
        private LoadPreferencesTask(Context context) {
            mContext = context;
        }

        @Override
        protected Bundle doInBackground(Void... params) {
            Bundle bundle = new Bundle();
            ContentResolver resolver = mContext.getContentResolver();
            String[] projection = new String[] { Preferences.COLUMN_KEY, Preferences.COLUMN_VALUE };
            try (Cursor cursor = resolver.query(Preferences.CONTENT_URI, projection, null, null,
                    null)) {
                if (cursor != null) {
                    while (!isCancelled() && cursor.moveToNext()) {
                        String key = cursor.getString(0);
                        String value = cursor.getString(1);
                        switch (key) {
                            case PREFS_KEY_CHANNEL_DATA_VERSION:
                            case PREFS_KEY_SCANNED_CHANNEL_COUNT:
                                try {
                                    bundle.putInt(key, Integer.parseInt(value));
                                } catch (NumberFormatException e) {
                                    // Does nothing.
                                }
                                break;
                            case PREFS_KEY_SCAN_DONE:
                            case PREFS_KEY_LAUNCH_SETUP:
                            case PREFS_KEY_STORE_TS_STREAM:
                                bundle.putBoolean(key, Boolean.parseBoolean(value));
                                break;
                        }
                    }
                }
            } catch (Exception e) {
                SoftPreconditions.warn(TAG, "getPreference", "Error querying preference values", e);
                return null;
            }
            return bundle;
        }

        @Override
        protected void onPostExecute(Bundle bundle) {
            sPreferenceValues.putAll(bundle);
        }
    }
}
