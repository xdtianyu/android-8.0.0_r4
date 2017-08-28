# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import unittest

import common
from autotest_lib.server.hosts import host_info


class HostInfoTest(unittest.TestCase):
    """Tests the non-trivial attributes of HostInfo."""

    def setUp(self):
        self.info = host_info.HostInfo()


    def test_build_needs_prefix(self):
        """The build prefix is of the form '<type>-version:'"""
        self.info.labels = ['cros-version', 'ab-version', 'testbed-version',
                            'fwrw-version', 'fwro-version']
        self.assertIsNone(self.info.build)


    def test_build_prefix_must_be_anchored(self):
        """Ensure that build ignores prefixes occuring mid-string."""
        self.info.labels = ['not-at-start-cros-version:cros1',
                            'not-at-start-ab-version:ab1',
                            'not-at-start-testbed-version:testbed1']
        self.assertIsNone(self.info.build)


    def test_build_ignores_firmware(self):
        """build attribute should ignore firmware versions."""
        self.info.labels = ['fwrw-version:fwrw1', 'fwro-version:fwro1']
        self.assertIsNone(self.info.build)


    def test_build_returns_first_match(self):
        """When multiple labels match, first one should be used as build."""
        self.info.labels = ['cros-version:cros1', 'cros-version:cros2']
        self.assertEqual(self.info.build, 'cros1')
        self.info.labels = ['ab-version:ab1', 'ab-version:ab2']
        self.assertEqual(self.info.build, 'ab1')
        self.info.labels = ['testbed-version:tb1', 'testbed-version:tb2']
        self.assertEqual(self.info.build, 'tb1')


    def test_build_prefer_cros_over_others(self):
        """When multiple versions are available, prefer cros."""
        self.info.labels = ['testbed-version:tb1', 'ab-version:ab1',
                            'cros-version:cros1']
        self.assertEqual(self.info.build, 'cros1')
        self.info.labels = ['cros-version:cros1', 'ab-version:ab1',
                            'testbed-version:tb1']
        self.assertEqual(self.info.build, 'cros1')


    def test_build_prefer_ab_over_testbed(self):
        """When multiple versions are available, prefer ab over testbed."""
        self.info.labels = ['testbed-version:tb1', 'ab-version:ab1']
        self.assertEqual(self.info.build, 'ab1')
        self.info.labels = ['ab-version:ab1', 'testbed-version:tb1']
        self.assertEqual(self.info.build, 'ab1')


    def test_os_no_match(self):
        """Use proper prefix to search for os information."""
        self.info.labels = ['something_else', 'cros-version:hana',
                            'os_without_colon']
        self.assertEqual(self.info.os, '')


    def test_os_returns_first_match(self):
        """Return the first matching os label."""
        self.info.labels = ['os:linux', 'os:windows', 'os_corrupted_label']
        self.assertEqual(self.info.os, 'linux')


    def test_board_no_match(self):
        """Use proper prefix to search for board information."""
        self.info.labels = ['something_else', 'cros-version:hana', 'os:blah',
                            'board_my_board_no_colon']
        self.assertEqual(self.info.board, '')


    def test_board_returns_first_match(self):
        """Return the first matching board label."""
        self.info.labels = ['board_corrupted', 'board:walk', 'board:bored']
        self.assertEqual(self.info.board, 'walk')


    def test_pools_no_match(self):
        """Use proper prefix to search for pool information."""
        self.info.labels = ['something_else', 'cros-version:hana', 'os:blah',
                            'board_my_board_no_colon', 'board:my_board']
        self.assertEqual(self.info.pools, set())


    def test_pools_returns_all_matches(self):
        """Return all matching pool labels."""
        self.info.labels = ['board_corrupted', 'board:walk', 'board:bored',
                            'pool:first_pool', 'pool:second_pool']
        self.assertEqual(self.info.pools, {'second_pool', 'first_pool'})


