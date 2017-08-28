#!/usr/bin/python
#
# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Script to archive old Autotest results to Google Storage.

Uses gsutil to archive files to the configured Google Storage bucket.
Upon successful copy, the local results directory is deleted.
"""

import base64
import datetime
import errno
import glob
import gzip
import logging
import logging.handlers
import os
import re
import shutil
import signal
import stat
import subprocess
import sys
import tempfile
import time

from optparse import OptionParser

import common
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import global_config
from autotest_lib.client.common_lib import site_utils
from autotest_lib.client.common_lib import utils
from autotest_lib.site_utils import job_directories
from autotest_lib.site_utils import pubsub_utils
from autotest_lib.tko import models

# Autotest requires the psutil module from site-packages, so it must be imported
# after "import common".
try:
    # Does not exist, nor is needed, on moblab.
    import psutil
except ImportError:
    psutil = None

from chromite.lib import parallel
try:
    from chromite.lib import metrics
    from chromite.lib import ts_mon_config
except ImportError:
    metrics = site_utils.metrics_mock
    ts_mon_config = site_utils.metrics_mock


GS_OFFLOADING_ENABLED = global_config.global_config.get_config_value(
        'CROS', 'gs_offloading_enabled', type=bool, default=True)

# Nice setting for process, the higher the number the lower the priority.
NICENESS = 10

# Maximum number of seconds to allow for offloading a single
# directory.
OFFLOAD_TIMEOUT_SECS = 60 * 60

# Sleep time per loop.
SLEEP_TIME_SECS = 5

# Minimum number of seconds between e-mail reports.
REPORT_INTERVAL_SECS = 60 * 60

# Location of Autotest results on disk.
RESULTS_DIR = '/usr/local/autotest/results'
FAILED_OFFLOADS_FILE = os.path.join(RESULTS_DIR, 'FAILED_OFFLOADS')

# Hosts sub-directory that contains cleanup, verify and repair jobs.
HOSTS_SUB_DIR = 'hosts'

LOG_LOCATION = '/usr/local/autotest/logs/'
LOG_FILENAME_FORMAT = 'gs_offloader_%s_log_%s.txt'
LOG_TIMESTAMP_FORMAT = '%Y%m%d_%H%M%S'
LOGGING_FORMAT = '%(asctime)s - %(levelname)s - %(message)s'

FAILED_OFFLOADS_FILE_HEADER = '''
This is the list of gs_offloader failed jobs.
Last offloader attempt at %s failed to offload %d files.
Check http://go/cros-triage-gsoffloader to triage the issue


First failure       Count   Directory name
=================== ======  ==============================
'''
# --+----1----+----  ----+  ----+----1----+----2----+----3

FAILED_OFFLOADS_LINE_FORMAT = '%19s  %5d  %-1s\n'
FAILED_OFFLOADS_TIME_FORMAT = '%Y-%m-%d %H:%M:%S'

USE_RSYNC_ENABLED = global_config.global_config.get_config_value(
        'CROS', 'gs_offloader_use_rsync', type=bool, default=False)

# According to https://cloud.google.com/storage/docs/bucket-naming#objectnames
INVALID_GS_CHARS = ['[', ']', '*', '?', '#']
INVALID_GS_CHAR_RANGE = [(0x00, 0x1F), (0x7F, 0x84), (0x86, 0xFF)]

# Maximum number of files in the folder.
MAX_FILE_COUNT = 500
FOLDERS_NEVER_ZIP = ['debug', 'ssp_logs', 'autoupdate_logs']
LIMIT_FILE_COUNT = global_config.global_config.get_config_value(
        'CROS', 'gs_offloader_limit_file_count', type=bool, default=False)

# Use multiprocessing for gsutil uploading.
GS_OFFLOADER_MULTIPROCESSING = global_config.global_config.get_config_value(
        'CROS', 'gs_offloader_multiprocessing', type=bool, default=False)

D = '[0-9][0-9]'
TIMESTAMP_PATTERN = '%s%s.%s.%s_%s.%s.%s' % (D, D, D, D, D, D, D)
CTS_RESULT_PATTERN = 'testResult.xml'
CTS_V2_RESULT_PATTERN = 'test_result.xml'
# Google Storage bucket URI to store results in.
DEFAULT_CTS_RESULTS_GSURI = global_config.global_config.get_config_value(
        'CROS', 'cts_results_server', default='')
DEFAULT_CTS_APFE_GSURI = global_config.global_config.get_config_value(
        'CROS', 'cts_apfe_server', default='')

_PUBSUB_ENABLED = global_config.global_config.get_config_value(
        'CROS', 'cloud_notification_enabled', type=bool, default=False)
_PUBSUB_TOPIC = global_config.global_config.get_config_value(
        'CROS', 'cloud_notification_topic', default=None)


# Test upload pubsub notification attributes
NOTIFICATION_ATTR_VERSION = 'version'
NOTIFICATION_ATTR_GCS_URI = 'gcs_uri'
NOTIFICATION_ATTR_MOBLAB_MAC = 'moblab_mac_address'
NOTIFICATION_ATTR_MOBLAB_ID = 'moblab_id'
NOTIFICATION_VERSION = '1'


# the message data for new test result notification.
NEW_TEST_RESULT_MESSAGE = 'NEW_TEST_RESULT'


class TimeoutException(Exception):
    """Exception raised by the timeout_handler."""
    pass


def timeout_handler(_signum, _frame):
    """Handler for SIGALRM when the offloading process times out.

    @param _signum: Signal number of the signal that was just caught.
                    14 for SIGALRM.
    @param _frame: Current stack frame.

    @raise TimeoutException: Automatically raises so that the time out
                             is caught by the try/except surrounding the
                             Popen call.
    """
    raise TimeoutException('Process Timed Out')


def get_cmd_list(multiprocessing, dir_entry, gs_path):
    """Return the command to offload a specified directory.

    @param multiprocessing: True to turn on -m option for gsutil.
    @param dir_entry: Directory entry/path that which we need a cmd_list
                      to offload.
    @param gs_path: Location in google storage where we will
                    offload the directory.

    @return A command list to be executed by Popen.
    """
    cmd = ['gsutil']
    if multiprocessing:
        cmd.append('-m')
    if USE_RSYNC_ENABLED:
        cmd.append('rsync')
        target = os.path.join(gs_path, os.path.basename(dir_entry))
    else:
        cmd.append('cp')
        target = gs_path
    cmd += ['-eR', dir_entry, target]
    return cmd


def get_directory_size_kibibytes_cmd_list(directory):
    """Returns command to get a directory's total size."""
    # Having this in its own method makes it easier to mock in
    # unittests.
    return ['du', '-sk', directory]


