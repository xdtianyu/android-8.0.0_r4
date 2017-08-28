# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# repohooks/pre-upload.py currently does not run pylint. But for developers who
# want to check their code manually we disable several harmless pylint warnings
# which just distract from more serious remaining issues.
#
# The instance variable _android_cts is not defined in __init__().
# pylint: disable=attribute-defined-outside-init
#
# Many short variable names don't follow the naming convention.
# pylint: disable=invalid-name

import logging
import os
import shutil

from autotest_lib.client.common_lib import error
from autotest_lib.server import utils
from autotest_lib.server.cros import tradefed_test

# likely hang unit the TIMEOUT hits and no RETRY steps will happen.
_CTS_MAX_RETRY = {'dev': 5, 'beta': 5, 'stable': 5}
# Maximum default time allowed for each individual CTS module.
_CTS_TIMEOUT_SECONDS = 3600

# Public download locations for android cts bundles.
_DL_CTS = 'https://dl.google.com/dl/android/cts/'
_CTS_URI = {
    'arm': _DL_CTS + 'android-cts-7.1_r3-linux_x86-arm.zip',
    'x86': _DL_CTS + 'android-cts-7.1_r3-linux_x86-x86.zip',
    'media': _DL_CTS + 'android-cts-media-1.2.zip',
}

_SDK_TOOLS_DIR_N = 'gs://chromeos-arc-images/builds/git_nyc-mr1-arc-linux-static_sdk_tools/3544738'
_ADB_DIR_N = 'gs://chromeos-arc-images/builds/git_nyc-mr1-arc-linux-cheets_arm-user/3544738'