class InMemoryHostInfoStoreTest(unittest.TestCase):
    """Basic tests for CachingHostInfoStore using InMemoryHostInfoStore."""

    def setUp(self):
        self.store = host_info.InMemoryHostInfoStore()


    def _verify_host_info_data(self, host_info, labels, attributes):
        """Verifies the data in the given host_info."""
        self.assertListEqual(host_info.labels, labels)
        self.assertDictEqual(host_info.attributes, attributes)


    def test_first_get_refreshes_cache(self):
        """Test that the first call to get gets the data from store."""
        self.store.info = host_info.HostInfo(['label1'], {'attrib1': 'val1'})
        got = self.store.get()
        self._verify_host_info_data(got, ['label1'], {'attrib1': 'val1'})


    def test_repeated_get_returns_from_cache(self):
        """Tests that repeated calls to get do not refresh cache."""
        self.store.info = host_info.HostInfo(['label1'], {'attrib1': 'val1'})
        got = self.store.get()
        self._verify_host_info_data(got, ['label1'], {'attrib1': 'val1'})

        self.store.info = host_info.HostInfo(['label1', 'label2'], {})
        got = self.store.get()
        self._verify_host_info_data(got, ['label1'], {'attrib1': 'val1'})


    def test_get_uncached_always_refreshes_cache(self):
        """Tests that calling get_uncached always refreshes the cache."""
        self.store.info = host_info.HostInfo(['label1'], {'attrib1': 'val1'})
        got = self.store.get(force_refresh=True)
        self._verify_host_info_data(got, ['label1'], {'attrib1': 'val1'})

        self.store.info = host_info.HostInfo(['label1', 'label2'], {})
        got = self.store.get(force_refresh=True)
        self._verify_host_info_data(got, ['label1', 'label2'], {})


    def test_commit(self):
        """Test that commit sends data to store."""
        info = host_info.HostInfo(['label1'], {'attrib1': 'val1'})
        self._verify_host_info_data(self.store.info, [], {})
        self.store.commit(info)
        self._verify_host_info_data(self.store.info, ['label1'],
                                    {'attrib1': 'val1'})


    def test_commit_then_get(self):
        """Test a commit-get roundtrip."""
        got = self.store.get()
        self._verify_host_info_data(got, [], {})

        info = host_info.HostInfo(['label1'], {'attrib1': 'val1'})
        self.store.commit(info)
        got = self.store.get()
        self._verify_host_info_data(got, ['label1'], {'attrib1': 'val1'})


    def test_commit_then_get_uncached(self):
        """Test a commit-get_uncached roundtrip."""
        got = self.store.get()
        self._verify_host_info_data(got, [], {})

        info = host_info.HostInfo(['label1'], {'attrib1': 'val1'})
        self.store.commit(info)
        got = self.store.get(force_refresh=True)
        self._verify_host_info_data(got, ['label1'], {'attrib1': 'val1'})


    def test_commit_deepcopies_data(self):
        """Once commited, changes to HostInfo don't corrupt the store."""
        info = host_info.HostInfo(['label1'], {'attrib1': {'key1': 'data1'}})
        self.store.commit(info)
        info.labels.append('label2')
        info.attributes['attrib1']['key1'] = 'data2'
        self._verify_host_info_data(self.store.info,
                                    ['label1'], {'attrib1': {'key1': 'data1'}})


    def test_get_returns_deepcopy(self):
        """The cached object is protected from |get| caller modifications."""
        self.store.info = host_info.HostInfo(['label1'],
                                             {'attrib1': {'key1': 'data1'}})
        got = self.store.get()
        self._verify_host_info_data(got,
                                    ['label1'], {'attrib1': {'key1': 'data1'}})
        got.labels.append('label2')
        got.attributes['attrib1']['key1'] = 'data2'
        got = self.store.get()
        self._verify_host_info_data(got,
                                    ['label1'], {'attrib1': {'key1': 'data1'}})


class ExceptionRaisingStore(host_info.CachingHostInfoStore):
    """A test class that always raises on refresh / commit."""

    def __init__(self):
        super(ExceptionRaisingStore, self).__init__()
        self.refresh_raises = True
        self.commit_raises = True


    def _refresh_impl(self):
        if self.refresh_raises:
            raise host_info.StoreError('no can do')
        return host_info.HostInfo()

    def _commit_impl(self, _):
        if self.commit_raises:
            raise host_info.StoreError('wont wont wont')


class CachingHostInfoStoreErrorTest(unittest.TestCase):
    """Tests error behaviours of CachingHostInfoStore."""

    def setUp(self):
        self.store = ExceptionRaisingStore()


    def test_failed_refresh_cleans_cache(self):
        """Sanity checks return values when refresh raises."""
        with self.assertRaises(host_info.StoreError):
            self.store.get()
        # Since |get| hit an error, a subsequent get should again hit the store.
        with self.assertRaises(host_info.StoreError):
            self.store.get()


    def test_failed_commit_cleans_cache(self):
        """Check that a failed commit cleanes cache."""
        # Let's initialize the store without errors.
        self.store.refresh_raises = False
        self.store.get(force_refresh=True)
        self.store.refresh_raises = True

        with self.assertRaises(host_info.StoreError):
            self.store.commit(host_info.HostInfo())
        # Since |commit| hit an error, a subsequent get should again hit the
        # store.
        with self.assertRaises(host_info.StoreError):
            self.store.get()


class GetStoreFromMachineTest(unittest.TestCase):
    """Tests the get_store_from_machine function."""

    def test_machine_is_dict(self):
        machine = {
                'something': 'else',
                'host_info_store': 5
        }
        self.assertEqual(host_info.get_store_from_machine(machine), 5)


    def test_machine_is_string(self):
        machine = 'hostname'
        self.assertTrue(isinstance(host_info.get_store_from_machine(machine),
                                   host_info.InMemoryHostInfoStore))


if __name__ == '__main__':
    unittest.main()
