#!/usr/bin/env python3.4
#
#   Copyright 2017 - The Android Open Source Project
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

import itertools
import pprint
import time

import acts.signals
import acts.test_utils.wifi.wifi_test_utils as wutils

from acts import asserts
from acts.test_decorators import test_tracker_info
from acts.test_utils.wifi.WifiBaseTest import WifiBaseTest

WifiEnums = wutils.WifiEnums


class WifiIOTTest(WifiBaseTest):
    """ Tests for wifi IOT

        Test Bed Requirement:
          * One Android device
          * Wi-Fi IOT networks visible to the device
    """

    def __init__(self, controllers):
        WifiBaseTest.__init__(self, controllers)

    def setup_class(self):
        self.dut = self.android_devices[0]
        wutils.wifi_test_device_init(self.dut)

        req_params = [ "iot_networks", "open_network", "iperf_server_address" ]
        self.unpack_userparams(req_param_names=req_params)

        asserts.assert_true(
            len(self.iot_networks) > 0,
            "Need at least one iot network with psk.")
        self.iot_networks.extend(self.open_network)

        wutils.wifi_toggle_state(self.dut, True)
        if "iperf_server_address" in self.user_params:
            self.iperf_server = self.iperf_servers[0]
        self.iot_test_prefix = "test_connection_to-"

    def setup_test(self):
        self.dut.droid.wakeLockAcquireBright()
        self.dut.droid.wakeUpNow()
        if self.iot_test_prefix in self.current_test_name:
            if "iperf_server_address" in self.user_params:
                self.iperf_server.start()

    def teardown_test(self):
        self.dut.droid.wakeLockRelease()
        self.dut.droid.goToSleepNow()
        wutils.reset_wifi(self.dut)
        if self.current_test_name and self.iot_test_prefix in self.current_test_name:
            if "iperf_server_address" in self.user_params:
                self.iperf_server.stop()

    def on_fail(self, test_name, begin_time):
        self.dut.take_bug_report(test_name, begin_time)
        self.dut.cat_adb_log(test_name, begin_time)

    """Helper Functions"""

    def connect_to_wifi_network(self, params):
        """Connection logic for open and psk wifi networks.

        Args:
            params: A tuple of network info and AndroidDevice object.
        """
        network, ad = params
        droid = ad.droid
        ed = ad.ed
        SSID = network[WifiEnums.SSID_KEY]
        ed.clear_all_events()
        wutils.start_wifi_connection_scan(ad)
        scan_results = droid.wifiGetScanResults()
        wutils.assert_network_in_list({WifiEnums.SSID_KEY: SSID}, scan_results)
        wutils.wifi_connect(ad, network, num_of_tries=3)

    def run_iperf_client(self, params):
        """Run iperf traffic after connection.

        Args:
            params: A tuple of network info and AndroidDevice object.
        """
        if "iperf_server_address" in self.user_params:
            wait_time = 5
            network, ad = params
            SSID = network[WifiEnums.SSID_KEY]
            self.log.info("Starting iperf traffic through {}".format(SSID))
            time.sleep(wait_time)
            port_arg = "-p {}".format(self.iperf_server.port)
            success, data = ad.run_iperf_client(self.iperf_server_address,
                                                port_arg)
            self.log.debug(pprint.pformat(data))
            asserts.assert_true(success, "Error occurred in iPerf traffic.")

    def connect_to_wifi_network_and_run_iperf(self, params):
        """Connection logic for open and psk wifi networks.

        Logic steps are
        1. Connect to the network.
        2. Run iperf traffic.

        Args:
            params: A tuple of network info and AndroidDevice object.
        """
        self.connect_to_wifi_network(params)
        self.run_iperf_client(params)

    """Tests"""

    @test_tracker_info(uuid="cfc0084d-8fe4-4d19-8af2-6c9a8d1e2b6b")
    @acts.signals.generated_test
    def test_iot_with_password(self):
        params = list(
            itertools.product(self.iot_networks, self.android_devices))
        name_gen = lambda p: "test_connection_to-%s" % p[0][WifiEnums.SSID_KEY]
        failed = self.run_generated_testcases(
            self.connect_to_wifi_network_and_run_iperf,
            params,
            name_func=name_gen)
        asserts.assert_true(not failed, "Failed ones: {}".format(failed))
