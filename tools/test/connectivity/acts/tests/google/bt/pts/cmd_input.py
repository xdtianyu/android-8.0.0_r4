#/usr/bin/env python3.4
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
Python script for wrappers to various libraries.
"""

from acts.test_utils.bt.BtEnum import BluetoothScanModeType
from acts.test_utils.bt.GattEnum import GattServerResponses
from ble_lib import BleLib
from bta_lib import BtaLib
from config_lib import ConfigLib
from gattc_lib import GattClientLib
from gatts_lib import GattServerLib
from rfcomm_lib import RfcommLib

import cmd
import gatt_test_database
"""Various Global Strings"""
CMD_LOG = "CMD {} result: {}"
FAILURE = "CMD {} threw exception: {}"


class CmdInput(cmd.Cmd):
    """Simple command processor for Bluetooth PTS Testing"""
    gattc_lib = None

    def setup_vars(self, android_devices, mac_addr, log):
        self.pri_dut = android_devices[0]
        if len(android_devices) > 1:
            self.sec_dut = android_devices[1]
            self.ter_dut = android_devices[2]
        self.mac_addr = mac_addr
        self.log = log

        # Initialize libraries
        self.config_lib = ConfigLib(log, self.pri_dut)
        self.bta_lib = BtaLib(log, mac_addr, self.pri_dut)
        self.ble_lib = BleLib(log, mac_addr, self.pri_dut)
        self.gattc_lib = GattClientLib(log, mac_addr, self.pri_dut)
        self.gatts_lib = GattServerLib(log, mac_addr, self.pri_dut)
        self.rfcomm_lib = RfcommLib(log, mac_addr, self.pri_dut)

    def emptyline(self):
        pass

    def do_EOF(self, line):
        "End Script"
        return True

    """Begin GATT Client wrappers"""

    def do_gattc_connect_over_le(self, line):
        """Perform GATT connection over LE"""
        cmd = "Gatt connect over LE"
        try:
            autoconnect = False
            if line:
                autoconnect = bool(line)
            self.gattc_lib.connect_over_le(autoconnect)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_connect_over_bredr(self, line):
        """Perform GATT connection over BREDR"""
        cmd = "Gatt connect over BR/EDR"
        try:
            self.gattc_lib.connect_over_bredr()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_disconnect(self, line):
        """Perform GATT disconnect"""
        cmd = "Gatt Disconnect"
        try:
            self.gattc_lib.disconnect()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_read_char_by_uuid(self, line):
        """GATT client read Characteristic by UUID."""
        cmd = "GATT client read Characteristic by UUID."
        try:
            self.gattc_lib.read_char_by_uuid(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_request_mtu(self, line):
        """Request MTU Change of input value"""
        cmd = "Request MTU Value"
        try:
            self.gattc_lib.request_mtu(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_list_all_uuids(self, line):
        """From the GATT Client, discover services and list all services,
        chars and descriptors
        """
        cmd = "Discovery Services and list all UUIDS"
        try:
            self.gattc_lib.list_all_uuids()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_discover_services(self, line):
        """GATT Client discover services of GATT Server"""
        cmd = "Discovery Services of GATT Server"
        try:
            self.gattc_lib.discover_services()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_refresh(self, line):
        """Perform Gatt Client Refresh"""
        cmd = "GATT Client Refresh"
        try:
            self.gattc_lib.refresh()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_read_char_by_instance_id(self, line):
        """From the GATT Client, discover services and list all services,
        chars and descriptors
        """
        cmd = "GATT Client Read By Instance ID"
        try:
            self.gattc_lib.read_char_by_instance_id(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_write_char_by_instance_id(self, line):
        """GATT Client Write to Characteristic by instance ID"""
        cmd = "GATT Client write to Characteristic by instance ID"
        try:
            self.gattc_lib.write_char_by_instance_id(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_mod_write_char_by_instance_id(self, line):
        """GATT Client Write to Char that doesn't have write permission"""
        cmd = "GATT Client Write to Char that doesn't have write permission"
        try:
            self.gattc_lib.mod_write_char_by_instance_id(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_write_invalid_char_by_instance_id(self, line):
        """GATT Client Write to Char that doesn't exists"""
        try:
            self.gattc_lib.write_invalid_char_by_instance_id(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_mod_read_char_by_instance_id(self, line):
        """GATT Client Read Char that doesn't have write permission"""
        cmd = "GATT Client Read Char that doesn't have write permission"
        try:
            self.gattc_lib.mod_read_char_by_instance_id(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_read_invalid_char_by_instance_id(self, line):
        """GATT Client Read Char that doesn't exists"""
        cmd = "GATT Client Read Char that doesn't exists"
        try:
            self.gattc_lib.read_invalid_char_by_instance_id(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_mod_write_desc_by_instance_id(self, line):
        """GATT Client Write to Desc that doesn't have write permission"""
        cmd = "GATT Client Write to Desc that doesn't have write permission"
        try:
            self.gattc_lib.mod_write_desc_by_instance_id(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_write_invalid_desc_by_instance_id(self, line):
        """GATT Client Write to Desc that doesn't exists"""
        cmd = "GATT Client Write to Desc that doesn't exists"
        try:
            self.gattc_lib.write_invalid_desc_by_instance_id(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_mod_read_desc_by_instance_id(self, line):
        """GATT Client Read Desc that doesn't have write permission"""
        cmd = "GATT Client Read Desc that doesn't have write permission"
        try:
            self.gattc_lib.mod_read_desc_by_instance_id(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_read_invalid_desc_by_instance_id(self, line):
        """GATT Client Read Desc that doesn't exists"""
        cmd = "GATT Client Read Desc that doesn't exists"
        try:
            self.gattc_lib.read_invalid_desc_by_instance_id(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_mod_read_char_by_uuid_and_instance_id(self, line):
        """GATT Client Read Char that doesn't have write permission"""
        cmd = "GATT Client Read Char that doesn't have write permission"
        try:
            self.gattc_lib.mod_read_char_by_uuid_and_instance_id(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_read_invalid_char_by_uuid(self, line):
        """GATT Client Read Char that doesn't exist"""
        cmd = "GATT Client Read Char that doesn't exist"
        try:
            self.gattc_lib.read_invalid_char_by_uuid(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_write_desc_by_instance_id(self, line):
        """GATT Client Write to Descriptor by instance ID"""
        cmd = "GATT Client Write to Descriptor by instance ID"
        try:
            self.gattc_lib.write_desc_by_instance_id(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_enable_notification_desc_by_instance_id(self, line):
        """GATT Client Enable Notification on Descriptor by instance ID"""
        cmd = "GATT Client Enable Notification on Descriptor by instance ID"
        try:
            self.gattc_lib.enable_notification_desc_by_instance_id(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_char_enable_all_notifications(self, line):
        """GATT Client enable all notifications"""
        cmd = "GATT Client enable all notifications"
        try:
            self.gattc_lib.char_enable_all_notifications()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_read_char_by_invalid_instance_id(self, line):
        """GATT Client read char by non-existant instance id"""
        cmd = "GATT Client read char by non-existant instance id"
        try:
            self.gattc_lib.read_char_by_invalid_instance_id(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_begin_reliable_write(self, line):
        """Begin a reliable write on the Bluetooth Gatt Client"""
        cmd = "GATT Client Begin Reliable Write"
        try:
            self.gattc_lib.begin_reliable_write()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_abort_reliable_write(self, line):
        """Abort a reliable write on the Bluetooth Gatt Client"""
        cmd = "GATT Client Abort Reliable Write"
        try:
            self.gattc_lib.abort_reliable_write()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_execute_reliable_write(self, line):
        """Execute a reliable write on the Bluetooth Gatt Client"""
        cmd = "GATT Client Execute Reliable Write"
        try:
            self.gattc_lib.execute_reliable_write()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_read_all_char(self, line):
        """GATT Client read all Characteristic values"""
        cmd = "GATT Client read all Characteristic values"
        try:
            self.gattc_lib.read_all_char()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_write_all_char(self, line):
        """Write to every Characteristic on the GATT server"""
        cmd = "GATT Client Write All Characteristics"
        try:
            self.gattc_lib.write_all_char(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_write_all_desc(self, line):
        """ Write to every Descriptor on the GATT server """
        cmd = "GATT Client Write All Descriptors"
        try:
            self.gattc_lib.write_all_desc(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_write_desc_notification_by_instance_id(self, line):
        """Write 0x00 or 0x02 to notification descriptor [instance_id]"""
        cmd = "Write 0x00 0x02 to notification descriptor"
        try:
            self.gattc_lib.write_desc_notification_by_instance_id(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gattc_discover_service_by_uuid(self, line):
        """Discover service by uuid"""
        cmd = "Discover service by uuid"
        try:
            self.gattc_lib.discover_service_by_uuid(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    """End GATT Client wrappers"""
    """Begin GATT Server wrappers"""

    def do_gatts_close_bluetooth_gatt_servers(self, line):
        """Close Bluetooth Gatt Servers"""
        cmd = "Close Bluetooth Gatt Servers"
        try:
            self.gatts_lib.close_bluetooth_gatt_servers()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def complete_gatts_setup_database(self, text, line, begidx, endidx):
        if not text:
            completions = list(
                gatt_test_database.GATT_SERVER_DB_MAPPING.keys())[:]
        else:
            completions = [
                s for s in gatt_test_database.GATT_SERVER_DB_MAPPING.keys()
                if s.startswith(text)
            ]
        return completions

    def complete_gatts_send_response(self, text, line, begidx, endidx):
        """GATT Server database name completion"""
        if not text:
            completions = list(GattServerResponses.keys())[:]
        else:
            completions = [
                s for s in GattServerResponses.keys() if s.startswith(text)
            ]
        return completions

    def complete_gatts_send_continuous_response(self, text, line, begidx,
                                                endidx):
        """GATT Server database name completion"""
        if not text:
            completions = list(GattServerResponses.keys())[:]
        else:
            completions = [
                s for s in GattServerResponses.keys() if s.startswith(text)
            ]
        return completions

    def complete_gatts_send_continuous_response_data(self, text, line, begidx,
                                                     endidx):
        """GATT Server database name completion"""
        if not text:
            completions = list(GattServerResponses.keys())[:]
        else:
            completions = [
                s for s in GattServerResponses.keys() if s.startswith(text)
            ]
        return completions

    def do_gatts_list_all_uuids(self, line):
        """From the GATT Client, discover services and list all services,
        chars and descriptors
        """
        cmd = "Discovery Services and list all UUIDS"
        try:
            self.gatts_lib.list_all_uuids()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gatts_send_response(self, line):
        """Send a response to the GATT Client"""
        cmd = "GATT server send response"
        try:
            self.gatts_lib.send_response(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gatts_notify_characteristic_changed(self, line):
        """Notify char changed by instance id [instance_id] [true|false]"""
        cmd = "Notify characteristic changed by instance id"
        try:
            info = line.split()
            instance_id = info[0]
            confirm_str = info[1]
            confirm = False
            if confirm_str.lower() == 'true':
                confirm = True
            self.gatts_lib.notify_characteristic_changed(instance_id, confirm)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gatts_setup_database(self, line):
        cmd = "Setup GATT Server database: {}".format(line)
        try:
            self.gatts_lib.setup_gatts_db(
                gatt_test_database.GATT_SERVER_DB_MAPPING.get(line))
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gatts_characteristic_set_value_by_instance_id(self, line):
        """Set Characteristic value by instance id"""
        cmd = "Change value of a characteristic by instance id"
        try:
            info = line.split()
            instance_id = info[0]
            size = int(info[1])
            value = []
            for i in range(size):
                value.append(i % 256)
            self.gatts_lib.gatt_server_characteristic_set_value_by_instance_id(
                instance_id, value)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_notify_characteristic_changed(self, line):
        """Notify characteristic changed [instance_id] [confirm]"""
        cmd = "Notify characteristic changed"
        try:
            info = line.split()
            instance_id = info[0]
            confirm = bool(info[1])
            self.gatts_lib.notify_characteristic_changed(instance_id, confirm)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gatts_open(self, line):
        """Open a GATT Server instance"""
        cmd = "Open an empty GATT Server"
        try:
            self.gatts_lib.open()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gatts_clear_services(self, line):
        """Clear BluetoothGattServices from BluetoothGattServer"""
        cmd = "Clear BluetoothGattServices from BluetoothGattServer"
        try:
            self.gatts_lib.gatt_server_clear_services()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gatts_send_continuous_response(self, line):
        """Send continous response with random data"""
        cmd = "Send continous response with random data"
        try:
            self.gatts_lib.send_continuous_response(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_gatts_send_continuous_response_data(self, line):
        """Send continous response including requested data"""
        cmd = "Send continous response including requested data"
        try:
            self.gatts_lib.send_continuous_response_data(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    """End GATT Server wrappers"""
    """Begin Ble wrappers"""

    def complete_ble_adv_data_include_local_name(self, text, line, begidx,
                                                 endidx):
        options = ['true', 'false']
        if not text:
            completions = list(options)[:]
        else:
            completions = [s for s in options if s.startswith(text)]
        return completions

    def complete_ble_adv_data_include_tx_power_level(self, text, line, begidx,
                                                     endidx):
        options = ['true', 'false']
        if not text:
            completions = list(options)[:]
        else:
            completions = [s for s in options if s.startswith(text)]
        return completions

    def complete_ble_stop_advertisement(self, text, line, begidx, endidx):
        str_adv_list = list(map(str, self.ble_lib.ADVERTISEMENT_LIST))
        if not text:
            completions = str_adv_list[:]
        else:
            completions = [s for s in str_adv_list if s.startswith(text)]
        return completions

    def do_ble_start_generic_connectable_advertisement(self, line):
        """Start a connectable LE advertisement"""
        cmd = "Start a connectable LE advertisement"
        try:
            self.ble_lib.start_generic_connectable_advertisement(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_ble_start_connectable_advertisement_set(self, line):
        """Start a connectable advertisement set"""
        try:
            self.ble_lib.start_connectable_advertisement_set(line)
        except Exception as err:
            self.log.error("Failed to start advertisement: {}".format(err))

    def do_ble_stop_all_advertisement_set(self, line):
        """Stop all advertisement sets"""
        try:
            self.ble_lib.stop_all_advertisement_set(line)
        except Exception as err:
            self.log.error("Failed to stop advertisement: {}".format(err))

    def do_ble_adv_add_service_uuid_list(self, line):
        """Add service UUID to the LE advertisement inputs:
         [uuid1 uuid2 ... uuidN]"""
        cmd = "Add a valid service UUID to the advertisement."
        try:
            self.ble_lib.adv_add_service_uuid_list(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_ble_adv_data_include_local_name(self, line):
        """Include local name in the advertisement. inputs: [true|false]"""
        cmd = "Include local name in the advertisement."
        try:
            self.ble_lib.adv_data_include_local_name(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_ble_adv_data_include_tx_power_level(self, line):
        """Include tx power level in the advertisement. inputs: [true|false]"""
        cmd = "Include local name in the advertisement."
        try:
            self.ble_lib.adv_data_include_tx_power_level(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_ble_adv_data_add_manufacturer_data(self, line):
        """Include manufacturer id and data to the advertisment:
        [id data1 data2 ... dataN]"""
        cmd = "Include manufacturer id and data to the advertisment."
        try:
            self.ble_lib.adv_data_add_manufacturer_data(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_ble_start_generic_nonconnectable_advertisement(self, line):
        """Start a nonconnectable LE advertisement"""
        cmd = "Start a nonconnectable LE advertisement"
        try:
            self.ble_lib.start_generic_nonconnectable_advertisement(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_ble_list_active_advertisement_ids(self, line):
        """List all active BLE advertisements"""
        self.log.info("IDs: {}".format(self.ble_lib.advertisement_list))

    def do_ble_stop_all_advertisements(self, line):
        """Stop all LE advertisements"""
        cmd = "Stop all LE advertisements"
        try:
            self.ble_lib.stop_all_advertisements(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_ble_stop_advertisement(self, line):
        """Stop an LE advertisement"""
        cmd = "Stop a connectable LE advertisement"
        try:
            self.do_ble_stop_advertisement(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    """End Ble wrappers"""
    """Begin Bta wrappers"""

    def complete_bta_start_pairing_helper(self, text, line, begidx, endidx):
        options = ['true', 'false']
        if not text:
            completions = list(options)[:]
        else:
            completions = [s for s in options if s.startswith(text)]
        return completions

    def complete_bta_set_scan_mode(self, text, line, begidx, endidx):
        completions = [e.name for e in BluetoothScanModeType]
        if not text:
            completions = completions[:]
        else:
            completions = [s for s in completions if s.startswith(text)]
        return completions

    def do_bta_set_scan_mode(self, line):
        """Set the Scan mode of the Bluetooth Adapter"""
        cmd = "Set the Scan mode of the Bluetooth Adapter"
        try:
            self.bta_lib.set_scan_mode(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_bta_set_device_name(self, line):
        """Set Bluetooth Adapter Name"""
        cmd = "Set Bluetooth Adapter Name"
        try:
            self.bta_lib.set_device_name(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_bta_enable(self, line):
        """Enable Bluetooth Adapter"""
        cmd = "Enable Bluetooth Adapter"
        try:
            self.bta_lib.enable()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_bta_disable(self, line):
        """Disable Bluetooth Adapter"""
        cmd = "Disable Bluetooth Adapter"
        try:
            self.bta_lib.disable()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_bta_init_bond(self, line):
        """Initiate bond to PTS device"""
        cmd = "Initiate Bond"
        try:
            self.bta_lib.init_bond()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_bta_start_discovery(self, line):
        """Start BR/EDR Discovery"""
        cmd = "Start BR/EDR Discovery"
        try:
            self.bta_lib.start_discovery()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_bta_stop_discovery(self, line):
        """Stop BR/EDR Discovery"""
        cmd = "Stop BR/EDR Discovery"
        try:
            self.bta_lib.stop_discovery()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_bta_get_discovered_devices(self, line):
        """Get Discovered Br/EDR Devices"""
        cmd = "Get Discovered Br/EDR Devices\n"
        try:
            self.bta_lib.get_discovered_devices()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_bta_bond(self, line):
        """Bond to PTS device"""
        cmd = "Bond to the PTS dongle directly"
        try:
            self.bta_lib.bond()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_bta_disconnect(self, line):
        """BTA disconnect"""
        cmd = "BTA disconnect"
        try:
            self.bta_lib.disconnect()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_bta_unbond(self, line):
        """Unbond from PTS device"""
        cmd = "Unbond from the PTS dongle"
        try:
            self.bta_lib.unbond()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_bta_start_pairing_helper(self, line):
        """Start or stop Bluetooth Pairing Helper"""
        cmd = "Start or stop BT Pairing helper"
        try:
            self.bta_lib.start_pairing_helper(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_bta_push_pairing_pin(self, line):
        """Push pairing pin to the Android Device"""
        cmd = "Push the pin to the Android Device"
        try:
            self.bta_lib.push_pairing_pin(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_bta_get_pairing_pin(self, line):
        """Get pairing PIN"""
        cmd = "Get Pin Info"
        try:
            self.bta_lib.get_pairing_pin()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_bta_fetch_uuids_with_sdp(self, line):
        """BTA fetch UUIDS with SDP"""
        cmd = "Fetch UUIDS with SDP"
        try:
            self.bta_lib.fetch_uuids_with_sdp()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    """End Bta wrappers"""
    """Begin Rfcomm wrappers"""

    def do_rfcomm_connect(self, line):
        """Perform an RFCOMM connect"""
        try:
            self.rfcomm_lib.connect(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_rfcomm_open_rfcomm_socket(self, line):
        """Open rfcomm socket"""
        cmd = "Open RFCOMM socket"
        try:
            self.rfcomm_lib.open_rfcomm_socket()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_rfcomm_open_l2cap_socket(self, line):
        """Open L2CAP socket"""
        cmd = "Open L2CAP socket"
        try:
            self.rfcomm_lib.open_l2cap_socket()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_rfcomm_write(self, line):
        cmd = "Write String data over an RFCOMM connection"
        try:
            self.rfcomm_lib.write(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_rfcomm_write_binary(self, line):
        cmd = "Write String data over an RFCOMM connection"
        try:
            self.rfcomm_lib.write_binary(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_rfcomm_end_connect(self, line):
        cmd = "End RFCOMM connection"
        try:
            self.rfcomm_lib.end_connect()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_rfcomm_accept(self, line):
        cmd = "Accept RFCOMM connection"
        try:
            self.rfcomm_lib.accept(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_rfcomm_stop(self, line):
        cmd = "STOP RFCOMM Connection"
        try:
            self.rfcomm_lib.stop()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_rfcomm_open_l2cap_socket(self, line):
        """Open L2CAP socket"""
        cmd = "Open L2CAP socket"
        try:
            self.rfcomm_lib.open_l2cap_socket()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    """End Rfcomm wrappers"""
    """Begin Config wrappers"""

    def do_config_reset(self, line):
        """Reset Bluetooth Config file"""
        cmd = "Reset Bluetooth Config file"
        try:
            self.config_lib.reset()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_config_set_nonbond(self, line):
        """Set NonBondable Mode"""
        cmd = "Set NonBondable Mode"
        try:
            self.config_lib.set_nonbond()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_config_set_disable_mitm(self, line):
        """Set Disable MITM"""
        cmd = "Set Disable MITM"
        try:
            self.config_lib.set_disable_mitm()
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    """End Config wrappers"""
