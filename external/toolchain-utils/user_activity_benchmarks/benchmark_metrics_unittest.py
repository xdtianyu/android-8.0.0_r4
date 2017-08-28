#!/usr/bin/python2

# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Unit tests for the benchmark_metrics module."""

import mock
import unittest
import benchmark_metrics


class MetricsComputationTest(unittest.TestCase):
  """Test class for MetricsComputation class."""

  def __init__(self, *args, **kwargs):
    super(MetricsComputationTest, self).__init__(*args, **kwargs)

  def testComputeDistanceForFunction(self):
    child_functions_statistics_sample = {
        'f,file_f': 0.1,
        'g,file_g': 0.2,
        'h,file_h': 0.3,
        'i,file_i': 0.4
    }
    child_functions_statistics_reference = {
        'f,file_f': 0.4,
        'i,file_i': 0.4,
        'h,file_h2': 0.2
    }
    distance = benchmark_metrics.ComputeDistanceForFunction(
        child_functions_statistics_sample, child_functions_statistics_reference)
    self.assertEqual(distance, 2.0)

    distance = benchmark_metrics.ComputeDistanceForFunction({}, {})
    self.assertEqual(distance, 1.0)

    distance = benchmark_metrics.ComputeDistanceForFunction(
        child_functions_statistics_sample, {})
    self.assertEqual(distance, 2.0)

    distance = benchmark_metrics.ComputeDistanceForFunction(
        {}, child_functions_statistics_reference)
    self.assertEqual(distance, 2.0)

  def testComputeScoreForFunction(self):
    score = benchmark_metrics.ComputeScoreForFunction(1.2, 0.3, 0.4)
    self.assertEqual(score, 0.1)

  def testComputeMetricsForComponents(self):
    function_metrics = {
        'func_f,/a/b/file_f': (1.0, 2.3),
        'func_g,/a/b/file_g': (1.1, 1.5),
        'func_h,/c/d/file_h': (2.0, 1.7),
        'func_i,/c/d/file_i': (1.9, 1.8),
        'func_j,/c/d/file_j': (1.8, 1.9),
        'func_k,/e/file_k': (1.2, 2.1),
        'func_l,/e/file_l': (1.3, 3.1)
    }
    cwp_function_groups = [('ab', '/a/b'), ('cd', '/c/d'), ('e', '/e')]
    expected_metrics = {'ab': ('/a/b', 2.0, 2.1, 1.05, 3.8, 1.9),
                        'e': ('/e', 2.0, 2.5, 1.25, 5.2, 2.6),
                        'cd': ('/c/d', 3.0, 5.7, 1.9000000000000001, 5.4, 1.8)}
    result_metrics = benchmark_metrics.ComputeMetricsForComponents(
        cwp_function_groups, function_metrics)

    self.assertDictEqual(expected_metrics, result_metrics)

  def testComputeMetricsForBenchmark(self):
    function_metrics = {'func_f': (1.0, 2.0),
                        'func_g': (1.1, 2.1),
                        'func_h': (1.2, 2.2),
                        'func_i': (1.3, 2.3)}
    expected_benchmark_metrics = \
        (4, 4.6000000000000005, 1.1500000000000001, 8.6, 2.15)
    result_benchmark_metrics = \
        benchmark_metrics.ComputeMetricsForBenchmark(function_metrics)

    self.assertEqual(expected_benchmark_metrics, result_benchmark_metrics)

  def testComputeMetricsForBenchmarkSet(self):
    """TODO(evelinad): Add unit test for ComputeMetricsForBenchmarkSet."""
    pass


if __name__ == '__main__':
  unittest.main()
