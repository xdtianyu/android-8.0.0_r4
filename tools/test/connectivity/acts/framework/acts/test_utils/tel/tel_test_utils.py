#!/usr/bin/env python3.4
#
#   Copyright 2016 - Google
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

from future import standard_library
standard_library.install_aliases()

import concurrent.futures
import json
import logging
import re
import os
import urllib.parse
import time

from queue import Empty
from acts.asserts import abort_all
from acts.controllers.adb import AdbError
from acts.controllers.android_device import AndroidDevice
from acts.controllers.event_dispatcher import EventDispatcher
from acts.test_utils.tel.tel_defines import AOSP_PREFIX
from acts.test_utils.tel.tel_defines import CARRIER_UNKNOWN
from acts.test_utils.tel.tel_defines import COUNTRY_CODE_LIST
from acts.test_utils.tel.tel_defines import DATA_STATE_CONNECTED
from acts.test_utils.tel.tel_defines import DATA_STATE_DISCONNECTED
from acts.test_utils.tel.tel_defines import DATA_ROAMING_ENABLE
from acts.test_utils.tel.tel_defines import DATA_ROAMING_DISABLE
from acts.test_utils.tel.tel_defines import GEN_4G
from acts.test_utils.tel.tel_defines import GEN_UNKNOWN
from acts.test_utils.tel.tel_defines import INCALL_UI_DISPLAY_BACKGROUND
from acts.test_utils.tel.tel_defines import INCALL_UI_DISPLAY_FOREGROUND
from acts.test_utils.tel.tel_defines import INVALID_SIM_SLOT_INDEX
from acts.test_utils.tel.tel_defines import INVALID_SUB_ID
from acts.test_utils.tel.tel_defines import MAX_SAVED_VOICE_MAIL
from acts.test_utils.tel.tel_defines import MAX_SCREEN_ON_TIME
from acts.test_utils.tel.tel_defines import \
    MAX_WAIT_TIME_ACCEPT_CALL_TO_OFFHOOK_EVENT
from acts.test_utils.tel.tel_defines import MAX_WAIT_TIME_AIRPLANEMODE_EVENT
from acts.test_utils.tel.tel_defines import MAX_WAIT_TIME_CALL_DROP
from acts.test_utils.tel.tel_defines import MAX_WAIT_TIME_CALL_INITIATION
from acts.test_utils.tel.tel_defines import MAX_WAIT_TIME_CALLEE_RINGING
from acts.test_utils.tel.tel_defines import MAX_WAIT_TIME_CONNECTION_STATE_UPDATE
from acts.test_utils.tel.tel_defines import MAX_WAIT_TIME_DATA_SUB_CHANGE
from acts.test_utils.tel.tel_defines import MAX_WAIT_TIME_CALL_IDLE_EVENT
from acts.test_utils.tel.tel_defines import MAX_WAIT_TIME_NW_SELECTION
from acts.test_utils.tel.tel_defines import MAX_WAIT_TIME_SMS_RECEIVE
from acts.test_utils.tel.tel_defines import MAX_WAIT_TIME_SMS_SENT_SUCCESS
from acts.test_utils.tel.tel_defines import MAX_WAIT_TIME_TELECOM_RINGING
from acts.test_utils.tel.tel_defines import MAX_WAIT_TIME_VOICE_MAIL_COUNT
from acts.test_utils.tel.tel_defines import MAX_WAIT_TIME_WFC_DISABLED
from acts.test_utils.tel.tel_defines import MAX_WAIT_TIME_WFC_ENABLED
from acts.test_utils.tel.tel_defines import NETWORK_MODE_LTE_ONLY
from acts.test_utils.tel.tel_defines import NETWORK_CONNECTION_TYPE_CELL
from acts.test_utils.tel.tel_defines import NETWORK_CONNECTION_TYPE_WIFI
from acts.test_utils.tel.tel_defines import NETWORK_SERVICE_DATA
from acts.test_utils.tel.tel_defines import NETWORK_SERVICE_VOICE
from acts.test_utils.tel.tel_defines import PHONE_NUMBER_STRING_FORMAT_7_DIGIT
from acts.test_utils.tel.tel_defines import PHONE_NUMBER_STRING_FORMAT_10_DIGIT
from acts.test_utils.tel.tel_defines import PHONE_NUMBER_STRING_FORMAT_11_DIGIT
from acts.test_utils.tel.tel_defines import PHONE_NUMBER_STRING_FORMAT_12_DIGIT
from acts.test_utils.tel.tel_defines import RAT_FAMILY_GSM
from acts.test_utils.tel.tel_defines import RAT_FAMILY_LTE
from acts.test_utils.tel.tel_defines import RAT_FAMILY_WLAN
from acts.test_utils.tel.tel_defines import RAT_FAMILY_WCDMA
from acts.test_utils.tel.tel_defines import RAT_1XRTT
from acts.test_utils.tel.tel_defines import RAT_UNKNOWN
from acts.test_utils.tel.tel_defines import SERVICE_STATE_EMERGENCY_ONLY
from acts.test_utils.tel.tel_defines import SERVICE_STATE_IN_SERVICE
from acts.test_utils.tel.tel_defines import SERVICE_STATE_OUT_OF_SERVICE
from acts.test_utils.tel.tel_defines import SERVICE_STATE_POWER_OFF
from acts.test_utils.tel.tel_defines import SIM_STATE_READY
from acts.test_utils.tel.tel_defines import WAIT_TIME_SUPPLY_PUK_CODE
from acts.test_utils.tel.tel_defines import TELEPHONY_STATE_IDLE
from acts.test_utils.tel.tel_defines import TELEPHONY_STATE_OFFHOOK
from acts.test_utils.tel.tel_defines import TELEPHONY_STATE_RINGING
from acts.test_utils.tel.tel_defines import VOICEMAIL_DELETE_DIGIT
from acts.test_utils.tel.tel_defines import WAIT_TIME_1XRTT_VOICE_ATTACH
from acts.test_utils.tel.tel_defines import WAIT_TIME_ANDROID_STATE_SETTLING
from acts.test_utils.tel.tel_defines import WAIT_TIME_BETWEEN_STATE_CHECK
from acts.test_utils.tel.tel_defines import WAIT_TIME_CHANGE_DATA_SUB_ID
from acts.test_utils.tel.tel_defines import WAIT_TIME_IN_CALL
from acts.test_utils.tel.tel_defines import WAIT_TIME_LEAVE_VOICE_MAIL
from acts.test_utils.tel.tel_defines import WAIT_TIME_REJECT_CALL
from acts.test_utils.tel.tel_defines import WAIT_TIME_VOICE_MAIL_SERVER_RESPONSE
from acts.test_utils.tel.tel_defines import WFC_MODE_DISABLED
from acts.test_utils.tel.tel_defines import EventCallStateChanged
from acts.test_utils.tel.tel_defines import EventConnectivityChanged
from acts.test_utils.tel.tel_defines import EventDataConnectionStateChanged
from acts.test_utils.tel.tel_defines import EventDataSmsReceived
from acts.test_utils.tel.tel_defines import EventMessageWaitingIndicatorChanged
from acts.test_utils.tel.tel_defines import EventServiceStateChanged
from acts.test_utils.tel.tel_defines import EventMmsSentSuccess
from acts.test_utils.tel.tel_defines import EventMmsDownloaded
from acts.test_utils.tel.tel_defines import EventSmsReceived
from acts.test_utils.tel.tel_defines import EventSmsSentSuccess
from acts.test_utils.tel.tel_defines import CallStateContainer
from acts.test_utils.tel.tel_defines import DataConnectionStateContainer
from acts.test_utils.tel.tel_defines import MessageWaitingIndicatorContainer
from acts.test_utils.tel.tel_defines import NetworkCallbackContainer
from acts.test_utils.tel.tel_defines import ServiceStateContainer
from acts.test_utils.tel.tel_lookup_tables import \
    connection_type_from_type_string
from acts.test_utils.tel.tel_lookup_tables import is_valid_rat
from acts.test_utils.tel.tel_lookup_tables import get_allowable_network_preference
from acts.test_utils.tel.tel_lookup_tables import \
    get_voice_mail_count_check_function
from acts.test_utils.tel.tel_lookup_tables import get_voice_mail_check_number
from acts.test_utils.tel.tel_lookup_tables import get_voice_mail_delete_digit
from acts.test_utils.tel.tel_lookup_tables import \
    network_preference_for_generaton
from acts.test_utils.tel.tel_lookup_tables import operator_name_from_plmn_id
from acts.test_utils.tel.tel_lookup_tables import \
    rat_families_for_network_preference
from acts.test_utils.tel.tel_lookup_tables import rat_family_for_generation
from acts.test_utils.tel.tel_lookup_tables import rat_family_from_rat
from acts.test_utils.tel.tel_lookup_tables import rat_generation_from_rat
from acts.test_utils.tel.tel_subscription_utils import \
    get_default_data_sub_id
from acts.test_utils.tel.tel_subscription_utils import \
    get_outgoing_message_sub_id
from acts.test_utils.tel.tel_subscription_utils import \
    get_outgoing_voice_sub_id
from acts.test_utils.tel.tel_subscription_utils import \
    get_incoming_voice_sub_id
from acts.test_utils.tel.tel_subscription_utils import \
    get_incoming_message_sub_id
from acts.test_utils.wifi import wifi_test_utils
from acts.test_utils.wifi import wifi_constants
from acts.utils import adb_shell_ping
from acts.utils import load_config

WIFI_SSID_KEY = wifi_test_utils.WifiEnums.SSID_KEY
WIFI_PWD_KEY = wifi_test_utils.WifiEnums.PWD_KEY
WIFI_CONFIG_APBAND_2G = wifi_test_utils.WifiEnums.WIFI_CONFIG_APBAND_2G
WIFI_CONFIG_APBAND_5G = wifi_test_utils.WifiEnums.WIFI_CONFIG_APBAND_5G
log = logging


class _CallSequenceException(Exception):
    pass


class TelTestUtilsError(Exception):
    pass


def abort_all_tests(log, msg):
    log.error("Aborting all ongoing tests due to: %s.", msg)
    abort_all(msg)


def get_phone_number_by_adb(ad):
    return phone_number_formatter(
        ad.adb.shell("service call iphonesubinfo 13"))


def get_iccid_by_adb(ad):
    return ad.adb.shell("service call iphonesubinfo 11")


def get_operator_by_adb(ad):
    return ad.adb.getprop("gsm.sim.operator.alpha")


def get_sub_id_by_adb(ad):
    return ad.adb.shell("service call iphonesubinfo 5")


def setup_droid_properties_by_adb(log, ad, sim_filename=None):

    # Check to see if droid already has this property
    if hasattr(ad, 'cfg'):
        return

    sim_data = None
    if sim_filename:
        try:
            sim_data = load_config(sim_filename)
        except Exception:
            log.warning("Failed to load %s!", sim_filename)

    sub_id = get_sub_id_by_adb(ad)
    if iccid in sim_data and sim_data[iccid].get("phone_num"):
        phone_number = phone_number_formatter(sim_data[iccid]["phone_num"])
    else:
        phone_number = get_phone_number_by_adb(ad)
    if not phone_number:
        abort_all_tests(ad.log, "Failed to find valid phone number")
    sim_record = {
        'phone_num': phone_number,
        'iccid': get_iccid_by_adb(ad),
        'sim_operator_name': get_operator_by_adb(ad)
    }
    device_props = {'subscription': {sub_id: sim_record}}
    ad.log.info("subId %s SIM record: %s", sub_id, sim_record)
    setattr(ad, 'cfg', device_props)


def setup_droid_properties(log, ad, sim_filename=None):

    # Check to see if droid already has this property
    if hasattr(ad, 'cfg'):
        return

    if ad.skip_sl4a:
        return setup_droid_properties_by_adb(
            log, ad, sim_filename=sim_filename)

    refresh_droid_config(log, ad)
    device_props = {}
    device_props['subscription'] = {}

    sim_data = {}
    if sim_filename:
        try:
            sim_data = load_config(sim_filename)
        except Exception:
            log.warning("Failed to load %s!", sim_filename)
    if not ad.cfg["subscription"]:
        abort_all_tests(ad.log, "No Valid SIMs found in device")
    for sub_id, sub_info in ad.cfg["subscription"].items():
        sub_info["operator"] = get_operator_name(log, ad, sub_id)
        iccid = sub_info["iccid"]
        if not iccid:
            ad.log.warn("Unable to find ICC-ID for SIM")
            continue
        if not sub_info["phone_num"]:
            if iccid not in sim_data or not sim_data[iccid].get("phone_num"):
                abort_all_tests(
                    ad.log,
                    "Failed to find valid phone number for ICCID %s in %s" %
                    (iccid, sim_filename))
            else:
                sub_info["phone_num"] = sim_data[iccid]["phone_num"]
        elif sim_data.get(iccid) and sim_data[iccid].get("phone_num"):
            if not check_phone_number_match(sub_info["phone_num"],
                                            sim_data[iccid]["phone_num"]):
                ad.log.error(
                    "ICCID %s phone number is %s in %s, does not match "
                    "the number %s retrieved from the phone", iccid,
                    sim_data[iccid]["phone_num"], sim_filename,
                    sub_info["phone_num"])
            sub_info["phone_num"] = sim_data[iccid]["phone_num"]
        if sub_info["sim_operator_name"] != sub_info["network_operator_name"]:
            setattr(ad, 'roaming', True)
    if getattr(ad, 'roaming', False):
        ad.log.info("Enable cell data roaming")
        toggle_cell_data_roaming(ad, True)

    ad.log.info("cfg = %s", ad.cfg)


def refresh_droid_config(log, ad):
    """ Update Android Device cfg records for each sub_id.

    Args:
        log: log object
        ad: android device object

    Returns:
        None
    """
    cfg = {"subscription": {}}
    droid = ad.droid
    sub_info_list = droid.subscriptionGetAllSubInfoList()
    for sub_info in sub_info_list:
        sub_id = sub_info["subscriptionId"]
        sim_slot = sub_info["simSlotIndex"]
        if sim_slot != INVALID_SIM_SLOT_INDEX:
            sim_serial = droid.telephonyGetSimSerialNumberForSubscription(
                sub_id)
            if not sim_serial:
                ad.log.error("Unable to find ICC-ID for SIM in slot %s",
                             sim_slot)
            sim_record = {}
            sim_record["iccid"] = sim_serial
            sim_record["sim_slot"] = sim_slot
            try:
                sim_record[
                    "phone_type"] = droid.telephonyGetPhoneTypeForSubscription(
                        sub_id)
            except:
                sim_record["phone_type"] = droid.telephonyGetPhoneType()
            sim_record[
                "sim_plmn"] = droid.telephonyGetSimOperatorForSubscription(
                    sub_id)
            sim_record[
                "sim_operator_name"] = droid.telephonyGetSimOperatorNameForSubscription(
                    sub_id)
            sim_record[
                "network_plmn"] = droid.telephonyGetNetworkOperatorForSubscription(
                    sub_id)
            sim_record[
                "network_operator_name"] = droid.telephonyGetNetworkOperatorNameForSubscription(
                    sub_id)
            sim_record[
                "network_type"] = droid.telephonyGetNetworkTypeForSubscription(
                    sub_id)
            sim_record[
                "sim_country"] = droid.telephonyGetSimCountryIsoForSubscription(
                    sub_id)
            sim_record["phone_num"] = phone_number_formatter(
                droid.telephonyGetLine1NumberForSubscription(sub_id))
            sim_record[
                "phone_tag"] = droid.telephonyGetLine1AlphaTagForSubscription(
                    sub_id)
            cfg['subscription'][sub_id] = sim_record
            ad.log.info("SubId %s SIM record: %s", sub_id, sim_record)
    setattr(ad, 'cfg', cfg)


def get_slot_index_from_subid(log, ad, sub_id):
    try:
        info = ad.droid.subscriptionGetSubInfoForSubscriber(sub_id)
        return info['simSlotIndex']
    except KeyError:
        return INVALID_SIM_SLOT_INDEX


def get_num_active_sims(log, ad):
    """ Get the number of active SIM cards by counting slots

    Args:
        ad: android_device object.

    Returns:
        result: The number of loaded (physical) SIM cards
    """
    # using a dictionary as a cheap way to prevent double counting
    # in the situation where multiple subscriptions are on the same SIM.
    # yes, this is a corner corner case.
    valid_sims = {}
    subInfo = ad.droid.subscriptionGetAllSubInfoList()
    for info in subInfo:
        ssidx = info['simSlotIndex']
        if ssidx == INVALID_SIM_SLOT_INDEX:
            continue
        valid_sims[ssidx] = True
    return len(valid_sims.keys())


def toggle_airplane_mode_by_adb(log, ad, new_state=None):
    """ Toggle the state of airplane mode.

    Args:
        log: log handler.
        ad: android_device object.
        new_state: Airplane mode state to set to.
            If None, opposite of the current state.
        strict_checking: Whether to turn on strict checking that checks all features.

    Returns:
        result: True if operation succeed. False if error happens.
    """
    cur_state = bool(int(ad.adb.shell("settings get global airplane_mode_on")))
    if new_state == cur_state:
        ad.log.info("Airplane mode already in %s", new_state)
        return True
    elif new_state is None:
        new_state = not cur_state

    ad.adb.shell("settings put global airplane_mode_on %s" % int(new_state))
    ad.adb.shell("am broadcast -a android.intent.action.AIRPLANE_MODE")
    return True


