# Copyright (c) 2012 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import inspect
import logging
import os
import re
import signal
import socket
import struct
import time
import urllib2
import uuid

from autotest_lib.client.common_lib import base_utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import global_config
from autotest_lib.client.common_lib import lsbrelease_utils
from autotest_lib.client.common_lib.cros.graphite import stats_es_mock
from autotest_lib.client.cros import constants


CONFIG = global_config.global_config

# Keep checking if the pid is alive every second until the timeout (in seconds)
CHECK_PID_IS_ALIVE_TIMEOUT = 6

_LOCAL_HOST_LIST = ('localhost', '127.0.0.1')

# The default address of a vm gateway.
DEFAULT_VM_GATEWAY = '10.0.2.2'

# Google Storage bucket URI to store results in.
DEFAULT_OFFLOAD_GSURI = CONFIG.get_config_value(
        'CROS', 'results_storage_server', default=None)

# Default Moblab Ethernet Interface.
_MOBLAB_ETH_0 = 'eth0'
_MOBLAB_ETH_1 = 'eth1'

# A list of subnets that requires dedicated devserver and drone in the same
# subnet. Each item is a tuple of (subnet_ip, mask_bits), e.g.,
# ('192.168.0.0', 24))
RESTRICTED_SUBNETS = []
restricted_subnets_list = CONFIG.get_config_value(
        'CROS', 'restricted_subnets', type=list, default=[])
# TODO(dshi): Remove the code to split subnet with `:` after R51 is off stable
# channel, and update shadow config to use `/` as delimiter for consistency.
for subnet in restricted_subnets_list:
    ip, mask_bits = subnet.split('/') if '/' in subnet else subnet.split(':')
    RESTRICTED_SUBNETS.append((ip, int(mask_bits)))

# regex pattern for CLIENT/wireless_ssid_ config. For example, global config
# can have following config in CLIENT section to indicate that hosts in subnet
# 192.168.0.1/24 should use wireless ssid of `ssid_1`
# wireless_ssid_192.168.0.1/24: ssid_1
WIRELESS_SSID_PATTERN = 'wireless_ssid_(.*)/(\d+)'

def get_built_in_ethernet_nic_name():
    """Gets the moblab public network interface.

    If the eth0 is an USB interface, try to use eth1 instead. Otherwise
    use eth0 by default.
    """
    try:
        cmd_result = base_utils.run('readlink -f /sys/class/net/eth0')
        if cmd_result.exit_status == 0 and 'usb' in cmd_result.stdout:
            cmd_result = base_utils.run('readlink -f /sys/class/net/eth1')
            if cmd_result.exit_status == 0 and not ('usb' in cmd_result.stdout):
                logging.info('Eth0 is a USB dongle, use eth1 as moblab nic.')
                return _MOBLAB_ETH_1
    except error.CmdError:
        # readlink is not supported.
        logging.info('No readlink available, use eth0 as moblab nic.')
        pass
    return _MOBLAB_ETH_0


def ping(host, deadline=None, tries=None, timeout=60):
    """Attempt to ping |host|.

    Shell out to 'ping' if host is an IPv4 addres or 'ping6' if host is an
    IPv6 address to try to reach |host| for |timeout| seconds.
    Returns exit code of ping.

    Per 'man ping', if you specify BOTH |deadline| and |tries|, ping only
    returns 0 if we get responses to |tries| pings within |deadline| seconds.

    Specifying |deadline| or |count| alone should return 0 as long as
    some packets receive responses.

    Note that while this works with literal IPv6 addresses it will not work
    with hostnames that resolve to IPv6 only.

    @param host: the host to ping.
    @param deadline: seconds within which |tries| pings must succeed.
    @param tries: number of pings to send.
    @param timeout: number of seconds after which to kill 'ping' command.
    @return exit code of ping command.
    """
    args = [host]
    ping_cmd = 'ping6' if re.search(r':.*:', host) else 'ping'

    if deadline:
        args.append('-w%d' % deadline)
    if tries:
        args.append('-c%d' % tries)

    return base_utils.run(ping_cmd, args=args, verbose=True,
                          ignore_status=True, timeout=timeout,
                          stdout_tee=base_utils.TEE_TO_LOGS,
                          stderr_tee=base_utils.TEE_TO_LOGS).exit_status


