#!/usr/bin/env python
#
#   Copyright 2016 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
from acts import base_test
from acts import test_runner
from acts.controllers.relay_lib.relay import SynchronizeRelays

class RelayDeviceSampleTest(base_test.BaseTestClass):
    """ Demonstrates example usage of a configurable access point."""

    def setup_class(self):
        # Take devices from relay_devices.
        self.relay_device = self.relay_devices[0]

        # You can use this workaround to get devices by name:

        relay_rig = self.relay_devices[0].rig
        #self.other_relay_device = relay_rig.devices['UniqueDeviceName']
        # Note: If the "devices" key from the config is missing
        # a GenericRelayDevice that contains every switch in the config
        # will be stored in relay_devices[0]. Its name will be
        # "GenericRelayDevice".

    def setup_test(self):
        # setup() will set the relay device to the default state.
        # Unless overridden, the default state is all switches set to off.
        self.relay_device.setup()

    def teardown_test(self):
        # clean_up() will set the relay device back to a default state.
        # Unless overridden, the default state is all switches set to off.
        self.relay_device.clean_up()


    def test_toggling(self):
        # This test just spams the toggle on each relay.
        ## print(self.relay_device.relays.mac_address)
        print("--->" + self.relay_device.mac_address + "<---")
        for _ in range(0, 2):
            self.relay_device.relays['Play'].toggle()

if __name__ == "__main__":
    test_runner.main()
