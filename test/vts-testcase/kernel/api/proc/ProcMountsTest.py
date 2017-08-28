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
from vts.testcases.kernel.api.proc import KernelProcFileTestBase
from vts.testcases.kernel.api.proc.KernelProcFileTestBase import repeat_rule, literal_token


class ProcMountsTest(KernelProcFileTestBase.KernelProcFileTestBase):
    '''/proc/self/mounts lists the mounted filesystems.

    /proc/mounts must symlink to this file.'''

    def parse_contents(self, contents):
        if len(contents) == 0 or contents[-1] != '\n':
            raise SyntaxError('Missing final newline')
        result = []
        for line in contents.split('\n')[:-1]:
            parsed = line.split(' ')
            parsed[3] = parsed[3].split(',')
            result.append(parsed)
        return result

    def result_correct(self, parse_results):
        for line in parse_results:
            if len(line[3]) < 1 or line[3][0] not in {'rw', 'ro'}:
                logging.error("First attribute must be rw or ro")
                return False
            if line[4] != '0' or line[5] != '0':
                logging.error("Last 2 columns must be 0")
                return False
        return True

    def prepare_test(self, shell):
        # Follow the symlink
        results = shell.Execute('readlink /proc/mounts')
        if results[const.EXIT_CODE][0] != 0:
            return False
        return results[const.STDOUT][0] == 'self/mounts\n'

    def get_path(self):
        return "/proc/self/mounts"