def host_is_in_lab_zone(hostname):
    """Check if the host is in the CLIENT.dns_zone.

    @param hostname: The hostname to check.
    @returns True if hostname.dns_zone resolves, otherwise False.
    """
    host_parts = hostname.split('.')
    dns_zone = CONFIG.get_config_value('CLIENT', 'dns_zone', default=None)
    fqdn = '%s.%s' % (host_parts[0], dns_zone)
    try:
        socket.gethostbyname(fqdn)
        return True
    except socket.gaierror:
        return False


def host_could_be_in_afe(hostname):
    """Check if the host could be in Autotest Front End.

    Report whether or not a host could be in AFE, without actually
    consulting AFE. This method exists because some systems are in the
    lab zone, but not actually managed by AFE.

    @param hostname: The hostname to check.
    @returns True if hostname is in lab zone, and does not match *-dev-*
    """
    # Do the 'dev' check first, so that we skip DNS lookup if the
    # hostname matches. This should give us greater resilience to lab
    # failures.
    return (hostname.find('-dev-') == -1) and host_is_in_lab_zone(hostname)


def get_chrome_version(job_views):
    """
    Retrieves the version of the chrome binary associated with a job.

    When a test runs we query the chrome binary for it's version and drop
    that value into a client keyval. To retrieve the chrome version we get all
    the views associated with a test from the db, including those of the
    server and client jobs, and parse the version out of the first test view
    that has it. If we never ran a single test in the suite the job_views
    dictionary will not contain a chrome version.

    This method cannot retrieve the chrome version from a dictionary that
    does not conform to the structure of an autotest tko view.

    @param job_views: a list of a job's result views, as returned by
                      the get_detailed_test_views method in rpc_interface.
    @return: The chrome version string, or None if one can't be found.
    """

    # Aborted jobs have no views.
    if not job_views:
        return None

    for view in job_views:
        if (view.get('attributes')
            and constants.CHROME_VERSION in view['attributes'].keys()):

            return view['attributes'].get(constants.CHROME_VERSION)

    logging.warning('Could not find chrome version for failure.')
    return None


def get_default_interface_mac_address():
    """Returns the default moblab MAC address."""
    return get_interface_mac_address(
            get_built_in_ethernet_nic_name())


def get_interface_mac_address(interface):
    """Return the MAC address of a given interface.

    @param interface: Interface to look up the MAC address of.
    """
    interface_link = base_utils.run(
            'ip addr show %s | grep link/ether' % interface).stdout
    # The output will be in the format of:
    # 'link/ether <mac> brd ff:ff:ff:ff:ff:ff'
    return interface_link.split()[1]


def get_moblab_id():
    """Gets the moblab random id.

    The random id file is cached on disk. If it does not exist, a new file is
    created the first time.

    @returns the moblab random id.
    """
    moblab_id_filepath = '/home/moblab/.moblab_id'
    if os.path.exists(moblab_id_filepath):
        with open(moblab_id_filepath, 'r') as moblab_id_file:
            random_id = moblab_id_file.read()
    else:
        random_id = uuid.uuid1()
        with open(moblab_id_filepath, 'w') as moblab_id_file:
            moblab_id_file.write('%s' % random_id)
    return random_id


def get_offload_gsuri():
    """Return the GSURI to offload test results to.

    For the normal use case this is the results_storage_server in the
    global_config.

    However partners using Moblab will be offloading their results to a
    subdirectory of their image storage buckets. The subdirectory is
    determined by the MAC Address of the Moblab device.

    @returns gsuri to offload test results to.
    """
    # For non-moblab, use results_storage_server or default.
    if not lsbrelease_utils.is_moblab():
        return DEFAULT_OFFLOAD_GSURI

    # For moblab, use results_storage_server or image_storage_server as bucket
    # name and mac-address/moblab_id as path.
    gsuri = DEFAULT_OFFLOAD_GSURI
    if not gsuri:
        gsuri = "%sresults/" % CONFIG.get_config_value('CROS', 'image_storage_server')

    return '%s%s/%s/' % (
            gsuri, get_interface_mac_address(get_built_in_ethernet_nic_name()),
            get_moblab_id())