def toggle_airplane_mode(log, ad, new_state=None, strict_checking=True):
    """ Toggle the state of airplane mode.

    Args:
        log: log handler.
        ad: android_device object.
        new_state: Airplane mode state to set to.
            If None, opposite of the current state.
        strict_checking: Whether to turn on strict checking that checks all features.

    Returns:
        result: True if operation succeed. False if error happens.
    """
    if ad.skip_sl4a:
        return toggle_airplane_mode_by_adb(log, ad, new_state)
    else:
        return toggle_airplane_mode_msim(
            log, ad, new_state, strict_checking=strict_checking)


def get_telephony_signal_strength(ad):
    signal_strength = ad.droid.telephonyGetSignalStrength()
    #{'evdoEcio': -1, 'asuLevel': 28, 'lteSignalStrength': 14, 'gsmLevel': 0,
    # 'cdmaAsuLevel': 99, 'evdoDbm': -120, 'gsmDbm': -1, 'cdmaEcio': -160,
    # 'level': 2, 'lteLevel': 2, 'cdmaDbm': -120, 'dbm': -112, 'cdmaLevel': 0,
    # 'lteAsuLevel': 28, 'gsmAsuLevel': 99, 'gsmBitErrorRate': 0,
    # 'lteDbm': -112, 'gsmSignalStrength': 99}
    if not signal_strength: signal_strength = {}
    out = ad.adb.shell("dumpsys telephony.registry | grep -i signalstrength")
    signals = re.findall(r"(-*\d+)", out)
    for i, val in enumerate(
        ("gsmSignalStrength", "gsmBitErrorRate", "cdmaDbm", "cdmaEcio",
         "evdoDbm", "evdoEcio", "evdoSnr", "lteSignalStrength", "lteRsrp",
         "lteRsrq", "lteRssnr", "lteCqi", "lteRsrpBoost")):
        signal_strength[val] = signal_strength.get(val, int(signals[i]))
    ad.log.info("Telephony Signal strength = %s", signal_strength)
    return signal_strength


def is_expected_event(event_to_check, events_list):
    """ check whether event is present in the event list

    Args:
        event_to_check: event to be checked.
        events_list: list of events
    Returns:
        result: True if event present in the list. False if not.
    """
    for event in events_list:
        if event in event_to_check['name']:
            return True
    return False


def is_sim_ready(log, ad, sim_slot_id=None):
    """ check whether SIM is ready.

    Args:
        ad: android_device object.
        sim_slot_id: check the SIM status for sim_slot_id
            This is optional. If this is None, check default SIM.

    Returns:
        result: True if all SIMs are ready. False if not.
    """
    if sim_slot_id is None:
        status = ad.droid.telephonyGetSimState()
    else:
        status = ad.droid.telephonyGetSimStateForSlotId(sim_slot_id)
    if status != SIM_STATE_READY:
        log.info("Sim not ready")
        return False
    return True


def _is_expecting_event(event_recv_list):
    """ check for more event is expected in event list

    Args:
        event_recv_list: list of events
    Returns:
        result: True if more events are expected. False if not.
    """
    for state in event_recv_list:
        if state is False:
            return True
    return False


def _set_event_list(event_recv_list, sub_id_list, sub_id, value):
    """ set received event in expected event list

    Args:
        event_recv_list: list of received events
        sub_id_list: subscription ID list
        sub_id: subscription id of current event
        value: True or False
    Returns:
        None.
    """
    for i in range(len(sub_id_list)):
        if sub_id_list[i] == sub_id:
            event_recv_list[i] = value


def _wait_for_bluetooth_in_state(log, ad, state, max_wait):
    # FIXME: These event names should be defined in a common location
    _BLUETOOTH_STATE_ON_EVENT = 'BluetoothStateChangedOn'
    _BLUETOOTH_STATE_OFF_EVENT = 'BluetoothStateChangedOff'
    ad.ed.clear_events(_BLUETOOTH_STATE_ON_EVENT)
    ad.ed.clear_events(_BLUETOOTH_STATE_OFF_EVENT)

    ad.droid.bluetoothStartListeningForAdapterStateChange()
    try:
        bt_state = ad.droid.bluetoothCheckState()
        if bt_state == state:
            return True
        if max_wait <= 0:
            ad.log.error("Time out: bluetooth state still %s, expecting %s",
                         bt_state, state)
            return False

        event = {
            False: _BLUETOOTH_STATE_OFF_EVENT,
            True: _BLUETOOTH_STATE_ON_EVENT
        }[state]
        ad.ed.pop_event(event, max_wait)
        return True
    except Empty:
        ad.log.error("Time out: bluetooth state still in %s, expecting %s",
                     bt_state, state)
        return False
    finally:
        ad.droid.bluetoothStopListeningForAdapterStateChange()


# TODO: replace this with an event-based function
def _wait_for_wifi_in_state(log, ad, state, max_wait):
    return _wait_for_droid_in_state(log, ad, max_wait,
        lambda log, ad, state: \
                (True if ad.droid.wifiCheckState() == state else False),
                state)


def toggle_airplane_mode_msim(log, ad, new_state=None, strict_checking=True):
    """ Toggle the state of airplane mode.

    Args:
        log: log handler.
        ad: android_device object.
        new_state: Airplane mode state to set to.
            If None, opposite of the current state.
        strict_checking: Whether to turn on strict checking that checks all features.

    Returns:
        result: True if operation succeed. False if error happens.
    """

    ad.ed.clear_all_events()
    sub_id_list = []

    active_sub_info = ad.droid.subscriptionGetAllSubInfoList()
    for info in active_sub_info:
        sub_id_list.append(info['subscriptionId'])

    cur_state = ad.droid.connectivityCheckAirplaneMode()
    if cur_state == new_state:
        ad.log.info("Airplane mode already in %s", new_state)
        return True
    elif new_state is None:
        ad.log.info("APM Current State %s New state %s", cur_state, new_state)

    if new_state is None:
        new_state = not cur_state

    service_state_list = []
    if new_state:
        service_state_list.append(SERVICE_STATE_POWER_OFF)
        ad.log.info("Turn on airplane mode")

    else:
        # If either one of these 3 events show up, it should be OK.
        # Normal SIM, phone in service
        service_state_list.append(SERVICE_STATE_IN_SERVICE)
        # NO SIM, or Dead SIM, or no Roaming coverage.
        service_state_list.append(SERVICE_STATE_OUT_OF_SERVICE)
        service_state_list.append(SERVICE_STATE_EMERGENCY_ONLY)
        ad.log.info("Turn off airplane mode")

    for sub_id in sub_id_list:
        ad.droid.telephonyStartTrackingServiceStateChangeForSubscription(
            sub_id)

    timeout_time = time.time() + MAX_WAIT_TIME_AIRPLANEMODE_EVENT
    ad.droid.connectivityToggleAirplaneMode(new_state)

    event = None

    try:
        try:
            event = ad.ed.wait_for_event(
                EventServiceStateChanged,
                is_event_match_for_list,
                timeout=MAX_WAIT_TIME_AIRPLANEMODE_EVENT,
                field=ServiceStateContainer.SERVICE_STATE,
                value_list=service_state_list)
        except Empty:
            pass
        if event is None:
            ad.log.error("Did not get expected service state %s",
                         service_state_list)
            return False
        else:
            ad.log.info("Received event: %s", event)
    finally:
        for sub_id in sub_id_list:
            ad.droid.telephonyStopTrackingServiceStateChangeForSubscription(
                sub_id)

    # APM on (new_state=True) will turn off bluetooth but may not turn it on
    try:
        if new_state and not _wait_for_bluetooth_in_state(
                log, ad, False, timeout_time - time.time()):
            ad.log.error(
                "Failed waiting for bluetooth during airplane mode toggle")
            if strict_checking: return False
    except Exception as e:
        ad.log.error("Failed to check bluetooth state due to %s", e)
        if strict_checking:
            raise

    # APM on (new_state=True) will turn off wifi but may not turn it on
    if new_state and not _wait_for_wifi_in_state(log, ad, False,
                                                 timeout_time - time.time()):
        ad.log.error("Failed waiting for wifi during airplane mode toggle on")
        if strict_checking: return False

    if ad.droid.connectivityCheckAirplaneMode() != new_state:
        ad.log.error("Set airplane mode to %s failed", new_state)
        return False
    return True


def wait_and_answer_call(log,
                         ad,
                         incoming_number=None,
                         incall_ui_display=INCALL_UI_DISPLAY_FOREGROUND,
                         caller=None):
    """Wait for an incoming call on default voice subscription and
       accepts the call.

    Args:
        ad: android device object.
        incoming_number: Expected incoming number.
            Optional. Default is None
        incall_ui_display: after answer the call, bring in-call UI to foreground or
            background. Optional, default value is INCALL_UI_DISPLAY_FOREGROUND.
            if = INCALL_UI_DISPLAY_FOREGROUND, bring in-call UI to foreground.
            if = INCALL_UI_DISPLAY_BACKGROUND, bring in-call UI to background.
            else, do nothing.

    Returns:
        True: if incoming call is received and answered successfully.
        False: for errors
        """
    return wait_and_answer_call_for_subscription(
        log,
        ad,
        get_incoming_voice_sub_id(ad),
        incoming_number,
        incall_ui_display=incall_ui_display,
        caller=caller)


def wait_for_ringing_event(log, ad, wait_time):
    log.warning("***DEPRECATED*** wait_for_ringing_event()")
    return _wait_for_ringing_event(log, ad, wait_time)


def _wait_for_ringing_event(log, ad, wait_time):
    """Wait for ringing event.

    Args:
        log: log object.
        ad: android device object.
        wait_time: max time to wait for ringing event.

    Returns:
        event_ringing if received ringing event.
        otherwise return None.
    """
    event_ringing = None

    try:
        event_ringing = ad.ed.wait_for_event(
            EventCallStateChanged,
            is_event_match,
            timeout=wait_time,
            field=CallStateContainer.CALL_STATE,
            value=TELEPHONY_STATE_RINGING)
        ad.log.info("Receive ringing event")
    except Empty:
        ad.log.info("No Ringing Event")
    finally:
        return event_ringing


def wait_for_ringing_call(log, ad, incoming_number=None):
    """Wait for an incoming call on default voice subscription and
       accepts the call.

    Args:
        log: log object.
        ad: android device object.
        incoming_number: Expected incoming number.
            Optional. Default is None

    Returns:
        True: if incoming call is received and answered successfully.
        False: for errors
        """
    return wait_for_ringing_call_for_subscription(
        log, ad, get_incoming_voice_sub_id(ad), incoming_number)


def wait_for_ringing_call_for_subscription(
        log,
        ad,
        sub_id,
        incoming_number=None,
        caller=None,
        event_tracking_started=False,
        timeout=MAX_WAIT_TIME_CALLEE_RINGING):
    """Wait for an incoming call on specified subscription.

    Args:
        log: log object.
        ad: android device object.
        sub_id: subscription ID
        incoming_number: Expected incoming number. Default is None
        event_tracking_started: True if event tracking already state outside
        timeout: time to wait for ring

    Returns:
        True: if incoming call is received and answered successfully.
        False: for errors
    """
    if not event_tracking_started:
        ad.ed.clear_all_events()
        ad.droid.telephonyStartTrackingCallStateForSubscription(sub_id)
    event_ringing = _wait_for_ringing_event(log, ad, timeout)
    if not event_tracking_started:
        ad.droid.telephonyStopTrackingCallStateChangeForSubscription(sub_id)
    if caller and not caller.droid.telecomIsInCall():
        caller.log.error("Caller not in call state")
        raise _CallSequenceException("Caller not in call state")
    if not event_ringing and not (
            ad.droid.telephonyGetCallStateForSubscription(sub_id) ==
            TELEPHONY_STATE_RINGING or ad.droid.telecomIsRinging()):
        ad.log.info("Not in call ringing state")
        return False
    if not incoming_number:
        return True

    result = check_phone_number_match(
        event_ringing['data'][CallStateContainer.INCOMING_NUMBER],
        incoming_number)
    if not result:
        ad.log.error(
            "Incoming Number not match. Expected number:%s, actual number:%s",
            incoming_number,
            event_ringing['data'][CallStateContainer.INCOMING_NUMBER])
        return False
    return True


def wait_for_call_offhook_event(
        log,
        ad,
        sub_id,
        event_tracking_started=False,
        timeout=MAX_WAIT_TIME_ACCEPT_CALL_TO_OFFHOOK_EVENT):
    """Wait for an incoming call on specified subscription.

    Args:
        log: log object.
        ad: android device object.
        event_tracking_started: True if event tracking already state outside
        timeout: time to wait for event

    Returns:
        True: if call offhook event is received.
        False: if call offhook event is not received.
    """
    if not event_tracking_started:
        ad.ed.clear_all_events()
        ad.droid.telephonyStartTrackingCallStateForSubscription(sub_id)
    try:
        ad.ed.wait_for_event(
            EventCallStateChanged,
            is_event_match,
            timeout=timeout,
            field=CallStateContainer.CALL_STATE,
            value=TELEPHONY_STATE_OFFHOOK)
    except Empty:
        ad.log.info("No event for call state change to OFFHOOK")
        return False
    finally:
        if not event_tracking_started:
            ad.droid.telephonyStopTrackingCallStateChangeForSubscription(
                sub_id)
    return True


def wait_and_answer_call_for_subscription(
        log,
        ad,
        sub_id,
        incoming_number=None,
        incall_ui_display=INCALL_UI_DISPLAY_FOREGROUND,
        timeout=MAX_WAIT_TIME_CALLEE_RINGING,
        caller=None):
    """Wait for an incoming call on specified subscription and
       accepts the call.

    Args:
        log: log object.
        ad: android device object.
        sub_id: subscription ID
        incoming_number: Expected incoming number.
            Optional. Default is None
        incall_ui_display: after answer the call, bring in-call UI to foreground or
            background. Optional, default value is INCALL_UI_DISPLAY_FOREGROUND.
            if = INCALL_UI_DISPLAY_FOREGROUND, bring in-call UI to foreground.
            if = INCALL_UI_DISPLAY_BACKGROUND, bring in-call UI to background.
            else, do nothing.

    Returns:
        True: if incoming call is received and answered successfully.
        False: for errors
    """
    ad.ed.clear_all_events()
    ad.droid.telephonyStartTrackingCallStateForSubscription(sub_id)
    try:
        if not _wait_for_droid_in_state(
                log,
                ad,
                timeout,
                wait_for_ringing_call_for_subscription,
                sub_id,
                incoming_number=None,
                caller=caller,
                event_tracking_started=True,
                timeout=WAIT_TIME_BETWEEN_STATE_CHECK):
            ad.log.info("Could not answer a call: phone never rang.")
            return False
        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)
        ad.log.info("Accept the ring call")
        ad.droid.telecomAcceptRingingCall()

        if ad.droid.telecomIsInCall() or wait_for_call_offhook_event(
                log, ad, sub_id, event_tracking_started=True,
                timeout=timeout) or ad.droid.telecomIsInCall():
            ad.log.info("Call answered successfully.")
            return True
        else:
            ad.log.error("Could not answer the call.")
            return False
    except Exception as e:
        log.error(e)
        return False
    finally:
        ad.droid.telephonyStopTrackingCallStateChangeForSubscription(sub_id)
        if incall_ui_display == INCALL_UI_DISPLAY_FOREGROUND:
            ad.droid.telecomShowInCallScreen()
        elif incall_ui_display == INCALL_UI_DISPLAY_BACKGROUND:
            ad.droid.showHomeScreen()


def wait_and_reject_call(log,
                         ad,
                         incoming_number=None,
                         delay_reject=WAIT_TIME_REJECT_CALL,
                         reject=True):
    """Wait for an incoming call on default voice subscription and
       reject the call.

    Args:
        log: log object.
        ad: android device object.
        incoming_number: Expected incoming number.
            Optional. Default is None
        delay_reject: time to wait before rejecting the call
            Optional. Default is WAIT_TIME_REJECT_CALL

    Returns:
        True: if incoming call is received and reject successfully.
        False: for errors
    """
    return wait_and_reject_call_for_subscription(
        log, ad,
        get_incoming_voice_sub_id(ad), incoming_number, delay_reject, reject)


def wait_and_reject_call_for_subscription(log,
                                          ad,
                                          sub_id,
                                          incoming_number=None,
                                          delay_reject=WAIT_TIME_REJECT_CALL,
                                          reject=True):
    """Wait for an incoming call on specific subscription and
       reject the call.

    Args:
        log: log object.
        ad: android device object.
        sub_id: subscription ID
        incoming_number: Expected incoming number.
            Optional. Default is None
        delay_reject: time to wait before rejecting the call
            Optional. Default is WAIT_TIME_REJECT_CALL

    Returns:
        True: if incoming call is received and reject successfully.
        False: for errors
    """

    if not wait_for_ringing_call_for_subscription(log, ad, sub_id,
                                                  incoming_number):
        ad.log.error("Could not reject a call: phone never rang.")
        return False

    ad.ed.clear_all_events()
    ad.droid.telephonyStartTrackingCallStateForSubscription(sub_id)
    if reject is True:
        # Delay between ringing and reject.
        time.sleep(delay_reject)
        is_find = False
        # Loop the call list and find the matched one to disconnect.
        for call in ad.droid.telecomCallGetCallIds():
            if check_phone_number_match(
                    get_number_from_tel_uri(get_call_uri(ad, call)),
                    incoming_number):
                ad.droid.telecomCallDisconnect(call)
                ad.log.info("Callee reject the call")
                is_find = True
        if is_find is False:
            ad.log.error("Callee did not find matching call to reject.")
            return False
    else:
        # don't reject on callee. Just ignore the incoming call.
        ad.log.info("Callee received incoming call. Ignore it.")
    try:
        ad.ed.wait_for_event(
            EventCallStateChanged,
            is_event_match_for_list,
            timeout=MAX_WAIT_TIME_CALL_IDLE_EVENT,
            field=CallStateContainer.CALL_STATE,
            value_list=[TELEPHONY_STATE_IDLE, TELEPHONY_STATE_OFFHOOK])
    except Empty:
        ad.log.error("No onCallStateChangedIdle event received.")
        return False
    finally:
        ad.droid.telephonyStopTrackingCallStateChangeForSubscription(sub_id)
    return True


