# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
import glob
import logging
import os
import time

from autotest_lib.client.bin import test
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import chrome
from autotest_lib.client.cros.graphics import graphics_utils
from autotest_lib.client.cros.image_comparison import pdiff_image_comparer
from autotest_lib.client.cros.input_playback import input_playback

def get_percent_difference(file1, file2):
    """
    Performs pixel comparison of two files, given by their paths |file1|
    and |file2| using terminal tool 'perceptualdiff' and returns percentage
    difference of the total file size.

    @param file1: path to image
    @param file2: path to secondary image
    @return: percentage difference of total file size.
    @raise ValueError: if image dimensions are not the same
    @raise OSError: if file does not exist or cannot be opened.

    """
    # Using pdiff image comparer to compare the two images. This class
    # invokes the terminal tool perceptualdiff.
    pdi = pdiff_image_comparer.PdiffImageComparer()
    diff_bytes = pdi.compare(file1, file2)[0]
    return round(100. * diff_bytes / os.path.getsize(file1))


class graphics_VTSwitch(test.test):
    """
    Verify that VT switching works.
    """
    version = 2
    GSC = None
    _WAIT = 5
    # TODO(crosbug.com/36417): Need to handle more than one display screen.

    def initialize(self):
        self.GSC = graphics_utils.GraphicsStateChecker()
        self._player = input_playback.InputPlayback()
        self._player.emulate(input_type='keyboard')
        self._player.find_connected_inputs()

    def _open_vt2(self):
        """Use keyboard shortcut to open VT2."""
        self._player.blocking_playback_of_default_file(
            input_type='keyboard', filename='keyboard_ctrl+alt+f2')
        time.sleep(self._WAIT)

    def _open_vt1(self):
        """Use keyboard shortcut to close VT2."""
        self._player.blocking_playback_of_default_file(
            input_type='keyboard', filename='keyboard_ctrl+alt+f1')
        time.sleep(self._WAIT)

    def run_once(self,
                 num_iterations=2,
                 similarity_percent_threshold=95,
                 difference_percent_threshold=5):

        # Check for chromebook type devices
        if not utils.get_board_type() == 'CHROMEBOOK':
            raise error.TestNAError('DUT is not Chromebook. Test Skipped.')

        self._num_errors = 0
        keyvals = {}

        # Make sure we start in VT1.
        self._open_vt1()

        with chrome.Chrome():

            # Take screenshot of browser.
            vt1_screenshot = self._take_current_vt_screenshot(1)

            keyvals['num_iterations'] = num_iterations

            # Go to VT2 and take a screenshot.
            self._open_vt2()
            vt2_screenshot = self._take_current_vt_screenshot(2)

            # Make sure VT1 and VT2 are sufficiently different.
            diff = get_percent_difference(vt1_screenshot, vt2_screenshot)
            keyvals['percent_initial_VT1_VT2_difference'] = diff
            if not diff >= difference_percent_threshold:
                self._num_errors += 1
                logging.error('VT1 and VT2 screenshots only differ by ' + \
                              '%d %%: %s vs %s' %
                              (diff, vt1_screenshot, vt2_screenshot))

            num_identical_vt1_screenshots = 0
            num_identical_vt2_screenshots = 0
            max_vt1_difference_percent = 0
            max_vt2_difference_percent = 0

            # Repeatedly switch between VT1 and VT2.
            for iteration in xrange(num_iterations):
                logging.info('Iteration #%d', iteration)

                # Go to VT1 and take a screenshot.
                self._open_vt1()
                current_vt1_screenshot = self._take_current_vt_screenshot(1)

                # Make sure the current VT1 screenshot is the same as (or similar
                # to) the original login screen screenshot.
                diff = get_percent_difference(vt1_screenshot,
                                              current_vt1_screenshot)
                if not diff < similarity_percent_threshold:
                    max_vt1_difference_percent = \
                        max(diff, max_vt1_difference_percent)
                    self._num_errors += 1
                    logging.error('VT1 screenshots differ by %d %%: %s vs %s',
                                  diff, vt1_screenshot,
                                  current_vt1_screenshot)
                else:
                    num_identical_vt1_screenshots += 1

                # Go to VT2 and take a screenshot.
                self._open_vt2()
                current_vt2_screenshot = self._take_current_vt_screenshot(2)

                # Make sure the current VT2 screenshot is the same as (or
                # similar to) the first VT2 screenshot.
                diff = get_percent_difference(vt2_screenshot,
                                              current_vt2_screenshot)
                if not diff <= similarity_percent_threshold:
                    max_vt2_difference_percent = \
                        max(diff, max_vt2_difference_percent)
                    self._num_errors += 1
                    logging.error(
                        'VT2 screenshots differ by %d %%: %s vs %s',
                        diff, vt2_screenshot, current_vt2_screenshot)
                else:
                    num_identical_vt2_screenshots += 1

        self._open_vt1()

        keyvals['percent_VT1_screenshot_max_difference'] = \
            max_vt1_difference_percent
        keyvals['percent_VT2_screenshot_max_difference'] = \
            max_vt2_difference_percent
        keyvals['num_identical_vt1_screenshots'] = num_identical_vt1_screenshots
        keyvals['num_identical_vt2_screenshots'] = num_identical_vt2_screenshots

        self.write_perf_keyval(keyvals)

        if self._num_errors > 0:
            raise error.TestFail('Failed: saw %d VT switching errors.' %
                                  self._num_errors)

    def _take_current_vt_screenshot(self, current_vt):
        """
        Captures a screenshot of the current VT screen in BMP format.

        @param current_vt: desired vt for screenshot.

        @returns the path of the screenshot file.

        """
        extension = 'bmp'

        # In VT1, X is running so use that screenshot function.
        if current_vt == 1:
            return graphics_utils.take_screenshot(self.resultsdir,
                                                  'graphics_VTSwitch_VT1',
                                                  extension)

        # Otherwise, take a screenshot using screenshot.py.
        prefix = 'graphics_VTSwitch_VT2'
        next_index = len(glob.glob(
            os.path.join(self.resultsdir, '%s-*.%s' % (prefix, extension))))
        filename = '%s-%d.%s' % (prefix, next_index, extension)
        output_path = os.path.join(self.resultsdir, filename)
        return self._take_screenshot(output_path)

    def _take_screenshot(self, output_path):
        """
        Takes screenshot.

        @param output_path: screenshot output path.

        @returns output_path where screenshot was saved.

        """
        screenshot_path = os.path.join(self.autodir, 'bin', 'screenshot.py')
        utils.system('%s %s' % (screenshot_path, output_path),
                     ignore_status=True)
        utils.system('sync', ignore_status=True)
        time.sleep(self._WAIT)
        logging.info('Saving screenshot to %s', output_path)
        return output_path

    def cleanup(self):
        # Return to VT1 when done.  Ideally, the screen should already be in VT1
        # but the test might fail and terminate while in VT2.
        self._open_vt1()
        if self.GSC:
            self.GSC.finalize()
        self._player.close()
