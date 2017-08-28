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
import os
import pprint
import re
import time
import urllib.request

from acts import asserts
from acts import base_test
from acts import test_runner
from acts.controllers import adb
from acts.test_decorators import test_tracker_info
from acts.test_utils.wifi import wifi_test_utils
from acts.test_utils.net import connectivity_const

VPN_CONST = connectivity_const.VpnProfile
VPN_TYPE = connectivity_const.VpnProfileType
VPN_PARAMS = connectivity_const.VpnReqParams


class LegacyVpnTest(base_test.BaseTestClass):
    """ Tests for Legacy VPN in Android

        Testbed requirement:
            1. One Android device
            2. A Wi-Fi network that can reach the VPN servers
    """

    def setup_class(self):
        """ Setup wi-fi connection and unpack params
        """
        self.dut = self.android_devices[0]
        required_params = dir(VPN_PARAMS)
        required_params = [x for x in required_params if not x.startswith('__')]
        self.unpack_userparams(required_params)
        wifi_test_utils.wifi_test_device_init(self.dut)
        wifi_test_utils.wifi_connect(self.dut, self.wifi_network)
        time.sleep(3)

    def teardown_class(self):
        """ Reset wifi to make sure VPN tears down cleanly
        """
        wifi_test_utils.reset_wifi(self.dut)

    def download_load_certs(self, vpn_type, vpn_server_addr, ipsec_server_type):
        """ Download the certificates from VPN server and push to sdcard of DUT

            Args:
                VPN type name

            Returns:
                Client cert file name on DUT's sdcard
        """
        url = "http://%s%s%s" % (vpn_server_addr,
                                 self.cert_path_vpnserver,
                                 self.client_pkcs_file_name)
        local_cert_name = "%s_%s_%s" % (vpn_type.name,
                                        ipsec_server_type,
                                        self.client_pkcs_file_name)
        local_file_path = os.path.join(self.log_path, local_cert_name)
        try:
            ret = urllib.request.urlopen(url)
            with open(local_file_path, "wb") as f:
                f.write(ret.read())
        except:
            asserts.fail("Unable to download certificate from the server")
        f.close()
        self.dut.adb.push("%s sdcard/" % local_file_path)
        return local_cert_name

    def generate_legacy_vpn_profile(self, vpn_type, vpn_server_addr, ipsec_server_type):
        """ Generate legacy VPN profile for a VPN

            Args:
                VpnProfileType
        """
        vpn_profile = {VPN_CONST.USER: self.vpn_username,
                       VPN_CONST.PWD: self.vpn_password,
                       VPN_CONST.TYPE: vpn_type.value,
                       VPN_CONST.SERVER: vpn_server_addr,}
        vpn_profile[VPN_CONST.NAME] = "test_%s_%s" % (vpn_type.name,ipsec_server_type)
        if vpn_type.name == "PPTP":
            vpn_profile[VPN_CONST.NAME] = "test_%s" % vpn_type.name
        psk_set = set(["L2TP_IPSEC_PSK", "IPSEC_XAUTH_PSK"])
        rsa_set = set(["L2TP_IPSEC_RSA", "IPSEC_XAUTH_RSA", "IPSEC_HYBRID_RSA"])
        if vpn_type.name in psk_set:
            vpn_profile[VPN_CONST.IPSEC_SECRET] = self.psk_secret
        elif vpn_type.name in rsa_set:
            cert_name = self.download_load_certs(vpn_type,
                                                 vpn_server_addr,
                                                 ipsec_server_type)
            vpn_profile[VPN_CONST.IPSEC_USER_CERT] = cert_name.split('.')[0]
            vpn_profile[VPN_CONST.IPSEC_CA_CERT] = cert_name.split('.')[0]
            self.dut.droid.installCertificate(vpn_profile,cert_name,self.cert_password)
        else:
            vpn_profile[VPN_CONST.MPPE] = self.pptp_mppe
        return vpn_profile

    def verify_ping_to_vpn_ip(self, connected_vpn_info):
        """ Verify if IP behind VPN server is pingable
            Ping should pass, if VPN is connected
            Ping should fail, if VPN is disconnected

            Args:
                connected_vpn_info which specifies the VPN connection status
        """
        try:
            ping_result = self.dut.adb.shell("ping -c 3 -W 2 %s"
                                             % self.vpn_verify_address)
            if not connected_vpn_info and "100% packet loss" \
                not in "%s" % ping_result:
                  asserts.fail("VPN is disconnected.\
                               Ping to the internal IP expected to fail")
        except adb.AdbError as ping_error:
            ping_error = "%s" % ping_error
            if connected_vpn_info and "100% packet loss" in ping_error:
                asserts.fail("Ping to the internal IP failed.\
                             Expected to pass as VPN is connected")

    def legacy_vpn_connection_test_logic(self, vpn_profile):
        """ Test logic for each legacy VPN connection

            Steps:
                1. Generate profile for the VPN type
                2. Establish connection to the server
                3. Verify that connection is established using LegacyVpnInfo
                4. Verify the connection by pinging the IP behind VPN
                5. Stop the VPN connection
                6. Check the connection status
                7. Verify that ping to IP behind VPN fails

            Args:
                VpnProfileType (1 of the 6 types supported by Android)
        """
        self.dut.adb.shell("ip xfrm state flush")
        logging.info("Connecting to: %s", vpn_profile)
        self.dut.droid.vpnStartLegacyVpn(vpn_profile)
        time.sleep(connectivity_const.VPN_TIMEOUT)
        connected_vpn_info = self.dut.droid.vpnGetLegacyVpnInfo()
        asserts.assert_equal(connected_vpn_info["state"],
                             connectivity_const.VPN_STATE_CONNECTED,
                             "Unable to establish VPN connection for %s"
                             % vpn_profile)
        self.verify_ping_to_vpn_ip(connected_vpn_info)
        ip_xfrm_state = self.dut.adb.shell("ip xfrm state")
        match_obj = re.search(r'hmac(.*)', "%s" % ip_xfrm_state)
        if match_obj:
            ip_xfrm_state = format(match_obj.group(0)).split()
            self.log.info("HMAC for ESP is %s " % ip_xfrm_state[0])
        self.dut.droid.vpnStopLegacyVpn()
        connected_vpn_info = self.dut.droid.vpnGetLegacyVpnInfo()
        asserts.assert_true(not connected_vpn_info,
                            "Unable to terminate VPN connection for %s"
                            % vpn_profile)
        self.verify_ping_to_vpn_ip(connected_vpn_info)

    """ Test Cases """

    @test_tracker_info(uuid="d2ac5a65-41fb-48de-a0a9-37e589b5456b")
    def test_connection_to_legacy_vpn(self):
        """ Verify VPN connection for all configurations.
            Supported VPN configurations are
            1.) PPTP            2.) L2TP IPSEC PSK
            3.) IPSEC XAUTH PSK 4.) L2TP IPSEC RSA
            5.) IPSEC XAUTH RSA 6.) IPSec Hybrid RSA

            Steps:
                1. Call legacy_vpn_connection_test_logic() for each VPN which
                tests the connection to the corresponding server

            Return:
                Pass: if all VPNs pass
                Fail: if any one VPN fails
        """
        def gen_name(vpn_profile):
            return "test_legacy_vpn_" + vpn_profile[VPN_CONST.NAME][5:]

        vpn_profiles = []
        for vpn in VPN_TYPE:
            for i in range(len(self.ipsec_server_type)):
                vpn_profiles.append(
                    self.generate_legacy_vpn_profile(vpn,
                                                     self.vpn_server_addresses[vpn.name][i],
                                                     self.ipsec_server_type[i]))
                # PPTP does not depend on ipsec and only strongswan supports Hybrid RSA
                if vpn.name =="PPTP" or vpn.name =="IPSEC_HYBRID_RSA":
                    break
        result = self.run_generated_testcases(self.legacy_vpn_connection_test_logic,
                                              vpn_profiles,
                                              name_func=gen_name,)
        msg = ("The following configs failed vpn connection %s"
               % pprint.pformat(result))
        asserts.assert_equal(len(result), 0, msg)
