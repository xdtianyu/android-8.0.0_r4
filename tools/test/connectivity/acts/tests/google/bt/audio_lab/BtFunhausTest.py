# /usr/bin/env python3.4
#
# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
"""
Test script to automate the Bluetooth Audio Funhaus.
"""
import time

from acts.test_decorators import test_tracker_info
from acts.test_utils.bt.BtFunhausBaseTest import BtFunhausBaseTest


class BtFunhausTest(BtFunhausBaseTest):
    music_file_to_play = ""
    device_fails_to_connect_list = []

    def __init__(self, controllers):
        BtFunhausBaseTest.__init__(self, controllers)

    @test_tracker_info(uuid='80a4cc4c-7c2a-428d-9eaf-46239a7926df')
    def test_run_bt_audio_12_hours(self):
        """Test audio quality over 12 hours.

        This test is designed to run Bluetooth audio for 12 hours
        and collect relavant logs. If all devices disconnect during
        the test or Bluetooth is off on all devices, then fail the
        test.

        Steps:
        1. For each Android device connected run steps 2-5.
        2. Open and play media file of music pushed to device
        3. Set media to loop indefintely.
        4. After 12 hours collect bluetooth_manager dumpsys information
        5. Stop media player

        Expected Result:
        Audio plays for 12 hours over Bluetooth

        Returns:
          Pass if True
          Fail if False

        TAGS: Classic, A2DP
        Priority: 1
        """
        self.start_playing_music_on_all_devices()

        sleep_interval = 120
        twelve_hours_in_seconds = 43200
        end_time = time.time() + twelve_hours_in_seconds
        status, bluetooth_off_list, device_not_connected_list = \
            self.monitor_music_play_util_deadline(end_time, sleep_interval)
        if not status:
            return status
        self._collect_bluetooth_manager_dumpsys_logs(self.android_devices)
        self.stop_playing_music_on_all_devices()
        self.collect_bluetooth_manager_metrics_logs(self.android_devices)
        if len(device_not_connected_list) > 0 or len(bluetooth_off_list) > 0:
            self.log.info("Devices reported as not connected: {}".format(
                device_not_connected_list))
            self.log.info("Devices reported with Bluetooth state off: {}".
                          format(bluetooth_off_list))
            return False
        return True

    @test_tracker_info(uuid='285be86d-f00f-4924-a206-e0a590b87b67')
    def test_setup_fail_if_devices_not_connected(self):
        """Test for devices connected or not during setup.

        This test is designed to fail if the number of devices having
        connection issues at time of setup is greater than 0. This lets
        the test runner know of the stability of the testbed.

        Steps:
        1. Check lenght of self.device_fails_to_connect_list

        Expected Result:
        No device should be in a disconnected state.

        Returns:
          Pass if True
          Fail if False

        TAGS: None
        Priority: 1
        """
        if len(self.device_fails_to_connect_list) > 0:
            self.log.error("Devices failed to reconnect:\n{}".format(
                self.device_fails_to_connect_list))
            return False
        return True
