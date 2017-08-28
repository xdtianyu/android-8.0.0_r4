#
# Copyright 2016 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import logging

from vts.utils.python.common import cmd_utils


class CloudClientController(object):
    '''Controller class to interact with TradeFed.

    Attributes:
        path_cmdfile: string, path to TradeFed cmdfile
    '''
    DEFAULT_PATH_CMDFILE = 'cmdfile.txt'

    def __init__(self, path_cmdfile=None):
        if not path_cmdfile:
            path_cmdfile = self.DEFAULT_PATH_CMDFILE
        self._path_cmdfile = path_cmdfile

    def ExecuteTfCommands(self, cmds):
        '''Execute a TradeFed command or a list of TradeFed commands.

        Args:
            cmds: string or list of string, commands
        '''
        if not isinstance(cmds, list):
            cmds = [cmds]

        cmd = '\n'.join(cmds)

        with open(self._path_cmdfile, 'w') as f:
            f.write(cmd)