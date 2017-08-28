# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time

from autotest_lib.client.common_lib import error
from autotest_lib.server import autotest, test
from autotest_lib.server.cros.servo import chrome_cr50
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_Cr50DeepSleepStress(FirmwareTest):
    """Verify cr50 deep sleep after running power_SuspendStress.

    Cr50 should enter deep sleep every suspend. Verify that by checking the
    idle deep sleep count.

    @param duration: Total time to spend running power_SuspendStress.
            suspend_iterations will take priority if it is set to some
            non-zero value.
    @param suspend_iterations: The number of iterations to run for
            power_SuspendStress.
    """
    version = 1

    SLEEP_DELAY = 20
    MIN_RESUME = 15
    MIN_SUSPEND = 15
    MEM = "mem"

    def initialize(self, host, cmdline_args):
        super(firmware_Cr50DeepSleepStress, self).initialize(host, cmdline_args)
        if not hasattr(self, "cr50"):
          raise error.TestError('Test needs to be run through CCD')

    def check_deep_sleep_count(self):
        self.cr50.ccd_enable()
        count = self.cr50.get_deep_sleep_count()
        logging.debug("Cr50 resumed from sleep %d times", count)
        return count

    def cleanup(self):
        self.check_deep_sleep_count()
        super(firmware_Cr50DeepSleepStress, self).cleanup()

    def run_once(self, host, duration=600, suspend_iterations=0):
        self.cr50.send_command('sysrst pulse')

        if self.MIN_SUSPEND + self.MIN_RESUME < self.SLEEP_DELAY:
            logging.info("Minimum suspend-resume cycle is %ds. This is " \
                         "shorter than the Cr50 idle timeout. Cr50 may not " \
                         "enter deep sleep every cycle",
                         self.MIN_SUSPEND + self.MIN_RESUME)

        # Clear the deep sleep count
        logging.info("Clear Cr50 deep sleep count")
        self.cr50.clear_deep_sleep_count()
        # Disable CCD so Cr50 can enter deep sleep
        logging.info("Disable CCD")
        self.cr50.ccd_disable()

        self.client_at = autotest.Autotest(host)
        suspend_iterations = suspend_iterations if suspend_iterations else None

        self.client_at.run_test('power_SuspendStress', tag="idle",
                                duration=duration,
                                min_suspend=self.MIN_SUSPEND,
                                min_resume=self.MIN_RESUME,
                                check_connection=False,
                                iterations=suspend_iterations,
                                suspend_state=self.MEM)

        count = self.check_deep_sleep_count()
        if suspend_iterations:
            logging.info("After %d suspend-resume cycles Cr50 entered deep " \
                         "sleep %d times." % (suspend_iterations, count))
            if count != suspend_iterations:
                raise error.TestFail("Cr50 deep sleep count, %d, did not " \
                                     "match suspend count, %d" %
                                     (count, suspend_iterations))
        else:
            logging.info("During the %ds test Cr50 entered deep sleep %d " \
                         "times" % (duration, count))
