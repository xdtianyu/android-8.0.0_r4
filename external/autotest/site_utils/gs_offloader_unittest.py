# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import __builtin__
import Queue
import base64
import datetime
import logging
import os
import shutil
import signal
import stat
import sys
import tempfile
import time
import unittest

import mox

import common
from autotest_lib.client.common_lib import global_config, site_utils
from autotest_lib.client.common_lib import time_utils
from autotest_lib.client.common_lib import utils
from autotest_lib.site_utils import gs_offloader
from autotest_lib.site_utils import job_directories
from autotest_lib.tko import models

# Test value to use for `days_old`, if nothing else is required.
_TEST_EXPIRATION_AGE = 7

# When constructing sample time values for testing expiration,
# allow this many seconds between the expiration time and the
# current time.
_MARGIN_SECS = 10.0


def _get_options(argv):
    """Helper function to exercise command line parsing.

    @param argv Value of sys.argv to be parsed.

    """
    sys.argv = ['bogus.py'] + argv
    return gs_offloader.parse_options()


def is_fifo(path):
  """Determines whether a path is a fifo."""
  return stat.S_ISFIFO(os.lstat(path).st_mode)


class OffloaderOptionsTests(mox.MoxTestBase):
    """Tests for the `Offloader` constructor.

    Tests that offloader instance fields are set as expected
    for given command line options.

    """

    _REGULAR_ONLY = set([job_directories.RegularJobDirectory])
    _SPECIAL_ONLY = set([job_directories.SpecialJobDirectory])
    _BOTH = _REGULAR_ONLY | _SPECIAL_ONLY


    def setUp(self):
        super(OffloaderOptionsTests, self).setUp()
        self.mox.StubOutWithMock(utils, 'get_offload_gsuri')
        gs_offloader.GS_OFFLOADING_ENABLED = True
        gs_offloader.GS_OFFLOADER_MULTIPROCESSING = False


    def _mock_get_offload_func(self, is_moblab, multiprocessing=False,
                               pubsub_topic=None, delete_age=0):
        """Mock the process of getting the offload_dir function."""
        if is_moblab:
            expected_gsuri = '%sresults/%s/%s/' % (
                    global_config.global_config.get_config_value(
                            'CROS', 'image_storage_server'),
                    'Fa:ke:ma:c0:12:34', 'rand0m-uu1d')
        else:
            expected_gsuri = utils.DEFAULT_OFFLOAD_GSURI
        utils.get_offload_gsuri().AndReturn(expected_gsuri)
        offload_func = gs_offloader.get_offload_dir_func(expected_gsuri,
            multiprocessing, delete_age, pubsub_topic)
        self.mox.StubOutWithMock(gs_offloader, 'get_offload_dir_func')
        gs_offloader.get_offload_dir_func(expected_gsuri, multiprocessing,
            delete_age, pubsub_topic).AndReturn(offload_func)
        self.mox.ReplayAll()
        return offload_func


    def test_process_no_options(self):
        """Test default offloader options."""
        offload_func = self._mock_get_offload_func(False)
        offloader = gs_offloader.Offloader(_get_options([]))
        self.assertEqual(set(offloader._jobdir_classes),
                         self._REGULAR_ONLY)
        self.assertEqual(offloader._processes, 1)
        self.assertEqual(offloader._offload_func,
                         offload_func)
        self.assertEqual(offloader._upload_age_limit, 0)
        self.assertEqual(offloader._delete_age_limit, 0)


    def test_process_all_option(self):
        """Test offloader handling for the --all option."""
        offload_func = self._mock_get_offload_func(False)
        offloader = gs_offloader.Offloader(_get_options(['--all']))
        self.assertEqual(set(offloader._jobdir_classes), self._BOTH)
        self.assertEqual(offloader._processes, 1)
        self.assertEqual(offloader._offload_func,
                         offload_func)
        self.assertEqual(offloader._upload_age_limit, 0)
        self.assertEqual(offloader._delete_age_limit, 0)


    def test_process_hosts_option(self):
        """Test offloader handling for the --hosts option."""
        offload_func = self._mock_get_offload_func(False)
        offloader = gs_offloader.Offloader(
                _get_options(['--hosts']))
        self.assertEqual(set(offloader._jobdir_classes),
                         self._SPECIAL_ONLY)
        self.assertEqual(offloader._processes, 1)
        self.assertEqual(offloader._offload_func,
                         offload_func)
        self.assertEqual(offloader._upload_age_limit, 0)
        self.assertEqual(offloader._delete_age_limit, 0)


    def test_parallelism_option(self):
        """Test offloader handling for the --parallelism option."""
        offload_func = self._mock_get_offload_func(False)
        offloader = gs_offloader.Offloader(
                _get_options(['--parallelism', '2']))
        self.assertEqual(set(offloader._jobdir_classes),
                         self._REGULAR_ONLY)
        self.assertEqual(offloader._processes, 2)
        self.assertEqual(offloader._offload_func,
                         offload_func)
        self.assertEqual(offloader._upload_age_limit, 0)
        self.assertEqual(offloader._delete_age_limit, 0)


    def test_delete_only_option(self):
        """Test offloader handling for the --delete_only option."""
        offloader = gs_offloader.Offloader(
                _get_options(['--delete_only']))
        self.assertEqual(set(offloader._jobdir_classes),
                         self._REGULAR_ONLY)
        self.assertEqual(offloader._processes, 1)
        self.assertEqual(offloader._offload_func,
                         gs_offloader.delete_files)
        self.assertEqual(offloader._upload_age_limit, 0)
        self.assertEqual(offloader._delete_age_limit, 0)
        self.assertIsNone(offloader._pubsub_topic)


    def test_days_old_option(self):
        """Test offloader handling for the --days_old option."""
        offload_func = self._mock_get_offload_func(False, delete_age=7)
        offloader = gs_offloader.Offloader(
                _get_options(['--days_old', '7']))
        self.assertEqual(set(offloader._jobdir_classes),
                         self._REGULAR_ONLY)
        self.assertEqual(offloader._processes, 1)
        self.assertEqual(offloader._offload_func,
                         offload_func)
        self.assertEqual(offloader._upload_age_limit, 7)
        self.assertEqual(offloader._delete_age_limit, 7)


    def test_moblab_gsuri_generation(self):
        """Test offloader construction for Moblab."""
        offload_func = self._mock_get_offload_func(True)
        offloader = gs_offloader.Offloader(_get_options([]))
        self.assertEqual(set(offloader._jobdir_classes),
                         self._REGULAR_ONLY)
        self.assertEqual(offloader._processes, 1)
        self.assertEqual(offloader._offload_func,
                         offload_func)
        self.assertEqual(offloader._upload_age_limit, 0)
        self.assertEqual(offloader._delete_age_limit, 0)


    def test_globalconfig_offloading_flag(self):
        """Test enabling of --delete_only via global_config."""
        gs_offloader.GS_OFFLOADING_ENABLED = False
        offloader = gs_offloader.Offloader(
                _get_options([]))
        self.assertEqual(offloader._offload_func,
                         gs_offloader.delete_files)

    def test_offloader_multiprocessing_flag_set(self):
        """Test multiprocessing is set."""
        offload_func = self._mock_get_offload_func(True, True)
        offloader = gs_offloader.Offloader(_get_options(['-m']))
        self.assertEqual(offloader._offload_func,
                         offload_func)
        self.mox.VerifyAll()

    def test_offloader_multiprocessing_flag_not_set_default_false(self):
        """Test multiprocessing is set."""
        gs_offloader.GS_OFFLOADER_MULTIPROCESSING = False
        offload_func = self._mock_get_offload_func(True, False)
        offloader = gs_offloader.Offloader(_get_options([]))
        self.assertEqual(offloader._offload_func,
                         offload_func)
        self.mox.VerifyAll()

    def test_offloader_multiprocessing_flag_not_set_default_true(self):
        """Test multiprocessing is set."""
        gs_offloader.GS_OFFLOADER_MULTIPROCESSING = True
        offload_func = self._mock_get_offload_func(True, True)
        offloader = gs_offloader.Offloader(_get_options([]))
        self.assertEqual(offloader._offload_func,
                         offload_func)
        self.mox.VerifyAll()

    def test_offloader_pubsub_topic_not_set(self):
        """Test multiprocessing is set."""
        offload_func = self._mock_get_offload_func(True, False)
        offloader = gs_offloader.Offloader(_get_options([]))
        self.assertEqual(offloader._offload_func,
                         offload_func)
        self.mox.VerifyAll()

    def test_offloader_pubsub_topic_set(self):
        """Test multiprocessing is set."""
        offload_func = self._mock_get_offload_func(True, False, 'test-topic')
        offloader = gs_offloader.Offloader(_get_options(['-t', 'test-topic']))
        self.assertEqual(offloader._offload_func,
                         offload_func)
        self.mox.VerifyAll()