def hangup_call(log, ad):
    """Hang up ongoing active call.

    Args:
        log: log object.
        ad: android device object.

    Returns:
        True: if all calls are cleared
        False: for errors
    """
    # short circuit in case no calls are active
    if not ad.droid.telecomIsInCall():
        return True
    ad.ed.clear_all_events()
    ad.droid.telephonyStartTrackingCallState()
    log.info("Hangup call.")
    ad.droid.telecomEndCall()

    try:
        ad.ed.wait_for_event(
            EventCallStateChanged,
            is_event_match,
            timeout=MAX_WAIT_TIME_CALL_IDLE_EVENT,
            field=CallStateContainer.CALL_STATE,
            value=TELEPHONY_STATE_IDLE)
    except Empty:
        if ad.droid.telecomIsInCall():
            log.error("Hangup call failed.")
            return False
    finally:
        ad.droid.telephonyStopTrackingCallStateChange()
    return True


def disconnect_call_by_id(log, ad, call_id):
    """Disconnect call by call id.
    """
    ad.droid.telecomCallDisconnect(call_id)
    return True


def _phone_number_remove_prefix(number):
    """Remove the country code and other prefix from the input phone number.
    Currently only handle phone number with the following formats:
        (US phone number format)
        +1abcxxxyyyy
        1abcxxxyyyy
        abcxxxyyyy
        abc xxx yyyy
        abc.xxx.yyyy
        abc-xxx-yyyy
        (EEUK phone number format)
        +44abcxxxyyyy
        0abcxxxyyyy

    Args:
        number: input phone number

    Returns:
        Phone number without country code or prefix
    """
    if number is None:
        return None, None
    for country_code in COUNTRY_CODE_LIST:
        if number.startswith(country_code):
            return number[len(country_code):], country_code
    if number[0] == "1" or number[0] == "0":
        return number[1:], None
    return number, None


def check_phone_number_match(number1, number2):
    """Check whether two input phone numbers match or not.

    Compare the two input phone numbers.
    If they match, return True; otherwise, return False.
    Currently only handle phone number with the following formats:
        (US phone number format)
        +1abcxxxyyyy
        1abcxxxyyyy
        abcxxxyyyy
        abc xxx yyyy
        abc.xxx.yyyy
        abc-xxx-yyyy
        (EEUK phone number format)
        +44abcxxxyyyy
        0abcxxxyyyy

        There are some scenarios we can not verify, one example is:
            number1 = +15555555555, number2 = 5555555555
            (number2 have no country code)

    Args:
        number1: 1st phone number to be compared.
        number2: 2nd phone number to be compared.

    Returns:
        True if two phone numbers match. Otherwise False.
    """
    number1 = phone_number_formatter(number1)
    number2 = phone_number_formatter(number2)
    # Handle extra country code attachment when matching phone number
    if number1.replace("+", "") in number2 or number2.replace("+",
                                                              "") in number1:
        return True
    else:
        logging.info("phone number1 %s and number2 %s does not match" %
                     (number1, number2))
        return False


def initiate_call(log,
                  ad,
                  callee_number,
                  emergency=False,
                  timeout=MAX_WAIT_TIME_CALL_INITIATION,
                  checking_interval=5):
    """Make phone call from caller to callee.

    Args:
        ad_caller: Caller android device object.
        callee_number: Callee phone number.
        emergency : specify the call is emergency.
            Optional. Default value is False.

    Returns:
        result: if phone call is placed successfully.
    """
    ad.ed.clear_all_events()
    sub_id = get_outgoing_voice_sub_id(ad)
    ad.droid.telephonyStartTrackingCallStateForSubscription(sub_id)

    try:
        # Make a Call
        ad.log.info("Make a phone call to %s", callee_number)
        if emergency:
            ad.droid.telecomCallEmergencyNumber(callee_number)
        else:
            ad.droid.telecomCallNumber(callee_number)

        # Verify OFFHOOK event
        checking_retries = int(timeout / checking_interval)
        for i in range(checking_retries):
            if (ad.droid.telecomIsInCall() and
                    ad.droid.telephonyGetCallState() == TELEPHONY_STATE_OFFHOOK
                    and
                    ad.droid.telecomGetCallState() == TELEPHONY_STATE_OFFHOOK
                ) or wait_for_call_offhook_event(log, ad, sub_id, True,
                                                 checking_interval):
                return True
        ad.log.error(
            "Make call to %s fail. telecomIsInCall:%s, Telecom State:%s,"
            " Telephony State:%s", callee_number,
            ad.droid.telecomIsInCall(),
            ad.droid.telephonyGetCallState(), ad.droid.telecomGetCallState())
        return False
    finally:
        ad.droid.telephonyStopTrackingCallStateChangeForSubscription(sub_id)


def call_reject(log, ad_caller, ad_callee, reject=True):
    """Caller call Callee, then reject on callee.


    """
    subid_caller = ad_caller.droid.subscriptionGetDefaultVoiceSubId()
    subid_callee = ad_callee.incoming_voice_sub_id
    ad_caller.log.info("Sub-ID Caller %s, Sub-ID Callee %s", subid_caller,
                       subid_callee)
    return call_reject_for_subscription(log, ad_caller, ad_callee,
                                        subid_caller, subid_callee, reject)


def call_reject_for_subscription(log,
                                 ad_caller,
                                 ad_callee,
                                 subid_caller,
                                 subid_callee,
                                 reject=True):
    """
    """

    caller_number = ad_caller.cfg['subscription'][subid_caller]['phone_num']
    callee_number = ad_callee.cfg['subscription'][subid_callee]['phone_num']

    ad_caller.log.info("Call from %s to %s", caller_number, callee_number)
    try:
        if not initiate_call(log, ad_caller, callee_number):
            raise _CallSequenceException("Initiate call failed.")

        if not wait_and_reject_call_for_subscription(
                log, ad_callee, subid_callee, caller_number,
                WAIT_TIME_REJECT_CALL, reject):
            raise _CallSequenceException("Reject call fail.")
        # Check if incoming call is cleared on callee or not.
        if ad_callee.droid.telephonyGetCallStateForSubscription(
                subid_callee) == TELEPHONY_STATE_RINGING:
            raise _CallSequenceException("Incoming call is not cleared.")
        # Hangup on caller
        hangup_call(log, ad_caller)
    except _CallSequenceException as e:
        log.error(e)
        return False
    return True


def call_reject_leave_message(log,
                              ad_caller,
                              ad_callee,
                              verify_caller_func=None,
                              wait_time_in_call=WAIT_TIME_LEAVE_VOICE_MAIL):
    """On default voice subscription, Call from caller to callee,
    reject on callee, caller leave a voice mail.

    1. Caller call Callee.
    2. Callee reject incoming call.
    3. Caller leave a voice mail.
    4. Verify callee received the voice mail notification.

    Args:
        ad_caller: caller android device object.
        ad_callee: callee android device object.
        verify_caller_func: function to verify caller is in correct state while in-call.
            This is optional, default is None.
        wait_time_in_call: time to wait when leaving a voice mail.
            This is optional, default is WAIT_TIME_LEAVE_VOICE_MAIL

    Returns:
        True: if voice message is received on callee successfully.
        False: for errors
    """
    subid_caller = get_outgoing_voice_sub_id(ad_caller)
    subid_callee = get_incoming_voice_sub_id(ad_callee)
    return call_reject_leave_message_for_subscription(
        log, ad_caller, ad_callee, subid_caller, subid_callee,
        verify_caller_func, wait_time_in_call)


def call_reject_leave_message_for_subscription(
        log,
        ad_caller,
        ad_callee,
        subid_caller,
        subid_callee,
        verify_caller_func=None,
        wait_time_in_call=WAIT_TIME_LEAVE_VOICE_MAIL):
    """On specific voice subscription, Call from caller to callee,
    reject on callee, caller leave a voice mail.

    1. Caller call Callee.
    2. Callee reject incoming call.
    3. Caller leave a voice mail.
    4. Verify callee received the voice mail notification.

    Args:
        ad_caller: caller android device object.
        ad_callee: callee android device object.
        subid_caller: caller's subscription id.
        subid_callee: callee's subscription id.
        verify_caller_func: function to verify caller is in correct state while in-call.
            This is optional, default is None.
        wait_time_in_call: time to wait when leaving a voice mail.
            This is optional, default is WAIT_TIME_LEAVE_VOICE_MAIL

    Returns:
        True: if voice message is received on callee successfully.
        False: for errors
    """

    # Currently this test utility only works for TMO and ATT and SPT.
    # It does not work for VZW (see b/21559800)
    # "with VVM TelephonyManager APIs won't work for vm"

    caller_number = ad_caller.cfg['subscription'][subid_caller]['phone_num']
    callee_number = ad_callee.cfg['subscription'][subid_callee]['phone_num']

    ad_caller.log.info("Call from %s to %s", caller_number, callee_number)

    try:
        voice_mail_count_before = ad_callee.droid.telephonyGetVoiceMailCountForSubscription(
            subid_callee)
        ad_callee.log.info("voice mail count is %s", voice_mail_count_before)
        # -1 means there are unread voice mail, but the count is unknown
        # 0 means either this API not working (VZW) or no unread voice mail.
        if voice_mail_count_before != 0:
            log.warning("--Pending new Voice Mail, please clear on phone.--")

        if not initiate_call(log, ad_caller, callee_number):
            raise _CallSequenceException("Initiate call failed.")

        if not wait_and_reject_call_for_subscription(
                log, ad_callee, subid_callee, incoming_number=caller_number):
            raise _CallSequenceException("Reject call fail.")

        ad_callee.droid.telephonyStartTrackingVoiceMailStateChangeForSubscription(
            subid_callee)

        # ensure that all internal states are updated in telecom
        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
        ad_callee.ed.clear_all_events()

        if verify_caller_func and not verify_caller_func(log, ad_caller):
            raise _CallSequenceException("Caller not in correct state!")

        # TODO: b/26293512 Need to play some sound to leave message.
        # Otherwise carrier voice mail server may drop this voice mail.
        time.sleep(wait_time_in_call)

        if not verify_caller_func:
            caller_state_result = ad_caller.droid.telecomIsInCall()
        else:
            caller_state_result = verify_caller_func(log, ad_caller)
        if not caller_state_result:
            raise _CallSequenceException(
                "Caller %s not in correct state after %s seconds" %
                (ad_caller.serial, wait_time_in_call))

        if not hangup_call(log, ad_caller):
            raise _CallSequenceException("Error in Hanging-Up Call")

        log.info("Wait for voice mail indicator on callee.")
        try:
            event = ad_callee.ed.wait_for_event(
                EventMessageWaitingIndicatorChanged,
                _is_on_message_waiting_event_true)
            log.info(event)
        except Empty:
            ad_callee.log.warning("No expected event %s",
                                  EventMessageWaitingIndicatorChanged)
            raise _CallSequenceException("No expected event {}.".format(
                EventMessageWaitingIndicatorChanged))
        voice_mail_count_after = ad_callee.droid.telephonyGetVoiceMailCountForSubscription(
            subid_callee)
        ad_callee.log.info(
            "telephonyGetVoiceMailCount output - before: %s, after: %s",
            voice_mail_count_before, voice_mail_count_after)

        # voice_mail_count_after should:
        # either equals to (voice_mail_count_before + 1) [For ATT and SPT]
        # or equals to -1 [For TMO]
        # -1 means there are unread voice mail, but the count is unknown
        if not check_voice_mail_count(log, ad_callee, voice_mail_count_before,
                                      voice_mail_count_after):
            log.error("before and after voice mail count is not incorrect.")
            return False

    except _CallSequenceException as e:
        log.error(e)
        return False
    finally:
        ad_callee.droid.telephonyStopTrackingVoiceMailStateChangeForSubscription(
            subid_callee)
    return True


def call_voicemail_erase_all_pending_voicemail(log, ad):
    """Script for phone to erase all pending voice mail.
    This script only works for TMO and ATT and SPT currently.
    This script only works if phone have already set up voice mail options,
    and phone should disable password protection for voice mail.

    1. If phone don't have pending voice message, return True.
    2. Dial voice mail number.
        For TMO, the number is '123'
        For ATT, the number is phone's number
        For SPT, the number is phone's number
    3. Wait for voice mail connection setup.
    4. Wait for voice mail play pending voice message.
    5. Send DTMF to delete one message.
        The digit is '7'.
    6. Repeat steps 4 and 5 until voice mail server drop this call.
        (No pending message)
    6. Check telephonyGetVoiceMailCount result. it should be 0.

    Args:
        log: log object
        ad: android device object
    Returns:
        False if error happens. True is succeed.
    """
    log.info("Erase all pending voice mail.")
    count = ad.droid.telephonyGetVoiceMailCount()
    if count == 0:
        ad.log.info("No Pending voice mail.")
        return True
    if count == -1:
        ad.log.info("There is pending voice mail, but the count is unknown")
        count = MAX_SAVED_VOICE_MAIL
    else:
        ad.log.info("There are %s voicemails", count)

    voice_mail_number = get_voice_mail_number(log, ad)
    delete_digit = get_voice_mail_delete_digit(get_operator_name(log, ad))
    if not initiate_call(log, ad, voice_mail_number):
        log.error("Initiate call to voice mail failed.")
        return False
    time.sleep(WAIT_TIME_VOICE_MAIL_SERVER_RESPONSE)
    callId = ad.droid.telecomCallGetCallIds()[0]
    time.sleep(WAIT_TIME_VOICE_MAIL_SERVER_RESPONSE)
    while (is_phone_in_call(log, ad) and (count > 0)):
        ad.log.info("Press %s to delete voice mail.", delete_digit)
        ad.droid.telecomCallPlayDtmfTone(callId, delete_digit)
        ad.droid.telecomCallStopDtmfTone(callId)
        time.sleep(WAIT_TIME_VOICE_MAIL_SERVER_RESPONSE)
        count -= 1
    if is_phone_in_call(log, ad):
        hangup_call(log, ad)

    # wait for telephonyGetVoiceMailCount to update correct result
    remaining_time = MAX_WAIT_TIME_VOICE_MAIL_COUNT
    while ((remaining_time > 0) and
           (ad.droid.telephonyGetVoiceMailCount() != 0)):
        time.sleep(1)
        remaining_time -= 1
    current_voice_mail_count = ad.droid.telephonyGetVoiceMailCount()
    ad.log.info("telephonyGetVoiceMailCount: %s", current_voice_mail_count)
    return (current_voice_mail_count == 0)


def _is_on_message_waiting_event_true(event):
    """Private function to return if the received EventMessageWaitingIndicatorChanged
    event MessageWaitingIndicatorContainer.IS_MESSAGE_WAITING field is True.
    """
    return event['data'][MessageWaitingIndicatorContainer.IS_MESSAGE_WAITING]


def call_setup_teardown(log,
                        ad_caller,
                        ad_callee,
                        ad_hangup=None,
                        verify_caller_func=None,
                        verify_callee_func=None,
                        wait_time_in_call=WAIT_TIME_IN_CALL,
                        incall_ui_display=INCALL_UI_DISPLAY_FOREGROUND):
    """ Call process, including make a phone call from caller,
    accept from callee, and hang up. The call is on default voice subscription

    In call process, call from <droid_caller> to <droid_callee>,
    accept the call, (optional)then hang up from <droid_hangup>.

    Args:
        ad_caller: Caller Android Device Object.
        ad_callee: Callee Android Device Object.
        ad_hangup: Android Device Object end the phone call.
            Optional. Default value is None, and phone call will continue.
        verify_call_mode_caller: func_ptr to verify caller in correct mode
            Optional. Default is None
        verify_call_mode_caller: func_ptr to verify caller in correct mode
            Optional. Default is None
        incall_ui_display: after answer the call, bring in-call UI to foreground or
            background. Optional, default value is INCALL_UI_DISPLAY_FOREGROUND.
            if = INCALL_UI_DISPLAY_FOREGROUND, bring in-call UI to foreground.
            if = INCALL_UI_DISPLAY_BACKGROUND, bring in-call UI to background.
            else, do nothing.

    Returns:
        True if call process without any error.
        False if error happened.

    """
    subid_caller = get_outgoing_voice_sub_id(ad_caller)
    subid_callee = get_incoming_voice_sub_id(ad_callee)
    return call_setup_teardown_for_subscription(
        log, ad_caller, ad_callee, subid_caller, subid_callee, ad_hangup,
        verify_caller_func, verify_callee_func, wait_time_in_call,
        incall_ui_display)