# TODO(petermayo): crosbug.com/31826 Share this with _GsUpload in
# //chromite.git/buildbot/prebuilt.py somewhere/somehow
def gs_upload(local_file, remote_file, acl, result_dir=None,
              transfer_timeout=300, acl_timeout=300):
    """Upload to GS bucket.

    @param local_file: Local file to upload
    @param remote_file: Remote location to upload the local_file to.
    @param acl: name or file used for controlling access to the uploaded
                file.
    @param result_dir: Result directory if you want to add tracing to the
                       upload.
    @param transfer_timeout: Timeout for this upload call.
    @param acl_timeout: Timeout for the acl call needed to confirm that
                        the uploader has permissions to execute the upload.

    @raise CmdError: the exit code of the gsutil call was not 0.

    @returns True/False - depending on if the upload succeeded or failed.
    """
    # https://developers.google.com/storage/docs/accesscontrol#extension
    CANNED_ACLS = ['project-private', 'private', 'public-read',
                   'public-read-write', 'authenticated-read',
                   'bucket-owner-read', 'bucket-owner-full-control']
    _GSUTIL_BIN = 'gsutil'
    acl_cmd = None
    if acl in CANNED_ACLS:
        cmd = '%s cp -a %s %s %s' % (_GSUTIL_BIN, acl, local_file, remote_file)
    else:
        # For private uploads we assume that the overlay board is set up
        # properly and a googlestore_acl.xml is present, if not this script
        # errors
        cmd = '%s cp -a private %s %s' % (_GSUTIL_BIN, local_file, remote_file)
        if not os.path.exists(acl):
            logging.error('Unable to find ACL File %s.', acl)
            return False
        acl_cmd = '%s setacl %s %s' % (_GSUTIL_BIN, acl, remote_file)
    if not result_dir:
        base_utils.run(cmd, timeout=transfer_timeout, verbose=True)
        if acl_cmd:
            base_utils.run(acl_cmd, timeout=acl_timeout, verbose=True)
        return True
    with open(os.path.join(result_dir, 'tracing'), 'w') as ftrace:
        ftrace.write('Preamble\n')
        base_utils.run(cmd, timeout=transfer_timeout, verbose=True,
                       stdout_tee=ftrace, stderr_tee=ftrace)
        if acl_cmd:
            ftrace.write('\nACL setting\n')
            # Apply the passed in ACL xml file to the uploaded object.
            base_utils.run(acl_cmd, timeout=acl_timeout, verbose=True,
                           stdout_tee=ftrace, stderr_tee=ftrace)
        ftrace.write('Postamble\n')
        return True


def gs_ls(uri_pattern):
    """Returns a list of URIs that match a given pattern.

    @param uri_pattern: a GS URI pattern, may contain wildcards

    @return A list of URIs matching the given pattern.

    @raise CmdError: the gsutil command failed.

    """
    gs_cmd = ' '.join(['gsutil', 'ls', uri_pattern])
    result = base_utils.system_output(gs_cmd).splitlines()
    return [path.rstrip() for path in result if path]


def nuke_pids(pid_list, signal_queue=[signal.SIGTERM, signal.SIGKILL]):
    """
    Given a list of pid's, kill them via an esclating series of signals.

    @param pid_list: List of PID's to kill.
    @param signal_queue: Queue of signals to send the PID's to terminate them.

    @return: A mapping of the signal name to the number of processes it
        was sent to.
    """
    sig_count = {}
    # Though this is slightly hacky it beats hardcoding names anyday.
    sig_names = dict((k, v) for v, k in signal.__dict__.iteritems()
                     if v.startswith('SIG'))
    for sig in signal_queue:
        logging.debug('Sending signal %s to the following pids:', sig)
        sig_count[sig_names.get(sig, 'unknown_signal')] = len(pid_list)
        for pid in pid_list:
            logging.debug('Pid %d', pid)
            try:
                os.kill(pid, sig)
            except OSError:
                # The process may have died from a previous signal before we
                # could kill it.
                pass
        if sig == signal.SIGKILL:
            return sig_count
        pid_list = [pid for pid in pid_list if base_utils.pid_is_alive(pid)]
        if not pid_list:
            break
        time.sleep(CHECK_PID_IS_ALIVE_TIMEOUT)
    failed_list = []
    for pid in pid_list:
        if base_utils.pid_is_alive(pid):
            failed_list.append('Could not kill %d for process name: %s.' % pid,
                               base_utils.get_process_name(pid))
    if failed_list:
        raise error.AutoservRunError('Following errors occured: %s' %
                                     failed_list, None)
    return sig_count


