#!/usr/bin/python2

# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Selects the optimal set of benchmarks.

For each benchmark, there is a file with the common functions, as extracted by
the process_hot_functions module.

The script receives as input the CSV file with the CWP inclusive count values,
the file with Chrome OS groups and the path containing a file with common
functions for every benchmark.

It extracts for every benchmark and for the CWP data all the functions that
match the given Chrome OS groups.

It generates all possible combinations of benchmark sets of a given size and
it computes for every set a metric.
It outputs the optimal sets, based on which ones have the best metric.

Three different metrics have been used: function count, distance
variation and score.

For the function count metric, we count the unique functions covered by a
set of benchmarks. Besides the number of unique functions, we compute also
the fraction of unique functions out of the amount of CWP functions from the
given groups. The benchmark set with the highest amount of unique functions
that belong to all the given groups is considered better.

For the distance variation metric, we compute the sum of the distance variations
of the functions covered by a set of benchmarks. We define the distance
variation as the difference between the distance value of a function and the
ideal distance value (1.0). If a function appears in multiple common functions
files, we consider only the minimum value. We compute also the distance
variation per function. The set that has the smaller value for the
distance variation per function is considered better.

For the score metric, we compute the sum of the scores of the functions from a
set of benchmarks. If a function appears in multiple common functions files,
we consider only the maximum value. We compute also the fraction of this sum
from the sum of all the scores of the functions from the CWP data covering the
given groups, in the ideal case (the ideal score of a function is 1.0).

