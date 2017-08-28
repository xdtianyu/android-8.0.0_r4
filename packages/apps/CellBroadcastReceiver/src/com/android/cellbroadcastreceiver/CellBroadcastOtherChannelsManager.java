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

package com.android.cellbroadcastreceiver;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.util.Log;
import android.util.SparseArray;

import com.android.cellbroadcastreceiver.CellBroadcastAlertAudio.ToneType;

import java.util.ArrayList;

/**
 * CellBroadcastOtherChannelsManager handles the additional cell broadcast channels that
 * carriers might enable through carrier config app.
 * Syntax: "<channel id range>:type=<tone type>"
 * For example,
 * <string-array name="carrier_additional_cbs_channels_strings" num="3">
 *     <item value="43008:type=earthquake" />
 *     <item value="0xAFEE:type=tsunami" />
 *     <item value="0xAC00-0xAFED:type=other" />
 *     <item value="1234-5678" />
 * </string-array>
 * If no tones are specified, the tone type will be set to CMAS_DEFAULT.
 */
public class CellBroadcastOtherChannelsManager {

    private static final String TAG = "CellBroadcastOtherChannelsManager";

    private static CellBroadcastOtherChannelsManager sInstance = null;

    /**
     * Channel range caches with sub id as the key.
     */
    private static SparseArray<ArrayList<CellBroadcastChannelRange>> sChannelRanges =
            new SparseArray<>();

    /**
     * Cell broadcast channel range
     * A range is consisted by starting channel id, ending channel id, and the tone type
     */
    public static class CellBroadcastChannelRange {

        private static final String KEY_TYPE = "type";
        private static final String KEY_EMERGENCY = "emergency";

        public int mStartId;
        public int mEndId;
        public ToneType mToneType;
        public boolean mIsEmergency;

        public CellBroadcastChannelRange(String channelRange) throws Exception {

            mToneType = ToneType.CMAS_DEFAULT;
            mIsEmergency = false;

            int colonIndex = channelRange.indexOf(':');
            if (colonIndex != -1){
                // Parse the tone type and emergency flag
                String[] pairs = channelRange.substring(colonIndex + 1).trim().split(",");
                for (String pair : pairs) {
                    pair = pair.trim();
                    String[] tokens = pair.split("=");
                    if (tokens.length == 2) {
                        String key = tokens[0].trim();
                        String value = tokens[1].trim();
                        switch (key) {
                            case KEY_TYPE:
                                mToneType = ToneType.valueOf(value.toUpperCase());
                                break;
                            case KEY_EMERGENCY:
                                mIsEmergency = value.equalsIgnoreCase("true");
                                break;
                        }
                    }
                }
                channelRange = channelRange.substring(0, colonIndex).trim();
            }

            // Parse the channel range
            int dashIndex = channelRange.indexOf('-');
            if (dashIndex != -1) {
                // range that has start id and end id
                mStartId = Integer.decode(channelRange.substring(0, dashIndex).trim());
                mEndId = Integer.decode(channelRange.substring(dashIndex + 1).trim());
            } else {
                // Not a range, only a single id
                mStartId = mEndId = Integer.decode(channelRange);
            }
        }
    }

    /**
     * Get the instance of the cell broadcast other channel manager
     * @return The singleton instance
     */
    public static CellBroadcastOtherChannelsManager getInstance() {
        if (sInstance == null) {
            sInstance = new CellBroadcastOtherChannelsManager();
        }
        return sInstance;
    }

    /**
     * Get cell broadcast channels enabled by the carriers.
     * @param context Application context
     * @param subId Subscription id
     * @return The list of channel ranges enabled by the carriers.
     */
     public ArrayList<CellBroadcastChannelRange> getCellBroadcastChannelRanges(
            Context context, int subId) {

        // Check if the cache already had it.
        if (sChannelRanges.get(subId) == null) {

            if (context == null) {
                loge("context is null");
                return null;
            }

            ArrayList<CellBroadcastChannelRange> result = new ArrayList<>();
            String[] ranges;
            CarrierConfigManager configManager =
                    (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);

            if (configManager != null) {
                PersistableBundle carrierConfig = configManager.getConfigForSubId(subId);

                if (carrierConfig != null) {
                    ranges = carrierConfig.getStringArray(
                            CarrierConfigManager.KEY_CARRIER_ADDITIONAL_CBS_CHANNELS_STRINGS);

                    if (ranges == null || ranges.length == 0) {
                        log("No additional channels configured. subId = " + subId);

                        // If there is nothing configured, store an empty list in the cache
                        // so we won't look up again next time.
                        sChannelRanges.put(subId, result);
                        return result;
                    }

                    for (String range : ranges) {
                        try {
                            result.add(new CellBroadcastChannelRange(range));
                        } catch (Exception e) {
                            loge("Failed to parse \"" + range + "\". e=" + e);
                        }
                    }

                    sChannelRanges.put(subId, result);

                } else {
                    loge("Can't get carrier config. subId=" + subId);
                    return null;
                }
            } else {
                loge("Carrier config manager is not available");
                return null;
            }
        }

        return sChannelRanges.get(subId);
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    private static void loge(String msg) {
        Log.e(TAG, msg);
    }
}