def externalize_host(host):
    """Returns an externally accessible host name.

    @param host: a host name or address (string)

    @return An externally visible host name or address

    """
    return socket.gethostname() if host in _LOCAL_HOST_LIST else host


def urlopen_socket_timeout(url, data=None, timeout=5):
    """
    Wrapper to urllib2.urlopen with a socket timeout.

    This method will convert all socket timeouts to
    TimeoutExceptions, so we can use it in conjunction
    with the rpc retry decorator and continue to handle
    other URLErrors as we see fit.

    @param url: The url to open.
    @param data: The data to send to the url (eg: the urlencoded dictionary
                 used with a POST call).
    @param timeout: The timeout for this urlopen call.

    @return: The response of the urlopen call.

    @raises: error.TimeoutException when a socket timeout occurs.
             urllib2.URLError for errors that not caused by timeout.
             urllib2.HTTPError for errors like 404 url not found.
    """
    old_timeout = socket.getdefaulttimeout()
    socket.setdefaulttimeout(timeout)
    try:
        return urllib2.urlopen(url, data=data)
    except urllib2.URLError as e:
        if type(e.reason) is socket.timeout:
            raise error.TimeoutException(str(e))
        raise
    finally:
        socket.setdefaulttimeout(old_timeout)


def parse_chrome_version(version_string):
    """
    Parse a chrome version string and return version and milestone.

    Given a chrome version of the form "W.X.Y.Z", return "W.X.Y.Z" as
    the version and "W" as the milestone.

    @param version_string: Chrome version string.
    @return: a tuple (chrome_version, milestone). If the incoming version
             string is not of the form "W.X.Y.Z", chrome_version will
             be set to the incoming "version_string" argument and the
             milestone will be set to the empty string.
    """
    match = re.search('(\d+)\.\d+\.\d+\.\d+', version_string)
    ver = match.group(0) if match else version_string
    milestone = match.group(1) if match else ''
    return ver, milestone


def is_localhost(server):
    """Check if server is equivalent to localhost.

    @param server: Name of the server to check.

    @return: True if given server is equivalent to localhost.

    @raise socket.gaierror: If server name failed to be resolved.
    """
    if server in _LOCAL_HOST_LIST:
        return True
    try:
        return (socket.gethostbyname(socket.gethostname()) ==
                socket.gethostbyname(server))
    except socket.gaierror:
        logging.error('Failed to resolve server name %s.', server)
        return False


def is_puppylab_vm(server):
    """Check if server is a virtual machine in puppylab.

    In the virtual machine testing environment (i.e., puppylab), each
    shard VM has a hostname like localhost:<port>.

    @param server: Server name to check.

    @return True if given server is a virtual machine in puppylab.

    """
    # TODO(mkryu): This is a puppylab specific hack. Please update
    # this method if you have a better solution.
    regex = re.compile(r'(.+):\d+')
    m = regex.match(server)
    if m:
        return m.group(1) in _LOCAL_HOST_LIST
    return False


