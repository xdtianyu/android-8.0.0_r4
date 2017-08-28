# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file

import time

from telemetry.core import exceptions
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import cfm_util

DEFAULT_TIMEOUT = 30
SHORT_TIMEOUT = 5

def config_riseplayer(browser, ext_id, app_config_id):
    """
    Configure Rise Player app with a specific display id.

    Step through the configuration screen of the Rise Player app
    which is launched within the browser and enter a display id
    within the configuration frame to initiate media display.

    @param browser: browser instance containing the Rise Player kiosk app.
    @param ext_id: extension id of the Rise Player Kiosk App.
    @param app_config_id: display id for the Rise Player app .

    """
    if not app_config_id:
        raise error.TestFail(
                'Error in configuring Rise Player: app_config_id is None')
    config_js = """
                var frameId = 'btn btn-primary display-register-button'
                document.getElementsByClassName(frameId)[0].click();
                $( "input:text" ).val("%s");
                document.getElementsByClassName(frameId)[4].click();
                """ % app_config_id

    kiosk_webview_context = cfm_util.get_cfm_webview_context(
            browser, ext_id)
    # Wait for the configuration frame to load.
    time.sleep(SHORT_TIMEOUT)
    kiosk_webview_context.ExecuteJavaScript(config_js)
    # TODO (krishnargv): Find a way to verify that content is playing
    #                    within the RisePlayer app.
    verify_app_config_id = """
            /rvashow.*.display&id=%s.*/.test(location.href)
            """ % app_config_id
    #Verify that Risepplayer successfully validates the display id.
    try:
        kiosk_webview_context.WaitForJavaScriptCondition(
                verify_app_config_id,
                timeout=DEFAULT_TIMEOUT)
    except exceptions.TimeoutException:
        raise error.TestFail('Error in configuring Rise Player with id: %s'
                             % app_config_id)
