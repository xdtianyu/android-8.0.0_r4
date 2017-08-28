#/usr/bin/env python3.4
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
"""
Sanity tests for connectivity tests in telephony
"""

import time
from queue import Empty

from acts.controllers.anritsu_lib.md8475a import BtsNumber
from acts.controllers.anritsu_lib.md8475a import BtsTechnology
from acts.controllers.anritsu_lib.md8475a import MD8475A
from acts.test_utils.tel import tel_test_utils
from acts.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest


class TelephonyDataSanityTest(TelephonyBaseTest):
    def __init__(self, controllers):
        TelephonyBaseTest.__init__(self, controllers)
        self.anritsu = MD8475A(tel_test_utils.MD8475A_IP_ADDRESS)

    def setup_test(self):
        self.lte_bts, self.wcdma_bts = tel_test_utils.set_system_model(
            self.anritsu, "LTE_WCDMA")
        tel_test_utils.init_phone(self.droid, self.ed)
        self.droid.telephonyStartTrackingServiceStateChange()
        self.droid.telephonyStartTrackingDataConnectionStateChange()
        self.log.info("Starting Simulation")
        self.anritsu.start_simulation()
        return True

    def teardown_test(self):
        self.droid.telephonyStopTrackingServiceStateChange()
        self.droid.telephonyStopTrackingDataConnectionStateChange()
        self.log.info("Stopping Simulation")
        self.anritsu.stop_simulation()
        # turn off modem
        tel_test_utils.turn_off_modem(self.droid)

    def teardown_class(self):
        self.anritsu.disconnect()

    def _wait_for_bts_state(self, btsnumber, state, timeout=30):
        ''' Wait till BTS state changes. state value are "IN" and "OUT" '''
        sleep_interval = 2
        status = "failed"

        wait_time = timeout
        while (wait_time > 0):
            if state == btsnumber.service_state:
                print(btsnumber.service_state)
                status = "passed"
                break
            else:
                time.sleep(sleep_interval)
                waiting_time = waiting_time - sleep_interval

        if status == "failed":
            self.log.info("Timeout: Expected state is not received.")

    """ Tests Begin """

    @TelephonyBaseTest.tel_test_wrap
    def test_data_conn_state_when_access_enabled(self):
        '''
        Check data conenction state after boot up

        Steps
        -----
        1. Get the device is IN_SERVICE state
        2. check the data conenction status
        '''
        test_status = "failed"
        # turn on modem to start registration
        tel_test_utils.turn_on_modem(self.droid)
        self.log.info("Waiting for Network registration")
        test_status, event = tel_test_utils.wait_for_network_registration(
            self.ed, self.anritsu, self.log)

        # proceed with next step only if previous step is success
        if test_status == "passed":
            self.log.info("Waiting for data state: DATA_CONNECTED")
            test_status, event = tel_test_utils.wait_for_data_state(
                self.ed, self.log, "DATA_CONNECTED", 120)

        if test_status == "passed":
            self.log.info("Data connection state(access enabled) "
                          "verification: Passed")
            return True
        else:
            self.log.info("Data connection state(access enabled) "
                          "verification: Failed")
            return False

    @TelephonyBaseTest.tel_test_wrap
    def test_data_conn_state_when_access_disabled(self):
        '''
        Check data conenction state after disabling data access

        Steps
        -----
        1. Get the device is IN_SERVICE state
        2. check the data conenction status ( data access enabled)
        3. disable the data access
        4. check the data conenction status ( data access enabled)
        '''
        test_status = "failed"
        # turn on modem to start registration
        tel_test_utils.turn_on_modem(self.droid)
        self.log.info("Waiting for Network registration")
        test_status, event = tel_test_utils.wait_for_network_registration(
            self.ed, self.anritsu, self.log)

        # proceed with next step only if previous step is success
        if test_status == "passed":
            self.log.info("Waiting for data state: DATA_CONNECTED")
            test_status, event = tel_test_utils.wait_for_data_state(
                self.ed, self.log, "DATA_CONNECTED", 120)

        if test_status == "passed":
            time.sleep(20)
            self.log.info("Disabling data access")
            self.droid.telephonyToggleDataConnection(False)
            self.log.info("Waiting for data state: DATA_DISCONNECTED")
            test_status, event = tel_test_utils.wait_for_data_state(
                self.ed, self.log, "DATA_DISCONNECTED", 120)

        if test_status == "passed":
            self.log.info("Data connection state(access disabled) "
                          "verification: Passed")
            return True
        else:
            self.log.info("Data connection state(access disabled) "
                          "verification: Failed")
            return False

    """ Tests End """
