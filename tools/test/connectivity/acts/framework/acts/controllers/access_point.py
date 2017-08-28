#!/usr/bin/env python3.4
#
#   Copyright 2016 - Google, Inc.
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

import collections
import ipaddress
import logging

from acts.controllers.ap_lib import dhcp_config
from acts.controllers.ap_lib import dhcp_server
from acts.controllers.ap_lib import hostapd
from acts.controllers.ap_lib import hostapd_config
from acts.controllers.utils_lib.commands import ip
from acts.controllers.utils_lib.commands import route
from acts.controllers.utils_lib.commands import shell
from acts.controllers.utils_lib.ssh import connection
from acts.controllers.utils_lib.ssh import settings

ACTS_CONTROLLER_CONFIG_NAME = 'AccessPoint'
ACTS_CONTROLLER_REFERENCE_NAME = 'access_points'


def create(configs):
    """Creates ap controllers from a json config.

    Creates an ap controller from either a list, or a single
    element. The element can either be just the hostname or a dictionary
    containing the hostname and username of the ap to connect to over ssh.

    Args:
        The json configs that represent this controller.

    Returns:
        A new AccessPoint.
    """
    return [
        AccessPoint(settings.from_config(c['ssh_config'])) for c in configs
    ]


def destroy(aps):
    """Destroys a list of access points.

    Args:
        aps: The list of access points to destroy.
    """
    for ap in aps:
        ap.close()


def get_info(aps):
    """Get information on a list of access points.

    Args:
        aps: A list of AccessPoints.

    Returns:
        A list of all aps hostname.
    """
    return [ap.ssh_settings.hostname for ap in aps]


class Error(Exception):
    """Error raised when there is a problem with the access point."""


_ApInstance = collections.namedtuple('_ApInstance', ['hostapd', 'subnet'])

# We use these today as part of a hardcoded mapping of interface name to
# capabilities.  However, medium term we need to start inspecting
# interfaces to determine their capabilities.
_AP_2GHZ_INTERFACE = 'wlan0'
_AP_5GHZ_INTERFACE = 'wlan1'
# These ranges were split this way since each physical radio can have up
# to 8 SSIDs so for the 2GHz radio the DHCP range will be
# 192.168.1 - 8 and the 5Ghz radio will be 192.168.9 - 16
_AP_2GHZ_SUBNET_STR = '192.168.1.0/24'
_AP_5GHZ_SUBNET_STR = '192.168.9.0/24'
_AP_2GHZ_SUBNET = dhcp_config.Subnet(ipaddress.ip_network(_AP_2GHZ_SUBNET_STR))
_AP_5GHZ_SUBNET = dhcp_config.Subnet(ipaddress.ip_network(_AP_5GHZ_SUBNET_STR))


