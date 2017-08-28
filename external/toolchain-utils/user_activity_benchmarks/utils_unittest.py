#!/usr/bin/python2

# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Unit tests for the utility module."""

import collections
import csv
import unittest

import utils


class UtilsTest(unittest.TestCase):
  """Test class for utility module."""

  def __init__(self, *args, **kwargs):
    super(UtilsTest, self).__init__(*args, **kwargs)
    self._pprof_top_csv_file = 'testdata/input/pprof_top_csv/file1.csv'
    self._pprof_top_file = 'testdata/input/pprof_top/file1.pprof'
    self._pprof_tree_csv_file = 'testdata/input/pprof_tree_csv/file1.csv'
    self._pprof_tree_file = 'testdata/input/pprof_tree/file1.pprof'
    self._pairwise_inclusive_count_test_file = \
        'testdata/input/pairwise_inclusive_count_test.csv'
    self._pairwise_inclusive_count_reference_file = \
        'testdata/input/pairwise_inclusive_count_reference.csv'
    self._inclusive_count_test_file = \
       'testdata/input/inclusive_count_test.csv'
    self._inclusive_count_reference_file = \
        'testdata/input/inclusive_count_reference.csv'

  def testParseFunctionGroups(self):
    cwp_function_groups_lines = \
        ['group1 /a\n', 'group2 /b\n', 'group3 /c\n', 'group4 /d\n']
    expected_output = [('group1', '/a'), ('group2', '/b'), ('group3', '/c'),
                       ('group4', '/d')]
    result = utils.ParseFunctionGroups(cwp_function_groups_lines)

    self.assertListEqual(expected_output, result)

  def testParsePProfTopOutput(self):
    result_pprof_top_output = utils.ParsePprofTopOutput(self._pprof_top_file)
    expected_pprof_top_output = {}

    with open(self._pprof_top_csv_file) as input_file:
      statistics_reader = csv.DictReader(input_file, delimiter=',')

      for statistic in statistics_reader:
        if statistic['file']:
          function_key = ','.join([statistic['function'], statistic['file']])
        else:
          function_key = statistic['function']
        expected_pprof_top_output[function_key] = \
            (statistic['flat'], statistic['flat_p'], statistic['sum_p'],
             statistic['cum'], statistic['cum_p'])

    self.assertDictEqual(result_pprof_top_output, expected_pprof_top_output)

  def testParsePProfTreeOutput(self):
    result_pprof_tree_output = utils.ParsePprofTreeOutput(self._pprof_tree_file)
    expected_pprof_tree_output = collections.defaultdict(dict)

    with open(self._pprof_tree_csv_file) as input_file:
      statistics_reader = csv.DictReader(input_file, delimiter=',')

      for statistic in statistics_reader:
        parent_function_key = \
            ','.join([statistic['parent_function'],
                      statistic['parent_function_file']])
        child_function_key = \
            ','.join([statistic['child_function'],
                      statistic['child_function_file']])

        expected_pprof_tree_output[parent_function_key][child_function_key] = \
            float(statistic['inclusive_count_fraction'])

    self.assertDictEqual(result_pprof_tree_output, expected_pprof_tree_output)

  def testParseCWPInclusiveCountFile(self):
    expected_inclusive_statistics_test = \
        {'func_i,/c/d/file_i': ('i', 5, 4.4, utils.EXTRA_FUNCTION),
         'func_j,/e/file_j': ('j', 6, 5.5, utils.EXTRA_FUNCTION),
         'func_f,/a/b/file_f': ('f', 4, 2.3, utils.EXTRA_FUNCTION),
         'func_h,/c/d/file_h': ('h', 1, 3.3, utils.EXTRA_FUNCTION),
         'func_k,/e/file_k': ('k', 7, 6.6, utils.EXTRA_FUNCTION),
         'func_g,/a/b/file_g': ('g', 2, 2.2, utils.EXTRA_FUNCTION)}
    expected_inclusive_statistics_reference = \
        {'func_i,/c/d/file_i': ('i', 5, 4.0, utils.EXTRA_FUNCTION),
         'func_j,/e/file_j': ('j', 6, 5.0, utils.EXTRA_FUNCTION),
         'func_f,/a/b/file_f': ('f', 1, 1.0, utils.EXTRA_FUNCTION),
         'func_l,/e/file_l': ('l', 7, 6.0, utils.EXTRA_FUNCTION),
         'func_h,/c/d/file_h': ('h', 4, 3.0, utils.EXTRA_FUNCTION),
         'func_g,/a/b/file_g': ('g', 5, 4.4, utils.EXTRA_FUNCTION)}
    result_inclusive_statistics_test = \
        utils.ParseCWPInclusiveCountFile(self._inclusive_count_test_file)
    result_inclusive_statistics_reference = \
        utils.ParseCWPInclusiveCountFile(self._inclusive_count_reference_file)

    self.assertDictEqual(result_inclusive_statistics_test,
                         expected_inclusive_statistics_test)
    self.assertDictEqual(result_inclusive_statistics_reference,
                         expected_inclusive_statistics_reference)

  def testParseCWPPairwiseInclusiveCountFile(self):
    expected_pairwise_inclusive_statistics_test = {
        'func_f': {'func_g,/a/b/file_g2': 0.01,
                   'func_h,/c/d/file_h': 0.02,
                   'func_i,/c/d/file_i': 0.03},
        'func_g': {'func_j,/e/file_j': 0.4,
                   'func_m,/e/file_m': 0.6}
    }
    expected_pairwise_inclusive_statistics_reference = {
        'func_f': {'func_g,/a/b/file_g': 0.1,
                   'func_h,/c/d/file_h': 0.2,
                   'func_i,/c/d/file_i': 0.3},
        'func_g': {'func_j,/e/file_j': 0.4}
    }
    result_pairwise_inclusive_statistics_test = \
        utils.ParseCWPPairwiseInclusiveCountFile(
            self._pairwise_inclusive_count_test_file)
    result_pairwise_inclusive_statistics_reference = \
        utils.ParseCWPPairwiseInclusiveCountFile(
            self._pairwise_inclusive_count_reference_file)

    self.assertDictEqual(result_pairwise_inclusive_statistics_test,
                         expected_pairwise_inclusive_statistics_test)
    self.assertDictEqual(result_pairwise_inclusive_statistics_reference,
                         expected_pairwise_inclusive_statistics_reference)


if __name__ == '__main__':
  unittest.main()
