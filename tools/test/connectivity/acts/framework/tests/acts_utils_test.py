#!/usr/bin/env python3.4
#
#   Copyright 2016 - The Android Open Source Project
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

import time
import unittest

from acts import utils


class ActsUtilsTest(unittest.TestCase):
    """This test class has unit tests for the implementation of everything
    under acts.utils.
    """

    def test_start_standing_subproc(self):
        with self.assertRaisesRegexp(utils.ActsUtilsError,
                                     "Process .* has terminated"):
            utils.start_standing_subprocess("sleep 0", check_health_delay=0.1)

    def test_stop_standing_subproc(self):
        p = utils.start_standing_subprocess("sleep 0")
        time.sleep(0.1)
        with self.assertRaisesRegexp(utils.ActsUtilsError,
                                     "Process .* has terminated"):
            utils.stop_standing_subprocess(p)


if __name__ == "__main__":
    unittest.main()
