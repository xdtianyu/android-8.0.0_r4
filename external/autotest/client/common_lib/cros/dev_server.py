# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from distutils import version
import cStringIO
import HTMLParser
import httplib
import json
import logging
import multiprocessing
import os
import re
import socket
import time
import urllib2
import urlparse

from autotest_lib.client.bin import utils as site_utils
from autotest_lib.client.common_lib import android_utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import global_config
from autotest_lib.client.common_lib import utils
from autotest_lib.client.common_lib.cros import retry
from autotest_lib.server import utils as server_utils
# TODO(cmasone): redo this class using requests module; http://crosbug.com/30107

try:
    from chromite.lib import metrics
except ImportError:
    metrics = utils.metrics_mock


CONFIG = global_config.global_config
# This file is generated at build time and specifies, per suite and per test,
# the DEPENDENCIES list specified in each control file.  It's a dict of dicts:
# {'bvt':   {'/path/to/autotest/control/site_tests/test1/control': ['dep1']}
#  'suite': {'/path/to/autotest/control/site_tests/test2/control': ['dep2']}
#  'power': {'/path/to/autotest/control/site_tests/test1/control': ['dep1'],
#            '/path/to/autotest/control/site_tests/test3/control': ['dep3']}
# }
DEPENDENCIES_FILE = 'test_suites/dependency_info'
# Number of seconds for caller to poll devserver's is_staged call to check if
# artifacts are staged.
_ARTIFACT_STAGE_POLLING_INTERVAL = 5
# Artifacts that should be staged when client calls devserver RPC to stage an
# image.
_ARTIFACTS_TO_BE_STAGED_FOR_IMAGE = 'full_payload,test_suites,stateful'
# Artifacts that should be staged when client calls devserver RPC to stage an
# image with autotest artifact.
_ARTIFACTS_TO_BE_STAGED_FOR_IMAGE_WITH_AUTOTEST = ('full_payload,test_suites,'
                                                   'control_files,stateful,'
                                                   'autotest_packages')
# Artifacts that should be staged when client calls devserver RPC to stage an
# Android build.
_BRILLO_ARTIFACTS_TO_BE_STAGED_FOR_IMAGE = ('zip_images,vendor_partitions')
SKIP_DEVSERVER_HEALTH_CHECK = CONFIG.get_config_value(
        'CROS', 'skip_devserver_health_check', type=bool)
# Number of seconds for the call to get devserver load to time out.
TIMEOUT_GET_DEVSERVER_LOAD = 2.0

# Android artifact path in devserver
ANDROID_BUILD_NAME_PATTERN = CONFIG.get_config_value(
        'CROS', 'android_build_name_pattern', type=str).replace('\\', '')

# Return value from a devserver RPC indicating the call succeeded.
SUCCESS = 'Success'

# The timeout minutes for a given devserver ssh call.
DEVSERVER_SSH_TIMEOUT_MINS = 1

# Error message for invalid devserver response.
ERR_MSG_FOR_INVALID_DEVSERVER_RESPONSE = 'Proxy Error'

# Error message for devserver call timedout.
ERR_MSG_FOR_TIMED_OUT_CALL = 'timeout'

# The timeout minutes for waiting a devserver staging.
DEVSERVER_IS_STAGING_RETRY_MIN = 100

# The timeout minutes for waiting a DUT auto-update finished.
DEVSERVER_IS_CROS_AU_FINISHED_TIMEOUT_MIN = 100

# The total times of devserver triggering CrOS auto-update.
AU_RETRY_LIMIT = 2

# Number of seconds for caller to poll devserver's get_au_status call to
# check if cros auto-update is finished.
CROS_AU_POLLING_INTERVAL = 10

# Number of seconds for intervals between retrying auto-update calls.
CROS_AU_RETRY_INTERVAL = 20

# The file name for auto-update logs.
CROS_AU_LOG_FILENAME = 'CrOS_update_%s_%s.log'

# Provision error patterns.
# People who see this should know that they shouldn't change these
# classification strings. These strings are used for monitoring provision
# failures. Any changes may mess up the stats.
_EXCEPTION_PATTERNS = [
        # Raised when devserver portfile does not exist on host.
        (r".*Devserver portfile does not exist!.*$",
         '(1) Devserver portfile does not exist on host'),
        # Raised when devserver cannot copy packages to host.
        (r".*Could not copy .* to device.*$",
         '(2) Cannot copy packages to host'),
        # Raised when devserver fails to run specific commands on host.
        (r".*cwd=None, extra env=\{'LC_MESSAGES': 'C'\}.*$",
         '(3) Fail to run specific command on host'),
        # Raised when new build fails to boot on the host.
        (r'.*RootfsUpdateError: Build .* failed to boot on.*$',
         '(4) Build failed to boot on host'),
        # Raised when the auto-update process is timed out.
        (r'.*The CrOS auto-update process is timed out, '
         'thus will be terminated.*$',
         '(5) Auto-update is timed out'),
        # Raised when the host is not pingable.
        (r".*DeviceNotPingableError.*$",
         '(6) Host is not pingable during auto-update'),
        # Raised when hosts have unexpected status after rootfs update.
        (r'.*Update failed with unexpected update status: '
         'UPDATE_STATUS_IDLE.*$',
         '(7) Host has unexpected status: UPDATE_STATUS_IDLE after rootfs '
         'update'),
        # Raised when devserver returns non-json response to shard/drone.
        (r'.*No JSON object could be decoded.*$',
         '(8) Devserver returned non-json object'),
        # Raised when devserver loses host's ssh connection
        (r'.*SSHConnectionError\: .* port 22\: Connection timed out.*$',
         "(9) Devserver lost host's ssh connection"),
        # Raised when error happens in writing files to host
        (r'.*Write failed\: Broken pipe.*$',
         "(10) Broken pipe while writing or connecting to host")]

PREFER_LOCAL_DEVSERVER = CONFIG.get_config_value(
        'CROS', 'prefer_local_devserver', type=bool, default=False)

ENABLE_SSH_CONNECTION_FOR_DEVSERVER = CONFIG.get_config_value(
        'CROS', 'enable_ssh_connection_for_devserver', type=bool,
        default=False)

# Directory to save auto-update logs
AUTO_UPDATE_LOG_DIR = 'autoupdate_logs'

DEFAULT_SUBNET_MASKBIT = 19


class DevServerException(Exception):
    """Raised when the dev server returns a non-200 HTTP response."""
    pass


class DevServerOverloadException(Exception):
    """Raised when the dev server returns a 502 HTTP response."""
    pass


class MarkupStripper(HTMLParser.HTMLParser):
    """HTML parser that strips HTML tags, coded characters like &amp;

    Works by, basically, not doing anything for any tags, and only recording
    the content of text nodes in an internal data structure.
    """
    def __init__(self):
        self.reset()
        self.fed = []


    def handle_data(self, d):
        """Consume content of text nodes, store it away."""
        self.fed.append(d)


    def get_data(self):
        """Concatenate and return all stored data."""
        return ''.join(self.fed)


def _strip_http_message(message):
    """Strip the HTTP marker from the an HTTP message.

    @param message: A string returned by an HTTP call.

    @return: A string with HTTP marker being stripped.
    """
    strip = MarkupStripper()
    try:
        strip.feed(message.decode('utf_32'))
    except UnicodeDecodeError:
        strip.feed(message)
    return strip.get_data()


def _get_image_storage_server():
    return CONFIG.get_config_value('CROS', 'image_storage_server', type=str)


def _get_canary_channel_server():
    """
    Get the url of the canary-channel server,
    eg: gsutil://chromeos-releases/canary-channel/<board>/<release>

    @return: The url to the canary channel server.
    """
    return CONFIG.get_config_value('CROS', 'canary_channel_server', type=str)


def _get_storage_server_for_artifacts(artifacts=None):
    """Gets the appropriate storage server for the given artifacts.

    @param artifacts: A list of artifacts we need to stage.
    @return: The address of the storage server that has these artifacts.
             The default image storage server if no artifacts are specified.
    """
    factory_artifact = global_config.global_config.get_config_value(
            'CROS', 'factory_artifact', type=str, default='')
    if artifacts and factory_artifact and factory_artifact in artifacts:
        return _get_canary_channel_server()
    return _get_image_storage_server()


def _reverse_lookup_from_config(address):
    """Look up hostname for the given IP address.

    This uses the hostname-address map from the config file.

    If multiple hostnames map to the same IP address, the first one
    defined in the configuration file takes precedence.

    @param address: IP address string
    @returns: hostname string, or original input if not found
    """
    for hostname, addr in _get_hostname_addr_map().iteritems():
        if addr == address:
            return hostname
    return address


def _get_hostname_addr_map():
    """Get hostname address mapping from config.

    @return: dict mapping server hostnames to addresses
    """
    return CONFIG.get_section_as_dict('HOSTNAME_ADDR_MAP')


def _get_dev_server_list():
    return CONFIG.get_config_value('CROS', 'dev_server', type=list, default=[])


def _get_crash_server_list():
    return CONFIG.get_config_value('CROS', 'crash_server', type=list,
        default=[])


def remote_devserver_call(timeout_min=DEVSERVER_IS_STAGING_RETRY_MIN,
                          exception_to_raise=DevServerException):
    """A decorator to use with remote devserver calls.

    This decorator converts urllib2.HTTPErrors into DevServerExceptions
    with any embedded error info converted into plain text. The method
    retries on urllib2.URLError or error.CmdError to avoid devserver flakiness.
    """
    #pylint: disable=C0111

    def inner_decorator(method):
        label = method.__name__ if hasattr(method, '__name__') else None
        def metrics_wrapper(*args, **kwargs):
            @retry.retry((urllib2.URLError, error.CmdError,
                          DevServerOverloadException),
                         timeout_min=timeout_min,
                         exception_to_raise=exception_to_raise,
                        label=label)
            def wrapper():
                """This wrapper actually catches the HTTPError."""
                try:
                    return method(*args, **kwargs)
                except urllib2.HTTPError as e:
                    error_markup = e.read()
                    raise DevServerException(_strip_http_message(error_markup))

            try:
                return wrapper()
            except Exception as e:
                if ERR_MSG_FOR_TIMED_OUT_CALL in str(e):
                    dev_server = None
                    if args and isinstance(args[0], DevServer):
                        dev_server = args[0].hostname
                    elif 'devserver' in kwargs:
                        dev_server = get_hostname(kwargs['devserver'])

                    logging.debug('RPC call %s has timed out on devserver %s.',
                                  label, dev_server)
                    c = metrics.Counter(
                            'chromeos/autotest/devserver/call_timeout')
                    c.increment(fields={'dev_server': dev_server,
                                        'healthy': label})

                raise

        return metrics_wrapper

    return inner_decorator


