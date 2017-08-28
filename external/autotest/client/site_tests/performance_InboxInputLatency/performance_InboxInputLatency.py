# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import numpy
import os
import tempfile
import time

from autotest_lib.client.bin import test
from autotest_lib.client.bin.input import input_device
from autotest_lib.client.bin.input.input_event_player import InputEventPlayer
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import file_utils
from autotest_lib.client.common_lib.cros import chrome
from telemetry.timeline import model as model_module
from telemetry.timeline import tracing_config


_COMPOSE_BUTTON_CLASS = 'y hC'
_DISCARD_BUTTON_CLASS = 'ezvJwb bG AK ew IB'
_IDLE_FOR_INBOX_STABLIZED = 30
_INBOX_URL = 'https://inbox.google.com'
_KEYIN_TEST_DATA = 'input_test_data'
# In order to have focus switched from url bar to body text area of the compose
# frame, 18 times of tab switch are performed accodingly which includes one
# hidden iframe then the whole inbox frame, 8 items in title bar of inbox frame,
# and 'Compose' button, 3 items of top-level compose frame:close, maximize and
# minimize, then 'To' text area, To's drop-down,'Subject' text area, and the
# final 'Body' text area.
_NUMBER_OF_TABS_TO_COMPOSER_FRAME = 18
_PRESS_TAB_KEY = 'key_event_tab'
_SCRIPT_TIMEOUT = 5
_TARGET_EVENT = 'InputLatency::Char'
_TARGET_TRACING_CATEGORIES = 'input, latencyInfo'
_TRACING_TIMEOUT = 60


class performance_InboxInputLatency(test.test):
    """Invoke Inbox composer, inject key events then measure latency."""
    version = 1

    def initialize(self):
        # Create a virtual keyboard device for key event playback.
        device_node = input_device.get_device_node(input_device.KEYBOARD_TYPES)
        if not device_node:
            raise error.TestFail('Could not find keyboard device node')
        self.keyboard = input_device.InputDevice(device_node)

        # Instantiate Chrome browser.
        with tempfile.NamedTemporaryFile() as cap:
            file_utils.download_file(chrome.CAP_URL, cap.name)
            password = cap.read().rstrip()

        self.browser = chrome.Chrome(gaia_login=True,
                                     username=chrome.CAP_USERNAME,
                                     password=password)
        self.tab = self.browser.browser.tabs[0]

        # Setup Chrome Tracing.
        config = tracing_config.TracingConfig()
        category_filter = config.chrome_trace_config.category_filter
        category_filter.AddFilterString(_TARGET_TRACING_CATEGORIES)
        config.enable_chrome_trace = True
        self.target_tracing_config = config

    def cleanup(self):
        if self.browser:
            self.browser.close()

    def inject_key_events(self, event_file):
        """
        Replay key events from file.

        @param event_file: the file with key events recorded.

        """
        current_dir = os.path.dirname(__file__)
        event_file_path = os.path.join(current_dir, event_file)
        InputEventPlayer().playback(self.keyboard, event_file_path)

    def click_button_by_class_name(self, class_name):
        """
        Locate a button by its class name and click it.

        @param class_name: the class name of the button.

        """
        button_query = 'document.getElementsByClassName("' + class_name +'")'
        # Make sure the target button is available
        self.tab.WaitForJavaScriptCondition(button_query + '.length == 1',
                                            timeout=_SCRIPT_TIMEOUT)
        # Perform click action
        self.tab.ExecuteJavaScript(button_query + '[0].click();')

    def setup_inbox_composer(self):
        """Navigate to Inbox, and click the compose button."""
        self.tab.Navigate(_INBOX_URL)
        tracing_controller = self.tab.browser.platform.tracing_controller
        if not tracing_controller.IsChromeTracingSupported():
            raise Exception('Chrome tracing not supported')

        # Idle for making inbox tab stablized, i.e. not busy in syncing with
        # backend inbox server.
        time.sleep(_IDLE_FOR_INBOX_STABLIZED)

        # Pressing tabs to jump into composer frame as a workaround.
        self.click_button_by_class_name(_COMPOSE_BUTTON_CLASS)
        for _ in range(_NUMBER_OF_TABS_TO_COMPOSER_FRAME):
            self.inject_key_events(_PRESS_TAB_KEY)

    def teardown_inbox_composer(self):
        """Discards the draft mail."""
        self.click_button_by_class_name(_DISCARD_BUTTON_CLASS)

    def measure_input_latency(self):
        """Injects key events then measure and report the latency."""
        tracing_controller = self.tab.browser.platform.tracing_controller
        tracing_controller.StartTracing(self.target_tracing_config,
                                        timeout=_TRACING_TIMEOUT)
        # Inject pre-recorded test key events
        self.inject_key_events(_KEYIN_TEST_DATA)
        results = tracing_controller.StopTracing()

        # Iterate recorded events and output target latency events
        timeline_model = model_module.TimelineModel(results)
        event_iter = timeline_model.IterAllEvents(
                event_type_predicate=model_module.IsSliceOrAsyncSlice)

        # Extract and report the latency information
        latency_data = []
        previous_start = 0.0
        for event in event_iter:
            if event.name == _TARGET_EVENT and event.start != previous_start:
                logging.info('input char latency = %f ms', event.duration)
                latency_data.append(event.duration)
                previous_start = event.start
        operators = ['mean', 'std', 'max', 'min']
        for operator in operators:
            description = 'input_char_latency_' + operator
            value = getattr(numpy, operator)(latency_data)
            logging.info('%s = %f', description, value)
            self.output_perf_value(description=description,
                                   value=value,
                                   units='ms',
                                   higher_is_better=False)

    def run_once(self):
        self.setup_inbox_composer()
        self.measure_input_latency()
        self.teardown_inbox_composer()
