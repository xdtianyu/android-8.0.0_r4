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
import xml.etree.ElementTree

from vts.runners.host import asserts
from vts.runners.host import const
from vts.runners.host import test_runner

from vts.testcases.template.binary_test import binary_test
from vts.testcases.template.binary_test import binary_test_case
from vts.testcases.template.gtest_binary_test import gtest_test_case


class GtestBinaryTest(binary_test.BinaryTest):
    '''Base class to run gtests binary on target.

    Attributes:
        _dut: AndroidDevice, the device under test as config
        shell: ShellMirrorObject, shell mirror
        testcases: list of GtestTestCase objects, list of test cases to run
        tags: all the tags that appeared in binary list
        DEVICE_TEST_DIR: string, temp location for storing binary
        TAG_PATH_SEPARATOR: string, separator used to separate tag and path
    '''

    # @Override
    def CreateTestCase(self, path, tag=''):
        '''Create a list of GtestTestCase objects from a binary path.

        Args:
            path: string, absolute path of a gtest binary on device
            tag: string, a tag that will be appended to the end of test name

        Returns:
            A list of GtestTestCase objects
        '''
        working_directory = self.working_directory[
            tag] if tag in self.working_directory else None
        envp = self.envp[tag] if tag in self.envp else ''
        args = self.args[tag] if tag in self.args else ''
        ld_library_path = self.ld_library_path[
            tag] if tag in self.ld_library_path else None
        profiling_library_path = self.profiling_library_path[
            tag] if tag in self.ld_library_path else None

        args += " --gtest_list_tests"
        list_test_case = binary_test_case.BinaryTestCase(
            'gtest_list_tests',
            path,
            path,
            tag,
            self.PutTag,
            working_directory,
            ld_library_path,
            profiling_library_path,
            envp=envp,
            args=args)
        cmd = ['chmod 755 %s' % path, list_test_case.GetRunCommand()]
        cmd_results = self.shell.Execute(cmd)
        if any(cmd_results[
                const.
                EXIT_CODE]):  # gtest binary doesn't exist or is corrupted
            logging.error('Failed to list test cases from binary %s' % path)

        test_cases = []

        test_suite = ''
        for line in cmd_results[const.STDOUT][1].split('\n'):
            line = str(line)
            if not len(line.strip()):
                continue
            elif line.startswith(' '):  # Test case name
                test_name = line.split('#')[0].strip()
                test_case = gtest_test_case.GtestTestCase(
                    test_suite, test_name, path, tag, self.PutTag,
                    working_directory, ld_library_path, profiling_library_path)
                logging.info('Gtest test case: %s' % test_case)
                test_cases.append(test_case)
            else:  # Test suite name
                test_suite = line.strip()
                if test_suite.endswith('.'):
                    test_suite = test_suite[:-1]

        return test_cases

    # @Override
    def VerifyTestResult(self, test_case, command_results):
        '''Parse Gtest xml result output.

        Sample
        <testsuites tests="1" failures="1" disabled="0" errors="0"
         timestamp="2017-05-24T18:32:10" time="0.012" name="AllTests">
          <testsuite name="ConsumerIrHidlTest"
           tests="1" failures="1" disabled="0" errors="0" time="0.01">
            <testcase name="TransmitTest" status="run" time="0.01"
             classname="ConsumerIrHidlTest">
              <failure message="hardware/interfaces..." type="">
                <![CDATA[hardware/interfaces...]]>
              </failure>
            </testcase>
          </testsuite>
        </testsuites>

        Args:
            test_case: GtestTestCase object, the test being run. This param
                       is not currently used in this method.
            command_results: dict of lists, shell command result
        '''
        asserts.assertTrue(command_results, 'Empty command response.')
        asserts.assertEqual(
            len(command_results), 3, 'Abnormal command response.')
        for item in command_results[const.STDOUT]:
            if item and item.strip():
                logging.info(item)
        for item in command_results[const.STDERR]:
            if item and item.strip():
                logging.error(item)

        asserts.assertFalse(command_results[const.EXIT_CODE][1],
            'Failed to show Gtest XML output: %s' % command_results)

        xml_str = command_results[const.STDOUT][1].strip()
        root = xml.etree.ElementTree.fromstring(xml_str)
        asserts.assertEqual(root.get('tests'), '1', 'No tests available')
        if root.get('errors') != '0' or root.get('failures') != '0':
            messages = [x.get('message') for x in root.findall('.//failure')]
            asserts.fail('\n'.join([x for x in messages if x]))
        asserts.skipIf(root.get('disabled') == '1', 'Gtest test case disabled')


if __name__ == "__main__":
    test_runner.main()
