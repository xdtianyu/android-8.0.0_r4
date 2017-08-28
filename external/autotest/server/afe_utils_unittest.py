#!/usr/bin/python
# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import unittest

import common
from autotest_lib.server import afe_utils
from autotest_lib.server import site_utils


class MockHost(object):
    """
    Object to represent host used to test afe_util.py methods.
    """

    def __init__(self, labels=[]):
      """
      Setup the self._afe_host attribute since that's what we're mostly using.
      """
      self._afe_host = site_utils.EmptyAFEHost()
      self._afe_host.labels = labels


class AfeUtilsUnittest(unittest.TestCase):
    """
    Test functions in afe_utils.py.
    """

    def testGetLabels(self):
        """
        Test method get_labels returns expected labels.
        """
        prefix = 'prefix'
        expected_labels = [prefix + ':' + str(i) for i in range(5)]
        all_labels = []
        all_labels += expected_labels
        all_labels += [str(i) for i in range(6, 9)]
        host = MockHost(labels=all_labels)
        got_labels = afe_utils.get_labels(host, prefix)

        self.assertItemsEqual(got_labels, expected_labels)


    def testGetLabelsAll(self):
        """
        Test method get_labels returns all labels.
        """
        prefix = 'prefix'
        prefix_labels = [prefix + ':' + str(i) for i in range(5)]
        all_labels = []
        all_labels += prefix_labels
        all_labels += [str(i) for i in range(6, 9)]
        host = MockHost(labels=all_labels)
        got_labels = afe_utils.get_labels(host)

        self.assertItemsEqual(got_labels, all_labels)


if __name__ == '__main__':
    unittest.main()

