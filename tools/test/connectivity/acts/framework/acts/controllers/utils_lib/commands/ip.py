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

import ipaddress
import re


class LinuxIpCommand(object):
    """Interface for doing standard IP commands on a linux system.

    Wraps standard shell commands used for ip into a python object that can
    be interacted with more easily.
    """

    def __init__(self, runner):
        """
        Args:
            runner: Object that can take unix commands and run them in an
                    enviroment (eg. connection.SshConnection).
        """
        self._runner = runner

    def get_ipv4_addresses(self, net_interface):
        """Gets all ipv4 addresses of a network interface.

        Args:
            net_interface: string, The network interface to get info on
                           (eg. wlan0).

        Returns: An iterator of tuples that contain (address, broadcast).
                 where address is a ipaddress.IPv4Interface and broadcast
                 is an ipaddress.IPv4Address.
        """
        results = self._runner.run('ip addr show dev %s' % net_interface)
        lines = results.stdout.splitlines()

        # Example stdout:
        # 2: eth0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc mq state UP group default qlen 1000
        #   link/ether 48:0f:cf:3c:9d:89 brd ff:ff:ff:ff:ff:ff
        #   inet 192.168.1.1/24 brd 192.168.1.255 scope global eth0
        #       valid_lft forever preferred_lft forever
        #   inet6 2620:0:1000:1500:a968:a776:2d80:a8b3/64 scope global temporary dynamic
        #       valid_lft 599919sec preferred_lft 80919sec

        for line in lines:
            line = line.strip()
            match = re.search('inet (?P<address>[^\s]*) brd (?P<bcast>[^\s]*)',
                              line)
            if match:
                d = match.groupdict()
                address = ipaddress.IPv4Interface(d['address'])
                bcast = ipaddress.IPv4Address(d['bcast'])
                yield (address, bcast)

            match = re.search('inet (?P<address>[^\s]*)', line)
            if match:
                d = match.groupdict()
                address = ipaddress.IPv4Interface(d['address'])
                yield (address, None)

    def add_ipv4_address(self, net_interface, address, broadcast=None):
        """Adds an ipv4 address to a net_interface.

        Args:
            net_interface: string, The network interface
                           to get the new ipv4 (eg. wlan0).
            address: ipaddress.IPv4Interface, The new ipaddress and netmask
                     to add to an interface.
            broadcast: ipaddress.IPv4Address, The broadcast address to use for
                       this net_interfaces subnet.
        """
        if broadcast:
            self._runner.run('ip addr add %s broadcast %s dev %s' %
                             (address, broadcast, net_interface))
        else:
            self._runner.run('ip addr add %s dev %s' %
                             (address, net_interface))

    def remove_ipv4_address(self, net_interface, address):
        """Remove an ipv4 address.

        Removes an ipv4 address from a network interface.

        Args:
            net_interface: string, The network interface to remove the
                           ipv4 address from (eg. wlan0).
            address: ipaddress.IPv4Interface or ipaddress.IPv4Address,
                     The ip address to remove from the net_interface.
        """
        self._runner.run('ip addr del %s dev %s' % (address, net_interface))

    def set_ipv4_address(self, net_interface, address, broadcast=None):
        """Set the ipv4 address.

        Sets the ipv4 address of a network interface. If the network interface
        has any other ipv4 addresses these will be cleared.

        Args:
            net_interface: string, The network interface to set the ip address
                           on (eg. wlan0).
            address: ipaddress.IPv4Interface, The ip address and subnet to give
                     the net_interface.
            broadcast: ipaddress.IPv4Address, The broadcast address to use for
                       the subnet.
        """
        self.clear_ipv4_addresses(net_interface)
        self.add_ipv4_address(net_interface, address, broadcast)

    def clear_ipv4_addresses(self, net_interface):
        """Clears all ipv4 addresses registered to a net_interface.

        Args:
            net_interface: string, The network interface to clear addresses from
                           (eg. wlan0).
        """
        ip_info = self.get_ipv4_addresses(net_interface)

        for address, _ in ip_info:
            self.remove_ipv4_address(net_interface, address)
