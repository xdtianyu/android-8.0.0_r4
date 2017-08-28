# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import chrome

DEFAULT_TIMEOUT = 30
DIAGNOSTIC_RUN_TIMEOUT = 180

def get_cfm_webview_context(browser, ext_id):
    """Get context for CFM webview.

    @param broswer: Telemetry broswer object.
    @param ext_id: Extension id of the hangouts app.
    @return webview context.
    """
    ext_contexts = wait_for_kiosk_ext(browser, ext_id)

    for context in ext_contexts:
        context.WaitForDocumentReadyStateToBeInteractiveOrBetter()
        tagName = context.EvaluateJavaScript(
            "document.querySelector('webview') ? 'WEBVIEW' : 'NOWEBVIEW'")

        if tagName == "WEBVIEW":
            def _webview_context():
                try:
                    wb_contexts = context.GetWebviewContexts()
                    if len(wb_contexts) == 1:
                        return wb_contexts[0]
                except (KeyError, chrome.Error):
                    pass
                return None
            return utils.poll_for_condition(
                    _webview_context,
                    exception=error.TestFail('Hangouts webview not available.'),
                    timeout=DEFAULT_TIMEOUT,
                    sleep_interval=1)


def wait_for_kiosk_ext(browser, ext_id):
    """Wait for kiosk extension launch.

    @param browser: Telemetry browser object.
    @param ext_id: Extension id of the hangouts app.
    @return extension contexts.
    """
    def _kiosk_ext_contexts():
        try:
            ext_contexts = browser.extensions.GetByExtensionId(ext_id)
            if len(ext_contexts) > 1:
                return ext_contexts
        except (KeyError, chrome.Error):
            pass
        return []
    return utils.poll_for_condition(
            _kiosk_ext_contexts,
            exception=error.TestFail('Kiosk app failed to launch'),
            timeout=DEFAULT_TIMEOUT,
            sleep_interval=1)


def wait_for_telemetry_commands(webview_context):
    """Wait for hotrod app to load and telemetry commands to be available.

    @param webview_context: Context for hangouts webview.
    """
    webview_context.WaitForJavaScriptCondition(
            "typeof window.hrOobIsStartPageForTest == 'function'",
            timeout=DEFAULT_TIMEOUT)
    logging.info('Hotrod telemetry commands available for testing.')


# UI commands/functions
def wait_for_oobe_start_page(webview_context):
    """Wait for oobe start screen to launch.

    @param webview_context: Context for hangouts webview.
    """
    webview_context.WaitForJavaScriptCondition(
            "window.hrOobIsStartPageForTest() === true;",
            timeout=DEFAULT_TIMEOUT)
    logging.info('Reached oobe start page')


def skip_oobe_screen(webview_context):
    """Skip Chromebox for Meetings oobe screen.

    @param webview_context: Context for hangouts webview.
    """
    webview_context.ExecuteJavaScript("window.hrOobSkipForTest()")
    utils.poll_for_condition(lambda: not webview_context.EvaluateJavaScript(
            "window.hrOobIsStartPageForTest()"),
            exception=error.TestFail('Not able to skip oobe screen.'),
            timeout=DEFAULT_TIMEOUT,
            sleep_interval=1)
    logging.info('Skipped oobe screen.')


def is_oobe_start_page(webview_context):
    """Check if device is on CFM oobe start screen.

    @param webview_context: Context for hangouts webview.
    """
    if webview_context.EvaluateJavaScript("window.hrOobIsStartPageForTest()"):
        logging.info('Is on oobe start page.')
        return True
    logging.info('Is not on oobe start page.')
    return False


# Hangouts commands/functions
def start_new_hangout_session(webview_context, hangout_name):
    """Start a new hangout session.

    @param webview_context: Context for hangouts webview.
    @param hangout_name: Name of the hangout session.
    """
    if not is_ready_to_start_hangout_session(webview_context):
        if is_in_hangout_session(webview_context):
            end_hangout_session(webview_context)
        utils.poll_for_condition(lambda: webview_context.EvaluateJavaScript(
                "window.hrIsReadyToStartHangoutForTest()"),
                exception=error.TestFail('Not ready to start hangout session.'),
                timeout=DEFAULT_TIMEOUT,
                sleep_interval=1)

    webview_context.ExecuteJavaScript("window.hrStartCallForTest('" +
                                  hangout_name + "')")
    utils.poll_for_condition(lambda: webview_context.EvaluateJavaScript(
            "window.hrIsInHangoutForTest()"),
            exception=error.TestFail('Not able to start session.'),
            timeout=DEFAULT_TIMEOUT,
            sleep_interval=1)
    logging.info('Started hangout session: %s', hangout_name)


