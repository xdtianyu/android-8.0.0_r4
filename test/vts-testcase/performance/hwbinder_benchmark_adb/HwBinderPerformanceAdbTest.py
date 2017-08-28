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
from vts.runners.host import test_runner
from vts.utils.python.controllers import adb
from vts.utils.python.controllers import android_device


class HwBinderPerformanceAdbTest(base_test.BaseTestClass):
    """A test case for the HWBinder performance benchmarking.

    Attributes:
        dut: the target DUT (device under test) instance.
        _cpu_freq: CpuFrequencyScalingController instance of self.dut.
    """

    THRESHOLD = {
        32: {
            "4": 100000,
            "8": 100000,
            "16": 100000,
            "32": 100000,
            "64": 100000,
            "128": 100000,
            "256": 100000,
            "512": 100000,
            "1024": 100000,
            "2k": 100000,
            "4k": 100000,
            "8k": 110000,
            "16k": 120000,
            "32k": 140000,
            "64k": 170000,
        },
        64: {
            "4": 100000,
            "8": 100000,
            "16": 100000,
            "32": 100000,
            "64": 100000,
            "128": 100000,
            "256": 100000,
            "512": 100000,
            "1024": 100000,
            "2k": 100000,
            "4k": 100000,
            "8k": 110000,
            "16k": 120000,
            "32k": 150000,
            "64k": 200000,
        }
    }
    LABEL_PREFIX_BINDERIZE = "BM_sendVec_binderize/"
    LABEL_PREFIX_PASSTHROUGH = "BM_sendVec_passthrough/"

    def setUpClass(self):
        required_params = ["hidl_hal_mode"]
        self.getUserParams(required_params)
        self.dut = self.registerController(android_device, False)[0]
        # Reboot target without restarting VTS services.
        self.dut.reboot(False)
        self.dut.stop()

    def tearDownClass(self):
        self.dut.start()

    def testRunBenchmark32Bit(self):
        """A testcase which runs the 32-bit benchmark."""
        self.RunBenchmark(32)

    def testRunBenchmark64Bit(self):
        """A testcase which runs the 64-bit benchmark."""
        self.RunBenchmark(64)

    def RunBenchmark(self, bits):
        """Runs the native binary and parses its result.

        Args:
            bits: integer (32 or 64), the number of bits in a word chosen
                  at the compile time (e.g., 32- vs. 64-bit library).
        """
        # Runs the benchmark.
        logging.info(
            "Start to run the benchmark with HIDL mode %s (%s bit mode)",
            self.hidl_hal_mode, bits)
        binary = "/data/local/tmp/%s/libhwbinder_benchmark%s" % (bits, bits)

        self.dut.adb.shell("chmod 755 %s" % binary)

        try:
            result = self.dut.adb.shell(
                "LD_LIBRARY_PATH=/system/lib%s:/data/local/tmp/%s/hw:"
                "/data/local/tmp/%s:"
                "$LD_LIBRARY_PATH %s -m %s" %
                (bits, bits, bits, binary, self.hidl_hal_mode.encode("utf-8")))
        except adb.AdbError as e:
            asserts.fail("HwBinderPerformanceTest failed.")

        # Parses the result.
        stdout_lines = result.split("\n")
        logging.info("stdout: %s", stdout_lines)
        label_result = []
        value_result = []
        prefix = (self.LABEL_PREFIX_BINDERIZE
                  if self.hidl_hal_mode == "BINDERIZE" else
                  self.LABEL_PREFIX_PASSTHROUGH)
        for line in stdout_lines:
            if line.startswith(prefix):
                tokens = line.split()
                benchmark_name = tokens[0]
                time_in_ns = tokens[1].split()[0]
                logging.info(benchmark_name)
                logging.info(time_in_ns)
                label_result.append(benchmark_name.replace(prefix, ""))
                value_result.append(int(time_in_ns))

        logging.info("result label for %sbits: %s", bits, label_result)
        logging.info("result value for %sbits: %s", bits, value_result)
        # To upload to the web DB.
        self.web.AddProfilingDataLabeledVector(
            "hwbinder_vector_roundtrip_latency_benchmark_%sbits" % bits,
            label_result,
            value_result,
            x_axis_label="Message Size (Bytes)",
            y_axis_label="Roundtrip HwBinder RPC Latency (naonseconds)")

        # Assertions to check the performance requirements
        for label, value in zip(label_result, value_result):
            if label in self.THRESHOLD[bits]:
                asserts.assertLess(
                    value, self.THRESHOLD[bits][label],
                    "%s ns for %s is longer than the threshold %s ns" % (
                        value, label, self.THRESHOLD[bits][label]))


if __name__ == "__main__":
    test_runner.main()
