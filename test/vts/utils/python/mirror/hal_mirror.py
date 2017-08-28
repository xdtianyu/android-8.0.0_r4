#
# Copyright (C) 2016 The Android Open Source Project
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

from google.protobuf import text_format

from vts.runners.host import errors
from vts.proto import AndroidSystemControlMessage_pb2 as ASysCtrlMsg
from vts.proto import ComponentSpecificationMessage_pb2 as CompSpecMsg
from vts.runners.host.tcp_client import vts_tcp_client
from vts.runners.host.tcp_server import callback_server
from vts.utils.python.mirror import mirror_object

COMPONENT_CLASS_DICT = {"hal_conventional": 1,
                        "hal_conventional_submodule": 2,
                        "hal_legacy": 3,
                        "hal_hidl": 4,
                        "hal_hidl_wrapped_conventional": 5,
                        "lib_shared": 11}

COMPONENT_TYPE_DICT = {"audio": 1,
                       "camera": 2,
                       "gps": 3,
                       "light": 4,
                       "wifi": 5,
                       "mobile": 6,
                       "bluetooth": 7,
                       "nfc": 8,
                       "vibrator": 12,
                       "thermal": 13,
                       "tv_input": 14,
                       "tv_cec": 15,
                       "sensors": 16,
                       "vehicle": 17,
                       "vr": 18,
                       "graphics_allocator": 19,
                       "graphics_mapper": 20,
                       "radio": 21,
                       "contexthub": 22,
                       "graphics_composer": 23,
                       "media_omx": 24,
                       "bionic_libm": 1001,
                       "bionic_libc": 1002,
                       "vndk_libcutils": 1101}

VTS_CALLBACK_SERVER_TARGET_SIDE_PORT = 5010

_DEFAULT_TARGET_BASE_PATHS = ["/system/lib64/hw"]
_DEFAULT_HWBINDER_SERVICE = "default"

