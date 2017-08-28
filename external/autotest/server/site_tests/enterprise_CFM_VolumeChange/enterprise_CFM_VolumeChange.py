# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import datetime, logging, random, time

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import tpm_utils
from autotest_lib.server import test
from autotest_lib.server.cros.multimedia import remote_facade_factory


_SHORT_TIMEOUT = 2
_LONG_TIMEOUT = 5


class enterprise_CFM_VolumeChange(test.test):
    """Volume changes made in the CFM / hotrod app should be accurately
    reflected in CrOS.
    """
    version = 1


    def _enroll_device(self):
        """Enroll device into CFM."""
        self.cfm_facade.enroll_device()
        self.cfm_facade.restart_chrome_for_cfm()
        self.cfm_facade.wait_for_telemetry_commands()
        self.cfm_facade.wait_for_oobe_start_page()

        if not self.cfm_facade.is_oobe_start_page():
            raise error.TestFail('CFM did not reach oobe screen.')

        self.cfm_facade.skip_oobe_screen()


    def _start_hangout_session(self):
        """Start a hangout session.

        @param webview_context: Context for hangouts webview.
        @raises error.TestFail if any of the checks fail.
        """
        current_time = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
        hangout_name = 'auto-hangout-' + current_time

        self.cfm_facade.start_new_hangout_session(hangout_name)

        if self.cfm_facade.is_ready_to_start_hangout_session():
            raise error.TestFail('Is already in hangout session and should not '
                                 'be able to start another session.')

        time.sleep(_SHORT_TIMEOUT)

        if self.cfm_facade.is_mic_muted():
            self.cfm_facade.unmute_mic()


    def _end_hangout_session(self):
        """End hangout session.

        @param webview_context: Context for hangouts window.
        """
        self.cfm_facade.end_hangout_session()

        if self.cfm_facade.is_in_hangout_session():
            raise error.TestFail('CFM should not be in hangout session.')


    def _change_volume(self, repeat, cmd):
        """Change volume using CFM api and cross check with cras_test_client
        output.

        @param repeat: Number of times the volume should be changed.
        @param cmd: cras_test_client command to run.
        @raises error.TestFail if cras volume does not match volume set by CFM.
        """
        # This is used to trigger crbug.com/614885
        for volume in range(55, 85):
            self.cfm_facade.set_speaker_volume(str(volume))
            time.sleep(random.uniform(0.01, 0.05))

        while repeat:
            # TODO: Change range back to 0, 100 once crbug.com/633809 is fixed.
            cfm_volume = str(random.randrange(2, 100, 1))
            self.cfm_facade.set_speaker_volume(cfm_volume)
            time.sleep(_SHORT_TIMEOUT)

            cras_volume = self.client.run_output(cmd)
            if cras_volume != cfm_volume:
                raise error.TestFail('Cras volume (%s) does not match volume '
                                     'set by CFM (%s).' %
                                     (cras_volume, cfm_volume))
            logging.info('Cras volume (%s) matches volume set by CFM (%s)',
                         cras_volume, cfm_volume)

            repeat -= 1


    def run_once(self, host, repeat, cmd):
        """Runs the test."""
        self.client = host

        factory = remote_facade_factory.RemoteFacadeFactory(
                host, no_chrome=True)
        self.cfm_facade = factory.create_cfm_facade()

        tpm_utils.ClearTPMOwnerRequest(self.client)

        if self.client.servo:
            self.client.servo.switch_usbkey('dut')
            self.client.servo.set('usb_mux_sel3', 'dut_sees_usbkey')
            time.sleep(_LONG_TIMEOUT)
            self.client.servo.set('dut_hub1_rst1', 'off')
            time.sleep(_LONG_TIMEOUT)

        try:
            self._enroll_device()
            self._start_hangout_session()
            self._change_volume(repeat, cmd)
            self._end_hangout_session()
        except Exception as e:
            raise error.TestFail(str(e))

        tpm_utils.ClearTPMOwnerRequest(self.client)
