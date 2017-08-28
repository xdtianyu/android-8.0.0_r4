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
package com.android.networkrecommendation.util;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.Locale;

/**
 * Wrapper for {@link Log} which adds the calling class/method to logged items.
 *
 * This works by traversing up the call stack and finding the first calling class whose name does
 * not end with "Log" (allowing clients to add another layer such as AppLog when a constant tag is
 * desired across the application).
 */
public final class Blog {
    private static final String TAG = "Blog";

    private Blog() {}

    public static void i(String tag, String format, Object... args) {
        Log.i(tag, buildMessage(format, args));
    }

    public static void i(String tag, Throwable tr, String format, Object... args) {
        Log.i(tag, buildMessage(format, args), tr);
    }

    public static void v(String tag, String format, Object... args) {
        // Not guarded with Log.isLoggable - these calls should be stripped out by proguard for
        // release builds.
        Log.v(tag, buildMessage(format, args));
    }

    public static void v(String tag, Throwable tr, String format, Object... args) {
        // Not guarded with Log.isLoggable - these calls should be stripped out by proguard for
        // release builds.
        Log.v(tag, buildMessage(format, args), tr);
    }

    public static void d(String tag, String format, Object... args) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, buildMessage(format, args));
        }
    }

    public static void d(String tag, Throwable tr, String format, Object... args) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, buildMessage(format, args), tr);
        }
    }

    public static void w(String tag, Throwable tr, String format, Object... args) {
        Log.w(tag, buildMessage(format, args), tr);
    }

    public static void w(String tag, String format, Object... args) {
        Log.w(tag, buildMessage(format, args));
    }

    public static void e(String tag, String format, Object... args) {
        Log.e(tag, buildMessage(format, args));
    }

    public static void e(String tag, Throwable tr, String format, Object... args) {
        Log.e(tag, buildMessage(format, args), tr);
    }

    public static void wtf(String tag, String format, Object... args) {
        // Ensure we always log a stack trace when calling Log.wtf.
        Log.wtf(tag, buildMessage(format, args), new WhatATerribleException());
    }

    public static void wtf(String tag, Throwable tr, String format, Object...args) {
        Log.wtf(tag, buildMessage(format, args), new WhatATerribleException(tr));
    }

    /**
     * Redact personally identifiable information for production users.
     *
     * If:
     *     1) String is null
     *     2) String is empty
     *     3) enableSensitiveLogging param passed in is true
     *   Return the String value of the input object.
     * Else:
     *   Return a SHA-1 hash of the String value of the input object.
     */
    public static String pii(Object pii, boolean enableSensitiveLogging) {
        String val = String.valueOf(pii);
        if (pii == null || TextUtils.isEmpty(val) || enableSensitiveLogging) {
            return val;
        }
        return "[" + secureHash(val.getBytes()) + "]";
    }

    /**
     * Returns a secure hash (using the SHA1 algorithm) of the provided input.
     *
     * @return the hash
     * @param input the bytes for which the secure hash should be computed.
     */
    public static String secureHash(byte[] input) {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        messageDigest.update(input);
        byte[] result = messageDigest.digest();
        return Base64.encodeToString(
                result, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    /**
     * Formats the caller's provided message and prepends useful info like
     * calling thread ID and method name.
     */
    private static String buildMessage(String format, Object... args) {
        String msg;
        try {
            msg = (args == null || args.length == 0) ? format
                    : String.format(Locale.US, format, args);
        } catch (IllegalFormatException ife) {
            String formattedArgs = Arrays.toString(args);
            wtf(TAG, ife, "msg: \"%s\" args: %s", format, formattedArgs);
            msg = format + " " + formattedArgs;
        }
        StackTraceElement[] trace = new Throwable().fillInStackTrace().getStackTrace();
        String caller = "<unknown>";
        // Walk up the stack looking for the first caller outside of Blog whose name doesn't end
        // with "Log". It will be at least two frames up, so start there.
        for (int i = 2; i < trace.length; i++) {
            String callingClass = trace[i].getClassName();
            if (!callingClass.equals(Blog.class.getName())
                        && !callingClass.endsWith("Log")) {
                callingClass = callingClass.substring(callingClass.lastIndexOf('.') + 1);
                callingClass = callingClass.substring(callingClass.lastIndexOf('$') + 1);
                caller = callingClass + "." + trace[i].getMethodName();
                break;
            }
        }
        return String.format(Locale.US, "[%d] %s: %s",
                Thread.currentThread().getId(), caller, msg);
    }

    /** Named exception thrown when calling wtf(). */
    public static class WhatATerribleException extends RuntimeException {
        public WhatATerribleException() {
            super();
        }
        public WhatATerribleException(Throwable t) {
            super(t);
        }
    }
}
