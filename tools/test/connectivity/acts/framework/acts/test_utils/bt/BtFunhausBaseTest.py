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
from acts.keys import Config
from acts.test_utils.bt.BtMetricsBaseTest import BtMetricsBaseTest
from acts.test_utils.bt.bt_test_utils import bluetooth_enabled_check
from acts.utils import bypass_setup_wizard
from acts.utils import create_dir
from acts.utils import exe_cmd
from acts.utils import sync_device_time
import json
import time
import os

BT_CONF_PATH = "/data/misc/bluedroid/bt_config.conf"


class BtFunhausBaseTest(BtMetricsBaseTest):
    """
    Base class for Bluetooth A2DP audio tests, this class is in charge of
    pushing link key to Android device so that it could be paired with remote
    A2DP device, pushing music to Android device, playing audio, monitoring
    audio play, and stop playing audio
    """
    music_file_to_play = ""
    device_fails_to_connect_list = []

    def __init__(self, controllers):
        BtMetricsBaseTest.__init__(self, controllers)

    def on_fail(self, test_name, begin_time):
        self._collect_bluetooth_manager_dumpsys_logs(self.android_devices)
        super(BtFunhausBaseTest, self).on_fail(test_name, begin_time)

    def setup_class(self):
        if not super(BtFunhausBaseTest, self).setup_class():
            return False
        for ad in self.android_devices:
            sync_device_time(ad)
            # Disable Bluetooth HCI Snoop Logs for audio tests
            ad.adb.shell("setprop persist.bluetooth.btsnoopenable false")
            if not bypass_setup_wizard(ad):
                self.log.debug(
                    "Failed to bypass setup wizard, continuing test.")
        if not "bt_config" in self.user_params.keys():
            self.log.error("Missing mandatory user config \"bt_config\"!")
            return False
        bt_config_map_file = self.user_params["bt_config"]
        return self._setup_bt_config(bt_config_map_file)

    def _setup_bt_config(self, bt_config_map_file):
        bt_config_map = {}
        if not os.path.isfile(bt_config_map_file):
            bt_config_map_file = os.path.join(
                self.user_params[Config.key_config_path], bt_config_map_file)
            if not os.path.isfile(bt_config_map_file):
                self.log.error("Unable to load bt_config file {}.".format(
                    bt_config_map_file))
                return False
        try:
            f = open(bt_config_map_file, 'r')
            bt_config_map = json.load(f)
            f.close()
        except FileNotFoundError:
            self.log.error("File not found: {}.".format(bt_config_map_file))
            return False
        # Connected devices return all upper case mac addresses.
        # Make the peripheral_info address attribute upper case
        # in order to ensure the BT mac addresses match.
        for serial in bt_config_map.keys():
            mac_address = bt_config_map[serial]["peripheral_info"][
                "address"].upper()
            bt_config_map[serial]["peripheral_info"]["address"] = mac_address
        for ad in self.android_devices:
            serial = ad.serial

            # Verify serial number in bt_config_map
            self.log.info("Verify serial number of Android device in config.")
            if serial not in bt_config_map.keys():
                self.log.error(
                    "Missing android device serial {} in bt_config.".format(
                        serial))
                return False

            # Push the bt_config.conf file to Android device
            if (not self._push_config_to_android_device(ad, bt_config_map,
                                                        serial)):
                return False

            # Add music to the Android device
            if not self._add_music_to_android_device(ad):
                return False

            # Verify Bluetooth is enabled
            self.log.info("Verifying Bluetooth is enabled on Android Device.")
            if not bluetooth_enabled_check(ad):
                self.log.error("Failed to toggle on Bluetooth on device {}".
                               format(serial))
                return False

            # Verify Bluetooth device is connected
            self.log.info(
                "Waiting up to 10 seconds for device to reconnect...")
            if not self._verify_bluetooth_device_is_connected(
                    ad, bt_config_map, serial):
                self.device_fails_to_connect_list.append(ad)
        if len(self.device_fails_to_connect_list) == len(self.android_devices):
            self.log.error("All devices failed to reconnect.")
            return False
        return True

    def _push_config_to_android_device(self, ad, bt_config_map, serial):
        """
        Push Bluetooth config file to android device so that it will have the
        paired link key to the remote device
        :param ad: Android device
        :param bt_config_map: Map to each device's config
        :param serial: Serial number of device
        :return: True on success, False on failure
        """
        self.log.info("Pushing bt_config.conf file to Android device.")
        config_path = bt_config_map[serial]["config_path"]
        if not os.path.isfile(config_path):
            config_path = os.path.join(
                self.user_params[Config.key_config_path], config_path)
            if not os.path.isfile(config_path):
                self.log.error(
                    "Unable to load bt_config file {}.".format(config_path))
                return False
        ad.adb.push("{} {}".format(config_path, BT_CONF_PATH))
        return True

    def _add_music_to_android_device(self, ad):
        """
        Add music to Android device as specified by the test config
        :param ad: Android device
        :return: True on success, False on failure
        """
        self.log.info("Pushing music to the Android device.")
        music_path_str = "music_path"
        android_music_path = "/sdcard/Music/"
        if music_path_str not in self.user_params:
            self.log.error("Need music for audio testcases...")
            return False
        music_path = self.user_params[music_path_str]
        if not os.path.isdir(music_path):
            music_path = os.path.join(self.user_params[Config.key_config_path],
                                      music_path)
            if not os.path.isdir(music_path):
                self.log.error(
                    "Unable to find music directory {}.".format(music_path))
                return False
        for dirname, dirnames, filenames in os.walk(music_path):
            for filename in filenames:
                self.music_file_to_play = filename
                file = os.path.join(dirname, filename)
                # TODO: Handle file paths with spaces
                ad.adb.push("{} {}".format(file, android_music_path))
        ad.reboot()
        return True

    def _verify_bluetooth_device_is_connected(self, ad, bt_config_map, serial):
        """
        Verify that remote Bluetooth device is connected
        :param ad: Android device
        :param bt_config_map: Config map
        :param serial: Serial number of Android device
        :return: True on success, False on failure
        """
        connected_devices = ad.droid.bluetoothGetConnectedDevices()
        start_time = time.time()
        wait_time = 10
        result = False
        while time.time() < start_time + wait_time:
            connected_devices = ad.droid.bluetoothGetConnectedDevices()
            if len(connected_devices) > 0:
                if bt_config_map[serial]["peripheral_info"]["address"] in {
                        d['address']
                        for d in connected_devices
                }:
                    result = True
                    break
            else:
                try:
                    ad.droid.bluetoothConnectBonded(
                        bt_config_map[serial]["peripheral_info"]["address"])
                except Exception as err:
                    self.log.error(
                        "Failed to connect bonded. Err: {}".format(err))
        if not result:
            self.log.info("Connected Devices: {}".format(connected_devices))
            self.log.info("Bonded Devices: {}".format(
                ad.droid.bluetoothGetBondedDevices()))
            self.log.error(
                "Failed to connect to peripheral name: {}, address: {}".format(
                    bt_config_map[serial]["peripheral_info"]["name"],
                    bt_config_map[serial]["peripheral_info"]["address"]))
            self.device_fails_to_connect_list.append("{}:{}".format(
                serial, bt_config_map[serial]["peripheral_info"]["name"]))

    def _collect_bluetooth_manager_dumpsys_logs(self, ads):
        """
        Collect "adb shell dumpsys bluetooth_manager" logs
        :param ads: list of active Android devices
        :return: None
        """
        for ad in ads:
            serial = ad.serial
            out_name = "{}_{}".format(serial, "bluetooth_dumpsys.txt")
            dumpsys_path = ''.join((ad.log_path, "/BluetoothDumpsys"))
            create_dir(dumpsys_path)
            cmd = ''.join(
                ("adb -s ", serial, " shell dumpsys bluetooth_manager > ",
                 dumpsys_path, "/", out_name))
            exe_cmd(cmd)

    def start_playing_music_on_all_devices(self):
        """
        Start playing music all devices
        :return: None
        """
        for ad in self.android_devices:
            ad.droid.mediaPlayOpen(
                "file:///sdcard/Music/{}".format(self.music_file_to_play))
            ad.droid.mediaPlaySetLooping(True)
            self.log.info(
                "Music is now playing on device {}".format(ad.serial))

    def stop_playing_music_on_all_devices(self):
        """
        Stop playing music on all devices
        :return: None
        """
        for ad in self.android_devices:
            ad.droid.mediaPlayStopAll()

    def monitor_music_play_util_deadline(self, end_time, sleep_interval=1):
        """
        Monitor music play on all devices, if a device's Bluetooth adapter is
        OFF or if a device is not connected to any remote Bluetooth devices,
        we add them to failure lists bluetooth_off_list and
        device_not_connected_list respectively
        :param end_time: The deadline in epoch floating point seconds that we
            must stop playing
        :param sleep_interval: How often to monitor, too small we may drain
            too much resources on Android, too big the deadline might be passed
            by a maximum of this amount
        :return:
            status: False iff all devices are off or disconnected otherwise True
            bluetooth_off_list: List of ADs that have Bluetooth at OFF state
            device_not_connected_list: List of ADs with no remote device
                                        connected
        """
        bluetooth_off_list = []
        device_not_connected_list = []
        while time.time() < end_time:
            for ad in self.android_devices:
                serial = ad.serial
                if (not ad.droid.bluetoothCheckState() and
                        serial not in bluetooth_off_list):
                    self.log.error(
                        "Device {}'s Bluetooth state is off.".format(serial))
                    bluetooth_off_list.append(serial)
                if (ad.droid.bluetoothGetConnectedDevices() == 0 and
                        serial not in device_not_connected_list):
                    self.log.error(
                        "Device {} not connected to any Bluetooth devices.".
                        format(serial))
                    device_not_connected_list.append(serial)
                if len(bluetooth_off_list) == len(self.android_devices):
                    self.log.error(
                        "Bluetooth off on all Android devices. Ending Test")
                    return False, bluetooth_off_list, device_not_connected_list
                if len(device_not_connected_list) == len(self.android_devices):
                    self.log.error(
                        "Every Android device has no device connected.")
                    return False, bluetooth_off_list, device_not_connected_list
            time.sleep(sleep_interval)
        return True, bluetooth_off_list, device_not_connected_list

    def play_music_for_duration(self, duration, sleep_interval=1):
        """
        A convenience method for above methods. It starts run music on all
        devices, monitors the health of music play and stops playing them when
        time passes the duration
        :param duration: Duration in floating point seconds
        :param sleep_interval: How often to check the health of music play
        :return:
            status: False iff all devices are off or disconnected otherwise True
            bluetooth_off_list: List of ADs that have Bluetooth at OFF state
            device_not_connected_list: List of ADs with no remote device
                                        connected
        """
        start_time = time.time()
        end_time = start_time + duration
        self.start_playing_music_on_all_devices()
        status, bluetooth_off_list, device_not_connected_list = \
            self.monitor_music_play_util_deadline(end_time, sleep_interval)
        if status:
            self.stop_playing_music_on_all_devices()
        return status, bluetooth_off_list, device_not_connected_list
