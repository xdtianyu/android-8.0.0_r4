# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

import common
from autotest_lib.frontend.afe.json_rpc import proxy as rpc_proxy
from autotest_lib.server.hosts import host_info
from autotest_lib.server.cros.dynamic_suite import frontend_wrappers

class AfeStore(host_info.CachingHostInfoStore):
    """Directly interact with the (given) AFE for host information."""

    _RETRYING_AFE_TIMEOUT_MIN = 5
    _RETRYING_AFE_RETRY_DELAY_SEC = 10

    def __init__(self, hostname, afe=None):
        """
        @param hostname: The name of the host for which we want to track host
                information.
        @param afe: A frontend.AFE object to make RPC calls. Will create one
                internally if None.
        """
        super(AfeStore, self).__init__()
        self._hostname = hostname
        self._afe = afe
        if self._afe is None:
            self._afe = frontend_wrappers.RetryingAFE(
                    timeout_min=self._RETRYING_AFE_TIMEOUT_MIN,
                    delay_sec=self._RETRYING_AFE_RETRY_DELAY_SEC)


    def _refresh_impl(self):
        """Obtains HostInfo directly from the AFE."""
        try:
            hosts = self._afe.get_hosts(hostname=self._hostname)
        except rpc_proxy.JSONRPCException as e:
            raise host_info.StoreError(e)

        if not hosts:
            raise host_info.StoreError('No hosts founds with hostname: %s' %
                                       self._hostname)

        if len(hosts) > 1:
            logging.warning(
                    'Found %d hosts with the name %s. Picking the first one.',
                    len(hosts), self._hostname)
        host = hosts[0]
        return host_info.HostInfo(host.labels, host.attributes)


    def _commit_impl(self, new_info):
        """Commits HostInfo back to the AFE.

        @param new_info: The new HostInfo to commit.
        """
        # TODO(pprabhu) crbug.com/680322
        # This method has a potentially malignent race condition. We obtain a
        # copy of HostInfo from the AFE and then add/remove labels / attribtes
        # based on that. If another user tries to commit it's changes in
        # parallel, we'll end up with corrupted labels / attributes.
        old_info = self._refresh_impl()
        self._remove_labels_on_afe(set(old_info.labels) - set(new_info.labels))
        self._add_labels_on_afe(set(new_info.labels) - set(old_info.labels))

        # TODO(pprabhu) Also commit attributes when we first replace a direct
        # AFE call for attribute update.
        if old_info.attributes != new_info.attributes:
            logging.warning(
                    'Updating attributes is currently not supported. '
                    'attributes update skipped. old attributes: %s, committed '
                    'attributes: %s',
                    old_info.attributes, new_info.attributes)


    def _remove_labels_on_afe(self, labels):
        """Requests the AFE to remove the given labels.

        @param labels: Remove these.
        """
        if not labels:
            return

        logging.debug('removing labels: %s', labels)
        try:
            self._afe.run('host_remove_labels', id=self._hostname,
                          labels=labels)
        except rpc_proxy.JSONRPCException as e:
            raise host_info.StoreError(e)


    def _add_labels_on_afe(self, labels):
        """Requests the AFE to add the given labels.

        @param labels: Add these.
        """
        if not labels:
            return

        logging.info('adding labels: %s', labels)
        try:
            self._afe.run('host_add_labels', id=self._hostname, labels=labels)
        except rpc_proxy.JSONRPCException as e:
            raise host_info.StoreError(e)
