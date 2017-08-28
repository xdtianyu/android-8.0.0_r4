# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.bin import test
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import chrome
from autotest_lib.client.cros.graphics import graphics_utils
from autotest_lib.client.cros.input_playback import input_playback


class platform_InputBrightness(test.test):
    """Tests if device suspends using shortcut keys."""
    version = 1
    BRIGHTNESS_CMD = "backlight_tool --get_brightness_percent"
    RESET_BRIGHTNESS_CMD = "backlight_tool --set_brightness_percent=70.0"

    def warmup(self):
        """Test setup."""
        # Emulate keyboard.
        # See input_playback. The keyboard is used to play back shortcuts.
        self._player = input_playback.InputPlayback()
        self._player.emulate(input_type='keyboard')
        self._player.find_connected_inputs()

    def test_brightness_down(self):
        """
        Use keyboard shortcut to test brightness Down (F6) key.

        @raises: error.TestFail if system brightness did not decrease.

        """
        sys_brightness = self.get_system_brightness()
        if not sys_brightness:
            utils.system_output(self.RESET_BRIGHTNESS_CMD)
            sys_brightness = self.get_system_brightness()
        self._player.blocking_playback_of_default_file(
            input_type='keyboard', filename='keyboard_f6')
        if not sys_brightness > self.get_system_brightness():
            raise error.TestFail("Brightness did not decrease from: %s" % sys_brightness)

    def test_brightness_up(self):
        """
        Use keyboard shortcut to test brightness Up (F7) key.

        @raises: error.TestFail if system brightness did not increase.

        """
        sys_brightness = self.get_system_brightness()
        if sys_brightness == 100:
            utils.system_output(self.RESET_BRIGHTNESS_CMD)
            sys_brightness = self.get_system_brightness()
        self._player.blocking_playback_of_default_file(
            input_type='keyboard', filename='keyboard_f7')
        if not sys_brightness < self.get_system_brightness():
            raise error.TestFail("Brightness did not increase from: %s" % sys_brightness)


    def get_system_brightness(self):
        """
        Get current system brightness percent (0.0-100.0).

        @returns: current system brightness.
        """
        sys_brightness = utils.system_output(self.BRIGHTNESS_CMD)
        return float(sys_brightness)


    def run_once(self):
        """
        Open browser, and affect brightness using  up and down functions.

        @raises: error.TestFail if brightness keys (F6/F7) did not work.

        """
        # Check for internal_display
        if not graphics_utils.has_internal_display():
            raise error.TestNAError('Test can not proceed on '
                                    'devices without internal display.')
        with chrome.Chrome():
            self.test_brightness_up()
            self.test_brightness_down()


    def cleanup(self):
        """Test cleanup."""
        self._player.close()
        utils.system_output(self.RESET_BRIGHTNESS_CMD)