def get_function_arg_value(func, arg_name, args, kwargs):
    """Get the value of the given argument for the function.

    @param func: Function being called with given arguments.
    @param arg_name: Name of the argument to look for value.
    @param args: arguments for function to be called.
    @param kwargs: keyword arguments for function to be called.

    @return: The value of the given argument for the function.

    @raise ValueError: If the argument is not listed function arguemnts.
    @raise KeyError: If no value is found for the given argument.
    """
    if arg_name in kwargs:
        return kwargs[arg_name]

    argspec = inspect.getargspec(func)
    index = argspec.args.index(arg_name)
    try:
        return args[index]
    except IndexError:
        try:
            # The argument can use a default value. Reverse the default value
            # so argument with default value can be counted from the last to
            # the first.
            return argspec.defaults[::-1][len(argspec.args) - index - 1]
        except IndexError:
            raise KeyError('Argument %s is not given a value. argspec: %s, '
                           'args:%s, kwargs:%s' %
                           (arg_name, argspec, args, kwargs))


def has_systemd():
    """Check if the host is running systemd.

    @return: True if the host uses systemd, otherwise returns False.
    """
    return os.path.basename(os.readlink('/proc/1/exe')) == 'systemd'


def version_match(build_version, release_version, update_url=''):
    """Compare release versino from lsb-release with cros-version label.

    build_version is a string based on build name. It is prefixed with builder
    info and branch ID, e.g., lumpy-release/R43-6809.0.0. It may not include
    builder info, e.g., lumpy-release, in which case, update_url shall be passed
    in to determine if the build is a trybot or pgo-generate build.
    release_version is retrieved from lsb-release.
    These two values might not match exactly.

    The method is designed to compare version for following 6 scenarios with
    samples of build version and expected release version:
    1. trybot non-release build (paladin, pre-cq or test-ap build).
    build version:   trybot-lumpy-paladin/R27-3837.0.0-b123
    release version: 3837.0.2013_03_21_1340

    2. trybot release build.
    build version:   trybot-lumpy-release/R27-3837.0.0-b456
    release version: 3837.0.0

    3. buildbot official release build.
    build version:   lumpy-release/R27-3837.0.0
    release version: 3837.0.0

    4. non-official paladin rc build.
    build version:   lumpy-paladin/R27-3878.0.0-rc7
    release version: 3837.0.0-rc7

    5. chrome-perf build.
    build version:   lumpy-chrome-perf/R28-3837.0.0-b2996
    release version: 3837.0.0

    6. pgo-generate build.
    build version:   lumpy-release-pgo-generate/R28-3837.0.0-b2996
    release version: 3837.0.0-pgo-generate

    TODO: This logic has a bug if a trybot paladin build failed to be
    installed in a DUT running an older trybot paladin build with same
    platform number, but different build number (-b###). So to conclusively
    determine if a tryjob paladin build is imaged successfully, we may need
    to find out the date string from update url.

    @param build_version: Build name for cros version, e.g.
                          peppy-release/R43-6809.0.0 or R43-6809.0.0
    @param release_version: Release version retrieved from lsb-release,
                            e.g., 6809.0.0
    @param update_url: Update url which include the full builder information.
                       Default is set to empty string.

    @return: True if the values match, otherwise returns False.
    """
    # If the build is from release, CQ or PFQ builder, cros-version label must
    # be ended with release version in lsb-release.
    if build_version.endswith(release_version):
        return True

    # Remove R#- and -b# at the end of build version
    stripped_version = re.sub(r'(R\d+-|-b\d+)', '', build_version)
    # Trim the builder info, e.g., trybot-lumpy-paladin/
    stripped_version = stripped_version.split('/')[-1]

    is_trybot_non_release_build = (
            re.match(r'.*trybot-.+-(paladin|pre-cq|test-ap|toolchain)',
                     build_version) or
            re.match(r'.*trybot-.+-(paladin|pre-cq|test-ap|toolchain)',
                     update_url))

    # Replace date string with 0 in release_version
    release_version_no_date = re.sub(r'\d{4}_\d{2}_\d{2}_\d+', '0',
                                    release_version)
    has_date_string = release_version != release_version_no_date

    is_pgo_generate_build = (
            re.match(r'.+-pgo-generate', build_version) or
            re.match(r'.+-pgo-generate', update_url))

    # Remove |-pgo-generate| in release_version
    release_version_no_pgo = release_version.replace('-pgo-generate', '')
    has_pgo_generate = release_version != release_version_no_pgo

    if is_trybot_non_release_build:
        if not has_date_string:
            logging.error('A trybot paladin or pre-cq build is expected. '
                          'Version "%s" is not a paladin or pre-cq  build.',
                          release_version)
            return False
        return stripped_version == release_version_no_date
    elif is_pgo_generate_build:
        if not has_pgo_generate:
            logging.error('A pgo-generate build is expected. Version '
                          '"%s" is not a pgo-generate build.',
                          release_version)
            return False
        return stripped_version == release_version_no_pgo
    else:
        if has_date_string:
            logging.error('Unexpected date found in a non trybot paladin or '
                          'pre-cq build.')
            return False
        # Versioned build, i.e., rc or release build.
        return stripped_version == release_version


