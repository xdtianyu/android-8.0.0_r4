# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import unittest

import common
from autotest_lib.server.cros import provision

_CROS_VERSION_SAMPLES = ['cave-release/R57-9030.0.0']
_ANDROID_VERSION_SAMPLES = ['git_mnc-release/shamu-userdebug/2457013']
_TESTBED_VERSION_SAMPLES = [
    ','.join(_ANDROID_VERSION_SAMPLES * 2),
    _ANDROID_VERSION_SAMPLES[0] + '#4'
]

class ImageParsingTests(unittest.TestCase):
    """Unit tests for `provision.get_version_label_prefix()`."""

    def _do_test_prefixes(self, expected, version_samples):
        for v in version_samples:
            prefix = provision.get_version_label_prefix(v)
            self.assertEqual(prefix, expected)

    def test_cros_prefix(self):
        """Test handling of Chrome OS version strings."""
        self._do_test_prefixes(provision.CROS_VERSION_PREFIX,
                               _CROS_VERSION_SAMPLES)

    def test_android_prefix(self):
        """Test handling of Android version strings."""
        self._do_test_prefixes(provision.ANDROID_BUILD_VERSION_PREFIX,
                               _ANDROID_VERSION_SAMPLES)

    def test_testbed_prefix(self):
        """Test handling of Testbed version strings."""
        self._do_test_prefixes(provision.TESTBED_BUILD_VERSION_PREFIX,
                               _TESTBED_VERSION_SAMPLES)


if __name__ == '__main__':
    unittest.main()
