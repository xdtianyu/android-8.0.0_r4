# Copyright 2016 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Utility functions for AFE-based interactions.

NOTE: This module should only be used in the context of a running test. Any
      utilities that require accessing the AFE, should do so by creating
      their own instance of the AFE client and interact with it directly.
"""

import common
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import global_config
from autotest_lib.server.cros.dynamic_suite import frontend_wrappers


AFE = frontend_wrappers.RetryingAFE(timeout_min=5, delay_sec=10)
_CROS_VERSION_MAP = AFE.get_stable_version_map(AFE.CROS_IMAGE_TYPE)
_FIRMWARE_VERSION_MAP = AFE.get_stable_version_map(AFE.FIRMWARE_IMAGE_TYPE)
_FAFT_VERSION_MAP = AFE.get_stable_version_map(AFE.FAFT_IMAGE_TYPE)
_ANDROID_VERSION_MAP = AFE.get_stable_version_map(AFE.ANDROID_IMAGE_TYPE)

_CONFIG = global_config.global_config
ENABLE_DEVSERVER_TRIGGER_AUTO_UPDATE = _CONFIG.get_config_value(
        'CROS', 'enable_devserver_trigger_auto_update', type=bool,
        default=False)


def _host_in_lab(host):
    """Check if the host is in the lab and an object the AFE knows.

    This check ensures that autoserv and the host's current job is running
    inside a fully Autotest instance, aka a lab environment. If this is the
    case it then verifies the host is registed with the configured AFE
    instance.

    @param host: Host object to verify.

    @returns The host model object.
    """
    if not host.job or not host.job.in_lab:
        return False
    return host._afe_host


def get_labels(host, prefix=None):
    """Get labels of a host with name started with given prefix.

    @param prefix: Prefix of label names, if None, return all labels.

    @returns List of labels that match the prefix or if prefix is None, all
             labels.
    """
    if not prefix:
        return host._afe_host.labels

    return [label for label in host._afe_host.labels
            if label.startswith(prefix)]


def clear_version_labels(host):
    """Clear version labels for a given host.

    @param host: Host whose version labels to clear.
    """
    host._afe_host.labels = [label for label in host._afe_host.labels
                             if not label.startswith(host.VERSION_PREFIX)]
    if not _host_in_lab(host):
        return

    host_list = [host.hostname]
    labels = AFE.get_labels(
            name__startswith=host.VERSION_PREFIX,
            host__hostname=host.hostname)

    for label in labels:
        label.remove_hosts(hosts=host_list)


def add_version_label(host, image_name):
    """Add version labels to a host.

    @param host: Host to add the version label for.
    @param image_name: Name of the build version to add to the host.
    """
    label = '%s:%s' % (host.VERSION_PREFIX, image_name)
    host._afe_host.labels.append(label)
    if not _host_in_lab(host):
        return
    AFE.run('label_add_hosts', id=label, hosts=[host.hostname])


def get_stable_cros_image_name(board):
    """Retrieve the Chrome OS stable image name for a given board.

    @param board: Board to lookup.

    @returns Name of a Chrome OS image to be installed in order to
            repair the given board.
    """
    return _CROS_VERSION_MAP.get_image_name(board)


def get_stable_firmware_version(board):
    """Retrieve the stable firmware version for a given board.

    @param board: Board to lookup.

    @returns A version of firmware to be installed via
             `chromeos-firmwareupdate` from a repair build.
    """
    return _FIRMWARE_VERSION_MAP.get_version(board)


def get_stable_faft_version(board):
    """Retrieve the stable firmware version for FAFT DUTs.

    @param board: Board to lookup.

    @returns A version of firmware to be installed in order to
            repair firmware on a DUT used for FAFT testing.
    """
    return _FAFT_VERSION_MAP.get_version(board)


def get_stable_android_version(board):
    """Retrieve the stable Android version a given board.

    @param board: Board to lookup.

    @returns Stable version of Android for the given board.
    """
    return _ANDROID_VERSION_MAP.get_version(board)


def get_host_attribute(host, attribute, use_local_value=True):
    """Looks up the value of host attribute for the host.

    @param host: A Host object to lookup for attribute value.
    @param attribute: Name of the host attribute.
    @param use_local_value: Boolean to indicate if the local value or AFE value
            should be retrieved.

    @returns value for the given attribute or None if not found.
    """
    local_value = host._afe_host.attributes.get(attribute)
    if not _host_in_lab(host) or use_local_value:
        return local_value

    hosts = AFE.get_hosts(hostname=host.hostname)
    if hosts and attribute in hosts[0].attributes:
        return hosts[0].attributes[attribute]
    else:
        return local_value


def clear_host_attributes_before_provision(host):
    """Clear host attributes before provision, e.g., job_repo_url.

    @param host: A Host object to clear attributes before provision.
    """
    attributes = host.get_attributes_to_clear_before_provision()
    for attribute in attributes:
        host._afe_host.attributes.pop(attribute, None)
    if not _host_in_lab(host):
        return

    for attribute in attributes:
        update_host_attribute(host, attribute, None)


def update_host_attribute(host, attribute, value):
    """Updates the host attribute with given value.

    @param host: A Host object to update attribute value.
    @param attribute: Name of the host attribute.
    @param value: Value for the host attribute.

    @raises AutoservError: If we failed to update the attribute.
    """
    host._afe_host.attributes[attribute] = value
    if not _host_in_lab(host):
        return

    AFE.set_host_attribute(attribute, value, hostname=host.hostname)
    if get_host_attribute(host, attribute, use_local_value=False) != value:
        raise error.AutoservError(
                'Failed to update host attribute `%s` with %s, host %s' %
                (attribute, value, host.hostname))


def machine_install_and_update_labels(host, *args, **dargs):
    """Calls machine_install and updates the version labels on a host.

    @param host: Host object to run machine_install on.
    @param *args: Args list to pass to machine_install.
    @param **dargs: dargs dict to pass to machine_install.
    """
    clear_version_labels(host)
    clear_host_attributes_before_provision(host)
    # If ENABLE_DEVSERVER_TRIGGER_AUTO_UPDATE is enabled and the host is a
    # CrosHost, devserver will be used to trigger auto-update.
    if host.support_devserver_provision:
        image_name, host_attributes = host.machine_install_by_devserver(
            *args, **dargs)
    else:
        image_name, host_attributes = host.machine_install(*args, **dargs)
    for attribute, value in host_attributes.items():
        update_host_attribute(host, attribute, value)
    add_version_label(host, image_name)
