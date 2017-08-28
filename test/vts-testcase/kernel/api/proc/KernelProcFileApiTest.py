#!/usr/bin/env python
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

from vts.testcases.kernel.api.proc import ProcCmdlineTest
from vts.testcases.kernel.api.proc import ProcCpuInfoTest
from vts.testcases.kernel.api.proc import ProcKmsgTest
from vts.testcases.kernel.api.proc import ProcMapsTest
from vts.testcases.kernel.api.proc import ProcMemInfoTest
from vts.testcases.kernel.api.proc import ProcModulesTest
from vts.testcases.kernel.api.proc import ProcMountsTest
from vts.testcases.kernel.api.proc import ProcQtaguidCtrlTest
from vts.testcases.kernel.api.proc import ProcRemoveUidRangeTest
from vts.testcases.kernel.api.proc import ProcSimpleFileTests
from vts.testcases.kernel.api.proc import ProcShowUidStatTest
from vts.testcases.kernel.api.proc import ProcStatTest
from vts.testcases.kernel.api.proc import ProcVersionTest
from vts.testcases.kernel.api.proc import ProcVmallocInfoTest
from vts.testcases.kernel.api.proc import ProcZoneInfoTest

from vts.utils.python.controllers import android_device
from vts.utils.python.file import file_utils

TEST_OBJECTS = {
    ProcCmdlineTest.ProcCmdlineTest(),
    ProcCpuInfoTest.ProcCpuInfoTest(),
    ProcKmsgTest.ProcKmsgTest(),
    ProcSimpleFileTests.ProcKptrRestrictTest(),
    ProcMapsTest.ProcMapsTest(),
    ProcMemInfoTest.ProcMemInfoTest(),
    ProcSimpleFileTests.ProcMmapMinAddrTest(),
    ProcSimpleFileTests.ProcMmapRndBitsTest(),
    ProcModulesTest.ProcModulesTest(),
    ProcMountsTest.ProcMountsTest(),
    ProcSimpleFileTests.ProcOverCommitMemoryTest(),
    ProcQtaguidCtrlTest.ProcQtaguidCtrlTest(),
    ProcSimpleFileTests.ProcRandomizeVaSpaceTest(),
    ProcRemoveUidRangeTest.ProcRemoveUidRangeTest(),
    ProcShowUidStatTest.ProcShowUidStatTest(),
    ProcStatTest.ProcStatTest(),
    ProcVersionTest.ProcVersionTest(),
    ProcVmallocInfoTest.ProcVmallocInfoTest(),
    ProcZoneInfoTest.ProcZoneInfoTest(),
}

TEST_OBJECTS_64 = {
    ProcSimpleFileTests.ProcMmapRndCompatBitsTest(),
}


class KernelProcFileApiTest(base_test.BaseTestClass):
    """Test cases which check content of proc files."""

    def setUpClass(self):
        self.dut = self.registerController(android_device)[0]
        self.dut.shell.InvokeTerminal(
            "KernelApiTest")  # creates a remote shell instance.
        self.shell = self.dut.shell.KernelApiTest

    def runProcFileTest(self, test_object):
        """Reads from the file and checks that it parses and the content is valid.

        Args:
            test_object: inherits KernelProcFileTestBase, contains the test functions
        """
        asserts.skipIf(test_object in TEST_OBJECTS_64 and not self.dut.is64Bit,
                       "Skip test for 64-bit kernel.")
        filepath = test_object.get_path()
        file_utils.assertPermissionsAndExistence(
            self.shell, filepath, test_object.get_permission_checker())

        logging.info("Testing format of %s", filepath)

        asserts.assertTrue(
            test_object.prepare_test(self.shell), "Setup failed!")

        if not test_object.test_format():
            return

        file_content = self.ReadFileContent(filepath)
        try:
            parse_result = test_object.parse_contents(file_content)
        except (SyntaxError, ValueError, IndexError) as e:
            asserts.fail("Failed to parse! " + str(e))
        asserts.assertTrue(
            test_object.result_correct(parse_result), "Results not valid!")

    def generateProcFileTests(self):
        """Run all proc file tests."""
        self.runGeneratedTests(
            test_func=self.runProcFileTest,
            settings=TEST_OBJECTS.union(TEST_OBJECTS_64),
            name_func=lambda test_obj: "test" + test_obj.__class__.__name__)

    def ReadFileContent(self, filepath):
        """Read the content of a file and perform assertions.

        Args:
            filepath: string, path to file

        Returns:
            string, content of file
        """
        cmd = "cat %s" % filepath
        results = self.shell.Execute(cmd)

        # checks the exit code
        asserts.assertEqual(
            results[const.EXIT_CODE][0], 0,
            "%s: Error happened while reading the file." % filepath)

        return results[const.STDOUT][0]


if __name__ == "__main__":
    test_runner.main()
