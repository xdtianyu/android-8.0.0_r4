# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# This file has been automatically generated. Do not edit!

AUTHOR = 'ARC Team'
NAME = 'cheets_CTS.arm.zzz.android.monkey'
ATTRIBUTES = 'suite:cts'
DEPENDENCIES = 'arc'
JOB_RETRIES = 2
TEST_TYPE = 'server'
TIME = 'LENGTHY'

DOC = ('Run package zzz.android.monkey of the '
       'Android 6.0_r16 Compatibility Test Suite (CTS), build 3736419,'
       'using arm ABI in the ARC container.')

def run_CTS(machine):
    host = hosts.create_host(machine)
    job.run_test(
        'cheets_CTS',
        host=host,
        iterations=1,
        tag='zzz.android.monkey',
        target_package='zzz.android.monkey',
        bundle='arm',
        timeout=3600)

parallel_simple(run_CTS, machines)