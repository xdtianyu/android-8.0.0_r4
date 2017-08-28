# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
#
# arc_util.py is supposed to be called from chrome.py for ARC specific logic.
# It should not import arc.py since it will create a import loop.

import logging
import os
import select
import tempfile
import time

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import file_utils
from autotest_lib.client.common_lib.cros import arc_common
from telemetry.internal.browser import extension_page

_ARC_SUPPORT_HOST_URL = 'chrome-extension://cnbgggchhmkkdmeppjobngjoejnihlei/'
_ARC_SUPPORT_HOST_PAGENAME = '_generated_background_page.html'
_DUMPSTATE_DEFAULT_TIMEOUT = 20
_DUMPSTATE_PATH = '/var/log/arc-dumpstate.log'
_DUMPSTATE_PIPE_PATH = '/var/run/arc/bugreport/pipe'
_USERNAME = 'arcplusplustest@gmail.com'
_USERNAME_DISPLAY = 'arcplusplustest@gmail.com'
_ARCP_URL = 'https://sites.google.com/a/chromium.org/dev/chromium-os' \
                '/testing/arcplusplus-testing/arcp'
_OPT_IN_BEGIN = 'Initializing ARC opt-in flow.'
_OPT_IN_FINISH = 'ARC opt-in flow complete.'

def should_start_arc(arc_mode):
    """
    Determines whether ARC should be started.

    @param arc_mode: mode as defined in arc_common.

    @returns: True or False.

    """
    logging.debug('ARC is enabled in mode ' + str(arc_mode))
    assert arc_mode is None or arc_mode in arc_common.ARC_MODES
    return arc_mode in [arc_common.ARC_MODE_ENABLED,
                        arc_common.ARC_MODE_ENABLED_ASYNC]


def get_extra_chrome_flags():
    """Returns extra Chrome flags for ARC tests to run"""
    return ['--disable-arc-opt-in-verification']


def post_processing_after_browser(chrome):
    """
    Called when a new browser instance has been initialized.

    Note that this hook function is called regardless of arc_mode.

    @param chrome: Chrome object.

    """
    # Remove any stale dumpstate files.
    if os.path.isfile(_DUMPSTATE_PATH):
        os.unlink(_DUMPSTATE_PATH)

    # Wait for Android container ready if ARC is enabled.
    if chrome.arc_mode == arc_common.ARC_MODE_ENABLED:
        try:
            arc_common.wait_for_android_boot()
        except Exception:
            # Save dumpstate so that we can figure out why boot does not
            # complete.
            _save_android_dumpstate()
            raise


def pre_processing_before_close(chrome):
    """
    Called when the browser instance is being closed.

    Note that this hook function is called regardless of arc_mode.

    @param chrome: Chrome object.

    """
    if not should_start_arc(chrome.arc_mode):
        return
    # TODO(b/29341443): Implement stopping of adb logcat when we start adb
    # logcat for all tests

    # Save dumpstate just before logout.
    _save_android_dumpstate()


def _save_android_dumpstate(timeout=_DUMPSTATE_DEFAULT_TIMEOUT):
    """
    Triggers a dumpstate and saves its contents to to /var/log/arc-dumpstate.log
    with logging.

    Exception thrown while doing dumpstate will be ignored.

    @param timeout: The timeout in seconds.
    """

    try:
        logging.info('Saving Android dumpstate.')
        with open(_DUMPSTATE_PATH, 'w') as out:
            # _DUMPSTATE_PIPE_PATH is a named pipe, so it permanently blocks if
            # opened normally if the other end has not been opened. In order to
            # avoid that, open the file with O_NONBLOCK and use a select loop to
            # read from the file with a timeout.
            fd = os.open(_DUMPSTATE_PIPE_PATH, os.O_RDONLY | os.O_NONBLOCK)
            with os.fdopen(fd, 'r') as pipe:
                end_time = time.time() + timeout
                while True:
                    remaining_time = end_time - time.time()
                    if remaining_time <= 0:
                        break
                    rlist, _, _ = select.select([pipe], [], [], remaining_time)
                    if pipe not in rlist:
                        break
                    buf = os.read(pipe.fileno(), 1024)
                    if len(buf) == 0:
                        break
                    out.write(buf)
        logging.info('Android dumpstate successfully saved.')
    except Exception:
        # Dumpstate is nice-to-have stuff. Do not make it as a fatal error.
        logging.exception('Failed to save Android dumpstate.')


def set_browser_options_for_opt_in(b_options):
    """
    Setup Chrome for gaia login and opt_in.

    @param b_options: browser options object used by chrome.Chrome.

    """
    b_options.username = _USERNAME
    with tempfile.NamedTemporaryFile() as pltp:
        file_utils.download_file(_ARCP_URL, pltp.name)
        b_options.password = pltp.read().rstrip()
    b_options.disable_default_apps = False
    b_options.disable_component_extensions_with_background_pages = False
    b_options.gaia_login = True


