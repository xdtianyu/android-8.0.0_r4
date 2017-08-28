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

from acts.controllers.anritsu_lib.md8475a import BtsServiceState
from acts.controllers.anritsu_lib.md8475a import BtsTechnology
from acts.controllers.anritsu_lib.md8475a import MD8475A
from acts.controllers.anritsu_lib.md8475a import ProcessingStatus
from acts.controllers.anritsu_lib.md8475a import TriggerMessageIDs
from acts.controllers.anritsu_lib.md8475a import TriggerMessageReply
from acts.test_utils.tel import tel_test_utils
from acts.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest


class TelephonyConnectivitySanityTest(TelephonyBaseTest):
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

    """ Tests Begin """

    @TelephonyBaseTest.tel_test_wrap
    def test_network_registration(self):
        '''
        Test ID: TEL-CO-01
        Checks the Network service state  after bootup. Verifies the
        network registration

        Steps
        -----
        1. The device is in airplane mode
        2. Turn off the airplane mode (This simulates the Boot up for modem)
        3. check for the service state. expecting IN SERVICE
        '''
        test_status = "failed"
        # turn on modem to start registration
        tel_test_utils.turn_on_modem(self.droid)
        self.log.info("Waiting for Network registration")
        test_status, event = tel_test_utils.wait_for_network_registration(
            self.ed, self.anritsu, self.log)

        if test_status == "passed":
            self.log.info(
                "TEL-CO-01:Network registration verification: Passed")
            return True
        else:
            self.log.info(
                "TEL-CO-01:Network registration verification: Failed")
            return False

    @TelephonyBaseTest.tel_test_wrap
    def test_network_params_verification(self):
        '''
        Test ID: TEL-CO-02
        verifies the network registration parameters

        Steps
        -----
        1. The device is in airplane mode
        2. Turn off the airplane mode (This simulates the Boot up for modem)
        3. check for the service state. expecting IN SERVICE
        4. verifies the values for different parameters
        '''
        test_status = "failed"
        # turn on modem to start registration
        tel_test_utils.turn_on_modem(self.droid)
        self.log.info("Waiting for Network registration")
        test_status, event = tel_test_utils.wait_for_network_registration(
            self.ed, self.anritsu, self.log)

        if test_status == "passed":
            self.log.info("Verifying the NW Service Parameters")
            expected_voice_nwtype = None
            operator_name = None
            mcc = None
            mnc = None

            bts_number, rat_info = self.anritsu.get_camping_cell()
            if rat_info == BtsTechnology.WCDMA.value:
                expected_voice_nwtype = "UMTS"
                operator_name = tel_test_utils.WCDMA_NW_NAME
                mcc = tel_test_utils.NW_MCC
                mnc = tel_test_utils.NW_MNC
            elif rat_info == BtsTechnology.LTE.value:
                expected_voice_nwtype = "LTE"
                operator_name = tel_test_utils.LTE_NW_NAME
                mcc = tel_test_utils.NW_MCC
                mnc = tel_test_utils.NW_MNC

            self.log.info("VoiceNwState :{}".format(event['data'][
                'VoiceRegState']))
            self.log.info("VoiceNetworkType :{}".format(event['data'][
                'VoiceNetworkType']))
            self.log.info("DataRegState :{}".format(event['data'][
                'DataRegState']))
            self.log.info("DataNetworkType :{}".format(event['data'][
                'DataNetworkType']))
            self.log.info("OperatorName :{}".format(event['data'][
                'OperatorName']))
            self.log.info("OperatorId :{}".format(event['data']['OperatorId']))
            self.log.info("Roaming :{}".format(event['data']['Roaming']))

            if event['data']['VoiceNetworkType'] != expected_voice_nwtype:
                test_status = "failed"
                self.log.info("Error:Expected NW Type is not received")
            if event['data']['OperatorId'][:3] != mcc:
                test_status = "failed"
                self.log.info("Error:Expected MNC is not received")
            if event['data']['OperatorId'][3:] != mnc:
                test_status = "failed"
                self.log.info("Error:Expected MCC is not received")

        # proceed with next step only if previous step is success
        if test_status == "passed":
            self.log.info("Waiting for data state: DATA_CONNECTED")
            test_status, event = tel_test_utils.wait_for_data_state(
                self.ed, self.log, "DATA_CONNECTED", 120)

        if test_status == "passed":
            self.log.info("TEL-CO-02: Network registration parameters"
                          " verification: Passed")
            return True
        else:
            self.log.info("TEL-CO-02: Network registration parameters"
                          " verification: Failed")
            return False

    @TelephonyBaseTest.tel_test_wrap
    def test_network_deregistration(self):
        '''
        Test ID: TEL-CO-03
        verifies the network de registration

        Steps
        -----
        1. The device is in airplane mode
        2. Turn off the airplane mode (This simulates the Boot up for modem)
        3. check for the service state. expecting IN SERVICE
        4. Turn on Airplane mode (This simulates network de registration)
        5. check for the service state. expecting POWER_OFF
        '''
        test_status = "failed"
        # turn on modem to start registration
        tel_test_utils.turn_on_modem(self.droid)
        self.log.info("Waiting for Network registration")
        test_status, event = tel_test_utils.wait_for_network_registration(
            self.ed, self.anritsu, self.log)

        # proceed with next step only if previous step is success
        if test_status == "passed":
            test_status = "failed"
            self.ed.clear_all_events()
            self.log.info("Making device to detach from network")
            self.droid.connectivityToggleAirplaneMode(True)
            self.log.info("Waiting for service state: POWER_OFF")
            test_status, event = tel_test_utils.wait_for_network_state(
                self.ed, self.log, "POWER_OFF", 60)

        if test_status == "passed":
            self.log.info("TEL-CO-03: Network de-registration"
                          " verification: Passed")
            return True
        else:
            self.log.info("TEL-CO-03: Network de-registration"
                          " verification: Failed")
            return False

    @TelephonyBaseTest.tel_test_wrap
    def test_network_out_of_service(self):
        '''
        Test ID: TEL-CO-04
        verifies the network out of state

        Steps
        -----
        1. The device is in airplane mode
        2. Turn off the airplane mode (This simulates the Boot up for modem)
        3. check for the service state. expecting IN SERVICE
        4. Make network out of service
        5. check for the service state. expecting OUT_OF_SERVICE
        '''

        test_status = "failed"
        # turn on modem to start registration
        tel_test_utils.turn_on_modem(self.droid)
        self.log.info("Waiting for Network registration")
        test_status, event = tel_test_utils.wait_for_network_registration(
            self.ed, self.anritsu, self.log)

        # proceed with next step only if previous step is success
        if test_status == "passed":
            test_status = "failed"
            self.ed.clear_all_events()
            # This sleep is required.Sometimes Anritsu box doesn't behave as
            # expected in executing the commands send to it without this delay.
            # May be it is in state transition.so the test doesn't proceed.
            # hence introduced this delay.
            time.sleep(5)
            bts_number, rat_info = self.anritsu.get_camping_cell()
            self.log.info("Making the attached NW as OUT_OF_STATE")
            if rat_info == BtsTechnology.LTE.value:
                self.lte_bts.service_state = BtsServiceState.SERVICE_STATE_OUT
            else:
                self.wcdma_bts.service_state = BtsServiceState.SERVICE_STATE_OUT
            self.log.info("Waiting for service state: OUT_OF_SERVICE")
            test_status, event = tel_test_utils.wait_for_network_state(
                self.ed, self.log, "OUT_OF_SERVICE", 90)

        if test_status == "passed":
            self.log.info("TEL-CO-04: Network out-of-service"
                          " verification: Passed")
            return True
        else:
            self.log.info("TEL-CO-04: Network out-of-service"
                          " verification: Failed")
            return False

    @TelephonyBaseTest.tel_test_wrap
    def test_network_return_inservice(self):
        '''
        Test ID: TEL-CO-06
        verifies the network returns to IN_SERVICE from OUT_OF_SERVICE

        Steps
        -----
        1. The device is in airplane mode
        2. Turn off the airplane mode (This simulates the Boot up for modem)
        3. check for the service state. expecting IN SERVICE
        4. Make the network out of service
        5. check for the service state. expecting OUT_OF_SERVICE
        6. Bring back the device to IN_SERVICE
        7. check for the service state. expecting IN_SERVICE
        '''
        test_status = "failed"
        # turn on modem to start registration
        tel_test_utils.turn_on_modem(self.droid)
        self.log.info("Waiting for Network registration")
        test_status, event = tel_test_utils.wait_for_network_registration(
            self.ed, self.anritsu, self.log)
        self.log.info("Waiting for data state: DATA_CONNECTED")
        test_status, event = tel_test_utils.wait_for_data_state(
            self.ed, self.log, "DATA_CONNECTED", 120)

        # proceed with next step only if previous step is success
        if test_status == "passed":
            test_status = "failed"
            self.ed.clear_all_events()
            # This sleep is required.Sometimes Anritsu box doesn't behave as
            # expected in executing the commands send to it without this delay.
            # May be it is in state transition.so the test doesn't proceed.
            # hence introduced this delay.
            time.sleep(5)
            bts_number, rat_info = self.anritsu.get_camping_cell()
            self.log.info("Making the attached NW as OUT_OF_STATE")
            if rat_info == BtsTechnology.LTE.value:
                self.lte_bts.service_state = BtsServiceState.SERVICE_STATE_OUT
            else:
                self.wcdma_bts.service_state = BtsServiceState.SERVICE_STATE_OUT
            self.log.info("Waiting for service state: OUT_OF_SERVICE")
            test_status, event = tel_test_utils.wait_for_network_state(
                self.ed, self.log, "OUT_OF_SERVICE", 120)

        # proceed with next step only if previous step is success
        if test_status == "passed":
            test_status = "failed"
            self.log.info("Waiting for Network registration")
            test_status, event = tel_test_utils.wait_for_network_registration(
                self.ed, self.anritsu, self.log)
            self.log.info("Waiting for data state: DATA_CONNECTED")
            test_status, event = tel_test_utils.wait_for_data_state(
                self.ed, self.log, "DATA_CONNECTED", 120)

        # proceed with next step only if previous step is success
        if test_status == "passed":
            test_status = "failed"
            self.ed.clear_all_events()
            # This sleep is required.Sometimes Anritsu box doesn't behave as
            #  expected in executing the commands send to it without this delay.
            # May be it is in state transition.so the test doesn't proceed.
            # hence introduced this delay.
            time.sleep(5)
            bts_number, rat_info = self.anritsu.get_camping_cell()
            self.log.info("Making the attached NW as OUT_OF_STATE")
            if rat_info == BtsTechnology.LTE.value:
                self.lte_bts.service_state = BtsServiceState.SERVICE_STATE_OUT
            else:
                self.wcdma_bts.service_state = BtsServiceState.SERVICE_STATE_OUT
            self.log.info("Waiting for service state: OUT_OF_SERVICE")
            test_status, event = tel_test_utils.wait_for_network_state(
                self.ed, self.log, "OUT_OF_SERVICE", 120)

        # proceed with next step only if previous step is success
        if test_status == "passed":
            test_status = "failed"
            self.ed.clear_all_events()
            # This sleep is required.Sometimes Anritsu box doesn't behave as
            # expected in executing the commands send to it without this delay.
            # May be it is in state transition.so the test doesn't proceed.
            # hence introduced this delay.
            time.sleep(5)
            self.log.info("Making the NW service IN_SERVICE")
            self.lte_bts.service_state = BtsServiceState.SERVICE_STATE_IN
            self.log.info("Waiting for Network registration")
            test_status, event = tel_test_utils.wait_for_network_registration(
                self.ed, self.anritsu, self.log)
            self.log.info("Waiting for data state: DATA_CONNECTED")
            test_status, event = tel_test_utils.wait_for_data_state(
                self.ed, self.log, "DATA_CONNECTED", 120)

        if test_status == "passed":
            self.log.info("TEL-CO-06: Network returning to IN_SERVICE"
                          " verification: Passed")
            return True
        else:
            self.log.info("TEL-CO-06: Network returning to IN_SERVICE"
                          " verification: Failed")
            return False

    @TelephonyBaseTest.tel_test_wrap
    def test_set_preferred_network(self):
        '''
        Test ID: TEL-CO-07
        verifies the network is registered on Preferred network

        Steps
        -----
        1. The device is in airplane mode
        2. Turn off the airplane mode (This simulates the Boot up for modem)
        3. check for the service state. expecting IN SERVICE
        4. Set the preferred network type
        5. check for the service state  and registered network
        '''
        test_status = "failed"
        # turn on modem to start registration
        tel_test_utils.turn_on_modem(self.droid)
        self.log.info("Waiting for Network registration")
        test_status, event = tel_test_utils.wait_for_network_registration(
            self.ed, self.anritsu, self.log)

        # proceed with next step only if previous step is success
        if test_status == "passed":
            test_status = "failed"
            pref_nwtype = 0
            expected_nwtype = ""
            bts_number, rat_info = self.anritsu.get_camping_cell()
            if rat_info == BtsTechnology.WCDMA.value:
                pref_nwtype = tel_test_utils.NETWORK_MODE_LTE_ONLY
                expected_nwtype = "LTE"
            elif rat_info == BtsTechnology.LTE.value:
                pref_nwtype = tel_test_utils.NETWORK_MODE_WCDMA_ONLY
                expected_nwtype = "UMTS"
            else:
                raise ValueError("Incorrect value of RAT returned by MD8475A")
            self.log.info("Setting preferred Network to " + expected_nwtype)
            self.droid.telephonySetPreferredNetwork(pref_nwtype)
            self.log.info("Waiting for service state: IN_SERVICE in " +
                          expected_nwtype)
            test_status, event = tel_test_utils.wait_for_network_registration(
                self.ed, self.anritsu, self.log, expected_nwtype)

        # proceed with next step only if previous step is success
        if test_status == "passed":
            test_status = "failed"
            pref_nwtype = 0
            expected_nwtype = ""
            bts_number, rat_info = self.anritsu.get_camping_cell()
            if rat_info == BtsTechnology.WCDMA.value:
                pref_nwtype = tel_test_utils.NETWORK_MODE_LTE_ONLY
                expected_nwtype = "LTE"
            elif rat_info == BtsTechnology.LTE.value:
                pref_nwtype = tel_test_utils.NETWORK_MODE_WCDMA_ONLY
                expected_nwtype = "UMTS"
            else:
                raise ValueError("Incorrect value of RAT returned by MD8475A")
            self.log.info("Setting preferred Network to " + expected_nwtype)
            self.droid.telephonySetPreferredNetwork(pref_nwtype)
            self.log.info("Waiting for service state: IN_SERVICE in " +
                          expected_nwtype)
            test_status, event = tel_test_utils.wait_for_network_registration(
                self.ed, self.anritsu, self.log, expected_nwtype)
        # setting the preferred network type to default
        self.droid.telephonySetPreferredNetwork(
            tel_test_utils.NETWORK_MODE_LTE_GSM_WCDMA)

        if test_status == "passed":
            self.log.info("TEL-CO-07: Setting preferred Network"
                          "verification: Passed")
            return True
        else:
            self.log.info("TEL-CO-07: Setting preferred Network"
                          "verification: Failed")
            return False

    @TelephonyBaseTest.tel_test_wrap
    def test_network_emergency(self):
        '''
        Test ID: TEL-CO-05
        verifies the network state - emergency

        Steps
        -----
        1. The device is in airplane mode
        2. Turn off the airplane mode (This simulates the Boot up for modem)
        3. check for the service state. expecting IN SERVICE
        4. Make the device emergency only
        5. check for the service state. expecting EMERGENCY_ONLY
        '''
        test_status = "failed"
        CAUSE_LA_NOTALLOWED = 12
        CAUSE_EPS_NOTALLOWED = 7
        triggermessage = self.anritsu.get_TriggerMessage()
        triggermessage.set_reply_type(TriggerMessageIDs.ATTACH_REQ,
                                      TriggerMessageReply.REJECT)
        triggermessage.set_reject_cause(TriggerMessageIDs.ATTACH_REQ,
                                        CAUSE_EPS_NOTALLOWED)
        # This sleep is required.Sometimes Anritsu box doesn't behave as
        # expected in executing the commands send to it without this delay.
        # May be it is in state transition.so the test doesn't proceed.
        # hence introduced this delay.
        time.sleep(5)
        triggermessage.set_reply_type(TriggerMessageIDs.MM_LOC_UPDATE_REQ,
                                      TriggerMessageReply.REJECT)
        triggermessage.set_reject_cause(TriggerMessageIDs.MM_LOC_UPDATE_REQ,
                                        CAUSE_LA_NOTALLOWED)

        # turn on modem to start registration
        tel_test_utils.turn_on_modem(self.droid)
        self.log.info("Waiting for service state: emergency")
        test_status, event = tel_test_utils.wait_for_network_state(
            self.ed, self.log, "EMERGENCY_ONLY", 300)

        if test_status == "passed":
            self.droid.connectivityToggleAirplaneMode(True)
            time_to_wait = 60
            sleep_interval = 1
            # Waiting for POWER OFF state in Anritsu
            start_time = time.time()
            end_time = start_time + time_to_wait
            while True:
                ue_status = self.anritsu.get_ue_status()
                if ue_status == ProcessingStatus.PROCESS_STATUS_POWEROFF:
                    break

                if time.time() <= end_time:
                    time.sleep(sleep_interval)
                    time_to_wait = end_time - time.time()
                else:
                    self.log.info("MD8475A has not come to POWEROFF state")
                    break

            # This sleep is required.Sometimes Anritsu box doesn't behave as
            # expected in executing the commands send to it without this delay.
            # May be it is in state transition.so the test doesn't proceed.
            # hence introduced this delay.
            time.sleep(10)
            triggermessage.set_reply_type(TriggerMessageIDs.ATTACH_REQ,
                                          TriggerMessageReply.ACCEPT)
            triggermessage.set_reply_type(TriggerMessageIDs.MM_LOC_UPDATE_REQ,
                                          TriggerMessageReply.ACCEPT)
            self.droid.connectivityToggleAirplaneMode(False)
            tel_test_utils.wait_for_network_registration(self.ed, self.anritsu,
                                                         self.log)

        if test_status == "passed":
            self.log.info("TEL-CO-05: Network emergency state"
                          " verification: Passed")
            return True
        else:
            self.log.info("TEL-CO-05: Network emergency state"
                          " verification: Failed")

            return False

    @TelephonyBaseTest.tel_test_wrap
    def test_manual_operator_selection(self):
        '''
        verifies the Manual Operator Selection

        Steps
        -----
        1. The device is in airplane mode
        2. Turn off the airplane mode (This simulates the Boot up for modem)
        3. check for the service state. expecting IN SERVICE
        4. search for NW operators and manually select a non-subscribed operator
        5. search for NW operators and manually select the subscribed operator
        6. verify the device is camped on subscribed operator
        '''
        # Android public APIs not available for this operation
        pass

    @TelephonyBaseTest.tel_test_wrap
    def test_auto_operator_selection(self):
        '''
        verifies the Automatic Operator Selection

        Steps
        -----
        1. The device is in airplane mode
        2. Turn off the airplane mode (This simulates the Boot up for modem)
        3. check for the service state. expecting IN SERVICE
        4. search for NW operators and manually select a non-subscribed operator
        5. select the the subscribed operator automatically
        6. verify the device is camped on subscribed operator
        '''
        # Android public APIs not available for this operation
        pass

    """ Tests End """
