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
import os

from vts.testcases.template.gtest_binary_test import gtest_test_case

class VtsHalMediaOmxV1_0TestCase(gtest_test_case.GtestTestCase):
    """A class to represent a media_omx test case.

    Attributes:
        component: string, name of a IOmxNode component.
        role: string, role of a IOmxNode component.
        instance: string, name of the binderized hal server instance.
    """

    def __init__(self, component, role, instance, *args, **kwargs):
        super(VtsHalMediaOmxV1_0TestCase, self).__init__(*args, **kwargs)
        self._component = component
        self._role = role
        self._instance = instance

    # @Override
    def GetRunCommand(self):
        """Get the command to run the test. """

        orig_cmds = super(VtsHalMediaOmxV1_0TestCase,
                          self).GetRunCommand(test_name=self.test_suite),

        new_cmd = [('{cmd} -I {instance} -C {component} -R {role}').format(
            cmd=orig_cmds[0][0],
            instance=self._instance,
            component=self._component,
            role=self._role), orig_cmds[0][1]]

        return new_cmd