class HalMirror(object):
    """The class that acts as the mirror to an Android device's HAL layer.

    This class holds and manages the life cycle of multiple mirror objects that
    map to different HAL components.

    One can use this class to create and destroy a HAL mirror object.

    Attributes:
        _hal_level_mirrors: dict, key is HAL handler name, value is HAL
                            mirror object.
        _callback_server: VtsTcpServer, the server that receives and handles
                          callback messages from target side.
        _host_command_port: int, the host-side port for command-response
                            sessions.
        _host_callback_port: int, the host-side port for callback sessions.
        _client: VtsTcpClient, the client instance that can be used to send
                 commands to the target-side's agent.
    """

    def __init__(self, host_command_port, host_callback_port):
        self._hal_level_mirrors = {}
        self._host_command_port = host_command_port
        self._host_callback_port = host_callback_port
        self._callback_server = None

    def __del__(self):
        self.CleanUp()

    def CleanUp(self):
        """Shutdown services and release resources held by the HalMirror.
        """
        for hal_mirror_name in self._hal_level_mirrors.values():
            hal_mirror_name.CleanUp()
        self._hal_level_mirrors = {}
        if self._callback_server:
            self._callback_server.Stop()
            self._callback_server = None

    def InitConventionalHal(self,
                            target_type,
                            target_version,
                            target_package="",
                            target_basepaths=_DEFAULT_TARGET_BASE_PATHS,
                            handler_name=None,
                            bits=64):
        """Initiates a handler for a particular conventional HAL.

        This will initiate a driver service for a HAL on the target side, create
        the top level mirror object for a HAL, and register it in the manager.

        Args:
            target_type: string, the target type name (e.g., light, camera).
            target_version: float, the target component version (e.g., 1.0).
            target_basepaths: list of strings, the paths to look for target
                             files in. Default is _DEFAULT_TARGET_BASE_PATHS.
            target_package: . separated string (e.g., a.b.c) to denote the
                            package name of target component.
            handler_name: string, the name of the handler. target_type is used
                          by default.
            bits: integer, processor architecture indicator: 32 or 64.
        """
        self._CreateMirrorObject("hal_conventional",
                                 target_type,
                                 target_version,
                                 target_package=target_package,
                                 target_basepaths=target_basepaths,
                                 handler_name=handler_name,
                                 bits=bits)

    def InitLegacyHal(self,
                      target_type,
                      target_version,
                      target_basepaths=_DEFAULT_TARGET_BASE_PATHS,
                      handler_name=None,
                      bits=64):
        """Initiates a handler for a particular legacy HAL.

        This will initiate a driver service for a HAL on the target side, create
        the top level mirror object for a HAL, and register it in the manager.

        Args:
            target_type: string, the target type name (e.g., light, camera).
            target_version: float, the target component version (e.g., 1.0).
            target_basepaths: list of strings, the paths to look for target
                             files in. Default is _DEFAULT_TARGET_BASE_PATHS.
            handler_name: string, the name of the handler. target_type is used
                          by default.
            bits: integer, processor architecture indicator: 32 or 64.
        """
        self._CreateMirrorObject("hal_legacy",
                                 target_type,
                                 target_version,
                                 target_basepaths=target_basepaths,
                                 handler_name=handler_name,
                                 bits=bits)

    def InitHidlHal(self,
                    target_type,
                    target_version,
                    target_package=None,
                    target_component_name=None,
                    target_basepaths=_DEFAULT_TARGET_BASE_PATHS,
                    handler_name=None,
                    hw_binder_service_name=_DEFAULT_HWBINDER_SERVICE,
                    bits=64):
        """Initiates a handler for a particular HIDL HAL.

        This will initiate a driver service for a HAL on the target side, create
        the top level mirror object for a HAL, and register it in the manager.

        Args:
            target_type: string, the target type name (e.g., light, camera).
            target_version: float, the target component version (e.g., 1.0).
            target_package: string, the package name of a target HIDL HAL.
            target_basepaths: list of strings, the paths to look for target
                              files in. Default is _DEFAULT_TARGET_BASE_PATHS.
            handler_name: string, the name of the handler. target_type is used
                          by default.
            hw_binder_service_name: string, the name of a HW binder service.
            bits: integer, processor architecture indicator: 32 or 64.
        """
        self._CreateMirrorObject("hal_hidl",
                                 target_type,
                                 target_version,
                                 target_package=target_package,
                                 target_component_name=target_component_name,
                                 target_basepaths=target_basepaths,
                                 handler_name=handler_name,
                                 hw_binder_service_name=hw_binder_service_name,
                                 bits=bits)

    def RemoveHal(self, handler_name):
        self._hal_level_mirrors[handler_name].CleanUp()
        self._hal_level_mirrors.pop(handler_name)

    def _StartCallbackServer(self):
        """Starts the callback server.

        Raises:
            errors.ComponentLoadingError is raised if the callback server fails
            to start.
        """
        self._callback_server = callback_server.CallbackServer()
        _, port = self._callback_server.Start(self._host_callback_port)
        if port != self._host_callback_port:
            raise errors.ComponentLoadingError(
                "Failed to start a callback TcpServer at port %s" %
                self._host_callback_port)

    def _CreateMirrorObject(self,
                            target_class,
                            target_type,
                            target_version,
                            target_package=None,
                            target_component_name=None,
                            target_basepaths=_DEFAULT_TARGET_BASE_PATHS,
                            handler_name=None,
                            hw_binder_service_name=None,
                            bits=64):
        """Initiates the driver for a HAL on the target device and creates a top
        level MirroObject for it.

        Also starts the callback server to listen for callback invocations.

        Args:
            target_class: string, the target class name (e.g., hal).
            target_type: string, the target type name (e.g., light, camera).
            target_version: float, the target component version (e.g., 1.0).
            target_package: string, the package name of a HIDL HAL.
            target_component_name: string, the name of a target component.
            target_basepaths: list of strings, the paths to look for target
                             files in. Default is _DEFAULT_TARGET_BASE_PATHS.
            handler_name: string, the name of the handler. target_type is used
                          by default.
            hw_binder_service_name: string, the name of a HW binder service.
            bits: integer, processor architecture indicator: 32 or 64.

        Raises:
            errors.ComponentLoadingError is raised when error occurs trying to
            create a MirrorObject.
        """
        if bits not in [32, 64]:
            raise error.ComponentLoadingError("Invalid value for bits: %s" %
                                              bits)
        self._StartCallbackServer()
        self._client = vts_tcp_client.VtsTcpClient()
        self._client.Connect(command_port=self._host_command_port,
                             callback_port=self._host_callback_port)
        if not handler_name:
            handler_name = target_type
        service_name = "vts_driver_%s" % handler_name

        target_filename = None
        if target_class == "hal_conventional" or target_class == "hal_legacy":
            # Get all the HALs available on the target.
            hal_list = self._client.ListHals(target_basepaths)
            if not hal_list:
                raise errors.ComponentLoadingError(
                    "Could not find any HAL under path %s" % target_basepaths)
            logging.debug(hal_list)

            # Find the corresponding filename for HAL target type.
            for name in hal_list:
                if target_type in name:
                    # TODO: check more exactly (e.g., multiple hits).
                    target_filename = name
            if not target_filename:
                raise errors.ComponentLoadingError(
                    "No file found for HAL target type %s." % target_type)

            # Check whether the requested binder service is already running.
            # if client.CheckDriverService(service_name=service_name):
            #     raise errors.ComponentLoadingError("A driver for %s already exists" %
            #                                        service_name)
        elif target_class == "hal_hidl":
            # TODO: either user the default hw-binder service or start a new
            # service (e.g., if an instrumented binary is used).
            pass

        # Launch the corresponding driver of the requested HAL on the target.
        logging.info("Init the driver service for %s", target_type)
        target_class_id = COMPONENT_CLASS_DICT[target_class.lower()]
        target_type_id = COMPONENT_TYPE_DICT[target_type.lower()]
        driver_type = {
            "hal_conventional": ASysCtrlMsg.VTS_DRIVER_TYPE_HAL_CONVENTIONAL,
            "hal_legacy": ASysCtrlMsg.VTS_DRIVER_TYPE_HAL_LEGACY,
            "hal_hidl": ASysCtrlMsg.VTS_DRIVER_TYPE_HAL_HIDL
        }.get(target_class)

        launched = self._client.LaunchDriverService(
            driver_type=driver_type,
            service_name=service_name,
            bits=bits,
            file_path=target_filename,
            target_class=target_class_id,
            target_type=target_type_id,
            target_version=target_version,
            target_package=target_package,
            target_component_name=target_component_name,
            hw_binder_service_name=hw_binder_service_name)

        if not launched:
            raise errors.ComponentLoadingError(
                "Failed to launch driver service %s from file path %s" %
                (target_type, target_filename))

        # Create API spec message.
        found_api_spec = self._client.ListApis()
        if not found_api_spec:
            raise errors.ComponentLoadingError("No API found for %s" %
                                               service_name)
        logging.debug("Found %d APIs for %s:\n%s", len(found_api_spec),
                      service_name, found_api_spec)
        if_spec_msg = CompSpecMsg.ComponentSpecificationMessage()
        text_format.Merge(found_api_spec, if_spec_msg)

        # Instantiate a MirrorObject and return it.
        hal_mirror = mirror_object.MirrorObject(self._client, if_spec_msg,
                                                self._callback_server)
        self._hal_level_mirrors[handler_name] = hal_mirror

    def __getattr__(self, name):
        return self._hal_level_mirrors[name]

    def Ping(self):
      """Returns true iff pinging the agent is successful, False otherwise."""
      return self._client.Ping()
