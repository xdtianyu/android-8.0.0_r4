# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import glob
import json
import logging
import os
import re
import shutil
import tempfile

from autotest_lib.client.bin import test, utils
from autotest_lib.client.common_lib import autotemp, error

CONFIG_JSON_TEMPLATE = '''
{
    "ociVersion": "1.0.0-rc1",
    "platform": {
        "os": "linux",
        "arch": "all"
    },
    "process": {
        "terminal": true,
        "user": {
            "uid": 10000,
            "gid": 10000
        },
        "args": [
            %s
        ],
        "cwd": "/"
    },
    "root": {
        "path": "rootfs",
        "readonly": false
    },
    "hostname": "runc",
    "mounts": [
    {
        "destination": "/proc",
        "type": "proc",
        "source": "proc"
    },
    {
        "destination": "/dev",
        "type": "tmpfs",
        "source": "tmpfs",
        "options": [
            "nosuid",
            "noexec"
        ]
    }
    ],
    "hooks": {},
    "linux": {
        "namespaces": [
        {
            "type": "cgroup"
        },
        {
            "type": "pid"
        },
        {
            "type": "network"
        },
        {
            "type": "ipc"
        },
        {
            "type": "user"
        },
        {
            "type": "uts"
        },
        {
            "type": "mount"
        }
        ],
        "uidMappings": [
        {
            "hostID": 10000,
            "containerID": 0,
            "size": 10
        }
        ],
        "gidMappings": [
        {
            "hostID": 10000,
            "containerID": 0,
            "size": 10
        }
        ]
    }
}
'''

class security_RunOci(test.test):
    version = 1

    preserve_srcdir = True

    def get_test_option(self, handle):
        """
        Gets the test configuration from the json file given in handle.
        """
        data = json.load(handle)
        return data['run_oci_args'], data['program_argv'], data['expected_result']


    def run_test_in_dir(self, run_oci_args, argv, expected, oci_path):
        """
        Executes the test in the given directory that points to an OCI image.
        """
        ret = 0
        cmd_output = utils.system_output(
                '/usr/bin/run_oci %s %s' % (run_oci_args, oci_path),
                retain_output=True)
        if cmd_output != expected:
            ret = 1
        return ret


    def run_test(self, run_oci_args, argv, expected):
        """
        Runs one test from the src directory.  Return 0 if the test passes,
        return 1 on failure.
        """
        td = autotemp.tempdir()
        os.chown(td.name, 10000, 10000)
        with open(os.path.join(td.name, 'config.json'), 'w') as config_file:
            config_file.write(CONFIG_JSON_TEMPLATE % argv)
        rootfs_path = os.path.join(td.name, 'rootfs')
        os.mkdir(rootfs_path)
        os.chown(rootfs_path, 10000, 10000)
        utils.run(['mount', "--bind", "/", rootfs_path])
        ret = self.run_test_in_dir(run_oci_args, argv, expected, td.name)
        utils.run(['umount', '-f', rootfs_path])
        return ret


    def run_once(self):
        """
        Runs each of the tests specified in the source directory.
        This test fails if any subtest fails. Sub tests exercise the run_oci
        command and check that the correct namespace mappings and mounts are
        made. If any subtest fails, this test will fail.
        """
        failed = []
        ran = 0
        for p in glob.glob('%s/test-*.json' % self.srcdir):
            name = os.path.basename(p)
            logging.info('Running: %s', name)
            run_oci_args, argv, expected = self.get_test_option(file(p))
            if self.run_test(run_oci_args, argv, expected):
                failed.append(name)
            ran += 1
        if ran == 0:
            failed.append('No tests found to run from %s!' % (self.srcdir))
        if failed:
            logging.error('Failed: %s', failed)
            raise error.TestFail('Failed: %s' % failed)
