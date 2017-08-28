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
from vts.utils.python.mirror import hal_mirror
from vts.utils.python.mirror import mirror_object

VTS_CALLBACK_SERVER_TARGET_SIDE_PORT = 5010

_DEFAULT_TARGET_BASE_PATHS = ["/system/lib64/hw"]


class LibMirror(object):
    """The class that acts as the mirror to an Android device's Lib layer.

    This class holds and manages the life cycle of multiple mirror objects that
    map to different lib components.

    One can use this class to create and destroy a lib mirror object.

    Attributes:
        _host_command_port: int, the host-side port for command-response
                            sessions.
        _lib_level_mirrors: dict, key is lib handler name, value is HAL
                            mirror object. internally, it uses a legacy_hal
                            mirror.
    """
    def __init__(self, host_command_port):
        self._lib_level_mirrors = {}
        self._host_command_port = host_command_port

    def __del__(self):
        for lib_mirror_name in self._lib_level_mirrors:
            self.RemoveLib(lib_mirror_name)
        self._lib_level_mirrors = {}

    def InitSharedLib(self,
                      target_type,
                      target_version,
                      target_basepaths=_DEFAULT_TARGET_BASE_PATHS,
                      target_package="",
                      target_filename=None,
                      handler_name=None,
                      bits=64):
        """Initiates a handler for a particular lib.

        This will initiate a driver service for a lib on the target side, create
        the top level mirror object for a lib, and register it in the manager.

        Args:
            target_type: string, the target type name (e.g., light, camera).
            target_version: float, the target component version (e.g., 1.0).
            target_basepaths: list of strings, the paths to look for target
                             files in. Default is _DEFAULT_TARGET_BASE_PATHS.
            target_package: . separated string (e.g., a.b.c) to denote the
                            package name of target component.
            target_filename: string, the target file name (e.g., libm.so).
            handler_name: string, the name of the handler. target_type is used
                          by default.
            bits: integer, processor architecture indicator: 32 or 64.
        """
        self._CreateMirrorObject("lib_shared",
                                 target_type,
                                 target_version,
                                 target_basepaths=target_basepaths,
                                 target_package=target_package,
                                 target_filename=target_filename,
                                 handler_name=handler_name,
                                 bits=bits)

    def RemoveLib(self, handler_name):
        lib_level_mirror = self._lib_level_mirrors[handler_name]
        lib_level_mirror.CleanUp()

    def _CreateMirrorObject(self,
                            target_class,
                            target_type,
                            target_version,
                            target_basepaths=_DEFAULT_TARGET_BASE_PATHS,
                            target_package="",
                            target_filename=None,
                            handler_name=None,
                            bits=64):
        """Initiates the driver for a lib on the target device and creates a top
        level MirroObject for it.

        Args:
            target_class: string, the target class name (e.g., lib).
            target_type: string, the target type name (e.g., light, camera).
            target_version: float, the target component version (e.g., 1.0).
            target_basepaths: list of strings, the paths to look for target
                             files in. Default is _DEFAULT_TARGET_BASE_PATHS.
            target_package: . separated string (e.g., a.b.c) to denote the
                            package name of target component.
            target_filename: string, the target file name (e.g., libm.so).
            handler_name: string, the name of the handler. target_type is used
                          by default.
            bits: integer, processor architecture indicator: 32 or 64.

        Raises:
            errors.ComponentLoadingError is raised when error occurs trying to
            create a MirrorObject.
        """
        if bits not in [32, 64]:
            raise error.ComponentLoadingError("Invalid value for bits: %s" % bits)
        client = vts_tcp_client.VtsTcpClient()
        client.Connect(command_port=self._host_command_port)
        if not handler_name:
            handler_name = target_type
        service_name = "vts_driver_%s" % handler_name

        # Get all the libs available on the target.
        lib_list = client.ListHals(target_basepaths)
        if not lib_list:
            raise errors.ComponentLoadingError(
                "Could not find any lib under path %s" % target_basepaths)
        logging.debug(lib_list)

        # Find the corresponding filename for Lib target type.
        if target_filename is not None:
            for name in lib_list:
                if name.endswith(target_filename):
                    target_filename = name
                    break
        else:
          for name in lib_list:
              if target_type in name:
                  # TODO: check more exactly (e.g., multiple hits).
                  target_filename = name

        if not target_filename:
            raise errors.ComponentLoadingError(
                "No file found for target type %s." % target_type)

        # Check whether the requested binder service is already running.
        # if client.CheckDriverService(service_name=service_name):
        #     raise errors.ComponentLoadingError("A driver for %s already exists" %
        #                                        service_name)

        # Launch the corresponding driver of the requested Lib on the target.
        logging.info("Init the driver service for %s", target_type)
        target_class_id = hal_mirror.COMPONENT_CLASS_DICT[target_class.lower()]
        target_type_id = hal_mirror.COMPONENT_TYPE_DICT[target_type.lower()]
        launched = client.LaunchDriverService(
            driver_type=ASysCtrlMsg.VTS_DRIVER_TYPE_HAL_CONVENTIONAL,
            service_name=service_name,
            bits=bits,
            file_path=target_filename,
            target_class=target_class_id,
            target_type=target_type_id,
            target_version=target_version,
            target_package=target_package)

        if not launched:
            raise errors.ComponentLoadingError(
                "Failed to launch driver service %s from file path %s" %
                (target_type, target_filename))

        # Create API spec message.
        found_api_spec = client.ListApis()
        if not found_api_spec:
            raise errors.ComponentLoadingError("No API found for %s" %
                                               service_name)
        logging.debug("Found %d APIs for %s:\n%s", len(found_api_spec),
                      service_name, found_api_spec)
        if_spec_msg = CompSpecMsg.ComponentSpecificationMessage()
        text_format.Merge(found_api_spec, if_spec_msg)

        # Instantiate a MirrorObject and return it.
        lib_mirror = mirror_object.MirrorObject(client, if_spec_msg, None)
        self._lib_level_mirrors[handler_name] = lib_mirror

    def __getattr__(self, name):
        return self._lib_level_mirrors[name]
