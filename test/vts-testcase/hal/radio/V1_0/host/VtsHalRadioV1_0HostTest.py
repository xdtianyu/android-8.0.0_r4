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
from vts.utils.python.precondition import precondition_utils


class VtsHalRadioV1_0HostTest(base_test.BaseTestClass):
    """A simple testcase for the VEHICLE HIDL HAL."""

    def setUpClass(self):
        """Creates a mirror and init vehicle hal."""
        self.dut = self.registerController(android_device)[0]

        self.dut.shell.InvokeTerminal("one")
        self.dut.shell.one.Execute("setenforce 0")  # SELinux permissive mode
        if not precondition_utils.CanRunHidlHalTest(
            self, self.dut, self.dut.shell.one):
            self._skip_all_testcases = True
            return

        if self.profiling.enabled:
            self.profiling.EnableVTSProfiling(self.dut.shell.one)

        self.dut.hal.InitHidlHal(
            target_type="radio",
            target_basepaths=self.dut.libPaths,
            target_version=1.0,
            target_package="android.hardware.radio",
            target_component_name="IRadio",
            hw_binder_service_name="Radio",
            bits=int(self.abi_bitness))

        self.radio = self.dut.hal.radio  # shortcut
        self.radio_types = self.dut.hal.radio.GetHidlTypeInterface("types")
        logging.info("Radio types: %s", self.radio_types)

    def tearDownClass(self):
        """ If profiling is enabled for the test, collect the profiling data
            and disable profiling after the test is done.
        """
        if not self._skip_all_testcases and self.profiling.enabled:
            self.profiling.ProcessAndUploadTraceData()

    def tearDown(self):
        """Process trace data.
        """
        if self.profiling.enabled:
            self.profiling.ProcessTraceDataForTestCase(self.dut)
            self.profiling.DisableVTSProfiling(self.dut.shell.one)

    def testHelloWorld(self):
        logging.info('hello world')


if __name__ == "__main__":
    test_runner.main()