def get_hostname(url):
    """Get the hostname portion of a URL

    schema://hostname:port/path

    @param url: a Url string
    @return: a hostname string
    """
    return urlparse.urlparse(url).hostname


class DevServer(object):
    """Base class for all DevServer-like server stubs.

    This is the base class for interacting with all Dev Server-like servers.
    A caller should instantiate a sub-class of DevServer with:

    host = SubClassServer.resolve(build)
    server = SubClassServer(host)
    """
    _MIN_FREE_DISK_SPACE_GB = 20
    _MAX_APACHE_CLIENT_COUNT = 75
    # Threshold for the CPU load percentage for a devserver to be selected.
    MAX_CPU_LOAD = 80.0
    # Threshold for the network IO, set to 80MB/s
    MAX_NETWORK_IO = 1024 * 1024 * 80
    DISK_IO = 'disk_total_bytes_per_second'
    NETWORK_IO = 'network_total_bytes_per_second'
    CPU_LOAD = 'cpu_percent'
    FREE_DISK = 'free_disk'
    AU_PROCESS = 'au_process_count'
    STAGING_THREAD_COUNT = 'staging_thread_count'
    APACHE_CLIENT_COUNT = 'apache_client_count'


    def __init__(self, devserver):
        self._devserver = devserver


    def url(self):
        """Returns the url for this devserver."""
        return self._devserver


    @property
    def hostname(self):
        """Return devserver hostname parsed from the devserver URL.

        Note that this is likely parsed from the devserver URL from
        shadow_config.ini, meaning that the "hostname" part of the
        devserver URL is actually an IP address.

        @return hostname string
        """
        return get_hostname(self.url())


    @property
    def resolved_hostname(self):
        """Return devserver hostname, resolved from its IP address.

        Unlike the hostname property, this property attempts to look up
        the proper hostname from the devserver IP address.  If lookup
        fails, then fall back to whatever the hostname property would
        have returned.

        @return hostname string
        """
        return _reverse_lookup_from_config(self.hostname)


    @staticmethod
    def get_server_url(url):
        """Get the devserver url from a repo url, which includes build info.

        @param url: A job repo url.

        @return A devserver url, e.g., http://127.0.0.10:8080
        """
        res = urlparse.urlparse(url)
        if res.netloc:
            return res.scheme + '://' + res.netloc


    @classmethod
    def get_devserver_load_wrapper(cls, devserver, timeout_sec, output):
        """A wrapper function to call get_devserver_load in parallel.

        @param devserver: url of the devserver.
        @param timeout_sec: Number of seconds before time out the devserver
                            call.
        @param output: An output queue to save results to.
        """
        load = cls.get_devserver_load(devserver, timeout_min=timeout_sec/60.0)
        if load:
            load['devserver'] = devserver
        output.put(load)


    @classmethod
    def get_devserver_load(cls, devserver,
                           timeout_min=DEVSERVER_SSH_TIMEOUT_MINS):
        """Returns True if the |devserver| is healthy to stage build.

        @param devserver: url of the devserver.
        @param timeout_min: How long to wait in minutes before deciding the
                            the devserver is not up (float).

        @return: A dictionary of the devserver's load.

        """
        call = cls._build_call(devserver, 'check_health')
        @remote_devserver_call(timeout_min=timeout_min)
        def get_load(devserver=devserver):
            """Inner method that makes the call."""
            return cls.run_call(call, timeout=timeout_min*60)

        try:
            return json.load(cStringIO.StringIO(get_load(devserver=devserver)))
        except Exception as e:
            logging.error('Devserver call failed: "%s", timeout: %s seconds,'
                          ' Error: %s', call, timeout_min * 60, e)


    @classmethod
    def is_free_disk_ok(cls, load):
        """Check if a devserver has enough free disk.

        @param load: A dict of the load of the devserver.

        @return: True if the devserver has enough free disk or disk check is
                 skipped in global config.

        """
        if SKIP_DEVSERVER_HEALTH_CHECK:
            logging.debug('devserver health check is skipped.')
        elif load[cls.FREE_DISK] < cls._MIN_FREE_DISK_SPACE_GB:
            return False

        return True


    @classmethod
    def is_apache_client_count_ok(cls, load):
        """Check if a devserver has enough Apache connections available.

        Apache server by default has maximum of 150 concurrent connections. If
        a devserver has too many live connections, it likely indicates the
        server is busy handling many long running download requests, e.g.,
        downloading stateful partitions. It is better not to add more requests
        to it.

        @param load: A dict of the load of the devserver.

        @return: True if the devserver has enough Apache connections available,
                 or disk check is skipped in global config.

        """
        if SKIP_DEVSERVER_HEALTH_CHECK:
            logging.debug('devserver health check is skipped.')
        elif cls.APACHE_CLIENT_COUNT not in load:
            logging.debug('Apache client count is not collected from devserver.')
        elif (load[cls.APACHE_CLIENT_COUNT] >
              cls._MAX_APACHE_CLIENT_COUNT):
            return False

        return True


    @classmethod
    def devserver_healthy(cls, devserver,
                          timeout_min=DEVSERVER_SSH_TIMEOUT_MINS):
        """Returns True if the |devserver| is healthy to stage build.

        @param devserver: url of the devserver.
        @param timeout_min: How long to wait in minutes before deciding the
                            the devserver is not up (float).

        @return: True if devserver is healthy. Return False otherwise.

        """
        c = metrics.Counter('chromeos/autotest/devserver/devserver_healthy')
        reason = ''
        healthy = False
        load = cls.get_devserver_load(devserver, timeout_min=timeout_min)
        try:
            if not load:
                # Failed to get the load of devserver.
                reason = '(1) Failed to get load.'
                return False

            apache_ok = cls.is_apache_client_count_ok(load)
            if not apache_ok:
                reason = '(2) Apache client count too high.'
                logging.error('Devserver check_health failed. Live Apache client '
                              'count is too high: %d.',
                              load[cls.APACHE_CLIENT_COUNT])
                return False

            disk_ok = cls.is_free_disk_ok(load)
            if not disk_ok:
                reason = '(3) Disk space too low.'
                logging.error('Devserver check_health failed. Free disk space is '
                              'low. Only %dGB is available.',
                              load[cls.FREE_DISK])
            healthy = bool(disk_ok)
            return disk_ok
        finally:
            c.increment(fields={'dev_server': cls(devserver).resolved_hostname,
                                'healthy': healthy,
                                'reason': reason})
            # Monitor how many AU processes the devserver is currently running.
            if load is not None and load.get(DevServer.AU_PROCESS):
                c_au = metrics.Gauge(
                        'chromeos/autotest/devserver/devserver_au_count')
                c_au.set(
                    load.get(DevServer.AU_PROCESS),
                    fields={'dev_server': cls(devserver).resolved_hostname})


    @staticmethod
    def _build_call(host, method, **kwargs):
        """Build a URL to |host| that calls |method|, passing |kwargs|.

        Builds a URL that calls |method| on the dev server defined by |host|,
        passing a set of key/value pairs built from the dict |kwargs|.

        @param host: a string that is the host basename e.g. http://server:90.
        @param method: the dev server method to call.
        @param kwargs: a dict mapping arg names to arg values.
        @return the URL string.
        """
        argstr = '&'.join(map(lambda x: "%s=%s" % x, kwargs.iteritems()))
        return "%(host)s/%(method)s?%(argstr)s" % dict(
                host=host, method=method, argstr=argstr)


    def build_call(self, method, **kwargs):
        """Builds a devserver RPC string that is used by 'run_call()'.

        @param method: remote devserver method to call.
        """
        return self._build_call(self._devserver, method, **kwargs)


    @classmethod
    def build_all_calls(cls, method, **kwargs):
        """Builds a list of URLs that makes RPC calls on all devservers.

        Build a URL that calls |method| on the dev server, passing a set
        of key/value pairs built from the dict |kwargs|.

        @param method: the dev server method to call.
        @param kwargs: a dict mapping arg names to arg values

        @return the URL string
        """
        calls = []
        # Note we use cls.servers as servers is class specific.
        for server in cls.servers():
            if cls.devserver_healthy(server):
                calls.append(cls._build_call(server, method, **kwargs))

        return calls


    @classmethod
    def run_call(cls, call, readline=False, timeout=None):
        """Invoke a given devserver call using urllib.open.

        Open the URL with HTTP, and return the text of the response. Exceptions
        may be raised as for urllib2.urlopen().

        @param call: a url string that calls a method to a devserver.
        @param readline: whether read http response line by line.
        @param timeout: The timeout seconds for this urlopen call.

        @return the results of this call.
        """
        if timeout is not None:
            return utils.urlopen_socket_timeout(
                    call, timeout=timeout).read()
        elif readline:
            response = urllib2.urlopen(call)
            return [line.rstrip() for line in response]
        else:
            return urllib2.urlopen(call).read()


    @staticmethod
    def servers():
        """Returns a list of servers that can serve as this type of server."""
        raise NotImplementedError()


    @classmethod
    def get_devservers_in_same_subnet(cls, ip, mask_bits=DEFAULT_SUBNET_MASKBIT,
                                      unrestricted_only=False):
        """Get the devservers in the same subnet of the given ip.

        @param ip: The IP address of a dut to look for devserver.
        @param mask_bits: Number of mask bits. Default is 19.
        @param unrestricted_only: Set to True to select from devserver in
                unrestricted subnet only. Default is False.

        @return: A list of devservers in the same subnet of the given ip.

        """
        # server from cls.servers() is a URL, e.g., http://10.1.1.10:8082, so
        # we need a dict to return the full devserver path once the IPs are
        # filtered in get_servers_in_same_subnet.
        server_names = {}
        all_devservers = []
        devservers = (cls.get_unrestricted_devservers() if unrestricted_only
                      else cls.servers())
        for server in devservers:
            server_name = get_hostname(server)
            server_names[server_name] = server
            all_devservers.append(server_name)
        devservers = utils.get_servers_in_same_subnet(ip, mask_bits,
                                                      all_devservers)
        return [server_names[s] for s in devservers]


    @classmethod
    def get_unrestricted_devservers(
                cls, restricted_subnets=utils.RESTRICTED_SUBNETS):
        """Get the devservers not in any restricted subnet specified in
        restricted_subnets.

        @param restricted_subnets: A list of restriected subnets.

        @return: A list of devservers not in any restricted subnet.

        """
        if not restricted_subnets:
            return cls.servers()

        devservers = []
        for server in cls.servers():
            server_name = get_hostname(server)
            if not utils.get_restricted_subnet(server_name, restricted_subnets):
                devservers.append(server)
        return devservers


    @classmethod
    def get_healthy_devserver(cls, build, devservers, ban_list=None):
        """"Get a healthy devserver instance from the list of devservers.

        @param build: The build (e.g. x86-mario-release/R18-1586.0.0-a1-b1514).
        @param devservers: The devserver list to be chosen out a healthy one.
        @param ban_list: The blacklist of devservers we don't want to choose.
                Default is None.

        @return: A DevServer object of a healthy devserver. Return None if no
                healthy devserver is found.

        """
        while devservers:
            hash_index = hash(build) % len(devservers)
            devserver = devservers.pop(hash_index)
            if ban_list and devserver in ban_list:
                continue

            if cls.devserver_healthy(devserver):
                return cls(devserver)


    @classmethod
    def get_available_devservers(cls, hostname=None,
                                 prefer_local_devserver=PREFER_LOCAL_DEVSERVER,
                                 restricted_subnets=utils.RESTRICTED_SUBNETS):
        """Get devservers in the same subnet of the given hostname.

        @param hostname: Hostname of a DUT to choose devserver for.

        @return: A tuple of (devservers, can_retry), devservers is a list of
                 devservers that's available for the given hostname. can_retry
                 is a flag that indicate if caller can retry the selection of
                 devserver if no devserver in the returned devservers can be
                 used. For example, if hostname is in a restricted subnet,
                 can_retry will be False.
        """
        host_ip = None
        if hostname:
            host_ip = site_utils.get_ip_address(hostname)
            if not host_ip:
                logging.error('Failed to get IP address of %s. Will pick a '
                              'devserver without subnet constraint.', hostname)

        if not host_ip:
            return cls.get_unrestricted_devservers(restricted_subnets), False

        # Go through all restricted subnet settings and check if the DUT is
        # inside a restricted subnet. If so, only return the devservers in the
        # restricted subnet and doesn't allow retry.
        if host_ip and restricted_subnets:
            for subnet_ip, mask_bits in restricted_subnets:
                if utils.is_in_same_subnet(host_ip, subnet_ip, mask_bits):
                    logging.debug('The host %s (%s) is in a restricted subnet. '
                                  'Try to locate a devserver inside subnet '
                                  '%s:%d.', hostname, host_ip, subnet_ip,
                                  mask_bits)
                    devservers = cls.get_devservers_in_same_subnet(
                            subnet_ip, mask_bits)
                    return devservers, False

        # If prefer_local_devserver is set to True and the host is not in
        # restricted subnet, pick a devserver in the same subnet if possible.
        # Set can_retry to True so it can pick a different devserver if all
        # devservers in the same subnet are down.
        if prefer_local_devserver:
            return (cls.get_devservers_in_same_subnet(
                    host_ip, DEFAULT_SUBNET_MASKBIT, True), True)

        return cls.get_unrestricted_devservers(restricted_subnets), False


    @classmethod
    def resolve(cls, build, hostname=None, ban_list=None):
        """"Resolves a build to a devserver instance.

        @param build: The build (e.g. x86-mario-release/R18-1586.0.0-a1-b1514).
        @param hostname: The hostname of dut that requests a devserver. It's
                         used to make sure a devserver in the same subnet is
                         preferred.
        @param ban_list: The blacklist of devservers shouldn't be chosen.

        @raise DevServerException: If no devserver is available.
        """
        tried_devservers = set()
        devservers, can_retry = cls.get_available_devservers(hostname)
        if devservers:
            tried_devservers |= set(devservers)

        devserver = cls.get_healthy_devserver(build, devservers,
                                              ban_list=ban_list)

        if not devserver and can_retry:
            # Find available devservers without dut location constrain.
            devservers, _ = cls.get_available_devservers()
            devserver = cls.get_healthy_devserver(build, devservers,
                                                  ban_list=ban_list)
            if devservers:
                tried_devservers |= set(devservers)
        if devserver:
            return devserver
        else:
            error_msg = ('All devservers are currently down: %s. '
                         'dut hostname: %s' %
                         (tried_devservers, hostname))
            logging.error(error_msg)
            raise DevServerException(error_msg)


    @classmethod
    def random(cls):
        """Return a random devserver that's available.

        Devserver election in `resolve` method is based on a hash of the
        build that a caller wants to stage. The purpose is that different
        callers requesting for the same build can get the same devserver,
        while the lab is able to distribute different builds across all
        devservers. That helps to reduce the duplication of builds across
        all devservers.
        This function returns a random devserver, by passing a random
        pseudo build name to `resolve `method.
        """
        return cls.resolve(build=str(time.time()))


