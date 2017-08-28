# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import itertools, time

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import tpm_utils
from autotest_lib.server import test
from autotest_lib.server.cros.multimedia import remote_facade_factory


_SHORT_TIMEOUT = 5
_WAIT_DELAY = 15


class enterprise_CFM_USBPeripheralHotplugStress(test.test):
    """Uses servo to hotplug and unplug USB peripherals multiple times and
    verify's that the hotrod app appropriately detects the peripherals using
    app api's."""
    version = 1


    def _set_hub_power(self, on=True):
        """Setting USB hub power status

        @param on: To power on the servo usb hub or not.

        """
        reset = 'off'
        if not on:
            reset = 'on'
        self.client.servo.set('dut_hub1_rst1', reset)
        time.sleep(_WAIT_DELAY)


    def _enroll_device_and_skip_oobe(self):
        """Enroll device into CFM and skip CFM oobe."""
        self.cfm_facade.enroll_device()
        self.cfm_facade.restart_chrome_for_cfm()
        self.cfm_facade.wait_for_telemetry_commands()
        self.cfm_facade.wait_for_oobe_start_page()

        if not self.cfm_facade.is_oobe_start_page():
            raise error.TestFail('CFM did not reach oobe screen.')

        self.cfm_facade.skip_oobe_screen()
        time.sleep(_SHORT_TIMEOUT)


    def _set_peripheral(self, peripheral_dict):
        """Set perferred peripherals.

        @param peripheral_dict: Dictionary of peripherals
        """
        avail_mics = self.cfm_facade.get_mic_devices()
        avail_speakers = self.cfm_facade.get_speaker_devices()
        avail_cameras = self.cfm_facade.get_camera_devices()

        if peripheral_dict.get('Microphone') in avail_mics:
            self.cfm_facade.set_preferred_mic(
                    peripheral_dict.get('Microphone'))
        if peripheral_dict.get('Speaker') in avail_speakers:
            self.cfm_facade.set_preferred_speaker(
                    peripheral_dict.get('Speaker'))
        if peripheral_dict.get('Camera') in avail_cameras:
            self.cfm_facade.set_preferred_camera(
                    peripheral_dict.get('Camera'))


    def _peripheral_detection(self, peripheral_dict, on_off):
        """Detect attached peripheral.

        @param peripheral_dict: Dictionary of peripherals
        @param on_off: Is USB hub on or off.
        """
        if 'Microphone' in peripheral_dict.keys():
            if (on_off and peripheral_dict.get('Microphone') not in
                    self.cfm_facade.get_preferred_mic()):
                raise error.TestFail('Microphone not detected.')
            if (not on_off and peripheral_dict.get('Microphone') in
                    self.cfm_facade.get_preferred_mic()):
                raise error.TestFail('Microphone should not be detected.')

        if 'Speaker' in peripheral_dict.keys():
            if (on_off and peripheral_dict.get('Speaker') not in
                    self.cfm_facade.get_preferred_speaker()):
                raise error.TestFail('Speaker not detected.')
            if not on_off and self.cfm_facade.get_preferred_speaker():
                raise error.TestFail('Speaker should not be detected.')

        if 'Camera' in peripheral_dict.keys():
            if (on_off and peripheral_dict.get('Camera') not in
                    self.cfm_facade.get_preferred_camera()):
                raise error.TestFail('Camera not detected.')
            if not on_off and self.cfm_facade.get_preferred_camera():
                raise error.TestFail('Camera should not be detected.')


    def run_once(self, host, repeat, peripheral_whitelist_dict):
        """Main function to run autotest.

        @param host: Host object representing the DUT.
        @param repeat: Number of times peripheral should be hotplugged.
        @param peripheral_whitelist_dict: Dictionary of peripherals to test.
        """
        self.client = host

        factory = remote_facade_factory.RemoteFacadeFactory(
                host, no_chrome=True)
        self.cfm_facade = factory.create_cfm_facade()

        tpm_utils.ClearTPMOwnerRequest(self.client)

        if self.client.servo:
            self.client.servo.switch_usbkey('dut')
            self.client.servo.set('usb_mux_sel3', 'dut_sees_usbkey')
            time.sleep(_SHORT_TIMEOUT)
            self._set_hub_power(True)

        try:
            self._enroll_device_and_skip_oobe()
            self._set_peripheral(peripheral_whitelist_dict)

            on_off_list = [True, False]
            on_off = itertools.cycle(on_off_list)
            while repeat:
                reset_ = on_off.next()
                self._set_hub_power(reset_)
                self._peripheral_detection(peripheral_whitelist_dict, reset_)
                repeat -= 1
        except Exception as e:
            raise error.TestFail(str(e))

        tpm_utils.ClearTPMOwnerRequest(self.client)
