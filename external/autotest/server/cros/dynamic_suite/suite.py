# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import datetime
import difflib
import hashlib
import logging
import operator
import os
import re
import sys

import common

from autotest_lib.frontend.afe.json_rpc import proxy
from autotest_lib.client.common_lib import control_data
from autotest_lib.client.common_lib import enum
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import global_config
from autotest_lib.client.common_lib import priorities
from autotest_lib.client.common_lib import site_utils
from autotest_lib.client.common_lib import time_utils
from autotest_lib.client.common_lib import utils
from autotest_lib.frontend.afe.json_rpc import proxy
from autotest_lib.server.cros import provision
from autotest_lib.server.cros.dynamic_suite import constants
from autotest_lib.server.cros.dynamic_suite import control_file_getter
from autotest_lib.server.cros.dynamic_suite import frontend_wrappers
from autotest_lib.server.cros.dynamic_suite import job_status
from autotest_lib.server.cros.dynamic_suite import tools
from autotest_lib.server.cros.dynamic_suite.job_status import Status

try:
    from chromite.lib import boolparse_lib
    from chromite.lib import cros_logging as logging
except ImportError:
    print 'Unable to import chromite.'
    print 'This script must be either:'
    print '  - Be run in the chroot.'
    print '  - (not yet supported) be run after running '
    print '    ../utils/build_externals.py'

_FILE_BUG_SUITES = ['au', 'bvt', 'bvt-cq', 'bvt-inline', 'paygen_au_beta',
                    'paygen_au_canary', 'paygen_au_dev', 'paygen_au_stable',
                    'sanity', 'push_to_prod']
_AUTOTEST_DIR = global_config.global_config.get_config_value(
        'SCHEDULER', 'drone_installation_directory')
ENABLE_CONTROLS_IN_BATCH = global_config.global_config.get_config_value(
        'CROS', 'enable_getting_controls_in_batch', type=bool, default=False)

class RetryHandler(object):
    """Maintain retry information.

    @var _retry_map: A dictionary that stores retry history.
            The key is afe job id. The value is a dictionary.
            {job_id: {'state':RetryHandler.States, 'retry_max':int}}
            - state:
                The retry state of a job.
                NOT_ATTEMPTED:
                    We haven't done anything about the job.
                ATTEMPTED:
                    We've made an attempt to schedule a retry job. The
                    scheduling may or may not be successful, e.g.
                    it might encounter an rpc error. Note failure
                    in scheduling a retry is different from a retry job failure.
                    For each job, we only attempt to schedule a retry once.
                    For example, assume we have a test with JOB_RETRIES=5 and
                    its second retry job failed. When we attempt to create
                    a third retry job to retry the second, we hit an rpc
                    error. In such case, we will give up on all following
                    retries.
                RETRIED:
                    A retry job has already been successfully
                    scheduled.
            - retry_max:
                The maximum of times the job can still
                be retried, taking into account retries
                that have occurred.
    @var _retry_level: A retry might be triggered only if the result
            is worse than the level.
    @var _max_retries: Maximum retry limit at suite level.
                     Regardless how many times each individual test
                     has been retried, the total number of retries happening in
                     the suite can't exceed _max_retries.
    """

    States = enum.Enum('NOT_ATTEMPTED', 'ATTEMPTED', 'RETRIED',
                       start_value=1, step=1)

    def __init__(self, initial_jobs_to_tests, retry_level='WARN',
                 max_retries=None):
        """Initialize RetryHandler.

        @param initial_jobs_to_tests: A dictionary that maps a job id to
                a ControlData object. This dictionary should contain
                jobs that are originally scheduled by the suite.
        @param retry_level: A retry might be triggered only if the result is
                worse than the level.
        @param max_retries: Integer, maxmium total retries allowed
                                  for the suite. Default to None, no max.
        """
        self._retry_map = {}
        self._retry_level = retry_level
        self._max_retries = (max_retries
                             if max_retries is not None else sys.maxint)
        for job_id, test in initial_jobs_to_tests.items():
            if test.job_retries > 0:
                self._add_job(new_job_id=job_id,
                              retry_max=test.job_retries)


    def _add_job(self, new_job_id, retry_max):
        """Add a newly-created job to the retry map.

        @param new_job_id: The afe_job_id of a newly created job.
        @param retry_max: The maximum of times that we could retry
                          the test if the job fails.

        @raises ValueError if new_job_id is already in retry map.

        """
        if new_job_id in self._retry_map:
            raise ValueError('add_job called when job is already in retry map.')

        self._retry_map[new_job_id] = {
                'state': self.States.NOT_ATTEMPTED,
                'retry_max': retry_max}


    def _suite_max_reached(self):
        """Return whether maximum retry limit for a suite has been reached."""
        return self._max_retries <= 0


    def add_retry(self, old_job_id, new_job_id):
        """Record a retry.

        Update retry map with the retry information.

        @param old_job_id: The afe_job_id of the job that is retried.
        @param new_job_id: The afe_job_id of the retry job.

        @raises KeyError if old_job_id isn't in the retry map.
        @raises ValueError if we have already retried or made an attempt
                to retry the old job.

        """
        old_record = self._retry_map[old_job_id]
        if old_record['state'] != self.States.NOT_ATTEMPTED:
            raise ValueError(
                    'We have already retried or attempted to retry job %d' %
                    old_job_id)
        old_record['state'] = self.States.RETRIED
        self._add_job(new_job_id=new_job_id,
                      retry_max=old_record['retry_max'] - 1)
        self._max_retries -= 1


    def set_attempted(self, job_id):
        """Set the state of the job to ATTEMPTED.

        @param job_id: afe_job_id of a job.

        @raises KeyError if job_id isn't in the retry map.
        @raises ValueError if the current state is not NOT_ATTEMPTED.

        """
        current_state = self._retry_map[job_id]['state']
        if current_state != self.States.NOT_ATTEMPTED:
            # We are supposed to retry or attempt to retry each job
            # only once. Raise an error if this is not the case.
            raise ValueError('Unexpected state transition: %s -> %s' %
                             (self.States.get_string(current_state),
                              self.States.get_string(self.States.ATTEMPTED)))
        else:
            self._retry_map[job_id]['state'] = self.States.ATTEMPTED


    def has_following_retry(self, result):
        """Check whether there will be a following retry.

        We have the following cases for a given job id (result.id),
        - no retry map entry -> retry not required, no following retry
        - has retry map entry:
            - already retried -> has following retry
            - has not retried
                (this branch can be handled by checking should_retry(result))
                - retry_max == 0 --> the last retry job, no more retry
                - retry_max > 0
                   - attempted, but has failed in scheduling a
                     following retry due to rpc error  --> no more retry
                   - has not attempped --> has following retry if test failed.

        @param result: A result, encapsulating the status of the job.

        @returns: True, if there will be a following retry.
                  False otherwise.

        """
        return (result.test_executed
                and result.id in self._retry_map
                and (self._retry_map[result.id]['state'] == self.States.RETRIED
                     or self._should_retry(result)))


    def _should_retry(self, result):
        """Check whether we should retry a job based on its result.

        This method only makes sense when called by has_following_retry().

        We will retry the job that corresponds to the result
        when all of the following are true.
        a) The test was actually executed, meaning that if
           a job was aborted before it could ever reach the state
           of 'Running', the job will not be retried.
        b) The result is worse than |self._retry_level| which
           defaults to 'WARN'.
        c) The test requires retry, i.e. the job has an entry in the retry map.
        d) We haven't made any retry attempt yet, i.e. state == NOT_ATTEMPTED
           Note that if a test has JOB_RETRIES=5, and the second time
           it was retried it hit an rpc error, we will give up on
           all following retries.
        e) The job has not reached its retry max, i.e. retry_max > 0

        @param result: A result, encapsulating the status of the job.

        @returns: True if we should retry the job.

        """
        assert result.test_executed
        assert result.id in self._retry_map
        return (
            not self._suite_max_reached()
            and result.is_worse_than(
                job_status.Status(self._retry_level, '', 'reason'))
            and self._retry_map[result.id]['state'] == self.States.NOT_ATTEMPTED
            and self._retry_map[result.id]['retry_max'] > 0
        )


    def get_retry_max(self, job_id):
        """Get the maximum times the job can still be retried.

        @param job_id: afe_job_id of a job.

        @returns: An int, representing the maximum times the job can still be
                  retried.
        @raises KeyError if job_id isn't in the retry map.

        """
        return self._retry_map[job_id]['retry_max']


