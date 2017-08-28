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
    Test Script for Telephony Stress Call Test
"""

import collections
import random
import time

from acts.asserts import fail
from acts.test_decorators import test_tracker_info
from acts.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts.test_utils.tel.tel_test_utils import active_file_download_test
from acts.test_utils.tel.tel_test_utils import call_setup_teardown
from acts.test_utils.tel.tel_test_utils import ensure_phone_default_state
from acts.test_utils.tel.tel_test_utils import ensure_phone_subscription
from acts.test_utils.tel.tel_test_utils import ensure_phone_idle
from acts.test_utils.tel.tel_test_utils import ensure_wifi_connected
from acts.test_utils.tel.tel_test_utils import hangup_call
from acts.test_utils.tel.tel_test_utils import initiate_call
from acts.test_utils.tel.tel_test_utils import is_phone_in_call
from acts.test_utils.tel.tel_test_utils import run_multithread_func
from acts.test_utils.tel.tel_test_utils import set_wfc_mode
from acts.test_utils.tel.tel_test_utils import sms_send_receive_verify
from acts.test_utils.tel.tel_test_utils import mms_send_receive_verify
from acts.test_utils.tel.tel_test_utils import verify_incall_state
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


class TelLiveSinglePhoneStressTest(TelephonyBaseTest):
    def setup_class(self):
        super(TelLiveSinglePhoneStressTest, self).setup_class()
        self.dut = self.android_devices[0]
        self.call_server_number = self.user_params.get("call_server_number",
                                                       "9523521350")
        self.user_params["telephony_auto_rerun"] = False
        self.wifi_network_ssid = self.user_params.get(
            "wifi_network_ssid") or self.user_params.get("wifi_network_ssid_2g")
        self.wifi_network_pass = self.user_params.get(
            "wifi_network_pass") or self.user_params.get("wifi_network_pass_2g")
        self.max_phone_call_duration = int(
            self.user_params.get("max_phone_call_duration", 3600))
        self.max_sleep_time = int(self.user_params.get("max_sleep_time", 1200))
        self.max_run_time = int(self.user_params.get("max_run_time", 18000))
        self.max_sms_length = int(self.user_params.get("max_sms_length", 1000))
        self.max_mms_length = int(self.user_params.get("max_mms_length", 160))
        self.crash_check_interval = int(
            self.user_params.get("crash_check_interval", 300))

        return True

    def _setup_wfc(self):
        if not ensure_wifi_connected(
                self.log,
                self.dut,
                self.wifi_network_ssid,
                self.wifi_network_pass,
                retry=3):
            self.dut.log.error("Phone Wifi connection fails.")
            return False
        self.dut.log.info("Phone WIFI is connected successfully.")
        if not set_wfc_mode(self.log, self.dut, WFC_MODE_WIFI_PREFERRED):
            self.dut.log.error("Phone failed to enable Wifi-Calling.")
            return False
        self.dut.log.info("Phone is set in Wifi-Calling successfully.")
        if not phone_idle_iwlan(self.log, self.dut):
            self.dut.log.error("Phone is not in WFC enabled state.")
            return False
        self.dut.log.info("Phone is in WFC enabled state.")
        return True

    def _setup_lte_volte_enabled(self):
        if not phone_setup_volte(self.log, self.dut):
            self.dut.log.error("Phone failed to enable VoLTE.")
            return False
        self.dut.log.info("Phone VOLTE is enabled successfully.")
        return True

    def _setup_lte_volte_disabled(self):
        if not phone_setup_csfb(self.log, self.dut):
            self.dut.log.error("Phone failed to setup CSFB.")
            return False
        self.dut.log.info("Phone VOLTE is disabled successfully.")
        return True

    def _setup_3g(self):
        if not phone_setup_voice_3g(self.log, self.dut):
            self.dut.log.error("Phone failed to setup 3g.")
            return False
        self.dut.log.info("Phone RAT 3G is enabled successfully.")
        return True

    def _setup_2g(self):
        if not phone_setup_voice_2g(self.log, self.dut):
            self.dut.log.error("Phone failed to setup 2g.")
            return False
        self.dut.log.info("RAT 2G is enabled successfully.")
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

    def call_test(self):
        failure = 0
        while time.time() < self.finishing_time:
            self.dut.log.info(dict(self.result_info))
            self.result_info["Total Calls"] += 1
            duration = random.randrange(1, self.max_phone_call_duration)
            # Current Voice RAT
            self.dut.log.info("Current Voice RAT is %s",
                              get_current_voice_rat(self.log, self.dut))
            self.dut.log.info("Make call to %s with call duration %s",
                              self.call_server_number, duration)
            if not initiate_call(self.log, self.dut, self.call_server_number):
                self.dut.log.error("Initiate phone call to %s failed.",
                                   self.call_server_number)
                self.result_info["Call initiation failure"] += 1
                self._take_bug_report(
                    "%s_call_initiation_failure" % self.test_name,
                    time.strftime("%m-%d-%Y-%H-%M-%S"))
                continue
            elapse_time = 0
            interval = min(60, duration)
            while elapse_time < duration:
                interval = min(duration - elapse_time, interval)
                time.sleep(interval)
                elapse_time += interval
                if not is_phone_in_call(self.log, self.dut):
                    self.dut.log.error("Call droped.")
                    self.result_info["Call drop"] += 1
                    self._take_bug_report("%s_call_drop" % self.test_name,
                                          time.strftime("%m-%d-%Y-%H-%M-%S"))
                    break
                else:
                    self.dut.log.info("DUT is in call")
            else:
                hangup_call(self.log, self.dut)
                self.dut.log.info("Call test succeed.")
                ensure_phone_idle(self.log, self.dut)
                self.dut.droid.goToSleepNow()
                time.sleep(random.randrange(0, self.max_sleep_time))
        return failure

    def message_test(self):
        failure = 0
        total_count = 0
        message_type_map = {0: "SMS", 1: "MMS"}
        max_length_map = {0: self.max_sms_length, 1: self.max_mms_length}
        message_func_map = {
            0: sms_send_receive_verify,
            1: mms_send_receive_verify
        }
        while time.time() < self.finishing_time:
            self.dut.log.info(dict(self.result_info))
            total_count += 1
            selection = random.randrange(0, 2)
            message_type = message_type_map[selection]
            self.result_info["Total %s" % message_type] += 1
            length = random.randrange(0, max_length_map[selection] + 1)
            text = rand_ascii_str(length)
            message_content_map = {0: [text], 1: [("Mms Message", text, None)]}
            if not message_func_map[selection](self.log, self.dut, self.dut,
                                               message_content_map[selection]):
                self.log.error("%s of length %s from self to self fails",
                               message_type, length)
                self.result_info["%s failure" % message_type] += 1
                #self._take_bug_report("%s_messaging_failure" % self.test_name,
                #                      time.strftime("%m-%d-%Y-%H-%M-%S"))
                failure += 1
            else:
                self.dut.log.info("%s of length %s from self to self succeed",
                                  message_type, length)
            self.dut.droid.goToSleepNow()
            time.sleep(random.randrange(0, self.max_sleep_time))
        return failure

    def data_test(self):
        failure = 0
        total_count = 0
        file_names = ["5MB", "10MB", "20MB", "50MB", "200MB", "512MB", "1GB"]
        while time.time() < self.finishing_time:
            self.dut.log.info(dict(self.result_info))
            self.result_info["Total file download"] += 1
            selection = random.randrange(0, 7)
            file_name = file_names[selection]
            if not active_file_download_test(self.log, self.dut, file_name):
                self.result_info["%s file download failure" % file_name] += 1
                #self._take_bug_report("%s_download_failure" % self.test_name,
                #                      time.strftime("%m-%d-%Y-%H-%M-%S"))
                self.dut.droid.goToSleepNow()
                time.sleep(random.randrange(0, self.max_sleep_time))
        return failure

    def parallel_tests(self, setup_func=None):
        if setup_func and not setup_func():
            self.log.error("Test setup %s failed", setup_func.__name__)
            return False
        self.result_info = collections.defaultdict(int)
        self.finishing_time = time.time() + self.max_run_time
        results = run_multithread_func(self.log, [(self.call_test, []), (
            self.message_test, []), (self.data_test, []),
                                                  (self.crash_check_test, [])])
        self.log.info(dict(self.result_info))
        if sum(results):
            fail(str(dict(self.result_info)))

    """ Tests Begin """

    @test_tracker_info(uuid="d035e5b9-476a-4e3d-b4e9-6fd86c51a68d")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_parallel_stress(self):
        """ Default state stress test"""
        return self.parallel_tests()

    @test_tracker_info(uuid="c21e1f17-3282-4f0b-b527-19f048798098")
    @TelephonyBaseTest.tel_test_wrap
    def test_lte_volte_parallel_stress(self):
        """ VoLTE on stress test"""
        return self.parallel_tests(setup_func=self._setup_lte_volte_enabled)

    @test_tracker_info(uuid="a317c23a-41e0-4ef8-af67-661451cfefcf")
    @TelephonyBaseTest.tel_test_wrap
    def test_csfb_parallel_stress(self):
        """ LTE non-VoLTE stress test"""
        return self.parallel_tests(setup_func=self._setup_lte_volte_disabled)

    @test_tracker_info(uuid="fdb791bf-c414-4333-9fa3-cc18c9b3b234")
    @TelephonyBaseTest.tel_test_wrap
    def test_wfc_parallel_stress(self):
        """ Wifi calling on stress test"""
        return self.parallel_tests(setup_func=self._setup_wfc)

    @test_tracker_info(uuid="4566eef6-55de-4ac8-87ee-58f2ef41a3e8")
    @TelephonyBaseTest.tel_test_wrap
    def test_3g_parallel_stress(self):
        """ 3G stress test"""
        return self.parallel_tests(setup_func=self._setup_3g)

    @test_tracker_info(uuid="f34f1a31-3948-4675-8698-372a83b8088d")
    @TelephonyBaseTest.tel_test_wrap
    def test_call_2g_parallel_stress(self):
        """ 2G call stress test"""
        return self.parallel_tests(setup_func=self._setup_2g)

    """ Tests End """