def get_directory_size_kibibytes(directory):
    """Calculate the total size of a directory with all its contents.

    @param directory: Path to the directory

    @return Size of the directory in kibibytes.
    """
    cmd = get_directory_size_kibibytes_cmd_list(directory)
    process = subprocess.Popen(cmd,
                               stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE)
    stdout_data, stderr_data = process.communicate()

    if process.returncode != 0:
        # This function is used for statistics only, if it fails,
        # nothing else should crash.
        logging.warning('Getting size of %s failed. Stderr:', directory)
        logging.warning(stderr_data)
        return 0

    return int(stdout_data.split('\t', 1)[0])


def get_sanitized_name(name):
    """Get a string with all invalid characters in the name being replaced.

    @param name: Name to be processed.

    @return A string with all invalid characters in the name being
             replaced.
    """
    match_pattern = ''.join([re.escape(c) for c in INVALID_GS_CHARS])
    match_pattern += ''.join([r'\x%02x-\x%02x' % (r[0], r[1])
                              for r in INVALID_GS_CHAR_RANGE])
    invalid = re.compile('[%s]' % match_pattern)
    return invalid.sub(lambda x: '%%%02x' % ord(x.group(0)), name)


def sanitize_dir(dir_entry):
    """Replace all invalid characters in folder and file names with valid ones.

    FIFOs are converted to regular files to prevent gsutil hangs (see crbug/684122).
    Symlinks are converted to regular files that store the link destination
    (crbug/692788).

    @param dir_entry: Directory entry to be sanitized.
    """
    if not os.path.exists(dir_entry):
        return
    renames = []
    fifos = []
    symlinks = []
    for root, dirs, files in os.walk(dir_entry):
        sanitized_root = get_sanitized_name(root)
        for name in dirs + files:
            sanitized_name = get_sanitized_name(name)
            sanitized_path = os.path.join(sanitized_root, sanitized_name)
            if name != sanitized_name:
                orig_path = os.path.join(sanitized_root, name)
                renames.append((orig_path, sanitized_path))
            current_path = os.path.join(root, name)
            file_stat = os.lstat(current_path)
            if stat.S_ISFIFO(file_stat.st_mode):
                # Replace fifos with markers
                fifos.append(sanitized_path)
            elif stat.S_ISLNK(file_stat.st_mode):
                # Replace symlinks with markers
                destination = os.readlink(current_path)
                symlinks.append((sanitized_path, destination))
    for src, dest in renames:
        logging.warning('Invalid character found. Renaming %s to %s.', src,
                        dest)
        shutil.move(src, dest)
    for fifo in fifos:
        logging.debug('Removing fifo %s', fifo)
        os.remove(fifo)
        logging.debug('Creating marker %s', fifo)
        with open(fifo, 'a') as marker:
            marker.write('<FIFO>')
    for link, destination in symlinks:
        logging.debug('Removing symlink %s', link)
        os.remove(link)
        logging.debug('Creating marker %s', link)
        with open(link, 'w') as marker:
            marker.write('<symlink to %s>' % destination)


