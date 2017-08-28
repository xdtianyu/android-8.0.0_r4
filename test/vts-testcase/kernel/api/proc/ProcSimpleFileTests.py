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

from vts.testcases.kernel.api.proc import KernelProcFileTestBase

from vts.utils.python.file import file_utils


class ProcKptrRestrictTest(KernelProcFileTestBase.KernelProcFileTestBase):
    '''/proc/sys/kernel/kptr_restrict determines whether kernel pointers are printed
    in proc files.
    '''

    def parse_contents(self, contents):
        return self.parse_line("{:d}\n", contents)[0]

    def result_correct(self, result):
        return result >= 0 and result <= 4

    def get_path(self):
        return "/proc/sys/kernel/kptr_restrict"

    def get_permission_checker(self):
        """Get r/w file permission checker.
        """
        return file_utils.IsReadWrite


class ProcRandomizeVaSpaceTest(KernelProcFileTestBase.KernelProcFileTestBase):
    '''/proc/sys/kernel/randomize_va_space determines the address layout randomization
    policy for the system.
    '''

    def parse_contents(self, contents):
        return self.parse_line("{:d}\n", contents)[0]

    def result_correct(self, result):
        return result >= 0 and result <= 2

    def get_path(self):
        return "/proc/sys/kernel/randomize_va_space"

    def get_permission_checker(self):
        """Get r/w file permission checker.
        """
        return file_utils.IsReadWrite


class ProcOverCommitMemoryTest(KernelProcFileTestBase.KernelProcFileTestBase):
    '''/proc/sys/vm/overcommit_memory determines the kernel virtual memory accounting mode.
    '''

    def parse_contents(self, contents):
        return self.parse_line("{:d}\n", contents)[0]

    def result_correct(self, result):
        return result >= 0 and result <= 2

    def get_path(self):
        return "/proc/sys/vm/overcommit_memory"

    def get_permission_checker(self):
        """Get r/w file permission checker.
        """
        return file_utils.IsReadWrite


class ProcMmapMinAddrTest(KernelProcFileTestBase.KernelProcFileTestBase):
    '''/proc/sys/vm/mmap_min_addr specifies the minimum address that can be mmap'd.
    '''

    def parse_contents(self, contents):
        return self.parse_line("{:d}\n", contents)[0]

    def get_path(self):
        return "/proc/sys/vm/mmap_min_addr"

    def get_permission_checker(self):
        """Get r/w file permission checker.
        """
        return file_utils.IsReadWrite


class ProcMmapRndBitsTest(KernelProcFileTestBase.KernelProcFileTestBase):
    '''/proc/sys/vm/mmap_rnd_(compat_)bits specifies the amount of randomness in mmap'd
    addresses. Must be >= 8.
    '''

    def parse_contents(self, contents):
        return self.parse_line("{:d}\n", contents)[0]

    def result_correct(self, result):
        return result >= 8

    def get_path(self):
        return "/proc/sys/vm/mmap_rnd_bits"

    def get_permission_checker(self):
        """Get r/w file permission checker.
        """
        return file_utils.IsReadWrite


class ProcMmapRndCompatBitsTest(ProcMmapRndBitsTest):
    def get_path(self):
        return "/proc/sys/vm/mmap_rnd_compat_bits"
