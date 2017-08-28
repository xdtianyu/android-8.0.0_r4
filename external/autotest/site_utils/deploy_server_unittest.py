#!/usr/bin/python
# Copyright (c) 2014 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Unittests for deploy_server_local.py."""

from __future__ import print_function

import unittest

import deploy_server as deploy_server


class TestDeployServer(unittest.TestCase):
    """Test deploy_server_local with commands mocked out."""

    def test_parse_arguments(self):
        """Test deploy_server_local.parse_arguments."""
        # Only requires args.
        results = deploy_server.parse_arguments(['--afe', 'foo'])
        self.assertEqual(
                {'afe': 'foo', 'servers': [], 'args': [],
                 'cont': False, 'dryrun': False, 'verbose': False,
                 'force_update': False, 'logfile': '/tmp/deployment.log',
                 'update_push_servers': False},
                vars(results))

        # Dryrun, continue
        results = deploy_server.parse_arguments(['--afe', 'foo',
                                                 '--dryrun', '--continue'])
        self.assertDictContainsSubset(
                {'afe': 'foo', 'servers': [], 'args': [],
                 'cont': True, 'dryrun': True, 'verbose': False,
                 'force_update': False, 'logfile': '/tmp/deployment.log',
                 'update_push_servers': False},
                vars(results))

        # List some servers
        results = deploy_server.parse_arguments(['--afe', 'foo',
                                                 'dummy', 'bar'])
        self.assertDictContainsSubset(
                {'afe': 'foo', 'servers': ['dummy', 'bar'], 'args': [],
                 'cont': False, 'dryrun': False, 'verbose': False,
                 'force_update': False, 'logfile': '/tmp/deployment.log',
                 'update_push_servers': False},
                vars(results))

        # List some local args
        results = deploy_server.parse_arguments(['--afe', 'foo',
                                                     '--', 'dummy', 'bar'])
        self.assertDictContainsSubset(
                {'afe': 'foo', 'servers': [], 'args': ['dummy', 'bar'],
                 'cont': False, 'dryrun': False, 'verbose': False,
                 'force_update': False, 'logfile': '/tmp/deployment.log',
                 'update_push_servers': False},
                 vars(results))

        # List everything.
        results = deploy_server.parse_arguments(
                ['--continue', '--afe', 'foo', '--dryrun', 'dummy', 'bar',
                 '--', '--actions-only', '--dryrun', '--update_push_servers',
                 '--force_update'])
        self.assertDictContainsSubset(
                {'afe': 'foo', 'servers': ['dummy', 'bar'],
                 'args': ['--actions-only', '--dryrun',
                          '--update_push_servers', '--force_update'],
                 'cont': True, 'dryrun': True, 'verbose': False},
                vars(results))


if __name__ == '__main__':
    unittest.main()