def call_setup_teardown_for_subscription(
        log,
        ad_caller,
        ad_callee,
        subid_caller,
        subid_callee,
        ad_hangup=None,
        verify_caller_func=None,
        verify_callee_func=None,
        wait_time_in_call=WAIT_TIME_IN_CALL,
        incall_ui_display=INCALL_UI_DISPLAY_FOREGROUND):
    """ Call process, including make a phone call from caller,
    accept from callee, and hang up. The call is on specified subscription

    In call process, call from <droid_caller> to <droid_callee>,
    accept the call, (optional)then hang up from <droid_hangup>.

    Args:
        ad_caller: Caller Android Device Object.
        ad_callee: Callee Android Device Object.
        subid_caller: Caller subscription ID
        subid_callee: Callee subscription ID
        ad_hangup: Android Device Object end the phone call.
            Optional. Default value is None, and phone call will continue.
        verify_call_mode_caller: func_ptr to verify caller in correct mode
            Optional. Default is None
        verify_call_mode_caller: func_ptr to verify caller in correct mode
            Optional. Default is None
        incall_ui_display: after answer the call, bring in-call UI to foreground or
            background. Optional, default value is INCALL_UI_DISPLAY_FOREGROUND.
            if = INCALL_UI_DISPLAY_FOREGROUND, bring in-call UI to foreground.
            if = INCALL_UI_DISPLAY_BACKGROUND, bring in-call UI to background.
            else, do nothing.

    Returns:
        True if call process without any error.
        False if error happened.

    """
    CHECK_INTERVAL = 3
    result = True

    caller_number = ad_caller.cfg['subscription'][subid_caller]['phone_num']
    callee_number = ad_callee.cfg['subscription'][subid_callee]['phone_num']

    ad_caller.log.info("Call from %s to %s", caller_number, callee_number)

    try:
        if not initiate_call(log, ad_caller, callee_number):
            raise _CallSequenceException("Initiate call failed.")
        else:
            ad_caller.log.info("Caller initate call successfully")
        if not wait_and_answer_call_for_subscription(
                log,
                ad_callee,
                subid_callee,
                incoming_number=caller_number,
                caller=ad_caller,
                incall_ui_display=incall_ui_display):
            raise _CallSequenceException("Answer call fail.")
        else:
            ad_callee.log.info("Callee answered the call successfully")
        # ensure that all internal states are updated in telecom
        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)

        if verify_caller_func and not verify_caller_func(log, ad_caller):
            raise _CallSequenceException("Caller not in correct state!")
        if verify_callee_func and not verify_callee_func(log, ad_callee):
            raise _CallSequenceException("Callee not in correct state!")

        elapsed_time = 0
        while (elapsed_time < wait_time_in_call):
            CHECK_INTERVAL = min(CHECK_INTERVAL,
                                 wait_time_in_call - elapsed_time)
            time.sleep(CHECK_INTERVAL)
            elapsed_time += CHECK_INTERVAL
            time_message = "at <%s>/<%s> second." % (elapsed_time,
                                                     wait_time_in_call)
            if not verify_caller_func:
                if not ad_caller.droid.telecomIsInCall():
                    ad_caller.log.error("Caller not in call state %s",
                                        time_message)
                    raise _CallSequenceException(
                        "Caller not in correct state %s.".format(time_message))
            else:
                if not verify_caller_func(log, ad_caller):
                    ad_caller.log.error("Caller %s return False",
                                        verify_caller_func.__name__)
                    raise _CallSequenceException(
                        "Caller not in correct state %s.".format(time_message))
            if not verify_callee_func:
                if not ad_callee.droid.telecomIsInCall():
                    ad_callee.log.error("Callee not in call state %s",
                                        time_message)
                    raise _CallSequenceException(
                        "Callee not in correct state %s.".format(time_message))
            else:
                if not verify_callee_func(log, ad_callee):
                    ad_callee.log.error("Callee %s return False",
                                        verify_callee_func.__name__)
                    raise _CallSequenceException(
                        "Callee not in correct state %s.".format(time_message))

        if not ad_hangup:
            return True
        if not hangup_call(log, ad_hangup):
            ad_hangup.log.info("Failed to hang up the call")
            raise _CallSequenceException("Error in Hanging-Up Call")
        return True

    except _CallSequenceException as e:
        log.error(e)
        result = False
        return False
    finally:
        if ad_hangup or not result:
            for ad in [ad_caller, ad_callee]:
                try:
                    if ad.droid.telecomIsInCall():
                        ad.droid.telecomEndCall()
                except Exception as e:
                    log.error(str(e))


def phone_number_formatter(input_string, formatter=None):
    """Get expected format of input phone number string.

    Args:
        input_string: (string) input phone number.
            The input could be 10/11/12 digital, with or without " "/"-"/"."
        formatter: (int) expected format, this could be 7/10/11/12
            if formatter is 7: output string would be 7 digital number.
            if formatter is 10: output string would be 10 digital (standard) number.
            if formatter is 11: output string would be "1" + 10 digital number.
            if formatter is 12: output string would be "+1" + 10 digital number.

    Returns:
        If no error happen, return phone number in expected format.
        Else, return None.
    """
    # make sure input_string is 10 digital
    # Remove white spaces, dashes, dots
    input_string = input_string.replace(" ", "").replace("-", "").replace(
        ".", "").lstrip("0")
    if not formatter:
        return input_string
    # Remove "1"  or "+1"from front
    if (len(input_string) == PHONE_NUMBER_STRING_FORMAT_11_DIGIT and
            input_string[0] == "1"):
        input_string = input_string[1:]
    elif (len(input_string) == PHONE_NUMBER_STRING_FORMAT_12_DIGIT and
          input_string[0:2] == "+1"):
        input_string = input_string[2:]
    elif (len(input_string) == PHONE_NUMBER_STRING_FORMAT_7_DIGIT and
          formatter == PHONE_NUMBER_STRING_FORMAT_7_DIGIT):
        return input_string
    elif len(input_string) != PHONE_NUMBER_STRING_FORMAT_10_DIGIT:
        return None
    # change input_string according to format
    if formatter == PHONE_NUMBER_STRING_FORMAT_12_DIGIT:
        input_string = "+1" + input_string
    elif formatter == PHONE_NUMBER_STRING_FORMAT_11_DIGIT:
        input_string = "1" + input_string
    elif formatter == PHONE_NUMBER_STRING_FORMAT_10_DIGIT:
        input_string = input_string
    elif formatter == PHONE_NUMBER_STRING_FORMAT_7_DIGIT:
        input_string = input_string[3:]
    else:
        return None
    return input_string


def get_internet_connection_type(log, ad):
    """Get current active connection type name.

    Args:
        log: Log object.
        ad: Android Device Object.
    Returns:
        current active connection type name.
    """
    if not ad.droid.connectivityNetworkIsConnected():
        return 'none'
    return connection_type_from_type_string(
        ad.droid.connectivityNetworkGetActiveConnectionTypeName())


def verify_http_connection(log,
                           ad,
                           url="http://www.google.com/",
                           retry=5,
                           retry_interval=15):
    """Make ping request and return status.

    Args:
        log: log object
        ad: Android Device Object.
        url: Optional. The ping request will be made to this URL.
            Default Value is "http://www.google.com/".

    """
    for i in range(0, retry + 1):

        try:
            http_response = ad.droid.httpPing(url)
        except:
            http_response = None

        # If httpPing failed, it may return {} (if phone just turn off APM) or
        # None (regular fail)
        # So here use "if http_response" to see if it pass or fail
        if http_response:
            ad.log.info("Verify Internet succeeded")
            return True
        else:
            if i < retry:
                time.sleep(retry_interval)
    ad.log.info("Verify Internet retry failed after %s second",
                i * retry_interval)
    return False


def _generate_file_name_and_out_path(url, out_path):
    file_name = url.split("/")[-1]
    if not out_path:
        out_path = "/sdcard/Download/%s" % file_name
    elif out_path.endswith("/"):
        out_path = os.path.join(out_path, file_name)
    else:
        file_name = out_path.split("/")[-1]
    return file_name, out_path


def _check_file_existance(ad, file_name, out_path, expected_file_size=None):
    """Check file existance by file_name and out_path. If expected_file_size
       is provided, then also check if the file meet the file size requirement.
    """
    if out_path.endswith("/"):
        out_path = os.path.join(out_path, file_name)
    out = ad.adb.shell("ls -l %s" % out_path, ignore_status=True)
    # Handle some old version adb returns error message "No such" into std_out
    if out and "No such" not in out and file_name in out:
        if expected_file_size:
            file_size = int(out.split(" ")[4])
            if file_size >= expected_file_size:
                ad.log.info("File %s of size %s is in %s", file_name,
                            file_size, out_path)
                return True
            else:
                ad.log.info(
                    "File size for %s in %s is %s does not meet expected %s",
                    file_name, out_path, file_size, expected_file_size)
                return False
        else:
            ad.log.info("File %s is in %s", file_name, out_path)
            return True
    else:
        ad.log.info("File %s does not exist in %s.", file_name, out_path)
        return False


def active_file_download_task(log, ad, file_name="5MB"):
    curl_capable = True
    try:
        out = ad.adb.shell("curl --version")
        if "not found" in out:
            curl_capable = False
    except AdbError:
        curl_capable = False
    # files available for download on the same website:
    # 1GB.zip, 512MB.zip, 200MB.zip, 50MB.zip, 20MB.zip, 10MB.zip, 5MB.zip
    # download file by adb command, as phone call will use sl4a
    url = "http://download.thinkbroadband.com/" + file_name + ".zip"
    file_map_dict = {
        '5MB': 5000000,
        '10MB': 10000000,
        '20MB': 20000000,
        '50MB': 50000000,
        '200MB': 200000000,
        '512MB': 512000000,
        '1GB': 1000000000
    }
    file_size = file_map_dict.get(file_name)
    if not file_size:
        log.warning("file_name %s for download is not available", file_name)
        return False
    timeout = min(max(file_size / 100000, 60), 3600)
    output_path = "/sdcard/Download/" + file_name + ".zip"
    if not curl_capable:
        return (http_file_download_by_chrome, (ad, url, file_size, True,
                                               timeout))
    else:
        return (http_file_download_by_curl, (ad, url, output_path, file_size,
                                             True, timeout))


def active_file_download_test(log, ad, file_name="5MB"):
    task = active_file_download_task(log, ad, file_name)
    return task[0](*task[1])


def verify_internet_connection(log, ad):
    """Verify internet connection by ping test.

    Args:
        log: log object
        ad: Android Device Object.

    """
    ad.log.info("Verify internet connection")
    return adb_shell_ping(ad, count=5, timeout=60, loss_tolerance=40)


def iperf_test_by_adb(log,
                      ad,
                      iperf_server,
                      port_num=None,
                      reverse=False,
                      timeout=180,
                      limit_rate=None,
                      omit=10,
                      ipv6=False,
                      rate_dict=None):
    """Iperf test by adb.

    Args:
        log: log object
        ad: Android Device Object.
        iperf_Server: The iperf host url".
        port_num: TCP/UDP server port
        timeout: timeout for file download to complete.
        limit_rate: iperf bandwidth option. None by default
        omit: the omit option provided in iperf command.
    """
    iperf_option = "-t %s -O %s -J" % (timeout, omit)
    if limit_rate: iperf_option += " -b %s" % limit_rate
    if port_num: iperf_option += " -p %s" % port_num
    if ipv6: iperf_option += " -6"
    if reverse: iperf_option += " -R"
    try:
        ad.log.info("Running adb iperf test with server %s", iperf_server)
        result, data = ad.run_iperf_client(
            iperf_server, iperf_option, timeout=timeout + 60)
        ad.log.info("Iperf test result with server %s is %s", iperf_server,
                    result)
        if result:
            data_json = json.loads(''.join(data))
            tx_rate = data_json['end']['sum_sent']['bits_per_second']
            rx_rate = data_json['end']['sum_received']['bits_per_second']
            ad.log.info(
                'iPerf3 upload speed is %sbps, download speed is %sbps',
                tx_rate, rx_rate)
            if rate_dict is not None:
                rate_dict["Uplink"] = tx_rate
                rate_dict["Downlink"] = rx_rate
        return result
    except Exception as e:
        ad.log.warning("Fail to run iperf test with exception %s", e)
        return False


def http_file_download_by_curl(ad,
                               url,
                               out_path=None,
                               expected_file_size=None,
                               remove_file_after_check=True,
                               timeout=900,
                               limit_rate=None,
                               retry=3):
    """Download http file by adb curl.

    Args:
        ad: Android Device Object.
        url: The url that file to be downloaded from".
        out_path: Optional. Where to download file to.
                  out_path is /sdcard/Download/ by default.
        expected_file_size: Optional. Provided if checking the download file meet
                            expected file size in unit of byte.
        remove_file_after_check: Whether to remove the downloaded file after
                                 check.
        timeout: timeout for file download to complete.
        limit_rate: download rate in bps. None, if do not apply rate limit.
        retry: the retry request times provided in curl command.
    """
    file_name, out_path = _generate_file_name_and_out_path(url, out_path)
    curl_cmd = "curl"
    if limit_rate:
        curl_cmd += " --limit-rate %s" % limit_rate
    if retry:
        curl_cmd += " --retry %s" % retry
    curl_cmd += " --url %s > %s" % (url, out_path)
    try:
        ad.log.info("Download file from %s to %s by adb shell command %s", url,
                    out_path, curl_cmd)
        ad.adb.shell(curl_cmd, timeout=timeout)
        if _check_file_existance(ad, file_name, out_path, expected_file_size):
            ad.log.info("File %s is downloaded from %s successfully",
                        file_name, url)
            return True
        else:
            ad.log.warning("Fail to download file from %s", url)
            return False
    except Exception as e:
        ad.log.warning("Download file from %s failed with exception %s", url,
                       e)
        return False
    finally:
        if remove_file_after_check:
            ad.log.info("Remove the downloaded file %s", out_path)
            ad.adb.shell("rm %s" % out_path, ignore_status=True)


def http_file_download_by_chrome(ad,
                                 url,
                                 expected_file_size=None,
                                 remove_file_after_check=True,
                                 timeout=900):
    """Download http file by chrome.

    Args:
        ad: Android Device Object.
        url: The url that file to be downloaded from".
        expected_file_size: Optional. Provided if checking the download file meet
                            expected file size in unit of byte.
        remove_file_after_check: Whether to remove the downloaded file after
                                 check.
        timeout: timeout for file download to complete.
    """
    file_name, out_path = _generate_file_name_and_out_path(
        url, "/sdcard/Download/")
    for cmd in ("am set-debug-app --persistent com.android.chrome",
                'echo "chrome --no-default-browser-check --no-first-run '
                '--disable-fre > /data/local/chrome-command-line"',
                "pm grant com.android.chrome "
                "android.permission.READ_EXTERNAL_STORAGE",
                "pm grant com.android.chrome "
                "android.permission.WRITE_EXTERNAL_STORAGE",
                'am start -a android.intent.action.VIEW -d "%s"' % url):
        ad.adb.shell(cmd)
    ad.log.info("Download %s from %s with timeout %s", file_name, url, timeout)
    elapse_time = 0
    while elapse_time < timeout:
        time.sleep(30)
        if _check_file_existance(ad, file_name, out_path, expected_file_size):
            ad.log.info("File %s is downloaded from %s successfully",
                        file_name, url)
            if remove_file_after_check:
                ad.log.info("Remove the downloaded file %s", out_path)
                ad.adb.shell("rm %s" % out_path, ignore_status=True)
            return True
        else:
            elapse_time += 30
    ad.log.warning("Fail to download file from %s", url)
    ad.adb.shell("rm %s" % out_path, ignore_status=True)
    return False


def http_file_download_by_sl4a(log,
                               ad,
                               url,
                               out_path=None,
                               expected_file_size=None,
                               remove_file_after_check=True,
                               timeout=300):
    """Download http file by sl4a RPC call.

    Args:
        log: log object
        ad: Android Device Object.
        url: The url that file to be downloaded from".
        out_path: Optional. Where to download file to.
                  out_path is /sdcard/Download/ by default.
        expected_file_size: Optional. Provided if checking the download file meet
                            expected file size in unit of byte.
        remove_file_after_check: Whether to remove the downloaded file after
                                 check.
        timeout: timeout for file download to complete.
    """
    file_name, out_path = _generate_file_name_and_out_path(url, out_path)
    try:
        ad.log.info("Download file from %s to %s by sl4a RPC call", url,
                    out_path)
        ad.droid.httpDownloadFile(url, out_path, timeout=timeout)
        if _check_file_existance(ad, file_name, out_path, expected_file_size):
            ad.log.info("File %s is downloaded from %s successfully",
                        file_name, url)
            return True
        else:
            ad.log.warning("Fail to download file from %s", url)
            return False
    except Exception as e:
        ad.log.error("Download file from %s failed with exception %s", url, e)
        raise
    finally:
        if remove_file_after_check:
            ad.log.info("Remove the downloaded file %s", out_path)
            ad.adb.shell("rm %s" % out_path, ignore_status=True)

def trigger_modem_crash(log, ad, timeout=10):
    cmd = "echo restart > /sys/kernel/debug/msm_subsys/modem"
    ad.log.info("Triggering Modem Crash using adb command %s", cmd)
    ad.adb.shell(cmd, timeout=timeout)
    return True

def _connection_state_change(_event, target_state, connection_type):
    if connection_type:
        if 'TypeName' not in _event['data']:
            return False
        connection_type_string_in_event = _event['data']['TypeName']
        cur_type = connection_type_from_type_string(
            connection_type_string_in_event)
        if cur_type != connection_type:
            log.info(
                "_connection_state_change expect: %s, received: %s <type %s>",
                connection_type, connection_type_string_in_event, cur_type)
            return False

    if 'isConnected' in _event['data'] and _event['data']['isConnected'] == target_state:
        return True
    return False


def wait_for_cell_data_connection(
        log, ad, state, timeout_value=EventDispatcher.DEFAULT_TIMEOUT):
    """Wait for data connection status to be expected value for default
       data subscription.

    Wait for the data connection status to be DATA_STATE_CONNECTED
        or DATA_STATE_DISCONNECTED.

    Args:
        log: Log object.
        ad: Android Device Object.
        state: Expected status: True or False.
            If True, it will wait for status to be DATA_STATE_CONNECTED.
            If False, it will wait for status ti be DATA_STATE_DISCONNECTED.
        timeout_value: wait for cell data timeout value.
            This is optional, default value is EventDispatcher.DEFAULT_TIMEOUT

    Returns:
        True if success.
        False if failed.
    """
    sub_id = get_default_data_sub_id(ad)
    return wait_for_cell_data_connection_for_subscription(
        log, ad, sub_id, state, timeout_value)


