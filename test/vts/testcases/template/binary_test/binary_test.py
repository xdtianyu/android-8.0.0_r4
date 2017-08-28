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
import os
import time

from vts.runners.host import asserts
from vts.runners.host import base_test
from vts.runners.host import const
from vts.runners.host import keys
from vts.runners.host import test_runner
from vts.utils.python.controllers import android_device
from vts.utils.python.common import list_utils
from vts.utils.python.os import path_utils
from vts.utils.python.precondition import precondition_utils
from vts.utils.python.web import feature_utils

from vts.testcases.template.binary_test import binary_test_case


class BinaryTest(base_test.BaseTestClass):
    '''Base class to run binary tests on target.

    Attributes:
        _dut: AndroidDevice, the device under test as config
        shell: ShellMirrorObject, shell mirror
        testcases: list of BinaryTestCase objects, list of test cases to run
        tags: all the tags that appeared in binary list
        DEVICE_TMP_DIR: string, temp location for storing binary
        TAG_DELIMITER: string, separator used to separate tag and path
        SYSPROP_VTS_NATIVE_SERVER: string, the name of a system property which
                                   tells whether to stop properly configured
                                   native servers where properly configured
                                   means a server's init.rc is configured to
                                   stop when that property's value is 1.
    '''
    SYSPROP_VTS_NATIVE_SERVER = "vts.native_server.on"

    DEVICE_TMP_DIR = '/data/local/tmp'
    TAG_DELIMITER = '::'
    PUSH_DELIMITER = '->'
    DEFAULT_TAG_32 = '_32bit'
    DEFAULT_TAG_64 = '_64bit'
    DEFAULT_LD_LIBRARY_PATH_32 = '/data/local/tmp/32/'
    DEFAULT_LD_LIBRARY_PATH_64 = '/data/local/tmp/64/'
    DEFAULT_PROFILING_LIBRARY_PATH_32 = '/data/local/tmp/32/'
    DEFAULT_PROFILING_LIBRARY_PATH_64 = '/data/local/tmp/64/'

    def setUpClass(self):
        '''Prepare class, push binaries, set permission, create test cases.'''
        required_params = [keys.ConfigKeys.IKEY_DATA_FILE_PATH, ]
        opt_params = [
            keys.ConfigKeys.IKEY_BINARY_TEST_SOURCE,
            keys.ConfigKeys.IKEY_BINARY_TEST_WORKING_DIRECTORY,
            keys.ConfigKeys.IKEY_BINARY_TEST_ENVP,
            keys.ConfigKeys.IKEY_BINARY_TEST_ARGS,
            keys.ConfigKeys.IKEY_BINARY_TEST_LD_LIBRARY_PATH,
            keys.ConfigKeys.IKEY_BINARY_TEST_PROFILING_LIBRARY_PATH,
            keys.ConfigKeys.IKEY_BINARY_TEST_DISABLE_FRAMEWORK,
            keys.ConfigKeys.IKEY_BINARY_TEST_STOP_NATIVE_SERVERS,
            keys.ConfigKeys.IKEY_NATIVE_SERVER_PROCESS_NAME,
        ]
        self.getUserParams(
            req_param_names=required_params, opt_param_names=opt_params)

        # test-module-name is required in binary tests.
        self.getUserParam(
            keys.ConfigKeys.KEY_TESTBED_NAME, error_if_not_found=True)

        logging.info("%s: %s", keys.ConfigKeys.IKEY_DATA_FILE_PATH,
                     self.data_file_path)

        self.binary_test_source = self.getUserParam(
            keys.ConfigKeys.IKEY_BINARY_TEST_SOURCE, default_value=[])

        self.working_directory = {}
        if hasattr(self, keys.ConfigKeys.IKEY_BINARY_TEST_WORKING_DIRECTORY):
            self.binary_test_working_directory = map(
                str, self.binary_test_working_directory)
            for token in self.binary_test_working_directory:
                tag = ''
                path = token
                if self.TAG_DELIMITER in token:
                    tag, path = token.split(self.TAG_DELIMITER)
                self.working_directory[tag] = path

        self.envp = {}
        if hasattr(self, keys.ConfigKeys.IKEY_BINARY_TEST_ENVP):
            self.binary_test_envp = map(str, self.binary_test_envp)
            for token in self.binary_test_envp:
                tag = ''
                path = token
                if self.TAG_DELIMITER in token:
                    tag, path = token.split(self.TAG_DELIMITER)
                if tag in self.envp:
                    self.envp[tag] += ' %s' % path
                else:
                    self.envp[tag] = path

        self.args = {}
        if hasattr(self, keys.ConfigKeys.IKEY_BINARY_TEST_ARGS):
            self.binary_test_args = map(str, self.binary_test_args)
            for token in self.binary_test_args:
                tag = ''
                arg = token
                if self.TAG_DELIMITER in token:
                    tag, arg = token.split(self.TAG_DELIMITER)
                if tag in self.args:
                    self.args[tag] += ' %s' % arg
                else:
                    self.args[tag] = arg

        self.ld_library_path = {
            self.DEFAULT_TAG_32: self.DEFAULT_LD_LIBRARY_PATH_32,
            self.DEFAULT_TAG_64: self.DEFAULT_LD_LIBRARY_PATH_64,
        }
        if hasattr(self, keys.ConfigKeys.IKEY_BINARY_TEST_LD_LIBRARY_PATH):
            self.binary_test_ld_library_path = map(
                str, self.binary_test_ld_library_path)
            for token in self.binary_test_ld_library_path:
                tag = ''
                path = token
                if self.TAG_DELIMITER in token:
                    tag, path = token.split(self.TAG_DELIMITER)
                if tag in self.ld_library_path:
                    self.ld_library_path[tag] = '{}:{}'.format(
                        path, self.ld_library_path[tag])
                else:
                    self.ld_library_path[tag] = path

        self.profiling_library_path = {
            self.DEFAULT_TAG_32: self.DEFAULT_PROFILING_LIBRARY_PATH_32,
            self.DEFAULT_TAG_64: self.DEFAULT_PROFILING_LIBRARY_PATH_64,
        }
        if hasattr(self,
                   keys.ConfigKeys.IKEY_BINARY_TEST_PROFILING_LIBRARY_PATH):
            self.binary_test_profiling_library_path = map(
                str, self.binary_test_profiling_library_path)
            for token in self.binary_test_profiling_library_path:
                tag = ''
                path = token
                if self.TAG_DELIMITER in token:
                    tag, path = token.split(self.TAG_DELIMITER)
                self.profiling_library_path[tag] = path

        if not hasattr(self, "_dut"):
            self._dut = self.registerController(android_device)[0]

        self._dut.shell.InvokeTerminal("one", int(self.abi_bitness))
        self.shell = self._dut.shell.one

        if self.coverage.enabled:
            self.coverage.LoadArtifacts()
            self.coverage.InitializeDeviceCoverage(self._dut)

        # TODO: only set permissive mode for userdebug and eng build.
        self.shell.Execute("setenforce 0")  # SELinux permissive mode

        if not precondition_utils.CanRunHidlHalTest(self, self._dut,
                                                    self._dut.shell.one):
            self._skip_all_testcases = True

        self.testcases = []
        self.tags = set()
        self.CreateTestCases()
        cmd = list(
            set('chmod 755 %s' % test_case.path
                for test_case in self.testcases))
        cmd_results = self.shell.Execute(cmd)
        if any(cmd_results[const.EXIT_CODE]):
            logging.error('Failed to set permission to some of the binaries:\n'
                          '%s\n%s', cmd, cmd_results)

        self.include_filter = self.ExpandListItemTags(self.include_filter)
        self.exclude_filter = self.ExpandListItemTags(self.exclude_filter)

        stop_requested = False

        if getattr(self, keys.ConfigKeys.IKEY_BINARY_TEST_DISABLE_FRAMEWORK,
                   False):
            # Stop Android runtime to reduce interference.
            self._dut.stop()
            stop_requested = True

        if getattr(self, keys.ConfigKeys.IKEY_BINARY_TEST_STOP_NATIVE_SERVERS,
                   False):
            # Stops all (properly configured) native servers.
            results = self._dut.setProp(self.SYSPROP_VTS_NATIVE_SERVER, "1")
            stop_requested = True

        if stop_requested:
            native_server_process_names = getattr(
                self, keys.ConfigKeys.IKEY_NATIVE_SERVER_PROCESS_NAME, [])
            if native_server_process_names:
                for native_server_process_name in native_server_process_names:
                    while True:
                        cmd_result = self.shell.Execute("ps -A")
                        if cmd_result[const.EXIT_CODE][0] != 0:
                            logging.error("ps command failed (exit code: %s",
                                          cmd_result[const.EXIT_CODE][0])
                            break
                        if (native_server_process_name not in
                            cmd_result[const.STDOUT][0]):
                            logging.info("Process %s not running",
                                         native_server_process_name)
                            break
                        logging.info("Checking process %s",
                                     native_server_process_name)
                        time.sleep(1)

    def CreateTestCases(self):
        '''Push files to device and create test case objects.'''
        source_list = list(map(self.ParseTestSource, self.binary_test_source))
        source_list = filter(bool, source_list)
        logging.info('Parsed test sources: %s', source_list)

        # Push source files first
        for src, dst, tag in source_list:
            if src:
                if os.path.isdir(src):
                    src = os.path.join(src, '.')
                logging.info('Pushing from %s to %s.', src, dst)
                self._dut.adb.push('{src} {dst}'.format(src=src, dst=dst))
                self.shell.Execute('ls %s' % dst)

        # Then create test cases
        for src, dst, tag in source_list:
            if tag is not None:
                # tag not being None means to create a test case
                self.tags.add(tag)
                logging.info('Creating test case from %s with tag %s', dst,
                             tag)
                testcase = self.CreateTestCase(dst, tag)
                if not testcase:
                    continue

                if type(testcase) is list:
                    self.testcases.extend(testcase)
                else:
                    self.testcases.append(testcase)

    def PutTag(self, name, tag):
        '''Put tag on name and return the resulting string.

        Args:
            name: string, a test name
            tag: string

        Returns:
            String, the result string after putting tag on the name
        '''
        return '{}{}'.format(name, tag)

    def ExpandListItemTags(self, input_list):
        '''Expand list items with tags.

        Since binary test allows a tag to be added in front of the binary
        path, test names are generated with tags attached. This function is
        used to expand the filters correspondingly. If a filter contains
        a tag, only test name with that tag will be included in output.
        Otherwise, all known tags will be paired to the test name in output
        list.

        Args:
            input_list: list of string, the list to expand

        Returns:
            A list of string
        '''
        result = []
        for item in input_list:
            if self.TAG_DELIMITER in item:
                tag, name = item.split(self.TAG_DELIMITER)
                result.append(self.PutTag(name, tag))
            for tag in self.tags:
                result.append(self.PutTag(item, tag))
        return result

    def tearDownClass(self):
        '''Perform clean-up tasks'''
        if getattr(self, keys.ConfigKeys.IKEY_BINARY_TEST_STOP_NATIVE_SERVERS,
                   False):
            # Restarts all (properly configured) native servers.
            results = self._dut.setProp(self.SYSPROP_VTS_NATIVE_SERVER, "0")

        # Restart Android runtime.
        if getattr(self, keys.ConfigKeys.IKEY_BINARY_TEST_DISABLE_FRAMEWORK,
                   False):
            self._dut.start()

        # Retrieve coverage if applicable
        if self.coverage.enabled:
            self.coverage.SetCoverageData(dut=self._dut, isGlobal=True)

        # Clean up the pushed binaries
        logging.info('Start class cleaning up jobs.')
        # Delete pushed files

        sources = [self.ParseTestSource(src)
                   for src in self.binary_test_source]
        sources = set(filter(bool, sources))
        paths = [dst for src, dst, tag in sources if src and dst]
        cmd = ['rm -rf %s' % dst for dst in paths]
        cmd_results = self.shell.Execute(cmd)
        if not cmd_results or any(cmd_results[const.EXIT_CODE]):
            logging.warning('Failed to clean up test class: %s', cmd_results)

        # Delete empty directories in working directories
        dir_set = set(path_utils.TargetDirName(dst) for dst in paths)
        dir_set.add(self.ParseTestSource('')[1])
        dirs = list(dir_set)
        dirs.sort(lambda x, y: cmp(len(y), len(x)))
        cmd = ['rmdir %s' % d for d in dirs]
        cmd_results = self.shell.Execute(cmd)
        if not cmd_results or any(cmd_results[const.EXIT_CODE]):
            logging.warning('Failed to remove: %s', cmd_results)

        if self.profiling.enabled:
            self.profiling.ProcessAndUploadTraceData()

        logging.info('Finished class cleaning up jobs.')

    def ParseTestSource(self, source):
        '''Convert host side binary path to device side path.

        Args:
            source: string, binary test source string

        Returns:
            A tuple of (string, string, string), representing (host side
            absolute path, device side absolute path, tag). Returned tag
            will be None if the test source is for pushing file to working
            directory only. If source file is specified for adb push but does not
            exist on host, None will be returned.
        '''
        tag = ''
        path = source
        if self.TAG_DELIMITER in source:
            tag, path = source.split(self.TAG_DELIMITER)

        src = path
        dst = None
        if self.PUSH_DELIMITER in path:
            src, dst = path.split(self.PUSH_DELIMITER)

        if src:
            src = os.path.join(self.data_file_path, src)
            if not os.path.exists(src):
                logging.warning('binary source file is specified '
                                'but does not exist on host: %s', src)
                return None

        push_only = dst is not None and dst == ''

        if not dst:
            if tag in self.working_directory:
                dst = path_utils.JoinTargetPath(self.working_directory[tag],
                                                os.path.basename(src))
            else:
                dst = path_utils.JoinTargetPath(
                    self.DEVICE_TMP_DIR, 'binary_test_temp_%s' %
                    self.__class__.__name__, tag, os.path.basename(src))

        if push_only:
            tag = None

        return str(src), str(dst), tag

    def CreateTestCase(self, path, tag=''):
        '''Create a list of TestCase objects from a binary path.

        Args:
            path: string, absolute path of a binary on device
            tag: string, a tag that will be appended to the end of test name

        Returns:
            A list of BinaryTestCase objects
        '''
        working_directory = self.working_directory[
            tag] if tag in self.working_directory else None
        envp = self.envp[tag] if tag in self.envp else ''
        args = self.args[tag] if tag in self.args else ''
        ld_library_path = self.ld_library_path[
            tag] if tag in self.ld_library_path else None
        profiling_library_path = self.profiling_library_path[
            tag] if tag in self.profiling_library_path else None

        return binary_test_case.BinaryTestCase(
            '',
            path_utils.TargetBaseName(path),
            path,
            tag,
            self.PutTag,
            working_directory,
            ld_library_path,
            profiling_library_path,
            envp=envp,
            args=args)

    def VerifyTestResult(self, test_case, command_results):
        '''Parse command result.

        Args:
            command_results: dict of lists, shell command result
        '''
        asserts.assertTrue(command_results, 'Empty command response.')
        asserts.assertFalse(
            any(command_results[const.EXIT_CODE]),
            'Test {} failed with the following results: {}'.format(
                test_case, command_results))

    def RunTestCase(self, test_case):
        '''Runs a test_case.

        Args:
            test_case: BinaryTestCase object
        '''
        if self.profiling.enabled:
            self.profiling.EnableVTSProfiling(self.shell,
                                              test_case.profiling_library_path)

        cmd = test_case.GetRunCommand()
        logging.info("Executing binary test command: %s", cmd)
        command_results = self.shell.Execute(cmd)

        self.VerifyTestResult(test_case, command_results)

        if self.profiling.enabled:
            self.profiling.ProcessTraceDataForTestCase(self._dut)
            self.profiling.DisableVTSProfiling(self.shell)

    def generateAllTests(self):
        '''Runs all binary tests.'''
        self.runGeneratedTests(
            test_func=self.RunTestCase, settings=self.testcases, name_func=str)


if __name__ == "__main__":
    test_runner.main()