def enable_arc_setting(browser):
    """
    Enable ARC++ via the settings page checkbox.

    Do nothing if the account is managed.

    @param browser: chrome.Chrome broswer object.

    @returns: True if the opt-in should continue; else False.

    """
    settings_tab = browser.tabs.New()

    try:
        settings_tab.Navigate('chrome://settings-frame')
        settings_tab.WaitForDocumentReadyStateToBeComplete()

        try:
            settings_tab.ExecuteJavaScript(
                    'assert(document.getElementById("android-apps-enabled"))')
        except Exception, e:
            raise error.TestFail('Could not locate section in chrome://settings'
                                 ' to enable arc. Make sure ARC is available.')

        # Skip enabling for managed users, since value is policy enforced.
        # Return early if a managed user has ArcEnabled set to false.
        is_managed = settings_tab.EvaluateJavaScript(
                'document.getElementById("android-apps-enabled").disabled')
        if is_managed:
            logging.info('Determined that ARC is managed by user policy.')
            policy_value = settings_tab.EvaluateJavaScript(
                    'document.getElementById("android-apps-enabled").checked')
            if not policy_value:
                logging.info(
                        'Returning early since ARC is policy-enforced off.')
                return False
        else:
            settings_tab.ExecuteJavaScript(
                    'Preferences.setBooleanPref("arc.enabled", true, true)')
    finally:
        settings_tab.Close()

    return True


def find_opt_in_extension_page(browser):
    """
    Find and verify the opt-in extension extension page.

    @param browser: chrome.Chrome broswer object.

    @returns: the extension page.

    @raises: error.TestFail if extension is not found or is mal-formed.

    """
    opt_in_extension_id = extension_page.UrlToExtensionId(_ARC_SUPPORT_HOST_URL)
    try:
        extension_pages = browser.extensions.GetByExtensionId(
            opt_in_extension_id)
    except Exception, e:
        raise error.TestFail('Could not locate extension for arc opt-in. '
                             'Make sure disable_default_apps is False. '
                             '"%s".' % e)

    extension_main_page = None
    for page in extension_pages:
        url = page.EvaluateJavaScript('location.href;')
        if url.endswith(_ARC_SUPPORT_HOST_PAGENAME):
            extension_main_page = page
            break
    if not extension_main_page:
        raise error.TestError('Found opt-in extension but not correct page!')
    extension_main_page.WaitForDocumentReadyStateToBeComplete()

    js_code_did_start_conditions = ['termsPage != null',
            '(termsPage.isManaged_ || termsPage.state_ == LoadState.LOADED)']
    try:
        for condition in js_code_did_start_conditions:
            extension_main_page.WaitForJavaScriptCondition(condition,
                                                           timeout=60)
    except Exception, e:
        raise error.TestError('Error waiting for "%s": "%s".' % (condition, e))

    return extension_main_page


def opt_in_and_wait_for_completion(extension_main_page):
    """
    Step through the user input of the opt-in extension and wait for completion.

    @param extension_main_page: opt-in extension object.

    @raises error.TestFail if opt-in doesn't complete after timeout.

    """
    js_code_click_agree = """
        doc = appWindow.contentWindow.document;
        agree_button_element = doc.getElementById('button-agree');
        agree_button_element.click();
    """
    extension_main_page.ExecuteJavaScript(js_code_click_agree)

    SIGN_IN_TIMEOUT = 120
    try:
        extension_main_page.WaitForJavaScriptCondition('!appWindow',
                                                       timeout=SIGN_IN_TIMEOUT)
    except Exception, e:
        js_read_error_message = """
            err = appWindow.contentWindow.document.getElementById(
                    "error-message");
            if (err) {
                err.innerText;
            }
        """
        err_msg = extension_main_page.EvaluateJavaScript(js_read_error_message)
        err_msg = err_msg.strip()
        logging.error('Error: %s', err_msg.strip())
        if err_msg:
            raise error.TestFail('Opt-in app error: %s' % err_msg)
        else:
            raise error.TestFail('Opt-in app did not finish running after %s '
                                 'seconds!' % SIGN_IN_TIMEOUT)


def opt_in(browser):
    """
    Step through opt in and wait for it to complete.

    Return early if the arc_setting cannot be set True.

    @param browser: chrome.Chrome broswer object.

    @raises: error.TestFail if opt in fails.

    """
    logging.info(_OPT_IN_BEGIN)
    if not enable_arc_setting(browser):
        return
    extension_main_page = find_opt_in_extension_page(browser)
    opt_in_and_wait_for_completion(extension_main_page)
    logging.info(_OPT_IN_FINISH)
