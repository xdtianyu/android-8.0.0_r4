# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import grp
import logging
import os
import pwd
import re
import shutil
import signal
import stat
import subprocess

import crash_test
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error


class UserCrashTest(crash_test.CrashTest):
    """
    Base class for tests that verify crash reporting for user processes. Shared
    functionality includes installing a crasher executable, generating Breakpad
    symbols, running the crasher process, and verifying collection and sending.
    """


    def setup(self):
        crasher_dir = os.path.join(os.path.dirname(__file__), 'crasher')
        shutil.copytree(crasher_dir, self.srcdir)

        os.chdir(self.srcdir)
        utils.make()


    def _prepare_crasher(self):
        """Extract the crasher and set its permissions.

        crasher is only gzipped to subvert Portage stripping.
        """
        self._crasher_path = os.path.join(self.srcdir, 'crasher_nobreakpad')
        utils.system('cd %s; tar xzf crasher.tgz-unmasked' %
                     self.srcdir)
        # Make sure all users (specifically chronos) have access to
        # this directory and its decendents in order to run crasher
        # executable as different users.
        utils.system('chmod -R a+rx ' + self.bindir)


    def _populate_symbols(self):
        """Set up Breakpad's symbol structure.

        Breakpad's minidump processor expects symbols to be in a directory
        hierarchy:
          <symbol-root>/<module_name>/<file_id>/<module_name>.sym
        """
        # Dump the symbols from the crasher
        self._symbol_dir = os.path.join(self.srcdir, 'symbols')
        utils.system('rm -rf %s' % self._symbol_dir)
        os.mkdir(self._symbol_dir)

        basename = os.path.basename(self._crasher_path)
        utils.system('/usr/bin/dump_syms %s > %s.sym' %
                     (self._crasher_path,
                      basename))
        sym_name = '%s.sym' % basename
        symbols = utils.read_file(sym_name)
        # First line should be like:
        # MODULE Linux x86 7BC3323FBDBA2002601FA5BA3186D6540 crasher_XXX
        #  or
        # MODULE Linux arm C2FE4895B203D87DD4D9227D5209F7890 crasher_XXX
        first_line = symbols.split('\n')[0]
        tokens = first_line.split()
        if tokens[0] != 'MODULE' or tokens[1] != 'Linux':
          raise error.TestError('Unexpected symbols format: %s',
                                first_line)
        file_id = tokens[3]
        target_dir = os.path.join(self._symbol_dir, basename, file_id)
        os.makedirs(target_dir)
        os.rename(sym_name, os.path.join(target_dir, sym_name))


    def _is_frame_in_stack(self, frame_index, module_name,
                           function_name, file_name,
                           line_number, stack):
        """Search for frame entries in the given stack dump text.

        A frame entry looks like (alone on a line):
          16  crasher_nobreakpad!main [crasher.cc : 21 + 0xb]

        Args:
          frame_index: number of the stack frame (0 is innermost frame)
          module_name: name of the module (executable or dso)
          function_name: name of the function in the stack
          file_name: name of the file containing the function
          line_number: line number
          stack: text string of stack frame entries on separate lines.

        Returns:
          Boolean indicating if an exact match is present.

        Note:
          We do not care about the full function signature - ie, is it
          foo or foo(ClassA *).  These are present in function names
          pulled by dump_syms for Stabs but not for DWARF.
        """
        regexp = (r'\n\s*%d\s+%s!%s.*\[\s*%s\s*:\s*%d\s.*\]' %
                  (frame_index, module_name,
                   function_name, file_name,
                   line_number))
        logging.info('Searching for regexp %s', regexp)
        return re.search(regexp, stack) is not None


    def _verify_stack(self, stack, basename, from_crash_reporter):
        logging.debug('Crash stackwalk was: %s', stack)

        # Should identify cause as SIGSEGV at address 0x16
        match = re.search(r'Crash reason:\s+(.*)', stack)
        expected_address = '0x16'
        if from_crash_reporter:
            # We cannot yet determine the crash address when coming
            # through core files via crash_reporter.
            expected_address = '0x0'
        if not match or match.group(1) != 'SIGSEGV':
            raise error.TestFail('Did not identify SIGSEGV cause')
        match = re.search(r'Crash address:\s+(.*)', stack)
        if not match or match.group(1) != expected_address:
            raise error.TestFail('Did not identify crash address %s' %
                                 expected_address)

        # Should identify crash at *(char*)0x16 assignment line
        if not self._is_frame_in_stack(0, basename,
                                       'recbomb', 'bomb.cc', 9, stack):
            raise error.TestFail('Did not show crash line on stack')

        # Should identify recursion line which is on the stack
        # for 15 levels
        if not self._is_frame_in_stack(15, basename, 'recbomb',
                                       'bomb.cc', 12, stack):
            raise error.TestFail('Did not show recursion line on stack')

        # Should identify main line
        if not self._is_frame_in_stack(16, basename, 'main',
                                       'crasher.cc', 20, stack):
            raise error.TestFail('Did not show main on stack')


    def _run_crasher_process(self, username, cause_crash=True, consent=True,
                             crasher_path=None):
        """Runs the crasher process.

        Will wait up to 5 seconds for crash_reporter to report the crash.
        crash_reporter_caught will be marked as true when the "Received crash
        notification message..." appears. While associated logs are likely to be
        available at this point, the function does not guarantee this.

        Returns:
          A dictionary with keys:
            returncode: return code of the crasher
            crashed: did the crasher return segv error code
            crash_reporter_caught: did crash_reporter catch a segv
            output: stderr/stdout output of the crasher process
        """
        if crasher_path is None:
            crasher_path = self._crasher_path
        else:
            utils.system('cp -a "%s" "%s"' % (self._crasher_path, crasher_path))

        self.enable_crash_filtering(os.path.basename(crasher_path))

        if username != 'root':
            crasher_command = ['su', username, '-c']
            expected_result = 128 + signal.SIGSEGV
        else:
            crasher_command = []
            expected_result = -signal.SIGSEGV

        crasher_command.append(crasher_path)
        basename = os.path.basename(crasher_path)
        if not cause_crash:
            crasher_command.append('--nocrash')
        self._set_consent(consent)
        crasher = subprocess.Popen(crasher_command,
                                   stdout=subprocess.PIPE,
                                   stderr=subprocess.PIPE)
        output = crasher.communicate()[1]
        logging.debug('Output from %s: %s', crasher_command, output)

        # Grab the pid from the process output.  We can't just use
        # crasher.pid unfortunately because that may be the PID of su.
        match = re.search(r'pid=(\d+)', output)
        if not match:
            raise error.TestFail('Could not find pid output from crasher: %s' %
                                 output)
        pid = int(match.group(1))

        expected_uid = pwd.getpwnam(username)[2]
        if consent:
            handled_string = 'handling'
        else:
            handled_string = 'ignoring - no consent'
        expected_message = (
            'Received crash notification for %s[%d] sig 11, user %d (%s)' %
            (basename, pid, expected_uid, handled_string))

        # Wait until no crash_reporter is running.
        utils.poll_for_condition(
            lambda: utils.system('pgrep -f crash_reporter.*:%s' % basename,
                                 ignore_status=True) != 0,
            timeout=10,
            exception=error.TestError(
                'Timeout waiting for crash_reporter to finish: ' +
                self._log_reader.get_logs()))

        logging.debug('crash_reporter_caught message: %s', expected_message)
        is_caught = False
        try:
            utils.poll_for_condition(
                lambda: self._log_reader.can_find(expected_message),
                timeout=5)
            is_caught = True
        except utils.TimeoutError:
            pass

        result = {'crashed': crasher.returncode == expected_result,
                  'crash_reporter_caught': is_caught,
                  'output': output,
                  'returncode': crasher.returncode}
        logging.debug('Crasher process result: %s', result)
        return result


    def _check_crash_directory_permissions(self, crash_dir):
        stat_info = os.stat(crash_dir)
        user = pwd.getpwuid(stat_info.st_uid)[0]
        group = grp.getgrgid(stat_info.st_gid)[0]
        mode = stat.S_IMODE(stat_info.st_mode)

        if crash_dir == '/var/spool/crash':
            expected_user = 'root'
            expected_group = 'root'
            expected_mode = 01755
        else:
            expected_user = 'chronos'
            expected_group = 'chronos'
            expected_mode = 0755

        if user != expected_user or group != expected_group:
            raise error.TestFail(
                'Expected %s.%s ownership of %s (actual %s.%s)' %
                (expected_user, expected_group, crash_dir, user, group))
        if mode != expected_mode:
            raise error.TestFail(
                'Expected %s to have mode %o (actual %o)' %
                (crash_dir, expected_mode, mode))


    def _check_minidump_stackwalk(self, minidump_path, basename,
                                  from_crash_reporter):
        # Now stackwalk the minidump
        stack = utils.system_output('/usr/bin/minidump_stackwalk %s %s' %
                                    (minidump_path, self._symbol_dir))
        self._verify_stack(stack, basename, from_crash_reporter)


    def _check_generated_report_sending(self, meta_path, payload_path,
                                        username, exec_name, report_kind,
                                        expected_sig=None):
        # Now check that the sending works
        result = self._call_sender_one_crash(
            username=username,
            report=os.path.basename(payload_path))
        if (not result['send_attempt'] or not result['send_success'] or
            result['report_exists']):
            raise error.TestFail('Report not sent properly')
        if result['exec_name'] != exec_name:
            raise error.TestFail('Executable name incorrect')
        if result['report_kind'] != report_kind:
            raise error.TestFail('Expected a minidump report')
        if result['report_payload'] != payload_path:
            raise error.TestFail('Sent the wrong minidump payload')
        if result['meta_path'] != meta_path:
            raise error.TestFail('Used the wrong meta file')
        if expected_sig is None:
            if result['sig'] is not None:
                raise error.TestFail('Report should not have signature')
        else:
            if not 'sig' in result or result['sig'] != expected_sig:
                raise error.TestFail('Report signature mismatch: %s vs %s' %
                                     (result['sig'], expected_sig))

        # Check version matches.
        lsb_release = utils.read_file('/etc/lsb-release')
        version_match = re.search(r'CHROMEOS_RELEASE_VERSION=(.*)', lsb_release)
        if not ('Version: %s' % version_match.group(1)) in result['output']:
            raise error.TestFail('Did not find version %s in log output' %
                                 version_match.group(1))


    def _run_crasher_process_and_analyze(self, username,
                                         cause_crash=True, consent=True,
                                         crasher_path=None):
        self._log_reader.set_start_by_current()

        result = self._run_crasher_process(username, cause_crash=cause_crash,
                                           consent=consent,
                                           crasher_path=crasher_path)

        if not result['crashed'] or not result['crash_reporter_caught']:
            return result;

        crash_dir = self._get_crash_dir(username)

        if not consent:
            if os.path.exists(crash_dir):
                raise error.TestFail('Crash directory should not exist')
            return result

        crash_contents = os.listdir(crash_dir)
        basename = os.path.basename(crasher_path or self._crasher_path)

        breakpad_minidump = None
        crash_reporter_minidump = None
        crash_reporter_meta = None
        crash_reporter_log = None

        self._check_crash_directory_permissions(crash_dir)

        logging.debug('Contents in %s: %s', crash_dir, crash_contents)

        for filename in crash_contents:
            if filename.endswith('.core'):
                # Ignore core files.  We'll test them later.
                pass
            elif (filename.startswith(basename) and
                  filename.endswith('.dmp')):
                # This appears to be a minidump created by the crash reporter.
                if not crash_reporter_minidump is None:
                    raise error.TestFail('Crash reporter wrote multiple '
                                         'minidumps')
                crash_reporter_minidump = os.path.join(crash_dir, filename)
            elif (filename.startswith(basename) and
                  filename.endswith('.meta')):
                if not crash_reporter_meta is None:
                    raise error.TestFail('Crash reporter wrote multiple '
                                         'meta files')
                crash_reporter_meta = os.path.join(crash_dir, filename)
            elif (filename.startswith(basename) and
                  filename.endswith('.log')):
                if not crash_reporter_log is None:
                    raise error.TestFail('Crash reporter wrote multiple '
                                         'log files')
                crash_reporter_log = os.path.join(crash_dir, filename)
            else:
                # This appears to be a breakpad created minidump.
                if not breakpad_minidump is None:
                    raise error.TestFail('Breakpad wrote multimpe minidumps')
                breakpad_minidump = os.path.join(crash_dir, filename)

        if breakpad_minidump:
            raise error.TestFail('%s did generate breakpad minidump' % basename)

        if not crash_reporter_meta:
            raise error.TestFail('crash reporter did not generate meta')

        result['minidump'] = crash_reporter_minidump
        result['basename'] = basename
        result['meta'] = crash_reporter_meta
        result['log'] = crash_reporter_log
        return result


    def _check_crashed_and_caught(self, result):
        if not result['crashed']:
            raise error.TestFail('crasher did not do its job of crashing: %d' %
                                 result['returncode'])

        if not result['crash_reporter_caught']:
            logging.debug('Messages that should have included segv: %s',
                          self._log_reader.get_logs())
            raise error.TestFail('Did not find segv message')


    def _check_crashing_process(self, username, consent=True):
        result = self._run_crasher_process_and_analyze(username,
                                                       consent=consent)

        self._check_crashed_and_caught(result)

        if not consent:
            return

        if not result['minidump']:
            raise error.TestFail('crash reporter did not generate minidump')

        if not self._log_reader.can_find('Stored minidump to ' +
                                         result['minidump']):
            raise error.TestFail('crash reporter did not announce minidump')

        self._check_minidump_stackwalk(result['minidump'],
                                       result['basename'],
                                       from_crash_reporter=True)
        self._check_generated_report_sending(result['meta'],
                                             result['minidump'],
                                             username,
                                             result['basename'],
                                             'minidump')
