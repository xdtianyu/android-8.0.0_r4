#!/usr/bin/env python
#
# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import os
import unittest

from vts.utils.python.coverage import gcno_parser
from vts.utils.python.coverage import gcda_parser
from vts.utils.python.coverage import coverage_report


class CoverageReportTest(unittest.TestCase):
    """Unit tests for CoverageReport of vts.utils.python.coverage.
    """

    @classmethod
    def setUpClass(cls):
        root_dir = os.path.join(
            os.getenv('ANDROID_BUILD_TOP'),
            'test/vts/utils/python/coverage/testdata/')
        gcno_path = os.path.join(root_dir, 'sample.gcno')
        with open(gcno_path, 'rb') as file:
            gcno_summary = gcno_parser.GCNOParser(file).Parse()
        gcda_path = os.path.join(root_dir, 'sample.gcda')
        with open(gcda_path, 'rb') as file:
            parser = gcda_parser.GCDAParser(file)
            parser.Parse(gcno_summary)
        cls.gcno_summary = gcno_summary

    def testGenerateLineCoverageVector(self):
        """Tests that coverage vector is correctly generated.

        Runs GenerateLineCoverageVector on sample file and checks
        result.
        """
        src_lines_counts = coverage_report.GenerateLineCoverageVector(
            'sample.c', self.gcno_summary)
        expected = [-1, -1, -1, -1, 2, -1, -1, -1, -1, -1, 2,
                    2, 2, -1, 2, -1, 2, 0, -1, 2, -1, -1, 2, 2, 502,
                    500, -1, -1, 2, -1, 2, -1, -1, -1, 2, -1,
                    -1, -1, -1, 2, 2, 2]
        self.assertEqual(src_lines_counts, expected)


if __name__ == "__main__":
    unittest.main()
