#!/usr/bin/env python3.4
#
#   Copyright 2016 - The Android Open Source Project
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

import time

from acts import asserts
from acts import base_test
from acts.test_decorators import test_tracker_info
from acts.test_utils.wifi import wifi_constants
from acts.test_utils.wifi import wifi_test_utils as wutils
from acts.test_utils.wifi.WifiBaseTest import WifiBaseTest

WifiEnums = wutils.WifiEnums
NETWORK_ID_ERROR = "Network don't have ID"
NETWORK_ERROR = "Device is not connected to reference network"


class WifiNewSetupAutoJoinTest(WifiBaseTest):
    def __init__(self, controllers):
        WifiBaseTest.__init__(self, controllers)
        self.tests = ("test_autojoin_out_of_range",
                      "test_autojoin_Ap1_2g",
                      "test_autojoin_Ap1_2gto5g",
                      "test_autojoin_in_AP1_5gto2g",
                      "test_autojoin_swtich_AP1toAp2",
                      "test_autojoin_Ap2_2gto5g",
                      "test_autojoin_Ap2_5gto2g",
                      "test_autojoin_out_of_range",
                      "test_autojoin_Ap2_2g",
                      "test_autojoin_Ap2_2gto5g",
                      "test_autojoin_in_Ap2_5gto2g",
                      "test_autojoin_swtich_AP2toAp1",
                      "test_autojoin_Ap1_2gto5g",
                      "test_autojoin_Ap1_5gto2g", )

    def setup_class(self):
        """It will setup the required dependencies from config file and configure
           the required networks for auto-join testing. Configured networks will
           not be removed. If networks are already configured it will skip
           configuring the networks

        Returns:
            True if successfully configured the requirements for testing.
        """
        self.dut = self.android_devices[0]
        wutils.wifi_test_device_init(self.dut)
        req_params = ("atten_val", "ping_addr", "max_bugreports")
        opt_param = ["reference_networks"]
        self.unpack_userparams(
            req_param_names=req_params, opt_param_names=opt_param)

        if "AccessPoint" in self.user_params:
            self.legacy_configure_ap_and_start(ap_count=2)

        configured_networks = self.dut.droid.wifiGetConfiguredNetworks()
        self.log.debug("Configured networks :: {}".format(configured_networks))
        count_confnet = 0
        result = False
        if self.reference_networks[0]['2g']['SSID'] == self.reference_networks[
                0]['5g']['SSID']:
            self.ref_ssid_count = 1
        else:
            self.ref_ssid_count = 2  # Different SSID for 2g and 5g
        for confnet in configured_networks:
            if confnet[WifiEnums.SSID_KEY] == self.reference_networks[0]['2g'][
                    'SSID']:
                count_confnet += 1
            elif confnet[WifiEnums.SSID_KEY] == self.reference_networks[0][
                    '5g']['SSID']:
                count_confnet += 1
        self.log.info("count_confnet {}".format(count_confnet))
        if count_confnet == self.ref_ssid_count:
            return
        else:
            self.log.info("Configured networks for testing")
            self.attenuators[0].set_atten(0)
            self.attenuators[1].set_atten(0)
            self.attenuators[2].set_atten(90)
            self.attenuators[3].set_atten(90)
            wait_time = 15
            self.dut.droid.wakeLockAcquireBright()
            self.dut.droid.wakeUpNow()
            try:
                self.dut.droid.wifiConnectByConfig(self.reference_networks[0][
                    '2g'])
                connect_result = self.dut.ed.pop_event(
                    wifi_constants.CONNECT_BY_CONFIG_SUCCESS, 1)
                self.log.info(connect_result)
                time.sleep(wait_time)
                if self.ref_ssid_count == 2:  #add 5g network as well
                    self.dut.droid.wifiConnectByConfig(self.reference_networks[
                        0]['5g'])
                    connect_result = self.dut.ed.pop_event(
                        wifi_constants.CONNECT_BY_CONFIG_SUCCESS, 1)
                    self.log.info(connect_result)
                    time.sleep(wait_time)
                current_network = self.dut.droid.wifiGetConnectionInfo()
                self.log.info("Current network: {}".format(current_network))
                asserts.assert_true('network_id' in current_network,
                                    NETWORK_ID_ERROR)
                asserts.assert_true(current_network['network_id'] >= 0,
                                    NETWORK_ERROR)
            finally:
                self.dut.droid.wifiLockRelease()
                self.dut.droid.goToSleepNow()

    def check_connection(self, network_bssid):
        """Check current wifi connection networks.
        Args:
            network_bssid: Network bssid to which connection.
        Returns:
            True if connection to given network happen, else return False.
        """
        time.sleep(40)  #time for connection state to be updated
        self.log.info("Check network for {}".format(network_bssid))
        current_network = self.dut.droid.wifiGetConnectionInfo()
        self.log.debug("Current network:  {}".format(current_network))
        if WifiEnums.BSSID_KEY in current_network:
            return current_network[WifiEnums.BSSID_KEY] == network_bssid
        return False

    def set_attn_and_validate_connection(self, attn_value, bssid):
        """Validate wifi connection status on different attenuation setting.

        Args:
            attn_value: Attenuation value for different APs signal.
            bssid: Bssid of excepted network.

        Returns:
            True if bssid of current network match, else false.
        """
        self.attenuators[0].set_atten(attn_value[0])
        self.attenuators[1].set_atten(attn_value[1])
        self.attenuators[2].set_atten(attn_value[2])
        self.attenuators[3].set_atten(attn_value[3])
        self.dut.droid.wakeLockAcquireBright()
        self.dut.droid.wakeUpNow()
        try:
            asserts.assert_true(
                self.check_connection(bssid),
                "Device is not connected to required bssid {}".format(bssid))
            time.sleep(10)  #wait for connection to be active
            asserts.assert_true(
                wutils.validate_connection(self.dut, self.ping_addr),
                "Error, No Internet connection for current bssid {}".format(
                    bssid))
        finally:
            self.dut.droid.wifiLockRelease()
            self.dut.droid.goToSleepNow()

    def on_fail(self, test_name, begin_time):
        if self.max_bugreports > 0:
            self.dut.take_bug_report(test_name, begin_time)
            self.max_bugreports -= 1
        self.dut.cat_adb_log(test_name, begin_time)

    """ Tests Begin """

    @test_tracker_info(uuid="9ea2c78d-d305-497f-87a5-f621f0a4b34e")
    def test_autojoin_Ap1_2g(self):
        """Test wifi auto join functionality move in range of AP1.

         1. Attenuate the signal to low range of AP1 and Ap2 not visible at all.
         2. Wake up the device.
         3. Check that device is connected to right BSSID and maintain stable
            connection to BSSID in range.
        """
        att0, att1, att2, att3 = self.atten_val["Ap1_2g"]
        variance = 5
        attenuations = ([att0 + variance * 2, att1, att2, att3],
                        [att0 + variance, att1, att2, att3], [att0, att1, att2, att3],
                        [att0 - variance, att1, att2, att3])
        name_func = lambda att_value, bssid: ("test_autojoin_Ap1_2g_AP1_{}_AP2"
                                              "_{}_AP3_{}").format(att_value[0], att_value[1], att_value[2])
        failed = self.run_generated_testcases(
            self.set_attn_and_validate_connection,
            attenuations,
            args=(self.reference_networks[0]["2g"]['bssid'], ),
            name_func=name_func)
        asserts.assert_false(
            failed,
            "Number of test_autojoin_Ap1_2g failed {}".format(len(failed)))

    @test_tracker_info(uuid="b5eba5ec-96e5-4bd8-b483-f5b2a9504558")
    def test_autojoin_Ap1_2gto5g(self):
        """Test wifi auto join functionality move to high range.

         1. Attenuate the signal to high range of AP1.
         2. Wake up the device.
         3. Check that device is connected to right BSSID and maintain stable
            connection to BSSID in range.
        """
        att0, att1, att2, attn3 = self.atten_val["Ap1_2gto5g"]
        variance = 5
        attenuations = ([att0 + variance * 2, att1, att2, attn3],
                        [att0 + variance, att1, att2, attn3], [att0, att1, att2, attn3])
        name_func = lambda att_value, bssid: ("test_autojoin_Ap1_2gto5g_AP1_{}_AP2"
                                              "_{}_AP3_{}").format(att_value[0], att_value[1], att_value[2])
        failed = self.run_generated_testcases(
            self.set_attn_and_validate_connection,
            attenuations,
            args=(self.reference_networks[0]["5g"]['bssid'], ),
            name_func=name_func)
        asserts.assert_false(
            failed,
            "Number of test_autojoin_Ap1_2gto5g failed {}".format(len(failed)))

    @test_tracker_info(uuid="37822578-d35c-462c-87c0-7a2d9252938c")
    def test_autojoin_in_AP1_5gto2g(self):
        """Test wifi auto join functionality move to low range toward AP2.

         1. Attenuate the signal to medium range of AP1 and low range of AP2.
         2. Wake up the device.
         3. Check that device is connected to right BSSID and maintain stable
            connection to BSSID in range.
        """
        att0, att1, att2, attn3 = self.atten_val["In_AP1_5gto2g"]
        variance = 5
        attenuations = ([att0 - variance, att1 + variance, att2, attn3],
                        [att0, att1, att2, attn3],
                        [att0 + variance, att1 - variance, att2, attn3])
        name_func = lambda att_value, bssid: ("test_autojoin_in_AP1_5gto2g_AP1_{}_AP2"
                                              "_{}_AP3_{}").format(att_value[0], att_value[1], att_value[2])
        failed = self.run_generated_testcases(
            self.set_attn_and_validate_connection,
            attenuations,
            args=(self.reference_networks[0]["2g"]['bssid'], ),
            name_func=name_func)
        asserts.assert_false(
            failed, "Number of test_autojoin_in_AP1_5gto2g failed {}".format(
                len(failed)))

    @test_tracker_info(uuid="8ffdcab1-2bfb-4acd-b1e8-089ba8a4ec41")
    def test_autojoin_swtich_AP1toAp2(self):
        """Test wifi auto join functionality move from low range of AP1 to better
           range of AP2.

         1. Attenuate the signal to low range of AP1 and medium range of AP2.
         2. Wake up the device.
         3. Check that device is connected to right BSSID and maintain stable
            connection to BSSID in range.
        """
        att0, att1, att2, attn3 = self.atten_val["Swtich_AP1toAp2"]
        variance = 5
        attenuations = ([att0 - variance, att1 + variance, att2, attn3],
                        [att0, att1, att2, attn3],
                        [att0 + variance, att1 - variance, att2, attn3])
        name_func = lambda att_value, bssid: ("test_autojoin_swtich_AP1toAp2_AP1_{}_AP2"
                                              "_{}_AP3_{}").format(att_value[0], att_value[1], att_value[2])
        failed = self.run_generated_testcases(
            self.set_attn_and_validate_connection,
            attenuations,
            args=(self.reference_networks[1]["2g"]['bssid'], ),
            name_func=name_func)
        asserts.assert_false(
            failed, "Number of test_autojoin_swtich_AP1toAp2 failed {}".format(
                len(failed)))

    @test_tracker_info(uuid="7a8b9242-f93c-449a-90a6-4562274a213a")
    def test_autojoin_Ap2_2gto5g(self):
        """Test wifi auto join functionality move to high range of AP2.

         1. Attenuate the signal to out range of AP1 and high range of AP2.
         2. Wake up the device.
         3. Check that device is connected to right BSSID and maintain stable
            connection to BSSID in range.
        """
        att0, att1, att2, attn3 = self.atten_val["Ap2_2gto5g"]
        variance = 5
        attenuations = ([att0 - variance, att1 + variance * 2, att2, attn3],
                        [att0, att1 + variance, att2, attn3], [att0, att1, att2, attn3])
        name_func = lambda att_value, bssid: ("test_autojoin_Ap2_2gto5g_AP1_{}_AP2"
                                              "_{}_AP3_{}").format(att_value[0], att_value[1], att_value[2])
        failed = self.run_generated_testcases(
            self.set_attn_and_validate_connection,
            attenuations,
            args=(self.reference_networks[1]["5g"]['bssid'], ),
            name_func=name_func)
        asserts.assert_false(
            failed,
            "Number of test_autojoin_Ap2_2gto5g failed {}".format(len(failed)))

    @test_tracker_info(uuid="009457df-f430-402c-96ab-c456b043b6f5")
    def test_autojoin_Ap2_5gto2g(self):
        """Test wifi auto join functionality move to low range of AP2.

         1. Attenuate the signal to low range of AP2.
         2. Wake up the device.
         3. Check that device is connected to right BSSID and maintain stable.
        """
        att0, att1, att2, attn3 = self.atten_val["Ap2_5gto2g"]
        variance = 5
        attenuations = ([att0, att1 - variance, att2, attn3], [att0, att1, att2, attn3],
                        [att0, att1 + variance, att2, attn3])
        name_func = lambda att_value, bssid: ("test_autojoin_Ap2_5gto2g_AP1_{}_AP2"
                                              "_{}_AP3_{}").format(att_value[0], att_value[1], att_value[2])
        failed = self.run_generated_testcases(
            self.set_attn_and_validate_connection,
            attenuations,
            args=(self.reference_networks[1]["2g"]['bssid'], ),
            name_func=name_func)
        asserts.assert_false(
            failed,
            "Number of test_autojoin_Ap2_5gto2g failed {}".format(len(failed)))

    @test_tracker_info(uuid="c6d070af-b601-42f1-adec-5ac564154b29")
    def test_autojoin_out_of_range(self):
        """Test wifi auto join functionality move to low range.

         1. Attenuate the signal to out of range.
         2. Wake up the device.
         3. Start the scan.
         4. Check that device is not connected to any network.
        """
        self.attenuators[0].set_atten(90)
        self.attenuators[1].set_atten(90)
        self.attenuators[2].set_atten(90)
        self.attenuators[3].set_atten(90)
        self.dut.droid.wakeLockAcquireBright()
        self.dut.droid.wakeUpNow()
        try:
            wutils.start_wifi_connection_scan(self.dut)
            wifi_results = self.dut.droid.wifiGetScanResults()
            self.log.debug("Scan result {}".format(wifi_results))
            time.sleep(20)
            current_network = self.dut.droid.wifiGetConnectionInfo()
            self.log.info("Current network: {}".format(current_network))
            asserts.assert_true(
                ('network_id' in current_network and
                 current_network['network_id'] == -1),
                "Device is connected to network {}".format(current_network))
        finally:
            self.dut.droid.wifiLockRelease()
            self.dut.droid.goToSleepNow()

    @test_tracker_info(uuid="15c27654-bae0-4d2d-bdc8-54fb04b901d1")
    def test_autojoin_Ap2_2g(self):
        """Test wifi auto join functionality move in low range of AP2.

         1. Attenuate the signal to move in range of AP2 and Ap1 not visible at all.
         2. Wake up the device.
         3. Check that device is connected to right BSSID and maintain stable
            connection to BSSID in range.
        """
        att0, att1, att2, attn3 = self.atten_val["Ap2_2g"]
        variance = 5
        attenuations = ([att0, att1 + variance * 2, att2, attn3],
                        [att0, att1 + variance, att2, attn3], [att0, att1, att2, attn3],
                        [att0, att1 - variance, att2, attn3])
        name_func = lambda att_value, bssid: ("test_autojoin_Ap2_2g_AP1_{}_AP2"
                                              "_{}_AP3_{}").format(att_value[0], att_value[1], att_value[2])
        failed = self.run_generated_testcases(
            self.set_attn_and_validate_connection,
            attenuations,
            args=(self.reference_networks[1]["2g"]['bssid'], ),
            name_func=name_func)
        asserts.assert_false(
            failed,
            "Number of test_autojoin_Ap2_2g failed {}".format(len(failed)))

    @test_tracker_info(uuid="80e74c78-59e2-46db-809d-cb03bd1b6824")
    def test_autojoin_in_Ap2_5gto2g(self):
        """Test wifi auto join functionality move to medium range of Ap2 and
           low range of AP1.

         1. Attenuate the signal to move in medium range of AP2 and low range of AP1.
         2. Wake up the device.
         3. Check that device is connected to right BSSID and maintain stable
            connection to BSSID in range.
        """
        att0, att1, att2, attn3 = self.atten_val["In_Ap2_5gto2g"]
        variance = 5
        attenuations = ([att0, att1 - variance, att2, attn3], [att0, att1, att2, attn3],
                        [att0, att1 + variance, att2, attn3])
        name_func = lambda att_value, bssid: ("test_autojoin_in_Ap2_5gto2g_AP1_{}_AP2"
                                              "_{}_AP3_{}").format(att_value[0], att_value[1], att_value[2])
        failed = self.run_generated_testcases(
            self.set_attn_and_validate_connection,
            attenuations,
            args=(self.reference_networks[1]["2g"]['bssid'], ),
            name_func=name_func)
        asserts.assert_false(
            failed, "Number of test_autojoin_in_Ap2_5gto2g failed {}".format(
                len(failed)))

    @test_tracker_info(uuid="01faeba0-bd66-4d30-a3d9-b81e959025b2")
    def test_autojoin_swtich_AP2toAp1(self):
        """Test wifi auto join functionality move from low range of AP2 to better
           range of AP1.

         1. Attenuate the signal to low range of AP2 and medium range of AP1.
         2. Wake up the device.
         3. Check that device is connected to right BSSID and maintain stable
            connection to BSSID in range.
        """
        att0, att1, att2, attn3 = self.atten_val["Swtich_AP2toAp1"]
        variance = 5
        attenuations = ([att0 + variance, att1 - variance, att2, attn3],
                        [att0, att1, att2, attn3],
                        [att0 - variance, att1 + variance, att2, attn3])
        name_func = lambda att_value, bssid: ("test_autojoin_swtich_AP2toAp1_AP1_{}_AP2"
                                              "_{}_AP3_{}").format(att_value[0], att_value[1], att_value[2])
        failed = self.run_generated_testcases(
            self.set_attn_and_validate_connection,
            attenuations,
            args=(self.reference_networks[0]["2g"]['bssid'], ),
            name_func=name_func)
        asserts.assert_false(
            failed, "Number of test_autojoin_swtich_AP2toAp1 failed {}".format(
                len(failed)))

    @test_tracker_info(uuid="ec509d40-e339-47c2-995e-cc77f5a28687")
    def test_autojoin_Ap1_5gto2g(self):
        """Test wifi auto join functionality move to medium range of AP1.

         1. Attenuate the signal to medium range of AP1.
         2. Wake up the device.
         3. Check that device is connected to right BSSID and maintain stable
            connection to BSSID in range.
        """
        att0, att1, att2, attn3 = self.atten_val["Ap1_5gto2g"]
        variance = 5
        attenuations = ([att0, att1, att2, attn3], [att0 + variance, att1, att2, attn3],
                        [att0 + variance * 2, att1, att2, attn3])
        name_func = lambda att_value, bssid: ("test_autojoin_Ap1_5gto2g_AP1_{}_AP2"
                                              "_{}_AP3_{}").format(att_value[0], att_value[1], att_value[2])
        failed = self.run_generated_testcases(
            self.set_attn_and_validate_connection,
            attenuations,
            args=(self.reference_networks[0]["2g"]['bssid'], ),
            name_func=name_func)
        asserts.assert_false(
            failed,
            "Number of test_autojoin_Ap1_5gto2g failed {}".format(len(failed)))
    """ Tests End """
