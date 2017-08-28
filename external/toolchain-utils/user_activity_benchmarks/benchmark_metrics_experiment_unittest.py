#!/usr/bin/python2

# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Unit tests for the benchmark_metrics_experiment module."""

import os
import tempfile
import unittest

from benchmark_metrics_experiment import MetricsExperiment


class MetricsExperimentTest(unittest.TestCase):
  """Test class for MetricsExperiment class."""

  def __init__(self, *args, **kwargs):
    super(MetricsExperimentTest, self).__init__(*args, **kwargs)
    self._pairwise_inclusive_count_test_file = \
        'testdata/input/pairwise_inclusive_count_test.csv'
    self._pairwise_inclusive_count_reference_file = \
        'testdata/input/pairwise_inclusive_count_reference.csv'
    self._inclusive_count_test_file = \
        'testdata/input/inclusive_count_test.csv'
    self._inclusive_count_reference_file = \
        'testdata/input/inclusive_count_reference.csv'
    self._cwp_function_groups_file = \
        'testdata/input/cwp_function_groups.txt'

  def _CheckFileContents(self, file_name, expected_content_lines):
    with open(file_name) as input_file:
      result_content_lines = input_file.readlines()
      self.assertListEqual(expected_content_lines, result_content_lines)

  def testExperiment(self):
    group_statistics_file, group_statistics_filename = tempfile.mkstemp()

    os.close(group_statistics_file)

    function_statistics_file, function_statistics_filename = tempfile.mkstemp()

    os.close(function_statistics_file)


    expected_group_statistics_lines = \
        ['group,file_path,function_count,distance_cum,distance_avg,score_cum,'
         'score_avg\n',
         'ab,/a/b,2.0,3.01,1.505,8.26344228895,4.13172114448\n',
         'e,/e,2.0,2.0,1.0,27.5,13.75\n',
         'cd,/c/d,2.0,2.0,1.0,27.5,13.75']
    expected_function_statistics_lines = \
        ['function,file,distance,score\n',
         'func_i,/c/d/file_i,1.0,17.6\n',
         'func_j,/e/file_j,1.0,27.5\n',
         'func_f,/a/b/file_f,1.59,1.4465408805\n',
         'func_h,/c/d/file_h,1.0,9.9\n',
         'func_k,/e/file_k,1.0,0.0\n',
         'func_g,/a/b/file_g,1.42,6.81690140845']
    metric_experiment = \
        MetricsExperiment(self._pairwise_inclusive_count_reference_file,
                          self._pairwise_inclusive_count_test_file,
                          self._inclusive_count_reference_file,
                          self._inclusive_count_test_file,
                          self._cwp_function_groups_file,
                          group_statistics_filename,
                          function_statistics_filename)

    metric_experiment.PerformComputation()
    self._CheckFileContents(group_statistics_filename,
                            expected_group_statistics_lines)
    self._CheckFileContents(function_statistics_filename,
                            expected_function_statistics_lines)
    os.remove(group_statistics_filename)
    os.remove(function_statistics_filename)


if __name__ == '__main__':
  unittest.main()
