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


class SampleQtaguidTest(base_test.BaseTestClass):
    """A sample testcase for the libcutil's qtaguid module."""

    def setUpClass(self):
        self.dut = self.registerController(android_device)[0]
        self.dut.lib.InitSharedLib(target_type="vndk_libcutils",
                                   target_basepaths=["/system/lib64"],
                                   target_version=1.0,
                                   target_filename="libcutils.so",
                                   bits=64,
                                   handler_name="libcutils",
                                   target_packege="lib.ndk.bionic")

    def testCall(self):
        """A simple testcase which just calls a function."""
        result = self.dut.lib.libcutils.qtaguid_tagSocket(0, 1, 2)
        logging.info("result: %s", result)


if __name__ == "__main__":
    test_runner.main()
