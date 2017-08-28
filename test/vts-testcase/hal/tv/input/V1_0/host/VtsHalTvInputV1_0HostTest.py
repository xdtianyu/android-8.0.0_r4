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

from vts.runners.host import asserts
from vts.runners.host import base_test
from vts.runners.host import const
from vts.runners.host import keys
from vts.runners.host import test_runner
from vts.utils.python.controllers import android_device
from vts.utils.python.precondition import precondition_utils


class TvInputHidlTest(base_test.BaseTestClass):
    """Two hello world test cases which use the shell driver."""

    def setUpClass(self):
        """Creates a mirror and init tv input hal."""
        self.dut = self.registerController(android_device)[0]

        self.dut.shell.InvokeTerminal("one")
        self.dut.shell.one.Execute("setenforce 0")  # SELinux permissive mode
        if not precondition_utils.CanRunHidlHalTest(
            self, self.dut, self.dut.shell.one):
            self._skip_all_testcases = True
            return

        if self.coverage.enabled:
            self.coverage.LoadArtifacts()
            self.coverage.InitializeDeviceCoverage(self.dut)

        self.dut.hal.InitHidlHal(target_type="tv_input",
                                 target_basepaths=self.dut.libPaths,
                                 target_version=1.0,
                                 target_package="android.hardware.tv.input",
                                 target_component_name="ITvInput",
                                 bits=int(self.abi_bitness))

        self.dut.shell.InvokeTerminal("one")

    def setUp(self):
        """Setup function that will be called every time before executing each
        test case in the test class."""
        if self.profiling.enabled:
            self.profiling.EnableVTSProfiling(self.dut.shell.one)

    def tearDown(self):
        """TearDown function that will be called every time after executing each
        test case in the test class."""
        if self.profiling.enabled:
            self.profiling.ProcessTraceDataForTestCase(self.dut)
            self.profiling.DisableVTSProfiling(self.dut.shell.one)

    def tearDownClass(self):
        """To be executed when all test cases are finished."""
        if self._skip_all_testcases:
            return

        if self.coverage.enabled:
            self.coverage.SetCoverageData(dut=self.dut, isGlobal=True)

        if self.profiling.enabled:
            self.profiling.ProcessAndUploadTraceData()

    def testGetStreamConfigurations(self):
        configs = self.dut.hal.tv_input.getStreamConfigurations(0)
        logging.info('return value of getStreamConfigurations(0): %s', configs)


if __name__ == "__main__":
    test_runner.main()
