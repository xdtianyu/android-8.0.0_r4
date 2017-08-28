# Copyright (c) 2014 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


import json
import os
import time
import urllib2

import selenium

from autotest_lib.client.bin import utils
from extension_pages import e2e_test_utils
from extension_pages import options


class TestUtils(object):
    """Contains all the helper functions for Chrome mirroring automation."""

    short_wait_secs = 3
    step_timeout_secs = 60
    device_info = 'http://%s:8008/setup/eureka_info'
    cpu_fields = ['user', 'nice', 'system', 'idle', 'iowait', 'irq',
                  'softirq', 'steal', 'guest', 'guest_nice']
    cpu_idle_fields = ['idle', 'iowait']

    def __init__(self):
        """Constructor"""


    def _connect_extension(self, driver, extension_id):
        """Connects to extension.

        @param driver: The webdriver instance.
        @param extension_id: The id of Media Router extension.
        """
        e2e_test_utils_page = e2e_test_utils.E2ETestUtilsPage(
                driver, extension_id)
        e2e_test_utils_page.go_to_page()
        e2e_test_utils_page.execute_script(
             'chrome.runtime.connect("%s")' % extension_id)


    def start_mirroring_media_router(
            self, driver, extension_id, receiver_ip, url, fullscreen,
            udp_proxy_server=None, network_profile=None):
        """Starts a mirroring session using Media Router extension.


        @param driver: The webdriver instance.
        @param extension_id: The id of Media Router extension.
        @param receiver_ip: The IP of the receiver to launch the activity.
        @param url: The URL to navigate to.
        @param fullscreen: Click the fullscreen button or not.
        @param udp_proxy_server: Address of udp proxy server,
                                  in http address format, http://<ip>:<port>.
        @param network_profile: Network profile to use.
        @return True if the function finishes.
        """

        e2e_test_utils_page = e2e_test_utils.E2ETestUtilsPage(
                driver, extension_id)
        tab_handles = driver.window_handles

        device_name = self._get_device_name(receiver_ip)
        self._connect_extension(driver, extension_id)
        e2e_test_utils_page.execute_script(
                'chrome.extension.getBackgroundPage().e2eTestService.start()')
        time.sleep(self.short_wait_secs * 3)  # Wait for discovery

        self._connect_extension(driver, extension_id)
        if network_profile and udp_proxy_server:
            e2e_test_utils_page.execute_script(
                    'chrome.extension.getBackgroundPage().e2eTestService.'
                    'mirrorTabViaCastStreaming("%s", "%s", "%s", "%s")' % (
                            device_name, url,
                            udp_proxy_server, network_profile))
        else:
            e2e_test_utils_page.execute_script(
                    'chrome.extension.getBackgroundPage().e2eTestService.'
                    'mirrorTabViaCastStreaming("%s", "%s")' % (
                            device_name, url))
        time.sleep(self.short_wait_secs)    # Wait for mirroring to start
        all_handles = driver.window_handles
        video_handle = [x for x in all_handles if x not in tab_handles].pop()
        driver.switch_to_window(video_handle)
        self.navigate_to_test_url(driver, url, fullscreen)
        return True

    def stop_mirroring_media_router(self, driver, extension_id):
        """Stops a mirroring session on a device using Media Router extension.

        @param driver: The webdriver instance.
        @param extension_id: The id of Media Router extension.
        """
        e2e_test_utils_page = e2e_test_utils.E2ETestUtilsPage(
                driver, extension_id)
        e2e_test_utils_page.go_to_page()
        self._connect_extension(driver, extension_id)
        e2e_test_utils_page.execute_script(
                ('chrome.extension.getBackgroundPage()'
                 '.e2eTestService.stopMirroring()'))
        # Wait for receiver to go back to home screen
        time.sleep(self.short_wait_secs)

    def enable_automatic_send_usage(self, driver):
        """Enables automatic send usage statistics and crash reports in Chrome.

        @param driver: The webdriver instance of the browser.
        """
        driver.get('chrome://settings-frame')
        driver.find_element_by_id('advanced-settings-expander').click()
        usage_check_box = driver.find_element_by_id('metrics-reporting-enabled')
        utils.poll_for_condition(
            usage_check_box.is_displayed, timeout=10, sleep_interval=1,
            desc='Wait for automatic send usage checkbox.')
        if not usage_check_box.is_selected():
            usage_check_box.click()

    def set_local_storage_mr_mirroring(
            self, driver, extension_id, frame_rate=60):
        """Enables extension fine log in Chrome for debugging.

        @param driver: The webdriver instance of the browser.
        @param extension_id: The extension ID of Media Router extension.
        @param frame_rate: The capture frame rate of Media Router extension.
        """
        scripts = ('localStorage["debug.console"] = true;'
                                   'localStorage["debug.logs"] = "fine";')
        scripts += ('localStorage["mr.mirror.Settings.Overrides"] ='
                             ' \'{"maxFrameRate":%s}\';') % str(frame_rate)
        self._execute_script_reload_extension(
            driver, extension_id, scripts)


    def upload_mirroring_logs_media_router(self, driver, extension_id):
        """Uploads MR mirroring logs for the latest mirroring session.

        @param driver: The webdriver instance of the browser.
        @param extension_id: The extension ID of Media Router extension.
        @return The report id in crash staging server or
                empty if there is nothing.
        """
        report_id = None
        wait_time = 0
        e2e_test_utils_page = e2e_test_utils.E2ETestUtilsPage(
                driver, extension_id)
        e2e_test_utils_page.go_to_page()
        while not report_id and wait_time < 90:
            report_id = e2e_test_utils_page.execute_script(
                    ('return localStorage["e2eTestService'
                     '.castStreamingMirrorLogId"]'))
            time.sleep(self.short_wait_secs)
            wait_time += self.short_wait_secs
        if report_id:
            return report_id
        else:
            return ''


    def get_chrome_version(self, driver):
        """Returns the Chrome version that is being used for running test.

        @param driver: The chromedriver instance.
        @return The Chrome version.
        """
        get_chrome_version_js = 'return window.navigator.appVersion;'
        app_version = driver.execute_script(get_chrome_version_js)
        for item in app_version.split():
           if 'Chrome/' in item:
              return item.split('/')[1]
        return None


    def get_chrome_revision(self, driver):
        """Returns Chrome revision number that is being used for running test.

        @param driver: The chromedriver instance.
        @return The Chrome revision number.
        """
        get_chrome_revision_js = ('return document.getElementById("version").'
                                  'getElementsByTagName("span")[2].innerHTML;')
        driver.get('chrome://version')
        return driver.execute_script(get_chrome_revision_js)


    def get_extension_id_from_flag(self, extra_flags):
        """Gets the extension ID based on the whitelisted extension id flag.

        @param extra_flags: A string which contains all the extra chrome flags.
        @return The ID of the extension. Return None if nothing is found.
        """
        extra_flags_list = extra_flags.split()
        for flag in extra_flags_list:
            if 'whitelisted-extension-id=' in flag:
                return flag.split('=')[1]
        return None


    def navigate_to_test_url(self, driver, url, fullscreen):
        """Navigates to a given URL. Click fullscreen button if needed.

        @param driver: The chromedriver instance.
        @param url: The URL of the site to navigate to.
        @param fullscreen: True and the video will play in full screen mode.
                           Otherwise, set to False.
        """
        driver.get(url)
        driver.refresh()
        if fullscreen:
          self.request_full_screen(driver)


    def request_full_screen(self, driver):
        """Requests full screen.

        @param driver: The chromedriver instance.
        """
        try:
            driver.find_element_by_id('fsbutton').click()
        except selenium.common.exceptions.NoSuchElementException as err_msg:
            print 'Full screen button is not found. ' + str(err_msg)


    def set_focus_tab(self, driver, tab_handle):
        """Sets the focus on a tab.

        @param driver: The chromedriver instance.
        @param tab_handle: The chrome driver handle of the tab.
        """
        driver.switch_to_window(tab_handle)
        driver.get_screenshot_as_base64()


    def output_dict_to_file(self, dictionary, file_name,
                            path=None, sort_keys=False):
        """Outputs a dictionary into a file.

        @param dictionary: The dictionary to be output as JSON.
        @param file_name: The name of the file that is being output.
        @param path: The path of the file. The default is None.
        @param sort_keys: Sort dictionary by keys when output. False by default.
        """
        if path is None:
            path = os.path.abspath(os.path.dirname(__file__))
        # if json file exists, read the existing one and append to it
        json_file = os.path.join(path, file_name)
        if os.path.isfile(json_file) and dictionary:
            with open(json_file, 'r') as existing_json_data:
                json_data = json.load(existing_json_data)
            dictionary = dict(json_data.items() + dictionary.items())
        output_json = json.dumps(dictionary, sort_keys=sort_keys)
        with open(json_file, 'w') as file_handler:
            file_handler.write(output_json)


    def compute_cpu_utilization(self, cpu_dict):
        """Generates the upper/lower bound and the average CPU consumption.

        @param cpu_dict: The dictionary that contains CPU usage every sec.
        @returns A dict that contains upper/lower bound and average cpu usage.
        """
        cpu_bound = {}
        cpu_usage = sorted(cpu_dict.values())
        cpu_bound['lower_bound'] = (
                '%.2f' % cpu_usage[int(len(cpu_usage) * 10.0 / 100.0)])
        cpu_bound['upper_bound'] = (
                '%.2f' % cpu_usage[int(len(cpu_usage) * 90.0 / 100.0)])
        cpu_bound['average'] = '%.2f' % (sum(cpu_usage) / float(len(cpu_usage)))
        return cpu_bound


    def cpu_usage_interval(self, duration, interval=1):
        """Gets the CPU usage over a period of time based on interval.

        @param duration: The duration of getting the CPU usage.
        @param interval: The interval to check the CPU usage. Default is 1 sec.
        @return A dict that contains CPU usage over different time intervals.
        """
        current_time = 0
        cpu_usage = {}
        while current_time < duration:
            pre_times = self._get_system_times()
            time.sleep(interval)
            post_times = self._get_system_times()
            cpu_usage[current_time] = self._get_avg_cpu_usage(
                    pre_times, post_times)
            current_time += interval
        return cpu_usage


    def _execute_script_reload_extension(self, driver, extension_id, script):
        """Executes javascript in the extension and reload it afterwards.

        @param driver: The chromedriver instance.
        @param extension_id: The id of Media Router extension.
        @param script: JavaScript to be executed.
        """
        script += 'chrome.runtime.reload();'
        current_handle = driver.current_window_handle
        new_tab_handle = self._open_new_tab(driver)
        e2e_test_utils_page = e2e_test_utils.E2ETestUtilsPage(
                driver, extension_id)
        e2e_test_utils_page.execute_script(script)
        driver.switch_to_window(new_tab_handle)


    def _get_avg_cpu_usage(self, pre_times, post_times):
        """Calculates the average CPU usage of two different periods of time.

        @param pre_times: The CPU usage information of the start point.
        @param post_times: The CPU usage information of the end point.
        @return Average CPU usage over a time period.
        """
        diff_times = {}
        for field in self.cpu_fields:
            diff_times[field] = post_times[field] - pre_times[field]

        idle_time = sum(diff_times[field] for field in self.cpu_idle_fields)
        total_time = sum(diff_times[field] for field in self.cpu_fields)
        return float(total_time - idle_time) / total_time * 100.0


    def _get_device_name(self, device_ip):
        """Gets all the Chromecast information through eureka_info page.

        @param device_ip: string, the IP address the device.
        @return Name of the device.
        """
        response = urllib2.urlopen(self.device_info % device_ip).read()
        return json.loads(response).get('name')


    def _get_system_times(self):
        """Gets the CPU information from the system times.

        @return An list with CPU usage of different processes.
        """
        proc_stat = utils.read_file('/proc/stat')
        for line in proc_stat.split('\n'):
            if line.startswith('cpu '):
                times = line[4:].strip().split(' ')
                times = [int(jiffies) for jiffies in times]
                return dict(zip(self.cpu_fields, times))


    def _open_new_tab(self, driver):
        """Opens a new tab in Chrome.

        @param driver: The webdriver instance of the browser.
        @return Handle of the new tab.
        """
        current_handles = driver.window_handles
        driver.execute_script('window.open("chrome://newtab")')
        utils.poll_for_condition(
            lambda: len(driver.window_handles) > len(current_handles),
            timeout=10, sleep_interval=1, desc='Wait for new tab to open.')
        return list(set(driver.window_handles) - set(current_handles))[0]