def _get_zippable_folders(dir_entry):
    folders_list = []
    for folder in os.listdir(dir_entry):
        folder_path = os.path.join(dir_entry, folder)
        if (not os.path.isfile(folder_path) and
                not folder in FOLDERS_NEVER_ZIP):
            folders_list.append(folder_path)
    return folders_list


def limit_file_count(dir_entry):
    """Limit the number of files in given directory.

    The method checks the total number of files in the given directory.
    If the number is greater than MAX_FILE_COUNT, the method will
    compress each folder in the given directory, except folders in
    FOLDERS_NEVER_ZIP.

    @param dir_entry: Directory entry to be checked.
    """
    count = utils.run('find "%s" | wc -l' % dir_entry,
                      ignore_status=True).stdout.strip()
    try:
        count = int(count)
    except (ValueError, TypeError):
        logging.warning('Fail to get the file count in folder %s.', dir_entry)
        return
    if count < MAX_FILE_COUNT:
        return

    # For test job, zip folders in a second level, e.g. 123-debug/host1.
    # This is to allow autoserv debug folder still be accessible.
    # For special task, it does not need to dig one level deeper.
    is_special_task = re.match(job_directories.SPECIAL_TASK_PATTERN,
                               dir_entry)

    folders = _get_zippable_folders(dir_entry)
    if not is_special_task:
        subfolders = []
        for folder in folders:
            subfolders.extend(_get_zippable_folders(folder))
        folders = subfolders

    for folder in folders:
        try:
            zip_name = '%s.tgz' % folder
            utils.run('tar -cz -C "%s" -f "%s" "%s"' %
                      (os.path.dirname(folder), zip_name,
                       os.path.basename(folder)))
        except error.CmdError as e:
            logging.error('Fail to compress folder %s. Error: %s',
                          folder, e)
            continue
        shutil.rmtree(folder)


def correct_results_folder_permission(dir_entry):
    """Make sure the results folder has the right permission settings.

    For tests running with server-side packaging, the results folder has
    the owner of root. This must be changed to the user running the
    autoserv process, so parsing job can access the results folder.

    @param dir_entry: Path to the results folder.
    """
    if not dir_entry:
        return

    logging.info('Trying to correct file permission of %s.', dir_entry)
    try:
        subprocess.check_call(
                ['sudo', '-n', 'chown', '-R', str(os.getuid()), dir_entry])
        subprocess.check_call(
                ['sudo', '-n', 'chgrp', '-R', str(os.getgid()), dir_entry])
    except subprocess.CalledProcessError as e:
        logging.error('Failed to modify permission for %s: %s',
                      dir_entry, e)


def upload_testresult_files(dir_entry, multiprocessing):
    """Upload test results to separate gs buckets.

    Upload testResult.xml.gz/test_result.xml.gz file to cts_results_bucket.
    Upload timestamp.zip to cts_apfe_bucket.

    @param dir_entry: Path to the results folder.
    @param multiprocessing: True to turn on -m option for gsutil.
    """
    for host in glob.glob(os.path.join(dir_entry, '*')):
        cts_path = os.path.join(host, 'cheets_CTS.*', 'results', '*',
                                TIMESTAMP_PATTERN)
        cts_v2_path = os.path.join(host, 'cheets_CTS_*', 'results', '*',
                                   TIMESTAMP_PATTERN)
        gts_v2_path = os.path.join(host, 'cheets_GTS.*', 'results', '*',
                                   TIMESTAMP_PATTERN)
        for result_path, result_pattern in [(cts_path, CTS_RESULT_PATTERN),
                            (cts_v2_path, CTS_V2_RESULT_PATTERN),
                            (gts_v2_path, CTS_V2_RESULT_PATTERN)]:
            for path in glob.glob(result_path):
                try:
                    _upload_files(host, path, result_pattern, multiprocessing)
                except Exception as e:
                    logging.error('ERROR uploading test results %s to GS: %s',
                                  path, e)