def _make_timestamp(age_limit, is_expired):
    """Create a timestamp for use by `job_directories.is_job_expired()`.

    The timestamp will meet the syntactic requirements for
    timestamps used as input to `is_job_expired()`.  If
    `is_expired` is true, the timestamp will be older than
    `age_limit` days before the current time; otherwise, the
    date will be younger.

    @param age_limit    The number of days before expiration of the
                        target timestamp.
    @param is_expired   Whether the timestamp should be expired
                        relative to `age_limit`.

    """
    seconds = -_MARGIN_SECS
    if is_expired:
        seconds = -seconds
    delta = datetime.timedelta(days=age_limit, seconds=seconds)
    reference_time = datetime.datetime.now() - delta
    return reference_time.strftime(time_utils.TIME_FMT)


class JobExpirationTests(unittest.TestCase):
    """Tests to exercise `job_directories.is_job_expired()`."""

    def test_expired(self):
        """Test detection of an expired job."""
        timestamp = _make_timestamp(_TEST_EXPIRATION_AGE, True)
        self.assertTrue(
            job_directories.is_job_expired(
                _TEST_EXPIRATION_AGE, timestamp))


    def test_alive(self):
        """Test detection of a job that's not expired."""
        # N.B.  This test may fail if its run time exceeds more than
        # about _MARGIN_SECS seconds.
        timestamp = _make_timestamp(_TEST_EXPIRATION_AGE, False)
        self.assertFalse(
            job_directories.is_job_expired(
                _TEST_EXPIRATION_AGE, timestamp))


class _MockJobDirectory(job_directories._JobDirectory):
    """Subclass of `_JobDirectory` used as a helper for tests."""

    GLOB_PATTERN = '[0-9]*-*'


    def __init__(self, resultsdir):
        """Create new job in initial state."""
        super(_MockJobDirectory, self).__init__(resultsdir)
        self._timestamp = None
        self.queue_args = [resultsdir, os.path.dirname(resultsdir), self._timestamp]


    def get_timestamp_if_finished(self):
        return self._timestamp


    def set_finished(self, days_old):
        """Make this job appear to be finished.

        After calling this function, calls to `enqueue_offload()`
        will find this job as finished, but not expired and ready
        for offload.  Note that when `days_old` is 0,
        `enqueue_offload()` will treat a finished job as eligible
        for offload.

        @param days_old The value of the `days_old` parameter that
                        will be passed to `enqueue_offload()` for
                        testing.

        """
        self._timestamp = _make_timestamp(days_old, False)
        self.queue_args[2] = self._timestamp


    def set_expired(self, days_old):
        """Make this job eligible to be offloaded.

        After calling this function, calls to `offload` will attempt
        to offload this job.

        @param days_old The value of the `days_old` parameter that
                        will be passed to `enqueue_offload()` for
                        testing.

        """
        self._timestamp = _make_timestamp(days_old, True)
        self.queue_args[2] = self._timestamp


    def set_incomplete(self):
        """Make this job appear to have failed offload just once."""
        self._offload_count += 1
        self._first_offload_start = time.time()
        if not os.path.isdir(self._dirname):
            os.mkdir(self._dirname)


    def set_reportable(self):
        """Make this job be reportable."""
        self.set_incomplete()
        self._offload_count += 1


    def set_complete(self):
        """Make this job be completed."""
        self._offload_count += 1
        if os.path.isdir(self._dirname):
            os.rmdir(self._dirname)


    def process_gs_instructions(self):
        """Always still offload the job directory."""
        return True


class CommandListTests(unittest.TestCase):
    """Tests for `get_cmd_list()`."""

    def _command_list_assertions(self, job, use_rsync=True, multi=False):
        """Call `get_cmd_list()` and check the return value.

        Check the following assertions:
          * The command name (argv[0]) is 'gsutil'.
          * '-m' option (argv[1]) is on when the argument, multi, is True.
          * The arguments contain the 'cp' subcommand.
          * The next-to-last argument (the source directory) is the
            job's `queue_args[0]`.
          * The last argument (the destination URL) is the job's
            'queue_args[1]'.

        @param job A job with properly calculated arguments to
                   `get_cmd_list()`
        @param use_rsync True when using 'rsync'. False when using 'cp'.
        @param multi True when using '-m' option for gsutil.

        """
        test_bucket_uri = 'gs://a-test-bucket'

        gs_offloader.USE_RSYNC_ENABLED = use_rsync

        command = gs_offloader.get_cmd_list(
                multi, job.queue_args[0],
                os.path.join(test_bucket_uri, job.queue_args[1]))

        self.assertEqual(command[0], 'gsutil')
        if multi:
            self.assertEqual(command[1], '-m')
        self.assertEqual(command[-2], job.queue_args[0])

        if use_rsync:
            self.assertTrue('rsync' in command)
            self.assertEqual(command[-1],
                             os.path.join(test_bucket_uri, job.queue_args[0]))
        else:
            self.assertTrue('cp' in command)
            self.assertEqual(command[-1],
                             os.path.join(test_bucket_uri, job.queue_args[1]))


    def test_get_cmd_list_regular(self):
        """Test `get_cmd_list()` as for a regular job."""
        job = _MockJobDirectory('118-debug')
        self._command_list_assertions(job)


    def test_get_cmd_list_special(self):
        """Test `get_cmd_list()` as for a special job."""
        job = _MockJobDirectory('hosts/host1/118-reset')
        self._command_list_assertions(job)


    def test_get_cmd_list_regular_no_rsync(self):
        """Test `get_cmd_list()` as for a regular job."""
        job = _MockJobDirectory('118-debug')
        self._command_list_assertions(job, use_rsync=False)


    def test_get_cmd_list_special_no_rsync(self):
        """Test `get_cmd_list()` as for a special job."""
        job = _MockJobDirectory('hosts/host1/118-reset')
        self._command_list_assertions(job, use_rsync=False)


    def test_get_cmd_list_regular_multi(self):
        """Test `get_cmd_list()` as for a regular job with True multi."""
        job = _MockJobDirectory('118-debug')
        self._command_list_assertions(job, multi=True)


    def test_get_cmd_list_special_multi(self):
        """Test `get_cmd_list()` as for a special job with True multi."""
        job = _MockJobDirectory('hosts/host1/118-reset')
        self._command_list_assertions(job, multi=True)


