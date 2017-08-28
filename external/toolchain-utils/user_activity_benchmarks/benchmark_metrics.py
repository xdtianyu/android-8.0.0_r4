# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Computes the metrics for functions, Chrome OS components and benchmarks."""

from collections import defaultdict


def ComputeDistanceForFunction(child_functions_statistics_sample,
                               child_functions_statistics_reference):
  """Computes the distance metric for a function.

  Args:
    child_functions_statistics_sample: A dict that has as a key the name of a
      function and as a value the inclusive count fraction. The keys are
      the child functions of a sample parent function.
    child_functions_statistics_reference: A dict that has as a key the name of
      a function and as a value the inclusive count fraction. The keys are
      the child functions of a reference parent function.

  Returns:
    A float value representing the sum of inclusive count fraction
    differences of pairs of common child functions. If a child function is
    present in a single data set, then we consider the missing inclusive
    count fraction as 0. This value describes the difference in behaviour
    between a sample and the reference parent function.
  """
  # We initialize the distance with a small value to avoid the further
  # division by zero.
  distance = 1.0

  for child_function, inclusive_count_fraction_reference in \
      child_functions_statistics_reference.iteritems():
    inclusive_count_fraction_sample = 0.0

    if child_function in child_functions_statistics_sample:
      inclusive_count_fraction_sample = \
          child_functions_statistics_sample[child_function]
    distance += \
        abs(inclusive_count_fraction_sample -
            inclusive_count_fraction_reference)

  for child_function, inclusive_count_fraction_sample in \
      child_functions_statistics_sample.iteritems():
    if child_function not in child_functions_statistics_reference:
      distance += inclusive_count_fraction_sample

  return distance


def ComputeScoreForFunction(distance, reference_fraction, sample_fraction):
  """Computes the score for a function.

  Args:
    distance: A float value representing the difference in behaviour between
      the sample and the reference function.
    reference_fraction: A float value representing the inclusive count
      fraction of the reference function.
    sample_fraction: A float value representing the inclusive count
      fraction of the sample function.

  Returns:
    A float value representing the score of the function.
  """
  return reference_fraction * sample_fraction / distance


def ComputeMetricsForComponents(cwp_function_groups, function_metrics):
  """Computes the metrics for a set of Chrome OS components.

  For every Chrome OS group, we compute the number of functions matching the
  group, the cumulative and average score, the cumulative and average distance
  of all those functions. A function matches a group if the path of the file
  containing its definition contains the common path describing the group.

  Args:
    cwp_function_groups: A dict having as a key the name of the group and as a
      value a common path describing the group.
    function_metrics: A dict having as a key the name of the function and the
      name of the file where it is declared concatenated by a ',', and as a
      value a tuple containing the distance and the score metrics.

  Returns:
    A dict containing as a key the name of the group and as a value a tuple
    with the group file path, the number of functions matching the group,
    the cumulative and average score, cumulative and average distance of all
    those functions.
  """
  function_groups_metrics = defaultdict(lambda: (0, 0.0, 0.0, 0.0, 0.0))

  for function_key, metric in function_metrics.iteritems():
    _, function_file = function_key.split(',')

    for group, common_path in cwp_function_groups:
      if common_path not in function_file:
        continue

      function_distance = metric[0]
      function_score = metric[1]
      group_statistic = function_groups_metrics[group]

      function_count = group_statistic[1] + 1
      function_distance_cum = function_distance + group_statistic[2]
      function_distance_avg = function_distance_cum / float(function_count)
      function_score_cum = function_score + group_statistic[4]
      function_score_avg = function_score_cum / float(function_count)

      function_groups_metrics[group] = \
          (common_path,
           function_count,
           function_distance_cum,
           function_distance_avg,
           function_score_cum,
           function_score_avg)
      break

  return function_groups_metrics


def ComputeMetricsForBenchmark(function_metrics):
  function_count = len(function_metrics.keys())
  distance_cum = 0.0
  distance_avg = 0.0
  score_cum = 0.0
  score_avg = 0.0

  for distance, score in function_metrics.values():
    distance_cum += distance
    score_cum += score

  distance_avg = distance_cum / float(function_count)
  score_avg = score_cum / float(function_count)
  return function_count, distance_cum, distance_avg, score_cum, score_avg


def ComputeFunctionCountForBenchmarkSet(set_function_metrics, cwp_functions,
                                        metric_string):
  """Computes the function count metric pair for the benchmark set.

     For the function count metric, we count the unique functions covered by the
     set of benchmarks. We compute the fraction of unique functions out
     of the amount of CWP functions given.

     We compute also the same metric pair for every group from the keys of the
     set_function_metrics dict.

  Args:
    set_function_metrics: A list of dicts having as a key the name of a group
      and as value a list of functions that match the given group.
    cwp_functions: A dict having as a key the name of the groups and as a value
      the list of CWP functions that match an individual group.
    metric_string: A tuple of strings that will be mapped to the tuple of metric
      values in the returned function group dict. This is done for convenience
      for the JSON output.

  Returns:
    A tuple with the metric pair and a dict with the group names and values
    of the metric pair. The first value of the metric pair represents the
    function count and the second value the function count fraction.
    The dict has as a key the name of the group and as a value a dict that
    maps the metric_string  to the values of the metric pair of the group.
  """
  cwp_functions_count = sum(len(functions)
                            for functions in cwp_functions.itervalues())
  set_groups_functions = defaultdict(set)
  for benchmark_function_metrics in set_function_metrics:
    for group_name in benchmark_function_metrics:
      set_groups_functions[group_name] |= \
          set(benchmark_function_metrics[group_name])

  set_groups_functions_count = {}
  set_functions_count = 0
  for group_name, functions \
      in set_groups_functions.iteritems():
    set_group_functions_count = len(functions)
    if group_name in cwp_functions:
      set_groups_functions_count[group_name] = {
          metric_string[0]: set_group_functions_count,
          metric_string[1]:
          set_group_functions_count / float(len(cwp_functions[group_name]))}
    else:
      set_groups_functions_count[group_name] = \
          {metric_string[0]: set_group_functions_count, metric_string[1]: 0.0}
    set_functions_count += set_group_functions_count

  set_functions_count_fraction = \
      set_functions_count / float(cwp_functions_count)
  return (set_functions_count, set_functions_count_fraction), \
      set_groups_functions_count