class CrashServer(DevServer):
    """Class of DevServer that symbolicates crash dumps."""

    @staticmethod
    def servers():
        return _get_crash_server_list()


    @remote_devserver_call()
    def symbolicate_dump(self, minidump_path, build):
        """Ask the devserver to symbolicate the dump at minidump_path.

        Stage the debug symbols for |build| and, if that works, ask the
        devserver to symbolicate the dump at |minidump_path|.

        @param minidump_path: the on-disk path of the minidump.
        @param build: The build (e.g. x86-mario-release/R18-1586.0.0-a1-b1514)
                      whose debug symbols are needed for symbolication.
        @return The contents of the stack trace
        @raise DevServerException upon any return code that's not HTTP OK.
        """
        try:
            import requests
        except ImportError:
            logging.warning("Can't 'import requests' to connect to dev server.")
            return ''
        f = {'dev_server': self.resolved_hostname}
        c = metrics.Counter('chromeos/autotest/crashserver/symbolicate_dump')
        c.increment(fields=f)
        # Symbolicate minidump.
        m = 'chromeos/autotest/crashserver/symbolicate_dump_duration'
        with metrics.SecondsTimer(m, fields=f):
            call = self.build_call('symbolicate_dump',
                                   archive_url=_get_image_storage_server() + build)
            request = requests.post(
                    call, files={'minidump': open(minidump_path, 'rb')})
            if request.status_code == requests.codes.OK:
                return request.text

        error_fd = cStringIO.StringIO(request.text)
        raise urllib2.HTTPError(
                call, request.status_code, request.text, request.headers,
                error_fd)


    @classmethod
    def get_available_devservers(cls, hostname):
        """Get all available crash servers.

        Crash server election doesn't need to count the location of hostname.

        @param hostname: Hostname of a DUT to choose devserver for.

        @return: A tuple of (all crash servers, False). can_retry is set to
                 False, as all crash servers are returned. There is no point to
                 retry.
        """
        return cls.servers(), False


