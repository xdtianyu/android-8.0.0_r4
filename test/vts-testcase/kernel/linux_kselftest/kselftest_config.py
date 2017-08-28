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

from vts.testcases.kernel.linux_kselftest import test_case

class ConfigKeys(object):
    TEST_TYPE = "test_type"

class ExitCode(object):
    """Exit codes for test binaries and test scripts."""
    KSFT_PASS = 0
    KSFT_FAIL = 1
    KSFT_XPASS = 2
    KSFT_XFAIL = 3
    KSFT_SKIP = 4

# Directory on the target where the tests are copied.
KSFT_DIR = "/data/local/tmp/linux-kselftest"

# Presubmit, stable, and staging lists are always mutually exclusive.
KSFT_CASES_PRESUBMIT = map(lambda x: test_case.LinuxKselftestTestcase(*(x)), [
    ("futex/functional/futex_wait_timeout", ["arm", "x86"], [32, 64]),
    ("futex/functional/futex_wait_wouldblock", ["arm", "x86"], [32, 64]),
    ("futex/functional/futex_requeue_pi_mismatched_ops", ["arm", "x86"], [32, 64]),
    ("futex/functional/futex_wait_uninitialized_heap", ["arm", "x86"], [32]),
    ("futex/functional/futex_wait_private_mapped_file", ["arm", "x86"], [32, 64]),
    ("net/socket", ["arm", "x86"], [32, 64]),
])

KSFT_CASES_STABLE = map(lambda x: test_case.LinuxKselftestTestcase(*(x)), [
    ("net/psock_tpacket", ["arm", "x86"], [32, 64]),
    ("ptrace/peeksiginfo", ["arm", "x86"], [64]),
    ("timers/posix_timers", ["arm", "x86"], [32, 64]),
    ("timers/nanosleep", ["arm", "x86"], [32, 64]),
    ("timers/nsleep-lat", ["arm", "x86"], [32, 64]),
    ("timers/set-timer-lat", ["arm", "x86"], [32, 64]),
    ("timers/inconsistency-check", ["arm", "x86"], [32, 64]),
    ("timers/raw_skew", ["arm", "x86"], [32, 64]),
    ("timers/threadtest", ["arm", "x86"], [32, 64]),
    ("timers/set-tai", ["arm", "x86"], [32, 64]),
    ("timers/valid-adjtimex", ["arm", "x86"], [64]),
])

KSFT_CASES_STAGING = map(lambda x: test_case.LinuxKselftestTestcase(*(x)), [
# TODO(trong): enable pstore test.
#    ("pstore/pstore_tests", ["arm", "x86"], [32, 64]),
    ("seccomp/seccomp_bpf", ["arm", "x86"], [32, 64]),
    ("timers/alarmtimer-suspend", ["arm", "x86"], [32, 64]),
    ("x86/single_step_syscall", ["x86"], [32, 64]),
    ("x86/sysret_ss_attrs", ["x86"], [32]),
    ("x86/syscall_nt", ["x86"], [32, 64]),
    ("x86/ptrace_syscall", ["x86"], [32, 64]),
    ("x86/test_mremap_vdso", ["x86"], [32, 64]),
    ("x86/check_initial_reg_state", ["x86"], [32, 64]),
    ("x86/ldt_gdt", ["x86"], [32, 64]),
    ("x86/syscall_arg_fault", ["x86"], [32]),
    ("x86/test_syscall_vdso", ["x86"], [32]),
    ("x86/unwind_vdso", ["x86"], [32]),
    ("x86/test_FCMOV", ["x86"], [32]),
    ("x86/test_FCOMI", ["x86"], [32]),
    ("x86/test_FISTTP", ["x86"], [32]),
    ("x86/vdso_restorer", ["x86"], [32]),
])