class PubSubTest(mox.MoxTestBase):
    """Test the test result notifcation data structure."""

    def test_create_test_result_notification(self):
        """Tests the test result notification message."""
        self.mox.StubOutWithMock(site_utils, 'get_moblab_id')
        self.mox.StubOutWithMock(site_utils,
                                 'get_default_interface_mac_address')
        site_utils.get_default_interface_mac_address().AndReturn(
            '1c:dc:d1:11:01:e1')
        site_utils.get_moblab_id().AndReturn(
            'c8386d92-9ad1-11e6-80f5-111111111111')
        self.mox.ReplayAll()
        msg = gs_offloader._create_test_result_notification(
                'gs://test_bucket', '123-moblab')
        self.assertEquals(base64.b64encode(
            gs_offloader.NEW_TEST_RESULT_MESSAGE), msg['data'])
        self.assertEquals(
            gs_offloader.NOTIFICATION_VERSION,
            msg['attributes'][gs_offloader.NOTIFICATION_ATTR_VERSION])
        self.assertEquals(
            '1c:dc:d1:11:01:e1',
            msg['attributes'][gs_offloader.NOTIFICATION_ATTR_MOBLAB_MAC])
        self.assertEquals(
            'c8386d92-9ad1-11e6-80f5-111111111111',
            msg['attributes'][gs_offloader.NOTIFICATION_ATTR_MOBLAB_ID])
        self.assertEquals(
            'gs://test_bucket/123-moblab',
            msg['attributes'][gs_offloader.NOTIFICATION_ATTR_GCS_URI])
        self.mox.VerifyAll()


class _MockJob(object):
    """Class to mock the return value of `AFE.get_jobs()`."""
    def __init__(self, created):
        self.created_on = created


class _MockHostQueueEntry(object):
    """Class to mock the return value of `AFE.get_host_queue_entries()`."""
    def __init__(self, finished):
        self.finished_on = finished


class _MockSpecialTask(object):
    """Class to mock the return value of `AFE.get_special_tasks()`."""
    def __init__(self, finished):
        self.time_finished = finished


class JobDirectorySubclassTests(mox.MoxTestBase):
    """Test specific to RegularJobDirectory and SpecialJobDirectory.

    This provides coverage for the implementation in both
    RegularJobDirectory and SpecialJobDirectory.

    """

    def setUp(self):
        super(JobDirectorySubclassTests, self).setUp()
        self.mox.StubOutWithMock(job_directories._AFE, 'get_jobs')
        self.mox.StubOutWithMock(job_directories._AFE,
                                 'get_host_queue_entries')
        self.mox.StubOutWithMock(job_directories._AFE,
                                 'get_special_tasks')


    def test_regular_job_fields(self):
        """Test the constructor for `RegularJobDirectory`.

        Construct a regular job, and assert that the `_dirname`
        and `_id` attributes are set as expected.

        """
        resultsdir = '118-fubar'
        job = job_directories.RegularJobDirectory(resultsdir)
        self.assertEqual(job._dirname, resultsdir)
        self.assertEqual(job._id, 118)


    def test_special_job_fields(self):
        """Test the constructor for `SpecialJobDirectory`.

        Construct a special job, and assert that the `_dirname`
        and `_id` attributes are set as expected.

        """
        destdir = 'hosts/host1'
        resultsdir = destdir + '/118-reset'
        job = job_directories.SpecialJobDirectory(resultsdir)
        self.assertEqual(job._dirname, resultsdir)
        self.assertEqual(job._id, 118)


    def _check_finished_job(self, jobtime, hqetimes, expected):
        """Mock and test behavior of a finished job.

        Initialize the mocks for a call to
        `get_timestamp_if_finished()`, then simulate one call.
        Assert that the returned timestamp matches the passed
        in expected value.

        @param jobtime Time used to construct a _MockJob object.
        @param hqetimes List of times used to construct
                        _MockHostQueueEntry objects.
        @param expected Expected time to be returned by
                        get_timestamp_if_finished

        """
        job = job_directories.RegularJobDirectory('118-fubar')
        job_directories._AFE.get_jobs(
                id=job._id, finished=True).AndReturn(
                        [_MockJob(jobtime)])
        job_directories._AFE.get_host_queue_entries(
                finished_on__isnull=False,
                job_id=job._id).AndReturn(
                        [_MockHostQueueEntry(t) for t in hqetimes])
        self.mox.ReplayAll()
        self.assertEqual(expected, job.get_timestamp_if_finished())
        self.mox.VerifyAll()


    def test_finished_regular_job(self):
        """Test getting the timestamp for a finished regular job.

        Tests the return value for
        `RegularJobDirectory.get_timestamp_if_finished()` when
        the AFE indicates the job is finished.

        """
        created_timestamp = _make_timestamp(1, True)
        hqe_timestamp = _make_timestamp(0, True)
        self._check_finished_job(created_timestamp,
                                 [hqe_timestamp],
                                 hqe_timestamp)


    def test_finished_regular_job_multiple_hqes(self):
        """Test getting the timestamp for a regular job with multiple hqes.

        Tests the return value for
        `RegularJobDirectory.get_timestamp_if_finished()` when
        the AFE indicates the job is finished and the job has multiple host
        queue entries.

        Tests that the returned timestamp is the latest timestamp in
        the list of HQEs, regardless of the returned order.

        """
        created_timestamp = _make_timestamp(2, True)
        older_hqe_timestamp = _make_timestamp(1, True)
        newer_hqe_timestamp = _make_timestamp(0, True)
        hqe_list = [older_hqe_timestamp,
                    newer_hqe_timestamp]
        self._check_finished_job(created_timestamp,
                                 hqe_list,
                                 newer_hqe_timestamp)
        self.mox.ResetAll()
        hqe_list.reverse()
        self._check_finished_job(created_timestamp,
                                 hqe_list,
                                 newer_hqe_timestamp)


    def test_finished_regular_job_null_finished_times(self):
        """Test getting the timestamp for an aborted regular job.

        Tests the return value for
        `RegularJobDirectory.get_timestamp_if_finished()` when
        the AFE indicates the job is finished and the job has aborted host
        queue entries.

        """
        timestamp = _make_timestamp(0, True)
        self._check_finished_job(timestamp, [], timestamp)


    def test_unfinished_regular_job(self):
        """Test getting the timestamp for an unfinished regular job.

        Tests the return value for
        `RegularJobDirectory.get_timestamp_if_finished()` when
        the AFE indicates the job is not finished.

        """
        job = job_directories.RegularJobDirectory('118-fubar')
        job_directories._AFE.get_jobs(
                id=job._id, finished=True).AndReturn([])
        self.mox.ReplayAll()
        self.assertIsNone(job.get_timestamp_if_finished())
        self.mox.VerifyAll()


    def test_finished_special_job(self):
        """Test getting the timestamp for a finished special job.

        Tests the return value for
        `SpecialJobDirectory.get_timestamp_if_finished()` when
        the AFE indicates the job is finished.

        """
        job = job_directories.SpecialJobDirectory(
                'hosts/host1/118-reset')
        timestamp = _make_timestamp(0, True)
        job_directories._AFE.get_special_tasks(
                id=job._id, is_complete=True).AndReturn(
                    [_MockSpecialTask(timestamp)])
        self.mox.ReplayAll()
        self.assertEqual(timestamp,
                         job.get_timestamp_if_finished())
        self.mox.VerifyAll()


    def test_unfinished_special_job(self):
        """Test getting the timestamp for an unfinished special job.

        Tests the return value for
        `SpecialJobDirectory.get_timestamp_if_finished()` when
        the AFE indicates the job is not finished.

        """
        job = job_directories.SpecialJobDirectory(
                'hosts/host1/118-reset')
        job_directories._AFE.get_special_tasks(
                id=job._id, is_complete=True).AndReturn([])
        self.mox.ReplayAll()
        self.assertIsNone(job.get_timestamp_if_finished())
        self.mox.VerifyAll()