def get_real_user():
    """Get the real user that runs the script.

    The function check environment variable SUDO_USER for the user if the
    script is run with sudo. Otherwise, it returns the value of environment
    variable USER.

    @return: The user name that runs the script.

    """
    user = os.environ.get('SUDO_USER')
    if not user:
        user = os.environ.get('USER')
    return user


def get_service_pid(service_name):
    """Return pid of service.

    @param service_name: string name of service.

    @return: pid or 0 if service is not running.
    """
    if has_systemd():
        # systemctl show prints 'MainPID=0' if the service is not running.
        cmd_result = base_utils.run('systemctl show -p MainPID %s' %
                                    service_name, ignore_status=True)
        return int(cmd_result.stdout.split('=')[1])
    else:
        cmd_result = base_utils.run('status %s' % service_name,
                                        ignore_status=True)
        if 'start/running' in cmd_result.stdout:
            return int(cmd_result.stdout.split()[3])
        return 0


def control_service(service_name, action='start', ignore_status=True):
    """Controls a service. It can be used to start, stop or restart
    a service.

    @param service_name: string service to be restarted.

    @param action: string choice of action to control command.

    @param ignore_status: boolean ignore if system command fails.

    @return: status code of the executed command.
    """
    if action not in ('start', 'stop', 'restart'):
        raise ValueError('Unknown action supplied as parameter.')

    control_cmd = action + ' ' + service_name
    if has_systemd():
        control_cmd = 'systemctl ' + control_cmd
    return base_utils.system(control_cmd, ignore_status=ignore_status)


def restart_service(service_name, ignore_status=True):
    """Restarts a service

    @param service_name: string service to be restarted.

    @param ignore_status: boolean ignore if system command fails.

    @return: status code of the executed command.
    """
    return control_service(service_name, action='restart', ignore_status=ignore_status)


def start_service(service_name, ignore_status=True):
    """Starts a service

    @param service_name: string service to be started.

    @param ignore_status: boolean ignore if system command fails.

    @return: status code of the executed command.
    """
    return control_service(service_name, action='start', ignore_status=ignore_status)


def stop_service(service_name, ignore_status=True):
    """Stops a service

    @param service_name: string service to be stopped.

    @param ignore_status: boolean ignore if system command fails.

    @return: status code of the executed command.
    """
    return control_service(service_name, action='stop', ignore_status=ignore_status)


def sudo_require_password():
    """Test if the process can run sudo command without using password.

    @return: True if the process needs password to run sudo command.

    """
    try:
        base_utils.run('sudo -n true')
        return False
    except error.CmdError:
        logging.warn('sudo command requires password.')
        return True


def is_in_container():
    """Check if the process is running inside a container.

    @return: True if the process is running inside a container, otherwise False.
    """
    result = base_utils.run('grep -q "/lxc/" /proc/1/cgroup',
                            verbose=False, ignore_status=True)
    return result.exit_status == 0


def is_flash_installed():
    """
    The Adobe Flash binary is only distributed with internal builds.
    """
    return (os.path.exists('/opt/google/chrome/pepper/libpepflashplayer.so')
        and os.path.exists('/opt/google/chrome/pepper/pepper-flash.info'))


def verify_flash_installed():
    """
    The Adobe Flash binary is only distributed with internal builds.
    Warn users of public builds of the extra dependency.
    """
    if not is_flash_installed():
        raise error.TestNAError('No Adobe Flash binary installed.')