class ImageServerBase(DevServer):
    """Base class for devservers used to stage builds.

    CrOS and Android builds are staged in different ways as they have different
    sets of artifacts. This base class abstracts the shared functions between
    the two types of ImageServer.
    """

    @classmethod
    def servers(cls):
        """Returns a list of servers that can serve as a desired type of
        devserver.
        """
        return _get_dev_server_list()


    def _get_image_url(self, image):
        """Returns the url of the directory for this image on the devserver.

        @param image: the image that was fetched.
        """
        image = self.translate(image)
        url_pattern = CONFIG.get_config_value('CROS', 'image_url_pattern',
                                              type=str)
        return (url_pattern % (self.url(), image)).replace('update', 'static')


    @staticmethod
    def create_metadata(server_name, image, artifacts=None, files=None):
        """Create a metadata dictionary given the staged items.

        The metadata can be send to metadata db along with stats.

        @param server_name: name of the devserver, e.g 172.22.33.44.
        @param image: The name of the image.
        @param artifacts: A list of artifacts.
        @param files: A list of files.

        @return A metadata dictionary.

        """
        metadata = {'devserver': server_name,
                    'image': image,
                    '_type': 'devserver'}
        if artifacts:
            metadata['artifacts'] = ' '.join(artifacts)
        if files:
            metadata['files'] = ' '.join(files)
        return metadata


    @classmethod
    def run_ssh_call(cls, call, readline=False, timeout=None):
        """Construct an ssh-based rpc call, and execute it.

        @param call: a url string that calls a method to a devserver.
        @param readline: whether read http response line by line.
        @param timeout: The timeout seconds for ssh call.

        @return the results of this call.
        """
        hostname = get_hostname(call)
        ssh_call = 'ssh %s \'curl "%s"\'' % (hostname, utils.sh_escape(call))
        timeout_seconds = timeout if timeout else DEVSERVER_SSH_TIMEOUT_MINS*60
        try:
            result = utils.run(ssh_call, timeout=timeout_seconds)
        except error.CmdError as e:
            logging.debug('Error occurred with exit_code %d when executing the '
                          'ssh call: %s.', e.result_obj.exit_status,
                          e.result_obj.stderr)
            c = metrics.Counter('chromeos/autotest/devserver/ssh_failure')
            c.increment(fields={'dev_server': hostname})
            raise
        response = result.stdout

        # If the curl command's returned HTTP response contains certain
        # exception string, raise the DevServerException of the response.
        if 'DownloaderException' in response:
            raise DevServerException(_strip_http_message(response))

        if readline:
            # Remove line terminators and trailing whitespace
            response = response.splitlines()
            return [line.rstrip() for line in response]

        return response


    @classmethod
    def run_call(cls, call, readline=False, timeout=None):
        """Invoke a given devserver call using urllib.open or ssh.

        Open the URL with HTTP or SSH-based HTTP, and return the text of the
        response. Exceptions may be raised as for urllib2.urlopen() or
        utils.run().

        @param call: a url string that calls a method to a devserver.
        @param readline: whether read http response line by line.
        @param timeout: The timeout seconds for urlopen call or ssh call.

        @return the results of this call.
        """
        if not ENABLE_SSH_CONNECTION_FOR_DEVSERVER:
            return super(ImageServerBase, cls).run_call(
                    call, readline=readline, timeout=timeout)
        else:
            return cls.run_ssh_call(
                    call, readline=readline, timeout=timeout)


    @classmethod
    def download_file(cls, remote_file, local_file, timeout=None):
        """Download file from devserver.

        The format of remote_file should be:
            http://devserver_ip:8082/static/board/...

        @param remote_file: The URL of the file on devserver that need to be
            downloaded.
        @param local_file: The path of the file saved to local.
        @param timeout: The timeout seconds for this call.
        """
        response = cls.run_call(remote_file, timeout=timeout)
        with open(local_file, 'w') as out_log:
            out_log.write(response)


    def _poll_is_staged(self, **kwargs):
        """Polling devserver.is_staged until all artifacts are staged.

        @param kwargs: keyword arguments to make is_staged devserver call.

        @return: True if all artifacts are staged in devserver.
        """
        call = self.build_call('is_staged', **kwargs)

        def all_staged():
            """Call devserver.is_staged rpc to check if all files are staged.

            @return: True if all artifacts are staged in devserver. False
                     otherwise.
            @rasies DevServerException, the exception is a wrapper of all
                    exceptions that were raised when devserver tried to download
                    the artifacts. devserver raises an HTTPError or a CmdError
                    when an exception was raised in the code. Such exception
                    should be re-raised here to stop the caller from waiting.
                    If the call to devserver failed for connection issue, a
                    URLError exception is raised, and caller should retry the
                    call to avoid such network flakiness.

            """
            try:
                result = self.run_call(call)
                logging.debug('whether artifact is staged: %r', result)
                return result == 'True'
            except urllib2.HTTPError as e:
                error_markup = e.read()
                raise DevServerException(_strip_http_message(error_markup))
            except urllib2.URLError as e:
                # Could be connection issue, retry it.
                # For example: <urlopen error [Errno 111] Connection refused>
                logging.error('URLError happens in is_stage: %r', e)
                return False
            except error.CmdError as e:
                # Retry if SSH failed to connect to the devserver.
                logging.warning('CmdError happens in is_stage: %r, will retry', e)
                return False

        site_utils.poll_for_condition(
                all_staged,
                exception=site_utils.TimeoutError(),
                timeout=DEVSERVER_IS_STAGING_RETRY_MIN * 60,
                sleep_interval=_ARTIFACT_STAGE_POLLING_INTERVAL)

        return True


    def _call_and_wait(self, call_name, error_message,
                       expected_response=SUCCESS, **kwargs):
        """Helper method to make a urlopen call, and wait for artifacts staged.

        @param call_name: name of devserver rpc call.
        @param error_message: Error message to be thrown if response does not
                              match expected_response.
        @param expected_response: Expected response from rpc, default to
                                  |Success|. If it's set to None, do not compare
                                  the actual response. Any response is consider
                                  to be good.
        @param kwargs: keyword arguments to make is_staged devserver call.

        @return: The response from rpc.
        @raise DevServerException upon any return code that's expected_response.

        """
        call = self.build_call(call_name, async=True, **kwargs)
        try:
            response = self.run_call(call)
            logging.debug('response for RPC: %r', response)
            if ERR_MSG_FOR_INVALID_DEVSERVER_RESPONSE in response:
                logging.debug('Proxy error happens in RPC call, '
                              'will retry in 30 seconds')
                time.sleep(30)
                raise DevServerOverloadException()
        except httplib.BadStatusLine as e:
            logging.error(e)
            raise DevServerException('Received Bad Status line, Devserver %s '
                                     'might have gone down while handling '
                                     'the call: %s' % (self.url(), call))

        if expected_response and not response == expected_response:
                raise DevServerException(error_message)

        # `os_type` is needed in build a devserver call, but not needed for
        # wait_for_artifacts_staged, since that method is implemented by
        # each ImageServerBase child class.
        if 'os_type' in kwargs:
            del kwargs['os_type']
        self.wait_for_artifacts_staged(**kwargs)
        return response


    def _stage_artifacts(self, build, artifacts, files, archive_url, **kwargs):
        """Tell the devserver to download and stage |artifacts| from |image|
        specified by kwargs.

        This is the main call point for staging any specific artifacts for a
        given build. To see the list of artifacts one can stage see:

        ~src/platfrom/dev/artifact_info.py.

        This is maintained along with the actual devserver code.

        @param artifacts: A list of artifacts.
        @param files: A list of files to stage.
        @param archive_url: Optional parameter that has the archive_url to stage
                this artifact from. Default is specified in autotest config +
                image.
        @param kwargs: keyword arguments that specify the build information, to
                make stage devserver call.

        @raise DevServerException upon any return code that's not HTTP OK.
        """
        if not archive_url:
            archive_url = _get_storage_server_for_artifacts(artifacts) + build

        artifacts_arg = ','.join(artifacts) if artifacts else ''
        files_arg = ','.join(files) if files else ''
        error_message = ("staging %s for %s failed;"
                         "HTTP OK not accompanied by 'Success'." %
                         ('artifacts=%s files=%s ' % (artifacts_arg, files_arg),
                          build))

        staging_info = ('build=%s, artifacts=%s, files=%s, archive_url=%s' %
                        (build, artifacts, files, archive_url))
        logging.info('Staging artifacts on devserver %s: %s',
                     self.url(), staging_info)
        success = False
        try:
            arguments = {'archive_url': archive_url,
                         'artifacts': artifacts_arg,
                         'files': files_arg}
            if kwargs:
                arguments.update(kwargs)
            # TODO(akeshet): canonicalize artifacts_arg before using it as a
            # metric field (as it stands it is a not-very-well-controlled
            # string).
            f = {'artifacts': artifacts_arg,
                 'dev_server': self.resolved_hostname}
            with metrics.SecondsTimer(
                    'chromeos/autotest/devserver/stage_artifact_duration',
                    fields=f):
                self.call_and_wait(call_name='stage', error_message=error_message,
                                   **arguments)
            logging.info('Finished staging artifacts: %s', staging_info)
            success = True
        except (site_utils.TimeoutError, error.TimeoutException):
            logging.error('stage_artifacts timed out: %s', staging_info)
            raise DevServerException(
                    'stage_artifacts timed out: %s' % staging_info)
        finally:
            f = {'success': success,
                 'artifacts': artifacts_arg,
                 'dev_server': self.resolved_hostname}
            metrics.Counter('chromeos/autotest/devserver/stage_artifact'
                            ).increment(fields=f)


    def call_and_wait(self, *args, **kwargs):
        """Helper method to make a urlopen call, and wait for artifacts staged.

        This method needs to be overridden in the subclass to implement the
        logic to call _call_and_wait.
        """
        raise NotImplementedError


    def _trigger_download(self, build, artifacts, files, synchronous=True,
                          **kwargs_build_info):
        """Tell the devserver to download and stage image specified in
        kwargs_build_info.

        Tells the devserver to fetch |image| from the image storage server
        named by _get_image_storage_server().

        If |synchronous| is True, waits for the entire download to finish
        staging before returning. Otherwise only the artifacts necessary
        to start installing images onto DUT's will be staged before returning.
        A caller can then call finish_download to guarantee the rest of the
        artifacts have finished staging.

        @param synchronous: if True, waits until all components of the image are
               staged before returning.
        @param kwargs_build_info: Dictionary of build information.
                For CrOS, it is None as build is the CrOS image name.
                For Android, it is {'target': target,
                                    'build_id': build_id,
                                    'branch': branch}

        @raise DevServerException upon any return code that's not HTTP OK.

        """
        if kwargs_build_info:
            archive_url = None
        else:
            archive_url = _get_image_storage_server() + build
        error_message = ("trigger_download for %s failed;"
                         "HTTP OK not accompanied by 'Success'." % build)
        kwargs = {'archive_url': archive_url,
                  'artifacts': artifacts,
                  'files': files,
                  'error_message': error_message}
        if kwargs_build_info:
            kwargs.update(kwargs_build_info)

        logging.info('trigger_download starts for %s', build)
        try:
            response = self.call_and_wait(call_name='stage', **kwargs)
            logging.info('trigger_download finishes for %s', build)
        except (site_utils.TimeoutError, error.TimeoutException):
            logging.error('trigger_download timed out for %s.', build)
            raise DevServerException(
                    'trigger_download timed out for %s.' % build)
        was_successful = response == SUCCESS
        if was_successful and synchronous:
            self._finish_download(build, artifacts, files, **kwargs_build_info)


    def _finish_download(self, build, artifacts, files, **kwargs_build_info):
        """Tell the devserver to finish staging image specified in
        kwargs_build_info.

        If trigger_download is called with synchronous=False, it will return
        before all artifacts have been staged. This method contacts the
        devserver and blocks until all staging is completed and should be
        called after a call to trigger_download.

        @param kwargs_build_info: Dictionary of build information.
                For CrOS, it is None as build is the CrOS image name.
                For Android, it is {'target': target,
                                    'build_id': build_id,
                                    'branch': branch}

        @raise DevServerException upon any return code that's not HTTP OK.
        """
        archive_url = _get_image_storage_server() + build
        error_message = ("finish_download for %s failed;"
                         "HTTP OK not accompanied by 'Success'." % build)
        kwargs = {'archive_url': archive_url,
                  'artifacts': artifacts,
                  'files': files,
                  'error_message': error_message}
        if kwargs_build_info:
            kwargs.update(kwargs_build_info)
        try:
            self.call_and_wait(call_name='stage', **kwargs)
        except (site_utils.TimeoutError, error.TimeoutException):
            logging.error('finish_download timed out for %s', build)
            raise DevServerException(
                    'finish_download timed out for %s.' % build)


    @remote_devserver_call()
    def locate_file(self, file_name, artifacts, build, build_info):
        """Locate a file with the given file_name on devserver.

        This method calls devserver RPC `locate_file` to look up a file with
        the given file name inside specified build artifacts.

        @param file_name: Name of the file to look for a file.
        @param artifacts: A list of artifact names to search for the file.
        @param build: Name of the build. For Android, it's None as build_info
                should be used.
        @param build_info: Dictionary of build information.
                For CrOS, it is None as build is the CrOS image name.
                For Android, it is {'target': target,
                                    'build_id': build_id,
                                    'branch': branch}

        @return: A devserver url to the file.
        @raise DevServerException upon any return code that's not HTTP OK.
        """
        if not build and not build_info:
            raise DevServerException('You must specify build information to '
                                     'look for file %s in artifacts %s.' %
                                     (file_name, artifacts))
        kwargs = {'file_name': file_name,
                  'artifacts': artifacts}
        if build_info:
            build_path = '%(branch)s/%(target)s/%(build_id)s' % build_info
            kwargs.update(build_info)
            # Devserver treats Android and Brillo build in the same way as they
            # are both retrieved from Launch Control and have similar build
            # artifacts. Therefore, os_type for devserver calls is `android` for
            # both Android and Brillo builds.
            kwargs['os_type'] = 'android'
        else:
            build_path = build
            kwargs['build'] = build
        call = self.build_call('locate_file', async=False, **kwargs)
        try:
            file_path = self.run_call(call)
            return os.path.join(self.url(), 'static', build_path, file_path)
        except httplib.BadStatusLine as e:
            logging.error(e)
            raise DevServerException('Received Bad Status line, Devserver %s '
                                     'might have gone down while handling '
                                     'the call: %s' % (self.url(), call))


    @remote_devserver_call()
    def list_control_files(self, build, suite_name=''):
        """Ask the devserver to list all control files for |build|.

        @param build: The build (e.g. x86-mario-release/R18-1586.0.0-a1-b1514)
                      whose control files the caller wants listed.
        @param suite_name: The name of the suite for which we require control
                           files.
        @return None on failure, or a list of control file paths
                (e.g. server/site_tests/autoupdate/control)
        @raise DevServerException upon any return code that's not HTTP OK.
        """
        build = self.translate(build)
        call = self.build_call('controlfiles', build=build,
                               suite_name=suite_name)
        return self.run_call(call, readline=True)


    @remote_devserver_call()
    def get_control_file(self, build, control_path):
        """Ask the devserver for the contents of a control file.

        @param build: The build (e.g. x86-mario-release/R18-1586.0.0-a1-b1514)
                      whose control file the caller wants to fetch.
        @param control_path: The file to fetch
                             (e.g. server/site_tests/autoupdate/control)
        @return The contents of the desired file.
        @raise DevServerException upon any return code that's not HTTP OK.
        """
        build = self.translate(build)
        call = self.build_call('controlfiles', build=build,
                               control_path=control_path)
        return self.run_call(call)


    @remote_devserver_call()
    def list_suite_controls(self, build, suite_name=''):
        """Ask the devserver to list contents of all control files for |build|.

        @param build: The build (e.g. x86-mario-release/R18-1586.0.0-a1-b1514)
                      whose control files' contents the caller wants returned.
        @param suite_name: The name of the suite for which we require control
                           files.
        @return None on failure, or a dict of contents of all control files
            (e.g. {'path1': "#Copyright controls ***", ...,
                pathX': "#Copyright controls ***"}
        @raise DevServerException upon any return code that's not HTTP OK.
        """
        build = self.translate(build)
        call = self.build_call('list_suite_controls', build=build,
                               suite_name=suite_name)
        return json.load(cStringIO.StringIO(self.run_call(call)))


