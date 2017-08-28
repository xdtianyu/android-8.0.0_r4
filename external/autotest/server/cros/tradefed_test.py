# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# repohooks/pre-upload.py currently does not run pylint. But for developers who
# want to check their code manually we disable several harmless pylint warnings
# which just distract from more serious remaining issues.
#
# The instance variables _host and _install_paths are not defined in __init__().
# pylint: disable=attribute-defined-outside-init
#
# Many short variable names don't follow the naming convention.
# pylint: disable=invalid-name
#
# _parse_result() and _dir_size() don't access self and could be functions.
# pylint: disable=no-self-use
#
# _ChromeLogin and _TradefedLogCollector have no public methods.
# pylint: disable=too-few-public-methods

import contextlib
import errno
import glob
import hashlib
import lockfile
import logging
import os
import pipes
import random
import re
import shutil
import stat
import tempfile
import urlparse

from autotest_lib.client.bin import utils as client_utils
from autotest_lib.client.common_lib import base_utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import dev_server
from autotest_lib.server import autotest
from autotest_lib.server import test
from autotest_lib.server import utils

# TODO(ihf): If akeshet doesn't fix crbug.com/691046 delete metrics again.
try:
    from chromite.lib import metrics
except ImportError:
    metrics = utils.metrics_mock

# TODO(ihf): Find a home for all these paths. This is getting out of hand.
_SDK_TOOLS_DIR_M = 'gs://chromeos-arc-images/builds/git_mnc-dr-arc-dev-linux-static_sdk_tools/3554341'
_SDK_TOOLS_FILES = ['aapt']
# To stabilize adb behavior, we use dynamically linked adb.
_ADB_DIR_M = 'gs://chromeos-arc-images/builds/git_mnc-dr-arc-dev-linux-cheets_arm-user/3554341'
_ADB_FILES = ['adb']

_ADB_POLLING_INTERVAL_SECONDS = 1
_ADB_READY_TIMEOUT_SECONDS = 60
_ANDROID_ADB_KEYS_PATH = '/data/misc/adb/adb_keys'

_ARC_POLLING_INTERVAL_SECONDS = 1
_ARC_READY_TIMEOUT_SECONDS = 60

_TRADEFED_PREFIX = 'autotest-tradefed-install_'
_TRADEFED_CACHE_LOCAL = '/tmp/autotest-tradefed-cache'
_TRADEFED_CACHE_CONTAINER = '/usr/local/autotest/results/shared/cache'
_TRADEFED_CACHE_CONTAINER_LOCK = '/usr/local/autotest/results/shared/lock'

# According to dshi a drone has 500GB of disk space. It is ok for now to use
# 10GB of disk space, as no more than 10 tests should run in parallel.
# TODO(ihf): Investigate tighter cache size.
_TRADEFED_CACHE_MAX_SIZE = (10 * 1024 * 1024 * 1024)


class _ChromeLogin(object):
    """Context manager to handle Chrome login state."""

    def __init__(self, host):
        self._host = host

    def __enter__(self):
        """Logs in to the Chrome."""
        logging.info('Ensure Android is running...')
        # If we can't login to Chrome and launch Android we want this job to
        # die roughly after 5 minutes instead of hanging for the duration.
        autotest.Autotest(self._host).run_timed_test('cheets_CTSHelper',
                                                     timeout=300,
                                                     check_client_result=True)

    def __exit__(self, exc_type, exc_value, traceback):
        """On exit, to wipe out all the login state, reboot the machine.

        @param exc_type: Exception type if an exception is raised from the
                         with-block.
        @param exc_value: Exception instance if an exception is raised from
                          the with-block.
        @param traceback: Stack trace info if an exception is raised from
                          the with-block.
        @return None, indicating not to ignore an exception from the with-block
                if raised.
        """
        logging.info('Rebooting...')
        try:
            self._host.reboot()
        except Exception:
            if exc_type is None:
                raise
            # If an exception is raise from the with-block, just record the
            # exception for the rebooting to avoid ignoring the original
            # exception.
            logging.exception('Rebooting failed.')


