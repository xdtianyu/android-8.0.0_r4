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
import os

from vts.runners.host import asserts
from vts.runners.host import base_test
from vts.runners.host import const
from vts.runners.host import test_runner
from vts.utils.python.controllers import android_device


class HidlTraceRecorder(base_test.BaseTestClass):
    """A HIDL HAL API trace recorder.

    This class uses an apk which is packaged as part of VTS. It uses to test the
    VTS TF's instrumentation preparer and post-processing modules. Those two
    Java TF-side modules are cherry-picked to CTS to collect HIDL traces while
    running various CTS test cases without having to package them as part of
    VTS.
    """

    CTS_TESTS = [
        {"apk": "CtsAccelerationTestCases.apk",
         "package": "android.acceleration.cts",
         "runner": "android.support.test.runner.AndroidJUnitRunner"},
        # TODO(yim): reenable once tests in that apk are no more flaky.
        # {"apk": "CtsSensorTestCases.apk",
        #  "package": "android.hardware.sensor.cts",
        #  "runner": "android.support.test.runner.AndroidJUnitRunner"},
        ]
    TMP_DIR = "/data/local/tmp"

    def setUpClass(self):
        self.dut = self.registerController(android_device)[0]

    def testRunCtsSensorTestCases(self):
        """Runs all test cases in CtsSensorTestCases.apk."""
        for cts_test in self.CTS_TESTS:
          logging.info("Run %s", cts_test["apk"])
          self.dut.adb.shell(
              "am instrument -w -r %s/%s" % (cts_test["package"],
                                             cts_test["runner"]))

if __name__ == "__main__":
    test_runner.main()
