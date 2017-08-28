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

import logging
import queue
import time

from acts import asserts
from acts import utils
from acts import base_test
from acts.test_decorators import test_tracker_info
from acts.test_utils.tel import tel_defines
from acts.test_utils.tel import tel_test_utils as tel_utils
from acts.test_utils.wifi import wifi_test_utils as wutils


class WifiSoftApTest(base_test.BaseTestClass):

    def setup_class(self):
        """It will setup the required dependencies from config file and configure
           the devices for softap mode testing.

        Returns:
            True if successfully configured the requirements for testing.
        """
        self.dut = self.android_devices[0]
        self.dut_client = self.android_devices[1]
        # Do a simple version of init - mainly just sync the time and enable
        # verbose logging.  This test will fail if the DUT has a sim and cell
        # data is disabled.  We would also like to test with phones in less
        # constrained states (or add variations where we specifically
        # constrain).
        utils.require_sl4a((self.dut, self.dut_client))
        utils.sync_device_time(self.dut)
        utils.sync_device_time(self.dut_client)

        # Enable verbose logging on the duts
        self.dut.droid.wifiEnableVerboseLogging(1)
        asserts.assert_equal(self.dut.droid.wifiGetVerboseLoggingLevel(), 1,
            "Failed to enable WiFi verbose logging on the softap dut.")
        self.dut_client.droid.wifiEnableVerboseLogging(1)
        asserts.assert_equal(self.dut_client.droid.wifiGetVerboseLoggingLevel(), 1,
            "Failed to enable WiFi verbose logging on the client dut.")

    def teardown_class(self):
        wutils.reset_wifi(self.dut)
        wutils.reset_wifi(self.dut_client)

    def on_fail(self, test_name, begin_time):
        self.dut.take_bug_report(test_name, begin_time)

    """ Helper Functions """
    def verify_return_to_wifi_enabled(self):
        """Verifies that wifi is enabled

        We consider wifi as enabled if two things have happened.  First, supplicant
        is started correctly (seen with the supplicant connection change).  Second,
        supplicant should initially enter the disconnected state.
        """
        curr_state = "waiting for supplicant to come back up"
        try:
            self.dut.droid.wifiStartTrackingStateChange()
            event = self.dut.ed.pop_event("SupplicantConnectionChanged", 10)
        except queue.Empty:
            self.log.exception("Failed to restart wifi: current state = %s",
                               curr_state)
            asserts.fail(curr_state)
        finally:
            self.dut.droid.wifiStopTrackingStateChange()

        #TODO(silberst): uncomment and remove loop below when b/30037819 is fixed
        #curr_state = "waiting for wifi to go back into connect mode"
        #try:
        #    self.dut.droid.wifiStartTrackingStateChange()
        #    event = self.dut.ed.pop_event("WifiNetworkDisconnected", 10)
        #    self.dut.droid.wifiStopTrackingStateChange()
        #except queue.Empty:
        #    self.log.exception("Failed to restart wifi: current state = %s",                                                                                 curr_state)
        #    asserts.fail(curr_state)
        attempt_count = 0
        max_attempts = 3
        while attempt_count < max_attempts:
            if not self.dut.droid.wifiCheckState():
                attempt_count += 1
                time.sleep(5)
            else:
                return
        asserts.fail("failed waiting for wifi to return to connect mode")

    def create_softap_config(self):
        """Create a softap config with ssid and password."""
        ap_ssid = "softap_" + utils.rand_ascii_str(8)
        ap_password = utils.rand_ascii_str(8)
        self.dut.log.info("softap setup: %s %s", ap_ssid, ap_password)
        config = {wutils.WifiEnums.SSID_KEY: ap_ssid}
        config[wutils.WifiEnums.PWD_KEY] = ap_password
        return config

    def confirm_softap_in_scan_results(self, ap_ssid):
        """Confirm the ap started by wifi tethering is seen in scan results.

        Args:
            ap_ssid: SSID of the ap we are looking for.
        """
        #TODO(silberst): debug and remove the extra scan before submitting this test
        wutils.start_wifi_connection_scan(self.dut_client)
        client_scan_results = self.dut_client.droid.wifiGetScanResults()
        wutils.start_wifi_connection_scan(self.dut_client)
        client_scan_results = self.dut_client.droid.wifiGetScanResults()
        for result in client_scan_results:
            self.dut.log.debug("scan found: %s", result[wutils.WifiEnums.SSID_KEY])
        wutils.assert_network_in_list({wutils.WifiEnums.SSID_KEY: ap_ssid},
                                      client_scan_results)

    def check_cell_data_and_enable(self):
        """Make sure that cell data is enabled if there is a sim present.

        If a sim is active, cell data needs to be enabled to allow provisioning
        checks through (when applicable).  This is done to relax hardware
        requirements on DUTs - without this check, running this set of tests
        after other wifi tests may cause failures.
        """
        # We do have a sim.  Make sure data is enabled so we can tether.
        if not self.dut.droid.telephonyIsDataEnabled():
            self.dut.log.info("need to enable data")
            self.dut.droid.telephonyToggleDataConnection(True)
            asserts.assert_true(self.dut.droid.telephonyIsDataEnabled(),
                                "Failed to enable cell data for softap dut.")


    """ Tests Begin """

    @test_tracker_info(uuid="495f1252-e440-461c-87a7-2c45f369e129")
    def test_check_wifi_tethering_supported(self):
        """Test check for wifi tethering support.

         1. Call method to check if wifi hotspot is supported
        """
        # TODO(silberst): wifiIsPortableHotspotSupported() is currently failing.
        # Remove the extra check and logging when b/30800811 is resolved
        hotspot_supported = self.dut.droid.wifiIsPortableHotspotSupported()
        tethering_supported = self.dut.droid.connectivityIsTetheringSupported()
        self.log.info(
            "IsPortableHotspotSupported: %s, IsTetheringSupported %s." % (
            hotspot_supported, tethering_supported))
        asserts.assert_true(hotspot_supported,
                            "DUT should support wifi tethering but is reporting false.")
        asserts.assert_true(tethering_supported,
                            "DUT should also support wifi tethering when called from ConnectivityManager")

    @test_tracker_info(uuid="09c19c35-c708-48a5-939b-ac2bbb403d54")
    def test_full_tether_startup(self):
        """Test full startup of wifi tethering

        1. Report current state.
        2. Switch to AP mode.
        3. verify SoftAP active.
        4. Shutdown wifi tethering.
        5. verify back to previous mode.
        """
        initial_wifi_state = self.dut.droid.wifiCheckState()
        initial_cell_state = tel_utils.is_sim_ready(self.log, self.dut)
        self.dut.log.info("current state: %s", initial_wifi_state)
        self.dut.log.info("is sim ready? %s", initial_cell_state)
        if initial_cell_state:
            self.check_cell_data_and_enable()
        config = self.create_softap_config()
        wutils.start_wifi_tethering(self.dut,
                                    config[wutils.WifiEnums.SSID_KEY],
                                    config[wutils.WifiEnums.PWD_KEY])
        self.confirm_softap_in_scan_results(config[wutils.WifiEnums.SSID_KEY])
        wutils.stop_wifi_tethering(self.dut)
        asserts.assert_false(self.dut.droid.wifiIsApEnabled(),
                             "SoftAp is still reported as running")
        if initial_wifi_state:
            self.verify_return_to_wifi_enabled()
        elif self.dut.droid.wifiCheckState():
            asserts.fail("Wifi was disabled before softap and now it is enabled")

    """ Tests End """


if __name__ == "__main__":
    pass