def ComputeDistanceForBenchmarkSet(set_function_metrics, cwp_functions,
                                   metric_string):
  """Computes the distance variation metric pair for the benchmark set.

     For the distance variation metric, we compute the sum of the distance
     variations of the functions covered by a set of benchmarks.
     We define the distance variation as the difference between the distance
     value of a functions and the ideal distance value (1.0).
     If a function appears in multiple common functions files, we consider
     only the minimum value. We compute also the distance variation per
     function.

     In addition, we compute also the same metric pair for every group from
     the keys of the set_function_metrics dict.

  Args:
    set_function_metrics: A list of dicts having as a key the name of a group
      and as value a list of functions that match the given group.
    cwp_functions: A dict having as a key the name of the groups and as a value
      the list of CWP functions that match an individual group.
    metric_string: A tuple of strings that will be mapped to the tuple of metric
      values in the returned function group dict. This is done for convenience
      for the JSON output.

  Returns:
    A tuple with the metric pair and a dict with the group names and values
    of the metric pair. The first value of the metric pair represents the
    distance variation per function and the second value the distance variation.
    The dict has as a key the name of the group and as a value a dict that
    maps the metric_string to the values of the metric pair of the group.
  """
  set_unique_functions = defaultdict(lambda: defaultdict(lambda: float('inf')))
  set_function_count = 0
  total_distance_variation = 0.0
  for benchmark_function_metrics in set_function_metrics:
    for group_name in benchmark_function_metrics:
      for function_key, metrics in \
          benchmark_function_metrics[group_name].iteritems():
        previous_distance = \
            set_unique_functions[group_name][function_key]
        min_distance = min(metrics[0], previous_distance)
        set_unique_functions[group_name][function_key] = min_distance
  groups_distance_variations = defaultdict(lambda: (0.0, 0.0))
  for group_name, functions_distances in set_unique_functions.iteritems():
    group_function_count = len(functions_distances)
    group_distance_variation = \
        sum(functions_distances.itervalues()) - float(group_function_count)
    total_distance_variation += group_distance_variation
    set_function_count += group_function_count
    groups_distance_variations[group_name] = \
        {metric_string[0]:
         group_distance_variation / float(group_function_count),
         metric_string[1]: group_distance_variation}

  return (total_distance_variation / set_function_count,
          total_distance_variation), groups_distance_variations


def ComputeScoreForBenchmarkSet(set_function_metrics, cwp_functions,
                                metric_string):
  """Computes the function count metric pair for the benchmark set.

     For the score metric, we compute the sum of the scores of the functions
     from a set of benchmarks. If a function appears in multiple common
     functions files, we consider only the maximum value. We compute also the
     fraction of this sum from the sum of all the scores of the functions from
     the CWP data covering the given groups, in the ideal case (the ideal
     score of a function is 1.0).

     In addition, we compute the same metric pair for every group from the
     keys of the set_function_metrics dict.

  Args:
    set_function_metrics: A list of dicts having as a key the name of a group
      and as value a list of functions that match the given group.
    cwp_functions: A dict having as a key the name of the groups and as a value
      the list of CWP functions that match an individual group.
    metric_string: A tuple of strings that will be mapped to the tuple of metric
      values in the returned function group dict. This is done for convenience
      for the JSON output.

  Returns:
    A tuple with the metric pair and a dict with the group names and values
    of the metric pair. The first value of the pair is the fraction of the sum
    of the scores from the ideal case and the second value represents the
    sum of scores of the functions. The dict has as a key the name of the group
    and as a value a dict that maps the metric_string to the values of the
    metric pair of the group.
  """
  cwp_functions_count = sum(len(functions)
                            for functions in cwp_functions.itervalues())
  set_unique_functions = defaultdict(lambda: defaultdict(lambda: 0.0))
  total_score = 0.0

  for benchmark_function_metrics in set_function_metrics:
    for group_name in benchmark_function_metrics:
      for function_key, metrics in \
          benchmark_function_metrics[group_name].iteritems():
        previous_score = \
            set_unique_functions[group_name][function_key]
        max_score = max(metrics[1], previous_score)
        set_unique_functions[group_name][function_key] = max_score

  groups_scores = defaultdict(lambda: (0.0, 0.0))

  for group_name, function_scores in set_unique_functions.iteritems():
    group_function_count = float(len(cwp_functions[group_name]))
    group_score = sum(function_scores.itervalues())
    total_score += group_score
    groups_scores[group_name] = {
        metric_string[0]: group_score / group_function_count,
        metric_string[1]: group_score
    }

  return (total_score / cwp_functions_count, total_score), groups_scores
