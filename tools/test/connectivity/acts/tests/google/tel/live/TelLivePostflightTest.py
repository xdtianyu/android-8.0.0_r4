#!/usr/bin/env python3.4
#
#   Copyright 2017 - Google
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
"""
    Test Script for Telephony Post Flight check.
"""

from acts.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts.asserts import fail


class TelLivePostflightTest(TelephonyBaseTest):
    @TelephonyBaseTest.tel_test_wrap
    def test_check_crash(self):
        msg = ""
        for ad in self.android_devices:
            post_crash = ad.check_crash_report()
            pre_crash = getattr(ad, "crash_report_preflight", [])
            crash_diff = list(set(post_crash).difference(set(pre_crash)))
            if crash_diff:
                msg += "%s find new crash reports %s" % (ad.serial, crash_diff)
                ad.pull_files(crash_diff)
                ad.log.error("Find new crash reports %s", crash_diff)
        if msg:
            fail(msg)
        return True
