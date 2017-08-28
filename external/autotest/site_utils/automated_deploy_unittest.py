#!/usr/bin/python
# Copyright (c) 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Unittests for automated_deploy.py."""

from __future__ import print_function

import mock
import unittest

import common
from autotest_lib.site_utils import automated_deploy as ad
from autotest_lib.site_utils.lib import infra


class AutomatedDeployTest(unittest.TestCase):
    """Test automated_deploy with commands mocked out."""

    PUSH_LOG = '''Total 0 (delta 0), reused 0 (delta 0)
remote: Processing changes: done
To https:TEST_URL
123..456  prod -> prod'''

    def setUp(self):
        infra.chdir = mock.MagicMock()


    @mock.patch.object(infra, 'local_runner')
    def testUpdateProdBranch(self, run_cmd):
        """Test automated_deploy.update_prod_branch.

        @param run_cmd: Mock of infra.local_runner call used.
        """
        # Test whether rebase to the given hash when the hash is given.
        run_cmd.return_value = self.PUSH_LOG
        ad.update_prod_branch('test', 'test_dir', '123')
        expect_cmds = [mock.call('git rebase 123 prod', stream_output=True),
                       mock.call('git push origin prod', stream_output=True)]
        run_cmd.assert_has_calls(expect_cmds)

        # Test whether rebase to prod-next branch when the hash is not given.
        run_cmd.return_value = self.PUSH_LOG
        ad.update_prod_branch('test', 'test_dir', None)
        expect_cmds = [mock.call('git rebase origin/prod-next prod',
                                 stream_output=True),
                       mock.call('git push origin prod', stream_output=True)]
        run_cmd.assert_has_calls(expect_cmds)

        # Test to grep the pushed commit range from the normal push log.
        run_cmd.return_value = self.PUSH_LOG
        self.assertEqual(ad.update_prod_branch('test', 'test_dir', None),
                         '123..456')

        # Test to grep the pushed commit range from the failed push log.
        run_cmd.return_value = 'Fail to push prod branch'
        with self.assertRaises(ad.AutoDeployException):
             ad.update_prod_branch('test', 'test_dir', None)


    @mock.patch.object(infra, 'local_runner')
    def testGetPushedCommits(self, run_cmd):
        """Test automated_deploy.get_pushed_commits.

        @param run_cmd: Mock of infra.local_runner call used.
        """
        fake_commits_logs = '123 Test_change_1\n456 Test_change_2'
        run_cmd.return_value = fake_commits_logs

        #Test to get pushed commits for autotest repo.
        repo = 'autotest'
        expect_git_log_cmd = 'git log --oneline 123..456|grep autotest'
        expect_return = ('\n%s:\n%s\n%s\n' %
                         (repo, expect_git_log_cmd, fake_commits_logs))
        actual_return = ad.get_pushed_commits(repo, 'test', '123..456')

        run_cmd.assert_called_with(expect_git_log_cmd, stream_output=True)
        self.assertEqual(expect_return, actual_return)

        #Test to get pushed commits for chromite repo.
        repo = 'chromite'
        expect_git_log_cmd = 'git log --oneline 123..456'
        expect_return = ('\n%s:\n%s\n%s\n' %
                         (repo, expect_git_log_cmd, fake_commits_logs))
        actual_return = ad.get_pushed_commits(repo, 'test', '123..456')

        run_cmd.assert_called_with(expect_git_log_cmd, stream_output=True)
        self.assertEqual(expect_return, actual_return)


if __name__ == '__main__':
    unittest.main()
