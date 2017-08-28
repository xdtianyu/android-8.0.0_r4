#!/usr/bin/python
#
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tool to validate code in prod branch before pushing to lab.

The script runs push_to_prod suite to verify code in prod branch is ready to be
pushed. Link to design document:
https://docs.google.com/a/google.com/document/d/1JMz0xS3fZRSHMpFkkKAL_rxsdbNZomhHbC3B8L71uuI/edit

To verify if prod branch can be pushed to lab, run following command in
chromeos-autotest.cbf server:
/usr/local/autotest/site_utils/test_push.py -e someone@company.com

The script uses latest gandof stable build as test build by default.

"""

import argparse
import ast
from contextlib import contextmanager
import getpass
import multiprocessing
import os
import re
import subprocess
import sys
import time
import traceback
import urllib2

import common
try:
    from autotest_lib.frontend import setup_django_environment
    from autotest_lib.frontend.afe import models
    from autotest_lib.frontend.afe import rpc_utils
except ImportError:
    # Unittest may not have Django database configured and will fail to import.
    pass
from autotest_lib.client.common_lib import global_config
from autotest_lib.client.common_lib import priorities
from autotest_lib.client.common_lib.cros import retry
from autotest_lib.server import site_utils
from autotest_lib.server import utils
from autotest_lib.server.cros import provision
from autotest_lib.server.cros.dynamic_suite import frontend_wrappers
from autotest_lib.server.hosts import afe_store
from autotest_lib.site_utils import gmail_lib
from autotest_lib.site_utils.suite_scheduler import constants

AUTOTEST_DIR=common.autotest_dir
CONFIG = global_config.global_config

AFE = frontend_wrappers.RetryingAFE(timeout_min=0.5, delay_sec=2)
TKO = frontend_wrappers.RetryingTKO(timeout_min=0.1, delay_sec=10)

MAIL_FROM = 'chromeos-test@google.com'
BUILD_REGEX = 'R[\d]+-[\d]+\.[\d]+\.[\d]+'
RUN_SUITE_COMMAND = 'run_suite.py'
PUSH_TO_PROD_SUITE = 'push_to_prod'
DUMMY_SUITE = 'dummy'
AU_SUITE = 'paygen_au_beta'
TESTBED_SUITE = 'testbed_push'
# TODO(shuqianz): Dynamically get android build after crbug.com/646068 fixed
DEFAULT_TIMEOUT_MIN_FOR_SUITE_JOB = 30
IMAGE_BUCKET = CONFIG.get_config_value('CROS', 'image_storage_server')
DEFAULT_EMAIL = CONFIG.get_config_value(
        'SCHEDULER', 'notify_email', type=str, default='')
DEFAULT_NUM_DUTS = "{'gandof': 4, 'quawks': 2, 'testbed': 1}"

SUITE_JOB_START_INFO_REGEX = ('^.*Created suite job:.*'
                              'tab_id=view_job&object_id=(\d+)$')

# Dictionary of test results keyed by test name regular expression.
EXPECTED_TEST_RESULTS = {'^SERVER_JOB$':                 'GOOD',
                         # This is related to dummy_Fail/control.dependency.
                         'dummy_Fail.dependency$':       'TEST_NA',
                         'login_LoginSuccess.*':         'GOOD',
                         'provision_AutoUpdate.double':  'GOOD',
                         'dummy_Pass.*':                 'GOOD',
                         'dummy_Fail.Fail$':             'FAIL',
                         'dummy_Fail.RetryFail$':        'FAIL',
                         'dummy_Fail.RetrySuccess':      'GOOD',
                         'dummy_Fail.Error$':            'ERROR',
                         'dummy_Fail.Warn$':             'WARN',
                         'dummy_Fail.NAError$':          'TEST_NA',
                         'dummy_Fail.Crash$':            'GOOD',
                         }

EXPECTED_TEST_RESULTS_DUMMY = {'^SERVER_JOB$':       'GOOD',
                               'dummy_Pass.*':       'GOOD',
                               'dummy_Fail.Fail':    'FAIL',
                               'dummy_Fail.Warn':    'WARN',
                               'dummy_Fail.Crash':   'GOOD',
                               'dummy_Fail.Error':   'ERROR',
                               'dummy_Fail.NAError': 'TEST_NA',}

EXPECTED_TEST_RESULTS_AU = {'SERVER_JOB$':                        'GOOD',
         'autoupdate_EndToEndTest.paygen_au_beta_delta.*': 'GOOD',
         'autoupdate_EndToEndTest.paygen_au_beta_full.*':  'GOOD',
         }

EXPECTED_TEST_RESULTS_TESTBED = {'^SERVER_JOB$':      'GOOD',
                                 'testbed_DummyTest': 'GOOD',}

EXPECTED_TEST_RESULTS_POWERWASH = {'platform_Powerwash': 'GOOD',
                                   'SERVER_JOB':         'GOOD'}

URL_HOST = CONFIG.get_config_value('SERVER', 'hostname', type=str)
URL_PATTERN = CONFIG.get_config_value('CROS', 'log_url_pattern', type=str)

# Some test could be missing from the test results for various reasons. Add
# such test in this list and explain the reason.
IGNORE_MISSING_TESTS = [
    # For latest build, npo_test_delta does not exist.
    'autoupdate_EndToEndTest.npo_test_delta.*',
    # For trybot build, nmo_test_delta does not exist.
    'autoupdate_EndToEndTest.nmo_test_delta.*',
    # Older build does not have login_LoginSuccess test in push_to_prod suite.
    # TODO(dshi): Remove following lines after R41 is stable.
    'login_LoginSuccess']

# Save all run_suite command output.
manager = multiprocessing.Manager()
run_suite_output = manager.list()
all_suite_ids = manager.list()
# A dict maps the name of the updated repos and the path of them.
UPDATED_REPOS = {'autotest': AUTOTEST_DIR,
                 'chromite': '%s/site-packages/chromite/' % AUTOTEST_DIR}
PUSH_USER = 'chromeos-test-lab'

class TestPushException(Exception):
    """Exception to be raised when the test to push to prod failed."""
    pass


@retry.retry(TestPushException, timeout_min=5, delay_sec=30)
def check_dut_inventory(required_num_duts, pool):
    """Check DUT inventory for each board in the pool specified..

    @param required_num_duts: a dict specifying the number of DUT each platform
                              requires in order to finish push tests.
    @param pool: the pool used by test_push.
    @raise TestPushException: if number of DUTs are less than the requirement.
    """
    print 'Checking DUT inventory...'
    pool_label = constants.Labels.POOL_PREFIX + pool
    hosts = AFE.run('get_hosts', status='Ready', locked=False)
    hosts = [h for h in hosts if pool_label in h.get('labels', [])]
    platforms = [host['platform'] for host in hosts]
    current_inventory = {p : platforms.count(p) for p in platforms}
    error_msg = ''
    for platform, req_num in required_num_duts.items():
        curr_num = current_inventory.get(platform, 0)
        if curr_num < req_num:
            error_msg += ('\nRequire %d %s DUTs in pool: %s, only %d are Ready'
                          ' now' % (req_num, platform, pool, curr_num))
    if error_msg:
        raise TestPushException('Not enough DUTs to run push tests. %s' %
                                error_msg)


def powerwash_dut_to_test_repair(hostname, timeout):
    """Powerwash dut to test repair workflow.

    @param hostname: hostname of the dut.
    @param timeout: seconds of the powerwash test to hit timeout.
    @raise TestPushException: if DUT fail to run the test.
    """
    t = models.Test.objects.get(name='platform_Powerwash')
    c = utils.read_file(os.path.join(common.autotest_dir, t.path))
    job_id = rpc_utils.create_job_common(
             'powerwash', priority=priorities.Priority.SUPER,
             control_type='Server', control_file=c, hosts=[hostname])

    end = time.time() + timeout
    while not TKO.get_job_test_statuses_from_db(job_id):
        if time.time() >= end:
            AFE.run('abort_host_queue_entries', job=job_id)
            raise TestPushException(
                'Powerwash test on %s timeout after %ds, abort it.' %
                (hostname, timeout))
        time.sleep(10)
    verify_test_results(job_id, EXPECTED_TEST_RESULTS_POWERWASH)
    # Kick off verify, verify will fail and a repair should be triggered.
    AFE.reverify_hosts(hostnames=[hostname])


def reverify_all_push_duts():
    """Reverify all the push DUTs."""
    print 'Reverifying all DUTs.'
    hosts = [h.hostname for h in AFE.get_hosts()]
    AFE.reverify_hosts(hostnames=hosts)


def get_default_build(board='gandof', server='chromeos-autotest.hot'):
    """Get the default build to be used for test.

    @param board: Name of board to be tested, default is gandof.
    @return: Build to be tested, e.g., gandof-release/R36-5881.0.0
    """
    build = None
    cmd = ('%s/cli/atest stable_version list --board=%s -w %s' %
           (AUTOTEST_DIR, board, server))
    result = subprocess.check_output(cmd, shell=True).strip()
    build = re.search(BUILD_REGEX, result)
    if build:
        return '%s-release/%s' % (board, build.group(0))

    # If fail to get stable version from cautotest, use that defined in config
    build = CONFIG.get_config_value('CROS', 'stable_cros_version')
    return '%s-release/%s' % (board, build)

def parse_arguments():
    """Parse arguments for test_push tool.

    @return: Parsed arguments.

    """
    parser = argparse.ArgumentParser()
    parser.add_argument('-b', '--board', dest='board', default='gandof',
                        help='Default is gandof.')
    parser.add_argument('-sb', '--shard_board', dest='shard_board',
                        default='quawks',
                        help='Default is quawks.')
    parser.add_argument('-i', '--build', dest='build', default=None,
                        help='Default is the latest stale build of given '
                             'board. Must be a stable build, otherwise AU test '
                             'will fail. (ex: gandolf-release/R54-8743.25.0)')
    parser.add_argument('-si', '--shard_build', dest='shard_build', default=None,
                        help='Default is the latest stable build of given '
                             'board. Must be a stable build, otherwise AU test '
                             'will fail.')
    parser.add_argument('-w', '--web', default='chromeos-autotest.hot',
                        help='Specify web server to grab stable version from.')
    parser.add_argument('-ab', '--android_board', dest='android_board',
                        default='shamu-2', help='Android board to test.')
    parser.add_argument('-ai', '--android_build', dest='android_build',
                        help='Android build to test.')
    parser.add_argument('-p', '--pool', dest='pool', default='bvt')
    parser.add_argument('-u', '--num', dest='num', type=int, default=3,
                        help='Run on at most NUM machines.')
    parser.add_argument('-e', '--email', dest='email', default=DEFAULT_EMAIL,
                        help='Email address for the notification to be sent to '
                             'after the script finished running.')
    parser.add_argument('-t', '--timeout_min', dest='timeout_min', type=int,
                        default=DEFAULT_TIMEOUT_MIN_FOR_SUITE_JOB,
                        help='Time in mins to wait before abort the jobs we '
                             'are waiting on. Only for the asynchronous suites '
                             'triggered by create_and_return flag.')
    parser.add_argument('-ud', '--num_duts', dest='num_duts',
                        default=DEFAULT_NUM_DUTS,
                        help="String of dict that indicates the required number"
                             " of DUTs for each board. E.g {'gandof':4}")
    parser.add_argument('-c', '--continue_on_failure', action='store_true',
                        dest='continue_on_failure',
                        help='All tests continue to run when there is failure')

    arguments = parser.parse_args(sys.argv[1:])

    # Get latest stable build as default build.
    if not arguments.build:
        arguments.build = get_default_build(arguments.board, arguments.web)
    if not arguments.shard_build:
        arguments.shard_build = get_default_build(arguments.shard_board,
                                                  arguments.web)

    arguments.num_duts = ast.literal_eval(arguments.num_duts)

    return arguments


def do_run_suite(suite_name, arguments, use_shard=False,
                 create_and_return=False, testbed_test=False):
    """Call run_suite to run a suite job, and return the suite job id.

    The script waits the suite job to finish before returning the suite job id.
    Also it will echo the run_suite output to stdout.

    @param suite_name: Name of a suite, e.g., dummy.
    @param arguments: Arguments for run_suite command.
    @param use_shard: If true, suite is scheduled for shard board.
    @param create_and_return: If True, run_suite just creates the suite, print
                              the job id, then finish immediately.
    @param testbed_test: True to run testbed test. Default is False.

    @return: Suite job ID.

    """
    if use_shard and not testbed_test:
        board = arguments.shard_board
        build = arguments.shard_build
    elif testbed_test:
        board = arguments.android_board
        build = arguments.android_build
    else:
        board = arguments.board
        build = arguments.build

    # Remove cros-version label to force provision.
    hosts = AFE.get_hosts(label=constants.Labels.BOARD_PREFIX+board,
                          locked=False)
    for host in hosts:
        labels_to_remove = [
                l for l in host.labels
                if (l.startswith(provision.CROS_VERSION_PREFIX) or
                    l.startswith(provision.TESTBED_BUILD_VERSION_PREFIX))]
        if labels_to_remove:
            AFE.run('host_remove_labels', id=host.id, labels=labels_to_remove)

        # Test repair work flow on shards, powerwash test will timeout after 7m.
        if use_shard and not create_and_return:
            powerwash_dut_to_test_repair(host.hostname, timeout=420)

    current_dir = os.path.dirname(os.path.realpath(__file__))
    cmd = [os.path.join(current_dir, RUN_SUITE_COMMAND),
           '-s', suite_name,
           '-b', board,
           '-i', build,
           '-p', arguments.pool,
           '-u', str(arguments.num)]
    if create_and_return:
        cmd += ['-c']
    if testbed_test:
        cmd += ['--run_prod_code']

    suite_job_id = None

    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT)

    while True:
        line = proc.stdout.readline()

        # Break when run_suite process completed.
        if not line and proc.poll() != None:
            break
        print line.rstrip()
        run_suite_output.append(line.rstrip())

        if not suite_job_id:
            m = re.match(SUITE_JOB_START_INFO_REGEX, line)
            if m and m.group(1):
                suite_job_id = int(m.group(1))
                all_suite_ids.append(suite_job_id)

    if not suite_job_id:
        raise TestPushException('Failed to retrieve suite job ID.')

    # If create_and_return specified, wait for the suite to finish.
    if create_and_return:
        end = time.time() + arguments.timeout_min * 60
        while not AFE.get_jobs(id=suite_job_id, finished=True):
            if time.time() < end:
                time.sleep(10)
            else:
                AFE.run('abort_host_queue_entries', job=suite_job_id)
                raise TestPushException(
                        'Asynchronous suite triggered by create_and_return '
                        'flag has timed out after %d mins. Aborting it.' %
                        arguments.timeout_min)

    print 'Suite job %s is completed.' % suite_job_id
    return suite_job_id


def check_dut_image(build, suite_job_id):
    """Confirm all DUTs used for the suite are imaged to expected build.

    @param build: Expected build to be imaged.
    @param suite_job_id: job ID of the suite job.
    @raise TestPushException: If a DUT does not have expected build imaged.
    """
    print 'Checking image installed in DUTs...'
    job_ids = [job.id for job in
               models.Job.objects.filter(parent_job_id=suite_job_id)]
    hqes = [models.HostQueueEntry.objects.filter(job_id=job_id)[0]
            for job_id in job_ids]
    hostnames = set([hqe.host.hostname for hqe in hqes])
    for hostname in hostnames:
        host_info_store = afe_store.AfeStore(hostname, AFE)
        info = host_info_store.get()
        if info.build != build:
            raise TestPushException('DUT is not imaged properly. Host %s has '
                                    'build %s, while build %s is expected.' %
                                    (hostname, info.build, build))


def test_suite(suite_name, expected_results, arguments, use_shard=False,
               create_and_return=False, testbed_test=False):
    """Call run_suite to start a suite job and verify results.

    @param suite_name: Name of a suite, e.g., dummy
    @param expected_results: A dictionary of test name to test result.
    @param arguments: Arguments for run_suite command.
    @param use_shard: If true, suite is scheduled for shard board.
    @param create_and_return: If True, run_suite just creates the suite, print
                              the job id, then finish immediately.
    @param testbed_test: True to run testbed test. Default is False.
    """
    suite_job_id = do_run_suite(suite_name, arguments, use_shard,
                                create_and_return, testbed_test)

    # Confirm all DUTs used for the suite are imaged to expected build.
    # hqe.host_id for jobs running in shard is not synced back to master db,
    # therefore, skip verifying dut build for jobs running in shard.
    build_expected = (arguments.android_build if testbed_test
                      else arguments.build)
    if suite_name != AU_SUITE and not use_shard and not testbed_test:
        check_dut_image(build_expected, suite_job_id)

    # Verify test results are the expected results.
    verify_test_results(suite_job_id, expected_results)


def verify_test_results(job_id, expected_results):
    """Verify the test results with the expected results.

    @param job_id: id of the running jobs. For suite job, it is suite_job_id.
    @param expected_results: A dictionary of test name to test result.
    @raise TestPushException: If verify fails.
    """
    print 'Comparing test results...'
    test_views = site_utils.get_test_views_from_tko(job_id, TKO)

    mismatch_errors = []
    extra_test_errors = []

    found_keys = set()
    for test_name, test_status in test_views.items():
        print "%s%s" % (test_name.ljust(30), test_status)
        # platform_InstallTestImage test may exist in old builds.
        if re.search('platform_InstallTestImage_SERVER_JOB$', test_name):
            continue
        test_found = False
        for key,val in expected_results.items():
            if re.search(key, test_name):
                test_found = True
                found_keys.add(key)
                if val != test_status:
                    error = ('%s Expected: [%s], Actual: [%s]' %
                             (test_name, val, test_status))
                    mismatch_errors.append(error)
        if not test_found:
            extra_test_errors.append(test_name)

    missing_test_errors = set(expected_results.keys()) - found_keys
    for exception in IGNORE_MISSING_TESTS:
        try:
            missing_test_errors.remove(exception)
        except KeyError:
            pass

    summary = []
    if mismatch_errors:
        summary.append(('Results of %d test(s) do not match expected '
                        'values:') % len(mismatch_errors))
        summary.extend(mismatch_errors)
        summary.append('\n')

    if extra_test_errors:
        summary.append('%d test(s) are not expected to be run:' %
                       len(extra_test_errors))
        summary.extend(extra_test_errors)
        summary.append('\n')

    if missing_test_errors:
        summary.append('%d test(s) are missing from the results:' %
                       len(missing_test_errors))
        summary.extend(missing_test_errors)
        summary.append('\n')

    # Test link to log can be loaded.
    job_name = '%s-%s' % (job_id, getpass.getuser())
    log_link = URL_PATTERN % (URL_HOST, job_name)
    try:
        urllib2.urlopen(log_link).read()
    except urllib2.URLError:
        summary.append('Failed to load page for link to log: %s.' % log_link)

    if summary:
        raise TestPushException('\n'.join(summary))


def test_suite_wrapper(queue, suite_name, expected_results, arguments,
                       use_shard=False, create_and_return=False,
                       testbed_test=False):
    """Wrapper to call test_suite. Handle exception and pipe it to parent
    process.

    @param queue: Queue to save exception to be accessed by parent process.
    @param suite_name: Name of a suite, e.g., dummy
    @param expected_results: A dictionary of test name to test result.
    @param arguments: Arguments for run_suite command.
    @param use_shard: If true, suite is scheduled for shard board.
    @param create_and_return: If True, run_suite just creates the suite, print
                              the job id, then finish immediately.
    @param testbed_test: True to run testbed test. Default is False.
    """
    try:
        test_suite(suite_name, expected_results, arguments, use_shard,
                   create_and_return, testbed_test)
    except:
        # Store the whole exc_info leads to a PicklingError.
        except_type, except_value, tb = sys.exc_info()
        queue.put((except_type, except_value, traceback.extract_tb(tb)))


def check_queue(queue):
    """Check the queue for any exception being raised.

    @param queue: Queue used to store exception for parent process to access.
    @raise: Any exception found in the queue.
    """
    if queue.empty():
        return
    exc_info = queue.get()
    # Raise the exception with original backtrace.
    print 'Original stack trace of the exception:\n%s' % exc_info[2]
    raise exc_info[0](exc_info[1])


def get_head_of_repos(repos):
    """Get HEAD of updated repos, currently are autotest and chromite repos

    @param repos: a map of repo name to the path of the repo. E.g.
                  {'autotest': '/usr/local/autotest'}
    @return: a map of repo names to the current HEAD of that repo.
    """
    @contextmanager
    def cd(new_wd):
        """Helper function to change working directory.

        @param new_wd: new working directory that switch to.
        """
        prev_wd = os.getcwd()
        os.chdir(os.path.expanduser(new_wd))
        try:
            yield
        finally:
            os.chdir(prev_wd)

    updated_repo_heads = {}
    for repo_name, path_to_repo in repos.iteritems():
        with cd(path_to_repo):
            head = subprocess.check_output('git rev-parse HEAD',
                                           shell=True).strip()
        updated_repo_heads[repo_name] = head
    return updated_repo_heads


def push_prod_next_branch(updated_repo_heads):
    """push prod-next branch to the tested HEAD after all tests pass.

    The push command must be ran as PUSH_USER, since only PUSH_USER has the
    right to push branches.

    @param updated_repo_heads: a map of repo names to tested HEAD of that repo.
    """
    # prod-next branch for every repo is downloaded under PUSH_USER home dir.
    cmd = ('cd ~/{repo}; git pull; git rebase {hash} prod-next;'
           'git push origin prod-next')
    run_push_as_push_user = "sudo su - %s -c '%s'" % (PUSH_USER, cmd)

    for repo_name, test_hash in updated_repo_heads.iteritems():
         push_cmd = run_push_as_push_user.format(hash=test_hash, repo=repo_name)
         print 'Pushing %s prod-next branch to %s' % (repo_name, test_hash)
         print subprocess.check_output(push_cmd, stderr=subprocess.STDOUT,
                                       shell=True)


def main():
    """Entry point for test_push script."""
    arguments = parse_arguments()
    updated_repo_heads = get_head_of_repos(UPDATED_REPOS)
    updated_repo_msg = '\n'.join(
        ['%s: %s' % (k, v) for k, v in updated_repo_heads.iteritems()])

    try:
        # Use daemon flag will kill child processes when parent process fails.
        use_daemon = not arguments.continue_on_failure
        # Verify all the DUTs at the beginning of testing push.
        reverify_all_push_duts()
        time.sleep(15) # Wait 15 secs for the verify test to start.
        check_dut_inventory(arguments.num_duts, arguments.pool)
        queue = multiprocessing.Queue()

        push_to_prod_suite = multiprocessing.Process(
                target=test_suite_wrapper,
                args=(queue, PUSH_TO_PROD_SUITE, EXPECTED_TEST_RESULTS,
                      arguments))
        push_to_prod_suite.daemon = use_daemon
        push_to_prod_suite.start()

        # TODO(dshi): Remove following line after crbug.com/267644 is fixed.
        # Also, merge EXPECTED_TEST_RESULTS_AU to EXPECTED_TEST_RESULTS
        # AU suite will be on shard until crbug.com/634049 is fixed.
        au_suite = multiprocessing.Process(
                target=test_suite_wrapper,
                args=(queue, AU_SUITE, EXPECTED_TEST_RESULTS_AU,
                      arguments, True))
        au_suite.daemon = use_daemon
        au_suite.start()

        # suite test with --create_and_return flag
        asynchronous_suite = multiprocessing.Process(
                target=test_suite_wrapper,
                args=(queue, DUMMY_SUITE, EXPECTED_TEST_RESULTS_DUMMY,
                      arguments, False, True))
        asynchronous_suite.daemon = True
        asynchronous_suite.start()

        # Test suite for testbed
        testbed_suite = multiprocessing.Process(
                target=test_suite_wrapper,
                args=(queue, TESTBED_SUITE, EXPECTED_TEST_RESULTS_TESTBED,
                      arguments, False, False, True))
        testbed_suite.daemon = use_daemon
        testbed_suite.start()

        while (push_to_prod_suite.is_alive() or au_suite.is_alive() or
               asynchronous_suite.is_alive() or testbed_suite.is_alive()):
            check_queue(queue)
            time.sleep(5)

        check_queue(queue)

        push_to_prod_suite.join()
        au_suite.join()
        asynchronous_suite.join()
        testbed_suite.join()

        # All tests pass, push prod-next branch for UPDATED_REPOS.
        push_prod_next_branch(updated_repo_heads)
    except Exception as e:
        print 'Test for pushing to prod failed:\n'
        print str(e)
        # Abort running jobs when choose not to continue when there is failure.
        if not arguments.continue_on_failure:
            for suite_id in all_suite_ids:
                if AFE.get_jobs(id=suite_id, finished=False):
                    AFE.run('abort_host_queue_entries', job=suite_id)
        # Send out email about the test failure.
        if arguments.email:
            gmail_lib.send_email(
                    arguments.email,
                    'Test for pushing to prod failed. Do NOT push!',
                    ('Test CLs of the following repos failed. Below are the '
                     'repos and the corresponding test HEAD.\n\n%s\n\n.'
                     'Error occurred during test:\n\n%s\n\n'
                     'All logs have been saved to '
                     '/var/log/test_push/test_push.log on push master. Detail '
                     'debugging info can be found at go/push-to-prod' %
                     (updated_repo_msg, str(e)) + '\n'.join(run_suite_output)))
        raise
    finally:
        # Reverify all the hosts
        reverify_all_push_duts()

    message = ('\nAll tests are completed successfully, the prod branch of the '
               'following repos ready to be pushed to the hash list below.\n'
               '%s\n\n\nInstructions for pushing to prod are available at '
               'https://goto.google.com/autotest-to-prod' % updated_repo_msg)
    print message
    # Send out email about test completed successfully.
    if arguments.email:
        gmail_lib.send_email(
                arguments.email,
                'Test for pushing to prod completed successfully',
                message)


if __name__ == '__main__':
    sys.exit(main())