def _is_valid_result(build, result_pattern, suite):
    """Check if the result should be uploaded to CTS/GTS buckets.

    @param build: Builder name.
    @param result_pattern: XML result file pattern.
    @param suite: Test suite name.

    @returns: Bool flag indicating whether a valid result.
    """
    if build is None or suite is None:
        return False

    # Not valid if it's not a release build.
    if not re.match(r'(?!trybot-).*-release/.*', build):
        return False

    # Not valid if it's cts result but not 'arc-cts*' or 'test_that_wrapper'
    # suite.
    whitelisted_suites = ['arc-cts', 'arc-cts-dev', 'arc-cts-beta',
                          'arc-cts-stable', 'arc-cts-perbuild', 'arc-gts',
                          'arc-gts-perbuild', 'test_that_wrapper']
    result_patterns = [CTS_RESULT_PATTERN, CTS_V2_RESULT_PATTERN]
    if result_pattern in result_patterns and suite not in whitelisted_suites:
        return False

    return True


def _upload_files(host, path, result_pattern, multiprocessing):
    keyval = models.test.parse_job_keyval(host)
    build = keyval.get('build')
    suite = keyval.get('suite')

    if not _is_valid_result(build, result_pattern, suite):
        # No need to upload current folder, return.
        return

    parent_job_id = str(keyval['parent_job_id'])

    folders = path.split(os.sep)
    job_id = folders[-6]
    package = folders[-4]
    timestamp = folders[-1]

    # Path: bucket/build/parent_job_id/cheets_CTS.*/job_id_timestamp/
    # or bucket/build/parent_job_id/cheets_GTS.*/job_id_timestamp/
    cts_apfe_gs_path = os.path.join(
            DEFAULT_CTS_APFE_GSURI, build, parent_job_id,
            package, job_id + '_' + timestamp) + '/'

    # Path: bucket/cheets_CTS.*/job_id_timestamp/
    # or bucket/cheets_GTS.*/job_id_timestamp/
    test_result_gs_path = os.path.join(
            DEFAULT_CTS_RESULTS_GSURI, package,
            job_id + '_' + timestamp) + '/'

    for zip_file in glob.glob(os.path.join('%s.zip' % path)):
        utils.run(' '.join(get_cmd_list(
                multiprocessing, zip_file, cts_apfe_gs_path)))
        logging.debug('Upload %s to %s ', zip_file, cts_apfe_gs_path)

    for test_result_file in glob.glob(os.path.join(path, result_pattern)):
        # gzip test_result_file(testResult.xml/test_result.xml)

        test_result_file_gz =  '%s.gz' % test_result_file
        with open(test_result_file, 'r') as f_in, (
                gzip.open(test_result_file_gz, 'w')) as f_out:
            shutil.copyfileobj(f_in, f_out)
        utils.run(' '.join(get_cmd_list(
                multiprocessing, test_result_file_gz, test_result_gs_path)))
        logging.debug('Zip and upload %s to %s',
                      test_result_file_gz, test_result_gs_path)
        # Remove test_result_file_gz(testResult.xml.gz/test_result.xml.gz)
        os.remove(test_result_file_gz)


def _create_test_result_notification(gs_path, dir_entry):
    """Construct a test result notification.

    @param gs_path: The test result Google Cloud Storage URI.
    @param dir_entry: The local offload directory name.

    @returns The notification message.
    """
    gcs_uri = os.path.join(gs_path, os.path.basename(dir_entry))
    logging.info('Notification on gcs_uri %s', gcs_uri)
    data = base64.b64encode(NEW_TEST_RESULT_MESSAGE)
    msg_payload = {'data': data}
    msg_attributes = {}
    msg_attributes[NOTIFICATION_ATTR_GCS_URI] = gcs_uri
    msg_attributes[NOTIFICATION_ATTR_VERSION] = NOTIFICATION_VERSION
    msg_attributes[NOTIFICATION_ATTR_MOBLAB_MAC] = \
        site_utils.get_default_interface_mac_address()
    msg_attributes[NOTIFICATION_ATTR_MOBLAB_ID] = site_utils.get_moblab_id()
    msg_payload['attributes'] = msg_attributes

    return msg_payload


