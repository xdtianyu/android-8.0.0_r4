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

import logging
import time
import socket

from acts import asserts
from acts import base_test
from acts import test_runner
from acts.controllers import adb
from acts.test_utils.tel import tel_data_utils
from acts.test_utils.tel import tel_test_utils
from acts.test_utils.tel import tel_defines
from acts.test_utils.wifi import wifi_test_utils


class WifiTetheringTest(base_test.BaseTestClass):
    """ Tests for Wifi Tethering """

    def setup_class(self):
        """ Setup devices for tethering and unpack params """
        self.hotspot_device, self.tethered_device = self.android_devices[:2]
        req_params = ("ssid", "password", "url")
        self.unpack_userparams(req_params)
        asserts.assert_true(
            tel_data_utils.toggle_airplane_mode(self.log, self.hotspot_device, False),
            "Could not disable airplane mode")
        wifi_test_utils.wifi_toggle_state(self.hotspot_device, False)
        self.hotspot_device.droid.telephonyToggleDataConnection(True)
        tel_data_utils.wait_for_cell_data_connection(self.log, self.hotspot_device, True)
        asserts.assert_true(
            tel_data_utils.verify_http_connection(self.log, self.hotspot_device),
            "HTTP verification failed on cell data connection")
        asserts.assert_true(
            self.hotspot_device.droid.connectivityIsTetheringSupported(),
            "Tethering is not supported for the provider")
        wifi_test_utils.wifi_test_device_init(self.tethered_device)
        self.tethered_device.droid.telephonyToggleDataConnection(False)

    def teardown_class(self):
        """ Reset devices """
        wifi_test_utils.wifi_toggle_state(self.hotspot_device, True)
        self.hotspot_device.droid.telephonyToggleDataConnection(True)
        self.tethered_device.droid.telephonyToggleDataConnection(True)

    """ Helper functions """

    def _is_ipaddress_ipv6(self,ip_address):
        """ Verify if the given string is a valid IPv6 address

        Args:
            1. string which contains the IP address

        Returns:
            True: if valid ipv6 address
            False: if not
        """
        try:
            socket.inet_pton(socket.AF_INET6, ip_address)
            return True
        except socket.error:
            return False

    def _supports_ipv6_tethering(self, dut):
        """ Check if provider supports IPv6 tethering.
            Currently, only Verizon supports IPv6 tethering

        Returns:
            True: if provider supports IPv6 tethering
            False: if not
        """
        # Currently only Verizon support IPv6 tethering
        carrier_supports_tethering = ["vzw"]
        operator = tel_test_utils.get_operator_name(self.log, dut)
        return operator in carrier_supports_tethering

    def _find_ipv6_default_route(self, dut):
        """ Checks if IPv6 default route exists in the link properites

        Returns:
            True: if default route found
            False: if not
        """
        default_route_substr = "::/0 -> "
        link_properties = dut.droid.connectivityGetActiveLinkProperties()
        return link_properties and default_route_substr in link_properties

    def _verify_ipv6_tethering(self,dut):
        """ Verify IPv6 tethering """
        http_response = dut.droid.httpRequestString(self.url)
        link_properties = dut.droid.connectivityGetActiveLinkProperties()
        self.log.info("IP address %s " % http_response)
        if dut==self.hotspot_device or self._supports_ipv6_tethering(self.hotspot_device):
            asserts.assert_true(self._is_ipaddress_ipv6(http_response),
                                "The http response did not return IPv6 address")
            asserts.assert_true(link_properties and http_response in link_properties,
                                "Could not find IPv6 address in link properties")
            asserts.assert_true(self._find_ipv6_default_route(dut),
                                "Could not find IPv6 default route in link properties")
        else:
            asserts.assert_true(not self._find_ipv6_default_route(dut),
                                "Found IPv6 default route in link properties")


    """ Test Cases """

    def test_ipv6_tethering(self):
        """ IPv6 tethering test

        Steps:
            1. Start wifi tethering on provider
            2. Client connects to wifi tethering SSID
            3. Verify IPv6 address on the client's link properties
            4. Verify ping on client using ping6 which should pass
            5. Disable mobile data on provider and verify that link properties
               does not have IPv6 address and default route
        """
        # Start wifi tethering on the hotspot device
        self.log.info("Start tethering on provider: {}"
                      .format(self.hotspot_device.serial))
        wifi_test_utils.start_wifi_tethering(self.hotspot_device,
                                             self.ssid,
                                             self.password)
        time.sleep(tel_defines.WAIT_TIME_ANDROID_STATE_SETTLING)
        asserts.assert_true(
            tel_data_utils.verify_http_connection(self.log,self.hotspot_device),
            "Could not verify http connection on the provider")

        # Verify link properties on hotspot device
        self.log.info("Check IPv6 properties on the hotspot device")
        self._verify_ipv6_tethering(self.hotspot_device)

        # Connect the client to the SSID
        asserts.assert_true(
            tel_test_utils.WifiUtils.wifi_connect(self.log,
                                                  self.tethered_device,
                                                  self.ssid,
                                                  self.password),
            "Unable to connect to the hotspot SSID")

        # Need to wait atleast 2 seconds for IPv6 address to
        # show up in the link properties
        time.sleep(2)

        # Verify link properties on tethered device
        self.log.info("Check IPv6 properties on the tethered device")
        self._verify_ipv6_tethering(self.tethered_device)

        # Verify ping6 on tethered device
        ping_result = self.tethered_device.droid.pingHost("www.google.com",
                                                          5,
                                                          "ping6")
        if self._supports_ipv6_tethering(self.hotspot_device):
            asserts.assert_true(ping_result, "Ping6 failed on the client")
        else:
            asserts.assert_true(not ping_result, "Ping6 failed as expected")

        # Disable mobile data on hotspot device
        # and verify the link properties on tethered device
        self.log.info("Disabling mobile data on hotspot device")
        self.hotspot_device.droid.telephonyToggleDataConnection(False)
        asserts.assert_equal(self.hotspot_device.droid.telephonyGetDataConnectionState(),
                             tel_defines.DATA_STATE_CONNECTED,
                             "Could not disable cell data")
        time.sleep(2) # wait until the IPv6 is removed from link properties
        asserts.assert_true(not self._find_ipv6_default_route(self.tethered_device),
                            "Found IPv6 default route in link properties - Data off")