We compute the metrics in the same manner for individual Chrome OS groups.
"""

from collections import defaultdict

import argparse
import csv
import itertools
import json
import operator
import os
import sys

import benchmark_metrics
import utils


class BenchmarkSet(object):
  """Selects the optimal set of benchmarks of given size."""

  # Constants that specify the metric type.
  FUNCTION_COUNT_METRIC = 'function_count'
  DISTANCE_METRIC = 'distance_variation'
  SCORE_METRIC = 'score_fraction'

  def __init__(self, benchmark_set_size, benchmark_set_output_file,
               benchmark_set_common_functions_path, cwp_inclusive_count_file,
               cwp_function_groups_file, metric):
    """Initializes the BenchmarkSet.

    Args:
      benchmark_set_size: Constant representing the size of a benchmark set.
      benchmark_set_output_file: The output file that will contain the set of
        optimal benchmarks with the metric values.
      benchmark_set_common_functions_path: The directory containing the files
        with the common functions for the list of benchmarks.
      cwp_inclusive_count_file: The CSV file containing the CWP functions with
        their inclusive count values.
      cwp_function_groups_file: The file that contains the CWP function groups.
      metric: The type of metric used for the analysis.
    """
    self._benchmark_set_size = int(benchmark_set_size)
    self._benchmark_set_output_file = benchmark_set_output_file
    self._benchmark_set_common_functions_path = \
        benchmark_set_common_functions_path
    self._cwp_inclusive_count_file = cwp_inclusive_count_file
    self._cwp_function_groups_file = cwp_function_groups_file
    self._metric = metric

  @staticmethod
  def OrganizeCWPFunctionsInGroups(cwp_inclusive_count_statistics,
                                   cwp_function_groups):
    """Selects the CWP functions that match the given Chrome OS groups.

    Args:
      cwp_inclusive_count_statistics: A dict with the CWP functions.
      cwp_function_groups: A list with the CWP function groups.

    Returns:
      A dict having as a key the name of the groups and as a value the list of
      CWP functions that match an individual group.
    """
    cwp_functions_grouped = defaultdict(list)
    for function_key in cwp_inclusive_count_statistics:
      _, file_name = function_key.split(',')
      for group_name, file_path in cwp_function_groups:
        if file_path not in file_name:
          continue
        cwp_functions_grouped[group_name].append(function_key)
        break
    return cwp_functions_grouped

  @staticmethod
  def OrganizeBenchmarkSetFunctionsInGroups(benchmark_set_files,
                                            benchmark_set_common_functions_path,
                                            cwp_function_groups):
    """Selects the benchmark functions that match the given Chrome OS groups.

    Args:
      benchmark_set_files: The list of common functions files corresponding to a
        benchmark.
      benchmark_set_common_functions_path: The directory containing the files
        with the common functions for the list of benchmarks.
      cwp_function_groups: A list with the CWP function groups.

    Returns:
      A dict having as a key the name of a common functions file. The value is
      a dict having as a key the name of a group and as value a list of
      functions that match the given group.
    """

    benchmark_set_functions_grouped = {}
    for benchmark_file_name in benchmark_set_files:
      benchmark_full_file_path = \
          os.path.join(benchmark_set_common_functions_path,
                       benchmark_file_name)
      with open(benchmark_full_file_path) as input_file:
        statistics_reader = \
            csv.DictReader(input_file, delimiter=',')
        benchmark_functions_grouped = defaultdict(dict)
        for statistic in statistics_reader:
          function_name = statistic['function']
          file_name = statistic['file']
          for group_name, file_path in cwp_function_groups:
            if file_path not in file_name:
              continue
            function_key = ','.join([function_name, file_name])
            distance = float(statistic['distance'])
            score = float(statistic['score'])
            benchmark_functions_grouped[group_name][function_key] = \
                (distance, score)
            break
          benchmark_set_functions_grouped[benchmark_file_name] = \
              benchmark_functions_grouped
    return benchmark_set_functions_grouped

  @staticmethod
  def SelectOptimalBenchmarkSetBasedOnMetric(all_benchmark_combinations_sets,
                                             benchmark_set_functions_grouped,
                                             cwp_functions_grouped,
                                             metric_function_for_set,
                                             metric_comparison_operator,
                                             metric_default_value,
                                             metric_string):
    """Generic method that selects the optimal benchmark set based on a metric.

    The reason of implementing a generic function is to avoid logic duplication
    for selecting a benchmark set based on the three different metrics.

    Args:
      all_benchmark_combinations_sets: The list with all the sets of benchmark
        combinations.
      benchmark_set_functions_grouped: A dict with benchmark functions as
        returned by OrganizeBenchmarkSetFunctionsInGroups.
      cwp_functions_grouped: A dict with the CWP functions as returned by
        OrganizeCWPFunctionsInGroups.
      metric_function_for_set: The method used to compute the metric for a given
        benchmark set.
      metric_comparison_operator: A comparison operator used to compare two
        values of the same metric (i.e: operator.lt or operator.gt).
      metric_default_value: The default value for the metric.
      metric_string: A tuple of strings used in the JSON output for the pair of
        the values of the metric.

    Returns:
      A list of tuples containing for each optimal benchmark set. A tuple
      contains the list of benchmarks from the set, the pair of metric values
      and a dictionary with the metrics for each group.
    """
    optimal_sets = [([], metric_default_value, {})]

    for benchmark_combination_set in all_benchmark_combinations_sets:
      function_metrics = [benchmark_set_functions_grouped[benchmark]
                          for benchmark in benchmark_combination_set]
      set_metrics, set_groups_metrics = \
          metric_function_for_set(function_metrics, cwp_functions_grouped,
                                  metric_string)
      optimal_value = optimal_sets[0][1][0]
      if metric_comparison_operator(set_metrics[0], optimal_value):
        optimal_sets = \
            [(benchmark_combination_set, set_metrics, set_groups_metrics)]
      elif set_metrics[0] == optimal_sets[0][1][0]:
        optimal_sets.append(
            (benchmark_combination_set, set_metrics, set_groups_metrics))

    return optimal_sets

  def SelectOptimalBenchmarkSet(self):
    """Selects the optimal benchmark sets and writes them in JSON format.

    Parses the CWP inclusive count statistics and benchmark common functions
    files. Organizes the functions into groups. For every optimal benchmark
    set, the method writes in the self._benchmark_set_output_file the list of
    benchmarks, the pair of metrics and a dictionary with the pair of
    metrics for each group covered by the benchmark set.
    """

    benchmark_set_files = os.listdir(self._benchmark_set_common_functions_path)
    all_benchmark_combinations_sets = \
        itertools.combinations(benchmark_set_files, self._benchmark_set_size)

    with open(self._cwp_function_groups_file) as input_file:
      cwp_function_groups = utils.ParseFunctionGroups(input_file.readlines())

    cwp_inclusive_count_statistics = \
        utils.ParseCWPInclusiveCountFile(self._cwp_inclusive_count_file)
    cwp_functions_grouped = self.OrganizeCWPFunctionsInGroups(
        cwp_inclusive_count_statistics, cwp_function_groups)
    benchmark_set_functions_grouped = \
        self.OrganizeBenchmarkSetFunctionsInGroups(
            benchmark_set_files, self._benchmark_set_common_functions_path,
            cwp_function_groups)

    if self._metric == self.FUNCTION_COUNT_METRIC:
      metric_function_for_benchmark_set = \
          benchmark_metrics.ComputeFunctionCountForBenchmarkSet
      metric_comparison_operator = operator.gt
      metric_default_value = (0, 0.0)
      metric_string = ('function_count', 'function_count_fraction')
    elif self._metric == self.DISTANCE_METRIC:
      metric_function_for_benchmark_set = \
          benchmark_metrics.ComputeDistanceForBenchmarkSet
      metric_comparison_operator = operator.lt
      metric_default_value = (float('inf'), float('inf'))
      metric_string = \
          ('distance_variation_per_function', 'total_distance_variation')
    elif self._metric == self.SCORE_METRIC:
      metric_function_for_benchmark_set = \
          benchmark_metrics.ComputeScoreForBenchmarkSet
      metric_comparison_operator = operator.gt
      metric_default_value = (0.0, 0.0)
      metric_string = ('score_fraction', 'total_score')
    else:
      raise ValueError("Invalid metric")

    optimal_benchmark_sets = \
        self.SelectOptimalBenchmarkSetBasedOnMetric(
            all_benchmark_combinations_sets, benchmark_set_functions_grouped,
            cwp_functions_grouped, metric_function_for_benchmark_set,
            metric_comparison_operator, metric_default_value, metric_string)

    json_output = []

    for benchmark_set in optimal_benchmark_sets:
      json_entry = {
          'benchmark_set':
              list(benchmark_set[0]),
          'metrics': {
              metric_string[0]: benchmark_set[1][0],
              metric_string[1]: benchmark_set[1][1]
          },
          'groups':
              dict(benchmark_set[2])
      }
      json_output.append(json_entry)

    with open(self._benchmark_set_output_file, 'w') as output_file:
      json.dump(json_output, output_file)


def ParseArguments(arguments):
  parser = argparse.ArgumentParser()

  parser.add_argument(
      '--benchmark_set_common_functions_path',
      required=True,
      help='The directory containing the CSV files with the common functions '
      'of the benchmark profiles and CWP data. A file will contain all the hot '
      'functions from a pprof top output file that are also included in the '
      'file containing the cwp inclusive count values. The CSV fields are: the '
      'function name, the file and the object where the function is declared, '
      'the CWP inclusive count and inclusive count fraction values, the '
      'cumulative and average distance, the cumulative and average score. The '
      'files with the common functions will have the same names with the '
      'corresponding pprof output files.')
  parser.add_argument(
      '--cwp_inclusive_count_file',
      required=True,
      help='The CSV file containing the CWP hot functions with their '
      'inclusive_count values. The CSV fields include the name of the '
      'function, the file and the object with the definition, the inclusive '
      'count value and the inclusive count fraction out of the total amount of '
      'inclusive count values.')
  parser.add_argument(
      '--benchmark_set_size',
      required=True,
      help='The size of the benchmark sets.')
  parser.add_argument(
      '--benchmark_set_output_file',
      required=True,
      help='The JSON output file containing optimal benchmark sets with their '
      'metrics. For every optimal benchmark set, the file contains the list of '
      'benchmarks, the pair of metrics and a dictionary with the pair of '
      'metrics for each group covered by the benchmark set.')
  parser.add_argument(
      '--metric',
      required=True,
      help='The metric used to select the optimal benchmark set. The possible '
      'values are: distance_variation, function_count and score_fraction.')
  parser.add_argument(
      '--cwp_function_groups_file',
      required=True,
      help='The file that contains the CWP function groups. A line consists in '
      'the group name and a file path describing the group. A group must '
      'represent a Chrome OS component.')

  options = parser.parse_args(arguments)

  return options


def Main(argv):
  options = ParseArguments(argv)
  benchmark_set = BenchmarkSet(options.benchmark_set_size,
                               options.benchmark_set_output_file,
                               options.benchmark_set_common_functions_path,
                               options.cwp_inclusive_count_file,
                               options.cwp_function_groups_file, options.metric)
  benchmark_set.SelectOptimalBenchmarkSet()


if __name__ == '__main__':
  Main(sys.argv[1:])
