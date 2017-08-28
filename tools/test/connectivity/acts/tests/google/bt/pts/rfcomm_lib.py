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
Bluetooth adapter libraries
"""

from acts.test_utils.bt.BtEnum import BluetoothScanModeType
from acts.test_utils.bt.bt_test_utils import set_bt_scan_mode

import pprint


class RfcommLib():
    def __init__(self, log, mac_addr, dut):
        self.advertisement_list = []
        self.dut = dut
        self.log = log
        self.mac_addr = mac_addr

    def connect(self, line):
        """Perform an RFCOMM connect"""
        uuid = None
        if len(line) > 0:
            uuid = line
        if uuid:
            self.dut.droid.bluetoothRfcommBeginConnectThread(self.mac_addr,
                                                                 uuid)
        else:
            self.dut.droid.bluetoothRfcommBeginConnectThread(self.mac_addr)

    def open_rfcomm_socket(self):
        """Open rfcomm socket"""
        self.dut.droid.rfcommCreateRfcommSocket(self.mac_addr, 1)

    def open_l2cap_socket(self):
        """Open L2CAP socket"""
        self.dut.droid.rfcommCreateL2capSocket(self.mac_addr, 1)

    def write(self, line):
        """Write String data over an RFCOMM connection"""
        self.dut.droid.bluetoothRfcommWrite(line)

    def write_binary(self, line):
        """Write String data over an RFCOMM connection"""
        self.dut.droid.bluetoothRfcommWriteBinary(line)

    def end_connect(self):
        """End RFCOMM connection"""
        self.dut.droid.bluetoothRfcommEndConnectThread()

    def accept(self, line):
        """Accept RFCOMM connection"""
        uuid = None
        if len(line) > 0:
            uuid = line
        if uuid:
            self.dut.droid.bluetoothRfcommBeginAcceptThread(uuid)
        else:
            self.dut.droid.bluetoothRfcommBeginAcceptThread(
                RfcommUuid.BASE_UUID.value)

    def stop(self):
        """Stop RFCOMM Connection"""
        self.dut.droid.bluetoothRfcommStop()

    def open_l2cap_socket(self):
        """Open L2CAP socket"""
        self.dut.droid.rfcommCreateL2capSocket(self.mac_addr, 1)