def _is_data_connection_state_match(log, ad, expected_data_connection_state):
    return (expected_data_connection_state ==
            ad.droid.telephonyGetDataConnectionState())


def _is_network_connected_state_match(log, ad,
                                      expected_network_connected_state):
    return (expected_network_connected_state ==
            ad.droid.connectivityNetworkIsConnected())


def wait_for_cell_data_connection_for_subscription(
        log, ad, sub_id, state, timeout_value=EventDispatcher.DEFAULT_TIMEOUT):
    """Wait for data connection status to be expected value for specified
       subscrption id.

    Wait for the data connection status to be DATA_STATE_CONNECTED
        or DATA_STATE_DISCONNECTED.

    Args:
        log: Log object.
        ad: Android Device Object.
        sub_id: subscription Id
        state: Expected status: True or False.
            If True, it will wait for status to be DATA_STATE_CONNECTED.
            If False, it will wait for status ti be DATA_STATE_DISCONNECTED.
        timeout_value: wait for cell data timeout value.
            This is optional, default value is EventDispatcher.DEFAULT_TIMEOUT

    Returns:
        True if success.
        False if failed.
    """
    state_str = {
        True: DATA_STATE_CONNECTED,
        False: DATA_STATE_DISCONNECTED
    }[state]

    data_state = ad.droid.telephonyGetDataConnectionState()
    if not state and ad.droid.telephonyGetDataConnectionState() == state_str:
        return True
    ad.ed.clear_all_events()
    ad.droid.telephonyStartTrackingDataConnectionStateChangeForSubscription(
        sub_id)
    ad.droid.connectivityStartTrackingConnectivityStateChange()
    try:
        # TODO: b/26293147 There is no framework API to get data connection
        # state by sub id
        data_state = ad.droid.telephonyGetDataConnectionState()
        if data_state == state_str:
            return _wait_for_nw_data_connection(
                log, ad, state, NETWORK_CONNECTION_TYPE_CELL, timeout_value)

        try:
            event = ad.ed.wait_for_event(
                EventDataConnectionStateChanged,
                is_event_match,
                timeout=timeout_value,
                field=DataConnectionStateContainer.DATA_CONNECTION_STATE,
                value=state_str)
        except Empty:
            ad.log.info("No expected event EventDataConnectionStateChanged %s",
                        state_str)

        # TODO: Wait for <MAX_WAIT_TIME_CONNECTION_STATE_UPDATE> seconds for
        # data connection state.
        # Otherwise, the network state will not be correct.
        # The bug is tracked here: b/20921915

        # Previously we use _is_data_connection_state_match,
        # but telephonyGetDataConnectionState sometimes return wrong value.
        # The bug is tracked here: b/22612607
        # So we use _is_network_connected_state_match.

        if _wait_for_droid_in_state(log, ad,
                                    MAX_WAIT_TIME_CONNECTION_STATE_UPDATE,
                                    _is_network_connected_state_match, state):
            return _wait_for_nw_data_connection(
                log, ad, state, NETWORK_CONNECTION_TYPE_CELL, timeout_value)
        else:
            return False

    finally:
        ad.droid.telephonyStopTrackingDataConnectionStateChangeForSubscription(
            sub_id)


def wait_for_wifi_data_connection(
        log, ad, state, timeout_value=EventDispatcher.DEFAULT_TIMEOUT):
    """Wait for data connection status to be expected value and connection is by WiFi.

    Args:
        log: Log object.
        ad: Android Device Object.
        state: Expected status: True or False.
            If True, it will wait for status to be DATA_STATE_CONNECTED.
            If False, it will wait for status ti be DATA_STATE_DISCONNECTED.
        timeout_value: wait for network data timeout value.
            This is optional, default value is EventDispatcher.DEFAULT_TIMEOUT

    Returns:
        True if success.
        False if failed.
    """
    ad.log.info("wait_for_wifi_data_connection")
    return _wait_for_nw_data_connection(
        log, ad, state, NETWORK_CONNECTION_TYPE_WIFI, timeout_value)


def wait_for_data_connection(log,
                             ad,
                             state,
                             timeout_value=EventDispatcher.DEFAULT_TIMEOUT):
    """Wait for data connection status to be expected value.

    Wait for the data connection status to be DATA_STATE_CONNECTED
        or DATA_STATE_DISCONNECTED.

    Args:
        log: Log object.
        ad: Android Device Object.
        state: Expected status: True or False.
            If True, it will wait for status to be DATA_STATE_CONNECTED.
            If False, it will wait for status ti be DATA_STATE_DISCONNECTED.
        timeout_value: wait for network data timeout value.
            This is optional, default value is EventDispatcher.DEFAULT_TIMEOUT

    Returns:
        True if success.
        False if failed.
    """
    return _wait_for_nw_data_connection(log, ad, state, None, timeout_value)


def _wait_for_nw_data_connection(
        log,
        ad,
        is_connected,
        connection_type=None,
        timeout_value=EventDispatcher.DEFAULT_TIMEOUT):
    """Wait for data connection status to be expected value.

    Wait for the data connection status to be DATA_STATE_CONNECTED
        or DATA_STATE_DISCONNECTED.

    Args:
        log: Log object.
        ad: Android Device Object.
        is_connected: Expected connection status: True or False.
            If True, it will wait for status to be DATA_STATE_CONNECTED.
            If False, it will wait for status ti be DATA_STATE_DISCONNECTED.
        connection_type: expected connection type.
            This is optional, if it is None, then any connection type will return True.
        timeout_value: wait for network data timeout value.
            This is optional, default value is EventDispatcher.DEFAULT_TIMEOUT

    Returns:
        True if success.
        False if failed.
    """
    ad.ed.clear_all_events()
    ad.droid.connectivityStartTrackingConnectivityStateChange()
    try:
        cur_data_connection_state = ad.droid.connectivityNetworkIsConnected()
        if is_connected == cur_data_connection_state:
            current_type = get_internet_connection_type(log, ad)
            ad.log.info("current data connection type: %s", current_type)
            if not connection_type:
                return True
            else:
                if not is_connected and current_type != connection_type:
                    ad.log.info("data connection not on %s!", connection_type)
                    return True
                elif is_connected and current_type == connection_type:
                    ad.log.info("data connection on %s as expected",
                                connection_type)
                    return True
        else:
            ad.log.info("current data connection state: %s target: %s",
                        cur_data_connection_state, is_connected)

        try:
            event = ad.ed.wait_for_event(
                EventConnectivityChanged, _connection_state_change,
                timeout_value, is_connected, connection_type)
            ad.log.info("received event: %s", event)
        except Empty:
            pass

        log.info(
            "_wait_for_nw_data_connection: check connection after wait event.")
        # TODO: Wait for <MAX_WAIT_TIME_CONNECTION_STATE_UPDATE> seconds for
        # data connection state.
        # Otherwise, the network state will not be correct.
        # The bug is tracked here: b/20921915
        if _wait_for_droid_in_state(
                log, ad, MAX_WAIT_TIME_CONNECTION_STATE_UPDATE,
                _is_network_connected_state_match, is_connected):
            current_type = get_internet_connection_type(log, ad)
            ad.log.info("current data connection type: %s", current_type)
            if not connection_type:
                return True
            else:
                if not is_connected and current_type != connection_type:
                    ad.log.info("data connection not on %s", connection_type)
                    return True
                elif is_connected and current_type == connection_type:
                    ad.log.info("after event wait, data connection on %s",
                                connection_type)
                    return True
                else:
                    return False
        else:
            return False
    except Exception as e:
        ad.log.error("Exception error %s", str(e))
        return False
    finally:
        ad.droid.connectivityStopTrackingConnectivityStateChange()


def toggle_cell_data_roaming(ad, state):
    """Enable cell data roaming for default data subscription.

    Wait for the data roaming status to be DATA_STATE_CONNECTED
        or DATA_STATE_DISCONNECTED.

    Args:
        log: Log object.
        ad: Android Device Object.
        state: True or False for enable or disable cell data roaming.

    Returns:
        True if success.
        False if failed.
    """
    state_int = {True: DATA_ROAMING_ENABLE, False: DATA_ROAMING_DISABLE}[state]
    action_str = {True: "Enable", False: "Disable"}[state]
    if ad.droid.connectivityCheckDataRoamingMode() == state:
        ad.log.info("Data roaming is already in state %s", state)
        return True
    if not ad.droid.connectivitySetDataRoaming(state_int):
        ad.error.info("Fail to config data roaming into state %s", state)
        return False
    if ad.droid.connectivityCheckDataRoamingMode() == state:
        ad.log.info("Data roaming is configured into state %s", state)
        return True
    else:
        ad.log.error("Data roaming is not configured into state %s", state)
        return False


def verify_incall_state(log, ads, expected_status):
    """Verify phones in incall state or not.

    Verify if all phones in the array <ads> are in <expected_status>.

    Args:
        log: Log object.
        ads: Array of Android Device Object. All droid in this array will be tested.
        expected_status: If True, verify all Phones in incall state.
            If False, verify all Phones not in incall state.

    """
    result = True
    for ad in ads:
        if ad.droid.telecomIsInCall() is not expected_status:
            ad.log.error("InCall status:%s, expected:%s",
                         ad.droid.telecomIsInCall(), expected_status)
            result = False
    return result


def verify_active_call_number(log, ad, expected_number):
    """Verify the number of current active call.

    Verify if the number of current active call in <ad> is
        equal to <expected_number>.

    Args:
        ad: Android Device Object.
        expected_number: Expected active call number.
    """
    calls = ad.droid.telecomCallGetCallIds()
    if calls is None:
        actual_number = 0
    else:
        actual_number = len(calls)
    if actual_number != expected_number:
        ad.log.error("Active Call number is %s, expecting", actual_number,
                     expected_number)
        return False
    return True


def num_active_calls(log, ad):
    """Get the count of current active calls.

    Args:
        log: Log object.
        ad: Android Device Object.

    Returns:
        Count of current active calls.
    """
    calls = ad.droid.telecomCallGetCallIds()
    return len(calls) if calls else 0


def toggle_volte(log, ad, new_state=None):
    """Toggle enable/disable VoLTE for default voice subscription.

    Args:
        ad: Android device object.
        new_state: VoLTE mode state to set to.
            True for enable, False for disable.
            If None, opposite of the current state.

    Raises:
        TelTestUtilsError if platform does not support VoLTE.
    """
    return toggle_volte_for_subscription(log, ad,
                                         get_outgoing_voice_sub_id(ad),
                                         new_state)


def toggle_volte_for_subscription(log, ad, sub_id, new_state=None):
    """Toggle enable/disable VoLTE for specified voice subscription.

    Args:
        ad: Android device object.
        sub_id: subscription ID
        new_state: VoLTE mode state to set to.
            True for enable, False for disable.
            If None, opposite of the current state.

    Raises:
        TelTestUtilsError if platform does not support VoLTE.
    """
    # TODO: b/26293960 No framework API available to set IMS by SubId.
    if not ad.droid.imsIsEnhanced4gLteModeSettingEnabledByPlatform():
        raise TelTestUtilsError("VoLTE not supported by platform.")
    current_state = ad.droid.imsIsEnhanced4gLteModeSettingEnabledByUser()
    if new_state is None:
        new_state = not current_state
    if new_state != current_state:
        ad.droid.imsSetEnhanced4gMode(new_state)
    return True


def set_wfc_mode(log, ad, wfc_mode):
    """Set WFC enable/disable and mode.

    Args:
        log: Log object
        ad: Android device object.
        wfc_mode: WFC mode to set to.
            Valid mode includes: WFC_MODE_WIFI_ONLY, WFC_MODE_CELLULAR_PREFERRED,
            WFC_MODE_WIFI_PREFERRED, WFC_MODE_DISABLED.

    Returns:
        True if success. False if ad does not support WFC or error happened.
    """
    try:
        ad.log.info("Set wfc mode to %s", wfc_mode)
        if not ad.droid.imsIsWfcEnabledByPlatform():
            if wfc_mode == WFC_MODE_DISABLED:
                return True
            else:
                ad.log.error("WFC not supported by platform.")
                return False
        ad.droid.imsSetWfcMode(wfc_mode)
        mode = ad.droid.imsGetWfcMode()
        if mode != wfc_mode:
            ad.log.error("WFC mode is %s, not in %s", mode, wfc_mode)
            return False
    except Exception as e:
        log.error(e)
        return False
    return True


def toggle_video_calling(log, ad, new_state=None):
    """Toggle enable/disable Video calling for default voice subscription.

    Args:
        ad: Android device object.
        new_state: Video mode state to set to.
            True for enable, False for disable.
            If None, opposite of the current state.

    Raises:
        TelTestUtilsError if platform does not support Video calling.
    """
    if not ad.droid.imsIsVtEnabledByPlatform():
        if new_state is not False:
            raise TelTestUtilsError("VT not supported by platform.")
        # if the user sets VT false and it's unavailable we just let it go
        return False

    current_state = ad.droid.imsIsVtEnabledByUser()
    if new_state is None:
        new_state = not current_state
    if new_state != current_state:
        ad.droid.imsSetVtSetting(new_state)
    return True


def _wait_for_droid_in_state(log, ad, max_time, state_check_func, *args,
                             **kwargs):
    while max_time >= 0:
        if state_check_func(log, ad, *args, **kwargs):
            return True

        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)
        max_time -= WAIT_TIME_BETWEEN_STATE_CHECK

    return False


def _wait_for_droid_in_state_for_subscription(
        log, ad, sub_id, max_time, state_check_func, *args, **kwargs):
    while max_time >= 0:
        if state_check_func(log, ad, sub_id, *args, **kwargs):
            return True

        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)
        max_time -= WAIT_TIME_BETWEEN_STATE_CHECK

    return False


def _wait_for_droids_in_state(log, ads, max_time, state_check_func, *args,
                              **kwargs):
    while max_time > 0:
        success = True
        for ad in ads:
            if not state_check_func(log, ad, *args, **kwargs):
                success = False
                break
        if success:
            return True

        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)
        max_time -= WAIT_TIME_BETWEEN_STATE_CHECK

    return False


def is_phone_in_call(log, ad):
    """Return True if phone in call.

    Args:
        log: log object.
        ad:  android device.
    """
    return ad.droid.telecomIsInCall()


def is_phone_not_in_call(log, ad):
    """Return True if phone not in call.

    Args:
        log: log object.
        ad:  android device.
    """
    in_call = ad.droid.telecomIsInCall()
    call_state = ad.droid.telephonyGetCallState()
    if in_call:
        ad.log.info("Device is In Call")
    if call_state != TELEPHONY_STATE_IDLE:
        ad.log.info("Call_state is %s, not %s", call_state,
                    TELEPHONY_STATE_IDLE)
    return ((not in_call) and (call_state == TELEPHONY_STATE_IDLE))


def wait_for_droid_in_call(log, ad, max_time):
    """Wait for android to be in call state.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.

    Returns:
        If phone become in call state within max_time, return True.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(log, ad, max_time, is_phone_in_call)


def wait_for_telecom_ringing(log, ad, max_time=MAX_WAIT_TIME_TELECOM_RINGING):
    """Wait for android to be in telecom ringing state.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time. This is optional.
            Default Value is MAX_WAIT_TIME_TELECOM_RINGING.

    Returns:
        If phone become in telecom ringing state within max_time, return True.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(
        log, ad, max_time, lambda log, ad: ad.droid.telecomIsRinging())


def wait_for_droid_not_in_call(log, ad, max_time=MAX_WAIT_TIME_CALL_DROP):
    """Wait for android to be not in call state.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.

    Returns:
        If phone become not in call state within max_time, return True.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(log, ad, max_time, is_phone_not_in_call)


def _is_attached(log, ad, voice_or_data):
    return _is_attached_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), voice_or_data)


def _is_attached_for_subscription(log, ad, sub_id, voice_or_data):
    rat = get_network_rat_for_subscription(log, ad, sub_id, voice_or_data)
    ad.log.info("Sub_id %s network rate is %s for %s", sub_id, rat,
                voice_or_data)
    return rat != RAT_UNKNOWN


def is_voice_attached(log, ad):
    return _is_attached_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), NETWORK_SERVICE_VOICE)


def wait_for_voice_attach(log, ad, max_time):
    """Wait for android device to attach on voice.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.

    Returns:
        Return True if device attach voice within max_time.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(log, ad, max_time, _is_attached,
                                    NETWORK_SERVICE_VOICE)


def wait_for_voice_attach_for_subscription(log, ad, sub_id, max_time):
    """Wait for android device to attach on voice in subscription id.

    Args:
        log: log object.
        ad:  android device.
        sub_id: subscription id.
        max_time: maximal wait time.

    Returns:
        Return True if device attach voice within max_time.
        Return False if timeout.
    """
    if not _wait_for_droid_in_state_for_subscription(
            log, ad, sub_id, max_time, _is_attached_for_subscription,
            NETWORK_SERVICE_VOICE):
        return False

    # TODO: b/26295983 if pone attach to 1xrtt from unknown, phone may not
    # receive incoming call immediately.
    if ad.droid.telephonyGetCurrentVoiceNetworkType() == RAT_1XRTT:
        time.sleep(WAIT_TIME_1XRTT_VOICE_ATTACH)
    return True