class AccessPoint(object):
    """An access point controller.

    Attributes:
        ssh: The ssh connection to this ap.
        ssh_settings: The ssh settings being used by the ssh conneciton.
        dhcp_settings: The dhcp server settings being used.
    """

    def __init__(self, ssh_settings):
        """
        Args:
            ssh_settings: acts.controllers.utils_lib.ssh.SshSettings instance.
        """
        self.ssh_settings = ssh_settings
        self.ssh = connection.SshConnection(self.ssh_settings)

        # Singleton utilities for running various commands.
        self._ip_cmd = ip.LinuxIpCommand(self.ssh)
        self._route_cmd = route.LinuxRouteCommand(self.ssh)

        # A map from network interface name to _ApInstance objects representing
        # the hostapd instance running against the interface.
        self._aps = dict()

    def __del__(self):
        self.close()

    def start_ap(self, hostapd_config, additional_parameters=None):
        """Starts as an ap using a set of configurations.

        This will start an ap on this host. To start an ap the controller
        selects a network interface to use based on the configs given. It then
        will start up hostapd on that interface. Next a subnet is created for
        the network interface and dhcp server is refreshed to give out ips
        for that subnet for any device that connects through that interface.

        Args:
            hostapd_config: hostapd_config.HostapdConfig, The configurations
                            to use when starting up the ap.
            additional_parameters: A dicitonary of parameters that can sent
                                   directly into the hostapd config file.  This
                                   can be used for debugging and or adding one
                                   off parameters into the config.

        Returns:
            An identifier for the ap being run. This identifier can be used
            later by this controller to control the ap.

        Raises:
            Error: When the ap can't be brought up.
        """
        # Right now, we hardcode that a frequency maps to a particular
        # network interface.  This is true of the hardware we're running
        # against right now, but in general, we'll want to do some
        # dynamic discovery of interface capabilities.  See b/32582843
        if hostapd_config.frequency < 5000:
            interface = _AP_2GHZ_INTERFACE
            subnet = _AP_2GHZ_SUBNET
        else:
            interface = _AP_5GHZ_INTERFACE
            subnet = _AP_5GHZ_SUBNET

        # In order to handle dhcp servers on any interface, the initiation of
        # the dhcp server must be done after the wlan interfaces are figured
        # out as opposed to being in __init__
        self._dhcp = dhcp_server.DhcpServer(self.ssh, interface=interface)

        # For multi bssid configurations the mac address
        # of the wireless interface needs to have enough space to mask out
        # up to 8 different mac addresses.  The easiest way to do this
        # is to set the last byte to 0.  While technically this could
        # cause a duplicate mac address it is unlikely and will allow for
        # one radio to have up to 8 APs on the interface.  The check ensures
        # backwards compatibility since if someone has set the bssid on purpose
        # the bssid will not be changed from what the user set.
        interface_mac_orig = None
        if not hostapd_config.bssid:
            cmd = "ifconfig %s|grep ether|awk -F' ' '{print $2}'" % interface
            interface_mac_orig = self.ssh.run(cmd)
            interface_mac = interface_mac_orig.stdout[:-1] + '0'
            hostapd_config.bssid = interface_mac

        if interface in self._aps:
            raise ValueError('No WiFi interface available for AP on '
                             'channel %d' % hostapd_config.channel)

        apd = hostapd.Hostapd(self.ssh, interface)
        new_instance = _ApInstance(hostapd=apd, subnet=subnet)
        self._aps[interface] = new_instance

        # Turn off the DHCP server, we're going to change its settings.
        self._dhcp.stop()
        # Clear all routes to prevent old routes from interfering.
        self._route_cmd.clear_routes(net_interface=interface)

        if hostapd_config.bss_lookup:
            # The dhcp_bss dictionary is created to hold the key/value
            # pair of the interface name and the ip scope that will be
            # used for the particular interface.  The a, b, c, d
            # variables below are the octets for the ip address.  The
            # third octet is then incremented for each interface that
            # is requested.  This part is designed to bring up the
            # hostapd interfaces and not the DHCP servers for each
            # interface.
            dhcp_bss = {}
            counter = 1
            for bss in hostapd_config.bss_lookup:
                if not hostapd_config.bss_lookup[bss].bssid:
                    if interface_mac_orig:
                        hostapd_config.bss_lookup[
                            bss].bssid = interface_mac_orig.stdout[:-1] + str(
                                counter)
                self._route_cmd.clear_routes(net_interface=str(bss))
                if interface is _AP_2GHZ_INTERFACE:
                    starting_ip_range = _AP_2GHZ_SUBNET_STR
                else:
                    starting_ip_range = _AP_5GHZ_SUBNET_STR
                a, b, c, d = starting_ip_range.split('.')
                dhcp_bss[bss] = dhcp_config.Subnet(
                    ipaddress.ip_network('%s.%s.%s.%s' % (a, b, str(
                        int(c) + counter), d)))
                counter = counter + 1

        apd.start(hostapd_config, additional_parameters=additional_parameters)

        # The DHCP serer requires interfaces to have ips and routes before
        # the server will come up.
        interface_ip = ipaddress.ip_interface(
            '%s/%s' % (subnet.router, subnet.network.netmask))
        self._ip_cmd.set_ipv4_address(interface, interface_ip)
        if hostapd_config.bss_lookup:
            # This loop goes through each interface that was setup for
            # hostapd and assigns the DHCP scopes that were defined but
            # not used during the hostapd loop above.  The k and v
            # variables represent the interface name, k, and dhcp info, v.
            for k, v in dhcp_bss.items():
                bss_interface_ip = ipaddress.ip_interface(
                    '%s/%s' %
                    (dhcp_bss[k].router, dhcp_bss[k].network.netmask))
                self._ip_cmd.set_ipv4_address(str(k), bss_interface_ip)

        # Restart the DHCP server with our updated list of subnets.
        configured_subnets = [x.subnet for x in self._aps.values()]
        if hostapd_config.bss_lookup:
            for k, v in dhcp_bss.items():
                configured_subnets.append(v)

        self._dhcp.start(config=dhcp_config.DhcpConfig(configured_subnets))

        return interface

    def get_bssid_from_ssid(self, ssid):
        """Gets the BSSID from a provided SSID

        Args:
            ssid: An SSID string
        Returns: The BSSID if on the AP or None is SSID could not be found.
        """

        cmd = "iw dev %s info|grep addr|awk -F' ' '{print $2}'" % str(ssid)
        iw_output = self.ssh.run(cmd)
        if 'command failed: No such device' in iw_output.stderr:
            return None
        else:
            return iw_output.stdout

    def stop_ap(self, identifier):
        """Stops a running ap on this controller.

        Args:
            identifier: The identify of the ap that should be taken down.
        """

        if identifier not in self._aps:
            raise ValueError('Invalid identifer %s given' % identifier)

        instance = self._aps.get(identifier)

        instance.hostapd.stop()
        self._dhcp.stop()
        self._ip_cmd.clear_ipv4_addresses(identifier)

        # DHCP server needs to refresh in order to tear down the subnet no
        # longer being used. In the event that all interfaces are torn down
        # then an exception gets thrown. We need to catch this exception and
        # check that all interfaces should actually be down.
        configured_subnets = [x.subnet for x in self._aps.values()]
        if configured_subnets:
            self._dhcp.start(dhcp_config.DhcpConfig(configured_subnets))

    def stop_all_aps(self):
        """Stops all running aps on this device."""

        for ap in self._aps.keys():
            try:
                self.stop_ap(ap)
            except dhcp_server.NoInterfaceError as e:
                pass

    def close(self):
        """Called to take down the entire access point.

        When called will stop all aps running on this host, shutdown the dhcp
        server, and stop the ssh conneciton.
        """

        if self._aps:
            self.stop_all_aps()
            self._dhcp.stop()

        self.ssh.close()
