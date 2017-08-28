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

import os

from vts.testcases.template.binary_test import binary_test_case


class HalHidlReplayTestCase(binary_test_case.BinaryTestCase):
    '''A class to represent a gtest test case.

    Attributes:
        trace_path: string, path to the trace file.
        target_vts_driver_file_path: string, path to the hal driver lib.
        service_name: string, name of the binderized hal server instance.
        DEVICE_VTS_SPEC_FILE_PATH: string, the path of a VTS spec file
                                   stored in a target device.
    '''

    DEVICE_VTS_SPEC_FILE_PATH = "/data/local/tmp/spec"

    def __init__(self, trace_path, target_vts_driver_file_path, service_name,
                 *args, **kwargs):
        super(HalHidlReplayTestCase, self).__init__(*args, **kwargs)
        self._trace_path = trace_path
        self._target_vts_driver_file_path = target_vts_driver_file_path
        self._service_name = service_name

    # @Override
    def GetRunCommand(self):
        '''Get the command to run the test.

        Returns:
            List of strings
        '''

        cmd = ("LD_LIBRARY_PATH=%s:$LD_LIBRARY_PATH "
               "%s "
               "--mode=replay "
               "--trace_path=%s "
               "--spec_path=%s "
               "--hal_service_name=%s "
               "%s" % (self.ld_library_path, self.path, self._trace_path,
                       self.DEVICE_VTS_SPEC_FILE_PATH, self._service_name,
                       self._target_vts_driver_file_path))

        return cmd