def end_hangout_session(webview_context):
    """End current hangout session.

    @param webview_context: Context for hangouts webview.
    """
    webview_context.ExecuteJavaScript("window.hrHangupCallForTest()")
    utils.poll_for_condition(lambda: not webview_context.EvaluateJavaScript(
            "window.hrIsInHangoutForTest()"),
            exception=error.TestFail('Not able to end session.'),
            timeout=DEFAULT_TIMEOUT,
            sleep_interval=1)

    logging.info('Ended hangout session.')


def is_in_hangout_session(webview_context):
    """Check if device is in hangout session.

    @param webview_context: Context for hangouts webview.
    """
    if webview_context.EvaluateJavaScript("window.hrIsInHangoutForTest()"):
        logging.info('Is in hangout session.')
        return True
    logging.info('Is not in hangout session.')
    return False


def is_ready_to_start_hangout_session(webview_context):
    """Check if device is ready to start a new hangout session.

    @param webview_context: Context for hangouts webview.
    """
    if (webview_context.EvaluateJavaScript(
            "window.hrIsReadyToStartHangoutForTest()")):
        logging.info('Is ready to start hangout session.')
        return True
    logging.info('Is not ready to start hangout session.')
    return False


# Diagnostics commands/functions
def is_diagnostic_run_in_progress(webview_context):
    """Check if hotrod diagnostics is running.

    @param webview_context: Context for hangouts webview.
    """
    if (webview_context.EvaluateJavaScript(
            "window.hrIsDiagnosticRunInProgressForTest()")):
        logging.info('Diagnostic run is in progress.')
        return True
    logging.info('Diagnostic run is not in progress.')
    return False


def wait_for_diagnostic_run_to_complete(webview_context):
    """Wait for hotrod diagnostics to complete.

    @param webview_context: Context for hangouts webview.
    """
    utils.poll_for_condition(lambda: not webview_context.EvaluateJavaScript(
            "window.hrIsDiagnosticRunInProgressForTest()"),
            exception=error.TestError('Diagnostic run still in progress after '
                                      '3 minutes.'),
            timeout=DIAGNOSTIC_RUN_TIMEOUT,
            sleep_interval=1)


def run_diagnostics(webview_context):
    """Run hotrod diagnostics.

    @param webview_context: Context for hangouts webview.
    """
    if is_diagnostic_run_in_progress(webview_context):
        wait_for_diagnostic_run_to_complete(webview_context)
    webview_context.ExecuteJavaScript("window.hrRunDiagnosticsForTest()")
    logging.info('Started diagnostics run.')


def get_last_diagnostics_results(webview_context):
    """Get latest hotrod diagnostics results.

    @param webview_context: Context for hangouts webview.
    """
    if is_diagnostic_run_in_progress(webview_context):
        wait_for_diagnostic_run_to_complete(webview_context)
    return webview_context.EvaluateJavaScript(
            "window.hrGetLastDiagnosticsResultForTest()")


# Mic audio commands/functions
def is_mic_muted(webview_context):
    """Check if mic is muted.

    @param webview_context: Context for hangouts webview.
    """
    if webview_context.EvaluateJavaScript("window.hrGetAudioInMutedForTest()"):
        logging.info('Mic is muted.')
        return True
    logging.info('Mic is not muted.')
    return False

def mute_mic(webview_context):
    """Local mic mute from toolbar.

    @param webview_context: Context for hangouts webview.
    """
    webview_context.ExecuteJavaScript("window.hrSetAudioInMutedForTest(true)")
    logging.info('Locally muted mic.')


def unmute_mic(webview_context):
    """Local mic unmute from toolbar.

    @param webview_context: Context for hangouts webview.
    """
    webview_context.ExecuteJavaScript("window.hrSetAudioInMutedForTest(false)")
    logging.info('Locally unmuted mic.')


def remote_mute_mic(webview_context):
    """Remote mic mute request from cPanel.

    @param webview_context: Context for hangouts webview.
    """
    webview_context.ExecuteJavaScript("window.hrMuteAudioForTest()")
    logging.info('Remotely muted mic.')


def remote_unmute_mic(webview_context):
    """Remote mic unmute request from cPanel.

    @param webview_context: Context for hangouts webview.
    """
    webview_context.ExecuteJavaScript("window.hrUnmuteAudioForTest()")
    logging.info('Remotely unmuted mic.')