class _TempResultsDirTestBase(mox.MoxTestBase):
    """Base class for tests using a temporary results directory."""

    REGULAR_JOBLIST = [
        '111-fubar', '112-fubar', '113-fubar', '114-snafu']
    HOST_LIST = ['host1', 'host2', 'host3']
    SPECIAL_JOBLIST = [
        'hosts/host1/333-reset', 'hosts/host1/334-reset',
        'hosts/host2/444-reset', 'hosts/host3/555-reset']


    def setUp(self):
        super(_TempResultsDirTestBase, self).setUp()
        self._resultsroot = tempfile.mkdtemp()
        self._cwd = os.getcwd()
        os.chdir(self._resultsroot)


    def tearDown(self):
        os.chdir(self._cwd)
        shutil.rmtree(self._resultsroot)
        super(_TempResultsDirTestBase, self).tearDown()


    def make_job(self, jobdir):
        """Create a job with results in `self._resultsroot`.

        @param jobdir Name of the subdirectory to be created in
                      `self._resultsroot`.

        """
        os.mkdir(jobdir)
        return _MockJobDirectory(jobdir)


    def make_job_hierarchy(self):
        """Create a sample hierarchy of job directories.

        `self.REGULAR_JOBLIST` is a list of directories for regular
        jobs to be created; `self.SPECIAL_JOBLIST` is a list of
        directories for special jobs to be created.

        """
        for d in self.REGULAR_JOBLIST:
            os.mkdir(d)
        hostsdir = 'hosts'
        os.mkdir(hostsdir)
        for host in self.HOST_LIST:
            os.mkdir(os.path.join(hostsdir, host))
        for d in self.SPECIAL_JOBLIST:
            os.mkdir(d)


class FailedOffloadsLogTest(_TempResultsDirTestBase):
    """Test the formatting of failed offloads log file."""
    # Below is partial sample of a failed offload log file.  This text is
    # deliberately hard-coded and then parsed to create the test data; the idea
    # is to make sure the actual text format will be reviewed by a human being.
    #
    # first offload      count  directory
    # --+----1----+----  ----+ ----+----1----+----2----+----3
    _SAMPLE_DIRECTORIES_REPORT = '''\
    =================== ======  ==============================
    2014-03-14 15:09:26      1  118-fubar
    2014-03-14 15:19:23      2  117-fubar
    2014-03-14 15:29:20      6  116-fubar
    2014-03-14 15:39:17     24  115-fubar
    2014-03-14 15:49:14    120  114-fubar
    2014-03-14 15:59:11    720  113-fubar
    2014-03-14 16:09:08   5040  112-fubar
    2014-03-14 16:19:05  40320  111-fubar
    '''

    def setUp(self):
        super(FailedOffloadsLogTest, self).setUp()
        self._offloader = gs_offloader.Offloader(_get_options([]))
        self._joblist = []
        for line in self._SAMPLE_DIRECTORIES_REPORT.split('\n')[1 : -1]:
            date_, time_, count, dir_ = line.split()
            job = _MockJobDirectory(dir_)
            job._offload_count = int(count)
            timestruct = time.strptime("%s %s" % (date_, time_),
                                       gs_offloader.FAILED_OFFLOADS_TIME_FORMAT)
            job._first_offload_start = time.mktime(timestruct)
            # enter the jobs in reverse order, to make sure we
            # test that the output will be sorted.
            self._joblist.insert(0, job)


    def assert_report_well_formatted(self, report_file):
        with open(report_file, 'r') as f:
            report_lines = f.read().split()

        for end_of_header_index in range(len(report_lines)):
            if report_lines[end_of_header_index].startswith('=='):
                break
        self.assertLess(end_of_header_index, len(report_lines),
                        'Failed to find end-of-header marker in the report')

        relevant_lines = report_lines[end_of_header_index:]
        expected_lines = self._SAMPLE_DIRECTORIES_REPORT.split()
        self.assertListEqual(relevant_lines, expected_lines)


    def test_failed_offload_log_format(self):
        """Trigger an e-mail report and check its contents."""
        log_file = os.path.join(self._resultsroot, 'failed_log')
        report = self._offloader._log_failed_jobs_locally(self._joblist,
                                                          log_file=log_file)
        self.assert_report_well_formatted(log_file)


    def test_failed_offload_file_overwrite(self):
        """Verify that we can saefly overwrite the log file."""
        log_file = os.path.join(self._resultsroot, 'failed_log')
        with open(log_file, 'w') as f:
            f.write('boohoohoo')
        report = self._offloader._log_failed_jobs_locally(self._joblist,
                                                          log_file=log_file)
        self.assert_report_well_formatted(log_file)


