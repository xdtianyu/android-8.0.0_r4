# Copyright (c) 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""
This module includes all moblab-related RPCs. These RPCs can only be run
on moblab.
"""

# The boto module is only available/used in Moblab for validation of cloud
# storage access. The module is not available in the test lab environment,
# and the import error is handled.
try:
    import boto
except ImportError:
    boto = None

import ConfigParser
import common
import logging
import os
import re
import shutil
import socket
import StringIO
import subprocess

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import global_config, utils, site_utils
from autotest_lib.frontend.afe import models
from autotest_lib.frontend.afe import rpc_utils
from autotest_lib.server import frontend
from autotest_lib.server.hosts import moblab_host


_CONFIG = global_config.global_config
MOBLAB_BOTO_LOCATION = '/home/moblab/.boto'

# Google Cloud Storage bucket url regex pattern. The pattern is used to extract
# the bucket name from the bucket URL. For example, "gs://image_bucket/google"
# should result in a bucket name "image_bucket".
GOOGLE_STORAGE_BUCKET_URL_PATTERN = re.compile(
        r'gs://(?P<bucket>[a-zA-Z][a-zA-Z0-9-_]*)/?.*')

# Contants used in Json RPC field names.
_IMAGE_STORAGE_SERVER = 'image_storage_server'
_GS_ACCESS_KEY_ID = 'gs_access_key_id'
_GS_SECRETE_ACCESS_KEY = 'gs_secret_access_key'
_RESULT_STORAGE_SERVER = 'results_storage_server'
_USE_EXISTING_BOTO_FILE = 'use_existing_boto_file'

# Location where dhcp leases are stored.
_DHCPD_LEASES = '/var/lib/dhcp/dhcpd.leases'

# File where information about the current device is stored.
_ETC_LSB_RELEASE = '/etc/lsb-release'

@rpc_utils.moblab_only
def get_config_values():
    """Returns all config values parsed from global and shadow configs.

    Config values are grouped by sections, and each section is composed of
    a list of name value pairs.
    """
    sections =_CONFIG.get_sections()
    config_values = {}
    for section in sections:
        config_values[section] = _CONFIG.config.items(section)
    return rpc_utils.prepare_for_serialization(config_values)


def _write_config_file(config_file, config_values, overwrite=False):
    """Writes out a configuration file.

    @param config_file: The name of the configuration file.
    @param config_values: The ConfigParser object.
    @param ovewrite: Flag on if overwriting is allowed.
    """
    if not config_file:
        raise error.RPCException('Empty config file name.')
    if not overwrite and os.path.exists(config_file):
        raise error.RPCException('Config file already exists.')

    if config_values:
        with open(config_file, 'w') as config_file:
            config_values.write(config_file)


def _read_original_config():
    """Reads the orginal configuratino without shadow.

    @return: A configuration object, see global_config_class.
    """
    original_config = global_config.global_config_class()
    original_config.set_config_files(shadow_file='')
    return original_config


def _read_raw_config(config_file):
    """Reads the raw configuration from a configuration file.

    @param: config_file: The path of the configuration file.

    @return: A ConfigParser object.
    """
    shadow_config = ConfigParser.RawConfigParser()
    shadow_config.read(config_file)
    return shadow_config


def _get_shadow_config_from_partial_update(config_values):
    """Finds out the new shadow configuration based on a partial update.

    Since the input is only a partial config, we should not lose the config
    data inside the existing shadow config file. We also need to distinguish
    if the input config info overrides with a new value or reverts back to
    an original value.

    @param config_values: See get_moblab_settings().

    @return: The new shadow configuration as ConfigParser object.
    """
    original_config = _read_original_config()
    existing_shadow = _read_raw_config(_CONFIG.shadow_file)
    for section, config_value_list in config_values.iteritems():
        for key, value in config_value_list:
            if original_config.get_config_value(section, key,
                                                default='',
                                                allow_blank=True) != value:
                if not existing_shadow.has_section(section):
                    existing_shadow.add_section(section)
                existing_shadow.set(section, key, value)
            elif existing_shadow.has_option(section, key):
                existing_shadow.remove_option(section, key)
    return existing_shadow


def _update_partial_config(config_values):
    """Updates the shadow configuration file with a partial config udpate.

    @param config_values: See get_moblab_settings().
    """
    existing_config = _get_shadow_config_from_partial_update(config_values)
    _write_config_file(_CONFIG.shadow_file, existing_config, True)


@rpc_utils.moblab_only
def update_config_handler(config_values):
    """Update config values and override shadow config.

    @param config_values: See get_moblab_settings().
    """
    original_config = _read_original_config()
    new_shadow = ConfigParser.RawConfigParser()
    for section, config_value_list in config_values.iteritems():
        for key, value in config_value_list:
            if original_config.get_config_value(section, key,
                                                default='',
                                                allow_blank=True) != value:
                if not new_shadow.has_section(section):
                    new_shadow.add_section(section)
                new_shadow.set(section, key, value)

    if not _CONFIG.shadow_file or not os.path.exists(_CONFIG.shadow_file):
        raise error.RPCException('Shadow config file does not exist.')
    _write_config_file(_CONFIG.shadow_file, new_shadow, True)

    # TODO (sbasi) crbug.com/403916 - Remove the reboot command and
    # instead restart the services that rely on the config values.
    os.system('sudo reboot')


@rpc_utils.moblab_only
def reset_config_settings():
    """Reset moblab shadow config."""
    with open(_CONFIG.shadow_file, 'w') as config_file:
        pass
    os.system('sudo reboot')


@rpc_utils.moblab_only
def reboot_moblab():
    """Simply reboot the device."""
    os.system('sudo reboot')


@rpc_utils.moblab_only
def set_boto_key(boto_key):
    """Update the boto_key file.

    @param boto_key: File name of boto_key uploaded through handle_file_upload.
    """
    if not os.path.exists(boto_key):
        raise error.RPCException('Boto key: %s does not exist!' % boto_key)
    shutil.copyfile(boto_key, moblab_host.MOBLAB_BOTO_LOCATION)


@rpc_utils.moblab_only
def set_service_account_credential(service_account_filename):
    """Update the service account credential file.

    @param service_account_filename: Name of uploaded file through
            handle_file_upload.
    """
    if not os.path.exists(service_account_filename):
        raise error.RPCException(
                'Service account file: %s does not exist!' %
                service_account_filename)
    shutil.copyfile(
            service_account_filename,
            moblab_host.MOBLAB_SERVICE_ACCOUNT_LOCATION)


@rpc_utils.moblab_only
def set_launch_control_key(launch_control_key):
    """Update the launch_control_key file.

    @param launch_control_key: File name of launch_control_key uploaded through
            handle_file_upload.
    """
    if not os.path.exists(launch_control_key):
        raise error.RPCException('Launch Control key: %s does not exist!' %
                                 launch_control_key)
    shutil.copyfile(launch_control_key,
                    moblab_host.MOBLAB_LAUNCH_CONTROL_KEY_LOCATION)
    # Restart the devserver service.
    os.system('sudo restart moblab-devserver-init')


###########Moblab Config Wizard RPCs #######################
def _get_public_ip_address(socket_handle):
    """Gets the public IP address.

    Connects to Google DNS server using a socket and gets the preferred IP
    address from the connection.

    @param: socket_handle: a unix socket.

    @return: public ip address as string.
    """
    try:
        socket_handle.settimeout(1)
        socket_handle.connect(('8.8.8.8', 53))
        socket_name = socket_handle.getsockname()
        if socket_name is not None:
            logging.info('Got socket name from UDP socket.')
            return socket_name[0]
        logging.warn('Created UDP socket but with no socket_name.')
    except socket.error:
        logging.warn('Could not get socket name from UDP socket.')
    return None


def _get_network_info():
    """Gets the network information.

    TCP socket is used to test the connectivity. If there is no connectivity, try to
    get the public IP with UDP socket.

    @return: a tuple as (public_ip_address, connected_to_internet).
    """
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    ip = _get_public_ip_address(s)
    if ip is not None:
        logging.info('Established TCP connection with well known server.')
        return (ip, True)
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    return (_get_public_ip_address(s), False)


@rpc_utils.moblab_only
def get_network_info():
    """Returns the server ip addresses, and if the server connectivity.

    The server ip addresses as an array of strings, and the connectivity as a
    flag.
    """
    network_info = {}
    info = _get_network_info()
    if info[0] is not None:
        network_info['server_ips'] = [info[0]]
    network_info['is_connected'] = info[1]

    return rpc_utils.prepare_for_serialization(network_info)


# Gets the boto configuration.
def _get_boto_config():
    """Reads the boto configuration from the boto file.

    @return: Boto configuration as ConfigParser object.
    """
    boto_config = ConfigParser.ConfigParser()
    boto_config.read(MOBLAB_BOTO_LOCATION)
    return boto_config


@rpc_utils.moblab_only
def get_cloud_storage_info():
    """RPC handler to get the cloud storage access information.
    """
    cloud_storage_info = {}
    value =_CONFIG.get_config_value('CROS', _IMAGE_STORAGE_SERVER)
    if value is not None:
        cloud_storage_info[_IMAGE_STORAGE_SERVER] = value
    value = _CONFIG.get_config_value('CROS', _RESULT_STORAGE_SERVER,
            default=None)
    if value is not None:
        cloud_storage_info[_RESULT_STORAGE_SERVER] = value

    boto_config = _get_boto_config()
    sections = boto_config.sections()

    if sections:
        cloud_storage_info[_USE_EXISTING_BOTO_FILE] = True
    else:
        cloud_storage_info[_USE_EXISTING_BOTO_FILE] = False
    if 'Credentials' in sections:
        options = boto_config.options('Credentials')
        if _GS_ACCESS_KEY_ID in options:
            value = boto_config.get('Credentials', _GS_ACCESS_KEY_ID)
            cloud_storage_info[_GS_ACCESS_KEY_ID] = value
        if _GS_SECRETE_ACCESS_KEY in options:
            value = boto_config.get('Credentials', _GS_SECRETE_ACCESS_KEY)
            cloud_storage_info[_GS_SECRETE_ACCESS_KEY] = value

    return rpc_utils.prepare_for_serialization(cloud_storage_info)


def _get_bucket_name_from_url(bucket_url):
    """Gets the bucket name from a bucket url.

    @param: bucket_url: the bucket url string.
    """
    if bucket_url:
        match = GOOGLE_STORAGE_BUCKET_URL_PATTERN.match(bucket_url)
        if match:
            return match.group('bucket')
    return None


def _is_valid_boto_key(key_id, key_secret):
    """Checks if the boto key is valid.

    @param: key_id: The boto key id string.
    @param: key_secret: The boto key string.

    @return: A tuple as (valid_boolean, details_string).
    """
    if not key_id or not key_secret:
        return (False, "Empty key id or secret.")
    conn = boto.connect_gs(key_id, key_secret)
    try:
        buckets = conn.get_all_buckets()
        return (True, None)
    except boto.exception.GSResponseError:
        details = "The boto access key is not valid"
        return (False, details)
    finally:
        conn.close()


def _is_valid_bucket(key_id, key_secret, bucket_name):
    """Checks if a bucket is valid and accessible.

    @param: key_id: The boto key id string.
    @param: key_secret: The boto key string.
    @param: bucket name string.

    @return: A tuple as (valid_boolean, details_string).
    """
    if not key_id or not key_secret or not bucket_name:
        return (False, "Server error: invalid argument")
    conn = boto.connect_gs(key_id, key_secret)
    bucket = conn.lookup(bucket_name)
    conn.close()
    if bucket:
        return (True, None)
    return (False, "Bucket %s does not exist." % bucket_name)


def _is_valid_bucket_url(key_id, key_secret, bucket_url):
    """Validates the bucket url is accessible.

    @param: key_id: The boto key id string.
    @param: key_secret: The boto key string.
    @param: bucket url string.

    @return: A tuple as (valid_boolean, details_string).
    """
    bucket_name = _get_bucket_name_from_url(bucket_url)
    if bucket_name:
        return _is_valid_bucket(key_id, key_secret, bucket_name)
    return (False, "Bucket url %s is not valid" % bucket_url)


def _validate_cloud_storage_info(cloud_storage_info):
    """Checks if the cloud storage information is valid.

    @param: cloud_storage_info: The JSON RPC object for cloud storage info.

    @return: A tuple as (valid_boolean, details_string).
    """
    valid = True
    details = None
    if not cloud_storage_info[_USE_EXISTING_BOTO_FILE]:
        key_id = cloud_storage_info[_GS_ACCESS_KEY_ID]
        key_secret = cloud_storage_info[_GS_SECRETE_ACCESS_KEY]
        valid, details = _is_valid_boto_key(key_id, key_secret)

        if valid:
            valid, details = _is_valid_bucket_url(
                key_id, key_secret, cloud_storage_info[_IMAGE_STORAGE_SERVER])

        # allows result bucket to be empty.
        if valid and cloud_storage_info[_RESULT_STORAGE_SERVER]:
            valid, details = _is_valid_bucket_url(
                key_id, key_secret, cloud_storage_info[_RESULT_STORAGE_SERVER])
    return (valid, details)


def _create_operation_status_response(is_ok, details):
    """Helper method to create a operation status reponse.

    @param: is_ok: Boolean for if the operation is ok.
    @param: details: A detailed string.

    @return: A serialized JSON RPC object.
    """
    status_response = {'status_ok': is_ok}
    if details:
        status_response['status_details'] = details
    return rpc_utils.prepare_for_serialization(status_response)


@rpc_utils.moblab_only
def validate_cloud_storage_info(cloud_storage_info):
    """RPC handler to check if the cloud storage info is valid.

    @param cloud_storage_info: The JSON RPC object for cloud storage info.
    """
    valid, details = _validate_cloud_storage_info(cloud_storage_info)
    return _create_operation_status_response(valid, details)


@rpc_utils.moblab_only
def submit_wizard_config_info(cloud_storage_info):
    """RPC handler to submit the cloud storage info.

    @param cloud_storage_info: The JSON RPC object for cloud storage info.
    """
    valid, details = _validate_cloud_storage_info(cloud_storage_info)
    if not valid:
        return _create_operation_status_response(valid, details)
    config_update = {}
    config_update['CROS'] = [
        (_IMAGE_STORAGE_SERVER, cloud_storage_info[_IMAGE_STORAGE_SERVER]),
        (_RESULT_STORAGE_SERVER, cloud_storage_info[_RESULT_STORAGE_SERVER])
    ]
    _update_partial_config(config_update)

    if not cloud_storage_info[_USE_EXISTING_BOTO_FILE]:
        boto_config = ConfigParser.RawConfigParser()
        boto_config.add_section('Credentials')
        boto_config.set('Credentials', _GS_ACCESS_KEY_ID,
                        cloud_storage_info[_GS_ACCESS_KEY_ID])
        boto_config.set('Credentials', _GS_SECRETE_ACCESS_KEY,
                        cloud_storage_info[_GS_SECRETE_ACCESS_KEY])
        _write_config_file(MOBLAB_BOTO_LOCATION, boto_config, True)

    _CONFIG.parse_config_file()
    services = ['moblab-devserver-init', 'moblab-apache-init',
    'moblab-devserver-cleanup-init', ' moblab-gsoffloader_s-init',
    'moblab-base-container-init', 'moblab-scheduler-init', 'moblab-gsoffloader-init']
    cmd = ';/sbin/restart '.join(services)
    os.system(cmd)

    return _create_operation_status_response(True, None)


@rpc_utils.moblab_only
def get_version_info():
    """ RPC handler to get informaiton about the version of the moblab.

    @return: A serialized JSON RPC object.
    """
    lines = open(_ETC_LSB_RELEASE).readlines()
    version_response = {
        x.split('=')[0]: x.split('=')[1] for x in lines if '=' in x}
    version_response['MOBLAB_ID'] = site_utils.get_moblab_id();
    version_response['MOBLAB_MAC_ADDRESS'] = (
        site_utils.get_default_interface_mac_address())
    return rpc_utils.prepare_for_serialization(version_response)


@rpc_utils.moblab_only
def get_connected_dut_info():
    """ RPC handler to get informaiton about the DUTs connected to the moblab.

    @return: A serialized JSON RPC object.
    """
    # Make a list of the connected DUT's
    leases = _get_dhcp_dut_leases()

    # Get a list of the AFE configured DUT's
    hosts = list(rpc_utils.get_host_query((), False, True, {}))
    models.Host.objects.populate_relationships(hosts, models.Label,
                                               'label_list')
    configured_duts = {}
    for host in hosts:
        labels = [label.name for label in host.label_list]
        labels.sort()
        configured_duts[host.hostname] = ', '.join(labels)

    return rpc_utils.prepare_for_serialization(
            {'configured_duts': configured_duts,
             'connected_duts': leases})


def _get_dhcp_dut_leases():
     """ Extract information about connected duts from the dhcp server.

     @return: A dict of ipaddress to mac address for each device connected.
     """
     lease_info = open(_DHCPD_LEASES).read()

     leases = {}
     for lease in lease_info.split('lease'):
         if lease.find('binding state active;') != -1:
             ipaddress = lease.split('\n')[0].strip(' {')
             last_octet = int(ipaddress.split('.')[-1].strip())
             if last_octet > 150:
                 continue
             mac_address_search = re.search('hardware ethernet (.*);', lease)
             if mac_address_search:
                 leases[ipaddress] = mac_address_search.group(1)
     return leases


@rpc_utils.moblab_only
def add_moblab_dut(ipaddress):
    """ RPC handler to add a connected DUT to autotest.

    @param ipaddress: IP address of the DUT.

    @return: A string giving information about the status.
    """
    cmd = '/usr/local/autotest/cli/atest host create %s &' % ipaddress
    subprocess.call(cmd, shell=True)
    return (True, 'DUT %s added to Autotest' % ipaddress)


@rpc_utils.moblab_only
def remove_moblab_dut(ipaddress):
    """ RPC handler to remove DUT entry from autotest.

    @param ipaddress: IP address of the DUT.

    @return: True if the command succeeds without an exception
    """
    models.Host.smart_get(ipaddress).delete()
    return (True, 'DUT %s deleted from Autotest' % ipaddress)


@rpc_utils.moblab_only
def add_moblab_label(ipaddress, label_name):
    """ RPC handler to add a label in autotest to a DUT entry.

    @param ipaddress: IP address of the DUT.
    @param label_name: The label name.

    @return: A string giving information about the status.
    """
    # Try to create the label in case it does not already exist.
    label = None
    try:
        label = models.Label.add_object(name=label_name)
    except:
        label = models.Label.smart_get(label_name)
    host_obj = models.Host.smart_get(ipaddress)
    if label:
        label.host_set.add(host_obj)
        return (True, 'Added label %s to DUT %s' % (label_name, ipaddress))
    return (False, 'Failed to add label %s to DUT %s' % (label_name, ipaddress))


@rpc_utils.moblab_only
def remove_moblab_label(ipaddress, label_name):
    """ RPC handler to remove a label in autotest from a DUT entry.

    @param ipaddress: IP address of the DUT.
    @param label_name: The label name.

    @return: A string giving information about the status.
    """
    host_obj = models.Host.smart_get(ipaddress)
    models.Label.smart_get(label_name).host_set.remove(host_obj)
    return (True, 'Removed label %s from DUT %s' % (label_name, ipaddress))


def _get_connected_dut_labels(requested_label, only_first_label=True):
    """ Query the DUT's attached to the moblab and return a filtered list of labels.

    @param requested_label:  the label name you are requesting.
    @param only_first_label:  if the device has the same label name multiple times only
                              return the first label value in the list.

    @return: A de-duped list of requested dut labels attached to the moblab.
    """
    hosts = list(rpc_utils.get_host_query((), False, True, {}))
    if not hosts:
        return []
    models.Host.objects.populate_relationships(hosts, models.Label,
                                               'label_list')
    labels = set()
    for host in hosts:
        for label in host.label_list:
            if requested_label in label.name:
                labels.add(label.name.replace(requested_label, ''))
                if only_first_label:
                    break
    return list(labels)


@rpc_utils.moblab_only
def get_connected_boards():
    """ RPC handler to get a list of the boards connected to the moblab.

    @return: A de-duped list of board types attached to the moblab.
    """
    boards = _get_connected_dut_labels("board:")
    boards.sort()
    return boards


@rpc_utils.moblab_only
def get_connected_pools():
    """ RPC handler to get a list of the pools labels on the DUT's connected.

    @return: A de-duped list of pool labels.
    """
    pools = _get_connected_dut_labels("pool:", False)
    pools.sort()
    return pools


@rpc_utils.moblab_only
def get_builds_for_board(board_name):
    """ RPC handler to find the most recent builds for a board.


    @param board_name: The name of a connected board.
    @return: A list of string with the most recent builds for the latest
             three milestones.
    """
    return _get_builds_for_in_directory(board_name + '-release')


@rpc_utils.moblab_only
def get_firmware_for_board(board_name):
    """ RPC handler to find the most recent firmware for a board.


    @param board_name: The name of a connected board.
    @return: A list of strings with the most recent firmware builds for the
             latest three milestones.
    """
    return _get_builds_for_in_directory(board_name + '-firmware')


def _get_builds_for_in_directory(directory_name, milestone_limit=3, build_limit=20):
    """ Fetch the most recent builds for the last three milestones from gcs.


    @param directory_name: The sub-directory under the configured GCS image
                           storage bucket to search.


    @return: A string list no longer than <milestone_limit> x <build_limit> items,
             containing the most recent <build_limit> builds from the last
             milestone_limit milestones.
    """
    output = StringIO.StringIO()
    gs_image_location =_CONFIG.get_config_value('CROS', _IMAGE_STORAGE_SERVER)
    utils.run('gsutil', args=('ls', gs_image_location + directory_name), stdout_tee=output)
    lines = output.getvalue().split('\n')
    output.close()
    builds = [line.replace(gs_image_location,'').strip('/ ') for line in lines if line != '']
    build_matcher = re.compile(r'^.*\/R([0-9]*)-.*')
    build_map = {}
    for build in builds:
        match = build_matcher.match(build)
        if match:
            milestone = match.group(1)
            if milestone not in build_map:
                build_map[milestone] = []
            build_map[milestone].append(build)
    milestones = build_map.keys()
    milestones.sort()
    milestones.reverse()
    build_list = []
    for milestone in milestones[:milestone_limit]:
         builds = build_map[milestone]
         builds.reverse()
         build_list.extend(builds[:build_limit])
    return build_list


@rpc_utils.moblab_only
def run_suite(board, build, suite, ro_firmware=None, rw_firmware=None, pool=None):
    """ RPC handler to run a test suite.

    @param board: a board name connected to the moblab.
    @param build: a build name of a build in the GCS.
    @param suite: the name of a suite to run
    @param ro_firmware: Optional ro firmware build number to use.
    @param rw_firmware: Optional rw firmware build number to use.
    @param pool: Optional pool name to run the suite in.

    @return: None
    """
    builds = {'cros-version': build}
    if rw_firmware:
        builds['fwrw-version'] = rw_firmware
    if ro_firmware:
        builds['fwro-version'] = ro_firmware
    afe = frontend.AFE(user='moblab')
    afe.run('create_suite_job', board=board, builds=builds, name=suite,
    pool=pool, run_prod_code=False, test_source_build=build,
    wait_for_results=False)
