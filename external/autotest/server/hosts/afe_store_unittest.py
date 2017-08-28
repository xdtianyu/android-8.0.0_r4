# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import mock
import unittest

import common
from autotest_lib.frontend.afe.json_rpc import proxy as rpc_proxy
from autotest_lib.server import frontend
from autotest_lib.server.hosts import afe_store
from autotest_lib.server.hosts import host_info

class AfeStoreTest(unittest.TestCase):
    """Test refresh/commit success cases for AfeStore."""

    def setUp(self):
        self.hostname = 'some-host'
        self.mock_afe = mock.create_autospec(frontend.AFE, instance=True)
        self.store = afe_store.AfeStore(self.hostname, afe=self.mock_afe)


    def _create_mock_host(self, labels, attributes):
        """Create a mock frontend.Host with the given labels and attributes.

        @param labels: The labels to set on the host.
        @param attributes: The attributes to set on the host.
        @returns: A mock object for frontend.Host.
        """
        mock_host = mock.create_autospec(frontend.Host, instance=True)
        mock_host.labels = labels
        mock_host.attributes = attributes
        return mock_host


    def test_refresh(self):
        """Test that refresh correctly translates host information."""
        self.mock_afe.get_hosts.return_value = [
                self._create_mock_host(['label1'], {'attrib1': 'val1'})]
        info = self.store._refresh_impl()
        self.assertListEqual(info.labels, ['label1'])
        self.assertDictEqual(info.attributes, {'attrib1': 'val1'})


    def test_refresh_no_host_raises(self):
        """Test that refresh complains if no host is found."""
        self.mock_afe.get_hosts.return_value = []
        with self.assertRaises(host_info.StoreError):
            self.store._refresh_impl()


    def test_refresh_multiple_hosts_picks_first(self):
        """Test that refresh returns the first host if multiple match."""
        self.mock_afe.get_hosts.return_value = [
                self._create_mock_host(['label1'], {'attrib1': 'val1'}),
                self._create_mock_host(['label2'], {'attrib2': 'val2'})]
        info = self.store._refresh_impl()
        self.assertListEqual(info.labels, ['label1'])
        self.assertDictEqual(info.attributes, {'attrib1': 'val1'})


    def test_commit_labels(self):
        """Tests that labels are updated correctly on commit."""
        self.mock_afe.get_hosts.return_value = [
                self._create_mock_host(['label1'], {})]
        info = host_info.HostInfo(['label2'], {})
        self.store._commit_impl(info)
        self.assertEqual(self.mock_afe.run.call_count, 2)
        expected_run_calls = [
                mock.call('host_remove_labels', id='some-host',
                          labels={'label1'}),
                mock.call('host_add_labels', id='some-host',
                          labels={'label2'}),
        ]
        self.mock_afe.run.assert_has_calls(expected_run_calls,
                                           any_order=True)


    def test_commit_labels_raises(self):
        """Test that exception while committing is translated properly."""
        self.mock_afe.get_hosts.return_value = [
                self._create_mock_host(['label1'], {})]
        self.mock_afe.run.side_effect = rpc_proxy.JSONRPCException('some error')
        info = host_info.HostInfo(['label2'], {})
        with self.assertRaises(host_info.StoreError):
            self.store._commit_impl(info)


    def test_committing_attributes_warns(self):
        """Test that a warning is issued if attribute changes are committed."""
        self.mock_afe.get_hosts.return_value = [
                self._create_mock_host([], {})]
        info = host_info.HostInfo([], {'attrib': 'val'})
        with mock.patch('logging.warning', autospec=True) as mock_warning:
            self.store._commit_impl(info)
        self.assertEqual(mock_warning.call_count, 1)
        self.assertRegexpMatches(
                mock_warning.call_args[0][0],
                '.*Updating attributes is currently not supported.*')


    @unittest.expectedFailure
    def test_commit_adds_attributes(self):
        """Tests that new attributes are added correctly on commit."""
        self.mock_afe.get_hosts.return_value = [
                self._create_mock_host([], {})]
        info = host_info.HostInfo([], {'attrib1': 'val1'})
        self.store._commit_impl(info)
        self.assertEqual(self.mock_afe.set_host_attribute.call_count, 1)
        self.mock_afe.assert_called_once_with('attrib1', 'val1',
                                              hostname=self.hostname)


    @unittest.expectedFailure
    def test_commit_updates_attributes(self):
        """Tests that existing attributes are updated correctly on commit."""
        self.mock_afe.get_hosts.return_value = [
                self._create_mock_host([], {'attrib1': 'val1'})]
        info = host_info.HostInfo([], {'attrib1': 'val1_updated'})
        self.store._commit_impl(info)
        self.assertEqual(self.mock_afe.set_host_attribute.call_count, 1)
        self.mock_afe.assert_called_once_with('attrib1', 'val1_updated',
                                              hostname=self.hostname)


    @unittest.expectedFailure
    def test_commit_deletes_attributes(self):
        """Tests that deleted attributes are updated correctly on commit."""
        self.mock_afe.get_hosts.return_value = [
                self._create_mock_host([], {'attrib1': 'val1'})]
        info = host_info.HostInfo([], {})
        self.store._commit_impl(info)
        self.assertEqual(self.mock_afe.set_host_attribute.call_count, 1)
        self.mock_afe.assert_called_once_with('attrib1', None,
                                              hostname=self.hostname)


if __name__ == '__main__':
    unittest.main()
