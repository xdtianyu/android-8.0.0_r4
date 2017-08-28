# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

AUTHOR = "ARC Team"
NAME = "cts"
PURPOSE = "Runs the CTS suite."
TIME = "LONG"
TEST_CATEGORY = "functional"
TEST_CLASS = "suite"
TEST_TYPE = "Server"

DOC = """
This suite wraps the current CTS bundle for autotest.
"""

import common
from autotest_lib.server.cros import provision
from autotest_lib.server.cros.dynamic_suite import dynamic_suite

args_dict['max_runtime_mins'] = 960
args_dict['add_experimental'] = True
args_dict['name'] = 'cts'
args_dict['version_prefix'] = provision.CROS_VERSION_PREFIX
args_dict['job'] = job

dynamic_suite.reimage_and_run(**args_dict)