def get_offload_dir_func(gs_uri, multiprocessing, delete_age, pubsub_topic=None):
    """Returns the offload directory function for the given gs_uri

    @param gs_uri: Google storage bucket uri to offload to.
    @param multiprocessing: True to turn on -m option for gsutil.
    @param pubsub_topic: The pubsub topic to publish notificaitons. If None,
          pubsub is not enabled.

    @return offload_dir function to perform the offload.
    """
    @metrics.SecondsTimerDecorator(
            'chromeos/autotest/gs_offloader/job_offload_duration')
    def offload_dir(dir_entry, dest_path, job_complete_time):
        """Offload the specified directory entry to Google storage.

        @param dir_entry: Directory entry to offload.
        @param dest_path: Location in google storage where we will
                          offload the directory.
        @param job_complete_time: The complete time of the job from the AFE
                                  database.

        """
        error = False
        stdout_file = None
        stderr_file = None
        try:
            upload_signal_filename = '%s/%s/.GS_UPLOADED' % (
                    RESULTS_DIR, dir_entry)
            if not os.path.isfile(upload_signal_filename):
                sanitize_dir(dir_entry)
                if DEFAULT_CTS_RESULTS_GSURI:
                    upload_testresult_files(dir_entry, multiprocessing)

                if LIMIT_FILE_COUNT:
                    limit_file_count(dir_entry)

                stdout_file = tempfile.TemporaryFile('w+')
                stderr_file = tempfile.TemporaryFile('w+')
                process = None
                signal.alarm(OFFLOAD_TIMEOUT_SECS)
                gs_path = '%s%s' % (gs_uri, dest_path)
                process = subprocess.Popen(
                        get_cmd_list(multiprocessing, dir_entry, gs_path),
                        stdout=stdout_file, stderr=stderr_file)
                process.wait()
                signal.alarm(0)

                if process.returncode == 0:
                    dir_size = get_directory_size_kibibytes(dir_entry)

                    m_offload_count = (
                            'chromeos/autotest/gs_offloader/jobs_offloaded')
                    metrics.Counter(m_offload_count).increment()
                    m_offload_size = ('chromeos/autotest/gs_offloader/'
                                      'kilobytes_transferred')
                    metrics.Counter(m_offload_size).increment_by(dir_size)

                    if pubsub_topic:
                        message = _create_test_result_notification(
                                gs_path, dir_entry)
                        msg_ids = pubsub_utils.publish_notifications(
                                pubsub_topic, [message])
                        if not msg_ids:
                            error = True

                    if not error:
                        open(upload_signal_filename, 'a').close()
                else:
                    error = True
            if os.path.isfile(upload_signal_filename):
                if job_directories.is_job_expired(delete_age, job_complete_time):
                    shutil.rmtree(dir_entry)

        except TimeoutException:
            m_timeout = 'chromeos/autotest/errors/gs_offloader/timed_out_count'
            metrics.Counter(m_timeout).increment()
            # If we finished the call to Popen(), we may need to
            # terminate the child process.  We don't bother calling
            # process.poll(); that inherently races because the child
            # can die any time it wants.
            if process:
                try:
                    process.terminate()
                except OSError:
                    # We don't expect any error other than "No such
                    # process".
                    pass
            logging.error('Offloading %s timed out after waiting %d '
                          'seconds.', dir_entry, OFFLOAD_TIMEOUT_SECS)
            error = True
        except OSError as e:
            # The wrong file permission can lead call
            # `shutil.rmtree(dir_entry)` to raise OSError with message
            # 'Permission denied'. Details can be found in
            # crbug.com/536151
            if e.errno == errno.EACCES:
                correct_results_folder_permission(dir_entry)
            m_permission_error = ('chromeos/autotest/errors/gs_offloader/'
                                  'wrong_permissions_count')
            metrics.Counter(m_permission_error).increment()
        finally:
            signal.alarm(0)
            if error:
                # Rewind the log files for stdout and stderr and log
                # their contents.
                stdout_file.seek(0)
                stderr_file.seek(0)
                stderr_content = stderr_file.read()
                logging.warning('Error occurred when offloading %s:', dir_entry)
                logging.warning('Stdout:\n%s \nStderr:\n%s', stdout_file.read(),
                                stderr_content)
                # Some result files may have wrong file permission. Try
                # to correct such error so later try can success.
                # TODO(dshi): The code is added to correct result files
                # with wrong file permission caused by bug 511778. After
                # this code is pushed to lab and run for a while to
                # clean up these files, following code and function
                # correct_results_folder_permission can be deleted.
                if 'CommandException: Error opening file' in stderr_content:
                    correct_results_folder_permission(dir_entry)
            if stdout_file:
                stdout_file.close()
            if stderr_file:
                stderr_file.close()
    return offload_dir


