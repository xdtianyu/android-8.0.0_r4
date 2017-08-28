# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
from autotest_lib.client.bin import test
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import utils
from autotest_lib.client.cros import service_stopper
from autotest_lib.client.cros.graphics import graphics_utils


class graphics_Drm(test.test):
    """Runs one of the drm-tests.
    """
    version = 1
    GSC = None
    _services = None
    _timeout = 120

    def initialize(self):
        self.GSC = graphics_utils.GraphicsStateChecker()
        self._services = service_stopper.ServiceStopper(['ui'])

    def cleanup(self):
        if self.GSC:
            self.GSC.finalize()
        if self._services:
            self._services.restore_services()

    def run_once(self, cmd, stop_ui=True, display_required=True):
        num_displays = graphics_utils.get_num_outputs_on()
        # Sanity check to guard against incorrect silent passes.
        if num_displays == 0 and utils.get_device_type() == 'CHROMEBOOK':
            raise error.TestFail('Error: found Chromebook without display.')
        if display_required and num_displays == 0:
            # If a test needs a display and we don't have a display,
            # consider it a pass.
            logging.warning('No display connected, skipping test.')
            return
        if stop_ui:
            self._services.stop_services()
        try:
            result = utils.run(cmd,
                               timeout=self._timeout,
                               ignore_status=True,
                               stderr_is_expected=True,
                               verbose=True,
                               stdout_tee=utils.TEE_TO_LOGS,
                               stderr_tee=utils.TEE_TO_LOGS)
        except Exception:
            # Fail on exceptions.
            raise error.TestFail('Failed: Exception running %s' % cmd)

        # Last but not least check return code and use it for triage.
        if result.exit_status != 0:
            raise error.TestFail('Failed: %s (exit=%d)' %
                                    (cmd, result.exit_status))
