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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for retrieving and storing key/value pairs in a SharedPreferences file.
 *
 * Typical usage:
 *
 * In your application's onCreate();
 * PreferenceFile.init(this);
 *
 * In common preferences declaration area:
 * private static final PreferenceFile sFile = new PreferenceFile("my_prefs");
 * public static final SharedPreference<String> pageUrl = sFile.value("page_url", "http://blah");
 *
 * At usage time:
 * String pageUrl = Preferences.pageUrl.get();
 * Preferences.pageUrl.put("http://www.newurl.com/");
 */
public class PreferenceFile {
    private static final String TAG = "PreferenceFile";

    private static Context sContext;

    public static void init(Context context) {
        sContext = context;
    }

    private final String mName;
    private final int mMode;

    @SuppressWarnings("deprecation")
    public PreferenceFile(String name) {
        this(name, Context.MODE_PRIVATE);
    }

    /**
     * @deprecated any mode other than MODE_PRIVATE is a bad idea. If you need multi-process
     * support, see if {@link MultiProcessPreferenceFile} is suitable.
     */
    @Deprecated
    public PreferenceFile(String name, int mode) {
        mName = name;
        mMode = mode;
    }

    /**
     * Returns a text dump of all preferences in this file; for debugging.
     */
    public String dump() {
        SharedPreferences sp = open();
        Map<String, ?> allPrefs = sp.getAll();
        String format = "%" + longestString(allPrefs.keySet()) + "s = %s\n";
        StringBuilder s = new StringBuilder();
        for (String key : allPrefs.keySet()) {
            s.append(String.format(Locale.US, format, key, allPrefs.get(key)));
        }
        return s.toString();
    }

    /** Return a SharedPreferences for this file. */
    public SharedPreferences open() {
        return sContext.getSharedPreferences(mName, mMode);
    }

    public void remove(SharedPreference<?>... preferences) {
        SharedPreferences.Editor editor = open().edit();
        for (SharedPreference<?> preference : preferences) {
            editor.remove(preference.getKey());
        }
        commit(editor);
    }

    /** Synchronously clear all SharedPreferences in this file. */
    public void clear() {
        open().edit().clear().commit();
    }


    /**
     * If on API >= 9, use the asynchronous
     * {@link Editor#apply()} method. Otherwise, use the
     * synchronous {@link Editor#commit()} method. <br />
     * <br />
     * If commit() is used, the result will be returned. If apply() is used, it
     * will always return true.
     *
     * @param editor The editor to save
     * @return the result of commit() on API &lt; 9 and true on API >= 9.
     */
    @SuppressLint("NewApi")
    public static boolean commit(Editor editor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
            return editor.commit();
        }
        editor.apply();
        return true;
    }

    /** Define a new Long value shared pref key. */
    public SharedPreference<Long> longValue(final String key, final Long defaultValue) {
        return new SharedPreference<Long>(this, key) {
            @Override
            protected Long read(SharedPreferences sp) {
                if (sp.contains(key)) {
                    return sp.getLong(key, 0L);
                }
                return defaultValue;
            }

            @Override
            protected void write(Editor editor, Long value) {
                if (value == null) {
                    throw new IllegalArgumentException("null cannot be written for Long");
                }
                editor.putLong(key, value);
            }
        };
    }

    /** Define a new String value shared pref key. */
    public SharedPreference<String> stringValue(final String key, final String defaultValue) {
        return new SharedPreference<String>(this, key) {
            @Override
            protected String read(SharedPreferences sp) {
                if (sp.contains(key)) {
                    return sp.getString(key, null);
                }
                return defaultValue;
            }

            @Override
            protected void write(Editor editor, String value) {
                if (value == null) {
                    throw new IllegalArgumentException("null cannot be written for String");
                }
                editor.putString(key, value);
            }
        };
    }

    /** Define a new Boolean value shared pref key. */
    public SharedPreference<Boolean> booleanValue(final String key, final Boolean defaultValue) {
        return new SharedPreference<Boolean>(this, key) {
            @Override
            protected Boolean read(SharedPreferences sp) {
                if (sp.contains(key)) {
                    return sp.getBoolean(key, false);
                }
                return defaultValue;
            }

            @Override
            protected void write(Editor editor, Boolean value) {
                if (value == null) {
                    throw new IllegalArgumentException("null cannot be written for Boolean");
                }
                editor.putBoolean(key, value);
            }
        };
    }

    /** Define a new Integer value shared pref key. */
    public SharedPreference<Integer> intValue(final String key, final Integer defaultValue) {
        return new SharedPreference<Integer>(this, key) {
            @Override
            protected Integer read(SharedPreferences sp) {
                if (sp.contains(key)) {
                    return sp.getInt(key, 0);
                }
                return defaultValue;
            }

            @Override
            protected void write(Editor editor, Integer value) {
                if (value == null) {
                    throw new IllegalArgumentException("null cannot be written for Integer");
                }
                editor.putInt(key, value);
            }
        };
    }

    /** Define a new Set<String> value shared pref key. */
    public SharedPreference<Set<String>> stringSetValue(final String key,
            final Set<String> defaultValue) {
        return new SharedPreference<Set<String>>(this, key) {
            @Override
            protected Set<String> read(SharedPreferences sp) {
                if (sp.contains(key)) {
                    return sp.getStringSet(key, null);
                }
                return defaultValue;
            }

            @Override
            protected void write(Editor editor, Set<String> value) {
                if (value == null) {
                    throw new IllegalArgumentException("null cannot be written for Set<String>");
                }
                editor.putStringSet(key, value);
            }
        };
    }

    /**
     * A class representing a key/value pair in a given {@link PreferenceFile}.
     */
    public static abstract class SharedPreference<T> {
        PreferenceFile mFile;
        final String mKey;

        protected SharedPreference(PreferenceFile file, String key) {
            mFile = file;
            mKey = key;
        }

        /** Read the value stored for this pref, or the default value if none is stored. */
        public final T get() {
            return read(mFile.open());
        }

        /** Get the representation in string of the value for this pref. */
        public String getValueString() {
            T value = get();
            if (value == null) {
                return null;
            }
            return value.toString();
        }

        /** Return this pref's key. */
        public final String getKey() {
            return mKey;
        }

        /** Return true if this key is defined in its file. */
        public final boolean exists() {
            return mFile.open().contains(mKey);
        }

        /** Write a new value for this pref to its file. */
        public final void put(T value) {
            SharedPreferences sp = mFile.open();
            Editor editor = sp.edit();
            write(editor, value);
            commit(editor);
        }

        /** Removes this pref from its file. */
        public final void remove() {
            commit(mFile.open().edit().remove(mKey));
        }

        /** Override the PreferenceFile used by this preference (for testing). */
        public final void override(PreferenceFile file) {
            mFile = file;
        }

        protected abstract T read(SharedPreferences sp);
        protected abstract void write(Editor editor, T value);
    }

    /**
     * Returns the length of the longest string in the provided Collection.
     */
    private static int longestString(Collection<String> strings) {
        int longest = 0;
        for (String s : strings) {
            longest = Math.max(longest, s.length());
        }
        return longest;
    }
}
