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
package com.android.networkrecommendation.scoring.util;

import android.util.Base64;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Hashcode and encoding utils. */
public final class HashUtil {
    private HashUtil() {}

    /**
     * Returns a base64-encoded secure hash (using the SHA-256 algorithm) of the provided input.
     *
     * @param input the bytes for which the secure hash should be computed.
     * @return the hash
     */
    public static String secureHash(String input) {
        return encodeBase64(getHash(input, "SHA-256"));
    }

    public static String encodeBase64(byte[] input) {
        return Base64.encodeToString(input, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    /** Retrieves the message digest instance for a given hash algorithm. */
    public static MessageDigest getMessageDigest(String hashAlgorithm) {
        try {
            return MessageDigest.getInstance(hashAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /**
     * @return hash of input using hashAlgorithm, or 0-length byte array if input is null, or null
     *     if hashAlgorithm can't be loaded
     */
    public static byte[] getHash(String input, String hashAlgorithm) {
        if (input != null) {
            MessageDigest digest = getMessageDigest(hashAlgorithm);
            if (digest == null) {
                return null;
            }
            return digest.digest(input.getBytes());
        }
        return new byte[0];
    }

    /**
     * Gets an SSID-specific hash which also ensures the SSID is in cannonical form (stripped of
     * quotes).
     */
    public static String getSsidHash(String ssid) {
        return secureHash(NetworkUtil.canonicalizeSsid(ssid));
    }

    /** Gets a single hash of over the combined SSID and BSSID. */
    public static String getSsidBssidHash(String ssid, String bssid) {
        String canonicalSsid = NetworkUtil.canonicalizeSsid(ssid);
        return secureHash(canonicalSsid + bssid);
    }

    /** Return the first 8 bytes of the SHA-256 hash of the given ssid as a long value. */
    public static long hashAsLong(String ssid) {
        byte[] h = getHash(ssid, "SHA-256");
        if (h == null || h.length < 8) {
            return 0;
        }
        return (h[0] & 0xFFL) << 56
                | (h[1] & 0xFFL) << 48
                | (h[2] & 0xFFL) << 40
                | (h[3] & 0xFFL) << 32
                | (h[4] & 0xFFL) << 24
                | (h[5] & 0xFFL) << 16
                | (h[6] & 0xFFL) << 8
                | (h[7] & 0xFFL);
    }
}
