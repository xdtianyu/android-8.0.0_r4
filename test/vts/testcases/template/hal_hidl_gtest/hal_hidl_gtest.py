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

from vts.runners.host import const
from vts.runners.host import keys
from vts.runners.host import test_runner
from vts.testcases.template.gtest_binary_test import gtest_binary_test
from vts.utils.python.controllers import android_device
from vts.utils.python.cpu import cpu_frequency_scaling


class HidlHalGTest(gtest_binary_test.GtestBinaryTest):
    '''Base class to run a VTS target-side HIDL HAL test.

    Attributes:
        DEVICE_TEST_DIR: string, temp location for storing binary
        TAG_PATH_SEPARATOR: string, separator used to separate tag and path
        tags: all the tags that appeared in binary list
        testcases: list of GtestTestCase objects, list of test cases to run
        _cpu_freq: CpuFrequencyScalingController instance of a target device.
        _dut: AndroidDevice, the device under test as config
    '''

    def setUpClass(self):
        """Checks precondition."""
        super(HidlHalGTest, self).setUpClass()

        opt_params = [keys.ConfigKeys.IKEY_SKIP_IF_THERMAL_THROTTLING]
        self.getUserParams(opt_param_names=opt_params)

        self._skip_if_thermal_throttling = self.getUserParam(
                keys.ConfigKeys.IKEY_SKIP_IF_THERMAL_THROTTLING,
                default_value=False)

        if not self._skip_all_testcases:
            logging.info("Disable CPU frequency scaling")
            self._cpu_freq = cpu_frequency_scaling.CpuFrequencyScalingController(
                self._dut)
            self._cpu_freq.DisableCpuScaling()
        else:
            self._cpu_freq = None

    def CreateTestCases(self):
        """Create testcases and conditionally enable passthrough mode.

        Create testcases as defined in HidlHalGtest. If the passthrough option
        is provided in the configuration or if coverage is enabled, enable
        passthrough mode on the test environment.
        """
        super(HidlHalGTest, self).CreateTestCases()

        passthrough_opt = self.getUserParam(
            keys.ConfigKeys.IKEY_PASSTHROUGH_MODE, default_value=False)

        # Enable coverage if specified in the configuration or coverage enabled.
        # TODO(ryanjcampbell@) support binderized mode
        if passthrough_opt or self.coverage.enabled:
            self._EnablePassthroughMode()

    def _EnablePassthroughMode(self):
        """Enable passthrough mode by setting getStub to true.

        This funciton should be called after super class' setupClass method.
        If called before setupClass, user_params will be changed in order to
        trigger setupClass method to invoke this method again.
        """
        if self.testcases:
            for test_case in self.testcases:
                envp = ' %s=true' % const.VTS_HAL_HIDL_GET_STUB
                test_case.envp += envp
        else:
            logging.warn('No test cases are defined yet. Maybe setupClass '
                         'has not been called. Changing user_params to '
                         'enable passthrough mode option.')
            self.user_params[keys.ConfigKeys.IKEY_PASSTHROUGH_MODE] = True

    def setUp(self):
        """Skips the test case if thermal throttling lasts for 30 seconds."""
        super(HidlHalGTest, self).setUp()

        if self._skip_if_thermal_throttling:
            self._cpu_freq.SkipIfThermalThrottling(retry_delay_secs=30)

    def tearDown(self):
        """Skips the test case if there is thermal throttling."""
        if self._skip_if_thermal_throttling:
            self._cpu_freq.SkipIfThermalThrottling()

        super(HidlHalGTest, self).tearDown()

    def tearDownClass(self):
        """Turns off CPU frequency scaling."""
        if not self._skip_all_testcases:
            logging.info("Enable CPU frequency scaling")
            self._cpu_freq.EnableCpuScaling()

        super(HidlHalGTest, self).tearDownClass()


if __name__ == "__main__":
    test_runner.main()
