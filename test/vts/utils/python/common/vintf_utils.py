#
# Copyright (C) 2017 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import logging

from xml.etree import ElementTree

_ARCH = "arch"
_HWBINDER = "hwbinder"
_NAME = "name"
_PASSTHROUGH = "passthrough"
_TRANSPORT = "transport"
_VERSION = "version"
_INTERFACE = "interface"
_INSTANCE = "instance"


class HalInterfaceDescription(object):
    """Class to store the information of a running hal service interface.

    Attributes:
        hal_interface_name: interface name within the hal e.g. INfc.
        hal_instance_instances: a list of instance name of the registered hal service e.g. default. nfc
    """

    def __init__(self, hal_interface_name, hal_interface_instances):
        self.hal_interface_name = hal_interface_name
        self.hal_interface_instances = hal_interface_instances


class HalDescription(object):
    """Class to store the information of a running hal service.

    Attributes:
        hal_name: hal name e.g. android.hardware.nfc.
        hal_version: hal version e.g. 1.0.
        hal_interfaces: a list of HalInterfaceDescription within the hal.
        hal_archs: a list of strings where each string indicates the supported
                   client bitness (e.g,. ["32", "64"]).
    """

    def __init__(self, hal_name, hal_version, hal_interfaces, hal_archs):
        self.hal_name = hal_name
        self.hal_version = hal_version
        self.hal_interfaces = hal_interfaces
        self.hal_archs = hal_archs


def GetHalDescriptions(vintf_xml):
    """Parses a vintf xml string.

    Args:
        vintf_xml: string, containing a vintf.xml file content.

    Returns:
        a dictionary containing the information of hwbinder HALs,
        a dictionary containing the information of passthrough HALs.
    """
    try:
        xml_root = ElementTree.fromstring(vintf_xml)
    except ElementTree.ParseError as e:
        logging.exception(e)
        logging.error('This vintf xml could not be parsed:\n%s' % vintf_xml)
        return None, None

    hwbinder_hals = dict()
    passthrough_hals = dict()

    for xml_hal in xml_root:
        hal_name = None
        hal_transport = None
        hal_version = None
        hal_interfaces = []
        hal_archs = ["32", "64"]
        for xml_hal_item in xml_hal:
            tag = str(xml_hal_item.tag)
            if tag == _NAME:
                hal_name = str(xml_hal_item.text)
            elif tag == _TRANSPORT:
                hal_transport = str(xml_hal_item.text)
                if _ARCH in xml_hal_item.attrib:
                    hal_archs = xml_hal_item.attrib[_ARCH].split("+")
            elif tag == _VERSION:
                hal_version = str(xml_hal_item.text)
            elif tag == _INTERFACE:
                hal_interface_name = None
                hal_interface_instances = []
                for interface_item in xml_hal_item:
                    tag = str(interface_item.tag)
                    if tag == _NAME:
                        hal_interface_name = str(interface_item.text)
                    elif tag == _INSTANCE:
                        hal_interface_instances.append(
                            str(interface_item.text))
                hal_interfaces.append(
                    HalInterfaceDescription(hal_interface_name,
                                            hal_interface_instances))
        hal_info = HalDescription(hal_name, hal_version, hal_interfaces,
                                  hal_archs)
        hal_key = "%s@%s" % (hal_name, hal_version)
        if hal_transport == _HWBINDER:
            hwbinder_hals[hal_key] = hal_info
        elif hal_transport == _PASSTHROUGH:
            passthrough_hals[hal_key] = hal_info
        else:
            logging.error("Unknown transport type %s", hal_transport)
    return hwbinder_hals, passthrough_hals
