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
"""
    Base Class for Defining Common Telephony Test Functionality
"""

import os
import time
import inspect
import traceback

import acts.controllers.diag_logger

from acts.base_test import BaseTestClass
from acts.keys import Config
from acts.signals import TestSignal
from acts.signals import TestAbortClass
from acts.signals import TestAbortAll
from acts import utils

from acts.test_utils.tel.tel_subscription_utils import \
    initial_set_up_for_subid_infomation
from acts.test_utils.tel.tel_test_utils import abort_all_tests
from acts.test_utils.tel.tel_test_utils import check_qxdm_logger_always_on
from acts.test_utils.tel.tel_test_utils import ensure_phones_default_state
from acts.test_utils.tel.tel_test_utils import ensure_phones_idle
from acts.test_utils.tel.tel_test_utils import refresh_droid_config
from acts.test_utils.tel.tel_test_utils import setup_droid_properties
from acts.test_utils.tel.tel_test_utils import set_phone_screen_on
from acts.test_utils.tel.tel_test_utils import set_phone_silent_mode
from acts.test_utils.tel.tel_test_utils import set_qxdm_logger_always_on
from acts.test_utils.tel.tel_defines import PRECISE_CALL_STATE_LISTEN_LEVEL_BACKGROUND
from acts.test_utils.tel.tel_defines import PRECISE_CALL_STATE_LISTEN_LEVEL_FOREGROUND
from acts.test_utils.tel.tel_defines import PRECISE_CALL_STATE_LISTEN_LEVEL_RINGING
from acts.test_utils.tel.tel_defines import WIFI_VERBOSE_LOGGING_ENABLED
from acts.test_utils.tel.tel_defines import WIFI_VERBOSE_LOGGING_DISABLED
from acts.utils import force_airplane_mode

QXDM_LOG_PATH = "/data/vendor/radio/diag_logs/logs/"