class OffloadDirectoryTests(_TempResultsDirTestBase):
    """Tests for `offload_dir()`."""

    def setUp(self):
        super(OffloadDirectoryTests, self).setUp()
        # offload_dir() logs messages; silence them.
        self._saved_loglevel = logging.getLogger().getEffectiveLevel()
        logging.getLogger().setLevel(logging.CRITICAL+1)
        self._job = self.make_job(self.REGULAR_JOBLIST[0])
        self.mox.StubOutWithMock(gs_offloader, 'get_cmd_list')
        self.mox.StubOutWithMock(signal, 'alarm')
        self.mox.StubOutWithMock(models.test, 'parse_job_keyval')


    def tearDown(self):
        logging.getLogger().setLevel(self._saved_loglevel)
        super(OffloadDirectoryTests, self).tearDown()

    def _mock_upload_testresult_files(self):
        self.mox.StubOutWithMock(gs_offloader, 'upload_testresult_files')
        gs_offloader.upload_testresult_files(
                mox.IgnoreArg(),mox.IgnoreArg()).AndReturn(None)

    def _mock_create_marker_file(self):
        self.mox.StubOutWithMock(__builtin__, 'open')
        mock_marker_file = self.mox.CreateMock(file)
        open(mox.IgnoreArg(), 'a').AndReturn(mock_marker_file)
        mock_marker_file.close()


    def _mock_offload_dir_calls(self, command, queue_args,
                                marker_initially_exists=False,
                                marker_eventually_exists=True):
        """Mock out the calls needed by `offload_dir()`.

        This covers only the calls made when there is no timeout.

        @param command Command list to be returned by the mocked
                       call to `get_cmd_list()`.

        """
        self.mox.StubOutWithMock(os.path, 'isfile')
        os.path.isfile(mox.IgnoreArg()).AndReturn(marker_initially_exists)
        signal.alarm(gs_offloader.OFFLOAD_TIMEOUT_SECS)
        command.append(queue_args[0])
        gs_offloader.get_cmd_list(
                False, queue_args[0],
                '%s%s' % (utils.DEFAULT_OFFLOAD_GSURI,
                          queue_args[1])).AndReturn(command)
        self._mock_upload_testresult_files()
        signal.alarm(0)
        signal.alarm(0)
        os.path.isfile(mox.IgnoreArg()).AndReturn(marker_eventually_exists)


    def _run_offload_dir(self, should_succeed, delete_age):
        """Make one call to `offload_dir()`.

        The caller ensures all mocks are set up already.

        @param should_succeed True iff the call to `offload_dir()`
                              is expected to succeed and remove the
                              offloaded job directory.

        """
        self.mox.ReplayAll()
        gs_offloader.get_offload_dir_func(
                utils.DEFAULT_OFFLOAD_GSURI, False, delete_age)(
                        self._job.queue_args[0],
                        self._job.queue_args[1],
                        self._job.queue_args[2])
        self.mox.VerifyAll()
        self.assertEqual(not should_succeed,
                         os.path.isdir(self._job.queue_args[0]))


    def test_offload_success(self):
        """Test that `offload_dir()` can succeed correctly."""
        self._mock_offload_dir_calls(['test', '-d'],
                                     self._job.queue_args)
        self._mock_create_marker_file()
        self._run_offload_dir(True, 0)


    def test_offload_failure(self):
        """Test that `offload_dir()` can fail correctly."""
        self._mock_offload_dir_calls(['test', '!', '-d'],
                                     self._job.queue_args,
                                     marker_eventually_exists=False)
        self._run_offload_dir(False, 0)


    def test_offload_timeout_early(self):
        """Test that `offload_dir()` times out correctly.

        This test triggers timeout at the earliest possible moment,
        at the first call to set the timeout alarm.

        """
        self._mock_upload_testresult_files()
        signal.alarm(gs_offloader.OFFLOAD_TIMEOUT_SECS).AndRaise(
                        gs_offloader.TimeoutException('fubar'))
        signal.alarm(0)
        self._run_offload_dir(False, 0)


    def test_offload_timeout_late(self):
        """Test that `offload_dir()` times out correctly.

        This test triggers timeout at the latest possible moment, at
        the call to clear the timeout alarm.

        """
        signal.alarm(gs_offloader.OFFLOAD_TIMEOUT_SECS)
        gs_offloader.get_cmd_list(
                False, mox.IgnoreArg(), mox.IgnoreArg()).AndReturn(
                        ['test', '-d', self._job.queue_args[0]])
        self._mock_upload_testresult_files()
        signal.alarm(0).AndRaise(
                gs_offloader.TimeoutException('fubar'))
        signal.alarm(0)
        self._run_offload_dir(False, 0)


    def test_sanitize_dir(self):
        """Test that folder/file name with invalid character can be corrected.
        """
        results_folder = tempfile.mkdtemp()
        invalid_chars = '_'.join(gs_offloader.INVALID_GS_CHARS)
        invalid_files = []
        invalid_folder_name = 'invalid_name_folder_%s' % invalid_chars
        invalid_folder = os.path.join(
                results_folder,
                invalid_folder_name)
        invalid_files.append(os.path.join(
                invalid_folder,
                'invalid_name_file_%s' % invalid_chars))
        for r in gs_offloader.INVALID_GS_CHAR_RANGE:
            for c in range(r[0], r[1]+1):
                # NULL cannot be in file name.
                if c != 0:
                    invalid_files.append(os.path.join(
                            invalid_folder,
                            'invalid_name_file_%s' % chr(c)))
        good_folder =  os.path.join(results_folder, 'valid_name_folder')
        good_file = os.path.join(good_folder, 'valid_name_file')
        for folder in [invalid_folder, good_folder]:
            os.makedirs(folder)
        for f in invalid_files + [good_file]:
            with open(f, 'w'):
                pass
        # check that broken symlinks don't break sanitization
        symlink = os.path.join(invalid_folder, 'broken-link')
        os.symlink(os.path.join(results_folder, 'no-such-file'),
                   symlink)
        fifo1 = os.path.join(results_folder, 'test_fifo1')
        fifo2 = os.path.join(good_folder, 'test_fifo2')
        fifo3 = os.path.join(invalid_folder, 'test_fifo3')
        invalid_fifo4_name = 'test_fifo4_%s' % invalid_chars
        fifo4 = os.path.join(invalid_folder, invalid_fifo4_name)
        os.mkfifo(fifo1)
        os.mkfifo(fifo2)
        os.mkfifo(fifo3)
        os.mkfifo(fifo4)
        gs_offloader.sanitize_dir(results_folder)
        for _, dirs, files in os.walk(results_folder):
            for name in dirs + files:
                self.assertEqual(name, gs_offloader.get_sanitized_name(name))
                for c in name:
                    self.assertFalse(c in gs_offloader.INVALID_GS_CHARS)
                    for r in gs_offloader.INVALID_GS_CHAR_RANGE:
                        self.assertFalse(ord(c) >= r[0] and ord(c) <= r[1])
        self.assertTrue(os.path.exists(good_file))

        self.assertTrue(os.path.exists(fifo1))
        self.assertFalse(is_fifo(fifo1))
        self.assertTrue(os.path.exists(fifo2))
        self.assertFalse(is_fifo(fifo2))
        corrected_folder = os.path.join(
                results_folder,
                gs_offloader.get_sanitized_name(invalid_folder_name))
        corrected_fifo3 = os.path.join(
                corrected_folder,
                'test_fifo3')
        self.assertFalse(os.path.exists(fifo3))
        self.assertTrue(os.path.exists(corrected_fifo3))
        self.assertFalse(is_fifo(corrected_fifo3))
        corrected_fifo4 = os.path.join(
                corrected_folder,
                gs_offloader.get_sanitized_name(invalid_fifo4_name))
        self.assertFalse(os.path.exists(fifo4))
        self.assertTrue(os.path.exists(corrected_fifo4))
        self.assertFalse(is_fifo(corrected_fifo4))

        corrected_symlink = os.path.join(
                corrected_folder,
                'broken-link')
        self.assertFalse(os.path.lexists(symlink))
        self.assertTrue(os.path.exists(corrected_symlink))
        self.assertFalse(os.path.islink(corrected_symlink))
        shutil.rmtree(results_folder)


    def check_limit_file_count(self, is_test_job=True):
        """Test that folder with too many files can be compressed.

        @param is_test_job: True to check the method with test job result
                            folder. Set to False for special task folder.
        """
        results_folder = tempfile.mkdtemp()
        host_folder = os.path.join(
                results_folder,
                'lab1-host1' if is_test_job else 'hosts/lab1-host1/1-repair')
        debug_folder = os.path.join(host_folder, 'debug')
        sysinfo_folder = os.path.join(host_folder, 'sysinfo')
        for folder in [debug_folder, sysinfo_folder]:
            os.makedirs(folder)
            for i in range(10):
                with open(os.path.join(folder, str(i)), 'w') as f:
                    f.write('test')

        gs_offloader.MAX_FILE_COUNT = 100
        gs_offloader.limit_file_count(
                results_folder if is_test_job else host_folder)
        self.assertTrue(os.path.exists(sysinfo_folder))

        gs_offloader.MAX_FILE_COUNT = 10
        gs_offloader.limit_file_count(
                results_folder if is_test_job else host_folder)
        self.assertFalse(os.path.exists(sysinfo_folder))
        self.assertTrue(os.path.exists(sysinfo_folder + '.tgz'))
        self.assertTrue(os.path.exists(debug_folder))

        shutil.rmtree(results_folder)


    def test_limit_file_count(self):
        """Test that folder with too many files can be compressed.
        """
        self.check_limit_file_count(is_test_job=True)
        self.check_limit_file_count(is_test_job=False)


    def test_is_valid_result(self):
        """Test _is_valid_result."""
        release_build = 'veyron_minnie-cheets-release/R52-8248.0.0'
        pfq_build = 'cyan-cheets-android-pfq/R54-8623.0.0-rc1'
        trybot_build = 'trybot-samus-release/R54-8640.0.0-b5092'
        trybot_2_build = 'trybot-samus-pfq/R54-8640.0.0-b5092'
        release_2_build = 'test-trybot-release/R54-8640.0.0-b5092'
        self.assertTrue(gs_offloader._is_valid_result(
            release_build, gs_offloader.CTS_RESULT_PATTERN, 'arc-cts'))
        self.assertTrue(gs_offloader._is_valid_result(
            release_build, gs_offloader.CTS_RESULT_PATTERN, 'test_that_wrapper'))
        self.assertFalse(gs_offloader._is_valid_result(
            release_build, gs_offloader.CTS_RESULT_PATTERN, 'arc-bvt-cq'))
        self.assertTrue(gs_offloader._is_valid_result(
            release_build, gs_offloader.CTS_V2_RESULT_PATTERN, 'arc-gts'))
        self.assertFalse(gs_offloader._is_valid_result(
            None, gs_offloader.CTS_RESULT_PATTERN, 'arc-cts'))
        self.assertFalse(gs_offloader._is_valid_result(
            release_build, gs_offloader.CTS_RESULT_PATTERN, None))
        self.assertFalse(gs_offloader._is_valid_result(
            pfq_build, gs_offloader.CTS_RESULT_PATTERN, 'arc-cts'))
        self.assertFalse(gs_offloader._is_valid_result(
            trybot_build, gs_offloader.CTS_RESULT_PATTERN, 'arc-cts'))
        self.assertFalse(gs_offloader._is_valid_result(
            trybot_2_build, gs_offloader.CTS_RESULT_PATTERN, 'arc-cts'))
        self.assertTrue(gs_offloader._is_valid_result(
            release_2_build, gs_offloader.CTS_RESULT_PATTERN, 'arc-cts'))


    def create_results_folder(self):
        """Create CTS/GTS results folders."""
        results_folder = tempfile.mkdtemp()
        host_folder = os.path.join(results_folder, 'chromeos4-row9-rack11-host22')
        debug_folder = os.path.join(host_folder, 'debug')
        sysinfo_folder = os.path.join(host_folder, 'sysinfo')
        cts_result_folder = os.path.join(
                host_folder, 'cheets_CTS.android.dpi', 'results', 'cts-results')
        cts_v2_result_folder = os.path.join(host_folder,
                'cheets_CTS_N.CtsGraphicsTestCases', 'results', 'android-cts')
        gts_result_folder = os.path.join(
                host_folder, 'cheets_GTS.google.admin', 'results', 'android-gts')
        timestamp_str = '2016.04.28_01.41.44'
        timestamp_cts_folder = os.path.join(cts_result_folder, timestamp_str)
        timestamp_cts_v2_folder = os.path.join(cts_v2_result_folder, timestamp_str)
        timestamp_gts_folder = os.path.join(gts_result_folder, timestamp_str)

        # Test results in cts_result_folder with a different time-stamp.
        timestamp_str_2 = '2016.04.28_10.41.44'
        timestamp_cts_folder_2 = os.path.join(cts_result_folder, timestamp_str_2)

        for folder in [debug_folder, sysinfo_folder, cts_result_folder,
                       timestamp_cts_folder, timestamp_cts_folder_2,
                       timestamp_cts_v2_folder, timestamp_gts_folder]:
            os.makedirs(folder)

        path_pattern_pair = [(timestamp_cts_folder, gs_offloader.CTS_RESULT_PATTERN),
                             (timestamp_cts_folder_2, gs_offloader.CTS_RESULT_PATTERN),
                             (timestamp_cts_v2_folder, gs_offloader.CTS_V2_RESULT_PATTERN),
                             (timestamp_gts_folder, gs_offloader.CTS_V2_RESULT_PATTERN)]

        # Create timestamp.zip file_path.
        cts_zip_file = os.path.join(cts_result_folder, timestamp_str + '.zip')
        cts_zip_file_2 = os.path.join(cts_result_folder, timestamp_str_2 + '.zip')
        cts_v2_zip_file = os.path.join(cts_v2_result_folder, timestamp_str + '.zip')
        gts_zip_file = os.path.join(gts_result_folder, timestamp_str + '.zip')

        # Create xml file_path.
        cts_result_file = os.path.join(timestamp_cts_folder, 'testResult.xml')
        cts_result_file_2 = os.path.join(timestamp_cts_folder_2,
                                         'testResult.xml')
        gts_result_file = os.path.join(timestamp_gts_folder, 'test_result.xml')
        cts_v2_result_file = os.path.join(timestamp_cts_v2_folder,
                                         'test_result.xml')

        for file_path in [cts_zip_file, cts_zip_file_2, cts_v2_zip_file,
                          gts_zip_file, cts_result_file, cts_result_file_2,
                          gts_result_file, cts_v2_result_file]:
            with open(file_path, 'w') as f:
                f.write('test')

        return (results_folder, host_folder, path_pattern_pair)


    def test_upload_testresult_files(self):
        """Test upload_testresult_files."""
        results_folder, host_folder, path_pattern_pair = self.create_results_folder()

        self.mox.StubOutWithMock(gs_offloader, '_upload_files')
        gs_offloader._upload_files(
            mox.IgnoreArg(), mox.IgnoreArg(), mox.IgnoreArg(), False).AndReturn(
                ['test', '-d', host_folder])
        gs_offloader._upload_files(
            mox.IgnoreArg(), mox.IgnoreArg(), mox.IgnoreArg(), False).AndReturn(
                ['test', '-d', host_folder])
        gs_offloader._upload_files(
            mox.IgnoreArg(), mox.IgnoreArg(), mox.IgnoreArg(), False).AndReturn(
                ['test', '-d', host_folder])

        self.mox.ReplayAll()
        gs_offloader.upload_testresult_files(results_folder, False)
        self.mox.VerifyAll()
        shutil.rmtree(results_folder)


    def test_upload_files(self):
        """Test upload_files"""
        results_folder, host_folder, path_pattern_pair = self.create_results_folder()

        for path, pattern in path_pattern_pair:
            models.test.parse_job_keyval(mox.IgnoreArg()).AndReturn({
                'build': 'veyron_minnie-cheets-release/R52-8248.0.0',
                'parent_job_id': 'p_id',
                'suite': 'arc-cts'
            })

            gs_offloader.get_cmd_list(
                False, mox.IgnoreArg(), mox.IgnoreArg()).AndReturn(
                    ['test', '-d', path])
            gs_offloader.get_cmd_list(
                False, mox.IgnoreArg(), mox.IgnoreArg()).AndReturn(
                    ['test', '-d', path])

            self.mox.ReplayAll()
            gs_offloader._upload_files(host_folder, path, pattern, False)
            self.mox.VerifyAll()
            self.mox.ResetAll()

        shutil.rmtree(results_folder)