def get_mic_devices(webview_context):
    """Get all mic devices detected by hotrod.

    @param webview_context: Context for hangouts webview.
    """
    return webview_context.EvaluateJavaScript(
            "window.hrGetAudioInDevicesForTest()")


def get_preferred_mic(webview_context):
    """Get mic preferred for hotrod.

    @param webview_context: Context for hangouts webview.
    """
    return webview_context.EvaluateJavaScript(
            "window.hrGetAudioInPrefForTest()")


def set_preferred_mic(webview_context, mic):
    """Set preferred mic for hotrod.

    @param webview_context: Context for hangouts webview.
    @param mic: String with mic name.
    """
    webview_context.ExecuteJavaScript(
            "window.hrSetAudioInPrefForTest('" + mic + "')")
    logging.info('Setting preferred mic to %s.', mic)


# Speaker commands/functions
def get_speaker_devices(webview_context):
    """Get all speaker devices detected by hotrod.

    @param webview_context: Context for hangouts webview.
    """
    return webview_context.EvaluateJavaScript(
            "window.hrGetAudioOutDevicesForTest()")


def get_preferred_speaker(webview_context):
    """Get speaker preferred for hotrod.

    @param webview_context: Context for hangouts webview.
    """
    return webview_context.EvaluateJavaScript(
            "window.hrGetAudioOutPrefForTest()")


def set_preferred_speaker(webview_context, speaker):
    """Set preferred speaker for hotrod.

    @param webview_context: Context for hangouts webview.
    @param mic: String with speaker name.
    """
    webview_context.ExecuteJavaScript(
            "window.hrSetAudioOutPrefForTest('" + speaker + "')")
    logging.info('Set preferred speaker to %s.', speaker)


def set_speaker_volume(webview_context, volume_level):
    """Set speaker volume.

    @param webview_context: Context for hangouts webview.
    @param volume_level: String value ranging from 0-100 to set volume to.
    """
    webview_context.ExecuteJavaScript(
            "window.hrSetAudioOutVolumeLevelForTest('" + volume_level + "')")
    logging.info('Set speaker volume to %s', volume_level)


def get_speaker_volume(webview_context):
    """Get current speaker volume.

    @param webview_context: Context for hangouts webview.
    """
    return webview_context.EvaluateJavaScript(
            "window.hrGetAudioOutVolumeLevelForTest()")


def play_test_sound(webview_context):
    """Play test sound.

    @param webview_context: Context for hangouts webview.
    """
    webview_context.ExecuteJavaScript("window.hrPlayTestSoundForTest()")
    logging.info('Playing test sound.')


# Camera commands/functions
def get_camera_devices(webview_context):
    """Get all camera devices detected by hotrod.

    @param webview_context: Context for hangouts webview.
    """
    return webview_context.EvaluateJavaScript(
            "window.hrGetVideoCaptureDevicesForTest()")


def get_preferred_camera(webview_context):
    """Get camera preferred for hotrod.

    @param webview_context: Context for hangouts webview.
    """
    return webview_context.EvaluateJavaScript(
            "window.hrGetVideoCapturePrefForTest()")


def set_preferred_camera(webview_context, camera):
    """Set preferred camera for hotrod.

    @param webview_context: Context for hangouts webview.
    @param mic: String with camera name.
    """
    webview_context.ExecuteJavaScript(
            "window.hrSetVideoCapturePrefForTest('" + camera + "')")
    logging.info('Set preferred camera to %s.', camera)


def is_camera_muted(webview_context):
    """Check if camera is muted (turned off).

    @param webview_context: Context for hangouts webview.
    """
    if webview_context.EvaluateJavaScript("window.hrGetVideoCaptureMutedForTest()"):
        logging.info('Camera is muted.')
        return True
    logging.info('Camera is not muted.')
    return False


def mute_camera(webview_context):
    """Turned camera off.

    @param webview_context: Context for hangouts webview.
    """
    webview_context.ExecuteJavaScript(
            "window.hrSetVideoCaptureMutedForTest(true)")
    logging.info('Camera muted.')


def unmute_camera(webview_context):
    """Turned camera on.

    @param webview_context: Context for hangouts webview.
    """
    webview_context.ExecuteJavaScript(
            "window.hrSetVideoCaptureMutedForTest(false)")
    logging.info('Camera unmuted.')