class cheets_CTS_N(tradefed_test.TradefedTest):
    """Sets up tradefed to run CTS tests."""
    version = 1

    def initialize(self, host=None):
        super(cheets_CTS_N, self).initialize(host=host, adb_dir=_ADB_DIR_N,
                                             sdk_tools_dir=_SDK_TOOLS_DIR_N)

    def setup(self, bundle=None, uri=None):
        """Download and install a zipfile bundle from Google Storage.

        @param bundle: bundle name, which needs to be key of the _CTS_URI
                       dictionary. Can be 'arm', 'x86' and undefined.
        @param uri: URI of CTS bundle. Required if |abi| is undefined.
        """
        if bundle in _CTS_URI:
            self._android_cts = self._install_bundle(_CTS_URI[bundle])
        else:
            self._android_cts = self._install_bundle(uri)

        self._cts_tradefed = os.path.join(self._android_cts, 'android-cts',
                                          'tools', 'cts-tradefed')
        logging.info('CTS-tradefed path: %s', self._cts_tradefed)

        # Load waivers and manual tests so TF doesn't re-run them.
        self.waivers_and_manual_tests = self._get_expected_failures(
            'expectations')
        # Load modules with no tests.
        self.notest_modules = self._get_expected_failures('notest_modules')

    def _clean_repository(self):
        """Ensures all old logs, results and plans are deleted.

        This function should be called at the start of each autotest iteration.
        """
        logging.info('Cleaning up repository.')
        repository = os.path.join(self._android_cts, 'android-cts')
        for directory in ['logs', 'subplans', 'results']:
            path = os.path.join(repository, directory)
            if os.path.exists(path):
                shutil.rmtree(path)
            self._safe_makedirs(path)

    def _install_plan(self, plan):
        logging.info('Install plan: %s', plan)
        plans_dir = os.path.join(self._android_cts, 'android-cts', 'repository',
                                 'plans')
        src_plan_file = os.path.join(self.bindir, 'plans', '%s.xml' % plan)
        shutil.copy(src_plan_file, plans_dir)

    def _tradefed_run_command(self,
                              module=None,
                              plan=None,
                              session_id=None,
                              test_class=None,
                              test_method=None):
        """Builds the CTS tradefed 'run' command line.

        There are five different usages:

        1. Test a module: assign the module name via |module|.
        2. Test a plan: assign the plan name via |plan|.
        3. Continue a session: assign the session ID via |session_id|.
        4. Run all test cases of a class: assign the class name via
           |test_class|.
        5. Run a specific test case: assign the class name and method name in
           |test_class| and |test_method|.

        @param module: the name of test module to be run.
        @param plan: name of the plan to be run.
        @param session_id: tradefed session id to continue.
        @param test_class: the name of the class of which all test cases will
                           be run.
        @param test_name: the name of the method of |test_class| to be run.
                          Must be used with |test_class|.
        @return: list of command tokens for the 'run' command.
        """
        if module is not None:
            # Run a particular module (used to be called package in M).
            cmd = ['run', 'commandAndExit', 'cts', '--module', module]
        elif plan is not None and session_id is not None:
            # In 7.1 R2 we can only retry session_id with the original plan.
            cmd = ['run', 'commandAndExit', 'cts', '--plan', plan,
                   '--retry', '%d' % session_id]
        elif plan is not None:
            # TODO(ihf): This needs testing to support media team.
            cmd = ['run', 'commandAndExit', 'cts', '--plan', plan]
        elif test_class is not None:
            # TODO(ihf): This needs testing to support media team.
            cmd = ['run', 'commandAndExit', 'cts', '-c', test_class]
            if test_method is not None:
                cmd += ['-m', test_method]
        else:
            logging.warning('Running all tests. This can take several days.')
            cmd = ['run', 'commandAndExit', 'cts']
        # We handle media download ourselves in the lab, as lazy as possible.
        cmd.append('--precondition-arg')
        cmd.append('skip-media-download')
        # If we are running outside of the lab we can collect more data.
        if not utils.is_in_container():
            logging.info('Running outside of lab, adding extra debug options.')
            cmd.append('--log-level-display=DEBUG')
            cmd.append('--screenshot-on-failure')
            # TODO(ihf): Add log collection once b/28333587 fixed.
            #cmd.append('--collect-deqp-logs')
        # TODO(ihf): Add tradefed_test.adb_keepalive() and remove
        # --disable-reboot. This might be more efficient.
        # At early stage, cts-tradefed tries to reboot the device by
        # "adb reboot" command. In a real Android device case, when the
        # rebooting is completed, adb connection is re-established
        # automatically, and cts-tradefed expects that behavior.
        # However, in ARC, it doesn't work, so the whole test process
        # is just stuck. Here, disable the feature.
        cmd.append('--disable-reboot')
        # Create a logcat file for each individual failure.
        cmd.append('--logcat-on-failure')
        return cmd

    def _run_cts_tradefed(self,
                          commands,
                          datetime_id=None,
                          collect_results=True):
        """Runs tradefed, collects logs and returns the result counts.

        Assumes that only last entry of |commands| actually runs tests and has
        interesting output (results, logs) for collection. Ignores all other
        commands for this purpose.

        @param commands: List of lists of command tokens.
        @param datetime_id: For 'continue' datetime of previous run is known.
                            Knowing it makes collecting logs more robust.
        @param collect: Skip result collection if False.
        @return: tuple of (tests, pass, fail, notexecuted) counts.
        """
        for command in commands:
            # Assume only last command actually runs tests and has interesting
            # output (results, logs) for collection.
            logging.info('RUN: ./cts-tradefed %s', ' '.join(command))
            output = self._run(
                self._cts_tradefed,
                args=tuple(command),
                timeout=self._timeout,
                verbose=True,
                ignore_status=False,
                # Make sure to tee tradefed stdout/stderr to autotest logs
                # continuously during the test run.
                stdout_tee=utils.TEE_TO_LOGS,
                stderr_tee=utils.TEE_TO_LOGS)
            logging.info('END: ./cts-tradefed %s\n', ' '.join(command))
        if not collect_results:
            return None
        result_destination = os.path.join(self.resultsdir, 'android-cts')
        # Gather the global log first. Datetime parsing below can abort the test
        # if tradefed startup had failed. Even then the global log is useful.
        self._collect_tradefed_global_log(output, result_destination)
        if not datetime_id:
            # Parse stdout to obtain datetime of the session. This is needed to
            # locate result xml files and logs.
            datetime_id = self._parse_tradefed_datetime_N(output, self.summary)
        # Collect tradefed logs for autotest.
        tradefed = os.path.join(self._android_cts, 'android-cts')
        self._collect_logs(tradefed, datetime_id, result_destination)
        return self._parse_result_v2(output,
                                     waivers=self.waivers_and_manual_tests)

    def _tradefed_retry(self, test_name, session_id):
        """Retries failing tests in session.

        It is assumed that there are no notexecuted tests of session_id,
        otherwise some tests will be missed and never run.

        @param test_name: the name of test to be retried.
        @param session_id: tradefed session id to retry.
        @param result_type: [N only] either 'failed' or 'not_executed'
        @return: tuple of (new session_id, tests, pass, fail, notexecuted).
        """
        # Creating new test plan for retry.
        derivedplan = 'retry.%s.%s' % (test_name, session_id)
        logging.info('Retrying failures using derived plan %s.', derivedplan)
        # The list commands are not required. It allows the reader to inspect
        # the tradefed state when examining the autotest logs.
        commands = [
                ['add', 'subplan', '--name', derivedplan,
                 '--session', '%d' % session_id,
                 '--result-type', 'failed', '--result-type', 'not_executed'],
                ['list', 'subplans'],
                ['list', 'results'],
                self._tradefed_run_command(plan=derivedplan,
                                           session_id=session_id)]
        # TODO(ihf): Consider if diffing/parsing output of "list results" for
        # new session_id might be more reliable. For now just assume simple
        # increment. This works if only one tradefed instance is active and
        # only a single run command is executing at any moment.
        return session_id + 1, self._run_cts_tradefed(commands)

    def _get_release_channel(self):
        """Returns the DUT channel of the image ('dev', 'beta', 'stable')."""
        # TODO(ihf): check CHROMEOS_RELEASE_DESCRIPTION and return channel.
        return 'dev'

    def _get_channel_retry(self):
        """Returns the maximum number of retries for DUT image channel."""
        channel = self._get_release_channel()
        if channel in _CTS_MAX_RETRY:
            return _CTS_MAX_RETRY[channel]
        retry = _CTS_MAX_RETRY['dev']
        logging.warning('Could not establish channel. Using retry=%d.', retry)
        return retry

    def _consistent(self, tests, passed, failed, notexecuted):
        """Verifies that the given counts are plausible.

        Used for finding bad logfile parsing using accounting identities.

        TODO(ihf): change to tests != passed + failed + notexecuted
        only once b/35530394 fixed."""
        return ((tests == passed + failed) or
                (tests == passed + failed + notexecuted))

    def run_once(self,
                 target_module=None,
                 target_plan=None,
                 target_class=None,
                 target_method=None,
                 needs_push_media=False,
                 max_retry=None,
                 timeout=_CTS_TIMEOUT_SECONDS):
        """Runs the specified CTS once, but with several retries.

        There are four usages:
        1. Test the whole module named |target_module|.
        2. Test with a plan named |target_plan|.
        3. Run all the test cases of class named |target_class|.
        4. Run a specific test method named |target_method| of class
           |target_class|.

        @param target_module: the name of test module to run.
        @param target_plan: the name of the test plan to run.
        @param target_class: the name of the class to be tested.
        @param target_method: the name of the method to be tested.
        @param needs_push_media: need to push test media streams.
        @param max_retry: number of retry steps before reporting results.
        @param timeout: time after which tradefed can be interrupted.
        """
        # On dev and beta channels timeouts are sharp, lenient on stable.
        self._timeout = timeout
        if self._get_release_channel == 'stable':
            self._timeout += 3600
        # Retries depend on channel.
        self._max_retry = max_retry
        if not self._max_retry:
            self._max_retry = self._get_channel_retry()
        logging.info('Maximum number of retry steps %d.', self._max_retry)
        session_id = 0

        self.result_history = {}
        steps = -1  # For historic reasons the first iteration is not counted.
        total_tests = 0
        total_passed = 0
        self.summary = ''
        if target_module is not None:
            test_name = 'module.%s' % target_module
            test_command = self._tradefed_run_command(
                module=target_module, session_id=session_id)
        elif target_plan is not None:
            test_name = 'plan.%s' % target_plan
            test_command = self._tradefed_run_command(
                plan=target_plan, session_id=session_id)
        elif target_class is not None:
            test_name = 'testcase.%s' % target_class
            if target_method is not None:
                test_name += '.' + target_method
            test_command = self._tradefed_run_command(
                test_class=target_class,
                test_method=target_method,
                session_id=session_id)
        else:
            test_command = self._tradefed_run_command()
            test_name = 'all_CTS'

        # Unconditionally run CTS module until we see some tests executed.
        while total_tests == 0 and steps < self._max_retry:
            with self._login_chrome():
                self._ready_arc()

                # Only push media for tests that need it. b/29371037
                if needs_push_media:
                    self._push_media(_CTS_URI)
                    # copy_media.sh is not lazy, but we try to be.
                    needs_push_media = False

                # Start each valid iteration with a clean repository. This
                # allows us to track session_id blindly.
                self._clean_repository()
                if target_plan is not None:
                    self._install_plan(target_plan)
                logging.info('Running %s:', test_name)

                # The list command is not required. It allows the reader to
                # inspect the tradefed state when examining the autotest logs.
                commands = [['list', 'results'], test_command]
                counts = self._run_cts_tradefed(commands)
                tests, passed, failed, notexecuted, waived = counts
                self.result_history[steps] = counts
                msg = 'run(t=%d, p=%d, f=%d, ne=%d, w=%d)' % counts
                logging.info('RESULT: %s', msg)
                self.summary += msg
                if tests == 0 and target_module in self.notest_modules:
                    logging.info('Package has no tests as expected.')
                    return
                if tests > 0 and target_module in self.notest_modules:
                    # We expected no tests, but the new bundle drop must have
                    # added some for us. Alert us to the situation.
                    raise error.TestFail('Failed: Remove module %s from '
                                         'notest_modules directory!' %
                                         target_module)
                if tests == 0 and target_module not in self.notest_modules:
                    logging.error('Did not find any tests in module. Hoping '
                                  'this is transient. Retry after reboot.')
                if not self._consistent(tests, passed, failed, notexecuted):
                    # Try to figure out what happened. Example: b/35605415.
                    self._run_cts_tradefed([['list', 'results']],
                                           collect_results=False)
                    logging.warning('Test count inconsistent. %s',
                                    self.summary)
                # Keep track of global count, we can't trust continue/retry.
                if total_tests == 0:
                    total_tests = tests
                total_passed += passed
                steps += 1
            # The DUT has rebooted at this point and is in a clean state.
        if total_tests == 0:
            raise error.TestFail('Error: Could not find any tests in module.')

        retry_inconsistency_error = None
        # If the results were not completed or were failing then continue or
        # retry them iteratively MAX_RETRY times.
        while steps < self._max_retry and failed > 0:
            # TODO(ihf): Use result_history to heuristically stop retries early.
            if failed > waived:
                with self._login_chrome():
                    steps += 1
                    self._ready_arc()
                    logging.info('Retrying failures of %s with session_id %d:',
                                 test_name, session_id)
                    expected_tests = failed + notexecuted
                    session_id, counts = self._tradefed_retry(test_name,
                                                              session_id)
                    tests, passed, failed, notexecuted, waived = counts
                    self.result_history[steps] = counts
                    # Consistency check, did we really run as many as we
                    # thought initially?
                    if expected_tests != tests:
                        msg = ('Retry inconsistency - '
                           'initially saw %d failed+notexecuted, ran %d tests. '
                           'passed=%d, failed=%d, notexecuted=%d, waived=%d.' %
                           (expected_tests, tests, passed, failed, notexecuted,
                            waived))
                        logging.warning(msg)
                        if expected_tests > tests:
                            # See b/36523200#comment8. Due to the existence of
                            # the multiple tests having the same ID, more cases
                            # may be run than previous fail count. As a
                            # workaround, making it an error only when the tests
                            # run were less than expected.
                            # TODO(kinaba): Find a way to handle this dup.
                            retry_inconsistency_error = msg
                    if not self._consistent(tests, passed, failed, notexecuted):
                        logging.warning('Tradefed inconsistency - retrying.')
                        session_id, counts = self._tradefed_retry(test_name,
                                                                  session_id)
                        tests, passed, failed, notexecuted, waived = counts
                        self.result_history[steps] = counts
                    msg = 'retry(t=%d, p=%d, f=%d, ne=%d, w=%d)' % counts
                    logging.info('RESULT: %s', msg)
                    self.summary += ' ' + msg
                    if not self._consistent(tests, passed, failed, notexecuted):
                        logging.warning('Test count inconsistent. %s',
                                        self.summary)
                    total_passed += passed
                # The DUT has rebooted at this point and is in a clean state.

        # Final classification of test results.
        if total_passed == 0 or failed > waived:
            raise error.TestFail(
                'Failed: after %d retries giving up. '
                'passed=%d, failed=%d, notexecuted=%d, waived=%d. %s' %
                (steps, total_passed, failed, notexecuted, waived,
                 self.summary))
        if not self._consistent(total_tests, total_passed, failed, notexecuted):
            raise error.TestFail('Error: Test count inconsistent. %s' %
                                 self.summary)
        if retry_inconsistency_error:
            raise error.TestFail('Error: %s %s' % (retry_inconsistency_error,
                                                   self.summary))
        if steps > 0:
            # TODO(ihf): Make this error.TestPass('...') once available.
            raise error.TestWarn(
                'Passed: after %d retries passing %d tests, waived=%d. %s' %
                (steps, total_passed, waived, self.summary))