class JobDirectoryOffloadTests(_TempResultsDirTestBase):
    """Tests for `_JobDirectory.enqueue_offload()`.

    When testing with a `days_old` parameter of 0, we use
    `set_finished()` instead of `set_expired()`.  This causes the
    job's timestamp to be set in the future.  This is done so as
    to test that when `days_old` is 0, the job is always treated
    as eligible for offload, regardless of the timestamp's value.

    Testing covers the following assertions:
     A. Each time `enqueue_offload()` is called, a message that
        includes the job's directory name will be logged using
        `logging.debug()`, regardless of whether the job was
        enqueued.  Nothing else is allowed to be logged.
     B. If the job is not eligible to be offloaded,
        `get_failure_time()` and `get_failure_count()` are 0.
     C. If the job is not eligible for offload, nothing is
        enqueued in `queue`.
     D. When the job is offloaded, `get_failure_count()` increments
        each time.
     E. When the job is offloaded, the appropriate parameters are
        enqueued exactly once.
     F. The first time a job is offloaded, `get_failure_time()` is
        set to the current time.
     G. `get_failure_time()` only changes the first time that the
        job is offloaded.

    The test cases below are designed to exercise all of the
    meaningful state transitions at least once.

    """

    def setUp(self):
        super(JobDirectoryOffloadTests, self).setUp()
        self._job = self.make_job(self.REGULAR_JOBLIST[0])
        self._queue = Queue.Queue()


    def _offload_unexpired_job(self, days_old):
        """Make calls to `enqueue_offload()` for an unexpired job.

        This method tests assertions B and C that calling
        `enqueue_offload()` has no effect.

        """
        self.assertEqual(self._job.get_failure_count(), 0)
        self.assertEqual(self._job.get_failure_time(), 0)
        self._job.enqueue_offload(self._queue, days_old)
        self._job.enqueue_offload(self._queue, days_old)
        self.assertTrue(self._queue.empty())
        self.assertEqual(self._job.get_failure_count(), 0)
        self.assertEqual(self._job.get_failure_time(), 0)


    def _offload_expired_once(self, days_old, count):
        """Make one call to `enqueue_offload()` for an expired job.

        This method tests assertions D and E regarding side-effects
        expected when a job is offloaded.

        """
        self._job.enqueue_offload(self._queue, days_old)
        self.assertEqual(self._job.get_failure_count(), count)
        self.assertFalse(self._queue.empty())
        v = self._queue.get_nowait()
        self.assertTrue(self._queue.empty())
        self.assertEqual(v, self._job.queue_args)


    def _offload_expired_job(self, days_old):
        """Make calls to `enqueue_offload()` for a just-expired job.

        This method directly tests assertions F and G regarding
        side-effects on `get_failure_time()`.

        """
        t0 = time.time()
        self._offload_expired_once(days_old, 1)
        t1 = self._job.get_failure_time()
        self.assertLessEqual(t1, time.time())
        self.assertGreaterEqual(t1, t0)
        self._offload_expired_once(days_old, 2)
        self.assertEqual(self._job.get_failure_time(), t1)
        self._offload_expired_once(days_old, 3)
        self.assertEqual(self._job.get_failure_time(), t1)


    def test_case_1_no_expiration(self):
        """Test a series of `enqueue_offload()` calls with `days_old` of 0.

        This tests that offload works as expected if calls are
        made both before and after the job becomes expired.

        """
        self._offload_unexpired_job(0)
        self._job.set_finished(0)
        self._offload_expired_job(0)


    def test_case_2_no_expiration(self):
        """Test a series of `enqueue_offload()` calls with `days_old` of 0.

        This tests that offload works as expected if calls are made
        only after the job becomes expired.

        """
        self._job.set_finished(0)
        self._offload_expired_job(0)


    def test_case_1_with_expiration(self):
        """Test a series of `enqueue_offload()` calls with `days_old` non-zero.

        This tests that offload works as expected if calls are made
        before the job finishes, before the job expires, and after
        the job expires.

        """
        self._offload_unexpired_job(_TEST_EXPIRATION_AGE)
        self._job.set_finished(_TEST_EXPIRATION_AGE)
        self._offload_unexpired_job(_TEST_EXPIRATION_AGE)
        self._job.set_expired(_TEST_EXPIRATION_AGE)
        self._offload_expired_job(_TEST_EXPIRATION_AGE)


    def test_case_2_with_expiration(self):
        """Test a series of `enqueue_offload()` calls with `days_old` non-zero.

        This tests that offload works as expected if calls are made
        between finishing and expiration, and after the job expires.

        """
        self._job.set_finished(_TEST_EXPIRATION_AGE)
        self._offload_unexpired_job(_TEST_EXPIRATION_AGE)
        self._job.set_expired(_TEST_EXPIRATION_AGE)
        self._offload_expired_job(_TEST_EXPIRATION_AGE)


    def test_case_3_with_expiration(self):
        """Test a series of `enqueue_offload()` calls with `days_old` non-zero.

        This tests that offload works as expected if calls are made
        only before finishing and after expiration.

        """
        self._offload_unexpired_job(_TEST_EXPIRATION_AGE)
        self._job.set_expired(_TEST_EXPIRATION_AGE)
        self._offload_expired_job(_TEST_EXPIRATION_AGE)


    def test_case_4_with_expiration(self):
        """Test a series of `enqueue_offload()` calls with `days_old` non-zero.

        This tests that offload works as expected if calls are made
        only after expiration.

        """
        self._job.set_expired(_TEST_EXPIRATION_AGE)
        self._offload_expired_job(_TEST_EXPIRATION_AGE)


