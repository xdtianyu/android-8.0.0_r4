# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_ECWakeSource(FirmwareTest):
    """
    Servo based EC wake source test.
    """
    version = 1

    # Delay for waiting client to shut down
    SHUTDOWN_DELAY = 10

    def initialize(self, host, cmdline_args):
        super(firmware_ECWakeSource, self).initialize(host, cmdline_args)
        # Only run in normal mode
        self.switcher.setup_mode('normal')

    def hibernate_and_wake_by_power_button(self):
        """Shutdown and hibernate EC. Then wake by power button."""
        self.faft_client.system.run_shell_command("shutdown -P now")
        time.sleep(self.SHUTDOWN_DELAY)
        self.switcher.wait_for_client_offline()
        self.ec.send_command("hibernate 1000")
        time.sleep(self.WAKE_DELAY)
        self.servo.power_short_press()

    def run_once(self):
        # TODO(victoryang): make this test run on both x86 and arm
        if not self.check_ec_capability(['x86', 'lid']):
            raise error.TestNAError("Nothing needs to be tested on this device")

        logging.info("Suspend and wake by power button.")
        self.suspend()
        self.switcher.wait_for_client_offline()
        self.servo.power_normal_press()
        self.switcher.wait_for_client()

        logging.info("Suspend and wake by lid switch.")
        self.suspend()
        self.switcher.wait_for_client_offline()
        self.servo.set('lid_open', 'no')
        time.sleep(self.LID_DELAY)
        self.servo.set('lid_open', 'yes')
        self.switcher.wait_for_client()

        logging.info("EC hibernate and wake by power button.")
        self.hibernate_and_wake_by_power_button()
        self.switcher.wait_for_client()
