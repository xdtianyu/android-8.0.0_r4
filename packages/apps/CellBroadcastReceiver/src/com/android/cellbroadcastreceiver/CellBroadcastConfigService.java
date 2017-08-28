/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.cellbroadcastreceiver.CellBroadcastOtherChannelsManager.CellBroadcastChannelRange;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.gsm.SmsCbConstants;

import java.util.ArrayList;

import static com.android.cellbroadcastreceiver.CellBroadcastReceiver.VDBG;

/**
 * This service manages enabling and disabling ranges of message identifiers
 * that the radio should listen for. It operates independently of the other
 * services and runs at boot time and after exiting airplane mode.
 *
 * Note that the entire range of emergency channels is enabled. Test messages
 * and lower priority broadcasts are filtered out in CellBroadcastAlertService
 * if the user has not enabled them in settings.
 *
 * TODO: add notification to re-enable channels after a radio reset.
 */
public class CellBroadcastConfigService extends IntentService {
    private static final String TAG = "CellBroadcastConfigService";

    static final String ACTION_ENABLE_CHANNELS = "ACTION_ENABLE_CHANNELS";

    private static final String COUNTRY_TAIWAN = "tw";
    private static final String COUNTRY_ISRAEL = "ir";
    private static final String COUNTRY_BRAZIL = "br";

