# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import os

from autotest_lib.client.bin import test, utils
from autotest_lib.client.common_lib import error

class security_CroshModules(test.test):
    """Make sure no surprise crosh modules end up installed."""

    version = 1
    CROSH_DIR = '/usr/share/crosh'
    MODULE_DIRS = ('dev.d', 'extra.d', 'removable.d')

    def load_whitelist(self):
        """Load the list of permitted files."""
        with open(os.path.join(self.bindir, 'whitelist')) as fp:
            return set(line.strip() for line in fp
                       if line and not line.startswith('#'))


    def run_once(self):
        """
        Do a find on the system for crosh modules and compare against whitelist.
        Fail if unknown modules are found on the system.
        """
        cmd = 'cd %s && find %s -type f' % (
            self.CROSH_DIR, ' '.join(self.MODULE_DIRS))
        cmd_output = utils.system_output(cmd, ignore_status=True)
        observed_set = set(cmd_output.splitlines())
        baseline_set = self.load_whitelist()

        # Report observed set for debugging.
        for line in observed_set:
            logging.debug('%s: %s', self.CROSH_DIR, line)

        # Fail if we find new binaries.
        new = observed_set.difference(baseline_set)
        if len(new):
            message = 'New modules: %s' % (', '.join(new),)
            raise error.TestFail(message)
        else:
            logging.debug('OK: whitelist matches system')
