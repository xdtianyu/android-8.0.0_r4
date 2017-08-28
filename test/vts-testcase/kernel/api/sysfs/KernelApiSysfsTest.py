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
import re

from vts.runners.host import asserts
from vts.runners.host import base_test
from vts.runners.host import test_runner
from vts.utils.python.controllers import android_device
from vts.utils.python.file import file_utils


class KernelApiSysfsTest(base_test.BaseTestClass):
    '''Test cases which check sysfs files.'''

    def setUpClass(self):
        self.dut = self.registerController(android_device)[0]
        self.dut.shell.InvokeTerminal(
            'default')  # creates a remote shell instance.
        self.shell = self.dut.shell.default

    def ConvertToInteger(self, text):
        '''Check whether a given text is interger.

        Args:
            text: object, usually a string representing the content of a file

        Returns:
            bool, True if is integer
        '''
        try:
            return int(text)
        except ValueError as e:
            logging.exception(e)
            asserts.fail('Content "%s" is not integer' % text)

    def MatchRegex(self, regex, string):
        '''Check whether a string completely matches a given regex.

        Assertions will fail if given string is not a complete match.

        Args:
            regex: string, regex pattern to match
            string: string, given string for matching
        '''
        pattern = re.compile(regex)
        match = pattern.match(string)
        message = 'String "%s" is not a complete match of regex "%s".' % (
            string, regex)
        asserts.assertTrue(match is not None, message)
        asserts.assertEqual(match.start(), 0, message)
        asserts.assertEqual(match.end(), len(string), message)

    def IsReadOnly(self, path):
        '''Check whether a given path is read only.

        Assertion will fail if given path does not exist or is not read only.
        '''
        permission = ''
        try:
            permission = file_utils.GetPermission(path, self.shell)
        except IOError as e:
            logging.exception(e)
            asserts.fail('Path "%s" does not exist or has invalid '
                         'permission bits' % path)

        try:
            asserts.assertTrue(
                file_utils.IsReadOnly(permission),
                'path %s is not read only' % path)
        except IOError as e:
            logging.exception(e)
            asserts.fail('Got invalid permission bits "%s" for path "%s"' %
                         (permission, path))

    def testCpuOnlineFormat(self):
        '''Check the format of cpu online file.

        Confirm /sys/devices/system/cpu/online exists and is read-only.
        Parse contents to ensure it is a comma-separated series of ranges
        (%d-%d) and/or integers.
        '''
        filepath = '/sys/devices/system/cpu/online'
        self.IsReadOnly(filepath)
        content = file_utils.ReadFileContent(filepath, self.shell)
        regex = '(\d+(-\d+)?)(,\d+(-\d+)?)*'
        if content.endswith('\n'):
            content = content[:-1]
        self.MatchRegex(regex, content)

    def testLastResumeReason(self):
        '''Check /sys/kernel/wakeup_reasons/last_resume_reason.'''
        filepath = '/sys/kernel/wakeup_reasons/last_resume_reason'
        self.IsReadOnly(filepath)

    def testKernelMax(self):
        '''Check the value of /sys/devices/system/cpu/kernel_max.'''
        filepath = '/sys/devices/system/cpu/kernel_max'
        self.IsReadOnly(filepath)
        content = file_utils.ReadFileContent(filepath, self.shell)
        self.ConvertToInteger(content)


if __name__ == "__main__":
    test_runner.main()