    public CellBroadcastConfigService() {
        super(TAG);          // use class name for worker thread name
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (ACTION_ENABLE_CHANNELS.equals(intent.getAction())) {
            try {

                SubscriptionManager subManager = SubscriptionManager.from(getApplicationContext());
                int subId = SubscriptionManager.getDefaultSmsSubscriptionId();
                if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    subId = SubscriptionManager.getDefaultSubscriptionId();
                    if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
                            subManager != null) {
                        int [] subIds = subManager.getActiveSubscriptionIdList();
                        if (subIds.length != 0) {
                            subId = subIds[0];
                        }
                    }
                }

                if (subManager != null) {
                    // Retrieve all the active sub ids. We only want to enable
                    // cell broadcast on the sub we are interested in and we'll disable
                    // it on other subs so the users will not receive duplicate messages from
                    // multiple carriers (e.g. for multi-sim users).
                    int [] subIds = subManager.getActiveSubscriptionIdList();
                    if (subIds.length != 0)
                    {
                        for (int id : subIds) {
                            SmsManager manager = SmsManager.getSmsManagerForSubscriptionId(id);
                            if (manager != null) {
                                if (id == subId) {
                                    // Enable cell broadcast messages on this sub.
                                    log("Enable CellBroadcast on sub " + id);
                                    setCellBroadcastOnSub(manager, id, true);
                                }
                                else {
                                    // Disable all cell broadcast message on this sub.
                                    // This is only for multi-sim scenario. For single SIM device
                                    // we should not reach here.
                                    log("Disable CellBroadcast on sub " + id);
                                    setCellBroadcastOnSub(manager, id, false);
                                }
                            }
                        }
                    }
                    else {
                        // For no sim scenario.
                        SmsManager manager = SmsManager.getDefault();
                        if (manager != null) {
                            setCellBroadcastOnSub(manager,
                                    SubscriptionManager.INVALID_SUBSCRIPTION_ID, true);
                        }
                    }
                }
            } catch (Exception ex) {
                Log.e(TAG, "exception enabling cell broadcast channels", ex);
            }
        }
    }

    /**
     * Enable/disable cell broadcast messages id on one subscription
     * This includes all ETWS and CMAS alerts.
     * @param manager SMS manager
     * @param subId Subscription id
     * @param enableForSub True if want to enable messages on this sub (e.g default SMS). False
     *                     will disable all messages
     */
    private void setCellBroadcastOnSub(SmsManager manager, int subId, boolean enableForSub) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Resources res = getResources();

        // boolean for each user preference checkbox, true for checked, false for unchecked
        // Note: If enableEmergencyAlerts is false, it disables ALL emergency broadcasts
        // except for CMAS presidential. i.e. to receive CMAS severe alerts, both
        // enableEmergencyAlerts AND enableCmasSevereAlerts must be true.
        boolean enableEmergencyAlerts = enableForSub && prefs.getBoolean(
                CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);

        boolean enableEtwsAlerts = enableEmergencyAlerts;

        // CMAS Presidential must be always on (See 3GPP TS 22.268 Section 6.2) regardless
        // user's preference
        boolean enablePresidential = enableForSub;

        boolean enableCmasExtremeAlerts = enableEmergencyAlerts && prefs.getBoolean(
                CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, true);

        boolean enableCmasSevereAlerts = enableEmergencyAlerts && prefs.getBoolean(
                CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, true);

        boolean enableCmasAmberAlerts = enableEmergencyAlerts && prefs.getBoolean(
                CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, true);

        // Check if ETWS/CMAS test message is forced disabled on the device.
        boolean forceDisableEtwsCmasTest =
                CellBroadcastSettings.isFeatureEnabled(this,
                        CarrierConfigManager.KEY_CARRIER_FORCE_DISABLE_ETWS_CMAS_TEST_BOOL, false);

        boolean enableEtwsTestAlerts = !forceDisableEtwsCmasTest &&
                enableEmergencyAlerts &&
                prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_ETWS_TEST_ALERTS, false);

        boolean enableCmasTestAlerts = !forceDisableEtwsCmasTest &&
                enableEmergencyAlerts &&
                prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_CMAS_TEST_ALERTS, false);

        TelephonyManager tm = (TelephonyManager) getSystemService(
                Context.TELEPHONY_SERVICE);

        boolean enableChannel50Support = res.getBoolean(R.bool.show_brazil_settings) ||
                COUNTRY_BRAZIL.equals(tm.getSimCountryIso(subId));

        boolean enableChannel50Alerts = enableChannel50Support &&
                prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_CHANNEL_50_ALERTS, true);

        // Current Israel requires enable certain CMAS messages ids.
        boolean supportIsraelPwsAlerts = (COUNTRY_ISRAEL.equals(tm.getSimCountryIso(subId))
                || COUNTRY_ISRAEL.equals(tm.getNetworkCountryIso(subId)));

        boolean supportTaiwanPwsAlerts = (COUNTRY_TAIWAN.equals(tm.getSimCountryIso(subId))
                || COUNTRY_TAIWAN.equals(tm.getNetworkCountryIso(subId)));

        if (VDBG) {
            log("enableEmergencyAlerts = " + enableEmergencyAlerts);
            log("enableEtwsAlerts = " + enableEtwsAlerts);
            log("enablePresidential = " + enablePresidential);
            log("enableCmasExtremeAlerts = " + enableCmasExtremeAlerts);
            log("enableCmasSevereAlerts = " + enableCmasExtremeAlerts);
            log("enableCmasAmberAlerts = " + enableCmasAmberAlerts);
            log("forceDisableEtwsCmasTest = " + forceDisableEtwsCmasTest);
            log("enableEtwsTestAlerts = " + enableEtwsTestAlerts);
            log("enableCmasTestAlerts = " + enableCmasTestAlerts);
            log("enableChannel50Alerts = " + enableChannel50Alerts);
            log("supportIsraelPwsAlerts = " + supportIsraelPwsAlerts);
            log("supportTaiwanPwsAlerts = " + supportTaiwanPwsAlerts);
        }

        /** Enable CDMA CMAS series messages. */

        // Enable/Disable CDMA Presidential messages.
        setCellBroadcastRange(manager, enablePresidential,
                SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT);

        // Enable/Disable CDMA CMAS extreme messages.
        setCellBroadcastRange(manager, enableCmasExtremeAlerts,
                SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT);

        // Enable/Disable CDMA CMAS severe messages.
        setCellBroadcastRange(manager, enableCmasSevereAlerts,
                SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT);

        // Enable/Disable CDMA CMAS amber alert messages.
        setCellBroadcastRange(manager, enableCmasAmberAlerts,
                SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY);

        // Enable/Disable CDMA CMAS test messages.
        setCellBroadcastRange(manager, enableCmasTestAlerts,
                SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE);

        /** Enable GSM ETWS series messages. */

        // Enable/Disable GSM ETWS messages (4352~4354).
        setCellBroadcastRange(manager, enableEtwsAlerts,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM,
                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING);

        // Enable/Disable GSM ETWS messages (4356)
        setCellBroadcastRange(manager, enableEtwsAlerts,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM,
                SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE);

        // Enable/Disable GSM ETWS test messages (4355).
        setCellBroadcastRange(manager, enableEtwsTestAlerts,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM,
                SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
                SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE);

        /** Enable GSM CMAS series messages. */

        // Enable/Disable GSM CMAS presidential message (4370).
        setCellBroadcastRange(manager, enablePresidential,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL);

        // Enable/Disable GSM CMAS extreme messages (4371~4372).
        setCellBroadcastRange(manager, enableCmasExtremeAlerts,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY);

        // Enable/Disable GSM CMAS severe messages (4373~4378).
        setCellBroadcastRange(manager, enableCmasSevereAlerts,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY);

        // Enable/Disable GSM CMAS amber alert messages (4379).
        setCellBroadcastRange(manager, enableCmasAmberAlerts,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY);

        // Enable/Disable GSM CMAS test messages (4380~4382).
        setCellBroadcastRange(manager, enableCmasTestAlerts,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE);


        /** Enable GSM CMAS series messages for additional languages. */

        // Enable/Disable GSM CMAS presidential messages for additional languages (4383).
        setCellBroadcastRange(manager, enablePresidential,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE);

        // Enable/Disable GSM CMAS extreme messages for additional languages (4384~4385).
        setCellBroadcastRange(manager, enableCmasExtremeAlerts,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE);

        // Enable/Disable GSM CMAS severe messages for additional languages (4386~4391).
        setCellBroadcastRange(manager, enableCmasSevereAlerts,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_LANGUAGE,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_LANGUAGE);

        // Enable/Disable GSM CMAS amber alert messages for additional languages (4392).
        setCellBroadcastRange(manager, enableCmasAmberAlerts,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE);

        // Enable/Disable GSM CMAS test messages for additional languages (4393~4395).
        setCellBroadcastRange(manager, enableCmasTestAlerts,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST_LANGUAGE,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE_LANGUAGE);

        // Enable/Disable channel 50 messages for Brazil (50).
        setCellBroadcastRange(manager, enableChannel50Alerts,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM,
                SmsCbConstants.MESSAGE_ID_GSMA_ALLOCATED_CHANNEL_50,
                SmsCbConstants.MESSAGE_ID_GSMA_ALLOCATED_CHANNEL_50);

        // Enable/Disable additional channels based on carrier specific requirement.
        ArrayList<CellBroadcastChannelRange> ranges = CellBroadcastOtherChannelsManager.
                getInstance().getCellBroadcastChannelRanges(getApplicationContext(), subId);

        if (ranges != null) {
            for (CellBroadcastChannelRange range: ranges) {
                setCellBroadcastRange(manager, enableEmergencyAlerts,
                        SmsManager.CELL_BROADCAST_RAN_TYPE_GSM,
                        range.mStartId, range.mEndId);
            }
        }

        // Enable/Disable additional channels based on country specific requirement.
        if (supportIsraelPwsAlerts) {
            // Enable/Disable Israel PWS channels (919~928).
            setCellBroadcastRange(manager, enableEmergencyAlerts,
                    SmsManager.CELL_BROADCAST_RAN_TYPE_GSM,
                    SmsCbConstants.MESSAGE_ID_GSMA_ALLOCATED_CHANNEL_919,
                    SmsCbConstants.MESSAGE_ID_GSMA_ALLOCATED_CHANNEL_928);
        } else if (supportTaiwanPwsAlerts) {
            // Enable/Disable Taiwan PWS Chinese channel (911).
            setCellBroadcastRange(manager, enableEmergencyAlerts,
                    SmsManager.CELL_BROADCAST_RAN_TYPE_GSM,
                    SmsCbConstants.MESSAGE_ID_GSMA_ALLOCATED_CHANNEL_911,
                    SmsCbConstants.MESSAGE_ID_GSMA_ALLOCATED_CHANNEL_911);

            // Enable/Disable Taiwan PWS English channel (919).
            setCellBroadcastRange(manager, enableEmergencyAlerts,
                    SmsManager.CELL_BROADCAST_RAN_TYPE_GSM,
                    SmsCbConstants.MESSAGE_ID_GSMA_ALLOCATED_CHANNEL_919,
                    SmsCbConstants.MESSAGE_ID_GSMA_ALLOCATED_CHANNEL_919);
        }
    }
    /**
     * Enable/disable cell broadcast with messages id range
     * @param manager SMS manager
     * @param enable True for enabling cell broadcast with id range, otherwise for disabling.
     * @param type GSM or CDMA
     * @param start Cell broadcast id range start
     * @param end Cell broadcast id range end
     */
    private boolean setCellBroadcastRange(
            SmsManager manager, boolean enable, int type, int start, int end) {
        if (enable) {
            return manager.enableCellBroadcastRange(start, end, type);
        } else {
            return manager.disableCellBroadcastRange(start, end, type);
        }
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
