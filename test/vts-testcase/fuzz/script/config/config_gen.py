#
# Copyright (C) 2017 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the 'License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an 'AS IS' BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import os
import sys

from importlib import import_module


class FuzzerType(object):
    """Types of fuzzers."""
    FUNC_FUZZER = 0
    IFACE_FUZZER = 1


class ConfigGen(object):
    """Config generator for test/vts-testcase/fuzz.

    Attributes:
        _android_build_top: string, equal to environment variable ANDROID_BUILD_TOP.
        _project_path: string, path to test/vts-testcase/fuzz.
        _template_dir: string, path to directory containig templates.
        _utils: test/vts-testcase/hal/script/build/config_gen_utils module.
        _vts_spec_parser: tools that generates and parses vts spec with hidl-gen.
    """

    def __init__(self):
        """ConfigGen constructor. """
        self._android_build_top = os.environ.get('ANDROID_BUILD_TOP')
        if not self._android_build_top:
            print 'Run "lunch" command first.'
            sys.exit(1)
        self._project_path = os.path.join(self._android_build_top, 'test',
                                          'vts-testcase', 'fuzz')
        self._template_dir = os.path.join(self._project_path, 'script',
                                          'config', 'template')
        sys.path.append(
            os.path.join(self._android_build_top, 'test', 'vts-testcase', 'hal',
                         'script', 'build'))
        vts_spec_parser = import_module('vts_spec_parser')
        self._utils = import_module('build_rule_gen_utils')
        self._vts_spec_parser = vts_spec_parser.VtsSpecParser()

    def UpdateFuzzerConfigs(self):
        """Updates build rules for fuzzers.

        Updates fuzzer configs for each pair of (hal_name, hal_version).
        """
        config_dir = os.path.join(self._project_path, 'config')
        self._utils.RemoveFilesInDirIf(
            config_dir, lambda x: x == 'AndroidTest.xml' or 'Android.mk')

        self.UpdateFuzzerConfigsForType(FuzzerType.FUNC_FUZZER)
        self.UpdateFuzzerConfigsForType(FuzzerType.IFACE_FUZZER)

    def UpdateFuzzerConfigsForType(self, fuzzer_type):
        """Updates build rules for fuzzers.

        Updates fuzzer configs for given fuzzer type.

        Args:
            fuzzer_type: FuzzerType, type of fuzzer.
        """
        mk_template_path = os.path.join(self._template_dir, 'template.mk')
        xml_template_path = os.path.join(self._template_dir, 'template.xml')
        with open(mk_template_path) as template_file:
            mk_template = str(template_file.read())
        with open(xml_template_path) as template_file:
            xml_template = str(template_file.read())

        hal_list = self._vts_spec_parser.HalNamesAndVersions()
        for hal_name, hal_version in hal_list:
            if not self._IsTestable(hal_name, hal_version):
                continue
            fuzzer_type_subdir = self._FuzzerTypeUnderscore(fuzzer_type)
            config_dir = os.path.join(
                self._project_path, 'config', self._utils.HalNameDir(hal_name),
                self._utils.HalVerDir(hal_version), fuzzer_type_subdir)
            mk_file_path = os.path.join(config_dir, 'Android.mk')
            xml_file_path = os.path.join(config_dir, 'AndroidTest.xml')
            mk_string = self._FillOutTemplate(hal_name, hal_version,
                                              fuzzer_type, mk_template)

            xml_string = self._FillOutTemplate(hal_name, hal_version,
                                               fuzzer_type, xml_template)

            self._utils.WriteBuildRule(mk_file_path, mk_string)
            self._utils.WriteBuildRule(xml_file_path, xml_string)

    def _FuzzerTestName(self, hal_name, hal_version, fuzzer_type):
        """Returns vts hal fuzzer test module name.

        Args:
            hal_name: string, name of the hal, e.g. 'vibrator'.
            hal_version: string, version of the hal, e.g '7.4'
            fuzzer_type: FuzzerType, type of fuzzer.

        Returns:
            string, test module name, e.g. VtsHalVibratorV7_4FuncFuzzer
        """
        test_name = 'VtsHal'
        test_name += ''.join(map(lambda x: x.title(), hal_name.split('.')))
        test_name += self._utils.HalVerDir(hal_version)
        test_name += self._FuzzerTypeCamel(fuzzer_type)
        return test_name

    def _FuzzerTypeUnderscore(self, fuzzer_type):
        """Returns vts hal fuzzer type string in underscore case.

        Args:
            fuzzer_type: FuzzerType, type of fuzzer.

        Returns:
            string, fuzzer type, e.g. "iface_fuzzer"
        """
        if fuzzer_type == FuzzerType.FUNC_FUZZER:
            test_type = 'func_fuzzer'
        else:
            test_type = 'iface_fuzzer'
        return test_type

    def _FuzzerTypeCamel(self, fuzzer_type):
        """Returns vts hal fuzzer type string in camel case.

        Args:
            fuzzer_type: FuzzerType, type of fuzzer.

        Returns:
            string, fuzzer type, e.g. "IfaceFuzzer"
        """
        if fuzzer_type == FuzzerType.FUNC_FUZZER:
            test_type = 'FuncFuzzer'
        else:
            test_type = 'IfaceFuzzer'
        return test_type

    def _FillOutTemplate(self, hal_name, hal_version, fuzzer_type, template):
        """Returns build rules in string form by filling out given template.

        Args:
            hal_name: string, name of the hal, e.g. 'vibrator'.
            hal_version: string, version of the hal, e.g '7.4'
            fuzzer_type: FuzzerType, type of fuzzer.
            template: string, build rule template to fill out.

        Returns:
            string, complete build rule in string form.
        """
        config = template
        config = config.replace('{TEST_NAME}', self._FuzzerTestName(
            hal_name, hal_version, fuzzer_type))
        config = config.replace('{HAL_NAME}', hal_name)
        config = config.replace('{HAL_VERSION}', hal_version)
        config = config.replace('{TEST_TYPE_CAMEL}',
                                self._FuzzerTypeCamel(fuzzer_type))
        config = config.replace('{TEST_TYPE_UNDERSCORE}',
                                self._FuzzerTypeUnderscore(fuzzer_type))
        return config

    def _IsTestable(self, hal_name, hal_version):
        """Returns true iff hal can be tested."""
        if 'tests' in hal_name:
            return False
        return True
