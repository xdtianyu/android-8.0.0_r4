# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

AUTHOR = "ARC++ Team"
NAME = "gts"
PURPOSE = "GTS tests for ARC++"
CRITERIA = "All tests with SUITE=gts must pass."

TIME = "SHORT"
TEST_CATEGORY = "General"
TEST_CLASS = "suite"
TEST_TYPE = "Server"

DOC = """
This suite wraps the current GTS bundle for autotest.
"""

import common
from autotest_lib.server.cros import provision
from autotest_lib.server.cros.dynamic_suite import dynamic_suite


args_dict['name'] = NAME
args_dict['job'] = job
args_dict['add_experimental'] = True
args_dict['version_prefix'] = provision.CROS_VERSION_PREFIX

dynamic_suite.reimage_and_run(**args_dict)

