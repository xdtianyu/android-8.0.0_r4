#!/usr/bin/env python
#
# Copyright 2017 - The Android Open Source Project
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
#

# Create the configuration files for a hidl hal test.
# This script copy a template which contains Android.mk and AndroidTest.xml
# files under test/vts-testcases/hal/ based on the hal package name.

import datetime
import os
import sys

from build.vts_spec_parser import VtsSpecParser
from xml.dom import minidom
from xml.etree import cElementTree as ET
from xml.sax.saxutils import unescape

VTS_TEST_CASE_PATH = 'test/vts-testcase/hal'
HAL_INTERFACE_PATH = 'hardware/interfaces'
HAL_TRACE_PATH = 'test/vts-testcase/hal-trace'
HAL_PACKAGE_PREFIX = 'android.hardware.'

ANDROID_MK_FILE_NAME = 'Android.mk'
ANDROID_TEST_XML_FILE_NAME = 'AndroidTest.xml'


class TestCaseCreator(object):
    """Init a test case directory with helloworld test case.

    Attributes:
        hal_package_name: string, package name of the testing hal. e.g. android.hardware.nfc@1.0.
        hal_name: name of the testing hal, derived from hal_package_name. e.g. nfc.
        hal_version: version of the testing hal, derived from hal_package_name.
        test_type: string, type of the test, currently support host and target.
        test_name_prefix: prefix of generated test name. e.g. android.hardware.nfc@1.0-test-target.
        test_name: test name generated. e.g. android.hardware.nfc@1.0-test-target-profiling.
        test_dir: string, test case absolute directory.
        time_out: string, timeout of the test, default is 1m.
        is_profiling: boolean, whether to create a profiling test case.
        stop_runtime: boolean whether to stop framework before the test.
        build_top: string, equal to environment variable ANDROID_BUILD_TOP.
        vts_spec_parser: tools that generates and parses vts spec with hidl-gen.
        current_year: current year.
    """

    def __init__(self, vts_spec_parser, hal_package_name):
        '''Initialize class attributes.'''
        self._hal_package_name = hal_package_name

        build_top = os.getenv('ANDROID_BUILD_TOP')
        if not build_top:
            print('Error: Missing ANDROID_BUILD_TOP env variable. Please run '
                  '\'. build/envsetup.sh; lunch <build target>\' Exiting...')
            sys.exit(1)
        self._build_top = build_top
        self._vts_spec_parser = vts_spec_parser

        [package, version] = hal_package_name.split('@')
        self._hal_name = package[len(HAL_PACKAGE_PREFIX):]
        self._hal_version = version

        self._current_year = datetime.datetime.now().year

    def LaunchTestCase(self,
                       test_type,
                       time_out='1m',
                       is_profiling=False,
                       is_replay=False,
                       stop_runtime=False,
                       update_only=False):
        """Create the necessary configuration files to launch a test case.

        Args:
          test_type: type of the test.
          time_out: timeout of the test.
          is_profiling: whether to create a profiling test case.
          stop_runtime: whether to stop framework before the test.
          update_only: flag to only update existing test configure.

        Returns:
          boolean, whether created/updated a test case successfully.
        """
        self._test_type = test_type
        self._time_out = time_out
        self._is_profiling = is_profiling
        self._is_replay = is_replay
        self._stop_runtime = stop_runtime

        self._test_module_name = self.GetVtsHalTestModuleName()
        self._test_name = self._test_module_name
        if is_replay:
            self._test_name = self._test_module_name + 'Replay'
        if is_profiling:
            self._test_name = self._test_module_name + 'Profiling'

        self._test_dir = self.GetHalTestCasePath()
        # Check whether the host side test script and target test binary is available.
        if self._test_type == 'host':
            test_script_file = self.GetVtsHostTestScriptFileName()
            if not os.path.exists(test_script_file):
                print('Could not find the host side test script: %s.' %
                      test_script_file)
                return False
        else:
            test_binary_file = self.GetVtsTargetTestSourceFileName()
            if not os.path.exists(test_binary_file):
                print('Could not find the target side test binary: %s.' %
                      test_binary_file)
                return False

        if os.path.exists(self._test_dir):
            print 'WARNING: Test directory already exists. Continuing...'
        elif not update_only:
            try:
                os.makedirs(self._test_dir)
            except:
                print('Error: Failed to create test directory at %s. '
                      'Exiting...' % self.test_dir)
                return False
        else:
            print('WARNING: Test directory does not exists, stop updating.')
            return True

        self.CreateAndroidMk()
        self.CreateAndroidTestXml()
        return True

    def GetVtsTargetTestSourceFileName(self):
        """Get the name of target side test source file ."""
        test_binary_name = self._test_module_name + 'Test.cpp'
        return os.path.join(self.GetHalInterfacePath(), 'vts/functional',
                            test_binary_name)

    def GetVtsHostTestScriptFileName(self):
        """Get the name of host side test script file ."""
        test_script_name = self._test_module_name + 'Test.py'
        return os.path.join(
            self.GetHalTestCasePath(ignore_profiling=True), test_script_name)

    def GetVtsHalTestModuleName(self):
        """Get the test model name with format VtsHalHalNameVersionTestType."""
        sub_names = self._hal_name.split('.')
        hal_name_upper_camel = ''.join(x.title() for x in sub_names)
        return 'VtsHal' + hal_name_upper_camel + self.GetHalVersionToken(
        ) + self._test_type.title()

    def GetVtsHalReplayTraceFiles(self):
        """Get the trace files for replay test."""
        trace_files = []
        for filename in os.listdir(self.GetHalTracePath()):
            if filename.endswith(".trace"):
                trace_files.append(filename)
        return trace_files

    def GetHalPath(self):
        """Get the hal path based on hal name."""
        return self._hal_name.replace('.', '/')

    def GetHalVersionToken(self):
        """Get a string of the hal version."""
        return 'V' + self._hal_version.replace('.', '_')

    def GetHalInterfacePath(self):
        """Get the directory that stores the .hal files."""
        return os.path.join(self._build_top, HAL_INTERFACE_PATH,
                            self.GetHalPath(), self._hal_version)

    def GetHalTestCasePath(self, ignore_profiling=False):
        """Get the directory that stores the test case."""
        test_dir = self._test_type
        if self._is_replay:
            test_dir = test_dir + '_replay'
        if self._is_profiling and not ignore_profiling:
            test_dir = test_dir + '_profiling'
        return os.path.join(self._build_top, VTS_TEST_CASE_PATH,
                            self.GetHalPath(),
                            self.GetHalVersionToken(), test_dir)

    def GetHalTracePath(self):
        """Get the directory that stores the hal trace files."""
        return os.path.join(self._build_top, HAL_TRACE_PATH,
                            self.GetHalPath(), self.GetHalVersionToken())

    def CreateAndroidMk(self):
        """Create Android.mk."""
        vts_dir = os.path.join(self._build_top, VTS_TEST_CASE_PATH)
        target = os.path.join(self._test_dir, ANDROID_MK_FILE_NAME)

        with open(target, 'w') as f:
            print 'Creating %s' % target
            f.write(LICENSE_STATEMENT_POUND.format(year=self._current_year))
            f.write('\n')
            f.write(
                ANDROID_MK_TEMPLATE.format(
                    test_name=self._test_name,
                    config_src_dir=self._test_dir[len(vts_dir) + 1:]))

    def CreateAndroidTestXml(self):
        """Create AndroidTest.xml."""
        VTS_FILE_PUSHER = 'com.android.compatibility.common.tradefed.targetprep.VtsFilePusher'
        VTS_PYTHON_PREPARER = 'com.android.tradefed.targetprep.VtsPythonVirtualenvPreparer'
        VTS_TEST_CLASS = 'com.android.tradefed.testtype.VtsMultiDeviceTest'

        configuration = ET.Element('configuration', {
            'description': 'Config for VTS ' + self._test_name + ' test cases'
        })
        file_pusher = ET.SubElement(configuration, 'target_preparer',
                                    {'class': VTS_FILE_PUSHER})

        self.GeneratePushFileConfigure(file_pusher)
        python_preparer = ET.SubElement(configuration, 'target_preparer',
                                        {'class': VTS_PYTHON_PREPARER})
        test = ET.SubElement(configuration, 'test', {'class': VTS_TEST_CLASS})

        self.GenerateTestOptionConfigure(test)

        target = os.path.join(self._test_dir, ANDROID_TEST_XML_FILE_NAME)
        with open(target, 'w') as f:
            print 'Creating %s' % target
            f.write(XML_HEADER)
            f.write(LICENSE_STATEMENT_XML.format(year=self._current_year))
            f.write(self.Prettify(configuration))

    def GeneratePushFileConfigure(self, file_pusher):
        """Create the push file configuration within AndroidTest.xml

        Args:
          file_pusher: parent xml element for push file configure.
        """
        ET.SubElement(file_pusher, 'option',
                      {'name': 'abort-on-push-failure',
                       'value': 'false'})

        if self._test_type == 'target':
            if self._is_replay:
                ET.SubElement(file_pusher, 'option', {
                    'name': 'push-group',
                    'value': 'HalHidlHostTest.push'
                })
            elif self._is_profiling:
                ET.SubElement(file_pusher, 'option', {
                    'name': 'push-group',
                    'value': 'HalHidlTargetProfilingTest.push'
                })
            else:
                ET.SubElement(file_pusher, 'option', {
                    'name': 'push-group',
                    'value': 'HalHidlTargetTest.push'
                })
        else:
            if self._is_profiling:
                ET.SubElement(file_pusher, 'option', {
                    'name': 'push-group',
                    'value': 'HalHidlHostProfilingTest.push'
                })
            else:
                ET.SubElement(file_pusher, 'option', {
                    'name': 'push-group',
                    'value': 'HalHidlHostTest.push'
                })

        imported_package_lists = self._vts_spec_parser.ImportedPackagesList(
            self._hal_name, self._hal_version)
        imported_package_lists.append(self._hal_package_name)
        # Generate additional push files e.g driver/profiler/vts_spec
        if self._test_type == 'host' or self._is_replay:
            ET.SubElement(file_pusher, 'option',
                          {'name': 'cleanup',
                           'value': 'true'})
            for imported_package in imported_package_lists:
                if imported_package.startswith(HAL_PACKAGE_PREFIX):
                    imported_package_str, imported_package_version = imported_package.split(
                        '@')
                    imported_package_name = imported_package_str[len(
                        HAL_PACKAGE_PREFIX):]
                    imported_vts_spec_lists = self._vts_spec_parser.VtsSpecNames(
                        imported_package_name, imported_package_version)
                    for vts_spec in imported_vts_spec_lists:
                        push_spec = VTS_SPEC_PUSH_TEMPLATE.format(
                            hal_path=imported_package_name.replace('.', '/'),
                            hal_version=imported_package_version,
                            package_path=imported_package_str.replace('.',
                                                                      '/'),
                            vts_file=vts_spec)
                        ET.SubElement(file_pusher, 'option',
                                      {'name': 'push',
                                       'value': push_spec})

                    dirver_package_name = imported_package + '-vts.driver.so'
                    push_driver = VTS_LIB_PUSH_TEMPLATE_32.format(
                        lib_name=dirver_package_name)
                    ET.SubElement(file_pusher, 'option',
                                  {'name': 'push',
                                   'value': push_driver})
                    push_driver = VTS_LIB_PUSH_TEMPLATE_64.format(
                        lib_name=dirver_package_name)
                    ET.SubElement(file_pusher, 'option',
                                  {'name': 'push',
                                   'value': push_driver})

        if self._is_profiling:
            if self._test_type == 'target':
                ET.SubElement(file_pusher, 'option',
                              {'name': 'cleanup',
                               'value': 'true'})
            for imported_package in imported_package_lists:
                if imported_package.startswith(HAL_PACKAGE_PREFIX):
                    profiler_package_name = imported_package + '-vts.profiler.so'
                    push_profiler = VTS_LIB_PUSH_TEMPLATE_32.format(
                        lib_name=profiler_package_name)
                    ET.SubElement(file_pusher, 'option',
                                  {'name': 'push',
                                   'value': push_profiler})
                    push_profiler = VTS_LIB_PUSH_TEMPLATE_64.format(
                        lib_name=profiler_package_name)
                    ET.SubElement(file_pusher, 'option',
                                  {'name': 'push',
                                   'value': push_profiler})

    def GenerateTestOptionConfigure(self, test):
        """Create the test option configuration within AndroidTest.xml

        Args:
          test: parent xml element for test option configure.
        """
        ET.SubElement(test, 'option',
                      {'name': 'test-module-name',
                       'value': self._test_name})

        if self._test_type == 'target':
            if self._is_replay:
                ET.SubElement(test, 'option', {
                    'name': 'binary-test-type',
                    'value': 'hal_hidl_replay_test'
                })
                for trace in self.GetVtsHalReplayTraceFiles():
                    ET.SubElement(
                        test,
                        'option', {
                            'name': 'hal-hidl-replay-test-trace-path',
                            'value': TEST_TRACE_TEMPLATE.format(
                                hal_path=self.GetHalPath(),
                                hal_version=self.GetHalVersionToken(),
                                trace_file=trace)
                        })
                ET.SubElement(test, 'option', {
                    'name': 'hal-hidl-package-name',
                    'value': self._hal_package_name
                })
            else:
                test_binary = TEST_BINEARY_TEMPLATE_32.format(
                    test_name=self._test_module_name + 'Test')
                ET.SubElement(test, 'option', {
                    'name': 'binary-test-source',
                    'value': test_binary
                })
                test_binary = TEST_BINEARY_TEMPLATE_64.format(
                    test_name=self._test_module_name + 'Test')
                ET.SubElement(test, 'option', {
                    'name': 'binary-test-source',
                    'value': test_binary
                })
                ET.SubElement(test, 'option', {
                    'name': 'binary-test-type',
                    'value': 'hal_hidl_gtest'
                })
                if self._stop_runtime:
                    ET.SubElement(test, 'option', {
                        'name': 'binary-test-disable-framework',
                        'value': 'true'
                    })
                    ET.SubElement(test, 'option', {
                        'name': 'binary-test-stop-native-servers',
                        'value': 'true'
                    })
        else:
            test_script = TEST_SCRIPT_TEMPLATE.format(
                hal_path=self.GetHalPath(),
                hal_version=self.GetHalVersionToken(),
                test_name=self._test_module_name + 'Test')
            ET.SubElement(test, 'option',
                          {'name': 'test-case-path',
                           'value': test_script})

        if self._is_profiling:
            ET.SubElement(test, 'option',
                          {'name': 'enable-profiling',
                           'value': 'true'})

        ET.SubElement(test, 'option', {
            'name': 'precondition-lshal',
            'value': self._hal_package_name
        })
        ET.SubElement(test, 'option',
                      {'name': 'test-timeout',
                       'value': self._time_out})

    def Prettify(self, elem):
        """Create a pretty-printed XML string for the Element.

        Args:
          elem: a xml element.

        Regurns:
          A pretty-printed XML string for the Element.
        """
        if elem:
            doc = minidom.Document()
            declaration = doc.toxml()
            rough_string = ET.tostring(elem, 'utf-8')
            reparsed = minidom.parseString(rough_string)
            return unescape(
                reparsed.toprettyxml(indent="    ")[len(declaration) + 1:])