class ImageServer(ImageServerBase):
    """Class for DevServer that handles RPCs related to CrOS images.

    The calls to devserver to stage artifacts, including stage and download, are
    made in async mode. That is, when caller makes an RPC |stage| to request
    devserver to stage certain artifacts, devserver handles the call and starts
    staging artifacts in a new thread, and return |Success| without waiting for
    staging being completed. When caller receives message |Success|, it polls
    devserver's is_staged call until all artifacts are staged.
    Such mechanism is designed to prevent cherrypy threads in devserver being
    running out, as staging artifacts might take long time, and cherrypy starts
    with a fixed number of threads that handle devserver rpc.
    """

    class ArtifactUrls(object):
        """A container for URLs of staged artifacts.

        Attributes:
            full_payload: URL for downloading a staged full release update
            mton_payload: URL for downloading a staged M-to-N release update
            nton_payload: URL for downloading a staged N-to-N release update

        """
        def __init__(self, full_payload=None, mton_payload=None,
                     nton_payload=None):
            self.full_payload = full_payload
            self.mton_payload = mton_payload
            self.nton_payload = nton_payload


    def wait_for_artifacts_staged(self, archive_url, artifacts='', files=''):
        """Polling devserver.is_staged until all artifacts are staged.

        @param archive_url: Google Storage URL for the build.
        @param artifacts: Comma separated list of artifacts to download.
        @param files: Comma separated list of files to download.
        @return: True if all artifacts are staged in devserver.
        """
        kwargs = {'archive_url': archive_url,
                  'artifacts': artifacts,
                  'files': files}
        return self._poll_is_staged(**kwargs)


    @remote_devserver_call()
    def call_and_wait(self, call_name, archive_url, artifacts, files,
                      error_message, expected_response=SUCCESS):
        """Helper method to make a urlopen call, and wait for artifacts staged.

        @param call_name: name of devserver rpc call.
        @param archive_url: Google Storage URL for the build..
        @param artifacts: Comma separated list of artifacts to download.
        @param files: Comma separated list of files to download.
        @param expected_response: Expected response from rpc, default to
                                  |Success|. If it's set to None, do not compare
                                  the actual response. Any response is consider
                                  to be good.
        @param error_message: Error message to be thrown if response does not
                              match expected_response.

        @return: The response from rpc.
        @raise DevServerException upon any return code that's expected_response.

        """
        kwargs = {'archive_url': archive_url,
                  'artifacts': artifacts,
                  'files': files}
        return self._call_and_wait(call_name, error_message,
                                   expected_response, **kwargs)


    @remote_devserver_call()
    def stage_artifacts(self, image=None, artifacts=None, files='',
                        archive_url=None):
        """Tell the devserver to download and stage |artifacts| from |image|.

         This is the main call point for staging any specific artifacts for a
        given build. To see the list of artifacts one can stage see:

        ~src/platfrom/dev/artifact_info.py.

        This is maintained along with the actual devserver code.

        @param image: the image to fetch and stage.
        @param artifacts: A list of artifacts.
        @param files: A list of files to stage.
        @param archive_url: Optional parameter that has the archive_url to stage
                this artifact from. Default is specified in autotest config +
                image.

        @raise DevServerException upon any return code that's not HTTP OK.
        """
        if not artifacts and not files:
            raise DevServerException('Must specify something to stage.')
        image = self.translate(image)
        self._stage_artifacts(image, artifacts, files, archive_url)


    @remote_devserver_call(timeout_min=DEVSERVER_SSH_TIMEOUT_MINS)
    def list_image_dir(self, image):
        """List the contents of the image stage directory, on the devserver.

        @param image: The image name, eg: <board>-<branch>/<Milestone>-<build>.

        @raise DevServerException upon any return code that's not HTTP OK.
        """
        image = self.translate(image)
        logging.info('Requesting contents from devserver %s for image %s',
                     self.url(), image)
        archive_url = _get_storage_server_for_artifacts() + image
        call = self.build_call('list_image_dir', archive_url=archive_url)
        response = self.run_call(call, readline=True)
        for line in response:
            logging.info(line)


    def trigger_download(self, image, synchronous=True):
        """Tell the devserver to download and stage |image|.

        Tells the devserver to fetch |image| from the image storage server
        named by _get_image_storage_server().

        If |synchronous| is True, waits for the entire download to finish
        staging before returning. Otherwise only the artifacts necessary
        to start installing images onto DUT's will be staged before returning.
        A caller can then call finish_download to guarantee the rest of the
        artifacts have finished staging.

        @param image: the image to fetch and stage.
        @param synchronous: if True, waits until all components of the image are
               staged before returning.

        @raise DevServerException upon any return code that's not HTTP OK.

        """
        image = self.translate(image)
        artifacts = _ARTIFACTS_TO_BE_STAGED_FOR_IMAGE
        self._trigger_download(image, artifacts, files='',
                               synchronous=synchronous)


    @remote_devserver_call()
    def setup_telemetry(self, build):
        """Tell the devserver to setup telemetry for this build.

        The devserver will stage autotest and then extract the required files
        for telemetry.

        @param build: the build to setup telemetry for.

        @returns path on the devserver that telemetry is installed to.
        """
        build = self.translate(build)
        archive_url = _get_image_storage_server() + build
        call = self.build_call('setup_telemetry', archive_url=archive_url)
        try:
            response = self.run_call(call)
        except httplib.BadStatusLine as e:
            logging.error(e)
            raise DevServerException('Received Bad Status line, Devserver %s '
                                     'might have gone down while handling '
                                     'the call: %s' % (self.url(), call))
        return response


    def finish_download(self, image):
        """Tell the devserver to finish staging |image|.

        If trigger_download is called with synchronous=False, it will return
        before all artifacts have been staged. This method contacts the
        devserver and blocks until all staging is completed and should be
        called after a call to trigger_download.

        @param image: the image to fetch and stage.
        @raise DevServerException upon any return code that's not HTTP OK.
        """
        image = self.translate(image)
        artifacts = _ARTIFACTS_TO_BE_STAGED_FOR_IMAGE_WITH_AUTOTEST
        self._finish_download(image, artifacts, files='')


    def get_update_url(self, image):
        """Returns the url that should be passed to the updater.

        @param image: the image that was fetched.
        """
        image = self.translate(image)
        url_pattern = CONFIG.get_config_value('CROS', 'image_url_pattern',
                                              type=str)
        return (url_pattern % (self.url(), image))


    def get_staged_file_url(self, filename, image):
        """Returns the url of a staged file for this image on the devserver."""
        return '/'.join([self._get_image_url(image), filename])


    def get_full_payload_url(self, image):
        """Returns a URL to a staged full payload.

        @param image: the image that was fetched.

        @return A fully qualified URL that can be used for downloading the
                payload.

        """
        return self._get_image_url(image) + '/update.gz'


    def get_test_image_url(self, image):
        """Returns a URL to a staged test image.

        @param image: the image that was fetched.

        @return A fully qualified URL that can be used for downloading the
                image.

        """
        return self._get_image_url(image) + '/chromiumos_test_image.bin'


    @remote_devserver_call()
    def get_dependencies_file(self, build):
        """Ask the dev server for the contents of the suite dependencies file.

        Ask the dev server at |self._dev_server| for the contents of the
        pre-processed suite dependencies file (at DEPENDENCIES_FILE)
        for |build|.

        @param build: The build (e.g. x86-mario-release/R21-2333.0.0)
                      whose dependencies the caller is interested in.
        @return The contents of the dependencies file, which should eval to
                a dict of dicts, as per site_utils/suite_preprocessor.py.
        @raise DevServerException upon any return code that's not HTTP OK.
        """
        build = self.translate(build)
        call = self.build_call('controlfiles',
                               build=build, control_path=DEPENDENCIES_FILE)
        return self.run_call(call)


    @remote_devserver_call()
    def get_latest_build_in_gs(self, board):
        """Ask the devservers for the latest offical build in Google Storage.

        @param board: The board for who we want the latest official build.
        @return A string of the returned build rambi-release/R37-5868.0.0
        @raise DevServerException upon any return code that's not HTTP OK.
        """
        call = self.build_call(
                'xbuddy_translate/remote/%s/latest-official' % board,
                image_dir=_get_image_storage_server())
        image_name = self.run_call(call)
        return os.path.dirname(image_name)


    def translate(self, build_name):
        """Translate the build name if it's in LATEST format.

        If the build name is in the format [builder]/LATEST, return the latest
        build in Google Storage otherwise return the build name as is.

        @param build_name: build_name to check.

        @return The actual build name to use.
        """
        match = re.match(r'([\w-]+)-(\w+)/LATEST', build_name, re.I)
        if not match:
            return build_name
        translated_build = self.get_latest_build_in_gs(match.groups()[0])
        logging.debug('Translated relative build %s to %s', build_name,
                      translated_build)
        return translated_build


    @classmethod
    @remote_devserver_call()
    def get_latest_build(cls, target, milestone=''):
        """Ask all the devservers for the latest build for a given target.

        @param target: The build target, typically a combination of the board
                       and the type of build e.g. x86-mario-release.
        @param milestone:  For latest build set to '', for builds only in a
                           specific milestone set to a str of format Rxx
                           (e.g. R16). Default: ''. Since we are dealing with a
                           webserver sending an empty string, '', ensures that
                           the variable in the URL is ignored as if it was set
                           to None.
        @return A string of the returned build e.g. R20-2226.0.0.
        @raise DevServerException upon any return code that's not HTTP OK.
        """
        calls = cls.build_all_calls('latestbuild', target=target,
                                    milestone=milestone)
        latest_builds = []
        for call in calls:
            latest_builds.append(cls.run_call(call))

        return max(latest_builds, key=version.LooseVersion)


    @remote_devserver_call()
    def _kill_au_process_for_host(self, **kwargs):
        """Kill the triggerred auto_update process if error happens in cros_au.

        @param kwargs: Arguments to make kill_au_proc devserver call.
        """
        call = self.build_call('kill_au_proc', **kwargs)
        response = self.run_call(call)
        if not response == 'True':
            raise DevServerException(
                    'Failed to kill the triggerred CrOS auto_update process'
                    'on devserver %s, the response is %s' % (
                            self.url(), response))


    def kill_au_process_for_host(self, host_name, pid):
        """Kill the triggerred auto_update process if error happens.

        Usually this function is used to clear all potential left au processes
        of the given host name.

        If pid is specified, the devserver will further check the given pid to
        make sure the process is killed. This is used for the case that the au
        process has started in background, but then provision fails due to
        some unknown issues very fast. In this case, when 'kill_au_proc' is
        called, there's no corresponding background track log created for this
        ongoing au process, which prevents this RPC call from killing this au
        process.

        @param host_name: The DUT's hostname.
        @param pid: The ongoing au process's pid.

        @return: True if successfully kill the auto-update process for host.
        """
        kwargs = {'host_name': host_name, 'pid': pid}
        try:
            self._kill_au_process_for_host(**kwargs)
        except DevServerException:
            return False

        return True


    @remote_devserver_call()
    def _clean_track_log(self, **kwargs):
        """Clean track log for the current auto-update process."""
        call = self.build_call('handler_cleanup', **kwargs)
        self.run_call(call)


    def clean_track_log(self, host_name, pid):
        """Clean track log for the current auto-update process.

        @param host_name: The host name to be updated.
        @param pid: The auto-update process id.

        @return: True if track log is successfully cleaned, False otherwise.
        """
        if not pid:
            return False

        kwargs = {'host_name': host_name, 'pid': pid}
        try:
            self._clean_track_log(**kwargs)
        except DevServerException as e:
            logging.debug('Failed to clean track_status_file on '
                          'devserver for host %s and process id %s: %s',
                          host_name, pid, str(e))
            return False

        return True


    def _get_au_log_filename(self, log_dir, host_name, pid):
        """Return the auto-update log's filename."""
        return os.path.join(log_dir, CROS_AU_LOG_FILENAME % (
                    host_name, pid))

    @remote_devserver_call()
    def _collect_au_log(self, log_dir, **kwargs):
        """Collect logs from devserver after cros-update process is finished.

        Collect the logs that recording the whole cros-update process, and
        write it to sysinfo path of a job.

        The example log file name that is stored is like:
            '1220-repair/sysinfo/CrOS_update_host_name_pid.log'

        @param host_name: the DUT's hostname.
        @param pid: the auto-update process id on devserver.
        @param log_dir: The directory to save the cros-update process log
                        retrieved from devserver.
        """
        call = self.build_call('collect_cros_au_log', **kwargs)
        response = self.run_call(call)
        if not os.path.exists(log_dir):
            os.mkdir(log_dir)
        write_file = self._get_au_log_filename(
                log_dir, kwargs['host_name'], kwargs['pid'])
        logging.debug('Saving auto-update logs into %s', write_file)
        try:
            with open(write_file, 'w') as out_log:
                out_log.write(response)
        except:
            raise DevServerException('Failed to write auto-update logs into '
                                     '%s' % write_file)


    def collect_au_log(self, host_name, pid, log_dir):
        """Collect logs from devserver after cros-update process is finished.

        @param host_name: the DUT's hostname.
        @param pid: the auto-update process id on devserver.
        @param log_dir: The directory to save the cros-update process log
                        retrieved from devserver.

        @return: True if auto-update log is successfully collected, False
          otherwise.
        """
        if not pid:
            return False

        kwargs = {'host_name': host_name, 'pid': pid}
        try:
            self._collect_au_log(log_dir, **kwargs)
        except DevServerException as e:
            logging.debug('Failed to collect auto-update log on '
                          'devserver for host %s and process id %s: %s',
                          host_name, pid, str(e))
            return False

        return True


    @remote_devserver_call()
    def _trigger_auto_update(self, **kwargs):
        """Trigger auto-update by calling devserver.cros_au.

        @param kwargs:  Arguments to make cros_au devserver call.

        @return: a tuple indicates whether the RPC call cros_au succeeds and
          the auto-update process id running on devserver.
        """
        host_name = kwargs['host_name']
        call = self.build_call('cros_au', async=True, **kwargs)
        try:
            response = self.run_call(call)
            logging.info(
                'Received response from devserver for cros_au call: %r',
                response)
        except httplib.BadStatusLine as e:
            logging.error(e)
            raise DevServerException('Received Bad Status line, Devserver %s '
                                     'might have gone down while handling '
                                     'the call: %s' % (self.url(), call))

        return response


    def _wait_for_auto_update_finished(self, pid, **kwargs):
        """Polling devserver.get_au_status to get current auto-update status.

        The current auto-update status is used to identify whether the update
        process is finished.

        @param pid:    The background process id for auto-update in devserver.
        @param kwargs: keyword arguments to make get_au_status devserver call.

        @return: True if auto-update is finished for a given dut.
        """
        logging.debug('Check the progress for auto-update process %r', pid)
        kwargs['pid'] = pid
        call = self.build_call('get_au_status', **kwargs)

        def all_finished():
            """Call devserver.get_au_status rpc to check if auto-update
               is finished.

            @return: True if auto-update is finished for a given dut. False
                     otherwise.
            @rasies  DevServerException, the exception is a wrapper of all
                     exceptions that were raised when devserver tried to
                     download the artifacts. devserver raises an HTTPError or
                     a CmdError when an exception was raised in the code. Such
                     exception should be re-raised here to stop the caller from
                     waiting. If the call to devserver failed for connection
                     issue, a URLError exception is raised, and caller should
                     retry the call to avoid such network flakiness.

            """
            try:
                au_status = self.run_call(call)
                response = json.loads(au_status)
                # This is a temp fix to fit both dict and tuple returning
                # values. The dict check will be removed after a corresponding
                # devserver CL is deployed.
                if isinstance(response, dict):
                    if response.get('detailed_error_msg'):
                        raise DevServerException(
                                response.get('detailed_error_msg'))

                    if response.get('finished'):
                        logging.debug('CrOS auto-update is finished')
                        return True
                    else:
                        logging.debug('Current CrOS auto-update status: %s',
                                      response.get('status'))
                        return False

                if not response[0]:
                    logging.debug('Current CrOS auto-update status: %s',
                                  response[1])
                    return False
                else:
                    logging.debug('CrOS auto-update is finished')
                    return True
            except urllib2.HTTPError as e:
                error_markup = e.read()
                raise DevServerException(_strip_http_message(error_markup))
            except urllib2.URLError as e:
                # Could be connection issue, retry it.
                # For example: <urlopen error [Errno 111] Connection refused>
                logging.warning('URLError (%r): Retrying connection to '
                                'devserver to check auto-update status.', e)
                return False
            except error.CmdError:
                # Retry if SSH failed to connect to the devserver.
                logging.warning('CmdError: Retrying SSH connection to check '
                                'auto-update status.')
                return False
            except socket.error as e:
                # Could be some temporary devserver connection issues.
                logging.warning('Socket Error (%r): Retrying connection to '
                                'devserver to check auto-update status.', e)
                return False
            except ValueError as e:
                raise DevServerException(
                        '%s (Got AU status: %r)' % (str(e), au_status))

        site_utils.poll_for_condition(
                all_finished,
                exception=site_utils.TimeoutError(),
                timeout=DEVSERVER_IS_CROS_AU_FINISHED_TIMEOUT_MIN * 60,
                sleep_interval=CROS_AU_POLLING_INTERVAL)

        return True


    def wait_for_auto_update_finished(self, response, **kwargs):
        """Processing response of 'cros_au' and polling for auto-update status.

        Will wait for the whole auto-update process is finished.

        @param response: The response from RPC 'cros_au'
        @param kwargs: keyword arguments to make get_au_status devserver call.

        @return: a tuple includes two elements.
          raised_error: None if everything works well or the raised error.
          pid: the auto-update process id on devserver.
        """

        pid = 0
        raised_error = None
        try:
            response = json.loads(response)
            if response[0]:
                pid = response[1]
                logging.debug('start process %r for auto_update in devserver',
                              pid)
                self._wait_for_auto_update_finished(pid, **kwargs)
        except Exception as e:
            logging.debug('Failed to trigger auto-update process on devserver')
            raised_error = e
        finally:
            return raised_error, pid


    def _parse_AU_error(self, response):
        """Parse auto_update error returned from devserver."""
        return re.split('\n', response)[-1]


    def _classify_exceptions(self, error_list):
        """Parse the error that was raised from auto_update.

        @param error_list: The list of errors (string) happened in auto-update

        @return: A classified exception type (string) from _EXCEPTION_PATTERNS
          or 'Unknown exception'. Current patterns in _EXCEPTION_PATTERNS are
          very specific so that errors cannot match more than one pattern.
        """
        raised_error = ''
        if not error_list:
            return raised_error
        else:
            target_error = error_list[0]

        for err_pattern, classification in _EXCEPTION_PATTERNS:
            match = re.match(err_pattern, target_error)
            if match:
                return classification

        return '(0) Unknown exception'

    def _is_retryable(self, error_msg):
        """Detect whether we will retry auto-update based on error_msg.

        @param error_msg: The given error message.

        @return A boolean variable which indicates whether we will retry
            auto_update with another devserver based on the given error_msg.
        """
        # For now we just hard-code the error message we think it's suspicious.
        # When we get more date about what's the json response when devserver
        # is overloaded, we can update this part.
        retryable_errors = ['No JSON object could be decoded',
                            'is not pingable']
        for err in retryable_errors:
            if err in error_msg:
                return True

        return False


    def auto_update(self, host_name, build_name, log_dir=None,
                    force_update=False, full_update=False):
        """Auto-update a CrOS host.

        @param host_name:    The hostname of the DUT to auto-update.
        @param build_name:   The build name to be auto-updated on the DUT.
        @param log_dir:      The log directory to store auto-update logs from
                             devserver.
        @param force_update: Force an update even if the version installed
                             is the same. Default: False.
        @param full_update:  If True, do not run stateful update, directly
                             force a full reimage. If False, try stateful
                             update first if the dut is already installed
                             with the same version.

        @return A set (is_success, is_retryable) in which:
            1. is_success indicates whether this auto_update succeeds.
            2. is_retryable indicates whether we should retry auto_update if
               if it fails.

        @raise DevServerException if auto_update fails and is not retryable.
        """
        kwargs = {'host_name': host_name,
                  'build_name': build_name,
                  'force_update': force_update,
                  'full_update': full_update}

        error_msg = 'CrOS auto-update failed for host %s: %s'
        error_msg_attempt = 'Exception raised on auto_update attempt #%s:\n%s'
        is_au_success = False
        au_log_dir = os.path.join(log_dir,
                                  AUTO_UPDATE_LOG_DIR) if log_dir else None
        error_list = []
        retry_with_another_devserver = False

        for au_attempt in range(AU_RETRY_LIMIT):
            logging.debug('Start CrOS auto-update for host %s at %d time(s).',
                          host_name, au_attempt + 1)
            # No matter _start_auto_update succeeds or fails, the auto-update
            # track_status_file should be cleaned, and the auto-update execute
            # log should be collected to directory sysinfo. Also, the error
            # raised by _start_auto_update should be displayed.
            try:
                response = self._trigger_auto_update(**kwargs)
            except DevServerException as e:
                logging.debug(error_msg_attempt, au_attempt+1, str(e))
                error_list.append(str(e))
            else:
                raised_error, pid = self.wait_for_auto_update_finished(response,
                                                                       **kwargs)
                # Error happens in _clean_track_log won't be raised. Auto-update
                # process will be retried.
                # TODO(xixuan): Change kwargs['host_name'] back to host_name
                # if crbug.com/651974 is fixed: host_name represents the host
                # name of the host, and kwargs['host_name'] could be host_name
                # or the IP of this host.
                is_clean_success = self.clean_track_log(kwargs['host_name'], pid)
                # Error happens in _collect_au_log won't be raised. Auto-update
                # process will be retried.
                if au_log_dir:
                    is_collect_success = self.collect_au_log(
                            kwargs['host_name'], pid, au_log_dir)
                else:
                    is_collect_success = True
                # If any error is raised previously, log it and retry
                # auto-update. Otherwise, claim a success CrOS auto-update.
                if not raised_error and is_clean_success and is_collect_success:
                    logging.debug('CrOS auto-update succeed for host %s',
                                  host_name)
                    is_au_success = True
                    break
                else:
                    if not self.kill_au_process_for_host(kwargs['host_name'],
                                                         pid):
                        logging.debug('Failed to kill auto_update process %d',
                                      pid)
                    if raised_error:
                        logging.debug(error_msg_attempt, au_attempt+1,
                                      str(raised_error))
                        if au_log_dir:
                            logging.debug('Please see error details in log %s',
                                          self._get_au_log_filename(
                                                  au_log_dir,
                                                  kwargs['host_name'],
                                                  pid))
                        error_list.append(self._parse_AU_error(str(raised_error)))
                        if self._is_retryable(str(raised_error)):
                            retry_with_another_devserver = True

            finally:
                if retry_with_another_devserver:
                    break

                if not is_au_success and au_attempt < AU_RETRY_LIMIT - 1:
                    time.sleep(CROS_AU_RETRY_INTERVAL)
                    # TODO(kevcheng): Remove this once crbug.com/651974 is
                    # fixed.
                    # DNS is broken in the cassandra lab, so use the IP of the
                    # hostname instead if it fails. Not rename host_name here
                    # for error msg reporting.
                    host_name_ip = socket.gethostbyname(host_name)
                    kwargs['host_name'] = host_name_ip
                    logging.debug(
                            'AU failed, trying IP instead of hostname: %s',
                            host_name_ip)

        # Upload data to metrics
        try:
            board, build_type, milestone, _ = server_utils.ParseBuildName(
                build_name)
        except server_utils.ParseBuildNameException:
            logging.warning('Unable to parse build name %s for metrics. '
                            'Continuing anyway.', build_name)
            board, build_type, milestone = ('', '', '')

        # Note: To avoid reaching or exceeding the monarch field cardinality
        # limit, we avoid a metric that includes both dut hostname and other
        # high cardinality fields.
        # Per-devserver cros_update metric.
        c = metrics.Counter(
                'chromeos/autotest/provision/cros_update_by_devserver')
        # Add a field |error| here. Current error's pattern is manually
        # specified in _EXCEPTION_PATTERNS.
        raised_error = self._classify_exceptions(error_list)
        f = {'dev_server': self.resolved_hostname,
             'success': is_au_success,
             'board': board,
             'build_type': build_type,
             'milestone': milestone,
             'error': raised_error}
        c.increment(fields=f)

        # Per-DUT cros_update metric.
        c = metrics.Counter('chromeos/autotest/provision/cros_update_per_dut')
        f = {'success': is_au_success,
             'board': board,
             'error': raised_error,
             'dut_host_name': host_name}
        c.increment(fields=f)

        if is_au_success or retry_with_another_devserver:
            return (is_au_success, retry_with_another_devserver)

        # If errors happen in the CrOS AU process, report the first error
        # since the following errors might be caused by the first error.
        # If error happens in RPCs of cleaning track log, collecting
        # auto-update logs, or killing auto-update processes, just report
        # them together.
        if error_list:
            raise DevServerException(error_msg % (host_name, error_list[0]))
        else:
            raise DevServerException(error_msg % (
                        host_name, ('RPC calls after the whole auto-update '
                                    'process failed.')))


