# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import datetime, logging, time

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import tpm_utils
from autotest_lib.server import test
from autotest_lib.server.cros.multimedia import remote_facade_factory

LONG_TIMEOUT = 8
SHORT_TIMEOUT = 5


class enterprise_CFM_SessionStress(test.test):
    """Stress tests the device in CFM kiosk mode by initiating a new hangout
    session multiple times.
    """
    version = 1


    def _run_hangout_session(self):
        """Start a hangout session and do some checks before ending the session.

        @raises error.TestFail if any of the checks fail.
        """
        current_time = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
        hangout_name = 'auto-hangout-' + current_time
        logging.info('Session name: %s', hangout_name)

        self.cfm_facade.start_new_hangout_session(hangout_name)
        time.sleep(LONG_TIMEOUT)
        self.cfm_facade.end_hangout_session()


    def run_once(self, host, repeat):
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
            self.cfm_facade.wait_for_telemetry_commands()
            self.cfm_facade.wait_for_oobe_start_page()

            if not self.cfm_facade.is_oobe_start_page():
                raise error.TestFail('CFM did not reach oobe screen.')

            self.cfm_facade.skip_oobe_screen()

            while repeat:
                self._run_hangout_session()
                time.sleep(SHORT_TIMEOUT)
                repeat -= 1
        except Exception as e:
            raise error.TestFail(str(e))

        tpm_utils.ClearTPMOwnerRequest(self.client)
