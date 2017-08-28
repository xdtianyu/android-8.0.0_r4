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

import static com.android.networkrecommendation.Constants.TAG;

import android.net.WifiKey;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.android.networkrecommendation.config.G;

/** Utility methods for Wifi Network SSID and BSSID manipulation. */
public final class SsidUtil {

    // A special BSSID used to indicate a wildcard/ignore.
    // The MAC address this refers to is reserved by IANA
    // http://www.iana.org/assignments/ethernet-numbers/ethernet-numbers.xhtml
    public static final String BSSID_IGNORE = "00:00:5E:00:00:00";

    /** Quote an SSID if it hasn't already been quoted. */
    @Nullable
    public static String quoteSsid(@Nullable String ssid) {
        if (ssid == null) {
            return null;
        }
        if (isValidQuotedSsid(ssid)) {
            return ssid;
        }
        return "\"" + ssid + "\"";
    }

    /** Strip initial and final quotations marks from an SSID. */
    public static String unquoteSsid(@Nullable String ssid) {
        if (ssid == null) {
            return null;
        }
        return ssid.replaceAll("^\"", "").replaceAll("\"$", "");
    }

    /**
     * Create a WifiKey for the given SSID/BSSID. Returns null if the key could not be created
     * (ssid/bssid are not valid patterns).
     */
    @Nullable
    public static WifiKey createWifiKey(String ssid, String bssid) {
        try {
            return new WifiKey(quoteSsid(ssid), bssid);
        } catch (IllegalArgumentException | NullPointerException e) {
            // Expect IllegalArgumentException only in Android O.
            Blog.e(
                    TAG,
                    e,
                    "Couldn't make a wifi key from %s/%s",
                    Blog.pii(ssid, G.Netrec.enableSensitiveLogging.get()),
                    Blog.pii(bssid, G.Netrec.enableSensitiveLogging.get()));
            return null;
        }
    }

    /**
     * Returns true if the given string will be accepted as an SSID by WifiKey, especially meaning
     * it is quoted.
     */
    public static boolean isValidQuotedSsid(@Nullable String ssid) {
        return ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"");
    }

    /** Thows IllegalArgumentException if the given string cannot be used for an SSID in WifiKey. */
    public static void checkIsValidQuotedSsid(String ssid) {
        if (!isValidQuotedSsid(ssid)) {
            throw new IllegalArgumentException("SSID " + ssid + " expected to be quoted");
        }
    }

    /**
     * Returns true if the canonical version of two SSIDs (ignoring wrapping quotations) is equal.
     */
    public static boolean areEqual(@Nullable String ssid1, @Nullable String ssid2) {
        String quotedSsid1 = quoteSsid(ssid1);
        String quotedSsid2 = quoteSsid(ssid2);
        return TextUtils.equals(quotedSsid1, quotedSsid2);
    }

    /**
     * Returns a string version of the SSID for logging which is typically redacted.
     *
     * <p>The ID will only be returned verbatim if the enableSensitiveLogging flag is set.
     */
    public static String getRedactedId(String ssid) {
        return Blog.pii(String.format("%s", ssid), G.Netrec.enableSensitiveLogging.get());
    }

    /**
     * Returns a string version of the SSID/BSSID pair for logging which is typically redacted.
     *
     * <p>The IDs will only be returned verbatim if the enableSensitiveLogging flag is set.
     */
    public static String getRedactedId(String ssid, String bssid) {
        return Blog.pii(String.format("%s/%s", ssid, bssid), G.Netrec.enableSensitiveLogging.get());
    }

    // Can't instantiate.
    private SsidUtil() {}
}
