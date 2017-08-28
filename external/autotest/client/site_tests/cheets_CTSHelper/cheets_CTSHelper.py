# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time
from autotest_lib.client.common_lib.cros import arc
from autotest_lib.client.common_lib.cros import chrome
from autotest_lib.client.bin import test


class cheets_CTSHelper(test.test):
    """Helper to run Android's CTS on autotest.

    Android CTS needs a running Android, which depends on a logged in ChromeOS
    user. This helper class logs in to ChromeOS and waits for Android boot
    complete.

    We do not log out, and the machine will be rebooted after test.
    """
    version = 1

    def run_once(self, count=None):
        if count:
            # Run stress test by logging in and starting ARC several times.
            # Each iteration is about 15s on Samus.
            for i in range(count):
                logging.info('cheets_CTSHelper iteration %d', i)
                with chrome.Chrome(
                        arc_mode=arc.arc_common.ARC_MODE_ENABLED) as _:
                    time.sleep(2)
        else:
            # Utility used by server tests to login. We do not log out, and
            # ensure the machine will be rebooted after test.
            try:
                self.chrome = chrome.Chrome(
                            arc_mode=arc.arc_common.ARC_MODE_ENABLED,
                            init_network_controller=False)
            except:
                # We are going to paper over some failures here. Notice these
                # should still be detected by regularly running
                # cheets_CTSHelper.stress.
                logging.error('Could not start Chrome. Retrying soon...')
                # Give system a chance to calm down.
                time.sleep(20)
                self.chrome = chrome.Chrome(
                            arc_mode=arc.arc_common.ARC_MODE_ENABLED,
                            num_tries=3,
                            init_network_controller=False)
