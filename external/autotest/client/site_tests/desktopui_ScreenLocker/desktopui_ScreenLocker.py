# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
from datetime import datetime

from autotest_lib.client.bin import test, utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import chrome, session_manager
from autotest_lib.client.cros import asan
from autotest_lib.client.cros.input_playback import input_playback

import gobject
from dbus.mainloop.glib import DBusGMainLoop

class desktopui_ScreenLocker(test.test):
    """This is a client side test that exercises the screenlocker."""
    version = 1

    _SCREEN_IS_LOCKED_TIMEOUT = 30
    # TODO(jdufault): Remove this timeout increase for asan bots once we figure
    # out what's taking so long to lock the screen. See crbug.com/452599.
    if asan.running_on_asan():
      _SCREEN_IS_LOCKED_TIMEOUT *= 2


    def initialize(self):
        super(desktopui_ScreenLocker, self).initialize()
        DBusGMainLoop(set_as_default=True)
        self.player = input_playback.InputPlayback()
        self.player.emulate(input_type='keyboard')
        self.player.find_connected_inputs()


    def cleanup(self):
        """Test cleanup."""
        self.player.close()


    @property
    def screen_locked(self):
        """True if the screen is locked."""
        return self._chrome.login_status['isScreenLocked']


    @property
    def screenlocker_visible(self):
        """True if the screenlocker screen is visible."""
        oobe = self._chrome.browser.oobe
        return (oobe and
                oobe.EvaluateJavaScript(
                    "(typeof Oobe == 'function') && "
                    "(typeof Oobe.authenticateForTesting == 'function') && "
                    "($('account-picker') != null)"))

    @property
    def error_bubble_visible(self):
        """True if the error bubble for bad password is visible."""
        return self._chrome.browser.oobe.EvaluateJavaScript(
                "cr.ui.login.DisplayManager.errorMessageWasShownForTesting_;")


    def attempt_unlock(self, password=''):
        """Attempt to unlock a locked screen. The correct password is the empty
           string.

        @param password: password to use to attempt the unlock.

        """
        self._chrome.browser.oobe.ExecuteJavaScript(
                "Oobe.authenticateForTesting('%s', '%s');"
                % (self._chrome.username, password))


    def lock_screen(self, perf_values):
        """Lock the screen.

        @param perf_values: Performance data will be stored inside of this dict.

        @raises: error.TestFail when screen already locked.
        @raises: error.TestFail if lockscreen screen not visible.
        @raises: error.TestFail when screen not locked.

        """
        logging.debug('lock_screen')
        if self.screen_locked:
            raise error.TestFail('Screen already locked')
        signal_listener = session_manager.ScreenIsLockedSignalListener(
                gobject.MainLoop())
        ext = self._chrome.autotest_ext

        start = datetime.now()
        ext.EvaluateJavaScript('chrome.autotestPrivate.lockScreen();')
        signal_listener.wait_for_signals(desc='Screen is locked.',
                                         timeout=self._SCREEN_IS_LOCKED_TIMEOUT)
        perf_values['lock_seconds'] = (datetime.now() - start).total_seconds()

        utils.poll_for_condition(lambda: self.screenlocker_visible,
                exception=error.TestFail('Screenlock screen not visible'))
        if not self.screen_locked:
            raise error.TestFail('Screen not locked')


    def lock_screen_through_keyboard(self):
        """Lock the screen with keyboard(search+L) .

         @raises: error.TestFail when screen already locked.
         @raises: error.TestFail if lockscreen screen not visible.
         @raises: error.TestFail when screen not locked after using keyboard shortcut.

         """
        logging.debug('Locking screen through the keyboard shortcut')
        if self.screen_locked:
            raise error.TestFail('Screen already locked')
        self.player.blocking_playback_of_default_file(
            input_type='keyboard', filename='keyboard_search+L')
        utils.poll_for_condition(lambda: self.screenlocker_visible,
                                 exception=error.TestFail('Screenlock screen not visible'))
        if not self.screen_locked:
            raise error.TestFail('Screen not locked after using keyboard shortcut')


    def attempt_unlock_bad_password(self):
        """Attempt unlock with a bad password.

         @raises: error.TestFail when try to unlock with bad password.
         @raises: error.TestFail if bubble prematurely visible.
         @raises: error.TestFail when Bad password bubble did not show.

         """
        logging.debug('attempt_unlock_bad_password')
        if self.error_bubble_visible:
            raise error.TestFail('Error bubble prematurely visible')
        self.attempt_unlock('bad')
        utils.poll_for_condition(lambda: self.error_bubble_visible,
                exception=error.TestFail('Bad password bubble did not show'))
        if not self.screen_locked:
            raise error.TestFail('Screen unlocked with bad password')


    def unlock_screen(self):
        """Unlock the screen with the right password.

         @raises: error.TestFail if failed to unlock screen.
         @raises: error.TestFail if screen unlocked.

        """
        logging.debug('unlock_screen')
        self.attempt_unlock()
        utils.poll_for_condition(
                lambda: not self._chrome.browser.oobe_exists,
                exception=error.TestFail('Failed to unlock screen'))
        if self.screen_locked:
            raise error.TestFail('Screen should be unlocked')


    def run_once(self):
        """
        This test locks the screen, tries to unlock with a bad password,
        then unlocks with the right password.

        """
        with chrome.Chrome(autotest_ext=True) as self._chrome:
            perf_values = {}
            self.lock_screen(perf_values)
            self.attempt_unlock_bad_password()
            self.unlock_screen()
            self.lock_screen_through_keyboard()
            self.unlock_screen()
            self.output_perf_value(
                    description='time_to_lock_screen',
                    value=perf_values['lock_seconds'],
                    units='s',
                    higher_is_better=False)