class GetJobDirectoriesTests(_TempResultsDirTestBase):
    """Tests for `_JobDirectory.get_job_directories()`."""

    def setUp(self):
        super(GetJobDirectoriesTests, self).setUp()
        self.make_job_hierarchy()
        os.mkdir('not-a-job')
        open('not-a-dir', 'w').close()


    def _run_get_directories(self, cls, expected_list):
        """Test `get_job_directories()` for the given class.

        Calls the method, and asserts that the returned list of
        directories matches the expected return value.

        @param expected_list Expected return value from the call.
        """
        dirlist = cls.get_job_directories()
        self.assertEqual(set(dirlist), set(expected_list))


    def test_get_regular_jobs(self):
        """Test `RegularJobDirectory.get_job_directories()`."""
        self._run_get_directories(job_directories.RegularJobDirectory,
                                  self.REGULAR_JOBLIST)


    def test_get_special_jobs(self):
        """Test `SpecialJobDirectory.get_job_directories()`."""
        self._run_get_directories(job_directories.SpecialJobDirectory,
                                  self.SPECIAL_JOBLIST)


class AddJobsTests(_TempResultsDirTestBase):
    """Tests for `Offloader._add_new_jobs()`."""

    MOREJOBS = ['115-fubar', '116-fubar', '117-fubar', '118-snafu']

    def setUp(self):
        super(AddJobsTests, self).setUp()
        self._initial_job_names = (
            set(self.REGULAR_JOBLIST) | set(self.SPECIAL_JOBLIST))
        self.make_job_hierarchy()
        self._offloader = gs_offloader.Offloader(_get_options(['-a']))
        self.mox.StubOutWithMock(logging, 'debug')


    def _run_add_new_jobs(self, expected_key_set):
        """Basic test assertions for `_add_new_jobs()`.

        Asserts the following:
          * The keys in the offloader's `_open_jobs` dictionary
            matches the expected set of keys.
          * For every job in `_open_jobs`, the job has the expected
            directory name.

        """
        count = len(expected_key_set) - len(self._offloader._open_jobs)
        logging.debug(mox.IgnoreArg(), count)
        self.mox.ReplayAll()
        self._offloader._add_new_jobs()
        self.assertEqual(expected_key_set,
                         set(self._offloader._open_jobs.keys()))
        for jobkey, job in self._offloader._open_jobs.items():
            self.assertEqual(jobkey, job._dirname)
        self.mox.VerifyAll()
        self.mox.ResetAll()


    def test_add_jobs_empty(self):
        """Test adding jobs to an empty dictionary.

        Calls the offloader's `_add_new_jobs()`, then perform
        the assertions of `self._check_open_jobs()`.

        """
        self._run_add_new_jobs(self._initial_job_names)


    def test_add_jobs_non_empty(self):
        """Test adding jobs to a non-empty dictionary.

        Calls the offloader's `_add_new_jobs()` twice; once from
        initial conditions, and then again after adding more
        directories.  After the second call, perform the assertions
        of `self._check_open_jobs()`.  Additionally, assert that
        keys added by the first call still map to their original
        job object after the second call.

        """
        self._run_add_new_jobs(self._initial_job_names)
        jobs_copy = self._offloader._open_jobs.copy()
        for d in self.MOREJOBS:
            os.mkdir(d)
        self._run_add_new_jobs(self._initial_job_names |
                                 set(self.MOREJOBS))
        for key in jobs_copy.keys():
            self.assertIs(jobs_copy[key],
                          self._offloader._open_jobs[key])