def is_in_same_subnet(ip_1, ip_2, mask_bits=24):
    """Check if two IP addresses are in the same subnet with given mask bits.

    The two IP addresses are string of IPv4, e.g., '192.168.0.3'.

    @param ip_1: First IP address to compare.
    @param ip_2: Second IP address to compare.
    @param mask_bits: Number of mask bits for subnet comparison. Default to 24.

    @return: True if the two IP addresses are in the same subnet.

    """
    mask = ((2L<<mask_bits-1) -1)<<(32-mask_bits)
    ip_1_num = struct.unpack('!I', socket.inet_aton(ip_1))[0]
    ip_2_num = struct.unpack('!I', socket.inet_aton(ip_2))[0]
    return ip_1_num & mask == ip_2_num & mask


def get_ip_address(hostname):
    """Get the IP address of given hostname.

    @param hostname: Hostname of a DUT.

    @return: The IP address of given hostname. None if failed to resolve
             hostname.
    """
    try:
        if hostname:
            return socket.gethostbyname(hostname)
    except socket.gaierror as e:
        logging.error('Failed to get IP address of %s, error: %s.', hostname, e)


def get_servers_in_same_subnet(host_ip, mask_bits, servers=None,
                               server_ip_map=None):
    """Get the servers in the same subnet of the given host ip.

    @param host_ip: The IP address of a dut to look for devserver.
    @param mask_bits: Number of mask bits.
    @param servers: A list of servers to be filtered by subnet specified by
                    host_ip and mask_bits.
    @param server_ip_map: A map between the server name and its IP address.
            The map can be pre-built for better performance, e.g., when
            allocating a drone for an agent task.

    @return: A list of servers in the same subnet of the given host ip.

    """
    matched_servers = []
    if not servers and not server_ip_map:
        raise ValueError('Either `servers` or `server_ip_map` must be given.')
    if not servers:
        servers = server_ip_map.keys()
    # Make sure server_ip_map is an empty dict if it's not set.
    if not server_ip_map:
        server_ip_map = {}
    for server in servers:
        server_ip = server_ip_map.get(server, get_ip_address(server))
        if server_ip and is_in_same_subnet(server_ip, host_ip, mask_bits):
            matched_servers.append(server)
    return matched_servers


def get_restricted_subnet(hostname, restricted_subnets=RESTRICTED_SUBNETS):
    """Get the restricted subnet of given hostname.

    @param hostname: Name of the host to look for matched restricted subnet.
    @param restricted_subnets: A list of restricted subnets, default is set to
            RESTRICTED_SUBNETS.

    @return: A tuple of (subnet_ip, mask_bits), which defines a restricted
             subnet.
    """
    host_ip = get_ip_address(hostname)
    if not host_ip:
        return
    for subnet_ip, mask_bits in restricted_subnets:
        if is_in_same_subnet(subnet_ip, host_ip, mask_bits):
            return subnet_ip, mask_bits


def get_wireless_ssid(hostname):
    """Get the wireless ssid based on given hostname.

    The method tries to locate the wireless ssid in the same subnet of given
    hostname first. If none is found, it returns the default setting in
    CLIENT/wireless_ssid.

    @param hostname: Hostname of the test device.

    @return: wireless ssid for the test device.
    """
    default_ssid = CONFIG.get_config_value('CLIENT', 'wireless_ssid',
                                           default=None)
    host_ip = get_ip_address(hostname)
    if not host_ip:
        return default_ssid

    # Get all wireless ssid in the global config.
    ssids = CONFIG.get_config_value_regex('CLIENT', WIRELESS_SSID_PATTERN)

    # There could be multiple subnet matches, pick the one with most strict
    # match, i.e., the one with highest maskbit.
    matched_ssid = default_ssid
    matched_maskbit = -1
    for key, value in ssids.items():
        # The config key filtered by regex WIRELESS_SSID_PATTERN has a format of
        # wireless_ssid_[subnet_ip]/[maskbit], for example:
        # wireless_ssid_192.168.0.1/24
        # Following line extract the subnet ip and mask bit from the key name.
        match = re.match(WIRELESS_SSID_PATTERN, key)
        subnet_ip, maskbit = match.groups()
        maskbit = int(maskbit)
        if (is_in_same_subnet(subnet_ip, host_ip, maskbit) and
            maskbit > matched_maskbit):
            matched_ssid = value
            matched_maskbit = maskbit
    return matched_ssid


