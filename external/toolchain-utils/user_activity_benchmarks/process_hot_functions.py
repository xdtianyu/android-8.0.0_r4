#!/usr/bin/python2

# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Processes the functions from the pprof(go/pprof) files and CWP(go/cwp) data.

The pprof --top and pprof --tree outputs should be extracted from the benchmark
profiles. The outputs contain the hot functions and the call chains.

For each pair of pprof --top and --tree output files, the tool will create a
file that contains the hot functions present also in the extracted CWP data.
The common functions are organized in groups that represent a Chrome OS
component. A function belongs to a group that is defined by a given file path
if it is declared in a file that shares that path.

A set of metrics are computed for each function, benchmark and Chrome OS group
covered by a benchmark.

Afterwards, this script extracts the functions that are present in the CWP
data and not in the benchmark profiles. The extra functions are also groupped
in Chrome OS components.
"""

from collections import defaultdict

import argparse
import os
import shutil
import sys

import benchmark_metrics
import utils


class HotFunctionsProcessor(object):
  """Does the pprof and CWP output processing.

  Extracts the common, extra functions from the pprof files, groups them in
  Chrome OS components. Computes the metrics for the common functions,
  benchmark and Chrome OS groups covered by a benchmark.
  """

  def __init__(self, pprof_top_path, pprof_tree_path, cwp_inclusive_count_file,
               cwp_pairwise_inclusive_count_file, cwp_function_groups_file,
               common_functions_path, common_functions_groups_path,
               benchmark_set_metrics_file, extra_cwp_functions_file,
               extra_cwp_functions_groups_file,
               extra_cwp_functions_groups_path):
    """Initializes the HotFunctionsProcessor.

    Args:
      pprof_top_path: The directory containing the files with the pprof --top
        output.
      pprof_tree_path: The directory containing the files with the pprof --tree
        output.
      cwp_inclusive_count_file: The CSV file containing the CWP functions with
        the inclusive count values.
      cwp_pairwise_inclusive_count_file: The CSV file containing the CWP pairs
        of parent and child functions with their inclusive count values.
      cwp_function_groups_file: The file that contains the CWP function groups.
      common_functions_path: The directory containing the CSV output files
        with the common functions of the benchmark profiles and CWP data.
      common_functions_groups_path: The directory containing the CSV output
        files with the CWP groups and their metrics that match the common
        functions of the benchmark profiles and CWP.
      benchmark_set_metrics_file: The CSV output file containing the metrics for
        each benchmark.
      extra_cwp_functions_file: The CSV output file containing the functions
        that are in the CWP data, but are not in any of the benchmark profiles.
      extra_cwp_functions_groups_file: The CSV output file containing the groups
        that match the extra CWP functions and their statistics.
      extra_cwp_functions_groups_path: The directory containing the CSV output
        files with the extra CWP functions that match a particular group.
    """
    self._pprof_top_path = pprof_top_path
    self._pprof_tree_path = pprof_tree_path
    self._cwp_inclusive_count_file = cwp_inclusive_count_file
    self._cwp_pairwise_inclusive_count_file = cwp_pairwise_inclusive_count_file
    self._cwp_function_groups_file = cwp_function_groups_file
    self._common_functions_path = common_functions_path
    self._common_functions_groups_path = common_functions_groups_path
    self._benchmark_set_metrics_file = benchmark_set_metrics_file
    self._extra_cwp_functions_file = extra_cwp_functions_file
    self._extra_cwp_functions_groups_file = extra_cwp_functions_groups_file
    self._extra_cwp_functions_groups_path = extra_cwp_functions_groups_path

  def ProcessHotFunctions(self):
    """Does the processing of the hot functions."""
    with open(self._cwp_function_groups_file) as input_file:
      cwp_function_groups = utils.ParseFunctionGroups(input_file.readlines())
    cwp_statistics = \
      self.ExtractCommonFunctions(self._pprof_top_path,
                                  self._pprof_tree_path,
                                  self._cwp_inclusive_count_file,
                                  self._cwp_pairwise_inclusive_count_file,
                                  cwp_function_groups,
                                  self._common_functions_path,
                                  self._common_functions_groups_path,
                                  self._benchmark_set_metrics_file)
    self.ExtractExtraFunctions(cwp_statistics, self._extra_cwp_functions_file)
    self.GroupExtraFunctions(cwp_statistics, cwp_function_groups,
                             self._extra_cwp_functions_groups_path,
                             self._extra_cwp_functions_groups_file)

  def ExtractCommonFunctions(self, pprof_top_path, pprof_tree_path,
                             cwp_inclusive_count_file,
                             cwp_pairwise_inclusive_count_file,
                             cwp_function_groups, common_functions_path,
                             common_functions_groups_path,
                             benchmark_set_metrics_file):
    """Extracts the common functions of the benchmark profiles and the CWP data.

    For each pair of pprof --top and --tree output files, it creates a separate
    file with the same name containing the common functions specifications and
    metrics, that will be placed in the common_functions_path directory.

    The resulting file is in CSV format, containing the following fields:
    function name, file name, object, inclusive count, inclusive_count_fraction,
    flat, flat%, sum%, cum, cum%, distance and score.

    For each pair of pprof files, an additional file is created with the
    Chrome OS groups that match the common functions.

    The file is in CSV format containing the fields: group name, group path,
    the number of functions that match the group, the average and cumulative
    distance, the average and cumulative score.
    The file has the same name with the pprof file and it is placed in the
    common_functions_groups_path directory.

    For all the analyzed benchmarks, the method creates a CSV output file
    containing the metrics for each benchmark. The CSV fields include the
    benchmark name, the number of common functions, the average and
    cumulative distance and score.

    It builds a dict of the CWP statistics by calling the
    utils.ParseCWPInclusiveCountFile method and if a function is common, it is
    marked as a COMMON_FUNCTION.

    Args:
      pprof_top_path: The name of the directory with the files with the
        pprof --top output.
      pprof_tree_path: The name of the directory with the files with the
        pprof --tree output.
      cwp_inclusive_count_file: A dict with the inclusive count values.
      cwp_pairwise_inclusive_count_file: A dict with the pairwise inclusive
        count values.
      cwp_function_groups: A list of tuples containing the name of the group
        and the corresponding file path.
      common_functions_path: The path containing the output files with the
        common functions and their metrics.
      common_functions_groups_path: The path containing the output files with
        the Chrome OS groups that match the common functions and their metrics.
      benchmark_set_metrics_file: The CSV output file containing the metrics for
        all the analyzed benchmarks.

    Returns:
      A dict containing the CWP statistics with the common functions marked as
      COMMON_FUNCTION.
    """
    cwp_inclusive_count_statistics = \
        utils.ParseCWPInclusiveCountFile(cwp_inclusive_count_file)
    cwp_pairwise_inclusive_count_statistics = \
        utils.ParseCWPPairwiseInclusiveCountFile(
            cwp_pairwise_inclusive_count_file)
    cwp_inclusive_count_statistics_cumulative = \
        utils.ComputeCWPCummulativeInclusiveStatistics(
            cwp_inclusive_count_statistics)
    cwp_pairwise_inclusive_count_fractions = \
        utils.ComputeCWPChildFunctionsFractions(
            cwp_inclusive_count_statistics_cumulative,
            cwp_pairwise_inclusive_count_statistics)
    benchmark_set_metrics = {}
    pprof_files = os.listdir(pprof_top_path)

    for pprof_file in pprof_files:
      pprof_top_statistics = \
          utils.ParsePprofTopOutput(os.path.join(pprof_top_path, pprof_file))
      pprof_tree_statistics = \
          utils.ParsePprofTreeOutput(os.path.join(pprof_tree_path, pprof_file))
      common_functions_lines = []
      benchmark_function_metrics = {}

      for function_key, function_statistic in pprof_top_statistics.iteritems():
        if function_key not in cwp_inclusive_count_statistics:
          continue

        cwp_dso_name, cwp_inclusive_count, cwp_inclusive_count_fraction, _ = \
            cwp_inclusive_count_statistics[function_key]
        cwp_inclusive_count_statistics[function_key] = \
            (cwp_dso_name, cwp_inclusive_count, cwp_inclusive_count_fraction,
             utils.COMMON_FUNCTION)

        function_name, _ = function_key.split(',')
        distance = benchmark_metrics.ComputeDistanceForFunction(
            pprof_tree_statistics[function_key],
            cwp_pairwise_inclusive_count_fractions.get(function_name, {}))
        benchmark_cum_p = float(function_statistic[4])
        score = benchmark_metrics.ComputeScoreForFunction(
            distance, cwp_inclusive_count_fraction, benchmark_cum_p)
        benchmark_function_metrics[function_key] = (distance, score)

        common_functions_lines.append(','.join([function_key, cwp_dso_name, str(
            cwp_inclusive_count), str(cwp_inclusive_count_fraction), ','.join(
                function_statistic), str(distance), str(score)]))
      benchmark_function_groups_statistics = \
          benchmark_metrics.ComputeMetricsForComponents(
              cwp_function_groups, benchmark_function_metrics)
      benchmark_set_metrics[pprof_file] = \
          benchmark_metrics.ComputeMetricsForBenchmark(
              benchmark_function_metrics)

      with open(os.path.join(common_functions_path, pprof_file), 'w') \
          as output_file:
        common_functions_lines.sort(
            key=lambda x: float(x.split(',')[11]), reverse=True)
        common_functions_lines.insert(0, 'function,file,dso,inclusive_count,'
                                      'inclusive_count_fraction,flat,flat%,'
                                      'sum%,cum,cum%,distance,score')
        output_file.write('\n'.join(common_functions_lines))

      with open(os.path.join(common_functions_groups_path, pprof_file), 'w') \
          as output_file:
        common_functions_groups_lines = \
            [','.join([group_name, ','.join(
                [str(statistic) for statistic in group_statistic])])
             for group_name, group_statistic in
             benchmark_function_groups_statistics.iteritems()]
        common_functions_groups_lines.sort(
            key=lambda x: float(x.split(',')[5]), reverse=True)
        common_functions_groups_lines.insert(
            0, 'group_name,file_path,number_of_functions,distance_cum,'
            'distance_avg,score_cum,score_avg')
        output_file.write('\n'.join(common_functions_groups_lines))

    with open(benchmark_set_metrics_file, 'w') as output_file:
      benchmark_set_metrics_lines = []

      for benchmark_name, metrics in benchmark_set_metrics.iteritems():
        benchmark_set_metrics_lines.append(','.join([benchmark_name, ','.join(
            [str(metric) for metric in metrics])]))
      benchmark_set_metrics_lines.sort(
          key=lambda x: float(x.split(',')[4]), reverse=True)
      benchmark_set_metrics_lines.insert(
          0, 'benchmark_name,number_of_functions,distance_cum,distance_avg,'
          'score_cum,score_avg')
      output_file.write('\n'.join(benchmark_set_metrics_lines))

    return cwp_inclusive_count_statistics

  def GroupExtraFunctions(self, cwp_statistics, cwp_function_groups,
                          extra_cwp_functions_groups_path,
                          extra_cwp_functions_groups_file):
    """Groups the extra functions.

    Writes the data of the functions that belong to each group in a separate
    file, sorted by their inclusive count value, in descending order. The file
    name is the same as the group name.

    The file is in CSV format, containing the fields: function name, file name,
    object name, inclusive count, inclusive count fraction.

    It creates a CSV file containing the name of the group, their
    common path, the total inclusive count and inclusive count fraction values
    of all the functions declared in files that share the common path, sorted
    in descending order by the inclusive count value.

    Args:
      cwp_statistics: A dict containing the CWP statistics.
      cwp_function_groups: A list of tuples with the groups names and the path
        describing the groups.
      extra_cwp_functions_groups_path: The name of the directory containing
        the CSV output files with the extra CWP functions that match a
        particular group.
      extra_cwp_functions_groups_file: The CSV output file containing the groups
        that match the extra functions and their statistics.
    """
    cwp_function_groups_statistics = defaultdict(lambda: ([], '', 0, 0.0))
    for function, statistics in cwp_statistics.iteritems():
      if statistics[3] == utils.COMMON_FUNCTION:
        continue

      file_name = function.split(',')[1]
      group_inclusive_count = int(statistics[1])
      group_inclusive_count_fraction = float(statistics[2])

      for group in cwp_function_groups:
        group_common_path = group[1]

        if group_common_path not in file_name:
          continue

        group_name = group[0]
        group_statistics = cwp_function_groups_statistics[group_name]
        group_lines = group_statistics[0]
        group_inclusive_count += group_statistics[2]
        group_inclusive_count_fraction += group_statistics[3]

        group_lines.append(','.join([function, statistics[0],
                                     str(statistics[1]), str(statistics[2])]))
        cwp_function_groups_statistics[group_name] = \
            (group_lines, group_common_path, group_inclusive_count,
             group_inclusive_count_fraction)
        break

    extra_cwp_functions_groups_lines = []
    for group_name, group_statistics \
        in cwp_function_groups_statistics.iteritems():
      group_output_lines = group_statistics[0]
      group_output_lines.sort(key=lambda x: int(x.split(',')[3]), reverse=True)
      group_output_lines.insert(
          0, 'function,file,dso,inclusive_count,inclusive_count_fraction')
      with open(os.path.join(extra_cwp_functions_groups_path, group_name),
                'w') as output_file:
        output_file.write('\n'.join(group_output_lines))
      extra_cwp_functions_groups_lines.append(','.join(
          [group_name, group_statistics[1], str(group_statistics[2]), str(
              group_statistics[3])]))

    extra_cwp_functions_groups_lines.sort(
        key=lambda x: int(x.split(',')[2]), reverse=True)
    extra_cwp_functions_groups_lines.insert(
        0, 'group,shared_path,inclusive_count,inclusive_count_fraction')
    with open(extra_cwp_functions_groups_file, 'w') as output_file:
      output_file.write('\n'.join(extra_cwp_functions_groups_lines))

  def ExtractExtraFunctions(self, cwp_statistics, extra_cwp_functions_file):
    """Gets the functions that are in the CWP data, but not in the pprof output.

    Writes the functions and their statistics in the extra_cwp_functions_file
    file. The output is sorted based on the inclusive_count value. The file is
    in CSV format, containing the fields: function name, file name, object name,
    inclusive count and inclusive count fraction.

    Args:
      cwp_statistics: A dict containing the CWP statistics indexed by the
        function and the file name, comma separated.
      extra_cwp_functions_file: The file where it should be stored the CWP
        functions and statistics that are marked as EXTRA_FUNCTION.
    """
    output_lines = []

    for function, statistics in cwp_statistics.iteritems():
      if statistics[3] == utils.EXTRA_FUNCTION:
        output_lines.append(','.join([function, statistics[0],
                                      str(statistics[1]), str(statistics[2])]))

    with open(extra_cwp_functions_file, 'w') as output_file:
      output_lines.sort(key=lambda x: int(x.split(',')[3]), reverse=True)
      output_lines.insert(0, 'function,file,dso,inclusive_count,'
                          'inclusive_count_fraction')
      output_file.write('\n'.join(output_lines))


def ParseArguments(arguments):
  parser = argparse.ArgumentParser()

  parser.add_argument(
      '--pprof_top_path',
      required=True,
      help='The directory containing the files with the pprof --top output of '
      'the benchmark profiles (the hot functions). The name of the files '
      'should match with the ones from the pprof tree output files.')
  parser.add_argument(
      '--pprof_tree_path',
      required=True,
      help='The directory containing the files with the pprof --tree output '
      'of the benchmark profiles (the call chains). The name of the files '
      'should match with the ones of the pprof top output files.')
  parser.add_argument(
      '--cwp_inclusive_count_file',
      required=True,
      help='The CSV file containing the CWP hot functions with their '
      'inclusive_count values. The CSV fields include the name of the '
      'function, the file and the object with the definition, the inclusive '
      'count value and the inclusive count fraction out of the total amount of '
      'inclusive count values.')
  parser.add_argument(
      '--cwp_pairwise_inclusive_count_file',
      required=True,
      help='The CSV file containing the CWP pairs of parent and child '
      'functions with their inclusive count values. The CSV fields include the '
      'name of the parent and child functions concatenated by ;;, the file '
      'and the object with the definition of the child function, and the '
      'inclusive count value.')
  parser.add_argument(
      '--cwp_function_groups_file',
      required=True,
      help='The file that contains the CWP function groups. A line consists in '
      'the group name and a file path describing the group. A group must '
      'represent a ChromeOS component.')
  parser.add_argument(
      '--common_functions_path',
      required=True,
      help='The directory containing the CSV output files with the common '
      'functions of the benchmark profiles and CWP data. A file will contain '
      'all the hot functions from a pprof top output file that are also '
      'included in the file containing the cwp inclusive count values. The CSV '
      'fields are: the function name, the file and the object where the '
      'function is declared, the CWP inclusive count and inclusive count '
      'fraction values, the cumulative and average distance, the cumulative '
      'and average score. The files with the common functions will have the '
      'same names with the corresponding pprof output files.')
  parser.add_argument(
      '--common_functions_groups_path',
      required=True,
      help='The directory containing the CSV output files with the Chrome OS '
      'groups and their metrics that match the common functions of the '
      'benchmark profiles and CWP. The files with the groups will have the '
      'same names with the corresponding pprof output files. The CSV fields '
      'include the group name, group path, the number of functions that match '
      'the group, the average and cumulative distance, the average and '
      'cumulative score.')
  parser.add_argument(
      '--benchmark_set_metrics_file',
      required=True,
      help='The CSV output file containing the metrics for each benchmark. The '
      'CSV fields include the benchmark name, the number of common functions, '
      'the average and cumulative distance and score.')
  parser.add_argument(
      '--extra_cwp_functions_file',
      required=True,
      help='The CSV output file containing the functions that are in the CWP '
      'data, but are not in any of the benchmark profiles. The CSV fields '
      'include the name of the function, the file name and the object with the '
      'definition, and the CWP inclusive count and inclusive count fraction '
      'values. The entries are sorted in descending order based on the '
      'inclusive count value.')
  parser.add_argument(
      '--extra_cwp_functions_groups_file',
      required=True,
      help='The CSV output file containing the groups that match the extra CWP '
      'functions and their statistics. The CSV fields include the group name, '
      'the file path, the total inclusive count and inclusive count fraction '
      'values of the functions matching a particular group.')
  parser.add_argument(
      '--extra_cwp_functions_groups_path',
      required=True,
      help='The directory containing the CSV output files with the extra CWP '
      'functions that match a particular group. The name of the file is the '
      'same as the group name. The CSV fields include the name of the '
      'function, the file name and the object with the definition, and the CWP '
      'inclusive count and inclusive count fraction values. The entries are '
      'sorted in descending order based on the inclusive count value.')

  options = parser.parse_args(arguments)

  return options


def Main(argv):
  options = ParseArguments(argv)

  if os.path.exists(options.common_functions_path):
    shutil.rmtree(options.common_functions_path)

  os.makedirs(options.common_functions_path)

  if os.path.exists(options.common_functions_groups_path):
    shutil.rmtree(options.common_functions_groups_path)

  os.makedirs(options.common_functions_groups_path)

  if os.path.exists(options.extra_cwp_functions_groups_path):
    shutil.rmtree(options.extra_cwp_functions_groups_path)

  os.makedirs(options.extra_cwp_functions_groups_path)

  hot_functions_processor = HotFunctionsProcessor(
      options.pprof_top_path, options.pprof_tree_path,
      options.cwp_inclusive_count_file,
      options.cwp_pairwise_inclusive_count_file,
      options.cwp_function_groups_file, options.common_functions_path,
      options.common_functions_groups_path, options.benchmark_set_metrics_file,
      options.extra_cwp_functions_file, options.extra_cwp_functions_groups_file,
      options.extra_cwp_functions_groups_path)

  hot_functions_processor.ProcessHotFunctions()


if __name__ == '__main__':
  Main(sys.argv[1:])
