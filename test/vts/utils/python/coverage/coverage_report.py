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
"""Generates coverage reports using outputs from GCC.

The GenerateCoverageReport() function returns HTML to display the coverage
at each line of code in a provided source file. Coverage information is
parsed from .gcno and .gcda file contents and combined with the source file
to reconstruct a coverage report. GenerateLineCoverageVector() is a helper
function that produces a vector of line counts and GenerateCoverageHTML()
uses the vector and source to produce the HTML coverage report.
"""

import cgi
import io
import logging
import os
from vts.utils.python.coverage import gcda_parser
from vts.utils.python.coverage import gcno_parser


def GenerateLineCoverageVector(src_file_name, gcno_file_summary):
    """Returns a list of invocation counts for each line in the file.

    Parses the GCNO and GCDA file specified to calculate the number of times
    each line in the source file specified by src_file_name was executed.

    Args:
        src_file_name: string, the source file name.
        gcno_file_summary: FileSummary object after gcno and gcda files have
                           been parsed.

    Returns:
        A list of non-negative integers or -1 representing the number of times
        the i-th line was executed. -1 indicates a line that is not executable.
    """
    src_lines_counts = []
    covered_line_count = 0
    for ident in gcno_file_summary.functions:
        func = gcno_file_summary.functions[ident]
        if not src_file_name == func.src_file_name:
            logging.warn("GenerateLineCoverageVector: \"%s\" file is skipped \"%s\"",
                         func.src_file_name, src_file_name)
            continue
        for block in func.blocks:
            for line in block.lines:
                if line > len(src_lines_counts):
                    src_lines_counts.extend([-1] *
                                            (line - len(src_lines_counts)))
                if src_lines_counts[line - 1] < 0:
                    src_lines_counts[line - 1] = 0
                src_lines_counts[line - 1] += block.count
                if block.count > 0:
                    covered_line_count += 1
    logging.info("GenerateLineCoverageVector: file %s: %s lines covered",
                 src_file_name, str(covered_line_count))
    return src_lines_counts


def GetCoverageStats(src_lines_counts):
    """Returns the coverage stats.

    Args:
        src_lines_counts: A list of non-negative integers or -1 representing
                          the number of times the i-th line was executed.
                          -1 indicates a line that is not executable.

    Returns:
        integer, the number of lines instrumented for coverage measurement
        integer, the number of executed or covered lines
    """
    total = 0
    covered = 0
    if not src_lines_counts or not isinstance(src_lines_counts, list):
        logging.error("GetCoverageStats: input invalid.")
        return total, covered

    for line in src_lines_counts:
        if line < 0:
            continue
        total += 1
        if line > 0:
            covered += 1
    return total, covered