@contextlib.contextmanager
def lock(filename):
    """Prevents other autotest/tradefed instances from accessing cache.

    @param filename: The file to be locked.
    """
    filelock = lockfile.FileLock(filename)
    # It is tempting just to call filelock.acquire(3600). But the implementation
    # has very poor temporal granularity (timeout/10), which is unsuitable for
    # our needs. See /usr/lib64/python2.7/site-packages/lockfile/
    attempts = 0
    while not filelock.i_am_locking():
        try:
            attempts += 1
            logging.info('Waiting for cache lock...')
            filelock.acquire(random.randint(1, 5))
        except (lockfile.AlreadyLocked, lockfile.LockTimeout):
            if attempts > 1000:
                # Normally we should aqcuire the lock in a few seconds. Once we
                # wait on the order of hours either the dev server IO is
                # overloaded or a lock didn't get cleaned up. Take one for the
                # team, break the lock and report a failure. This should fix
                # the lock for following tests. If the failure affects more than
                # one job look for a deadlock or dev server overload.
                logging.error('Permanent lock failure. Trying to break lock.')
                filelock.break_lock()
                raise error.TestFail('Error: permanent cache lock failure.')
        else:
            logging.info('Acquired cache lock after %d attempts.', attempts)
    try:
        yield
    finally:
        filelock.release()
        logging.info('Released cache lock.')


@contextlib.contextmanager
def adb_keepalive(target, extra_paths):
    """A context manager that keeps the adb connection alive.

    AdbKeepalive will spin off a new process that will continuously poll for
    adb's connected state, and will attempt to reconnect if it ever goes down.
    This is the only way we can currently recover safely from (intentional)
    reboots.

    @param target: the hostname and port of the DUT.
    @param extra_paths: any additional components to the PATH environment
                        variable.
    """
    from autotest_lib.client.common_lib.cros import adb_keepalive as module
    # |__file__| returns the absolute path of the compiled bytecode of the
    # module. We want to run the original .py file, so we need to change the
    # extension back.
    script_filename = module.__file__.replace('.pyc', '.py')
    job = base_utils.BgJob([script_filename, target],
                           nickname='adb_keepalive', stderr_level=logging.DEBUG,
                           stdout_tee=base_utils.TEE_TO_LOGS,
                           stderr_tee=base_utils.TEE_TO_LOGS,
                           extra_paths=extra_paths)

    try:
        yield
    finally:
        # The adb_keepalive.py script runs forever until SIGTERM is sent.
        base_utils.nuke_subprocess(job.sp)
        base_utils.join_bg_jobs([job])


@contextlib.contextmanager
def pushd(d):
    """Defines pushd.
    @param d: the directory to change to.
    """
    current = os.getcwd()
    os.chdir(d)
    try:
        yield
    finally:
        os.chdir(current)