def delete_files(dir_entry, dest_path, job_complete_time):
    """Simply deletes the dir_entry from the filesystem.

    Uses same arguments as offload_dir so that it can be used in replace
    of it on systems that only want to delete files instead of
    offloading them.

    @param dir_entry: Directory entry to offload.
    @param dest_path: NOT USED.
    @param job_complete_time: NOT USED.
    """
    shutil.rmtree(dir_entry)


def _format_job_for_failure_reporting(job):
    """Formats a _JobDirectory for reporting / logging.

    @param job: The _JobDirectory to format.
    """
    d = datetime.datetime.fromtimestamp(job.get_failure_time())
    data = (d.strftime(FAILED_OFFLOADS_TIME_FORMAT),
            job.get_failure_count(),
            job.get_job_directory())
    return FAILED_OFFLOADS_LINE_FORMAT % data


def wait_for_gs_write_access(gs_uri):
    """Verify and wait until we have write access to Google Storage.

    @param gs_uri: The Google Storage URI we are trying to offload to.
    """
    # TODO (sbasi) Try to use the gsutil command to check write access.
    # Ensure we have write access to gs_uri.
    dummy_file = tempfile.NamedTemporaryFile()
    test_cmd = get_cmd_list(False, dummy_file.name, gs_uri)
    while True:
        try:
            subprocess.check_call(test_cmd)
            subprocess.check_call(
                    ['gsutil', 'rm',
                     os.path.join(gs_uri,
                                  os.path.basename(dummy_file.name))])
            break
        except subprocess.CalledProcessError:
            logging.debug('Unable to offload to %s, sleeping.', gs_uri)
            time.sleep(120)