def wait_for_data_attach(log, ad, max_time):
    """Wait for android device to attach on data.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.

    Returns:
        Return True if device attach data within max_time.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(log, ad, max_time, _is_attached,
                                    NETWORK_SERVICE_DATA)


def wait_for_data_attach_for_subscription(log, ad, sub_id, max_time):
    """Wait for android device to attach on data in subscription id.

    Args:
        log: log object.
        ad:  android device.
        sub_id: subscription id.
        max_time: maximal wait time.

    Returns:
        Return True if device attach data within max_time.
        Return False if timeout.
    """
    return _wait_for_droid_in_state_for_subscription(
        log, ad, sub_id, max_time, _is_attached_for_subscription,
        NETWORK_SERVICE_DATA)


def is_ims_registered(log, ad):
    """Return True if IMS registered.

    Args:
        log: log object.
        ad: android device.

    Returns:
        Return True if IMS registered.
        Return False if IMS not registered.
    """
    return ad.droid.telephonyIsImsRegistered()


def wait_for_ims_registered(log, ad, max_time):
    """Wait for android device to register on ims.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.

    Returns:
        Return True if device register ims successfully within max_time.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(log, ad, max_time, is_ims_registered)


def is_volte_enabled(log, ad):
    """Return True if VoLTE feature bit is True.

    Args:
        log: log object.
        ad: android device.

    Returns:
        Return True if VoLTE feature bit is True and IMS registered.
        Return False if VoLTE feature bit is False or IMS not registered.
    """
    if not ad.droid.telephonyIsVolteAvailable():
        ad.log.info("IsVolteCallingAvailble is False")
        return False
    else:
        ad.log.info("IsVolteCallingAvailble is True")
        if not is_ims_registered(log, ad):
            ad.log.info("VoLTE is Available, but IMS is not registered.")
            return False
        return True


def is_video_enabled(log, ad):
    """Return True if Video Calling feature bit is True.

    Args:
        log: log object.
        ad: android device.

    Returns:
        Return True if Video Calling feature bit is True and IMS registered.
        Return False if Video Calling feature bit is False or IMS not registered.
    """
    video_status = ad.droid.telephonyIsVideoCallingAvailable()
    if video_status is True and is_ims_registered(log, ad) is False:
        log.error("Error! Video Call is Available, but IMS is not registered.")
        return False
    return video_status


def wait_for_volte_enabled(log, ad, max_time):
    """Wait for android device to report VoLTE enabled bit true.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.

    Returns:
        Return True if device report VoLTE enabled bit true within max_time.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(log, ad, max_time, is_volte_enabled)


def wait_for_video_enabled(log, ad, max_time):
    """Wait for android device to report Video Telephony enabled bit true.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.

    Returns:
        Return True if device report Video Telephony enabled bit true within max_time.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(log, ad, max_time, is_video_enabled)


def is_wfc_enabled(log, ad):
    """Return True if WiFi Calling feature bit is True.

    Args:
        log: log object.
        ad: android device.

    Returns:
        Return True if WiFi Calling feature bit is True and IMS registered.
        Return False if WiFi Calling feature bit is False or IMS not registered.
    """
    if not ad.droid.telephonyIsWifiCallingAvailable():
        ad.log.info("IsWifiCallingAvailble is False")
        return False
    else:
        ad.log.info("IsWifiCallingAvailble is True")
        if not is_ims_registered(log, ad):
            ad.log.info(
                "WiFi Calling is Available, but IMS is not registered.")
            return False
        return True


def wait_for_wfc_enabled(log, ad, max_time=MAX_WAIT_TIME_WFC_ENABLED):
    """Wait for android device to report WiFi Calling enabled bit true.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.
            Default value is MAX_WAIT_TIME_WFC_ENABLED.

    Returns:
        Return True if device report WiFi Calling enabled bit true within max_time.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(log, ad, max_time, is_wfc_enabled)


def wait_for_wfc_disabled(log, ad, max_time=MAX_WAIT_TIME_WFC_DISABLED):
    """Wait for android device to report WiFi Calling enabled bit false.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.
            Default value is MAX_WAIT_TIME_WFC_DISABLED.

    Returns:
        Return True if device report WiFi Calling enabled bit false within max_time.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(
        log, ad, max_time, lambda log, ad: not is_wfc_enabled(log, ad))


def get_phone_number(log, ad):
    """Get phone number for default subscription

    Args:
        log: log object.
        ad: Android device object.

    Returns:
        Phone number.
    """
    return get_phone_number_for_subscription(log, ad,
                                             get_outgoing_voice_sub_id(ad))


def get_phone_number_for_subscription(log, ad, subid):
    """Get phone number for subscription

    Args:
        log: log object.
        ad: Android device object.
        subid: subscription id.

    Returns:
        Phone number.
    """
    number = None
    try:
        number = ad.cfg['subscription'][subid]['phone_num']
    except KeyError:
        number = ad.droid.telephonyGetLine1NumberForSubscription(subid)
    return number


def set_phone_number(log, ad, phone_num):
    """Set phone number for default subscription

    Args:
        log: log object.
        ad: Android device object.
        phone_num: phone number string.

    Returns:
        True if success.
    """
    return set_phone_number_for_subscription(log, ad,
                                             get_outgoing_voice_sub_id(ad),
                                             phone_num)


def set_phone_number_for_subscription(log, ad, subid, phone_num):
    """Set phone number for subscription

    Args:
        log: log object.
        ad: Android device object.
        subid: subscription id.
        phone_num: phone number string.

    Returns:
        True if success.
    """
    try:
        ad.cfg['subscription'][subid]['phone_num'] = phone_num
    except Exception:
        return False
    return True


def get_operator_name(log, ad, subId=None):
    """Get operator name (e.g. vzw, tmo) of droid.

    Args:
        ad: Android device object.
        sub_id: subscription ID
            Optional, default is None

    Returns:
        Operator name.
    """
    try:
        if subId is not None:
            result = operator_name_from_plmn_id(
                ad.droid.telephonyGetNetworkOperatorForSubscription(subId))
        else:
            result = operator_name_from_plmn_id(
                ad.droid.telephonyGetNetworkOperator())
    except KeyError:
        result = CARRIER_UNKNOWN
    return result


def get_model_name(ad):
    """Get android device model name

    Args:
        ad: Android device object

    Returns:
        model name string
    """
    # TODO: Create translate table.
    model = ad.model
    if (model.startswith(AOSP_PREFIX)):
        model = model[len(AOSP_PREFIX):]
    return model


def is_sms_match(event, phonenumber_tx, text):
    """Return True if 'text' equals to event['data']['Text']
        and phone number match.

    Args:
        event: Event object to verify.
        phonenumber_tx: phone number for sender.
        text: text string to verify.

    Returns:
        Return True if 'text' equals to event['data']['Text']
            and phone number match.
    """
    return (
        check_phone_number_match(event['data']['Sender'], phonenumber_tx) and
        event['data']['Text'] == text)


def is_sms_partial_match(event, phonenumber_tx, text):
    """Return True if 'text' starts with event['data']['Text']
        and phone number match.

    Args:
        event: Event object to verify.
        phonenumber_tx: phone number for sender.
        text: text string to verify.

    Returns:
        Return True if 'text' starts with event['data']['Text']
            and phone number match.
    """
    return (
        check_phone_number_match(event['data']['Sender'], phonenumber_tx) and
        text.startswith(event['data']['Text']))


def sms_send_receive_verify(log, ad_tx, ad_rx, array_message):
    """Send SMS, receive SMS, and verify content and sender's number.

        Send (several) SMS from droid_tx to droid_rx.
        Verify SMS is sent, delivered and received.
        Verify received content and sender's number are correct.

    Args:
        log: Log object.
        ad_tx: Sender's Android Device Object
        ad_rx: Receiver's Android Device Object
        array_message: the array of message to send/receive
    """
    subid_tx = get_outgoing_message_sub_id(ad_tx)
    subid_rx = get_incoming_message_sub_id(ad_rx)
    return sms_send_receive_verify_for_subscription(
        log, ad_tx, ad_rx, subid_tx, subid_rx, array_message)


def wait_for_matching_sms(log,
                          ad_rx,
                          phonenumber_tx,
                          text,
                          allow_multi_part_long_sms=True):
    """Wait for matching incoming SMS.

    Args:
        log: Log object.
        ad_rx: Receiver's Android Device Object
        phonenumber_tx: Sender's phone number.
        text: SMS content string.
        allow_multi_part_long_sms: is long SMS allowed to be received as
            multiple short SMS. This is optional, default value is True.

    Returns:
        True if matching incoming SMS is received.
    """
    if not allow_multi_part_long_sms:
        try:
            ad_rx.ed.wait_for_event(EventSmsReceived, is_sms_match,
                                    MAX_WAIT_TIME_SMS_RECEIVE, phonenumber_tx,
                                    text)
            return True
        except Empty:
            ad_rx.log.error("No matched SMS received event.")
            return False
    else:
        try:
            received_sms = ''
            while (text != ''):
                event = ad_rx.ed.wait_for_event(
                    EventSmsReceived, is_sms_partial_match,
                    MAX_WAIT_TIME_SMS_RECEIVE, phonenumber_tx, text)
                text = text[len(event['data']['Text']):]
                received_sms += event['data']['Text']
            return True
        except Empty:
            ad_rx.log.error("No matched SMS received event.")
            if received_sms != '':
                ad_rx.log.error("Only received partial matched SMS: %s",
                                received_sms)
            return False


def is_mms_match(event, phonenumber_tx, text):
    """Return True if 'text' equals to event['data']['Text']
        and phone number match.

    Args:
        event: Event object to verify.
        phonenumber_tx: phone number for sender.
        text: text string to verify.

    Returns:
        Return True if 'text' equals to event['data']['Text']
            and phone number match.
    """
    #TODO:  add mms matching after mms message parser is added in sl4a. b/34276948
    return True


def wait_for_matching_mms(log, ad_rx, phonenumber_tx, text):
    """Wait for matching incoming SMS.

    Args:
        log: Log object.
        ad_rx: Receiver's Android Device Object
        phonenumber_tx: Sender's phone number.
        text: SMS content string.
        allow_multi_part_long_sms: is long SMS allowed to be received as
            multiple short SMS. This is optional, default value is True.

    Returns:
        True if matching incoming SMS is received.
    """
    try:
        #TODO: add mms matching after mms message parser is added in sl4a. b/34276948
        ad_rx.ed.wait_for_event(EventMmsDownloaded, is_mms_match,
                                MAX_WAIT_TIME_SMS_RECEIVE, phonenumber_tx,
                                text)
        return True
    except Empty:
        ad_rx.log.warning("No matched MMS downloaded event.")
        return False


def sms_send_receive_verify_for_subscription(log, ad_tx, ad_rx, subid_tx,
                                             subid_rx, array_message):
    """Send SMS, receive SMS, and verify content and sender's number.

        Send (several) SMS from droid_tx to droid_rx.
        Verify SMS is sent, delivered and received.
        Verify received content and sender's number are correct.

    Args:
        log: Log object.
        ad_tx: Sender's Android Device Object..
        ad_rx: Receiver's Android Device Object.
        subid_tx: Sender's subsciption ID to be used for SMS
        subid_rx: Receiver's subsciption ID to be used for SMS
        array_message: the array of message to send/receive
    """

    phonenumber_tx = ad_tx.cfg['subscription'][subid_tx]['phone_num']
    phonenumber_rx = ad_rx.cfg['subscription'][subid_rx]['phone_num']
    for text in array_message:
        length = len(text)
        ad_tx.log.info("Sending SMS from %s to %s, len: %s, content: %s.",
                       phonenumber_tx, phonenumber_rx, length, text)
        result = False
        ad_rx.ed.clear_all_events()
        ad_rx.droid.smsStartTrackingIncomingSmsMessage()
        try:
            ad_tx.droid.smsSendTextMessage(phonenumber_rx, text, True)

            try:
                ad_tx.ed.pop_event(EventSmsSentSuccess,
                                   MAX_WAIT_TIME_SMS_SENT_SUCCESS)
            except Empty:
                ad_tx.log.error("No sent_success event for SMS of length %s.",
                                length)
                return False

            if not wait_for_matching_sms(
                    log,
                    ad_rx,
                    phonenumber_tx,
                    text,
                    allow_multi_part_long_sms=True):
                ad_rx.log.error("No matching received SMS of length %s.",
                                length)
                return False
        finally:
            ad_rx.droid.smsStopTrackingIncomingSmsMessage()
    return True


def mms_send_receive_verify(log, ad_tx, ad_rx, array_message):
    """Send MMS, receive MMS, and verify content and sender's number.

        Send (several) MMS from droid_tx to droid_rx.
        Verify MMS is sent, delivered and received.
        Verify received content and sender's number are correct.

    Args:
        log: Log object.
        ad_tx: Sender's Android Device Object
        ad_rx: Receiver's Android Device Object
        array_message: the array of message to send/receive
    """
    return mms_send_receive_verify_for_subscription(
        log, ad_tx, ad_rx,
        get_outgoing_message_sub_id(ad_tx),
        get_incoming_message_sub_id(ad_rx), array_message)


#TODO: add mms matching after mms message parser is added in sl4a. b/34276948
def mms_send_receive_verify_for_subscription(log, ad_tx, ad_rx, subid_tx,
                                             subid_rx, array_payload):
    """Send MMS, receive MMS, and verify content and sender's number.

        Send (several) MMS from droid_tx to droid_rx.
        Verify MMS is sent, delivered and received.
        Verify received content and sender's number are correct.

    Args:
        log: Log object.
        ad_tx: Sender's Android Device Object..
        ad_rx: Receiver's Android Device Object.
        subid_tx: Sender's subsciption ID to be used for SMS
        subid_rx: Receiver's subsciption ID to be used for SMS
        array_message: the array of message to send/receive
    """

    phonenumber_tx = ad_tx.cfg['subscription'][subid_tx]['phone_num']
    phonenumber_rx = ad_rx.cfg['subscription'][subid_rx]['phone_num']
    for subject, message, filename in array_payload:
        ad_tx.log.info(
            "Sending MMS from %s to %s, subject: %s, message: %s, file: %s.",
            phonenumber_tx, phonenumber_rx, subject, message, filename)
        result = False
        ad_rx.ed.clear_all_events()
        ad_rx.droid.smsStartTrackingIncomingMmsMessage()
        try:
            ad_tx.droid.smsSendMultimediaMessage(
                phonenumber_rx, subject, message, phonenumber_tx, filename)
            try:
                ad_tx.ed.pop_event(EventMmsSentSuccess,
                                   MAX_WAIT_TIME_SMS_SENT_SUCCESS)
            except Empty:
                ad_tx.log.warning("No sent_success event.")
                return False

            if not wait_for_matching_mms(log, ad_rx, phonenumber_tx, message):
                return False
        finally:
            ad_rx.droid.smsStopTrackingIncomingMmsMessage()
    return True


def mms_receive_verify_after_call_hangup(log, ad_tx, ad_rx, array_message):
    """Verify the suspanded MMS during call will send out after call release.

        Hangup call from droid_tx to droid_rx.
        Verify MMS is sent, delivered and received.
        Verify received content and sender's number are correct.

    Args:
        log: Log object.
        ad_tx: Sender's Android Device Object
        ad_rx: Receiver's Android Device Object
        array_message: the array of message to send/receive
    """
    return mms_receive_verify_after_call_hangup_for_subscription(
        log, ad_tx, ad_rx,
        get_outgoing_message_sub_id(ad_tx),
        get_incoming_message_sub_id(ad_rx), array_message)


#TODO: add mms matching after mms message parser is added in sl4a. b/34276948
def mms_receive_verify_after_call_hangup_for_subscription(
        log, ad_tx, ad_rx, subid_tx, subid_rx, array_payload):
    """Verify the suspanded MMS during call will send out after call release.

        Hangup call from droid_tx to droid_rx.
        Verify MMS is sent, delivered and received.
        Verify received content and sender's number are correct.

    Args:
        log: Log object.
        ad_tx: Sender's Android Device Object..
        ad_rx: Receiver's Android Device Object.
        subid_tx: Sender's subsciption ID to be used for SMS
        subid_rx: Receiver's subsciption ID to be used for SMS
        array_message: the array of message to send/receive
    """

    phonenumber_tx = ad_tx.cfg['subscription'][subid_tx]['phone_num']
    phonenumber_rx = ad_rx.cfg['subscription'][subid_rx]['phone_num']
    for subject, message, filename in array_payload:
        ad_rx.log.info(
            "Waiting MMS from %s to %s, subject: %s, message: %s, file: %s.",
            phonenumber_tx, phonenumber_rx, subject, message, filename)
        result = False
        ad_rx.ed.clear_all_events()
        ad_rx.droid.smsStartTrackingIncomingMmsMessage()
        time.sleep(5)
        try:
            hangup_call(log, ad_tx)
            hangup_call(log, ad_rx)
            try:
                ad_tx.ed.pop_event(EventMmsSentSuccess,
                                   MAX_WAIT_TIME_SMS_SENT_SUCCESS)
            except Empty:
                log.warning("No sent_success event.")
                return False

            if not wait_for_matching_mms(log, ad_rx, phonenumber_tx, message):
                return False
        finally:
            ad_rx.droid.smsStopTrackingIncomingMmsMessage()
    return True


def ensure_network_rat(log,
                       ad,
                       network_preference,
                       rat_family,
                       voice_or_data=None,
                       max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
                       toggle_apm_after_setting=False):
    """Ensure ad's current network is in expected rat_family.
    """
    return ensure_network_rat_for_subscription(
        log, ad,
        ad.droid.subscriptionGetDefaultSubId(), network_preference, rat_family,
        voice_or_data, max_wait_time, toggle_apm_after_setting)