class TradefedTest(test.test):
    """Base class to prepare DUT to run tests via tradefed."""
    version = 1

    # TODO(ihf): Remove _ABD_DIR_M/_SDK_TOOLS_DIR_M defaults once M is dead.
    def initialize(self, host=None, adb_dir=_ADB_DIR_M,
                   sdk_tools_dir=_SDK_TOOLS_DIR_M):
        """Sets up the tools and binary bundles for the test."""
        logging.info('Hostname: %s', host.hostname)
        self._host = host
        self._install_paths = []
        # Tests in the lab run within individual lxc container instances.
        if utils.is_in_container():
            cache_root = _TRADEFED_CACHE_CONTAINER
        else:
            cache_root = _TRADEFED_CACHE_LOCAL
        # Quick sanity check and spew of java version installed on the server.
        utils.run('java', args=('-version',), ignore_status=False, verbose=True,
                  stdout_tee=utils.TEE_TO_LOGS, stderr_tee=utils.TEE_TO_LOGS)
        # The content of the cache survives across jobs.
        self._safe_makedirs(cache_root)
        self._tradefed_cache = os.path.join(cache_root, 'cache')
        self._tradefed_cache_lock = os.path.join(cache_root, 'lock')
        # The content of the install location does not survive across jobs and
        # is isolated (by using a unique path)_against other autotest instances.
        # This is not needed for the lab, but if somebody wants to run multiple
        # TradedefTest instance.
        self._tradefed_install = tempfile.mkdtemp(prefix=_TRADEFED_PREFIX)
        # Under lxc the cache is shared between multiple autotest/tradefed
        # instances. We need to synchronize access to it. All binaries are
        # installed through the (shared) cache into the local (unshared)
        # lxc/autotest instance storage.
        # If clearing the cache it must happen before all downloads.
        self._clear_download_cache_if_needed()
        # Set permissions (rwxr-xr-x) to the executable binaries.
        permission = (stat.S_IRWXU | stat.S_IRGRP | stat.S_IXGRP | stat.S_IROTH
                | stat.S_IXOTH)
        self._install_files(adb_dir, _ADB_FILES, permission)
        self._install_files(sdk_tools_dir, _SDK_TOOLS_FILES, permission)

    def cleanup(self):
        """Cleans up any dirtied state."""
        # Kill any lingering adb servers.
        self._run('adb', verbose=True, args=('kill-server',))
        logging.info('Cleaning up %s.', self._tradefed_install)
        shutil.rmtree(self._tradefed_install)

    def _login_chrome(self):
        """Returns Chrome log-in context manager.

        Please see also cheets_CTSHelper for details about how this works.
        """
        return _ChromeLogin(self._host)

    def _get_adb_target(self):
        return '{}:{}'.format(self._host.hostname, self._host.port)

    def _try_adb_connect(self):
        """Attempts to connect to adb on the DUT.

        @return boolean indicating if adb connected successfully.
        """
        # This may fail return failure due to a race condition in adb connect
        # (b/29370989). If adb is already connected, this command will
        # immediately return success.
        hostport = self._get_adb_target()
        result = self._run(
                'adb',
                args=('connect', hostport),
                verbose=True,
                ignore_status=True)
        logging.info('adb connect {}:\n{}'.format(hostport, result.stdout))
        if result.exit_status != 0:
            return False

        result = self._run('adb', args=('devices',))
        logging.info('adb devices:\n' + result.stdout)
        if not re.search(
                r'{}\s+(device|unauthorized)'.format(re.escape(hostport)),
                result.stdout):
            return False

        # Actually test the connection with an adb command as there can be
        # a race between detecting the connected device and actually being
        # able to run a commmand with authenticated adb.
        result = self._run('adb', args=('shell', 'exit'), ignore_status=True)
        return result.exit_status == 0

    def _android_shell(self, command):
        """Run a command remotely on the device in an android shell

        This function is strictly for internal use only, as commands do not run
        in a fully consistent Android environment. Prefer adb shell instead.
        """
        self._host.run('android-sh -c ' + pipes.quote(command))

    def _write_android_file(self, filename, data):
        """Writes a file to a location relative to the android container.

        This is an internal function used to bootstrap adb.
        Tests should use adb push to write files.
        """
        android_cmd = 'echo %s > %s' % (pipes.quote(data),
                                        pipes.quote(filename))
        self._android_shell(android_cmd)

    def _connect_adb(self):
        """Sets up ADB connection to the ARC container."""
        logging.info('Setting up adb connection.')
        # Generate and push keys for adb.
        # TODO(elijahtaylor): Extract this code to arc_common and de-duplicate
        # code in arc.py on the client side tests.
        key_path = os.path.join(self.tmpdir, 'test_key')
        pubkey_path = key_path + '.pub'
        self._run('adb', verbose=True, args=('keygen', pipes.quote(key_path)))
        with open(pubkey_path, 'r') as f:
            self._write_android_file(_ANDROID_ADB_KEYS_PATH, f.read())
        self._android_shell('restorecon ' + pipes.quote(_ANDROID_ADB_KEYS_PATH))
        os.environ['ADB_VENDOR_KEYS'] = key_path

        # Kill existing adb server to ensure that the env var is picked up.
        self._run('adb', verbose=True, args=('kill-server',))

        # This starts adbd.
        self._android_shell('setprop sys.usb.config mtp,adb')

        # Also let it be automatically started upon reboot.
        self._android_shell('setprop persist.sys.usb.config mtp,adb')

        # adbd may take some time to come up. Repeatedly try to connect to adb.
        utils.poll_for_condition(lambda: self._try_adb_connect(),
                                 exception=error.TestFail(
                                     'Error: Failed to set up adb connection'),
                                 timeout=_ADB_READY_TIMEOUT_SECONDS,
                                 sleep_interval=_ADB_POLLING_INTERVAL_SECONDS)

        logging.info('Successfully setup adb connection.')

    def _wait_for_arc_boot(self):
        """Wait until ARC is fully booted.

        Tests for the presence of the intent helper app to determine whether ARC
        has finished booting.
        """
        def _intent_helper_running():
            result = self._run('adb', args=('shell', 'pgrep', '-f',
                                            'org.chromium.arc.intent_helper'))
            return bool(result.stdout)
        utils.poll_for_condition(
            _intent_helper_running,
            exception=error.TestFail(
                'Error: Timed out waiting for intent helper.'),
            timeout=_ARC_READY_TIMEOUT_SECONDS,
            sleep_interval=_ARC_POLLING_INTERVAL_SECONDS)

    def _disable_adb_install_dialog(self):
        """Disables a dialog shown on adb install execution.

        By default, on adb install execution, "Allow Google to regularly check
        device activity ... " dialog is shown. It requires manual user action
        so that tests are blocked at the point.
        This method disables it.
        """
        logging.info('Disabling the adb install dialog.')
        result = self._run(
                'adb',
                verbose=True,
                args=(
                        'shell',
                        'settings',
                        'put',
                        'global',
                        'verifier_verify_adb_installs',
                        '0'))
        logging.info('Disable adb dialog: %s', result.stdout)

    def _ready_arc(self):
        """Ready ARC and adb for running tests via tradefed."""
        self._connect_adb()
        self._disable_adb_install_dialog()
        self._wait_for_arc_boot()

    def _safe_makedirs(self, path):
        """Creates a directory at |path| and its ancestors.

        Unlike os.makedirs(), ignore errors even if directories exist.
        """
        try:
            os.makedirs(path)
        except OSError as e:
            if not (e.errno == errno.EEXIST and os.path.isdir(path)):
                raise

    def _unzip(self, filename):
        """Unzip the file.

        The destination directory name will be the stem of filename.
        E.g., _unzip('foo/bar/baz.zip') will create directory at
        'foo/bar/baz', and then will inflate zip's content under the directory.
        If here is already a directory at the stem, that directory will be used.

        @param filename: Path to the zip archive.
        @return Path to the inflated directory.
        """
        destination = os.path.splitext(filename)[0]
        if os.path.isdir(destination):
            return destination
        self._safe_makedirs(destination)
        utils.run('unzip', args=('-d', destination, filename))
        return destination

    def _dir_size(self, directory):
        """Compute recursive size in bytes of directory."""
        size = 0
        for root, _, files in os.walk(directory):
            size += sum(os.path.getsize(os.path.join(root, name))
                    for name in files)
        return size

    def _clear_download_cache_if_needed(self):
        """Invalidates cache to prevent it from growing too large."""
        # If the cache is large enough to hold a working set, we can simply
        # delete everything without thrashing.
        # TODO(ihf): Investigate strategies like LRU.
        with lock(self._tradefed_cache_lock):
            size = self._dir_size(self._tradefed_cache)
            if size > _TRADEFED_CACHE_MAX_SIZE:
                logging.info('Current cache size=%d got too large. Clearing %s.'
                        , size, self._tradefed_cache)
                shutil.rmtree(self._tradefed_cache)
                self._safe_makedirs(self._tradefed_cache)
            else:
                logging.info('Current cache size=%d of %s.', size,
                        self._tradefed_cache)

    def _download_to_cache(self, uri):
        """Downloads the uri from the storage server.

        It always checks the cache for available binaries first and skips
        download if binaries are already in cache.

        The caller of this function is responsible for holding the cache lock.

        @param uri: The Google Storage or dl.google.com uri.
        @return Path to the downloaded object, name.
        """
        # Split uri into 3 pieces for use by gsutil and also by wget.
        parsed = urlparse.urlparse(uri)
        filename = os.path.basename(parsed.path)
        # We are hashing the uri instead of the binary. This is acceptable, as
        # the uris are supposed to contain version information and an object is
        # not supposed to be changed once created.
        output_dir = os.path.join(self._tradefed_cache,
                                  hashlib.md5(uri).hexdigest())
        output = os.path.join(output_dir, filename)
        # Check for existence of file.
        if os.path.exists(output):
            logging.info('Skipping download of %s, reusing %s.', uri, output)
            return output
        self._safe_makedirs(output_dir)

        if parsed.scheme not in ['gs', 'http', 'https']:
            raise error.TestFail('Error: Unknown download scheme %s' %
                                 parsed.scheme)
        if parsed.scheme in ['http', 'https']:
            logging.info('Using wget to download %s to %s.', uri, output_dir)
            # We are downloading 1 file at a time, hence using -O over -P.
            utils.run(
                'wget',
                args=(
                    '--report-speed=bits',
                    '-O',
                    output,
                    uri),
                verbose=True)
            return output

        if not client_utils.is_moblab():
            # If the machine can access to the storage server directly,
            # defer to "gsutil" for downloading.
            logging.info('Host %s not in lab. Downloading %s directly to %s.',
                    self._host.hostname, uri, output)
            # b/17445576: gsutil rsync of individual files is not implemented.
            utils.run('gsutil', args=('cp', uri, output), verbose=True)
            return output

        # We are in the moblab. Because the machine cannot access the storage
        # server directly, use dev server to proxy.
        logging.info('Host %s is in lab. Downloading %s by staging to %s.',
                self._host.hostname, uri, output)

        dirname = os.path.dirname(parsed.path)
        archive_url = '%s://%s%s' % (parsed.scheme, parsed.netloc, dirname)

        # First, request the devserver to download files into the lab network.
        # TODO(ihf): Switch stage_artifacts to honor rsync. Then we don't have
        # to shuffle files inside of tarballs.
        info = self._host.host_info_store.get()
        ds = dev_server.ImageServer.resolve(info.build)
        ds.stage_artifacts(info.build, files=[filename],
                           archive_url=archive_url)

        # Then download files from the dev server.
        # TODO(ihf): use rsync instead of wget. Are there 3 machines involved?
        # Itself, dev_server plus DUT? Or is there just no rsync in moblab?
        ds_src = '/'.join([ds.url(), 'static', dirname, filename])
        logging.info('dev_server URL: %s', ds_src)
        # Calls into DUT to pull uri from dev_server.
        utils.run(
                'wget',
                args=(
                        '--report-speed=bits',
                        '-O',
                        output,
                        ds_src),
                verbose=True)
        return output

    def _instance_copy(self, cache_path):
        """Makes a copy of a file from the (shared) cache to a wholy owned
        local instance. Also copies one level of cache directoy (MD5 named).
        """
        filename = os.path.basename(cache_path)
        dirname = os.path.basename(os.path.dirname(cache_path))
        instance_dir = os.path.join(self._tradefed_install, dirname)
        # Make sure destination directory is named the same.
        self._safe_makedirs(instance_dir)
        instance_path = os.path.join(instance_dir, filename)
        shutil.copyfile(cache_path, instance_path)
        return instance_path

    def _install_bundle(self, gs_uri):
        """Downloads a zip file, installs it and returns the local path."""
        if not gs_uri.endswith('.zip'):
            raise error.TestFail('Error: Not a .zip file %s.', gs_uri)
        # Atomic write through of file.
        with lock(self._tradefed_cache_lock):
            cache_path = self._download_to_cache(gs_uri)
            local = self._instance_copy(cache_path)

        unzipped = self._unzip(local)
        self._abi = 'x86' if 'x86-x86' in unzipped else 'arm'
        return unzipped

    def _install_files(self, gs_dir, files, permission):
        """Installs binary tools."""
        for filename in files:
            gs_uri = os.path.join(gs_dir, filename)
            # Atomic write through of file.
            with lock(self._tradefed_cache_lock):
                cache_path = self._download_to_cache(gs_uri)
                local = self._instance_copy(cache_path)
            os.chmod(local, permission)
            # Keep track of PATH.
            self._install_paths.append(os.path.dirname(local))

    def _copy_media(self, media):
        """Calls copy_media to push media files to DUT via adb."""
        logging.info('Copying media to device. This can take a few minutes.')
        copy_media = os.path.join(media, 'copy_media.sh')
        with pushd(media):
            try:
                self._run('file', args=('/bin/sh',), verbose=True,
                          ignore_status=True, timeout=60,
                          stdout_tee=utils.TEE_TO_LOGS,
                          stderr_tee=utils.TEE_TO_LOGS)
                self._run('sh', args=('--version',), verbose=True,
                          ignore_status=True, timeout=60,
                          stdout_tee=utils.TEE_TO_LOGS,
                          stderr_tee=utils.TEE_TO_LOGS)
            except:
                logging.warning('Could not obtain sh version.')
            self._run(
                'sh',
                args=('-e', copy_media, 'all'),
                timeout=7200,  # Wait at most 2h for download of media files.
                verbose=True,
                ignore_status=False,
                stdout_tee=utils.TEE_TO_LOGS,
                stderr_tee=utils.TEE_TO_LOGS)

    def _verify_media(self, media):
        """Verify that the local media directory matches the DUT.
        Used for debugging b/32978387 where we may see file corruption."""
        # TODO(ihf): Remove function once b/32978387 is resolved.
        # Find all files in the bbb_short and bbb_full directories, md5sum these
        # files and sort by filename, both on the DUT and on the local tree.
        logging.info('Computing md5 of remote media files.')
        remote = self._run('adb', args=('shell',
            'cd /sdcard/test; find ./bbb_short ./bbb_full -type f -print0 | '
            'xargs -0 md5sum | grep -v "\.DS_Store" | sort -k 2'))
        logging.info('Computing md5 of local media files.')
        local = self._run('/bin/sh', args=('-c',
            ('cd %s; find ./bbb_short ./bbb_full -type f -print0 | '
            'xargs -0 md5sum | grep -v "\.DS_Store" | sort -k 2') % media))

        # 'adb shell' terminates lines with CRLF. Normalize before comparing.
        if remote.stdout.replace('\r\n','\n') != local.stdout:
            logging.error('Some media files differ on DUT /sdcard/test vs. local.')
            logging.info('media=%s', media)
            logging.error('remote=%s', remote)
            logging.error('local=%s', local)
            # TODO(ihf): Return False.
            return True
        logging.info('Media files identical on DUT /sdcard/test vs. local.')
        return True

    def _push_media(self, CTS_URI):
        """Downloads, caches and pushed media files to DUT."""
        media = self._install_bundle(CTS_URI['media'])
        base = os.path.splitext(os.path.basename(CTS_URI['media']))[0]
        cts_media = os.path.join(media, base)
        # TODO(ihf): this really should measure throughput in Bytes/s.
        m = 'chromeos/autotest/infra_benchmark/cheets/push_media/duration'
        fields = {'success': False,
                  'dut_host_name': self._host.hostname}
        with metrics.SecondsTimer(m, fields=fields) as c:
            self._copy_media(cts_media)
            c['success'] = True
        if not self._verify_media(cts_media):
            raise error.TestFail('Error: saw corruption pushing media files.')

    def _run(self, *args, **kwargs):
        """Executes the given command line.

        To support SDK tools, such as adb or aapt, this adds _install_paths
        to the extra_paths. Before invoking this, ensure _install_files() has
        been called.
        """
        kwargs['extra_paths'] = (
                kwargs.get('extra_paths', []) + self._install_paths)
        return utils.run(*args, **kwargs)

    def _collect_tradefed_global_log(self, result, destination):
        """Collects the tradefed global log.

        @param result: The result object from utils.run.
        @param destination: Autotest result directory (destination of logs).
        """
        match = re.search(r'Saved log to /tmp/(tradefed_global_log_.*\.txt)',
                          result.stdout)
        if not match:
            logging.error('no tradefed_global_log file is found')
            return

        name = match.group(1)
        dest = os.path.join(destination, 'logs', 'tmp')
        self._safe_makedirs(dest)
        shutil.copy(os.path.join('/tmp', name), os.path.join(dest, name))

    def _parse_tradefed_datetime(self, result, summary=None):
        """Get the tradefed provided result ID consisting of a datetime stamp.

        Unfortunately we are unable to tell tradefed where to store the results.
        In the lab we have multiple instances of tradefed running in parallel
        writing results and logs to the same base directory. This function
        finds the identifier which tradefed used during the current run and
        returns it for further processing of result files.

        @param result: The result object from utils.run.
        @param summary: Test result summary from runs so far.
        @return datetime_id: The result ID chosen by tradefed.
                             Example: '2016.07.14_00.34.50'.
        """
        # This string is show for both 'run' and 'continue' after all tests.
        match = re.search(r': XML test result file generated at (\S+). Passed',
                result.stdout)
        if not (match and match.group(1)):
            # TODO(ihf): Find out if we ever recover something interesting in
            # this case. Otherwise delete it.
            # Try harder to find the remains. This string shows before all
            # tests but only with 'run', not 'continue'.
            logging.warning('XML test result file incomplete?')
            match = re.search(r': Created result dir (\S+)', result.stdout)
            if not (match and match.group(1)):
                error_msg = 'Test did not complete due to Chrome or ARC crash.'
                if summary:
                    error_msg += (' Test summary from previous runs: %s'
                            % summary)
                raise error.TestFail(error_msg)
        datetime_id = match.group(1)
        logging.info('Tradefed identified results and logs with %s.',
                     datetime_id)
        return datetime_id

    def _parse_tradefed_datetime_N(self, result, summary=None):
        """Get the tradefed provided result ID consisting of a datetime stamp.

        Unfortunately we are unable to tell tradefed where to store the results.
        In the lab we have multiple instances of tradefed running in parallel
        writing results and logs to the same base directory. This function
        finds the identifier which tradefed used during the current run and
        returns it for further processing of result files.

        @param result: The result object from utils.run.
        @param summary: Test result summary from runs so far.
        @return datetime_id: The result ID chosen by tradefed.
                             Example: '2016.07.14_00.34.50'.
        """
        # This string is show for both 'run' and 'continue' after all tests.
        match = re.search(r'(\d\d\d\d.\d\d.\d\d_\d\d.\d\d.\d\d)', result.stdout)
        if not (match and match.group(1)):
            error_msg = 'Error: Test did not complete. (Chrome or ARC crash?)'
            if summary:
                error_msg += (' Test summary from previous runs: %s'
                        % summary)
            raise error.TestFail(error_msg)
        datetime_id = match.group(1)
        logging.info('Tradefed identified results and logs with %s.',
                     datetime_id)
        return datetime_id

    def _parse_result(self, result, waivers=None):
        """Check the result from the tradefed output.

        This extracts the test pass/fail/executed list from the output of
        tradefed. It is up to the caller to handle inconsistencies.

        @param result: The result object from utils.run.
        @param waivers: a set() of tests which are permitted to fail.
        """
        # Parse the stdout to extract test status. In particular step over
        # similar output for each ABI and just look at the final summary.
        match = re.search(r'(XML test result file generated at (\S+). '
                 r'Passed (\d+), Failed (\d+), Not Executed (\d+))',
                 result.stdout)
        if not match:
            raise error.Test('Test log does not contain a summary.')

        passed = int(match.group(3))
        failed = int(match.group(4))
        not_executed = int(match.group(5))
        match = re.search(r'(Start test run of (\d+) packages, containing '
                          r'(\d+(?:,\d+)?) tests)', result.stdout)
        if match and match.group(3):
            tests = int(match.group(3).replace(',', ''))
        else:
            # Unfortunately this happens. Assume it made no other mistakes.
            logging.warning('Tradefed forgot to print number of tests.')
            tests = passed + failed + not_executed
        # TODO(rohitbm): make failure parsing more robust by extracting the list
        # of failing tests instead of searching in the result blob. As well as
        # only parse for waivers for the running ABI.
        if waivers:
            for testname in waivers:
                # TODO(dhaddock): Find a more robust way to apply waivers.
                fail_count = result.stdout.count(testname + ' FAIL')
                if fail_count:
                    if fail_count > 2:
                        raise error.TestFail('Error: There are too many '
                                             'failures found in the output to '
                                             'be valid for applying waivers. '
                                             'Please check output.')
                    failed -= fail_count
                    # To maintain total count consistency.
                    passed += fail_count
                    logging.info('Waived failure for %s %d time(s)',
                                 testname, fail_count)
        logging.info('tests=%d, passed=%d, failed=%d, not_executed=%d',
                tests, passed, failed, not_executed)
        if failed < 0:
            raise error.TestFail('Error: Internal waiver book keeping has '
                                 'become inconsistent.')
        return (tests, passed, failed, not_executed)

    # TODO(kinaba): Add unit test.
    def _parse_result_v2(self, result, accumulative_count=False, waivers=None):
        """Check the result from the tradefed-v2 output.

        This extracts the test pass/fail/executed list from the output of
        tradefed. It is up to the caller to handle inconsistencies.

        @param result: The result object from utils.run.
        @param accumulative_count: set True if using an old version of tradefed
                                   that prints test count in accumulative way.
        @param waivers: a set() of tests which are permitted to fail.
        """
        # Regular expressions for start/end messages of each test-run chunk.
        abi_re = r'armeabi-v7a|x86'
        # TODO(kinaba): use the current running module name.
        module_re = r'\S+'
        start_re = re.compile(r'(?:Start|Continu)ing (%s) %s with'
                              r' (\d+(?:,\d+)?) test' % (abi_re, module_re))
        end_re = re.compile(r'(%s) %s (?:complet|fail)ed in .*\.'
                            r' (\d+) passed, (\d+) failed, (\d+) not executed'
                            % (abi_re, module_re))

        # Records the result per each ABI.
        total_test = dict()
        total_pass = dict()
        total_fail = dict()
        last_notexec = dict()

        # ABI and the test count for the current chunk.
        abi = None
        ntest = None

        for line in result.stdout.splitlines():
            # Beginning of a chunk of tests.
            match = start_re.search(line)
            if match:
               if abi:
                   raise error.TestFail('Error: Unexpected test start: ' + line)
               abi = match.group(1)
               ntest = int(match.group(2).replace(',',''))
            else:
               # End of the current chunk.
               match = end_re.search(line)
               if not match:
                   continue

               if abi != match.group(1):
                   raise error.TestFail('Error: Unexpected test end: ' + line)
               npass, nfail, nnotexec = map(int, match.group(2,3,4))

               # When the test crashes too ofen, tradefed seems to finish the
               # iteration by running "0 tests, 0 passed, ...". Do not count
               # that in.
               if ntest > 0:
                   if accumulative_count:
                       total_test[abi] = ntest
                       total_pass[abi] = npass
                       total_fail[abi] = nfail
                   else:
                       total_test[abi] = (total_test.get(abi, 0) + ntest -
                           last_notexec.get(abi, 0))
                       total_pass[abi] = total_pass.get(abi, 0) + npass
                       total_fail[abi] = total_fail.get(abi, 0) + nfail
                   last_notexec[abi] = nnotexec
               abi = None

        if abi:
            raise error.TestFail('Error: No end message for the last chunk.')

        # TODO(rohitbm): make failure parsing more robust by extracting the list
        # of failing tests instead of searching in the result blob. As well as
        # only parse for waivers for the running ABI.
        waived = 0
        if waivers:
            abis = total_test.keys()
            for testname in waivers:
                # TODO(dhaddock): Find a more robust way to apply waivers.
                fail_count = (result.stdout.count(testname + ' FAIL') +
                              result.stdout.count(testname + ' fail'))
                if fail_count:
                    if fail_count > len(abis):
                        raise error.TestFail('Error: Found %d failures for %s '
                                             'but there are only %d abis: %s' %
                                             (fail_count, testname, len(abis),
                                             abis))
                    waived += fail_count
                    logging.info('Waived failure for %s %d time(s)',
                                 testname, fail_count)
        counts = tuple(sum(count_per_abi.values()) for count_per_abi in
            (total_test, total_pass, total_fail, last_notexec)) + (waived,)
        msg = ('tests=%d, passed=%d, failed=%d, not_executed=%d, waived=%d' %
               counts)
        logging.info(msg)
        if counts[2] - waived < 0:
            raise error.TestFail('Error: Internal waiver bookkeeping has '
                                 'become inconsistent (%s)' % msg)
        return counts

    def _collect_logs(self, repository, datetime, destination):
        """Collects the tradefed logs.

        It is legal to collect the same logs multiple times. This is normal
        after 'tradefed continue' updates existing logs with new results.

        @param repository: Full path to tradefeds output on disk.
        @param datetime: The identifier which tradefed assigned to the run.
                         Currently this looks like '2016.07.14_00.34.50'.
        @param destination: Autotest result directory (destination of logs).
        """
        logging.info('Collecting tradefed testResult.xml and logs to %s.',
                     destination)
        repository_results = os.path.join(repository, 'results')
        repository_logs = os.path.join(repository, 'logs')
        # Because other tools rely on the currently chosen Google storage paths
        # we need to keep destination_results in
        # cheets_CTS.*/results/android-cts/2016.mm.dd_hh.mm.ss(/|.zip)
        # and destination_logs in
        # cheets_CTS.*/results/android-cts/logs/2016.mm.dd_hh.mm.ss/
        destination_results = destination
        destination_results_datetime = os.path.join(destination_results,
                                                    datetime)
        destination_results_datetime_zip = destination_results_datetime + '.zip'
        destination_logs = os.path.join(destination, 'logs')
        destination_logs_datetime = os.path.join(destination_logs, datetime)
        # We may have collected the same logs before, clean old versions.
        if os.path.exists(destination_results_datetime_zip):
            os.remove(destination_results_datetime_zip)
        if os.path.exists(destination_results_datetime):
            shutil.rmtree(destination_results_datetime)
        if os.path.exists(destination_logs_datetime):
            shutil.rmtree(destination_logs_datetime)
        shutil.copytree(
                os.path.join(repository_results, datetime),
                destination_results_datetime)
        # Copying the zip file has to happen after the tree so the destination
        # directory is available.
        shutil.copy(
                os.path.join(repository_results, datetime) + '.zip',
                destination_results_datetime_zip)
        shutil.copytree(
                os.path.join(repository_logs, datetime),
                destination_logs_datetime)

    def _get_expected_failures(self, directory):
        """Return a list of expected failures.

        @return: a list of expected failures.
        """
        logging.info('Loading expected failures from %s.', directory)
        expected_fail_dir = os.path.join(self.bindir, directory)
        expected_fail_files = glob.glob(expected_fail_dir + '/*.' + self._abi)
        expected_failures = set()
        for expected_fail_file in expected_fail_files:
            try:
                file_path = os.path.join(expected_fail_dir, expected_fail_file)
                with open(file_path) as f:
                    lines = set(f.read().splitlines())
                    logging.info('Loaded %d expected failures from %s',
                                 len(lines), expected_fail_file)
                    expected_failures |= lines
            except IOError as e:
                logging.error('Error loading %s (%s).', file_path, e.strerror)
        logging.info('Finished loading expected failures: %s',
                     expected_failures)
        return expected_failures