class AndroidBuildServer(ImageServerBase):
    """Class for DevServer that handles RPCs related to Android builds.

    The calls to devserver to stage artifacts, including stage and download, are
    made in async mode. That is, when caller makes an RPC |stage| to request
    devserver to stage certain artifacts, devserver handles the call and starts
    staging artifacts in a new thread, and return |Success| without waiting for
    staging being completed. When caller receives message |Success|, it polls
    devserver's is_staged call until all artifacts are staged.
    Such mechanism is designed to prevent cherrypy threads in devserver being
    running out, as staging artifacts might take long time, and cherrypy starts
    with a fixed number of threads that handle devserver rpc.
    """

    def wait_for_artifacts_staged(self, target, build_id, branch,
                                  archive_url=None, artifacts='', files=''):
        """Polling devserver.is_staged until all artifacts are staged.

        @param target: Target of the android build to stage, e.g.,
                       shamu-userdebug.
        @param build_id: Build id of the android build to stage.
        @param branch: Branch of the android build to stage.
        @param archive_url: Google Storage URL for the build.
        @param artifacts: Comma separated list of artifacts to download.
        @param files: Comma separated list of files to download.

        @return: True if all artifacts are staged in devserver.
        """
        kwargs = {'target': target,
                  'build_id': build_id,
                  'branch': branch,
                  'artifacts': artifacts,
                  'files': files,
                  'os_type': 'android'}
        if archive_url:
            kwargs['archive_url'] = archive_url
        return self._poll_is_staged(**kwargs)


    @remote_devserver_call()
    def call_and_wait(self, call_name, target, build_id, branch, archive_url,
                      artifacts, files, error_message,
                      expected_response=SUCCESS):
        """Helper method to make a urlopen call, and wait for artifacts staged.

        @param call_name: name of devserver rpc call.
        @param target: Target of the android build to stage, e.g.,
                       shamu-userdebug.
        @param build_id: Build id of the android build to stage.
        @param branch: Branch of the android build to stage.
        @param archive_url: Google Storage URL for the CrOS build.
        @param artifacts: Comma separated list of artifacts to download.
        @param files: Comma separated list of files to download.
        @param expected_response: Expected response from rpc, default to
                                  |Success|. If it's set to None, do not compare
                                  the actual response. Any response is consider
                                  to be good.
        @param error_message: Error message to be thrown if response does not
                              match expected_response.

        @return: The response from rpc.
        @raise DevServerException upon any return code that's expected_response.

        """
        kwargs = {'target': target,
                  'build_id': build_id,
                  'branch': branch,
                  'artifacts': artifacts,
                  'files': files,
                  'os_type': 'android'}
        if archive_url:
            kwargs['archive_url'] = archive_url
        return self._call_and_wait(call_name, error_message, expected_response,
                                   **kwargs)


    @remote_devserver_call()
    def stage_artifacts(self, target=None, build_id=None, branch=None,
                        image=None, artifacts=None, files='', archive_url=None):
        """Tell the devserver to download and stage |artifacts| from |image|.

         This is the main call point for staging any specific artifacts for a
        given build. To see the list of artifacts one can stage see:

        ~src/platfrom/dev/artifact_info.py.

        This is maintained along with the actual devserver code.

        @param target: Target of the android build to stage, e.g.,
                               shamu-userdebug.
        @param build_id: Build id of the android build to stage.
        @param branch: Branch of the android build to stage.
        @param image: Name of a build to test, in the format of
                      branch/target/build_id
        @param artifacts: A list of artifacts.
        @param files: A list of files to stage.
        @param archive_url: Optional parameter that has the archive_url to stage
                this artifact from. Default is specified in autotest config +
                image.

        @raise DevServerException upon any return code that's not HTTP OK.
        """
        if image and not target and not build_id and not branch:
            branch, target, build_id = utils.parse_launch_control_build(image)
        if not target or not build_id or not branch:
            raise DevServerException('Must specify all build info (target, '
                                     'build_id and branch) to stage.')

        android_build_info = {'target': target,
                              'build_id': build_id,
                              'branch': branch}
        if not artifacts and not files:
            raise DevServerException('Must specify something to stage.')
        if not all(android_build_info.values()):
            raise DevServerException(
                    'To stage an Android build, must specify target, build id '
                    'and branch.')
        build = ANDROID_BUILD_NAME_PATTERN % android_build_info
        self._stage_artifacts(build, artifacts, files, archive_url,
                              **android_build_info)

    def get_pull_url(self, target, build_id, branch):
        """Get the url to pull files from the devserver.

        @param target: Target of the android build, e.g., shamu_userdebug
        @param build_id: Build id of the android build.
        @param branch: Branch of the android build.

        @return A url to pull files from the dev server given a specific
                android build.
        """
        return os.path.join(self.url(), 'static', branch, target, build_id)


    def trigger_download(self, target, build_id, branch, artifacts=None,
                         files='', os='android', synchronous=True):
        """Tell the devserver to download and stage an Android build.

        Tells the devserver to fetch an Android build from the image storage
        server named by _get_image_storage_server().

        If |synchronous| is True, waits for the entire download to finish
        staging before returning. Otherwise only the artifacts necessary
        to start installing images onto DUT's will be staged before returning.
        A caller can then call finish_download to guarantee the rest of the
        artifacts have finished staging.

        @param target: Target of the android build to stage, e.g.,
                       shamu-userdebug.
        @param build_id: Build id of the android build to stage.
        @param branch: Branch of the android build to stage.
        @param artifacts: A string of artifacts separated by comma. If None,
               use the default artifacts for Android or Brillo build.
        @param files: String of file seperated by commas.
        @param os: OS artifacts to download (android/brillo).
        @param synchronous: if True, waits until all components of the image are
               staged before returning.

        @raise DevServerException upon any return code that's not HTTP OK.

        """
        android_build_info = {'target': target,
                              'build_id': build_id,
                              'branch': branch}
        build = ANDROID_BUILD_NAME_PATTERN % android_build_info
        if not artifacts:
            board = target.split('-')[0]
            artifacts = (
                android_utils.AndroidArtifacts.get_artifacts_for_reimage(
                        board, os))
        self._trigger_download(build, artifacts, files=files,
                               synchronous=synchronous, **android_build_info)


    def finish_download(self, target, build_id, branch, os='android'):
        """Tell the devserver to finish staging an Android build.

        If trigger_download is called with synchronous=False, it will return
        before all artifacts have been staged. This method contacts the
        devserver and blocks until all staging is completed and should be
        called after a call to trigger_download.

        @param target: Target of the android build to stage, e.g.,
                       shamu-userdebug.
        @param build_id: Build id of the android build to stage.
        @param branch: Branch of the android build to stage.
        @param os: OS artifacts to download (android/brillo).

        @raise DevServerException upon any return code that's not HTTP OK.
        """
        android_build_info = {'target': target,
                              'build_id': build_id,
                              'branch': branch}
        build = ANDROID_BUILD_NAME_PATTERN % android_build_info
        board = target.split('-')[0]
        artifacts = (
                android_utils.AndroidArtifacts.get_artifacts_for_reimage(
                        board))
        self._finish_download(build, artifacts, files='', **android_build_info)


    def get_staged_file_url(self, filename, target, build_id, branch):
        """Returns the url of a staged file for this image on the devserver.

        @param filename: Name of the file.
        @param target: Target of the android build to stage, e.g.,
                       shamu-userdebug.
        @param build_id: Build id of the android build to stage.
        @param branch: Branch of the android build to stage.

        @return: The url of a staged file for this image on the devserver.
        """
        android_build_info = {'target': target,
                              'build_id': build_id,
                              'branch': branch,
                              'os_type': 'android'}
        build = ANDROID_BUILD_NAME_PATTERN % android_build_info
        return '/'.join([self._get_image_url(build), filename])


    @remote_devserver_call()
    def translate(self, build_name):
        """Translate the build name if it's in LATEST format.

        If the build name is in the format [branch]/[target]/LATEST, return the
        latest build in Launch Control otherwise return the build name as is.

        @param build_name: build_name to check.

        @return The actual build name to use.
        """
        branch, target, build_id = utils.parse_launch_control_build(build_name)
        if build_id.upper() != 'LATEST':
            return build_name
        call = self.build_call('latestbuild', branch=branch, target=target,
                               os_type='android')
        translated_build_id = self.run_call(call)
        translated_build = (ANDROID_BUILD_NAME_PATTERN %
                            {'branch': branch,
                             'target': target,
                             'build_id': translated_build_id})
        logging.debug('Translated relative build %s to %s', build_name,
                      translated_build)
        return translated_build


