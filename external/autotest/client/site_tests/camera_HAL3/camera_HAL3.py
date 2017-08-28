# Copyright 2017 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""A test which verifies the camera function with HAL3 interface."""

import os, logging
from autotest_lib.client.bin import test, utils

class camera_HAL3(test.test):
    """
    This test is a wrapper of the test binary arc_camera3_test.
    """

    version = 1
    binary = 'arc_camera3_test'
    dep = 'camera_hal3'
    timeout = 60

    def setup(self):
        self.dep_dir = os.path.join(self.autodir, 'deps', self.dep)
        self.job.setup_dep([self.dep])
        logging.debug('mydep is at %s' % self.dep_dir)

    def run_once(self):
        self.job.install_pkg(self.dep, 'dep', self.dep_dir)

        if utils.system_output('ldconfig -p').find('camera_hal.so') == -1:
            logging.debug('Skip test because camera_hal.so is not installed.')
            return

        binary_path = os.path.join(self.dep_dir, 'bin', self.binary)
        utils.system(binary_path, timeout=self.timeout)
