#!/usr/bin/python2
#
# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Runs an experiment with the benchmark metrics on a pair of CWP data sets.

A data set should contain the files with the pairwise inclusive and the
inclusive statistics. The pairwise inclusive file contains pairs of
parent and child functions with their inclusive count fractions out of the
total amount of inclusive count values and the files of the child functions.
The inclusive file contains the functions with their inclusive count fraction
out of the total amount of inclusive count values and the file name of the
function. The input data should be collected using the scripts
collect_experiment_data.sh or collect_experiment_data_odd_even_session.sh

For every function, this script computes the distance and the score values.
The output is stored in the file cwp_functions_statistics_file.

For every Chrome OS component, this script computes a set of metrics consisting
in the number of functions, the average and cumulative distance and score of
the functions matching the group. The output is stored in the file
cwp_function_groups_statistics_file.
"""

import argparse
import sys

import benchmark_metrics
import utils


class MetricsExperiment(object):
  """Runs an experiment with the benchmark metrics on a pair of data sets."""

  def __init__(self, cwp_pairwise_inclusive_reference,
               cwp_pairwise_inclusive_test, cwp_inclusive_reference,
               cwp_inclusive_test, cwp_function_groups_file,
               cwp_function_groups_statistics_file,
               cwp_function_statistics_file):
    """Initializes the MetricsExperiment class.

    Args:
      cwp_pairwise_inclusive_reference: The CSV file containing the pairwise
        inclusive values from the reference data set.
      cwp_pairwise_inclusive_test: The CSV file containing the pairwise
        inclusive values from the test data set.
      cwp_inclusive_reference: The CSV file containing the inclusive values
        from the reference data set.
      cwp_inclusive_test: The CSV file containing the inclusive values from
        the test data set.
      cwp_function_groups_file: The CSV file containing the groups of functions.
      cwp_function_groups_statistics_file: The output CSV file that will
        contain the metrics for the function groups.
      cwp_function_statistics_file: The output CSV file that will contain the
        metrics for the CWP functions.
    """
    self._cwp_pairwise_inclusive_reference = cwp_pairwise_inclusive_reference
    self._cwp_pairwise_inclusive_test = cwp_pairwise_inclusive_test
    self._cwp_inclusive_reference = cwp_inclusive_reference
    self._cwp_inclusive_test = cwp_inclusive_test
    self._cwp_function_groups_file = cwp_function_groups_file
    self._cwp_function_groups_statistics_file = \
        cwp_function_groups_statistics_file
    self._cwp_function_statistics_file = cwp_function_statistics_file

  def PerformComputation(self):
    """Does the benchmark metrics experimental computation.

    For every function, it is computed a distance based on the sum of the
    differences of the fractions spent in the child functions. Afterwards,
    it is computed a score based on the inclusive values fractions and the
    distance value. The statistics for all the function are written in the file
    self._cwp_function_statistics_file.

    The functions are grouped on Chrome OS components based on the path of the
    file where a function is defined. For every group, there are computed the
    total number of functions matching that group, the cumulative distance, the
    average distance and the cumulative score of the functions.
    """

    inclusive_statistics_reference = \
        utils.ParseCWPInclusiveCountFile(self._cwp_inclusive_reference)
    inclusive_statistics_cum_reference = \
        utils.ComputeCWPCummulativeInclusiveStatistics(
            inclusive_statistics_reference)
    inclusive_statistics_test = \
        utils.ParseCWPInclusiveCountFile(self._cwp_inclusive_test)
    inclusive_statistics_cum_test = \
        utils.ComputeCWPCummulativeInclusiveStatistics(
            inclusive_statistics_test)
    pairwise_inclusive_statistics_reference = \
        utils.ParseCWPPairwiseInclusiveCountFile(
            self._cwp_pairwise_inclusive_reference)
    pairwise_inclusive_fractions_reference = \
        utils.ComputeCWPChildFunctionsFractions(
            inclusive_statistics_cum_reference,
            pairwise_inclusive_statistics_reference)
    pairwise_inclusive_statistics_test = \
        utils.ParseCWPPairwiseInclusiveCountFile(
            self._cwp_pairwise_inclusive_test)
    pairwise_inclusive_fractions_test = \
        utils.ComputeCWPChildFunctionsFractions(
            inclusive_statistics_cum_test,
            pairwise_inclusive_statistics_test)
    parent_function_statistics = {}

    with open(self._cwp_function_groups_file) as input_file:
      cwp_function_groups = utils.ParseFunctionGroups(input_file.readlines())

    for parent_function_key, parent_function_statistics_test \
        in inclusive_statistics_test.iteritems():
      parent_function_name, _ = parent_function_key.split(',')
      parent_function_fraction_test = parent_function_statistics_test[2]

      parent_function_fraction_reference = \
          inclusive_statistics_reference[parent_function_key][2]

      child_functions_fractions_test = \
          pairwise_inclusive_fractions_test.get(parent_function_name, {})

      child_functions_fractions_reference = \
          pairwise_inclusive_fractions_reference.get(parent_function_name, {})

      distance = benchmark_metrics.ComputeDistanceForFunction(
          child_functions_fractions_test, child_functions_fractions_reference)

      parent_function_score_test = benchmark_metrics.ComputeScoreForFunction(
          distance, parent_function_fraction_test,
          parent_function_fraction_reference)

      parent_function_statistics[parent_function_key] = \
          (distance, parent_function_score_test)

    with open(self._cwp_function_statistics_file, 'w') as output_file:
      statistics_lines = ['function,file,distance,score']
      statistics_lines += \
          [','.join([parent_function_key.replace(';;', ','),
                     str(statistic[0]),
                     str(statistic[1])])
           for parent_function_key, statistic
           in parent_function_statistics.iteritems()]
      output_file.write('\n'.join(statistics_lines))

    cwp_groups_statistics_test = benchmark_metrics.ComputeMetricsForComponents(
        cwp_function_groups, parent_function_statistics)

    with open(self._cwp_function_groups_statistics_file, 'w') as output_file:
      group_statistics_lines = \
          ['group,file_path,function_count,distance_cum,distance_avg,score_cum,'
           'score_avg']
      group_statistics_lines += \
          [','.join([group_name,
                     str(statistic[0]),
                     str(statistic[1]),
                     str(statistic[2]),
                     str(statistic[3]),
                     str(statistic[4]),
                     str(statistic[5])])
           for group_name, statistic
           in cwp_groups_statistics_test.iteritems()]
      output_file.write('\n'.join(group_statistics_lines))


def ParseArguments(arguments):
  parser = argparse.ArgumentParser(
      description='Runs an experiment with the benchmark metrics on a pair of '
      'CWP data sets.')
  parser.add_argument(
      '--cwp_pairwise_inclusive_reference',
      required=True,
      help='The reference CSV file that will contain a pair of parent and '
      'child functions with their inclusive count fractions out of the total '
      'amount of inclusive count values.')
  parser.add_argument(
      '--cwp_pairwise_inclusive_test',
      required=True,
      help='The test CSV file that will contain a pair of parent and '
      'child functions with their inclusive count fractions out of the total '
      'amount of inclusive count values.')
  parser.add_argument(
      '--cwp_inclusive_reference',
      required=True,
      help='The reference CSV file that will contain a function with its '
      'inclusive count fraction out of the total amount of inclusive count '
      'values.')
  parser.add_argument(
      '--cwp_inclusive_test',
      required=True,
      help='The test CSV file that will contain a function with its '
      'inclusive count fraction out of the total amount of inclusive count '
      'values.')
  parser.add_argument(
      '-g',
      '--cwp_function_groups_file',
      required=True,
      help='The file that will contain the CWP function groups.'
      'A line consists in the group name and a file path. A group must '
      'represent a ChromeOS component.')
  parser.add_argument(
      '-s',
      '--cwp_function_groups_statistics_file',
      required=True,
      help='The output file that will contain the metric statistics for the '
      'CWP function groups in CSV format. A line consists in the group name, '
      'file path, number of functions matching the group, the total score '
      'and distance values.')
  parser.add_argument(
      '-f',
      '--cwp_function_statistics_file',
      required=True,
      help='The output file that will contain the metric statistics for the '
      'CWP functions in CSV format. A line consists in the function name, file '
      'name, cummulative distance, average distance, cummulative score and '
      'average score values.')

  options = parser.parse_args(arguments)
  return options


def Main(argv):
  options = ParseArguments(argv)
  metrics_experiment = MetricsExperiment(
      options.cwp_pairwise_inclusive_reference,
      options.cwp_pairwise_inclusive_test, options.cwp_inclusive_reference,
      options.cwp_inclusive_test, options.cwp_function_groups_file,
      options.cwp_function_groups_statistics_file,
      options.cwp_function_statistics_file)
  metrics_experiment.PerformComputation()


if __name__ == '__main__':
  Main(sys.argv[1:])
