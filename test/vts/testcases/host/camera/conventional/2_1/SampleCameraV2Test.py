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


class SampleCameraV2Test(base_test.BaseTestClass):
    """A sample testcase for the non-HIDL, conventional Camera HAL."""
    # Camera HAL version value (v2.1).
    VERSION_2_1 = 0x201
    VERSION_2_4 = 0x204
    MAX_RETRIES = 5

    def setUpClass(self):
        self.dut = self.registerController(android_device)[0]
        self.dut.hal.InitConventionalHal(target_type="camera",
                                         target_version=2.1,
                                         target_basepaths=["/system/lib/hw"],
                                         bits=32,
                                         target_package="hal.conventional.camera")

    def setUp(self):
        self.call_count_camera_device_status_change = 0
        self.call_count_torch_mode_status_change = 0

    def testCameraNormal(self):
        """A simple testcase which just emulates a normal usage pattern."""
        version = self.dut.hal.camera.common.GetAttributeValue("module_api_version")
        logging.info("version: %s", hex(version))
        if version != self.VERSION_2_1 and version != self.VERSION_2_4:
            asserts.skip("HAL version %s is neither v2.1 nor v2.4" % version)

        result = self.dut.hal.camera.get_number_of_cameras()
        count = result.return_type.scalar_value.int32_t
        logging.info("# of found cameras: %s", count)
        asserts.assertTrue(count > 0, "no camera found")
        for index in range(0, count):
            arg = self.dut.hal.camera.camera_info_t(facing=0)
            logging.info(self.dut.hal.camera.get_camera_info(index, arg))

        # uncomment when undefined function is handled gracefully.
        # self.dut.hal.camera.init()
        def camera_device_status_change(callbacks, camera_id, new_status):
            self.call_count_camera_device_status_change += 1
            logging.info("camera_device_status_change")
            logging.info("camera_device_status_change: camera_id = %s", camera_id)
            logging.info("camera_device_status_change: new_status = %s", new_status)
            logging.info("camera_device_status_change: callbacks = %s", callbacks)

        def torch_mode_status_change(callbacks, camera_id, new_status):
            self.profiling.StopHostProfiling("callback_latency_torch_mode_status_change")
            self.call_count_torch_mode_status_change += 1
            logging.info("torch_mode_status_change")
            logging.info("torch_mode_status_change: camera_id = %s", camera_id)
            logging.info("torch_mode_status_change: new_status = %s", new_status)
            logging.info("torch_mode_status_change: callbacks = %s", callbacks)

        my_callback = self.dut.hal.camera.camera_module_callbacks_t(
            camera_device_status_change, torch_mode_status_change)
        self.dut.hal.camera.set_callbacks(my_callback)
        self.profiling.StartHostProfiling("callback_latency_torch_mode_status_change")
        self.dut.hal.camera.common.methods.open()  # note args are skipped
        retries = 0
        while (self.call_count_torch_mode_status_change < 1 and
               retries < self.MAX_RETRIES):
            logging.info("waiting %s %s",
                         self.call_count_camera_device_status_change,
                         self.call_count_torch_mode_status_change)
            time.sleep(1)
            retries += 1
        if self.call_count_torch_mode_status_change < 1:
            # The above callback was not always called (~50% of chance).
            logging.error("Callback not called within %s seconds",
                          self.MAX_RETRIES)


if __name__ == "__main__":
    test_runner.main()
