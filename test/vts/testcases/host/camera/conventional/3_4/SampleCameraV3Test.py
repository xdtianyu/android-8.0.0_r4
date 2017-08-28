#!/usr/bin/env python
#
# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import logging
import time

from vts.runners.host import asserts
from vts.runners.host import base_test
from vts.runners.host import test_runner
from vts.utils.python.controllers import android_device


class SampleCameraV3Test(base_test.BaseTestClass):
    """A sample testcase for the non-HIDL, conventional Camera HAL."""
    # Camera HAL version value (v3.4).
    VERSION_3_4 = 0x1000304
    MAX_RETRIES = 3

    def setUpClass(self):
        self.dut = self.registerController(android_device)[0]
        self.dut.hal.InitConventionalHal(target_type="camera",
                                         target_version=3.4,
                                         target_basepaths=["/system/lib/hw"],
                                         bits=32,
                                         target_package="hal.conventional.camera")

    def setUp(self):
        self.call_count_camera_device_status_change = 0
        self.call_count_torch_mode_status_change = 0

    def testCameraNormal(self):
        """A simple testcase which just emulates a normal usage pattern."""
        version = self.dut.hal.camera.common.GetAttributeValue("version")
        logging.info("version: %s", hex(version))
        if version != self.VERSION_3_4:
            asserts.skip("HAL version != v3.4")

        self.dut.hal.camera.common.module.methods.open()  # note args are skipped

        ops = self.dut.hal.camera.GetAttributeValue("ops")
        logging.info("ops: %s", ops)
        ops.flush(None)


if __name__ == "__main__":
    test_runner.main()
