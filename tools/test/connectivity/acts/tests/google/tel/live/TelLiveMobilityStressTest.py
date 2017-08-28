#!/usr/bin/env python3.4
#
#   Copyright 2017 - Google
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
    Test Script for Telephony Mobility Stress Test
"""

import collections
import random
import time
from acts.asserts import fail
from acts.test_decorators import test_tracker_info
from acts.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts.test_utils.tel.tel_atten_utils import set_rssi
from acts.test_utils.tel.tel_defines import CELL_WEAK_RSSI_VALUE
from acts.test_utils.tel.tel_defines import CELL_STRONG_RSSI_VALUE
from acts.test_utils.tel.tel_defines import MAX_RSSI_RESERVED_VALUE
from acts.test_utils.tel.tel_defines import MIN_RSSI_RESERVED_VALUE
from acts.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts.test_utils.tel.tel_defines import WFC_MODE_DISABLED
from acts.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts.test_utils.tel.tel_defines import WIFI_WEAK_RSSI_VALUE
from acts.test_utils.tel.tel_test_utils import active_file_download_test
from acts.test_utils.tel.tel_test_utils import call_setup_teardown
from acts.test_utils.tel.tel_test_utils import ensure_phone_default_state
from acts.test_utils.tel.tel_test_utils import ensure_phone_subscription
from acts.test_utils.tel.tel_test_utils import ensure_phones_idle
from acts.test_utils.tel.tel_test_utils import ensure_wifi_connected
from acts.test_utils.tel.tel_test_utils import hangup_call
from acts.test_utils.tel.tel_test_utils import is_voice_attached
from acts.test_utils.tel.tel_test_utils import run_multithread_func
from acts.test_utils.tel.tel_test_utils import set_wfc_mode
from acts.test_utils.tel.tel_test_utils import sms_send_receive_verify
from acts.test_utils.tel.tel_test_utils import mms_send_receive_verify
from acts.test_utils.tel.tel_voice_utils import is_phone_in_call_3g
from acts.test_utils.tel.tel_voice_utils import is_phone_in_call_2g
from acts.test_utils.tel.tel_voice_utils import is_phone_in_call_csfb
from acts.test_utils.tel.tel_voice_utils import is_phone_in_call_iwlan
from acts.test_utils.tel.tel_voice_utils import is_phone_in_call_volte
from acts.test_utils.tel.tel_voice_utils import phone_setup_csfb
from acts.test_utils.tel.tel_voice_utils import phone_setup_iwlan
from acts.test_utils.tel.tel_voice_utils import phone_setup_voice_3g
from acts.test_utils.tel.tel_voice_utils import phone_setup_voice_2g
from acts.test_utils.tel.tel_voice_utils import phone_setup_volte
from acts.test_utils.tel.tel_voice_utils import get_current_voice_rat
from acts.utils import rand_ascii_str
from TelWifiVoiceTest import TelWifiVoiceTest
from TelWifiVoiceTest import ATTEN_NAME_FOR_WIFI_2G
from TelWifiVoiceTest import ATTEN_NAME_FOR_WIFI_5G
from TelWifiVoiceTest import ATTEN_NAME_FOR_CELL_3G
from TelWifiVoiceTest import ATTEN_NAME_FOR_CELL_4G


class TelLiveMobilityStressTest(TelWifiVoiceTest):
    def setup_class(self):
        super().setup_class()
        #super(TelWifiVoiceTest, self).setup_class()
        self.user_params["telephony_auto_rerun"] = False
        self.max_phone_call_duration = int(
            self.user_params.get("max_phone_call_duration", 600))
        self.max_sleep_time = int(self.user_params.get("max_sleep_time", 120))
        self.max_run_time = int(self.user_params.get("max_run_time", 7200))
        self.max_sms_length = int(self.user_params.get("max_sms_length", 1000))
        self.max_mms_length = int(self.user_params.get("max_mms_length", 160))
        self.crash_check_interval = int(
            self.user_params.get("crash_check_interval", 300))
        self.signal_change_interval = int(
            self.user_params.get("signal_change_interval", 10))
        self.signal_change_step = int(
            self.user_params.get("signal_change_step", 5))
        self.dut = self.android_devices[0]
        self.helper = self.android_devices[1]

        return True

    def _setup_volte_wfc_wifi_preferred(self):
        return self._wfc_phone_setup(
            False, WFC_MODE_WIFI_PREFERRED, volte_mode=True)

    def _setup_volte_wfc_cell_preferred(self):
        return self._wfc_phone_setup(
            False, WFC_MODE_CELLULAR_PREFERRED, volte_mode=True)

    def _setup_csfb_wfc_wifi_preferred(self):
        return self._wfc_phone_setup(
            False, WFC_MODE_WIFI_PREFERRED, volte_mode=False)

    def _setup_csfb_wfc_cell_preferred(self):
        return self._wfc_phone_setup(
            False, WFC_MODE_CELLULAR_PREFERRED, volte_mode=False)

    def _setup_volte_wfc_disabled(self):
        return self._wfc_phone_setup(False, WFC_MODE_DISABLED, volte_mode=True)

    def _setup_csfb_wfc_disabled(self):
        return self._wfc_phone_setup(
            False, WFC_MODE_DISABLED, volte_mode=False)

    def _send_message(self, ads):
        selection = random.randrange(0, 2)
        message_type_map = {0: "SMS", 1: "MMS"}
        max_length_map = {0: self.max_sms_length, 1: self.max_mms_length}
        length = random.randrange(0, max_length_map[selection] + 1)
        text = rand_ascii_str(length)
        message_content_map = {0: [text], 1: [("Mms Message", text, None)]}
        message_func_map = {
            0: sms_send_receive_verify,
            1: mms_send_receive_verify
        }
        if not message_func_map[selection](self.log, ads[0], ads[1],
                                           message_content_map[selection]):
            self.log.error("%s of length %s from %s to %s fails",
                           message_type_map[selection], length, ads[0].serial,
                           ads[1].serial)
            self.result_info["%s failure" % message_type_map[selection]] += 1
            return False
        else:
            self.log.info("%s of length %s from %s to %s succeed",
                          message_type_map[selection], length, ads[0].serial,
                          ads[1].serial)
            return True

    def _make_phone_call(self, ads):
        if not call_setup_teardown(
                self.log,
                ads[0],
                ads[1],
                ad_hangup=ads[random.randrange(0, 2)],
                verify_caller_func=is_voice_attached,
                verify_callee_func=is_voice_attached,
                wait_time_in_call=random.randrange(
                    30, self.max_phone_call_duration)):
            ads[0].log.error("Setup phone Call failed.")
            return False
        ads[0].log.info("Setup call successfully.")
        return True

    def crash_check_test(self):
        failure = 0
        while time.time() < self.finishing_time:
            new_crash = self.dut.check_crash_report()
            crash_diff = set(new_crash).difference(set(self.dut.crash_report))
            self.dut.crash_report = new_crash
            if crash_diff:
                self.dut.log.error("Find new crash reports %s",
                                   list(crash_diff))
                self.dut.pull_files(list(crash_diff))
                failure += 1
                self.result_info["Crashes"] += 1
                self._take_bug_report("%s_crash_found" % self.test_name,
                                      time.strftime("%m-%d-%Y-%H-%M-%S"))
            self.dut.droid.goToSleepNow()
            time.sleep(self.crash_check_interval)
        return failure

    def environment_change_4g_wifi(self):
        #block cell 3G, WIFI 2G
        set_rssi(self.log, self.attens[ATTEN_NAME_FOR_CELL_3G], 0,
                 MIN_RSSI_RESERVED_VALUE)
        set_rssi(self.log, self.attens[ATTEN_NAME_FOR_WIFI_2G], 0,
                 MIN_RSSI_RESERVED_VALUE)
        while time.time() < self.finishing_time:
            #set strong wifi 5G and LTE
            set_rssi(self.log, self.attens[ATTEN_NAME_FOR_WIFI_5G], 0,
                     MAX_RSSI_RESERVED_VALUE)
            set_rssi(self.log, self.attens[ATTEN_NAME_FOR_CELL_4G], 0,
                     MAX_RSSI_RESERVED_VALUE)
            #gratually decrease wifi 5g
            set_rssi(self.log, self.attens[ATTEN_NAME_FOR_WIFI_5G],
                     self.wifi_rssi_with_no_atten, MIN_RSSI_RESERVED_VALUE,
                     self.signal_change_step, self.signal_change_interval)
            #gratually increase wifi 5g
            set_rssi(self.log, self.attens[ATTEN_NAME_FOR_WIFI_5G],
                     MIN_RSSI_RESERVED_VALUE, MAX_RSSI_RESERVED_VALUE,
                     self.signal_change_step, self.signal_change_interval)

            #gratually decrease cell 4G
            set_rssi(self.log, self.attens[ATTEN_NAME_FOR_CELL_4G],
                     self.cell_rssi_with_no_atten, CELL_WEAK_RSSI_VALUE,
                     self.signal_change_step, self.signal_change_interval)
            #gradtually increase cell 4G
            set_rssi(self.log, self.attens[ATTEN_NAME_FOR_CELL_4G],
                     MIN_RSSI_RESERVED_VALUE, MAX_RSSI_RESERVED_VALUE,
                     self.signal_change_step, self.signal_change_interval)
        return 0

    def environment_change_4g_3g(self):
        #block wifi 2G and 5G
        set_rssi(self.log, self.attens[ATTEN_NAME_FOR_WIFI_2G], 0,
                 MIN_RSSI_RESERVED_VALUE)
        set_rssi(self.log, self.attens[ATTEN_NAME_FOR_WIFI_5G], 0,
                 MIN_RSSI_RESERVED_VALUE)
        while time.time() < self.finishing_time:
            #set strong cell 4G and 3G
            set_rssi(self.log, self.attens[ATTEN_NAME_FOR_CELL_4G], 0,
                     MAX_RSSI_RESERVED_VALUE)
            set_rssi(self.log, self.attens[ATTEN_NAME_FOR_CELL_3G], 0,
                     MAX_RSSI_RESERVED_VALUE)
            #gratually decrease cell 4G
            set_rssi(self.log, self.attens[ATTEN_NAME_FOR_CELL_4G],
                     self.cell_rssi_with_no_atten, MIN_RSSI_RESERVED_VALUE,
                     self.signal_change_step, self.signal_change_interval)
            #gradtually increase cell 4G
            set_rssi(self.log, self.attens[ATTEN_NAME_FOR_CELL_4G],
                     MIN_RSSI_RESERVED_VALUE, MAX_RSSI_RESERVED_VALUE,
                     self.signal_change_step, self.signal_change_interval)
            #gratually decrease cell 3G
            set_rssi(self.log, self.attens[ATTEN_NAME_FOR_CELL_3G],
                     self.cell_rssi_with_no_atten, MIN_RSSI_RESERVED_VALUE,
                     self.signal_change_step, self.signal_change_interval)
            #gradtually increase cell 3G
            set_rssi(self.log, self.attens[ATTEN_NAME_FOR_CELL_3G],
                     MIN_RSSI_RESERVED_VALUE, MAX_RSSI_RESERVED_VALUE,
                     self.signal_change_step, self.signal_change_interval)

        return 0

    def call_test(self):
        failure = 0
        total_count = 0
        while time.time() < self.finishing_time:
            ads = [self.dut, self.helper]
            random.shuffle(ads)
            total_count += 1
            # Current Voice RAT
            self.dut.log.info("Current Voice RAT is %s",
                              get_current_voice_rat(self.log, self.dut))
            self.helper.log.info("Current Voice RAT is %s",
                              get_current_voice_rat(self.log, self.helper))
            if not self._make_phone_call(ads):
                failure += 1
                self.log.error("New call test failure: %s/%s", failure,
                               total_count)
                self.result_info["Call failure"] += 1
                self._take_bug_report("%s_call_failure" % self.test_name,
                                      time.strftime("%m-%d-%Y-%H-%M-%S"))
            self.dut.droid.goToSleepNow()
            time.sleep(random.randrange(0, self.max_sleep_time))
        return failure

    def message_test(self):
        failure = 0
        total_count = 0
        while time.time() < self.finishing_time:
            ads = [self.dut, self.helper]
            random.shuffle(ads)
            total_count += 1
            if not self._send_message(ads):
                failure += 1
                self.log.error("New messaging test failure: %s/%s", failure,
                               total_count)
                #self._take_bug_report("%s_messaging_failure" % self.test_name,
                #                      time.strftime("%m-%d-%Y-%H-%M-%S"))
            self.dut.droid.goToSleepNow()
            time.sleep(random.randrange(0, self.max_sleep_time))
        return failure

    def data_test(self):
        failure = 0
        #file_names = ["5MB", "10MB", "20MB", "50MB", "200MB", "512MB", "1GB"]
        #wifi download is very slow in lab, limit the file size upto 200MB
        file_names = ["5MB", "10MB", "20MB", "50MB", "200MB"]
        while time.time() < self.finishing_time:
            self.dut.log.info(dict(self.result_info))
            self.result_info["Total file download"] += 1
            selection = random.randrange(0, 5)
            file_name = file_names[selection]
            if not active_file_download_test(self.log, self.dut, file_name):
                self.result_info["%s file download failure" % file_name] += 1
                #self._take_bug_report("%s_download_failure" % self.test_name,
                #                      time.strftime("%m-%d-%Y-%H-%M-%S"))
                failure += 1
                self.dut.droid.goToSleepNow()
                time.sleep(random.randrange(0, self.max_sleep_time))
        return failure

    def parallel_tests(self, change_env_func, setup_func=None):
        if setup_func and not setup_func():
            self.log.error("Test setup %s failed", setup_func.__name__)
            return False
        self.result_info = collections.defaultdict(int)
        self.finishing_time = time.time() + self.max_run_time
        results = run_multithread_func(self.log, [(self.call_test, []), (
            self.message_test, []), (self.data_test, []), (
                change_env_func, []), (self.crash_check_test, [])])
        self.log.info(dict(self.result_info))
        if results[0]:
            fail(str(dict(self.result_info)))
        else:
            return True

    """ Tests Begin """

    @test_tracker_info(uuid="6fcba97c-3572-47d7-bcac-9608f1aa5304")
    @TelephonyBaseTest.tel_test_wrap
    def test_volte_wfc_wifi_preferred_parallel_stress(self):
        return self.parallel_tests(
            self.environment_change_4g_wifi,
            setup_func=self._setup_volte_wfc_wifi_preferred)

    @test_tracker_info(uuid="df78a9a8-2a14-40bf-a7aa-719502f975be")
    @TelephonyBaseTest.tel_test_wrap
    def test_volte_wfc_cell_preferred_parallel_stress(self):
        return self.parallel_tests(
            self.environment_change_4g_wifi,
            setup_func=self._setup_volte_wfc_cell_preferred)

    @test_tracker_info(uuid="4cb47315-c420-44c2-ac47-a8bdca6d0e25")
    @TelephonyBaseTest.tel_test_wrap
    def test_csfb_wfc_wifi_preferred_parallel_stress(self):
        return self.parallel_tests(
            self.environment_change_4g_wifi,
            setup_func=self._setup_csfb_wfc_wifi_preferred)

    @test_tracker_info(uuid="92821ef7-542a-4139-b3b0-22278e2b06c4")
    @TelephonyBaseTest.tel_test_wrap
    def test_csfb_wfc_cell_preferred_parallel_stress(self):
        return self.parallel_tests(
            self.self.environment_change_4g_wifi,
            setup_func=self._setup_csfb_wfc_cell_preferred)

    @test_tracker_info(uuid="dd23923e-ebbc-461e-950a-0657e845eacf")
    @TelephonyBaseTest.tel_test_wrap
    def test_4g_volte_3g_parallel_stress(self):
        return self.parallel_tests(
            self.environment_change_4g_3g,
            setup_func=self._setup_volte_wfc_disabled)

    @test_tracker_info(uuid="faef9384-a5b0-4640-8cfa-f9f34ce6d977")
    @TelephonyBaseTest.tel_test_wrap
    def test_4g_csfb_3g_parallel_stress(self):
        return self.parallel_tests(
            self.environment_change_4g_3g,
            setup_func=self._setup_csfb_wfc_disabled)

    """ Tests End """
