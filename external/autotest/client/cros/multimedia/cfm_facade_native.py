# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Facade to access the CFM functionality."""

import time

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import enrollment, cfm_util


class TimeoutException(Exception):
    """Timeout Exception class."""
    pass


class CFMFacadeNative(object):
    """Facade to access the CFM functionality.

    The methods inside this class only accept Python native types.
    """
    _USER_ID = 'cfmtest@croste.tv'
    _PWD = 'test0000'
    _EXT_ID = 'ikfcpmgefdpheiiomgmhlmmkihchmdlj'
    _ENROLLMENT_DELAY = 15


    def __init__(self, resource):
        """Initializes a CFMFacadeNative.

        @param resource: A FacadeResource object.
        """
        self._resource = resource


    def enroll_device(self):
        """Enroll device into CFM."""
        self._resource.start_custom_chrome({"auto_login": False,
                                            "disable_gaia_services": False})
        enrollment.RemoraEnrollment(self._resource._browser, self._USER_ID,
                self._PWD)
        # Timeout to allow for the device to stablize and go back to the
        # login screen before proceeding.
        time.sleep(self._ENROLLMENT_DELAY)
        self.restart_chrome_for_cfm()
        self.check_hangout_extension_context()


    def restart_chrome_for_cfm(self):
        """Restart chrome with custom values for CFM."""
        custom_chrome_setup = {"clear_enterprise_policy": False,
                               "dont_override_profile": True,
                               "disable_gaia_services": False,
                               "disable_default_apps": False,
                               "auto_login": False}
        self._resource.start_custom_chrome(custom_chrome_setup)


    def check_hangout_extension_context(self):
        """Check to make sure hangout app launched.

        @raises error.TestFail if the URL checks fails.
        """
        ext_contexts = cfm_util.wait_for_kiosk_ext(
                self._resource._browser, self._EXT_ID)
        ext_urls = set([context.EvaluateJavaScript('location.href;')
                        for context in ext_contexts])
        expected_urls = set(
                ['chrome-extension://' + self._EXT_ID + '/' + path
                 for path in ['hangoutswindow.html?windowid=0',
                              '_generated_background_page.html']])
        if expected_urls != ext_urls:
            raise error.TestFail(
                    'Unexpected extension context urls, expected %s, got %s'
                    % (expected_urls, ext_urls))

    @property
    def _webview_context(self):
        """Get webview context object."""
        return cfm_util.get_cfm_webview_context(self._resource._browser,
                self._EXT_ID)


    def wait_for_telemetry_commands(self):
        """Wait for telemetry commands."""
        cfm_util.wait_for_telemetry_commands(self._webview_context)


    # UI commands/functions
    def wait_for_oobe_start_page(self):
        """Wait for oobe start screen to launch."""
        cfm_util.wait_for_oobe_start_page(self._webview_context)


    def skip_oobe_screen(self):
        """Skip Chromebox for Meetings oobe screen."""
        cfm_util.skip_oobe_screen(self._webview_context)


    def is_oobe_start_page(self):
        """Check if device is on CFM oobe start screen.

        @return a boolean, based on oobe start page status.
        """
        return cfm_util.is_oobe_start_page(self._webview_context)


    # Hangouts commands/functions
    def start_new_hangout_session(self, session_name):
        """Start a new hangout session.

        @param session_name: Name of the hangout session.
        """
        cfm_util.start_new_hangout_session(self._webview_context, session_name)


    def end_hangout_session(self):
        """End current hangout session."""
        cfm_util.end_hangout_session(self._webview_context)


    def is_in_hangout_session(self):
        """Check if device is in hangout session.

        @return a boolean, for hangout session state.
        """
        return cfm_util.is_in_hangout_session(self._webview_context)


    def is_ready_to_start_hangout_session(self):
        """Check if device is ready to start a new hangout session.

        @return a boolean for hangout session ready state.
        """
        return cfm_util.is_ready_to_start_hangout_session(
                self._webview_context)


    # Diagnostics commands/functions
    def is_diagnostic_run_in_progress(self):
        """Check if hotrod diagnostics is running.

        @return a boolean for diagnostic run state.
        """
        return cfm_util.is_diagnostic_run_in_progress(self._webview_context)


    def wait_for_diagnostic_run_to_complete(self):
        """Wait for hotrod diagnostics to complete."""
        cfm_util.wait_for_diagnostic_run_to_complete(self._webview_context)


    def run_diagnostics(self):
        """Run hotrod diagnostics."""
        cfm_util.run_diagnostics(self._webview_context)


    def get_last_diagnostics_results(self):
        """Get latest hotrod diagnostics results.

        @return a dict with diagnostic test results.
        """
        return cfm_util.get_last_diagnostics_results(self._webview_context)


    # Mic audio commands/functions
    def is_mic_muted(self):
        """Check if mic is muted.

        @return a boolean for mic mute state.
        """
        return cfm_util.is_mic_muted(self._webview_context)


    def mute_mic(self):
        """Local mic mute from toolbar."""
        cfm_util.mute_mic(self._webview_context)


    def unmute_mic(self):
        """Local mic unmute from toolbar."""
        cfm_util.unmute_mic(self._webview_context)


    def remote_mute_mic(self):
        """Remote mic mute request from cPanel."""
        cfm_util.remote_mute_mic(self._webview_context)


    def remote_unmute_mic(self):
        """Remote mic unmute request from cPanel."""
        cfm_util.remote_unmute_mic(self._webview_context)


    def get_mic_devices(self):
        """Get all mic devices detected by hotrod.

        @return a list of mic devices.
        """
        return cfm_util.get_mic_devices(self._webview_context)


    def get_preferred_mic(self):
        """Get mic preferred for hotrod.

        @return a str with preferred mic name.
        """
        return cfm_util.get_preferred_mic(self._webview_context)


    def set_preferred_mic(self, mic):
        """Set preferred mic for hotrod.

        @param mic: String with mic name.
        """
        cfm_util.set_preferred_mic(self._webview_context, mic)


    # Speaker commands/functions
    def get_speaker_devices(self):
        """Get all speaker devices detected by hotrod.

        @return a list of speaker devices.
        """
        return cfm_util.get_speaker_devices(self._webview_context)


    def get_preferred_speaker(self):
        """Get speaker preferred for hotrod.

        @return a str with preferred speaker name.
        """
        return cfm_util.get_preferred_speaker(self._webview_context)


    def set_preferred_speaker(self, speaker):
        """Set preferred speaker for hotrod.

        @param speaker: String with speaker name.
        """
        cfm_util.set_preferred_speaker(self._webview_context, speaker)


    def set_speaker_volume(self, volume_level):
        """Set speaker volume.

        @param volume_level: String value ranging from 0-100 to set volume to.
        """
        cfm_util.set_speaker_volume(self._webview_context, volume_level)


    def get_speaker_volume(self):
        """Get current speaker volume.

        @return a str value with speaker volume level 0-100.
        """
        return cfm_util.get_speaker_volume(self._webview_context)


    def play_test_sound(self):
        """Play test sound."""
        cfm_util.play_test_sound(self._webview_context)


    # Camera commands/functions
    def get_camera_devices(self):
        """Get all camera devices detected by hotrod.

        @return a list of camera devices.
        """
        return cfm_util.get_camera_devices(self._webview_context)


    def get_preferred_camera(self):
        """Get camera preferred for hotrod.

        @return a str with preferred camera name.
        """
        return cfm_util.get_preferred_camera(self._webview_context)


    def set_preferred_camera(self, camera):
        """Set preferred camera for hotrod.

        @param camera: String with camera name.
        """
        cfm_util.set_preferred_camera(self._webview_context, camera)


    def is_camera_muted(self):
        """Check if camera is muted (turned off).

        @return a boolean for camera muted state.
        """
        return cfm_util.is_camera_muted(self._webview_context)


    def mute_camera(self):
        """Turned camera off."""
        cfm_util.mute_camera(self._webview_context)


    def unmute_camera(self):
        """Turned camera on."""
        cfm_util.unmute_camera(self._webview_context)