def ensure_network_rat_for_subscription(
        log,
        ad,
        sub_id,
        network_preference,
        rat_family,
        voice_or_data=None,
        max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
        toggle_apm_after_setting=False):
    """Ensure ad's current network is in expected rat_family.
    """
    if not ad.droid.telephonySetPreferredNetworkTypesForSubscription(
            network_preference, sub_id):
        ad.log.error("Set sub_id %s Preferred Networks Type %s failed.",
                     sub_id, network_preference)
        return False
    if is_droid_in_rat_family_for_subscription(log, ad, sub_id, rat_family,
                                               voice_or_data):
        ad.log.info("Sub_id %s in RAT %s for %s", sub_id, rat_family,
                    voice_or_data)
        return True

    if toggle_apm_after_setting:
        toggle_airplane_mode(log, ad, new_state=True, strict_checking=False)
        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
        toggle_airplane_mode(log, ad, new_state=None, strict_checking=False)

    result = wait_for_network_rat_for_subscription(
        log, ad, sub_id, rat_family, max_wait_time, voice_or_data)

    log.info(
        "End of ensure_network_rat_for_subscription for %s. "
        "Setting to %s, Expecting %s %s. Current: voice: %s(family: %s), "
        "data: %s(family: %s)", ad.serial, network_preference, rat_family,
        voice_or_data,
        ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(sub_id),
        rat_family_from_rat(
            ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(
                sub_id)),
        ad.droid.telephonyGetCurrentDataNetworkTypeForSubscription(sub_id),
        rat_family_from_rat(
            ad.droid.telephonyGetCurrentDataNetworkTypeForSubscription(
                sub_id)))
    return result


def ensure_network_preference(log,
                              ad,
                              network_preference,
                              voice_or_data=None,
                              max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
                              toggle_apm_after_setting=False):
    """Ensure that current rat is within the device's preferred network rats.
    """
    return ensure_network_preference_for_subscription(
        log, ad,
        ad.droid.subscriptionGetDefaultSubId(), network_preference,
        voice_or_data, max_wait_time, toggle_apm_after_setting)


def ensure_network_preference_for_subscription(
        log,
        ad,
        sub_id,
        network_preference,
        voice_or_data=None,
        max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
        toggle_apm_after_setting=False):
    """Ensure ad's network preference is <network_preference> for sub_id.
    """
    rat_family_list = rat_families_for_network_preference(network_preference)
    if not ad.droid.telephonySetPreferredNetworkTypesForSubscription(
            network_preference, sub_id):
        log.error("Set Preferred Networks failed.")
        return False
    if is_droid_in_rat_family_list_for_subscription(
            log, ad, sub_id, rat_family_list, voice_or_data):
        return True

    if toggle_apm_after_setting:
        toggle_airplane_mode(log, ad, new_state=True, strict_checking=False)
        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
        toggle_airplane_mode(log, ad, new_state=False, strict_checking=False)

    result = wait_for_preferred_network_for_subscription(
        log, ad, sub_id, network_preference, max_wait_time, voice_or_data)

    ad.log.info(
        "End of ensure_network_preference_for_subscription. "
        "Setting to %s, Expecting %s %s. Current: voice: %s(family: %s), "
        "data: %s(family: %s)", network_preference, rat_family_list,
        voice_or_data,
        ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(sub_id),
        rat_family_from_rat(
            ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(
                sub_id)),
        ad.droid.telephonyGetCurrentDataNetworkTypeForSubscription(sub_id),
        rat_family_from_rat(
            ad.droid.telephonyGetCurrentDataNetworkTypeForSubscription(
                sub_id)))
    return result


def ensure_network_generation(log,
                              ad,
                              generation,
                              max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
                              voice_or_data=None,
                              toggle_apm_after_setting=False):
    """Ensure ad's network is <network generation> for default subscription ID.

    Set preferred network generation to <generation>.
    Toggle ON/OFF airplane mode if necessary.
    Wait for ad in expected network type.
    """
    return ensure_network_generation_for_subscription(
        log, ad,
        ad.droid.subscriptionGetDefaultSubId(), generation, max_wait_time,
        voice_or_data, toggle_apm_after_setting)


def ensure_network_generation_for_subscription(
        log,
        ad,
        sub_id,
        generation,
        max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
        voice_or_data=None,
        toggle_apm_after_setting=False):
    """Ensure ad's network is <network generation> for specified subscription ID.

    Set preferred network generation to <generation>.
    Toggle ON/OFF airplane mode if necessary.
    Wait for ad in expected network type.
    """
    ad.log.info(
        "RAT network type voice: %s, data: %s",
        ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(sub_id),
        ad.droid.telephonyGetCurrentDataNetworkTypeForSubscription(sub_id))
    if is_droid_in_network_generation_for_subscription(
            log, ad, sub_id, generation, voice_or_data):
        return True

    try:
        ad.log.info("Finding the network preference for generation %s for "
                    "operator %s phone type %s", generation,
                    ad.cfg["subscription"][sub_id]["operator"],
                    ad.cfg["subscription"][sub_id]["phone_type"])
        network_preference = network_preference_for_generaton(
            generation, ad.cfg["subscription"][sub_id]["operator"],
            ad.cfg["subscription"][sub_id]["phone_type"])
        ad.log.info("Network preference for %s is %s", generation,
                    network_preference)
        rat_family = rat_family_for_generation(
            generation, ad.cfg["subscription"][sub_id]["operator"],
            ad.cfg["subscription"][sub_id]["phone_type"])
    except KeyError as e:
        ad.log.error("Failed to find a rat_family entry for generation %s"
                     " for subscriber %s with error %s", generation,
                     ad.cfg["subscription"][sub_id], e)
        return False

    current_network_preference = \
            ad.droid.telephonyGetPreferredNetworkTypesForSubscription(
                sub_id)

    if (current_network_preference is not network_preference and
            not ad.droid.telephonySetPreferredNetworkTypesForSubscription(
                network_preference, sub_id)):
        ad.log.error(
            "Network preference is %s. Set Preferred Networks to %s failed.",
            current_network_preference, network_preference)
        return False

    if is_droid_in_network_generation_for_subscription(
            log, ad, sub_id, generation, voice_or_data):
        return True

    if toggle_apm_after_setting:
        toggle_airplane_mode(log, ad, new_state=True, strict_checking=False)
        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
        toggle_airplane_mode(log, ad, new_state=False, strict_checking=False)

    result = wait_for_network_generation_for_subscription(
        log, ad, sub_id, generation, max_wait_time, voice_or_data)

    ad.log.info(
        "Ensure network %s %s %s. With network preference %s, "
        "current: voice: %s(family: %s), data: %s(family: %s)", generation,
        voice_or_data, result, network_preference,
        ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(sub_id),
        rat_generation_from_rat(
            ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(
                sub_id)),
        ad.droid.telephonyGetCurrentDataNetworkTypeForSubscription(sub_id),
        rat_generation_from_rat(
            ad.droid.telephonyGetCurrentDataNetworkTypeForSubscription(
                sub_id)))

    return result


def wait_for_network_rat(log,
                         ad,
                         rat_family,
                         max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
                         voice_or_data=None):
    return wait_for_network_rat_for_subscription(
        log, ad,
        ad.droid.subscriptionGetDefaultSubId(), rat_family, max_wait_time,
        voice_or_data)


def wait_for_network_rat_for_subscription(
        log,
        ad,
        sub_id,
        rat_family,
        max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
        voice_or_data=None):
    return _wait_for_droid_in_state_for_subscription(
        log, ad, sub_id, max_wait_time,
        is_droid_in_rat_family_for_subscription, rat_family, voice_or_data)


def wait_for_not_network_rat(log,
                             ad,
                             rat_family,
                             max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
                             voice_or_data=None):
    return wait_for_not_network_rat_for_subscription(
        log, ad,
        ad.droid.subscriptionGetDefaultSubId(), rat_family, max_wait_time,
        voice_or_data)


def wait_for_not_network_rat_for_subscription(
        log,
        ad,
        sub_id,
        rat_family,
        max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
        voice_or_data=None):
    return _wait_for_droid_in_state_for_subscription(
        log, ad, sub_id, max_wait_time,
        lambda log, ad, sub_id, *args, **kwargs: not is_droid_in_rat_family_for_subscription(log, ad, sub_id, rat_family, voice_or_data)
    )


def wait_for_preferred_network(log,
                               ad,
                               network_preference,
                               max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
                               voice_or_data=None):
    return wait_for_preferred_network_for_subscription(
        log, ad,
        ad.droid.subscriptionGetDefaultSubId(), network_preference,
        max_wait_time, voice_or_data)


def wait_for_preferred_network_for_subscription(
        log,
        ad,
        sub_id,
        network_preference,
        max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
        voice_or_data=None):
    rat_family_list = rat_families_for_network_preference(network_preference)
    return _wait_for_droid_in_state_for_subscription(
        log, ad, sub_id, max_wait_time,
        is_droid_in_rat_family_list_for_subscription, rat_family_list,
        voice_or_data)


def wait_for_network_generation(log,
                                ad,
                                generation,
                                max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
                                voice_or_data=None):
    return wait_for_network_generation_for_subscription(
        log, ad,
        ad.droid.subscriptionGetDefaultSubId(), generation, max_wait_time,
        voice_or_data)


def wait_for_network_generation_for_subscription(
        log,
        ad,
        sub_id,
        generation,
        max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
        voice_or_data=None):
    return _wait_for_droid_in_state_for_subscription(
        log, ad, sub_id, max_wait_time,
        is_droid_in_network_generation_for_subscription, generation,
        voice_or_data)


def is_droid_in_rat_family(log, ad, rat_family, voice_or_data=None):
    return is_droid_in_rat_family_for_subscription(
        log, ad,
        ad.droid.subscriptionGetDefaultSubId(), rat_family, voice_or_data)


def is_droid_in_rat_family_for_subscription(log,
                                            ad,
                                            sub_id,
                                            rat_family,
                                            voice_or_data=None):
    return is_droid_in_rat_family_list_for_subscription(
        log, ad, sub_id, [rat_family], voice_or_data)


def is_droid_in_rat_familiy_list(log, ad, rat_family_list, voice_or_data=None):
    return is_droid_in_rat_family_list_for_subscription(
        log, ad,
        ad.droid.subscriptionGetDefaultSubId(), rat_family_list, voice_or_data)


def is_droid_in_rat_family_list_for_subscription(log,
                                                 ad,
                                                 sub_id,
                                                 rat_family_list,
                                                 voice_or_data=None):
    service_list = [NETWORK_SERVICE_DATA, NETWORK_SERVICE_VOICE]
    if voice_or_data:
        service_list = [voice_or_data]

    for service in service_list:
        nw_rat = get_network_rat_for_subscription(log, ad, sub_id, service)
        if nw_rat == RAT_UNKNOWN or not is_valid_rat(nw_rat):
            continue
        if rat_family_from_rat(nw_rat) in rat_family_list:
            return True
    return False


def is_droid_in_network_generation(log, ad, nw_gen, voice_or_data):
    """Checks if a droid in expected network generation ("2g", "3g" or "4g").

    Args:
        log: log object.
        ad: android device.
        nw_gen: expected generation "4g", "3g", "2g".
        voice_or_data: check voice network generation or data network generation
            This parameter is optional. If voice_or_data is None, then if
            either voice or data in expected generation, function will return True.

    Returns:
        True if droid in expected network generation. Otherwise False.
    """
    return is_droid_in_network_generation_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), nw_gen, voice_or_data)


def is_droid_in_network_generation_for_subscription(log, ad, sub_id, nw_gen,
                                                    voice_or_data):
    """Checks if a droid in expected network generation ("2g", "3g" or "4g").

    Args:
        log: log object.
        ad: android device.
        nw_gen: expected generation "4g", "3g", "2g".
        voice_or_data: check voice network generation or data network generation
            This parameter is optional. If voice_or_data is None, then if
            either voice or data in expected generation, function will return True.

    Returns:
        True if droid in expected network generation. Otherwise False.
    """
    service_list = [NETWORK_SERVICE_DATA, NETWORK_SERVICE_VOICE]

    if voice_or_data:
        service_list = [voice_or_data]

    for service in service_list:
        nw_rat = get_network_rat_for_subscription(log, ad, sub_id, service)
        ad.log.info("%s network rat is %s", service, nw_rat)
        if nw_rat == RAT_UNKNOWN or not is_valid_rat(nw_rat):
            continue

        if rat_generation_from_rat(nw_rat) == nw_gen:
            ad.log.info("%s network rat %s is expected %s", service, nw_rat,
                        nw_gen)
            return True
        else:
            ad.log.info("%s network rat %s is %s, does not meet expected %s",
                        service, nw_rat,
                        rat_generation_from_rat(nw_rat), nw_gen)
            return False

    return False


def get_network_rat(log, ad, voice_or_data):
    """Get current network type (Voice network type, or data network type)
       for default subscription id

    Args:
        ad: Android Device Object
        voice_or_data: Input parameter indicating to get voice network type or
            data network type.

    Returns:
        Current voice/data network type.
    """
    return get_network_rat_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), voice_or_data)


def get_network_rat_for_subscription(log, ad, sub_id, voice_or_data):
    """Get current network type (Voice network type, or data network type)
       for specified subscription id

    Args:
        ad: Android Device Object
        sub_id: subscription ID
        voice_or_data: Input parameter indicating to get voice network type or
            data network type.

    Returns:
        Current voice/data network type.
    """
    if voice_or_data == NETWORK_SERVICE_VOICE:
        ret_val = ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(
            sub_id)
    elif voice_or_data == NETWORK_SERVICE_DATA:
        ret_val = ad.droid.telephonyGetCurrentDataNetworkTypeForSubscription(
            sub_id)
    else:
        ret_val = ad.droid.telephonyGetNetworkTypeForSubscription(sub_id)

    if ret_val is None:
        log.error("get_network_rat(): Unexpected null return value")
        return RAT_UNKNOWN
    else:
        return ret_val


def get_network_gen(log, ad, voice_or_data):
    """Get current network generation string (Voice network type, or data network type)

    Args:
        ad: Android Device Object
        voice_or_data: Input parameter indicating to get voice network generation
            or data network generation.

    Returns:
        Current voice/data network generation.
    """
    return get_network_gen_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), voice_or_data)


def get_network_gen_for_subscription(log, ad, sub_id, voice_or_data):
    """Get current network generation string (Voice network type, or data network type)

    Args:
        ad: Android Device Object
        voice_or_data: Input parameter indicating to get voice network generation
            or data network generation.

    Returns:
        Current voice/data network generation.
    """
    try:
        return rat_generation_from_rat(
            get_network_rat_for_subscription(log, ad, sub_id, voice_or_data))
    except KeyError as e:
        ad.log.error("KeyError %s", e)
        return GEN_UNKNOWN


def check_voice_mail_count(log, ad, voice_mail_count_before,
                           voice_mail_count_after):
    """function to check if voice mail count is correct after leaving a new voice message.
    """
    return get_voice_mail_count_check_function(get_operator_name(log, ad))(
        voice_mail_count_before, voice_mail_count_after)


def get_voice_mail_number(log, ad):
    """function to get the voice mail number
    """
    voice_mail_number = get_voice_mail_check_number(get_operator_name(log, ad))
    if voice_mail_number is None:
        return get_phone_number(log, ad)
    return voice_mail_number


def ensure_phones_idle(log, ads, max_time=MAX_WAIT_TIME_CALL_DROP):
    """Ensure ads idle (not in call).
    """
    result = True
    for ad in ads:
        if not ensure_phone_idle(log, ad, max_time=max_time):
            result = False
    return result


def ensure_phone_idle(log, ad, max_time=MAX_WAIT_TIME_CALL_DROP):
    """Ensure ad idle (not in call).
    """
    if ad.droid.telecomIsInCall():
        ad.droid.telecomEndCall()
    if not wait_for_droid_not_in_call(log, ad, max_time=max_time):
        ad.log.error("Failed to end call")
        return False
    return True


def ensure_phone_subscription(log, ad):
    """Ensure Phone Subscription.
    """
    #check for sim and service
    subInfo = ad.droid.subscriptionGetAllSubInfoList()
    if not subInfo or len(subInfo) < 1:
        ad.log.error("Unable to find A valid subscription!")
        return False
    if ad.droid.subscriptionGetDefaultDataSubId() <= INVALID_SUB_ID:
        ad.log.error("No Default Data Sub ID")
        return False
    elif ad.droid.subscriptionGetDefaultVoiceSubId() <= INVALID_SUB_ID:
        ad.log.error("No Valid Voice Sub ID")
        return False
    sub_id = ad.droid.subscriptionGetDefaultVoiceSubId()
    if not wait_for_voice_attach_for_subscription(log, ad, sub_id,
                                                  MAX_WAIT_TIME_NW_SELECTION):
        ad.log.error("Did Not Attach For Voice Services")
        return False
    return True


def ensure_phone_default_state(log, ad, check_subscription=True):
    """Ensure ad in default state.
    Phone not in call.
    Phone have no stored WiFi network and WiFi disconnected.
    Phone not in airplane mode.
    """
    result = True
    if not toggle_airplane_mode(log, ad, False, False):
        ad.log.error("Fail to turn off airplane mode")
        result = False
    set_wifi_to_default(log, ad)
    try:
        if ad.droid.telecomIsInCall():
            ad.droid.telecomEndCall()
            if not wait_for_droid_not_in_call(log, ad):
                ad.log.error("Failed to end call")
        ad.droid.telephonyFactoryReset()
        ad.droid.imsFactoryReset()
    except Exception as e:
        ad.log.error("%s failure, toggle APM instead", e)
        toggle_airplane_mode(log, ad, True, False)
        toggle_airplane_mode(log, ad, False, False)
        ad.droid.telephonyToggleDataConnection(True)
        set_wfc_mode(log, ad, WFC_MODE_DISABLED)

    get_telephony_signal_strength(ad)

    if not wait_for_not_network_rat(
            log, ad, RAT_FAMILY_WLAN, voice_or_data=NETWORK_SERVICE_DATA):
        ad.log.error("%s still in %s", NETWORK_SERVICE_DATA, RAT_FAMILY_WLAN)
        result = False

    if getattr(ad, 'data_roaming', False):
        ad.log.info("Enable cell data roaming")
        toggle_cell_data_roaming(ad, True)
    if check_subscription and not ensure_phone_subscription(log, ad):
        ad.log.error("Unable to find a valid subscription!")
        result = False

    return result