class JobStateTests(_TempResultsDirTestBase):
    """Tests for job state predicates.

    This tests for the expected results from the
    `is_offloaded()` predicate method.

    """

    def test_unfinished_job(self):
        """Test that an unfinished job reports the correct state.

        A job is "unfinished" if it isn't marked complete in the
        database.  A job in this state is neither "complete" nor
        "reportable".

        """
        job = self.make_job(self.REGULAR_JOBLIST[0])
        self.assertFalse(job.is_offloaded())


    def test_incomplete_job(self):
        """Test that an incomplete job reports the correct state.

        A job is "incomplete" if exactly one attempt has been made
        to offload the job, but its results directory still exists.
        A job in this state is neither "complete" nor "reportable".

        """
        job = self.make_job(self.REGULAR_JOBLIST[0])
        job.set_incomplete()
        self.assertFalse(job.is_offloaded())


    def test_reportable_job(self):
        """Test that a reportable job reports the correct state.

        A job is "reportable" if more than one attempt has been made
        to offload the job, and its results directory still exists.
        A job in this state is "reportable", but not "complete".

        """
        job = self.make_job(self.REGULAR_JOBLIST[0])
        job.set_reportable()
        self.assertFalse(job.is_offloaded())


    def test_completed_job(self):
        """Test that a completed job reports the correct state.

        A job is "completed" if at least one attempt has been made
        to offload the job, and its results directory still exists.
        A job in this state is "complete", and not "reportable".

        """
        job = self.make_job(self.REGULAR_JOBLIST[0])
        job.set_complete()
        self.assertTrue(job.is_offloaded())


class ReportingTests(_TempResultsDirTestBase):
    """Tests for `Offloader._update_offload_results()`."""

    def setUp(self):
        super(ReportingTests, self).setUp()
        self._offloader = gs_offloader.Offloader(_get_options([]))
        self.mox.StubOutWithMock(self._offloader, '_log_failed_jobs_locally')
        self.mox.StubOutWithMock(logging, 'debug')


    def _add_job(self, jobdir):
        """Add a job to the dictionary of unfinished jobs."""
        j = self.make_job(jobdir)
        self._offloader._open_jobs[j._dirname] = j
        return j


    def _expect_log_message(self, new_open_jobs, with_failures):
        """Mock expected logging calls.

        `_update_offload_results()` logs one message with the number
        of jobs removed from the open job set and the number of jobs
        still remaining.  Additionally, if there are reportable
        jobs, then it logs the number of jobs that haven't yet
        offloaded.

        This sets up the logging calls using `new_open_jobs` to
        figure the job counts.  If `with_failures` is true, then
        the log message is set up assuming that all jobs in
        `new_open_jobs` have offload failures.

        @param new_open_jobs New job set for calculating counts
                             in the messages.
        @param with_failures Whether the log message with a
                             failure count is expected.

        """
        count = len(self._offloader._open_jobs) - len(new_open_jobs)
        logging.debug(mox.IgnoreArg(), count, len(new_open_jobs))
        if with_failures:
            logging.debug(mox.IgnoreArg(), len(new_open_jobs))


    def _run_update(self, new_open_jobs):
        """Call `_update_offload_results()`.

        Initial conditions are set up by the caller.  This calls
        `_update_offload_results()` once, and then checks these
        assertions:
          * The offloader's new `_open_jobs` field contains only
            the entries in `new_open_jobs`.

        @param new_open_jobs A dictionary representing the expected
                             new value of the offloader's
                             `_open_jobs` field.
        """
        self.mox.ReplayAll()
        self._offloader._update_offload_results()
        self.assertEqual(self._offloader._open_jobs, new_open_jobs)
        self.mox.VerifyAll()
        self.mox.ResetAll()


    def _expect_failed_jobs(self, failed_jobs):
        """Mock expected call to log the failed jobs on local disk.

        TODO(crbug.com/686904): The fact that we have to mock an internal
        function for this test is evidence that we need to pull out the local
        file formatter in its own object in a future CL.

        @param failed_jobs: The list of jobs being logged as failed.
        """
        self._offloader._log_failed_jobs_locally(failed_jobs)


    def test_no_jobs(self):
        """Test `_update_offload_results()` with no open jobs.

        Initial conditions are an empty `_open_jobs` list.
        Expected result is an empty `_open_jobs` list.

        """
        self._expect_log_message({}, False)
        self._expect_failed_jobs([])
        self._run_update({})


    def test_all_completed(self):
        """Test `_update_offload_results()` with only complete jobs.

        Initial conditions are an `_open_jobs` list consisting of only completed
        jobs.
        Expected result is an empty `_open_jobs` list.

        """
        for d in self.REGULAR_JOBLIST:
            self._add_job(d).set_complete()
        self._expect_log_message({}, False)
        self._expect_failed_jobs([])
        self._run_update({})


    def test_none_finished(self):
        """Test `_update_offload_results()` with only unfinished jobs.

        Initial conditions are an `_open_jobs` list consisting of only
        unfinished jobs.
        Expected result is no change to the `_open_jobs` list.

        """
        for d in self.REGULAR_JOBLIST:
            self._add_job(d)
        new_jobs = self._offloader._open_jobs.copy()
        self._expect_log_message(new_jobs, False)
        self._expect_failed_jobs([])
        self._run_update(new_jobs)


if __name__ == '__main__':
    unittest.main()
