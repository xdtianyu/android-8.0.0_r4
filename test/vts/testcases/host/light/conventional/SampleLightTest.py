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

from vts.runners.host import base_test
from vts.runners.host import test_runner
from vts.utils.python.controllers import android_device


class SampleLightTest(base_test.BaseTestClass):
    """A sample testcase for the legacy lights HAL."""

    def setUpClass(self):
        self.dut = self.registerController(android_device)[0]
        self.dut.hal.InitConventionalHal(target_type="light",
                                         target_basepaths=["/data/local/tmp/64/hw"],
                                         target_version=1.0,
                                         bits=64,
                                         target_package="hal.conventional.light")
        self.dut.hal.light.OpenConventionalHal("backlight")

    def testTurnOnBackgroundLight(self):
        """A simple testcase which just calls a function."""
        # TODO: support ability to test non-instrumented hals.
        arg = self.dut.hal.light.light_state_t(
            color=0xffffff00,
            flashMode=self.dut.hal.light.LIGHT_FLASH_HARDWARE,
            flashOnMs=100,
            flashOffMs=200,
            brightnessMode=self.dut.hal.light.BRIGHTNESS_MODE_USER)
        self.dut.hal.light.set_light(None, arg)

    def testTurnOnBackgroundLightUsingInstrumentedLib(self):
        """A simple testcase which just calls a function."""
        arg = self.dut.hal.light.light_state_t(
            color=0xffffff00,
            flashMode=self.dut.hal.light.LIGHT_FLASH_HARDWARE,
            flashOnMs=100,
            flashOffMs=200,
            brightnessMode=self.dut.hal.light.BRIGHTNESS_MODE_USER)
        logging.debug(self.dut.hal.light.set_light(None, arg))


if __name__ == "__main__":
    test_runner.main()
