# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os
import re

from autotest_lib.client.bin import test, utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.audio import alsa_utils


class audio_AlsaAPI(test.test):
    """Checks that simple ALSA API functions correctly."""
    version = 2
    _SND_DEV_DIR = '/dev/snd/'
    _PLAYBACK_DEVICE_NAME = '^pcmC(\d+)D(\d+)p$'
    # A dict of list of (card, device) to be skipped on some boards.
    # Chell's HDMI device hw:0,4 can not be used without being plugged.
    # Also, its HDMI device hw:0,5 and hw:0,6 are dummy devices.
    _DEVICES_TO_BE_SKIPPED = {'chell': [(0, 4), (0, 5), (0, 6)]}

    def run_once(self, to_test):
        """Run alsa_api_test binary and verify its result.

        Checks the source code of alsa_api_test in audiotest repo for detail.

        @param to_test: support these test items:
                        move: Checks snd_pcm_forward API.
                        fill: Checks snd_pcm_mmap_begin API.
                        drop: Checks snd_pcm_drop API.

        """
        self._devices = []
        self._find_sound_devices()
        method_name = 'test_' + to_test
        method = getattr(self, method_name)
        for card_index, device_index in self._devices:
            device = 'hw:%s,%s' % (card_index, device_index)
            method(device)


    def _skip_device(self, card_device):
        """Skips devices on some boards.

        @param card_device: A tuple of (card index, device index).

        @returns: True if the device should be skipped. False otherwise.

        """
        return card_device in self._DEVICES_TO_BE_SKIPPED.get(
                utils.get_board().lower(), [])


    def _find_sound_devices(self):
        """Finds playback devices in sound device directory.

        @raises: error.TestError if there is no playback device.
        """
        filenames = os.listdir(self._SND_DEV_DIR)
        for filename in filenames:
            search = re.match(self._PLAYBACK_DEVICE_NAME, filename)
            if search:
                card_device = (int(search.group(1)), int(search.group(2)))
                if not self._skip_device(card_device):
                    self._devices.append(card_device)
        if not self._devices:
            raise error.TestError('There is no playback device')


    def _make_alsa_api_test_command(self, option, device):
        """Makes command for alsa_api_test.

        @param option: same as to_test in run_once.
        @param device: device in hw:<card index>:<device index> format.

        @returns: The command in a list of args.

        """
        return ['alsa_api_test', '--device', device, '--%s' % option]


    def test_move(self, device):
        """Runs alsa_api_test command and checks the return code.

        Test snd_pcm_forward can move appl_ptr to hw_ptr.

        @param device: device in hw:<card index>:<device index> format.

        @raises error.TestError if command fails.

        """
        ret = utils.system(
                command=self._make_alsa_api_test_command('move', device),
                ignore_status=True)
        if ret:
            raise error.TestError(
                    'ALSA API failed to move appl_ptr on device %s' % device)


    def test_fill(self, device):
        """Runs alsa_api_test command and checks the return code.

        Test snd_pcm_mmap_begin can provide the access to the buffer, and memset
        can fill it with zeros without using snd_pcm_mmap_commit.

        @param device: device in hw:<card index>:<device index> format.

        @raises error.TestError if command fails.

        """
        ret = utils.system(
                command=self._make_alsa_api_test_command('fill', device),
                ignore_status=True)
        if ret:
            raise error.TestError(
                    'ALSA API failed to fill buffer on device %s' % device)


    def test_drop(self, device):
        """Runs alsa_api_test command and checks the return code.

        Test snd_pcm_drop can stop playback and reset hw_ptr to 0 in hardware.

        @param device: device in hw:<card index>:<device index> format.

        @raises error.TestError if command fails.

        """
        ret = utils.system(
                command=self._make_alsa_api_test_command('drop', device),
                ignore_status=True)
        if ret:
            raise error.TestError(
                    'ALSA API failed to drop playback and reset hw_ptr'
                    'on device %s' % device)