class TelephonyBaseTest(BaseTestClass):
    def __init__(self, controllers):

        BaseTestClass.__init__(self, controllers)
        self.logger_sessions = []

        for ad in self.android_devices:
            if getattr(ad, "qxdm_always_on", False):
                #this is only supported on 2017 devices
                ad.log.info("qxdm_always_on is set in config file")
                mask = getattr(ad, "qxdm_mask", "Radio-general.cfg")
                if not check_qxdm_logger_always_on(ad, mask):
                    ad.log.info("qxdm always on is not set, turn it on")
                    set_qxdm_logger_always_on(ad, mask)
                else:
                    ad.log.info("qxdm always on is already set")
            #The puk and pin should be provided in testbed config file.
            #"AndroidDevice": [{"serial": "84B5T15A29018214",
            #                   "adb_logcat_param": "-b all",
            #                   "puk": "12345678",
            #                   "puk_pin": "1234"}]
            if hasattr(ad, 'puk'):
                if not hasattr(ad, 'puk_pin'):
                    abort_all_tests(ad.log, "puk_pin is not provided")
                ad.log.info("Enter PUK code and pin")
                if not ad.droid.telephonySupplyPuk(ad.puk, ad.puk_pin):
                    abort_all_tests(
                        ad.log,
                        "Puk and puk_pin provided in testbed config do NOT work"
                    )

        self.skip_reset_between_cases = self.user_params.get(
            "skip_reset_between_cases", True)

    # Use for logging in the test cases to facilitate
    # faster log lookup and reduce ambiguity in logging.
    @staticmethod
    def tel_test_wrap(fn):
        def _safe_wrap_test_case(self, *args, **kwargs):
            current_time = time.strftime("%m-%d-%Y-%H-%M-%S")
            func_name = fn.__name__
            test_id = "%s:%s:%s" % (self.__class__.__name__, func_name,
                                    current_time)
            self.test_id = test_id
            self.begin_time = current_time
            self.test_name = func_name
            log_string = "[Test ID] %s" % test_id
            self.log.info(log_string)
            try:
                for ad in self.android_devices:
                    ad.droid.logI("Started %s" % log_string)
                    ad.crash_report = ad.check_crash_report(
                        log_crash_report=False)
                    if ad.crash_report:
                        ad.log.info("Found crash reports %s before test start",
                                    ad.crash_report)

                # TODO: b/19002120 start QXDM Logging
                result = fn(self, *args, **kwargs)
                for ad in self.android_devices:
                    ad.droid.logI("Finished %s" % log_string)
                    new_crash = ad.check_crash_report()
                    crash_diff = set(new_crash).difference(
                        set(ad.crash_report))
                    if crash_diff:
                        ad.log.error("Find new crash reports %s",
                                     list(crash_diff))
                if not result and self.user_params.get("telephony_auto_rerun"):
                    self.teardown_test()
                    # re-run only once, if re-run pass, mark as pass
                    log_string = "[Rerun Test ID] %s. 1st run failed." % test_id
                    self.log.info(log_string)
                    self.setup_test()
                    for ad in self.android_devices:
                        ad.droid.logI("Rerun Started %s" % log_string)
                    result = fn(self, *args, **kwargs)
                    if result is True:
                        self.log.info("Rerun passed.")
                    elif result is False:
                        self.log.info("Rerun failed.")
                    else:
                        # In the event that we have a non-bool or null
                        # retval, we want to clearly distinguish this in the
                        # logs from an explicit failure, though the test will
                        # still be considered a failure for reporting purposes.
                        self.log.info("Rerun indeterminate.")
                        result = False
                return result
            except (TestSignal, TestAbortClass, TestAbortAll):
                raise
            except Exception as e:
                self.log.error(traceback.format_exc())
                self.log.error(str(e))
                return False
            finally:
                # TODO: b/19002120 stop QXDM Logging
                for ad in self.android_devices:
                    try:
                        ad.adb.wait_for_device()
                    except Exception as e:
                        self.log.error(str(e))

        return _safe_wrap_test_case

    def setup_class(self):
        sim_conf_file = self.user_params.get("sim_conf_file")
        if not sim_conf_file:
            self.log.info("\"sim_conf_file\" is not provided test bed config!")
        else:
            # If the sim_conf_file is not a full path, attempt to find it
            # relative to the config file.
            if not os.path.isfile(sim_conf_file):
                sim_conf_file = os.path.join(
                    self.user_params[Config.key_config_path], sim_conf_file)
                if not os.path.isfile(sim_conf_file):
                    self.log.error("Unable to load user config %s ",
                                   sim_conf_file)
                    return False

        setattr(self, "diag_logger",
                self.register_controller(
                    acts.controllers.diag_logger, required=False))
        is_mobility_setup = self.user_params.get("Attenuator")
        if not is_mobility_setup:
            ensure_phones_default_state(self.log, self.android_devices)
        for ad in self.android_devices:
            setup_droid_properties(self.log, ad, sim_conf_file)

            # Setup VoWiFi MDN for Verizon. b/33187374
            build_id = ad.build_info["build_id"]
            if "vzw" in [
                    sub["operator"] for sub in ad.cfg["subscription"].values()
            ] and ad.is_apk_installed("com.google.android.wfcactivation"):
                ad.log.info("setup VoWiFi MDN per b/33187374")
                ad.adb.shell("setprop dbg.vzw.force_wfc_nv_enabled true")
                ad.adb.shell("am start --ei EXTRA_LAUNCH_CARRIER_APP 0 -n "
                             "\"com.google.android.wfcactivation/"
                             ".VzwEmergencyAddressActivity\"")
            if not ad.is_apk_running("com.google.telephonymonitor"):
                ad.log.info("TelephonyMonitor is not running, start it now")
                ad.adb.shell(
                    'am broadcast -a '
                    'com.google.gservices.intent.action.GSERVICES_OVERRIDE -e '
                    '"ce.telephony_monitor_enable" "true"')

            # Ensure that a test class starts from a consistent state that
            # improves chances of valid network selection and facilitates
            # logging.
            try:
                if not set_phone_screen_on(self.log, ad):
                    self.log.error("Failed to set phone screen-on time.")
                    return False
                if not set_phone_silent_mode(self.log, ad):
                    self.log.error("Failed to set phone silent mode.")
                    return False

                ad.droid.telephonyAdjustPreciseCallStateListenLevel(
                    PRECISE_CALL_STATE_LISTEN_LEVEL_FOREGROUND, True)
                ad.droid.telephonyAdjustPreciseCallStateListenLevel(
                    PRECISE_CALL_STATE_LISTEN_LEVEL_RINGING, True)
                ad.droid.telephonyAdjustPreciseCallStateListenLevel(
                    PRECISE_CALL_STATE_LISTEN_LEVEL_BACKGROUND, True)

                if "enable_wifi_verbose_logging" in self.user_params:
                    ad.droid.wifiEnableVerboseLogging(
                        WIFI_VERBOSE_LOGGING_ENABLED)
            except Exception as e:
                self.log.error("Failure with %s", e)
        # Sub ID setup
        for ad in self.android_devices:
            initial_set_up_for_subid_infomation(self.log, ad)
        return True

    def teardown_class(self):
        try:
            ensure_phones_default_state(self.log, self.android_devices)

            for ad in self.android_devices:
                ad.droid.telephonyAdjustPreciseCallStateListenLevel(
                    PRECISE_CALL_STATE_LISTEN_LEVEL_FOREGROUND, False)
                ad.droid.telephonyAdjustPreciseCallStateListenLevel(
                    PRECISE_CALL_STATE_LISTEN_LEVEL_RINGING, False)
                ad.droid.telephonyAdjustPreciseCallStateListenLevel(
                    PRECISE_CALL_STATE_LISTEN_LEVEL_BACKGROUND, False)
                if "enable_wifi_verbose_logging" in self.user_params:
                    ad.droid.wifiEnableVerboseLogging(
                        WIFI_VERBOSE_LOGGING_DISABLED)
            return True
        except Exception as e:
            self.log.error("Failure with %s", e)

    def setup_test(self):
        if getattr(self, "diag_logger", None):
            for logger in self.diag_logger:
                self.log.info("Starting a diagnostic session %s", logger)
                self.logger_sessions.append((logger, logger.start()))

        if self.skip_reset_between_cases:
            return ensure_phones_idle(self.log, self.android_devices)
        return ensure_phones_default_state(self.log, self.android_devices)

    def teardown_test(self):
        return True

    def on_exception(self, test_name, begin_time):
        self._pull_diag_logs(test_name, begin_time)
        self._take_bug_report(test_name, begin_time)
        self._cleanup_logger_sessions()

    def on_fail(self, test_name, begin_time):
        self._pull_diag_logs(test_name, begin_time)
        self._take_bug_report(test_name, begin_time)
        self._cleanup_logger_sessions()

    def on_pass(self, test_name, begin_time):
        self._cleanup_logger_sessions()

    def get_stress_test_number(self):
        """Gets the stress_test_number param from user params.

        Gets the stress_test_number param. If absent, returns default 100.
        """
        return int(self.user_params.get("stress_test_number", 100))