def ensure_phones_default_state(log, ads, check_subscription=True):
    """Ensure ads in default state.
    Phone not in call.
    Phone have no stored WiFi network and WiFi disconnected.
    Phone not in airplane mode.

    Returns:
        True if all steps of restoring default state succeed.
        False if any of the steps to restore default state fails.
    """
    tasks = []
    for ad in ads:
        tasks.append((ensure_phone_default_state, (log, ad,
                                                   check_subscription)))
    if not multithread_func(log, tasks):
        log.error("Ensure_phones_default_state Fail.")
        return False
    return True


def check_is_wifi_connected(log, ad, wifi_ssid):
    """Check if ad is connected to wifi wifi_ssid.

    Args:
        log: Log object.
        ad: Android device object.
        wifi_ssid: WiFi network SSID.

    Returns:
        True if wifi is connected to wifi_ssid
        False if wifi is not connected to wifi_ssid
    """
    wifi_info = ad.droid.wifiGetConnectionInfo()
    if wifi_info["supplicant_state"] == "completed" and wifi_info["SSID"] == wifi_ssid:
        ad.log.info("Wifi is connected to %s", wifi_ssid)
        return True
    else:
        ad.log.info("Wifi is not connected to %s", wifi_ssid)
        ad.log.debug("Wifi connection_info=%s", wifi_info)
        return False


def ensure_wifi_connected(log, ad, wifi_ssid, wifi_pwd=None, retries=3):
    """Ensure ad connected to wifi on network wifi_ssid.

    Args:
        log: Log object.
        ad: Android device object.
        wifi_ssid: WiFi network SSID.
        wifi_pwd: optional secure network password.
        retries: the number of retries.

    Returns:
        True if wifi is connected to wifi_ssid
        False if wifi is not connected to wifi_ssid
    """
    network = {WIFI_SSID_KEY: wifi_ssid}
    if wifi_pwd:
        network[WIFI_PWD_KEY] = wifi_pwd
    for i in range(retries):
        if not ad.droid.wifiCheckState():
            ad.log.info("Wifi state is down. Turn on Wifi")
            ad.droid.wifiToggleState(True)
        if check_is_wifi_connected(log, ad, wifi_ssid):
            ad.log.info("Wifi is connected to %s", wifi_ssid)
            return True
        else:
            ad.log.info("Connecting to wifi %s", wifi_ssid)
            try:
                ad.droid.wifiConnectByConfig(network)
            except Exception:
                ad.log.info("Connecting to wifi by wifiConnect instead")
                ad.droid.wifiConnect(network)
            time.sleep(20)
            if check_is_wifi_connected(log, ad, wifi_ssid):
                ad.log.info("Connected to Wifi %s", wifi_ssid)
                return True
    ad.log.info("Fail to connected to wifi %s", wifi_ssid)
    return False


def forget_all_wifi_networks(log, ad):
    """Forget all stored wifi network information

    Args:
        log: log object
        ad: AndroidDevice object

    Returns:
        boolean success (True) or failure (False)
    """
    if not ad.droid.wifiGetConfiguredNetworks():
        return True
    try:
        old_state = ad.droid.wifiCheckState()
        wifi_test_utils.reset_wifi(ad)
        wifi_toggle_state(log, ad, old_state)
    except Exception as e:
        log.error("forget_all_wifi_networks with exception: %s", e)
        return False
    return True


def wifi_reset(log, ad, disable_wifi=True):
    """Forget all stored wifi networks and (optionally) disable WiFi

    Args:
        log: log object
        ad: AndroidDevice object
        disable_wifi: boolean to disable wifi, defaults to True
    Returns:
        boolean success (True) or failure (False)
    """
    if not forget_all_wifi_networks(log, ad):
        ad.log.error("Unable to forget all networks")
        return False
    if not wifi_toggle_state(log, ad, not disable_wifi):
        ad.log.error("Failed to toggle WiFi state to %s!", not disable_wifi)
        return False
    return True


def set_wifi_to_default(log, ad):
    """Set wifi to default state (Wifi disabled and no configured network)

    Args:
        log: log object
        ad: AndroidDevice object

    Returns:
        boolean success (True) or failure (False)
    """
    ad.droid.wifiFactoryReset()
    ad.droid.wifiToggleState(False)


def wifi_toggle_state(log, ad, state, retries=3):
    """Toggle the WiFi State

    Args:
        log: log object
        ad: AndroidDevice object
        state: True, False, or None

    Returns:
        boolean success (True) or failure (False)
    """
    for i in range(retries):
        if wifi_test_utils.wifi_toggle_state(ad, state, assert_on_fail=False):
            return True
        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)
    return False


def start_wifi_tethering(log, ad, ssid, password, ap_band=None):
    """Start a Tethering Session

    Args:
        log: log object
        ad: AndroidDevice object
        ssid: the name of the WiFi network
        password: optional password, used for secure networks.
        ap_band=DEPRECATED specification of 2G or 5G tethering
    Returns:
        boolean success (True) or failure (False)
    """
    return wifi_test_utils._assert_on_fail_handler(
        wifi_test_utils.start_wifi_tethering,
        False,
        ad,
        ssid,
        password,
        band=ap_band)


def stop_wifi_tethering(log, ad):
    """Stop a Tethering Session

    Args:
        log: log object
        ad: AndroidDevice object
    Returns:
        boolean success (True) or failure (False)
    """
    return wifi_test_utils._assert_on_fail_handler(
        wifi_test_utils.stop_wifi_tethering, False, ad)


def reset_preferred_network_type_to_allowable_range(log, ad):
    """If preferred network type is not in allowable range, reset to GEN_4G
    preferred network type.

    Args:
        log: log object
        ad: android device object

    Returns:
        None
    """
    for sub_id, sub_info in ad.cfg["subscription"].items():
        current_preference = \
            ad.droid.telephonyGetPreferredNetworkTypesForSubscription(sub_id)
        ad.log.debug("sub_id network preference is %s", current_preference)
        try:
            if current_preference not in get_allowable_network_preference(
                    sub_info["operator"], sub_info["phone_type"]):
                network_preference = network_preference_for_generaton(
                    GEN_4G, sub_info["operator"], sub_info["phone_type"])
                ad.droid.telephonySetPreferredNetworkTypesForSubscription(
                    network_preference, sub_id)
        except KeyError:
            pass


def task_wrapper(task):
    """Task wrapper for multithread_func

    Args:
        task[0]: function to be wrapped.
        task[1]: function args.

    Returns:
        Return value of wrapped function call.
    """
    func = task[0]
    params = task[1]
    return func(*params)


def run_multithread_func(log, tasks):
    """Run multi-thread functions and return results.

    Args:
        log: log object.
        tasks: a list of tasks to be executed in parallel.

    Returns:
        results for tasks.
    """
    MAX_NUMBER_OF_WORKERS = 5
    number_of_workers = min(MAX_NUMBER_OF_WORKERS, len(tasks))
    executor = concurrent.futures.ThreadPoolExecutor(
        max_workers=number_of_workers)
    results = list(executor.map(task_wrapper, tasks))
    executor.shutdown()
    log.info("multithread_func %s result: %s",
             [task[0].__name__ for task in tasks], results)
    return results


def multithread_func(log, tasks):
    """Multi-thread function wrapper.

    Args:
        log: log object.
        tasks: tasks to be executed in parallel.

    Returns:
        True if all tasks return True.
        False if any task return False.
    """
    results = run_multithread_func(log, tasks)
    for r in results:
        if not r:
            return False
    return True


def multithread_func_and_check_results(log, tasks, expected_results):
    """Multi-thread function wrapper.

    Args:
        log: log object.
        tasks: tasks to be executed in parallel.
        expected_results: check if the results from tasks match expected_results.

    Returns:
        True if expected_results are met.
        False if expected_results are not met.
    """
    return_value = True
    results = run_multithread_func(log, tasks)
    log.info("multithread_func result: %s, expecting %s", results,
             expected_results)
    for task, result, expected_result in zip(tasks, results, expected_results):
        if result != expected_result:
            logging.info("Result for task %s is %s, expecting %s", task[0],
                         result, expected_result)
            return_value = False
    return return_value


def set_phone_screen_on(log, ad, screen_on_time=MAX_SCREEN_ON_TIME):
    """Set phone screen on time.

    Args:
        log: Log object.
        ad: Android device object.
        screen_on_time: screen on time.
            This is optional, default value is MAX_SCREEN_ON_TIME.
    Returns:
        True if set successfully.
    """
    ad.droid.setScreenTimeout(screen_on_time)
    return screen_on_time == ad.droid.getScreenTimeout()


def set_phone_silent_mode(log, ad, silent_mode=True):
    """Set phone silent mode.

    Args:
        log: Log object.
        ad: Android device object.
        silent_mode: set phone silent or not.
            This is optional, default value is True (silent mode on).
    Returns:
        True if set successfully.
    """
    ad.droid.toggleRingerSilentMode(silent_mode)
    ad.droid.setMediaVolume(0)
    ad.droid.setVoiceCallVolume(0)
    ad.droid.setAlarmVolume(0)

    return silent_mode == ad.droid.checkRingerSilentMode()


def set_preferred_subid_for_sms(log, ad, sub_id):
    """set subscription id for SMS

    Args:
        log: Log object.
        ad: Android device object.
        sub_id :Subscription ID.

    """
    ad.log.info("Setting subscription %s as preferred SMS SIM", sub_id)
    ad.droid.subscriptionSetDefaultSmsSubId(sub_id)
    # Wait to make sure settings take effect
    time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
    return sub_id == ad.droid.subscriptionGetDefaultSmsSubId()


def set_preferred_subid_for_data(log, ad, sub_id):
    """set subscription id for data

    Args:
        log: Log object.
        ad: Android device object.
        sub_id :Subscription ID.

    """
    ad.log.info("Setting subscription %s as preferred Data SIM", sub_id)
    ad.droid.subscriptionSetDefaultDataSubId(sub_id)
    time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
    # Wait to make sure settings take effect
    # Data SIM change takes around 1 min
    # Check whether data has changed to selected sim
    if not wait_for_data_connection(log, ad, True,
                                    MAX_WAIT_TIME_DATA_SUB_CHANGE):
        log.error("Data Connection failed - Not able to switch Data SIM")
        return False
    return True


def set_preferred_subid_for_voice(log, ad, sub_id):
    """set subscription id for voice

    Args:
        log: Log object.
        ad: Android device object.
        sub_id :Subscription ID.

    """
    ad.log.info("Setting subscription %s as Voice SIM", sub_id)
    ad.droid.subscriptionSetDefaultVoiceSubId(sub_id)
    ad.droid.telecomSetUserSelectedOutgoingPhoneAccountBySubId(sub_id)
    # Wait to make sure settings take effect
    time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
    return True


def set_call_state_listen_level(log, ad, value, sub_id):
    """Set call state listen level for subscription id.

    Args:
        log: Log object.
        ad: Android device object.
        value: True or False
        sub_id :Subscription ID.

    Returns:
        True or False
    """
    if sub_id == INVALID_SUB_ID:
        log.error("Invalid Subscription ID")
        return False
    ad.droid.telephonyAdjustPreciseCallStateListenLevelForSubscription(
        "Foreground", value, sub_id)
    ad.droid.telephonyAdjustPreciseCallStateListenLevelForSubscription(
        "Ringing", value, sub_id)
    ad.droid.telephonyAdjustPreciseCallStateListenLevelForSubscription(
        "Background", value, sub_id)
    return True


def setup_sim(log, ad, sub_id, voice=False, sms=False, data=False):
    """set subscription id for voice, sms and data

    Args:
        log: Log object.
        ad: Android device object.
        sub_id :Subscription ID.
        voice: True if to set subscription as default voice subscription
        sms: True if to set subscription as default sms subscription
        data: True if to set subscription as default data subscription

    """
    if sub_id == INVALID_SUB_ID:
        log.error("Invalid Subscription ID")
        return False
    else:
        if voice:
            if not set_preferred_subid_for_voice(log, ad, sub_id):
                return False
        if sms:
            if not set_preferred_subid_for_sms(log, ad, sub_id):
                return False
        if data:
            if not set_preferred_subid_for_data(log, ad, sub_id):
                return False
    return True


def is_event_match(event, field, value):
    """Return if <field> in "event" match <value> or not.

    Args:
        event: event to test. This event need to have <field>.
        field: field to match.
        value: value to match.

    Returns:
        True if <field> in "event" match <value>.
        False otherwise.
    """
    return is_event_match_for_list(event, field, [value])


def is_event_match_for_list(event, field, value_list):
    """Return if <field> in "event" match any one of the value
        in "value_list" or not.

    Args:
        event: event to test. This event need to have <field>.
        field: field to match.
        value_list: a list of value to match.

    Returns:
        True if <field> in "event" match one of the value in "value_list".
        False otherwise.
    """
    try:
        value_in_event = event['data'][field]
    except KeyError:
        return False
    for value in value_list:
        if value_in_event == value:
            return True
    return False


def is_network_call_back_event_match(event, network_callback_id,
                                     network_callback_event):
    try:
        return (
            (network_callback_id == event['data'][NetworkCallbackContainer.ID])
            and (network_callback_event == event['data']
                 [NetworkCallbackContainer.NETWORK_CALLBACK_EVENT]))
    except KeyError:
        return False


def is_build_id(log, ad, build_id):
    """Return if ad's build id is the same as input parameter build_id.

    Args:
        log: log object.
        ad: android device object.
        build_id: android build id.

    Returns:
        True if ad's build id is the same as input parameter build_id.
        False otherwise.
    """
    actual_bid = ad.droid.getBuildID()

    ad.log.info("BUILD DISPLAY: %s", ad.droid.getBuildDisplay())
    #In case we want to log more stuff/more granularity...
    #log.info("{} BUILD ID:{} ".format(ad.serial, ad.droid.getBuildID()))
    #log.info("{} BUILD FINGERPRINT: {} "
    # .format(ad.serial), ad.droid.getBuildFingerprint())
    #log.info("{} BUILD TYPE: {} "
    # .format(ad.serial), ad.droid.getBuildType())
    #log.info("{} BUILD NUMBER: {} "
    # .format(ad.serial), ad.droid.getBuildNumber())
    if actual_bid.upper() != build_id.upper():
        ad.log.error("%s: Incorrect Build ID", ad.model)
        return False
    return True


def is_uri_equivalent(uri1, uri2):
    """Check whether two input uris match or not.

    Compare Uris.
        If Uris are tel URI, it will only take the digit part
        and compare as phone number.
        Else, it will just do string compare.

    Args:
        uri1: 1st uri to be compared.
        uri2: 2nd uri to be compared.

    Returns:
        True if two uris match. Otherwise False.
    """

    #If either is None/empty we return false
    if not uri1 or not uri2:
        return False

    try:
        if uri1.startswith('tel:') and uri2.startswith('tel:'):
            uri1_number = get_number_from_tel_uri(uri1)
            uri2_number = get_number_from_tel_uri(uri2)
            return check_phone_number_match(uri1_number, uri2_number)
        else:
            return uri1 == uri2
    except AttributeError as e:
        return False


def get_call_uri(ad, call_id):
    """Get call's uri field.

    Get Uri for call_id in ad.

    Args:
        ad: android device object.
        call_id: the call id to get Uri from.

    Returns:
        call's Uri if call is active and have uri field. None otherwise.
    """
    try:
        call_detail = ad.droid.telecomCallGetDetails(call_id)
        return call_detail["Handle"]["Uri"]
    except:
        return None


def get_number_from_tel_uri(uri):
    """Get Uri number from tel uri

    Args:
        uri: input uri

    Returns:
        If input uri is tel uri, return the number part.
        else return None.
    """
    if uri.startswith('tel:'):
        uri_number = ''.join(
            i for i in urllib.parse.unquote(uri) if i.isdigit())
        return uri_number
    else:
        return None


def set_qxdm_logger_always_on(ad, mask_file="Radio-general.cfg"):
    """Set QXDM logger always on.

    Args:
        ad: android device object.

    """
    ad.adb.shell("setprop persist.sys.modem.diag.mdlog true")
    ad.adb.shell("setprop persist.radio.smlog_switch false")
    ad.adb.shell('echo "diag_mdlog -f /data/vendor/radio/diag_logs/cfg/%s'
                 ' -o /data/vendor/radio/diag_logs/logs -s 500 -n 10 -b -c > '
                 '/data/vendor/radio/diag_logs/diag.conf"' % mask_file)
    ad.reboot()


def check_qxdm_logger_always_on(ad, mask_file="Radio-general.cfg"):
    """Check if QXDM logger always on is set.

    Args:
        ad: android device object.

    """
    if ad.adb.shell("getprop persist.sys.modem.diag.mdlog") != 'true':
        return False
    if ad.adb.shell("getprop persist.radio.smlog_switch") != 'false':
        return False
    if mask_file not in ad.adb.shell(
            "cat /data/vendor/radio/diag_logs/diag.conf", ignore_status=True):
        return False
    return True
