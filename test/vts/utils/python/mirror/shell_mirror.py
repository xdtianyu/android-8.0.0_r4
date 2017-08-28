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

from vts.runners.host import errors
from vts.proto import AndroidSystemControlMessage_pb2 as ASysCtrlMsg
from vts.runners.host.tcp_client import vts_tcp_client
from vts.utils.python.mirror import shell_mirror_object


class ShellMirror(object):
    """The class that acts as the mirror to an Android device's shell terminal.

    This class holds and manages the life cycle of multiple mirror objects that
    map to different shell instances.

    Attributes:
        _host_command_port: int, the host-side port for command-response
                            sessions.
        _shell_mirrors: dict, key is instance name, value is mirror object.
    """

    def __init__(self, host_command_port):
        self._shell_mirrors = {}
        self._host_command_port = host_command_port

    def __del__(self):
        for instance_name in self._shell_mirrors:
            self.RemoveShell(instance_name)
        self._shell_mirrors = {}

    def RemoveShell(self, instance_name):
        """Removes a shell mirror object.

        Args:
            instance_name: string, the shell terminal instance name.
        """
        shell_mirror = self._shell_mirrors[instance_name]
        shell_mirror.CleanUp()

    def InvokeTerminal(self, instance_name, bits=32):
        """Initiates a handler for a particular conventional HAL.

        This will initiate a driver service for a shell on the target side,
        create the top level mirror object for the same, and register it in
        the manager.

        Args:
            instance_name: string, the shell terminal instance name.
            bits: integer, processor architecture indicator: 32 or 64.
        """
        if not instance_name:
            raise error.ComponentLoadingError("instance_name is None")
        if bits not in [32, 64]:
            raise error.ComponentLoadingError("Invalid value for bits: %s" % bits)

        client = vts_tcp_client.VtsTcpClient()
        client.Connect(command_port=self._host_command_port)

        logging.info("Init the driver service for shell, %s", instance_name)
        launched = client.LaunchDriverService(
            driver_type=ASysCtrlMsg.VTS_DRIVER_TYPE_SHELL,
            service_name="shell_" + instance_name,
            bits=bits)

        if not launched:
            raise errors.ComponentLoadingError(
                "Failed to launch shell driver service %s" % instance_name)

        mirror_object = shell_mirror_object.ShellMirrorObject(client)
        self._shell_mirrors[instance_name] = mirror_object

    def __getattr__(self, name):
        return self._shell_mirrors[name]