class Offloader(object):
    """State of the offload process.

    Contains the following member fields:
      * _offload_func:  Function to call for each attempt to offload
        a job directory.
      * _jobdir_classes:  List of classes of job directory to be
        offloaded.
      * _processes:  Maximum number of outstanding offload processes
        to allow during an offload cycle.
      * _age_limit:  Minimum age in days at which a job may be
        offloaded.
      * _open_jobs: a dictionary mapping directory paths to Job
        objects.
    """

    def __init__(self, options):
        self._pubsub_topic = None
        self._upload_age_limit = options.age_to_upload
        self._delete_age_limit = options.age_to_delete
        if options.delete_only:
            self._offload_func = delete_files
        else:
            self.gs_uri = utils.get_offload_gsuri()
            logging.debug('Offloading to: %s', self.gs_uri)
            multiprocessing = False
            if options.multiprocessing:
                multiprocessing = True
            elif options.multiprocessing is None:
                multiprocessing = GS_OFFLOADER_MULTIPROCESSING
            logging.info(
                    'Offloader multiprocessing is set to:%r', multiprocessing)
            if options.pubsub_topic_for_job_upload:
              self._pubsub_topic = options.pubsub_topic_for_job_upload
            elif _PUBSUB_ENABLED:
              self._pubsub_topic = _PUBSUB_TOPIC
            self._offload_func = get_offload_dir_func(
                    self.gs_uri, multiprocessing, self._delete_age_limit,
                    self._pubsub_topic)
        classlist = []
        if options.process_hosts_only or options.process_all:
            classlist.append(job_directories.SpecialJobDirectory)
        if not options.process_hosts_only:
            classlist.append(job_directories.RegularJobDirectory)
        self._jobdir_classes = classlist
        assert self._jobdir_classes
        self._processes = options.parallelism
        self._open_jobs = {}
        self._pusub_topic = None


    def _add_new_jobs(self):
        """Find new job directories that need offloading.

        Go through the file system looking for valid job directories
        that are currently not in `self._open_jobs`, and add them in.

        """
        new_job_count = 0
        for cls in self._jobdir_classes:
            for resultsdir in cls.get_job_directories():
                if resultsdir in self._open_jobs:
                    continue
                self._open_jobs[resultsdir] = cls(resultsdir)
                new_job_count += 1
        logging.debug('Start of offload cycle - found %d new jobs',
                      new_job_count)


    def _remove_offloaded_jobs(self):
        """Removed offloaded jobs from `self._open_jobs`."""
        removed_job_count = 0
        for jobkey, job in self._open_jobs.items():
            if job.is_offloaded():
                del self._open_jobs[jobkey]
                removed_job_count += 1
        logging.debug('End of offload cycle - cleared %d new jobs, '
                      'carrying %d open jobs',
                      removed_job_count, len(self._open_jobs))


    def _update_offload_results(self):
        """Check and report status after attempting offload.

        This function processes all jobs in `self._open_jobs`, assuming
        an attempt has just been made to offload all of them.

        Any jobs that have been successfully offloaded are removed.

        If any jobs have reportable errors, and we haven't generated
        an e-mail report in the last `REPORT_INTERVAL_SECS` seconds,
        send new e-mail describing the failures.

        """
        self._remove_offloaded_jobs()
        failed_jobs = [j for j in self._open_jobs.values() if
                       j.get_failure_time()]
        self._report_failed_jobs_count(failed_jobs)
        self._log_failed_jobs_locally(failed_jobs)


    def offload_once(self):
        """Perform one offload cycle.

        Find all job directories for new jobs that we haven't seen
        before.  Then, attempt to offload the directories for any
        jobs that have finished running.  Offload of multiple jobs
        is done in parallel, up to `self._processes` at a time.

        After we've tried uploading all directories, go through the list
        checking the status of all uploaded directories.  If necessary,
        report failures via e-mail.

        """
        self._add_new_jobs()
        self._report_current_jobs_count()
        with parallel.BackgroundTaskRunner(
                self._offload_func, processes=self._processes) as queue:
            for job in self._open_jobs.values():
                job.enqueue_offload(queue, self._upload_age_limit)
        self._update_offload_results()


    def _log_failed_jobs_locally(self, failed_jobs,
                                 log_file=FAILED_OFFLOADS_FILE):
        """Updates a local file listing all the failed jobs.

        The dropped file can be used by the developers to list jobs that we have
        failed to upload.

        @param failed_jobs: A list of failed _JobDirectory objects.
        @param log_file: The file to log the failed jobs to.
        """
        now = datetime.datetime.now()
        now_str = now.strftime(FAILED_OFFLOADS_TIME_FORMAT)
        formatted_jobs = [_format_job_for_failure_reporting(job)
                            for job in failed_jobs]
        formatted_jobs.sort()

        with open(log_file, 'w') as logfile:
            logfile.write(FAILED_OFFLOADS_FILE_HEADER %
                          (now_str, len(failed_jobs)))
            logfile.writelines(formatted_jobs)


    def _report_current_jobs_count(self):
        """Report the number of outstanding jobs to monarch."""
        metrics.Gauge('chromeos/autotest/gs_offloader/current_jobs_count').set(
                len(self._open_jobs))


    def _report_failed_jobs_count(self, failed_jobs):
        """Report the number of outstanding failed offload jobs to monarch.

        @param: List of failed jobs.
        """
        metrics.Gauge('chromeos/autotest/gs_offloader/failed_jobs_count').set(
                len(failed_jobs))


