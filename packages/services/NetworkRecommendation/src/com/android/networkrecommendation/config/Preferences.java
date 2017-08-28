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

import com.android.networkrecommendation.config.PreferenceFile.SharedPreference;
import java.util.Collections;
import java.util.Set;

/** The NetRec preferences file. */
public final class Preferences {
    private Preferences() {}

    private static final PreferenceFile sPrefs =
            new PreferenceFile("com.android.networkrecommendation");

    /**
     * {@link ScoreNetworksChimeraBroadcastReceiver} sets this to true when the scorer is enabled.
     * {@link com.android.networkrecommendation.scoring.service.FutureRefreshRequestor} checks for
     * this value and triggers a quick score refresh if this is set.
     */
    public static final SharedPreference<Boolean> justEnabled =
            sPrefs.booleanValue("justEnabled", false);

    /**
     * The next time, in ms since system boot, that a rapid (i.e. outside the usual refresh window)
     * will be allowed to make a network request.
     */
    public static final SharedPreference<Long> nextRapidRefreshAllowed =
            sPrefs.longValue("nextRapidRefreshAllowedMillis", 0L);

    /**
     * The set of saved ssid hashes in previous scan result list when the user disabled Wi-Fi. Saved
     * to preferences when {@link com.android.networkrecommendation.wakeup.WifiWakeupController}
     * stops.
     */
    public static final SharedPreference<Set<String>> savedSsidsOnDisable =
            sPrefs.stringSetValue("savedSsidsOnDisable", Collections.emptySet());

    /**
     * The set of saved ssid hashes that were previously shown as Wi-Fi Enabled notifications
     * through {@link com.android.networkrecommendation.wakeup.WifiWakeupController}.
     */
    public static final SharedPreference<Set<String>> ssidsForWakeupShown =
            sPrefs.stringSetValue("ssidsForWakeupShown", Collections.emptySet());

    /** Key for {@link com.android.networkrecommendation.storage.Encrypter} on pre-MNC devices. */
    public static final SharedPreference<String> encrypterKey =
            sPrefs.stringValue("encrypterKey", null);

    /**
     * How long we should wait before requesting another network. Used by {@link
     * PersistentNetworkRequest} to support GCS/WFA.
     */
    public static final SharedPreference<Integer> nextNetworkRequestDelayMs =
            sPrefs.intValue("nextNetworkRequestDelayMs", 0);

    /**
     * Hash of the SSID that last satisfied the network request in {@link PersistentNetworkRequest}.
     */
    public static final SharedPreference<String> lastSsidHash =
            sPrefs.stringValue("lastSsidHash", "");
}
