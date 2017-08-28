#!/usr/bin/env python3.4
#
# Copyright (C) 2017 The Android Open Source Project
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

from vts.runners.host import asserts
from vts.runners.host import base_test
from vts.runners.host import const
from vts.runners.host import test_runner
from vts.utils.python.controllers import android_device

ANDROID_O_API_VERSION = 26

class VtsTreblePlatformVersionTest(base_test.BaseTestClass):
    """VTS should run on devices launched with O or later."""

    def setUpClass(self):
        self.dut = self.registerController(android_device)[0]
        self.dut.shell.InvokeTerminal("VtsTreblePlatformVersionTest")

    def getProp(self, prop, required=True):
        """Helper to retrieve a property from device."""

        results = self.dut.shell.VtsTreblePlatformVersionTest.Execute(
            "getprop " + prop)
        if required:
            asserts.assertEqual(results[const.EXIT_CODE][0], 0,
                "getprop must succeed")
        else:
            if results[const.EXIT_CODE][0] != 0:
                logging.info("sysprop %s undefined", prop)
                return None

        result = results[const.STDOUT][0].strip()

        logging.info("getprop {}={}".format(prop, result))

        return result

    def testFirstApiLevel(self):
        """Test that device launched with O or later."""
        try:
            firstApiLevel = self.getProp("ro.product.first_api_level",
                                         required=False)
            if firstApiLevel is None:
                asserts.skip("ro.product.first_api_level undefined")
            firstApiLevel = int(firstApiLevel)
            asserts.assertTrue(firstApiLevel >= ANDROID_O_API_VERSION,
                "VTS can only be run for new launches in O or above")
        except ValueError:
            asserts.fail("Unexpected value returned from getprop")

    def testTrebleEnabled(self):
        """Test that device has Treble enabled."""
        trebleIsEnabledStr = self.getProp("ro.treble.enabled")
        asserts.assertEqual(trebleIsEnabledStr, "true",
            "VTS can only be run for Treble enabled devices")

    def testSdkVersion(self):
        """Test that SDK version >= O (26)."""
        try:
            sdkVersion = int(self.getProp("ro.build.version.sdk"))
            asserts.assertTrue(sdkVersion >= ANDROID_O_API_VERSION,
                "VTS is for devices launching in O or above")
        except ValueError:
            asserts.fail("Unexpected value returned from getprop")

    def testVndkVersion(self):
        """Test that VNDK version is specified."""
        vndkVersion = self.getProp("ro.vendor.vndk.version")
        asserts.assertLess(0, len(vndkVersion),
            "VNDK version is not defined")

if __name__ == "__main__":
    test_runner.main()
