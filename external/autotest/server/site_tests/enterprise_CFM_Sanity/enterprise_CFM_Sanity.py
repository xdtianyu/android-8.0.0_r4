# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import datetime, logging, time

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import tpm_utils
from autotest_lib.server import test
from autotest_lib.server.cros.multimedia import remote_facade_factory


LONG_TIMEOUT = 10
SHORT_TIMEOUT = 5
FAILED_TEST_LIST = list()


class enterprise_CFM_Sanity(test.test):
    """Tests the following fuctionality works on CFM enrolled devices:
           1. Is able to reach the oobe screen
           2. Is able to start a hangout session
           3. Should not be able to start a hangout session if already in a
              session.
           4. Exits hangout session successfully.
           5. Should be able to start a hangout session if currently not in
              a session.
           6. Is able to detect attached peripherals: mic, speaker, camera.
           7. Is able to run hotrod diagnostics.
    """
    version = 1


    def _hangouts_sanity_test(self):
        """Execute a series of test actions and perform verifications.

        @raises error.TestFail if any of the checks fail.
        """
        current_time = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
        hangout_name = 'auto-hangout-' + current_time

        self.cfm_facade.wait_for_telemetry_commands()
        self.cfm_facade.wait_for_oobe_start_page()

        if not self.cfm_facade.is_oobe_start_page():
            raise error.TestFail('CFM did not reach oobe screen.')

        self.cfm_facade.skip_oobe_screen()

        if self.cfm_facade.is_ready_to_start_hangout_session():
            self.cfm_facade.start_new_hangout_session(hangout_name)

        if not self.cfm_facade.is_in_hangout_session():
            raise error.TestFail('CFM was not able to start hangout session.')

        time.sleep(LONG_TIMEOUT)
        self.cfm_facade.unmute_mic()

        if self.cfm_facade.is_ready_to_start_hangout_session():
            raise error.TestFail('Is already in hangout session and should not '
                                 'be able to start another session.')

        if self.cfm_facade.is_oobe_start_page():
            raise error.TestFail('CFM should be in hangout session and not on '
                                 'oobe screen.')

        time.sleep(SHORT_TIMEOUT)
        self.cfm_facade.mute_mic()
        time.sleep(SHORT_TIMEOUT)
        self.cfm_facade.end_hangout_session()

        if self.cfm_facade.is_in_hangout_session():
            raise error.TestFail('CFM should not be in hangout session.')

        if self.cfm_facade.is_oobe_start_page():
            raise error.TestFail('CFM should not be on oobe screen.')

        if not self.cfm_facade.is_ready_to_start_hangout_session():
            raise error.TestFail('CFM should be in read state to start hangout '
                           'session.')


    def _peripherals_sanity_test(self):
        """Checks for connected peripherals."""
        self.cfm_facade.wait_for_telemetry_commands()

        time.sleep(SHORT_TIMEOUT)

        if not self.cfm_facade.get_mic_devices():
            FAILED_TEST_LIST.append('No mic detected')

        if not self.cfm_facade.get_speaker_devices():
            FAILED_TEST_LIST.append('No speaker detected')

        if not self.cfm_facade.get_camera_devices():
            FAILED_TEST_LIST.append('No camera detected')

        if not self.cfm_facade.get_preferred_mic():
            FAILED_TEST_LIST.append('No preferred mic')

        if not self.cfm_facade.get_preferred_speaker():
            FAILED_TEST_LIST.append('No preferred speaker')

        if not self.cfm_facade.get_preferred_camera():
            FAILED_TEST_LIST.append('No preferred camera')


    def _diagnostics_sanity_test(self):
        """Runs hotrod diagnostics and checks status.

        @raise error.TestFail if diagnostic checks fail.
        """
        self.cfm_facade.wait_for_telemetry_commands()

        if self.cfm_facade.is_diagnostic_run_in_progress():
            raise error.TestFail('Diagnostics should not be running.')

        self.cfm_facade.run_diagnostics()

        if not self.cfm_facade.is_diagnostic_run_in_progress():
            raise error.TestFail('Diagnostics should be running.')

        diag_results = self.cfm_facade.get_last_diagnostics_results()

        if diag_results['status'] not in 'success':
            logging.debug(diag_results['childrens'])
            FAILED_TEST_LIST.append('Diagnostics failed')


    def run_once(self, host=None):
        """Runs the test."""
        self.client = host

        factory = remote_facade_factory.RemoteFacadeFactory(
                host, no_chrome=True)
        self.cfm_facade = factory.create_cfm_facade()

        tpm_utils.ClearTPMOwnerRequest(self.client)

        if self.client.servo:
            self.client.servo.switch_usbkey('dut')
            self.client.servo.set('usb_mux_sel3', 'dut_sees_usbkey')
            time.sleep(SHORT_TIMEOUT)
            self.client.servo.set('dut_hub1_rst1', 'off')
            time.sleep(SHORT_TIMEOUT)

        try:
            self.cfm_facade.enroll_device()
            self.cfm_facade.restart_chrome_for_cfm()

            self._hangouts_sanity_test()
            self._peripherals_sanity_test()
            self._diagnostics_sanity_test()
        except Exception as e:
            raise error.TestFail(str(e))

        tpm_utils.ClearTPMOwnerRequest(self.client)

        if FAILED_TEST_LIST:
            raise error.TestFail('Test failed because of following reasons: %s'
                                 % ', '.join(map(str, FAILED_TEST_LIST)))
