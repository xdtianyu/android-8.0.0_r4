# Copyright (c) 2014 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os
import logging
import time

from autotest_lib.client.common_lib.cros import chrome
from autotest_lib.client.cros.a11y import a11y_test_base


class accessibility_Sanity(a11y_test_base.a11y_test_base):
    """Enables then disables all a11y features via accessibilityFeatures API."""
    version = 1

    # Features that do not have their own separate tests
    _FEATURE_LIST = [
        'largeCursor',
        'stickyKeys',
        'highContrast',
        'screenMagnifier',
        'autoclick',
        'virtualKeyboard'
    ]


    def _check_chromevox(self):
        """Run ChromeVox specific checks.

        Check the reported state of ChromeVox before/after enable and
        for the presence of the indicator before/after disable.

        """
        # ChromeVox is initially off.
        self._confirm_chromevox_state(False)

        # Turn ChromeVox on and check that all the pieces work.
        self._toggle_chromevox()
        self._confirm_chromevox_state(True)
        self._tab_move('forwards') # Ensure that indicator is shown.
        self._confirm_chromevox_indicator(True)

        # Turn ChromeVox off.
        self._toggle_chromevox()
        self._tab.Navigate(self._url) # reload page to remove old indicators
        self._confirm_chromevox_indicator(False)


    def run_once(self):
        """Entry point of this test."""
        extension_path = self._get_extension_path()

        with chrome.Chrome(extension_paths=[extension_path],
                           init_network_controller=True) as cr:
            self._extension = cr.get_extension(extension_path)

            # Open test page.
            self._tab = cr.browser.tabs[0]
            cr.browser.platform.SetHTTPServerDirectories(
                    os.path.join(os.path.dirname(__file__)))
            page_path = os.path.join(self.bindir, 'page.html')
            self._url = cr.browser.platform.http_server.UrlOf(page_path)
            self._tab.Navigate(self._url)

            # Check specific features.
            self._check_chromevox()

            # Enable then disable all other accessibility features.
            for value in [True, False]:
                for feature in self._FEATURE_LIST:
                    logging.info('Setting %s to %s.', feature, value)
                    self._set_feature(feature, value)
                    time.sleep(1)