LICENSE_STATEMENT_POUND = """#
# Copyright (C) {year} The Android Open Source Project
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
"""

LICENSE_STATEMENT_XML = """<!-- Copyright (C) {year} The Android Open Source Project

     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
-->
"""

ANDROID_MK_TEMPLATE = """LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := {test_name}
VTS_CONFIG_SRC_DIR := testcases/hal/{config_src_dir}
include test/vts/tools/build/Android.host_config.mk
"""

XML_HEADER = """<?xml version="1.0" encoding="utf-8"?>
"""

TEST_BINEARY_TEMPLATE_32 = '_32bit::DATA/nativetest/{test_name}/{test_name}'
TEST_BINEARY_TEMPLATE_64 = '_64bit::DATA/nativetest64/{test_name}/{test_name}'

TEST_SCRIPT_TEMPLATE = 'vts/testcases/hal/{hal_path}/{hal_version}/host/{test_name}'
TEST_TRACE_TEMPLATE = 'test/vts-testcase/hal-trace/{hal_path}/{hal_version}/{trace_file}'
VTS_SPEC_PUSH_TEMPLATE = (
    'spec/hardware/interfaces/{hal_path}/{hal_version}/vts/{vts_file}->'
    '/data/local/tmp/spec/{package_path}/{hal_version}/{vts_file}')
VTS_LIB_PUSH_TEMPLATE_32 = 'DATA/lib/{lib_name}->/data/local/tmp/32/{lib_name}'
VTS_LIB_PUSH_TEMPLATE_64 = 'DATA/lib64/{lib_name}->/data/local/tmp/64/{lib_name}'