def parse_launch_control_build(build_name):
    """Get branch, target, build_id from the given Launch Control build_name.

    @param build_name: Name of a Launch Control build, should be formated as
                       branch/target/build_id

    @return: Tuple of branch, target, build_id
    @raise ValueError: If the build_name is not correctly formated.
    """
    branch, target, build_id = build_name.split('/')
    return branch, target, build_id


def parse_android_target(target):
    """Get board and build type from the given target.

    @param target: Name of an Android build target, e.g., shamu-eng.

    @return: Tuple of board, build_type
    @raise ValueError: If the target is not correctly formated.
    """
    board, build_type = target.split('-')
    return board, build_type


def parse_launch_control_target(target):
    """Parse the build target and type from a Launch Control target.

    The Launch Control target has the format of build_target-build_type, e.g.,
    shamu-eng or dragonboard-userdebug. This method extracts the build target
    and type from the target name.

    @param target: Name of a Launch Control target, e.g., shamu-eng.

    @return: (build_target, build_type), e.g., ('shamu', 'userdebug')
    """
    match = re.match('(?P<build_target>.+)-(?P<build_type>[^-]+)', target)
    if match:
        return match.group('build_target'), match.group('build_type')
    else:
        return None, None


def is_launch_control_build(build):
    """Check if a given build is a Launch Control build.

    @param build: Name of a build, e.g.,
                  ChromeOS build: daisy-release/R50-1234.0.0
                  Launch Control build: git_mnc_release/shamu-eng

    @return: True if the build name matches the pattern of a Launch Control
             build, False otherwise.
    """
    try:
        _, target, _ = parse_launch_control_build(build)
        build_target, _ = parse_launch_control_target(target)
        if build_target:
            return True
    except ValueError:
        # parse_launch_control_build or parse_launch_control_target failed.
        pass
    return False


def which(exec_file):
    """Finds an executable file.

    If the file name contains a path component, it is checked as-is.
    Otherwise, we check with each of the path components found in the system
    PATH prepended. This behavior is similar to the 'which' command-line tool.

    @param exec_file: Name or path to desired executable.

    @return: An actual path to the executable, or None if not found.
    """
    if os.path.dirname(exec_file):
        return exec_file if os.access(exec_file, os.X_OK) else None
    sys_path = os.environ.get('PATH')
    prefix_list = sys_path.split(os.pathsep) if sys_path else []
    for prefix in prefix_list:
        path = os.path.join(prefix, exec_file)
        if os.access(path, os.X_OK):
            return path


class TimeoutError(error.TestError):
    """Error raised when we time out when waiting on a condition."""
    pass


def poll_for_condition(condition,
                       exception=None,
                       timeout=10,
                       sleep_interval=0.1,
                       desc=None):
    """Polls until a condition becomes true.

    @param condition: function taking no args and returning bool
    @param exception: exception to throw if condition doesn't become true
    @param timeout: maximum number of seconds to wait
    @param sleep_interval: time to sleep between polls
    @param desc: description of default TimeoutError used if 'exception' is
                 None

    @return The true value that caused the poll loop to terminate.

    @raise 'exception' arg if supplied; TimeoutError otherwise
    """
    start_time = time.time()
    while True:
        value = condition()
        if value:
            return value
        if time.time() + sleep_interval - start_time > timeout:
            if exception:
                logging.error(exception)
                raise exception

            if desc:
                desc = 'Timed out waiting for condition: ' + desc
            else:
                desc = 'Timed out waiting for unnamed condition'
            logging.error(desc)
            raise TimeoutError(desc)

        time.sleep(sleep_interval)


class metrics_mock(stats_es_mock.mock_class_base):
    """mock class for metrics in case chromite is not installed."""
    pass