def parse_options():
    """Parse the args passed into gs_offloader."""
    defaults = 'Defaults:\n  Destination: %s\n  Results Path: %s' % (
            utils.DEFAULT_OFFLOAD_GSURI, RESULTS_DIR)
    usage = 'usage: %prog [options]\n' + defaults
    parser = OptionParser(usage)
    parser.add_option('-a', '--all', dest='process_all',
                      action='store_true',
                      help='Offload all files in the results directory.')
    parser.add_option('-s', '--hosts', dest='process_hosts_only',
                      action='store_true',
                      help='Offload only the special tasks result files '
                      'located in the results/hosts subdirectory')
    parser.add_option('-p', '--parallelism', dest='parallelism',
                      type='int', default=1,
                      help='Number of parallel workers to use.')
    parser.add_option('-o', '--delete_only', dest='delete_only',
                      action='store_true',
                      help='GS Offloader will only the delete the '
                      'directories and will not offload them to google '
                      'storage. NOTE: If global_config variable '
                      'CROS.gs_offloading_enabled is False, --delete_only '
                      'is automatically True.',
                      default=not GS_OFFLOADING_ENABLED)
    parser.add_option('-d', '--days_old', dest='days_old',
                      help='Minimum job age in days before a result can be '
                      'offloaded.', type='int', default=0)
    parser.add_option('-t', '--pubsub_topic_for_job_upload',
                      dest='pubsub_topic_for_job_upload',
                      help='The pubsub topic to send notifciations for '
                      'new job upload',
                      action='store', type='string', default=None)
    parser.add_option('-l', '--log_size', dest='log_size',
                      help='Limit the offloader logs to a specified '
                      'number of Mega Bytes.', type='int', default=0)
    parser.add_option('-m', dest='multiprocessing', action='store_true',
                      help='Turn on -m option for gsutil. If not set, the '
                      'global config setting gs_offloader_multiprocessing '
                      'under CROS section is applied.')
    parser.add_option('-i', '--offload_once', dest='offload_once',
                      action='store_true',
                      help='Upload all available results and then exit.')
    parser.add_option('-y', '--normal_priority', dest='normal_priority',
                      action='store_true',
                      help='Upload using normal process priority.')
    parser.add_option('-u', '--age_to_upload', dest='age_to_upload',
                      help='Minimum job age in days before a result can be '
                      'offloaded, but not removed from local storage',
                      type='int', default=None)
    parser.add_option('-n', '--age_to_delete', dest='age_to_delete',
                      help='Minimum job age in days before a result can be '
                      'removed from local storage',
                      type='int', default=None)

    options = parser.parse_args()[0]
    if options.process_all and options.process_hosts_only:
        parser.print_help()
        print ('Cannot process all files and only the hosts '
               'subdirectory. Please remove an argument.')
        sys.exit(1)

    if options.days_old and (options.age_to_upload or options.age_to_delete):
        parser.print_help()
        print('Use the days_old option or the age_to_* options but not both')
        sys.exit(1)

    if options.age_to_upload == None:
        options.age_to_upload = options.days_old
    if options.age_to_delete == None:
        options.age_to_delete = options.days_old

    return options


def main():
    """Main method of gs_offloader."""
    options = parse_options()

    if options.process_all:
        offloader_type = 'all'
    elif options.process_hosts_only:
        offloader_type = 'hosts'
    else:
        offloader_type = 'jobs'

    log_timestamp = time.strftime(LOG_TIMESTAMP_FORMAT)
    if options.log_size > 0:
        log_timestamp = ''
    log_basename = LOG_FILENAME_FORMAT % (offloader_type, log_timestamp)
    log_filename = os.path.join(LOG_LOCATION, log_basename)
    log_formatter = logging.Formatter(LOGGING_FORMAT)
    # Replace the default logging handler with a RotatingFileHandler. If
    # options.log_size is 0, the file size will not be limited. Keeps
    # one backup just in case.
    handler = logging.handlers.RotatingFileHandler(
            log_filename, maxBytes=1024 * options.log_size, backupCount=1)
    handler.setFormatter(log_formatter)
    logger = logging.getLogger()
    logger.setLevel(logging.DEBUG)
    logger.addHandler(handler)

    # Nice our process (carried to subprocesses) so we don't overload
    # the system.
    if not options.normal_priority:
        logging.debug('Set process to nice value: %d', NICENESS)
        os.nice(NICENESS)
    if psutil:
        proc = psutil.Process()
        logging.debug('Set process to ionice IDLE')
        proc.ionice(psutil.IOPRIO_CLASS_IDLE)

    # os.listdir returns relative paths, so change to where we need to
    # be to avoid an os.path.join on each loop.
    logging.debug('Offloading Autotest results in %s', RESULTS_DIR)
    os.chdir(RESULTS_DIR)

    signal.signal(signal.SIGALRM, timeout_handler)

    with ts_mon_config.SetupTsMonGlobalState('gs_offloader', indirect=True,
                                             short_lived=False):
        offloader = Offloader(options)
        if not options.delete_only:
            wait_for_gs_write_access(offloader.gs_uri)
        while True:
            offloader.offload_once()
            if options.offload_once:
                break
            time.sleep(SLEEP_TIME_SECS)


if __name__ == '__main__':
    main()