def _is_load_healthy(load):
    """Check if devserver's load meets the minimum threshold.

    @param load: The devserver's load stats to check.

    @return: True if the load meets the minimum threshold. Return False
             otherwise.

    """
    # Threshold checks, including CPU load.
    if load[DevServer.CPU_LOAD] > DevServer.MAX_CPU_LOAD:
        logging.debug('CPU load of devserver %s is at %s%%, which is higher '
                      'than the threshold of %s%%', load['devserver'],
                      load[DevServer.CPU_LOAD], DevServer.MAX_CPU_LOAD)
        return False
    if load[DevServer.NETWORK_IO] > DevServer.MAX_NETWORK_IO:
        logging.debug('Network IO of devserver %s is at %i Bps, which is '
                      'higher than the threshold of %i bytes per second.',
                      load['devserver'], load[DevServer.NETWORK_IO],
                      DevServer.MAX_NETWORK_IO)
        return False
    return True


def _compare_load(devserver1, devserver2):
    """Comparator function to compare load between two devservers.

    @param devserver1: A dictionary of devserver load stats to be compared.
    @param devserver2: A dictionary of devserver load stats to be compared.

    @return: Negative value if the load of `devserver1` is less than the load
             of `devserver2`. Return positive value otherwise.

    """
    return int(devserver1[DevServer.DISK_IO] - devserver2[DevServer.DISK_IO])