class _DynamicSuiteDiscoverer(object):
    """Test discoverer for dynamic suite tests."""


    def __init__(self, tests, add_experimental=True):
        """Initialize instance.

        @param tests: iterable of tests (ControlData objects)
        @param add_experimental: schedule experimental tests as well, or not.
        """
        self._tests = list(tests)
        self._add_experimental = add_experimental


    def discover_tests(self):
        """Return a list of tests to be scheduled for this suite.

        @returns: list of tests (ControlData objects)
        """
        tests = self.stable_tests
        if self._add_experimental:
            for test in self.unstable_tests:
                if not test.name.startswith(constants.EXPERIMENTAL_PREFIX):
                    test.name = constants.EXPERIMENTAL_PREFIX + test.name
                tests.append(test)
        return tests


    @property
    def stable_tests(self):
        """Non-experimental tests.

        @returns: list
        """
        return filter(lambda t: not t.experimental, self._tests)


    @property
    def unstable_tests(self):
        """Experimental tests.

        @returns: list
        """
        return filter(lambda t: t.experimental, self._tests)


class Suite(object):
    """
    A suite of tests, defined by some predicate over control file variables.

    Given a place to search for control files a predicate to match the desired
    tests, can gather tests and fire off jobs to run them, and then wait for
    results.

    @var _predicate: a function that should return True when run over a
         ControlData representation of a control file that should be in
         this Suite.
    @var _tag: a string with which to tag jobs run in this suite.
    @var _builds: the builds on which we're running this suite.
    @var _afe: an instance of AFE as defined in server/frontend.py.
    @var _tko: an instance of TKO as defined in server/frontend.py.
    @var _jobs: currently scheduled jobs, if any.
    @var _jobs_to_tests: a dictionary that maps job ids to tests represented
                         ControlData objects.
    @var _cf_getter: a control_file_getter.ControlFileGetter
    @var _retry: a bool value indicating whether jobs should be retried on
                 failure.
    @var _retry_handler: a RetryHandler object.

    """


    @staticmethod
    def _create_ds_getter(build, devserver):
        """
        @param build: the build on which we're running this suite.
        @param devserver: the devserver which contains the build.
        @return a FileSystemGetter instance that looks under |autotest_dir|.
        """
        return control_file_getter.DevServerGetter(build, devserver)


    @staticmethod
    def create_fs_getter(autotest_dir):
        """
        @param autotest_dir: the place to find autotests.
        @return a FileSystemGetter instance that looks under |autotest_dir|.
        """
        # currently hard-coded places to look for tests.
        subpaths = ['server/site_tests', 'client/site_tests',
                    'server/tests', 'client/tests']
        directories = [os.path.join(autotest_dir, p) for p in subpaths]
        return control_file_getter.FileSystemGetter(directories)


    @staticmethod
    def name_in_tag_predicate(name):
        """Returns predicate that takes a control file and looks for |name|.

        Builds a predicate that takes in a parsed control file (a ControlData)
        and returns True if the SUITE tag is present and contains |name|.

        @param name: the suite name to base the predicate on.
        @return a callable that takes a ControlData and looks for |name| in that
                ControlData object's suite member.
        """
        return lambda t: name in t.suite_tag_parts


    @staticmethod
    def name_in_tag_similarity_predicate(name):
        """Returns predicate that takes a control file and gets the similarity
        of the suites in the control file and the given name.

        Builds a predicate that takes in a parsed control file (a ControlData)
        and returns a list of tuples of (suite name, ratio), where suite name
        is each suite listed in the control file, and ratio is the similarity
        between each suite and the given name.

        @param name: the suite name to base the predicate on.
        @return a callable that takes a ControlData and returns a list of tuples
                of (suite name, ratio), where suite name is each suite listed in
                the control file, and ratio is the similarity between each suite
                and the given name.
        """
        return lambda t: [(suite,
                           difflib.SequenceMatcher(a=suite, b=name).ratio())
                          for suite in t.suite_tag_parts] or [(None, 0)]


    @staticmethod
    def test_name_equals_predicate(test_name):
        """Returns predicate that matched based on a test's name.

        Builds a predicate that takes in a parsed control file (a ControlData)
        and returns True if the test name is equal to |test_name|.

        @param test_name: the test name to base the predicate on.
        @return a callable that takes a ControlData and looks for |test_name|
                in that ControlData's name.
        """
        return lambda t: hasattr(t, 'name') and test_name == t.name


    @staticmethod
    def test_name_matches_pattern_predicate(test_name_pattern):
        """Returns predicate that matches based on a test's name pattern.

        Builds a predicate that takes in a parsed control file (a ControlData)
        and returns True if the test name matches the given regular expression.

        @param test_name_pattern: regular expression (string) to match against
                                  test names.
        @return a callable that takes a ControlData and returns
                True if the name fields matches the pattern.
        """
        return lambda t: hasattr(t, 'name') and re.match(test_name_pattern,
                                                         t.name)


    @staticmethod
    def test_file_matches_pattern_predicate(test_file_pattern):
        """Returns predicate that matches based on a test's file name pattern.

        Builds a predicate that takes in a parsed control file (a ControlData)
        and returns True if the test's control file name matches the given
        regular expression.

        @param test_file_pattern: regular expression (string) to match against
                                  control file names.
        @return a callable that takes a ControlData and and returns
                True if control file name matches the pattern.
        """
        return lambda t: hasattr(t, 'path') and re.match(test_file_pattern,
                                                         t.path)


    @staticmethod
    def matches_attribute_expression_predicate(test_attr_boolstr):
        """Returns predicate that matches based on boolean expression of
        attributes.

        Builds a predicate that takes in a parsed control file (a ControlData)
        ans returns True if the test attributes satisfy the given attribute
        boolean expression.

        @param test_attr_boolstr: boolean expression of the attributes to be
                                  test, like 'system:all and interval:daily'.

        @return a callable that takes a ControlData and returns True if the test
                attributes satisfy the given boolean expression.
        """
        return lambda t: boolparse_lib.BoolstrResult(
            test_attr_boolstr, t.attributes)

    @staticmethod
    def test_name_similarity_predicate(test_name):
        """Returns predicate that matched based on a test's name.

        Builds a predicate that takes in a parsed control file (a ControlData)
        and returns a tuple of (test name, ratio), where ratio is the similarity
        between the test name and the given test_name.

        @param test_name: the test name to base the predicate on.
        @return a callable that takes a ControlData and returns a tuple of
                (test name, ratio), where ratio is the similarity between the
                test name and the given test_name.
        """
        return lambda t: ((None, 0) if not hasattr(t, 'name') else
                (t.name,
                 difflib.SequenceMatcher(a=t.name, b=test_name).ratio()))


    @staticmethod
    def test_file_similarity_predicate(test_file_pattern):
        """Returns predicate that gets the similarity based on a test's file
        name pattern.

        Builds a predicate that takes in a parsed control file (a ControlData)
        and returns a tuple of (file path, ratio), where ratio is the
        similarity between the test file name and the given test_file_pattern.

        @param test_file_pattern: regular expression (string) to match against
                                  control file names.
        @return a callable that takes a ControlData and and returns a tuple of
                (file path, ratio), where ratio is the similarity between the
                test file name and the given test_file_pattern.
        """
        return lambda t: ((None, 0) if not hasattr(t, 'path') else
                (t.path, difflib.SequenceMatcher(a=t.path,
                                                 b=test_file_pattern).ratio()))


    @classmethod
    def list_all_suites(cls, build, devserver, cf_getter=None):
        """
        Parses all ControlData objects with a SUITE tag and extracts all
        defined suite names.

        @param build: the build on which we're running this suite.
        @param devserver: the devserver which contains the build.
        @param cf_getter: control_file_getter.ControlFileGetter. Defaults to
                          using DevServerGetter.

        @return list of suites
        """
        if cf_getter is None:
            cf_getter = cls._create_ds_getter(build, devserver)

        suites = set()
        predicate = lambda t: True
        for test in cls.find_and_parse_tests(cf_getter, predicate,
                                             add_experimental=True):
            suites.update(test.suite_tag_parts)
        return list(suites)


    @staticmethod
    def get_test_source_build(builds, **dargs):
        """Get the build of test code.

        Get the test source build from arguments. If parameter
        `test_source_build` is set and has a value, return its value. Otherwise
        returns the ChromeOS build name if it exists. If ChromeOS build is not
        specified either, raise SuiteArgumentException.

        @param builds: the builds on which we're running this suite. It's a
                       dictionary of version_prefix:build.
        @param **dargs: Any other Suite constructor parameters, as described
                        in Suite.__init__ docstring.

        @return: The build contains the test code.
        @raise: SuiteArgumentException if both test_source_build and ChromeOS
                build are not specified.

        """
        if dargs.get('test_source_build', None):
            return dargs['test_source_build']
        test_source_build = builds.get(provision.CROS_VERSION_PREFIX, None)
        if not test_source_build:
            raise error.SuiteArgumentException(
                    'test_source_build must be specified if CrOS build is not '
                    'specified.')
        return test_source_build


    @classmethod
    def create_from_predicates(cls, predicates, builds, board, devserver,
                               cf_getter=None, name='ad_hoc_suite',
                               run_prod_code=False, **dargs):
        """
        Create a Suite using a given predicate test filters.

        Uses supplied predicate(s) to instantiate a Suite. Looks for tests in
        |autotest_dir| and will schedule them using |afe|.  Pulls control files
        from the default dev server. Results will be pulled from |tko| upon
        completion.

        @param predicates: A list of callables that accept ControlData
                           representations of control files. A test will be
                           included in suite if all callables in this list
                           return True on the given control file.
        @param builds: the builds on which we're running this suite. It's a
                       dictionary of version_prefix:build.
        @param board: the board on which we're running this suite.
        @param devserver: the devserver which contains the build.
        @param cf_getter: control_file_getter.ControlFileGetter. Defaults to
                          using DevServerGetter.
        @param name: name of suite. Defaults to 'ad_hoc_suite'
        @param run_prod_code: If true, the suite will run the tests that
                              lives in prod aka the test code currently on the
                              lab servers.
        @param **dargs: Any other Suite constructor parameters, as described
                        in Suite.__init__ docstring.
        @return a Suite instance.
        """
        if cf_getter is None:
            if run_prod_code:
                cf_getter = cls.create_fs_getter(_AUTOTEST_DIR)
            else:
                build = cls.get_test_source_build(builds, **dargs)
                cf_getter = cls._create_ds_getter(build, devserver)

        return cls(predicates,
                   name, builds, board, cf_getter, run_prod_code, **dargs)


    @classmethod
    def create_from_name(cls, name, builds, board, devserver, cf_getter=None,
                         **dargs):
        """
        Create a Suite using a predicate based on the SUITE control file var.

        Makes a predicate based on |name| and uses it to instantiate a Suite
        that looks for tests in |autotest_dir| and will schedule them using
        |afe|.  Pulls control files from the default dev server.
        Results will be pulled from |tko| upon completion.

        @param name: a value of the SUITE control file variable to search for.
        @param builds: the builds on which we're running this suite. It's a
                       dictionary of version_prefix:build.
        @param board: the board on which we're running this suite.
        @param devserver: the devserver which contains the build.
        @param cf_getter: control_file_getter.ControlFileGetter. Defaults to
                          using DevServerGetter.
        @param **dargs: Any other Suite constructor parameters, as described
                        in Suite.__init__ docstring.
        @return a Suite instance.
        """
        if cf_getter is None:
            build = cls.get_test_source_build(builds, **dargs)
            cf_getter = cls._create_ds_getter(build, devserver)

        return cls([cls.name_in_tag_predicate(name)],
                   name, builds, board, cf_getter, **dargs)


    def __init__(
            self,
            predicates,
            tag,
            builds,
            board,
            cf_getter,
            run_prod_code=False,
            afe=None,
            tko=None,
            pool=None,
            results_dir=None,
            max_runtime_mins=24*60,
            timeout_mins=24*60,
            file_bugs=False,
            file_experimental_bugs=False,
            suite_job_id=None,
            ignore_deps=False,
            extra_deps=None,
            priority=priorities.Priority.DEFAULT,
            forgiving_parser=True,
            wait_for_results=True,
            job_retry=False,
            max_retries=sys.maxint,
            offload_failures_only=False,
            test_source_build=None,
            job_keyvals=None,
            test_args=None
    ):
        """
        Constructor

        @param predicates: A list of callables that accept ControlData
                           representations of control files. A test will be
                           included in suite is all callables in this list
                           return True on the given control file.
        @param tag: a string with which to tag jobs run in this suite.
        @param builds: the builds on which we're running this suite.
        @param board: the board on which we're running this suite.
        @param cf_getter: a control_file_getter.ControlFileGetter
        @param afe: an instance of AFE as defined in server/frontend.py.
        @param tko: an instance of TKO as defined in server/frontend.py.
        @param pool: Specify the pool of machines to use for scheduling
                purposes.
        @param run_prod_code: If true, the suite will run the test code that
                              lives in prod aka the test code currently on the
                              lab servers.
        @param results_dir: The directory where the job can write results to.
                            This must be set if you want job_id of sub-jobs
                            list in the job keyvals.
        @param max_runtime_mins: Maximum suite runtime, in minutes.
        @param timeout: Maximum job lifetime, in hours.
        @param suite_job_id: Job id that will act as parent id to all sub jobs.
                             Default: None
        @param ignore_deps: True if jobs should ignore the DEPENDENCIES
                            attribute and skip applying of dependency labels.
                            (Default:False)
        @param extra_deps: A list of strings which are the extra DEPENDENCIES
                           to add to each test being scheduled.
        @param priority: Integer priority level.  Higher is more important.
        @param wait_for_results: Set to False to run the suite job without
                                 waiting for test jobs to finish. Default is
                                 True.
        @param job_retry: A bool value indicating whether jobs should be retired
                          on failure. If True, the field 'JOB_RETRIES' in
                          control files will be respected. If False, do not
                          retry.
        @param max_retries: Maximum retry limit at suite level.
                            Regardless how many times each individual test
                            has been retried, the total number of retries
                            happening in the suite can't exceed _max_retries.
                            Default to sys.maxint.
        @param offload_failures_only: Only enable gs_offloading for failed
                                      jobs.
        @param test_source_build: Build that contains the server-side test code.
        @param job_keyvals: General job keyvals to be inserted into keyval file,
                            which will be used by tko/parse later.
        @param test_args: A dict of args passed all the way to each individual
                          test that will be actually ran.
        """
        if extra_deps is None:
            extra_deps = []

        self._tag = tag
        self._builds = builds
        self._board = board
        self._cf_getter = cf_getter
        self._results_dir = results_dir
        self._afe = afe or frontend_wrappers.RetryingAFE(timeout_min=30,
                                                         delay_sec=10,
                                                         debug=False)
        self._tko = tko or frontend_wrappers.RetryingTKO(timeout_min=30,
                                                         delay_sec=10,
                                                         debug=False)
        self._pool = pool
        self._jobs = []
        self._jobs_to_tests = {}
        self.tests = self.find_and_parse_tests(
                self._cf_getter,
                lambda control_data: all(f(control_data) for f in predicates),
                self._tag,
                add_experimental=True,
                forgiving_parser=forgiving_parser,
                run_prod_code=run_prod_code,
                test_args=test_args,
        )

        self._max_runtime_mins = max_runtime_mins
        self._timeout_mins = timeout_mins
        self._file_bugs = file_bugs
        self._file_experimental_bugs = file_experimental_bugs
        self._suite_job_id = suite_job_id
        self._ignore_deps = ignore_deps
        self._extra_deps = extra_deps
        self._priority = priority
        self._job_retry=job_retry
        self._max_retries = max_retries
        # RetryHandler to be initialized in schedule()
        self._retry_handler = None
        self.wait_for_results = wait_for_results
        self._offload_failures_only = offload_failures_only
        self._test_source_build = test_source_build
        self._job_keyvals = job_keyvals
        self._test_args = test_args


    @property
    def _cros_build(self):
        """Return the CrOS build or the first build in the builds dict."""
        # TODO(ayatane): Note that the builds dict isn't ordered.  I'm not
        # sure what the implications of this are, but it's probably not a
        # good thing.
        return self._builds.get(provision.CROS_VERSION_PREFIX,
                                self._builds.values()[0])


    def _create_job(self, test, retry_for=None):
        """
        Thin wrapper around frontend.AFE.create_job().

        @param test: ControlData object for a test to run.
        @param retry_for: If the to-be-created job is a retry for an
                          old job, the afe_job_id of the old job will
                          be passed in as |retry_for|, which will be
                          recorded in the new job's keyvals.
        @returns: A frontend.Job object with an added test_name member.
                  test_name is used to preserve the higher level TEST_NAME
                  name of the job.
        """
        test_obj = self._afe.create_job(
            control_file=test.text,
            name=tools.create_job_name(
                    self._test_source_build or self._cros_build,
                    self._tag,
                    test.name),
            control_type=test.test_type.capitalize(),
            meta_hosts=[self._board]*test.sync_count,
            dependencies=self._create_job_deps(test),
            keyvals=self._create_keyvals_for_test_job(test, retry_for),
            max_runtime_mins=self._max_runtime_mins,
            timeout_mins=self._timeout_mins,
            parent_job_id=self._suite_job_id,
            test_retry=test.retries,
            priority=self._priority,
            synch_count=test.sync_count,
            require_ssp=test.require_ssp)

        test_obj.test_name = test.name
        return test_obj


    def _create_job_deps(self, test):
        """Create job deps list for a test job.

        @returns: A list of dependency strings.
        """
        if self._ignore_deps:
            job_deps = []
        else:
            job_deps = list(test.dependencies)
        job_deps.extend(self._extra_deps)
        if self._pool:
            job_deps.append(self._pool)
        job_deps.append(self._board)
        return job_deps


    def _create_keyvals_for_test_job(self, test, retry_for=None):
        """Create keyvals dict for creating a test job.

        @param test: ControlData object for a test to run.
        @param retry_for: If the to-be-created job is a retry for an
                          old job, the afe_job_id of the old job will
                          be passed in as |retry_for|, which will be
                          recorded in the new job's keyvals.
        @returns: A keyvals dict for creating the test job.
        """
        keyvals = {
            constants.JOB_BUILD_KEY: self._cros_build,
            constants.JOB_SUITE_KEY: self._tag,
            constants.JOB_EXPERIMENTAL_KEY: test.experimental,
            constants.JOB_BUILDS_KEY: self._builds
        }
        # test_source_build is saved to job_keyvals so scheduler can retrieve
        # the build name from database when compiling autoserv commandline.
        # This avoid a database change to add a new field in afe_jobs.
        #
        # Only add `test_source_build` to job keyvals if the build is different
        # from the CrOS build or the job uses more than one build, e.g., both
        # firmware and CrOS will be updated in the dut.
        # This is for backwards compatibility, so the update Autotest code can
        # compile an autoserv command line to run in a SSP container using
        # previous builds.
        if (self._test_source_build and
            (self._cros_build != self._test_source_build or
             len(self._builds) > 1)):
            keyvals[constants.JOB_TEST_SOURCE_BUILD_KEY] = \
                    self._test_source_build
            for prefix, build in self._builds.iteritems():
                if prefix == provision.FW_RW_VERSION_PREFIX:
                    keyvals[constants.FWRW_BUILD]= build
                elif prefix == provision.FW_RO_VERSION_PREFIX:
                    keyvals[constants.FWRO_BUILD] = build
        # Add suite job id to keyvals so tko parser can read it from keyval
        # file.
        if self._suite_job_id:
            keyvals[constants.PARENT_JOB_ID] = self._suite_job_id
        # We drop the old job's id in the new job's keyval file so that
        # later our tko parser can figure out the retry relationship and
        # invalidate the results of the old job in tko database.
        if retry_for:
            keyvals[constants.RETRY_ORIGINAL_JOB_ID] = retry_for
        if self._offload_failures_only:
            keyvals[constants.JOB_OFFLOAD_FAILURES_KEY] = True
        return keyvals


    def _schedule_test(self, record, test, retry_for=None, ignore_errors=False):
        """Schedule a single test and return the job.

        Schedule a single test by creating a job, and then update relevant
        data structures that are used to keep track of all running jobs.

        Emits a TEST_NA status log entry if it failed to schedule the test due
        to NoEligibleHostException or a non-existent board label.

        Returns a frontend.Job object if the test is successfully scheduled.
        If scheduling failed due to NoEligibleHostException or a non-existent
        board label, returns None.  If ignore_errors is True, all unknown
        errors return None, otherwise the errors are raised as-is.

        @param record: A callable to use for logging.
                       prototype: record(base_job.status_log_entry)
        @param test: ControlData for a test to run.
        @param retry_for: If we are scheduling a test to retry an
                          old job, the afe_job_id of the old job
                          will be passed in as |retry_for|.
        @param ignore_errors: If True, when an rpc error occur, ignore
                             the error and will return None.
                             If False, rpc errors will be raised.

        @returns: A frontend.Job object or None
        """
        msg = 'Scheduling %s' % test.name
        if retry_for:
            msg = msg + ', to retry afe job %d' % retry_for
        logging.debug(msg)
        begin_time_str = datetime.datetime.now().strftime(time_utils.TIME_FMT)
        try:
            job = self._create_job(test, retry_for=retry_for)
        except (error.NoEligibleHostException, proxy.ValidationError) as e:
            if (isinstance(e, error.NoEligibleHostException)
                or (isinstance(e, proxy.ValidationError)
                    and _is_nonexistent_board_error(e))):
                # Treat a dependency on a non-existent board label the same as
                # a dependency on a board that exists, but for which there's no
                # hardware.
                logging.debug('%s not applicable for this board/pool. '
                              'Emitting TEST_NA.', test.name)
                Status('TEST_NA', test.name,
                       'Skipping:  test not supported on this board/pool.',
                       begin_time_str=begin_time_str).record_all(record)
                return None
            else:
                raise e
        except (error.RPCException, proxy.JSONRPCException) as e:
            if retry_for:
                # Mark that we've attempted to retry the old job.
                self._retry_handler.set_attempted(job_id=retry_for)

            if ignore_errors:
                logging.error('Failed to schedule test: %s, Reason: %s',
                              test.name, e)
                return None
            else:
                raise e
        else:
            self._jobs.append(job)
            self._jobs_to_tests[job.id] = test
            if retry_for:
                # A retry job was just created, record it.
                self._retry_handler.add_retry(
                        old_job_id=retry_for, new_job_id=job.id)
                retry_count = (test.job_retries -
                               self._retry_handler.get_retry_max(job.id))
                logging.debug('Job %d created to retry job %d. '
                              'Have retried for %d time(s)',
                              job.id, retry_for, retry_count)
            self._remember_job_keyval(job)
            return job


    def schedule(self, record, add_experimental=True):
        #pylint: disable-msg=C0111
        """
        Schedule jobs using |self._afe|.

        frontend.Job objects representing each scheduled job will be put in
        |self._jobs|.

        @param record: A callable to use for logging.
                       prototype: record(base_job.status_log_entry)
        @param add_experimental: schedule experimental tests as well, or not.
        @returns: The number of tests that were scheduled.
        """
        scheduled_test_names = []
        discoverer = _DynamicSuiteDiscoverer(
                tests=self.tests,
                add_experimental=add_experimental)
        logging.debug('Discovered %d stable tests.',
                      len(discoverer.stable_tests))
        logging.debug('Discovered %d unstable tests.',
                      len(discoverer.unstable_tests))

        Status('INFO', 'Start %s' % self._tag).record_result(record)
        try:
            # Write job_keyvals into keyval file.
            if self._job_keyvals:
                utils.write_keyval(self._results_dir, self._job_keyvals)

            for test in discoverer.discover_tests():
                scheduled_job = self._schedule_test(record, test)
                if scheduled_job is not None:
                    scheduled_test_names.append(test.name)

            # Write the num of scheduled tests and name of them to keyval file.
            logging.debug('Scheduled %d tests, writing the total to keyval.',
                          len(scheduled_test_names))
            utils.write_keyval(
                self._results_dir,
                self._make_scheduled_tests_keyvals(scheduled_test_names))
        except Exception:  # pylint: disable=W0703
            logging.exception('Exception while scheduling suite')
            Status('FAIL', self._tag,
                   'Exception while scheduling suite').record_result(record)

        if self._job_retry:
            self._retry_handler = RetryHandler(
                    initial_jobs_to_tests=self._jobs_to_tests,
                    max_retries=self._max_retries)
        return len(scheduled_test_names)


    def _make_scheduled_tests_keyvals(self, scheduled_test_names):
        """Make a keyvals dict to write for scheduled test names.

        @param scheduled_test_names: A list of scheduled test name strings.

        @returns: A keyvals dict.
        """
        return {
            constants.SCHEDULED_TEST_COUNT_KEY: len(scheduled_test_names),
            constants.SCHEDULED_TEST_NAMES_KEY: repr(scheduled_test_names),
        }


    def _should_report(self, result):
        """
        Returns True if this failure requires to be reported.

        @param result: A result, encapsulating the status of the failed job.
        @return: True if we should report this failure.
        """
        if self._has_retry(result):
            return False

        is_not_experimental = (
            constants.EXPERIMENTAL_PREFIX not in result._test_name and
            constants.EXPERIMENTAL_PREFIX not in result._job_name)

        return (self._file_bugs and result.test_executed and
                (is_not_experimental or self._file_experimental_bugs) and
                not result.is_testna() and
                result.is_worse_than(job_status.Status('GOOD', '', 'reason')))


    def _has_retry(self, result):
        """
        Return True if this result gets to retry.

        @param result: A result, encapsulating the status of the failed job.
        @return: bool
        """
        return (self._job_retry
                and self._retry_handler.has_following_retry(result))


    def wait(self, record, bug_template=None):
        """
        Polls for the job statuses, using |record| to print status when each
        completes.

        @param record: callable that records job status.
                 prototype:
                   record(base_job.status_log_entry)
        @param bug_template: A template dictionary specifying the default bug
                             filing options for failures in this suite.
        """
        # reporting modules have dependency on external packages, e.g., httplib2
        # Such dependency can cause issue to any module tries to import suite.py
        # without building site-packages first. Since the reporting modules are
        # only used in this function, move the imports here avoid the
        # requirement of building site packages to use other functions in this
        # module.
        from autotest_lib.server.cros.dynamic_suite import reporting

        if bug_template is None:
            bug_template = {}

        if self._file_bugs:
            bug_reporter = reporting.Reporter()
        else:
            bug_reporter = reporting.NullReporter()
        try:
            if self._suite_job_id:
                results_generator = job_status.wait_for_child_results(
                        self._afe, self._tko, self._suite_job_id)
            else:
                logging.warning('Unknown suite_job_id, falling back to less '
                                'efficient results_generator.')
                results_generator = job_status.wait_for_results(self._afe,
                                                                self._tko,
                                                                self._jobs)
            for result in results_generator:
                self._record_result(
                    result=result,
                    record=record,
                    results_generator=results_generator,
                    bug_reporter=bug_reporter,
                    bug_template=bug_template)

        except Exception:  # pylint: disable=W0703
            logging.exception('Exception waiting for results')
            Status('FAIL', self._tag,
                   'Exception waiting for results').record_result(record)


    def _record_result(self, result, record, results_generator, bug_reporter,
                         bug_template):
        """
        Record a single test job result.

        @param result: Status instance for job.
        @param record: callable that records job status.
                 prototype:
                   record(base_job.status_log_entry)
        @param results_generator: Results generator for sending job retries.
        @param bug_reporter: Reporter instance for reporting bugs.
        @param bug_template: A template dictionary specifying the default bug
                             filing options for failures in this suite.
        """
        result.record_all(record)
        self._remember_job_keyval(result)

        if self._has_retry(result):
            new_job = self._schedule_test(
                    record=record, test=self._jobs_to_tests[result.id],
                    retry_for=result.id, ignore_errors=True)
            if new_job:
                results_generator.send([new_job])

        # TODO (fdeng): If the suite times out before a retry could
        # finish, we would lose the chance to file a bug for the
        # original job.
        if self._should_report(result):
            if self._should_file_bugs:
                self._file_bug(result, bug_reporter, bug_template)
            else:
                # reporting modules have dependency on external
                # packages, e.g., httplib2 Such dependency can cause
                # issue to any module tries to import suite.py without
                # building site-packages first. Since the reporting
                # modules are only used in this function, move the
                # imports here avoid the requirement of building site
                # packages to use other functions in this module.
                from autotest_lib.server.cros.dynamic_suite import reporting

                reporting.send_email(
                        self._get_test_bug(result),
                        self._get_bug_template(result, bug_template))


    def _get_bug_template(self, result, bug_template):
        """Get BugTemplate for test job.

        @param result: Status instance for job.
        @param bug_template: A template dictionary specifying the default bug
                             filing options for failures in this suite.
        @returns: BugTemplate instance
        """
        # reporting modules have dependency on external packages, e.g., httplib2
        # Such dependency can cause issue to any module tries to import suite.py
        # without building site-packages first. Since the reporting modules are
        # only used in this function, move the imports here avoid the
        # requirement of building site packages to use other functions in this
        # module.
        from autotest_lib.server.cros.dynamic_suite import reporting_utils

        # Try to merge with bug template in test control file.
        template = reporting_utils.BugTemplate(bug_template)
        try:
            test_data = self._jobs_to_tests[result.id]
            return template.finalize_bug_template(
                    test_data.bug_template)
        except AttributeError:
            # Test control file does not have bug template defined.
            return template.bug_template
        except reporting_utils.InvalidBugTemplateException as e:
            logging.error('Merging bug templates failed with '
                          'error: %s An empty bug template will '
                          'be used.', e)
            return {}


    def _get_test_bug(self, result):
        """Get TestBug for the given result.

        @param result: Status instance for a test job.
        @returns: TestBug instance.
        """
        # reporting modules have dependency on external packages, e.g., httplib2
        # Such dependency can cause issue to any module tries to import suite.py
        # without building site-packages first. Since the reporting modules are
        # only used in this function, move the imports here avoid the
        # requirement of building site packages to use other functions in this
        # module.
        from autotest_lib.server.cros.dynamic_suite import reporting

        job_views = self._tko.run('get_detailed_test_views',
                                  afe_job_id=result.id)
        return reporting.TestBug(self._cros_build,
                site_utils.get_chrome_version(job_views),
                self._tag,
                result)


    @property
    def _should_file_bugs(self):
        """Return whether bugs should be filed.

        @returns: bool
        """
        # File bug when failure is one of the _FILE_BUG_SUITES,
        # otherwise send an email to the owner anc cc.
        return self._tag in _FILE_BUG_SUITES


    def _file_bug(self, result, bug_reporter, bug_template):
        """File a bug for a test job result.

        @param result: Status instance for job.
        @param bug_reporter: Reporter instance for reporting bugs.
        @param bug_template: A template dictionary specifying the default bug
                             filing options for failures in this suite.
        """
        bug_id, bug_count = bug_reporter.report(
                self._get_test_bug(result),
                self._get_bug_template(result, bug_template))

        # We use keyvals to communicate bugs filed with run_suite.
        if bug_id is not None:
            bug_keyvals = tools.create_bug_keyvals(
                    result.id, result.test_name,
                    (bug_id, bug_count))
            try:
                utils.write_keyval(self._results_dir,
                                   bug_keyvals)
            except ValueError:
                logging.error('Unable to log bug keyval for:%s',
                              result.test_name)


    def abort(self):
        """
        Abort all scheduled test jobs.
        """
        if self._jobs:
            job_ids = [job.id for job in self._jobs]
            self._afe.run('abort_host_queue_entries', job__id__in=job_ids)


    def _remember_job_keyval(self, job):
        """
        Record provided job as a suite job keyval, for later referencing.

        @param job: some representation of a job that has the attributes:
                    id, test_name, and owner
        """
        if self._results_dir and job.id and job.owner and job.test_name:
            job_id_owner = '%s-%s' % (job.id, job.owner)
            logging.debug('Adding job keyval for %s=%s',
                          job.test_name, job_id_owner)
            utils.write_keyval(
                self._results_dir,
                {hashlib.md5(job.test_name).hexdigest(): job_id_owner})


    @staticmethod
    def _find_all_tests(cf_getter, suite_name='', add_experimental=False,
                        forgiving_parser=True, run_prod_code=False,
                        test_args=None):
        """
        Function to scan through all tests and find all tests.

        When this method is called with a file system ControlFileGetter, or
        enable_controls_in_batch is set as false, this function will looks at
        control files returned by cf_getter.get_control_file_list() for tests.

        If cf_getter is a File system ControlFileGetter, it performs a full
        parse of the root directory associated with the getter. This is the
        case when it's invoked from suite_preprocessor.

        If cf_getter is a devserver getter it looks up the suite_name in a
        suite to control file map generated at build time, and parses the
        relevant control files alone. This lookup happens on the devserver,
        so as far as this method is concerned, both cases are equivalent. If
        enable_controls_in_batch is switched on, this function will call
        cf_getter.get_suite_info() to get a dict of control files and contents
        in batch.

        @param cf_getter: a control_file_getter.ControlFileGetter used to list
               and fetch the content of control files
        @param suite_name: If specified, this method will attempt to restrain
                           the search space to just this suite's control files.
        @param add_experimental: add tests with experimental attribute set.
        @param forgiving_parser: If False, will raise ControlVariableExceptions
                                 if any are encountered when parsing control
                                 files. Note that this can raise an exception
                                 for syntax errors in unrelated files, because
                                 we parse them before applying the predicate.
        @param run_prod_code: If true, the suite will run the test code that
                              lives in prod aka the test code currently on the
                              lab servers by disabling SSP for the discovered
                              tests.
        @param test_args: A dict of args to be seeded in test control file under
                          the name |args_dict|.

        @raises ControlVariableException: If forgiving_parser is False and there
                                          is a syntax error in a control file.

        @returns a dictionary of ControlData objects that based on given
                 parameters.
        """
        logging.debug('Getting control file list for suite: %s', suite_name)
        tests = {}
        use_batch = (ENABLE_CONTROLS_IN_BATCH and hasattr(
                cf_getter, '_dev_server'))
        if use_batch:
            suite_info = cf_getter.get_suite_info(suite_name=suite_name)
            files = suite_info.keys()
        else:
            files = cf_getter.get_control_file_list(suite_name=suite_name)


        logging.debug('Parsing control files ...')
        matcher = re.compile(r'[^/]+/(deps|profilers)/.+')
        for file in filter(lambda f: not matcher.match(f), files):
            if use_batch:
                text = suite_info[file]
            else:
                text = cf_getter.get_control_file_contents(file)
            # Seed test_args into the control file.
            if test_args:
                text = tools.inject_vars(test_args, text)
            try:
                found_test = control_data.parse_control_string(
                        text, raise_warnings=True, path=file)
                if not add_experimental and found_test.experimental:
                    continue
                found_test.text = text
                if run_prod_code:
                    found_test.require_ssp = False
                tests[file] = found_test
            except control_data.ControlVariableException, e:
                if not forgiving_parser:
                    msg = "Failed parsing %s\n%s" % (file, e)
                    raise control_data.ControlVariableException(msg)
                logging.warning("Skipping %s\n%s", file, e)
            except Exception, e:
                logging.error("Bad %s\n%s", file, e)
        return tests


    @classmethod
    def find_and_parse_tests(cls, cf_getter, predicate, suite_name='',
                             add_experimental=False, forgiving_parser=True,
                             run_prod_code=False, test_args=None):
        """
        Function to scan through all tests and find eligible tests.

        Search through all tests based on given cf_getter, suite_name,
        add_experimental and forgiving_parser, return the tests that match
        given predicate.

        @param cf_getter: a control_file_getter.ControlFileGetter used to list
               and fetch the content of control files
        @param predicate: a function that should return True when run over a
               ControlData representation of a control file that should be in
               this Suite.
        @param suite_name: If specified, this method will attempt to restrain
                           the search space to just this suite's control files.
        @param add_experimental: add tests with experimental attribute set.
        @param forgiving_parser: If False, will raise ControlVariableExceptions
                                 if any are encountered when parsing control
                                 files. Note that this can raise an exception
                                 for syntax errors in unrelated files, because
                                 we parse them before applying the predicate.
        @param run_prod_code: If true, the suite will run the test code that
                              lives in prod aka the test code currently on the
                              lab servers by disabling SSP for the discovered
                              tests.
        @param test_args: A dict of args to be seeded in test control file.

        @raises ControlVariableException: If forgiving_parser is False and there
                                          is a syntax error in a control file.

        @return list of ControlData objects that should be run, with control
                file text added in |text| attribute. Results are sorted based
                on the TIME setting in control file, slowest test comes first.
        """
        tests = cls._find_all_tests(cf_getter, suite_name, add_experimental,
                                    forgiving_parser,
                                    run_prod_code=run_prod_code,
                                    test_args=test_args)
        logging.debug('Parsed %s control files.', len(tests))
        tests = [test for test in tests.itervalues() if predicate(test)]
        tests.sort(key=lambda t:
                   control_data.ControlData.get_test_time_index(t.time),
                   reverse=True)
        return tests


    @classmethod
    def find_possible_tests(cls, cf_getter, predicate, suite_name='', count=10):
        """
        Function to scan through all tests and find possible tests.

        Search through all tests based on given cf_getter, suite_name,
        add_experimental and forgiving_parser. Use the given predicate to
        calculate the similarity and return the top 10 matches.

        @param cf_getter: a control_file_getter.ControlFileGetter used to list
               and fetch the content of control files
        @param predicate: a function that should return a tuple of (name, ratio)
               when run over a ControlData representation of a control file that
               should be in this Suite. `name` is the key to be compared, e.g.,
               a suite name or test name. `ratio` is a value between [0,1]
               indicating the similarity of `name` and the value to be compared.
        @param suite_name: If specified, this method will attempt to restrain
                           the search space to just this suite's control files.
        @param count: Number of suggestions to return, default to 10.

        @return list of top names that similar to the given test, sorted by
                match ratio.
        """
        tests = cls._find_all_tests(cf_getter, suite_name,
                                    add_experimental=True,
                                    forgiving_parser=True)
        logging.debug('Parsed %s control files.', len(tests))
        similarities = {}
        for test in tests.itervalues():
            ratios = predicate(test)
            # Some predicates may return a list of tuples, e.g.,
            # name_in_tag_similarity_predicate. Convert all returns to a list.
            if not isinstance(ratios, list):
                ratios = [ratios]
            for name, ratio in ratios:
                similarities[name] = ratio
        return [s[0] for s in
                sorted(similarities.items(), key=operator.itemgetter(1),
                       reverse=True)][:count]


def _is_nonexistent_board_error(e):
    """Return True if error is caused by nonexistent board label.

    As of this writing, the particular case we want looks like this:

     1) e.problem_keys is a dictionary
     2) e.problem_keys['meta_hosts'] exists as the only key
        in the dictionary.
     3) e.problem_keys['meta_hosts'] matches this pattern:
        "Label "board:.*" not found"

    We check for conditions 1) and 2) on the
    theory that they're relatively immutable.
    We don't check condition 3) because it seems
    likely to be a maintenance burden, and for the
    times when we're wrong, being right shouldn't
    matter enough (we _hope_).

    @param e: proxy.ValidationError instance
    @returns: boolean
    """
    return (isinstance(e.problem_keys, dict)
            and len(e.problem_keys) == 1
            and 'meta_hosts' in e.problem_keys)