def get_least_loaded_devserver(devserver_type=ImageServer, hostname=None):
    """Get the devserver with the least load.

    Iterate through all devservers and get the one with least load.

    TODO(crbug.com/486278): Devserver with required build already staged should
    take higher priority. This will need check_health call to be able to verify
    existence of a given build/artifact. Also, in case all devservers are
    overloaded, the logic here should fall back to the old behavior that randomly
    selects a devserver based on the hash of the image name/url.

    @param devserver_type: Type of devserver to select from. Default is set to
                           ImageServer.
    @param hostname: Hostname of the dut that the devserver is used for. The
            picked devserver needs to respect the location of the host if
            `prefer_local_devserver` is set to True or `restricted_subnets` is
            set.

    @return: Name of the devserver with the least load.

    """
    devservers, can_retry = devserver_type.get_available_devservers(
            hostname)
    # If no healthy devservers available and can_retry is False, return None.
    # Otherwise, relax the constrain on hostname, allow all devservers to be
    # available.
    if not devserver_type.get_healthy_devserver('', devservers):
        if not can_retry:
            return None
        else:
            devservers, _ = devserver_type.get_available_devservers()

    # get_devserver_load call needs to be made in a new process to allow force
    # timeout using signal.
    output = multiprocessing.Queue()
    processes = []
    for devserver in devservers:
        processes.append(multiprocessing.Process(
                target=devserver_type.get_devserver_load_wrapper,
                args=(devserver, TIMEOUT_GET_DEVSERVER_LOAD, output)))

    for p in processes:
        p.start()
    for p in processes:
        p.join()
    loads = [output.get() for p in processes]
    # Filter out any load failed to be retrieved or does not support load check.
    loads = [load for load in loads if load and DevServer.CPU_LOAD in load and
             DevServer.is_free_disk_ok(load) and
             DevServer.is_apache_client_count_ok(load)]
    if not loads:
        logging.debug('Failed to retrieve load stats from any devserver. No '
                      'load balancing can be applied.')
        return None
    loads = [load for load in loads if _is_load_healthy(load)]
    if not loads:
        logging.error('No devserver has the capacity to be selected.')
        return None
    loads = sorted(loads, cmp=_compare_load)
    return loads[0]['devserver']


def resolve(build, hostname=None, ban_list=None):
    """Resolve a devserver can be used for given build and hostname.

    @param build: Name of a build to stage on devserver, e.g.,
                  ChromeOS build: daisy-release/R50-1234.0.0
                  Launch Control build: git_mnc_release/shamu-eng
    @param hostname: Hostname of a devserver for, default is None, which means
            devserver is not restricted by the network location of the host.
    @param ban_list: The blacklist of devservers shouldn't be chosen.

    @return: A DevServer instance that can be used to stage given build for the
             given host.
    """
    if utils.is_launch_control_build(build):
        return AndroidBuildServer.resolve(build, hostname)
    else:
        return ImageServer.resolve(build, hostname, ban_list=ban_list)
