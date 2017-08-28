# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Utility functions for parsing pprof, CWP data and Chrome OS groups files."""

from collections import defaultdict

import csv
import os
import re

SEPARATOR_REGEX = re.compile(r'-+\+-+')
FUNCTION_STATISTIC_REGEX = \
    re.compile(r'(\S+)\s+(\S+)%\s+(\S+)%\s+(\S+)\s+(\S+)%')
CHILD_FUNCTION_PERCENTAGE_REGEX = re.compile(r'([0-9.]+)%')
FUNCTION_KEY_SEPARATOR_REGEX = re.compile(r'\|\s+')
# Constants used to identify if a function is common in the pprof and CWP
# files.
COMMON_FUNCTION = 'common'
EXTRA_FUNCTION = 'extra'
PARENT_CHILD_FUNCTIONS_SEPARATOR = ';;'
# List of pairs of strings used for make substitutions in file names to make
# CWP and pprof data consistent.
FILE_NAME_REPLACING_PAIR_STRINGS = [('gnawty', 'BOARD'),
                                    ('amd64-generic', 'BOARD'),
                                    (' ../sysdeps', ',sysdeps'),
                                    (' ../nptl', ',nptl'),
                                    ('  aes-x86_64.s', ',aes-x86_64.s'),
                                    (' (inline)', ''),
                                    (' (partial-inline)', ''),
                                    (' ../', ','),
                                    ('../', '')]
# Separator used to delimit the function from the file name.
FUNCTION_FILE_SEPARATOR = ' /'


def MakeCWPAndPprofFileNamesConsistent(file_name):
  """Makes the CWP and pprof file names consistent.

  For the same function, it may happen for some file paths to differ slightly
  in the CWP data compared to the pprof output. In a file name, for each tuple
  element of the list, we substitute the first element with the second one.

  Args:
    file_name: A string representing the name of the file.

  Returns:
    A string representing the modified name of tihe file.
  """
  file_name = file_name.replace(', ', '; ')
  for replacing_pair_string in FILE_NAME_REPLACING_PAIR_STRINGS:
    file_name = file_name.replace(replacing_pair_string[0],
                                  replacing_pair_string[1])

  return file_name

def MakePprofFunctionKey(function_and_file_name):
  """Creates the function key from the function and file name.

  Parsing the the pprof --top and --tree outputs is difficult due to the fact
  that it hard to extract the function and file name (i.e the function names
  can have a lot of unexpected charachters such as spaces, operators etc).
  For the moment, we used FUNCTION_FILE_SEPARATOR as delimiter between the
  function and the file name. However, there are some cases where the file name
  does not start with / and we treat this cases separately (i.e ../sysdeps,
  ../nptl, aes-x86_64.s).

  Args:
    function_and_file_name: A string representing the function and the file name
      as it appears in the pprof output.

  Returns:
    A string representing the function key, composed from the function and file
    name, comma separated.
  """
  # TODO(evelinad): Use pprof --topproto instead of pprof --top to parse
  # protobuffers instead of text output. Investigate if there is an equivalent
  # for pprof --tree that gives protobuffer output.
  #
  # In the CWP output, we replace the , with ; as a workaround for parsing
  # csv files. We do the same for the pprof output.
  #
  # TODO(evelinad): Use dremel --csv_dialect=excel-tab in the queries for
  # replacing the , delimiter with tab.
  function_and_file_name = function_and_file_name.replace(', ', '; ')
  # If the function and file name sequence contains the FUNCTION_FILE_SEPARATOR,
  # we normalize the path name of the file and make the string subtitutions
  # to make the CWP and pprof data  consistent. The returned key is composed
  # from the function name and normalized file path name, separated by a comma.
  # If the function and file name does not contain the FUNCTION_FILE_SEPARATOR,
  # we just do the strings substitution.
  if FUNCTION_FILE_SEPARATOR in function_and_file_name:
    function_name, file_name = \
        function_and_file_name.split(FUNCTION_FILE_SEPARATOR)
    file_name = \
        MakeCWPAndPprofFileNamesConsistent(os.path.normpath("/" + file_name))
    return ','.join([function_name, file_name])

  return MakeCWPAndPprofFileNamesConsistent(function_and_file_name)


def ComputeCWPCummulativeInclusiveStatistics(cwp_inclusive_count_statistics):
  """Computes the cumulative inclusive count value of a function.

  A function might appear declared in multiple files or objects. When
  computing the fraction of the inclusive count value from a child function to
  the parent function, we take into consideration the sum of the
  inclusive_count
  count values from all the ocurences of that function.

  Args:
    cwp_inclusive_count_statistics: A dict containing the inclusive count
    statistics extracted by the ParseCWPInclusiveCountFile method.

  Returns:
    A dict having as a ket the name of the function and as a value the sum of
    the inclusive count values of the occurences of the functions from all
    the files and objects.
  """
  cwp_inclusive_count_statistics_cumulative = defaultdict(int)

  for function_key, function_statistics \
      in cwp_inclusive_count_statistics.iteritems():
    function_name, _ = function_key.split(',')
    cwp_inclusive_count_statistics_cumulative[function_name] += \
        function_statistics[1]

  return cwp_inclusive_count_statistics_cumulative

def ComputeCWPChildFunctionsFractions(cwp_inclusive_count_statistics_cumulative,
                                      cwp_pairwise_inclusive_count_statistics):
  """Computes the fractions of the inclusive count values for child functions.

  The fraction represents the inclusive count value of a child function over
  the one of the parent function.

  Args:
    cwp_inclusive_count_statistics_cumulative: A dict containing the
      cumulative inclusive count values of the CWP functions.
    cwp_pairwise_inclusive_count_statistics: A dict containing the inclusive
      count statistics for pairs of parent and child functions. The key is the
      parent function. The value is a dict with the key the name of the child
      function and the file name, comma separated, and the value is the
      inclusive count value of the pair of parent and child functions.

  Returns:
      A dict containing the inclusive count statistics for pairs of parent
      and child functions. The key is the parent function. The value is a
      dict with the key the name of the child function and the file name,
      comma separated, and the value is the inclusive count fraction of the
      child function out of the parent function.
  """

  pairwise_inclusive_count_fractions = {}

  for parent_function_key, child_functions_metrics in \
      cwp_pairwise_inclusive_count_statistics.iteritems():
    child_functions_fractions = {}
    parent_function_inclusive_count = \
    cwp_inclusive_count_statistics_cumulative.get(parent_function_key, 0.0)

    if parent_function_key in cwp_inclusive_count_statistics_cumulative:
      for child_function_key, child_function_inclusive_count \
          in child_functions_metrics.iteritems():
        child_functions_fractions[child_function_key] = \
           child_function_inclusive_count / parent_function_inclusive_count
    else:
      for child_function_key, child_function_inclusive_count \
          in child_functions_metrics.iteritems():
        child_functions_fractions[child_function_key] = 0.0
    pairwise_inclusive_count_fractions[parent_function_key] = \
        child_functions_fractions

  return pairwise_inclusive_count_fractions

def ParseFunctionGroups(cwp_function_groups_lines):
  """Parses the contents of the function groups file.

  Args:
    cwp_function_groups_lines: A list of the lines contained in the CWP
      function groups file. A line contains the group name and the file path
      that describes the group, separated by a space.

  Returns:
    A list of tuples containing the group name and the file path.
  """
  # The order of the groups mentioned in the cwp_function_groups file
  # matters. A function declared in a file will belong to the first
  # mentioned group that matches its path to the one of the file.
  # It is possible to have multiple paths that belong to the same group.
  return [tuple(line.split()) for line in cwp_function_groups_lines]


def ParsePprofTopOutput(file_name):
  """Parses a file that contains the output of the pprof --top command.

  Args:
    file_name: The name of the file containing the pprof --top output.

  Returns:
    A dict having as a key the name of the function and the file containing
    the declaration of the function, separated by a comma, and as a value
    a tuple containing the flat, flat percentage, sum percentage, cummulative
    and cummulative percentage values.
  """

  pprof_top_statistics = {}

  # In the pprof top output, the statistics of the functions start from the
  # 6th line.
  with open(file_name) as input_file:
    pprof_top_content = input_file.readlines()[6:]

  for line in pprof_top_content:
    function_statistic_match = FUNCTION_STATISTIC_REGEX.search(line)
    flat, flat_p, sum_p, cum, cum_p = function_statistic_match.groups()
    flat_p = str(float(flat_p) / 100.0)
    sum_p = str(float(sum_p) / 100.0)
    cum_p = str(float(cum_p) / 100.0)
    lookup_index = function_statistic_match.end()
    function_and_file_name = line[lookup_index + 2 : -1]
    key = MakePprofFunctionKey(function_and_file_name)
    pprof_top_statistics[key] = (flat, flat_p, sum_p, cum, cum_p)
  return pprof_top_statistics


def ParsePprofTreeOutput(file_name):
  """Parses a file that contains the output of the pprof --tree command.

  Args:
    file_name: The name of the file containing the pprof --tree output.

  Returns:
    A dict including the statistics for pairs of parent and child functions.
    The key is the name of the parent function and the file where the
    function is declared, separated by a comma. The value is a dict having as
    a key the name of the child function and the file where the function is
    delcared, comma separated and as a value the percentage of time the
    parent function spends in the child function.
  """

  # In the pprof output, the statistics of the functions start from the 9th
  # line.
  with open(file_name) as input_file:
    pprof_tree_content = input_file.readlines()[9:]

  pprof_tree_statistics = defaultdict(lambda: defaultdict(float))
  track_child_functions = False

  # The statistics of a given function, its parent and child functions are
  # included between two separator marks.
  # All the parent function statistics are above the line containing the
  # statistics of the given function.
  # All the statistics of a child function are below the statistics of the
  # given function.
  # The statistics of a parent or a child function contain the calls, calls
  # percentage, the function name and the file where the function is declared.
  # The statistics of the given function contain the flat, flat percentage,
  # sum percentage, cummulative, cummulative percentage, function name and the
  # name of the file containing the declaration of the function.
  for line in pprof_tree_content:
    separator_match = SEPARATOR_REGEX.search(line)

    if separator_match:
      track_child_functions = False
      continue

    parent_function_statistic_match = FUNCTION_STATISTIC_REGEX.search(line)

    if parent_function_statistic_match:
      track_child_functions = True
      lookup_index = parent_function_statistic_match.end()
      parent_function_key_match = \
          FUNCTION_KEY_SEPARATOR_REGEX.search(line, pos=lookup_index)
      lookup_index = parent_function_key_match.end()
      parent_function_key = MakePprofFunctionKey(line[lookup_index:-1])
      continue

    if not track_child_functions:
      continue

    child_function_statistic_match = \
        CHILD_FUNCTION_PERCENTAGE_REGEX.search(line)
    child_function_percentage = \
        float(child_function_statistic_match.group(1))
    lookup_index = child_function_statistic_match.end()
    child_function_key_match = \
        FUNCTION_KEY_SEPARATOR_REGEX.search(line, pos=lookup_index)
    lookup_index = child_function_key_match.end()
    child_function_key = MakePprofFunctionKey(line[lookup_index:-1])

    pprof_tree_statistics[parent_function_key][child_function_key] += \
        child_function_percentage / 100.0

  return pprof_tree_statistics


def ParseCWPInclusiveCountFile(file_name):
  """Parses the CWP inclusive count files.

  A line should contain the name of the function, the file name with the
  declaration, the inclusive count and inclusive count fraction out of the
  total extracted inclusive count values.

  Args:
    file_name: The file containing the inclusive count values of the CWP
    functions.

  Returns:
    A dict containing the inclusive count statistics. The key is the name of
    the function and the file name, comma separated. The value represents a
    tuple with the object name containing the function declaration, the
    inclusive count and inclusive count fraction values, and a marker to
    identify if the function is present in one of the benchmark profiles.
  """
  cwp_inclusive_count_statistics = defaultdict(lambda: ('', 0, 0.0, 0))

  with open(file_name) as input_file:
    statistics_reader = csv.DictReader(input_file, delimiter=',')
    for statistic in statistics_reader:
      function_name = statistic['function']
      file_name = MakeCWPAndPprofFileNamesConsistent(
          os.path.normpath(statistic['file']))
      dso_name = statistic['dso']
      inclusive_count = statistic['inclusive_count']
      inclusive_count_fraction = statistic['inclusive_count_fraction']

      # We ignore the lines that have empty fields(i.e they specify only the
      # addresses of the functions and the inclusive counts values).
      if all([
          function_name, file_name, dso_name, inclusive_count,
          inclusive_count_fraction
      ]):
        key = '%s,%s' % (function_name, file_name)

        # There might be situations where a function appears in multiple files
        # or objects. Such situations can occur when in the Dremel queries there
        # are not specified the Chrome OS version and the name of the board (i.e
        # the files can belong to different kernel or library versions).
        inclusive_count_sum = \
            cwp_inclusive_count_statistics[key][1] + int(inclusive_count)
        inclusive_count_fraction_sum = \
            cwp_inclusive_count_statistics[key][2] + \
            float(inclusive_count_fraction)

        # All the functions are initially marked as EXTRA_FUNCTION.
        value = \
            (dso_name, inclusive_count_sum, inclusive_count_fraction_sum,
             EXTRA_FUNCTION)
        cwp_inclusive_count_statistics[key] = value

  return cwp_inclusive_count_statistics


def ParseCWPPairwiseInclusiveCountFile(file_name):
  """Parses the CWP pairwise inclusive count files.

  A line of the file should contain a pair of a parent and a child function,
  concatenated by the PARENT_CHILD_FUNCTIONS_SEPARATOR, the name of the file
  where the child function is declared and the inclusive count fractions of
  the pair of functions out of the total amount of inclusive count values.

  Args:
    file_name: The file containing the pairwise inclusive_count statistics of
      the
    CWP functions.

  Returns:
    A dict containing the statistics of the parent functions and each of
    their child functions. The key of the dict is the name of the parent
    function. The value is a dict having as a key the name of the child
    function with its file name separated by a ',' and as a value the
    inclusive count value of the parent-child function pair.
  """
  pairwise_inclusive_count_statistics = defaultdict(lambda: defaultdict(float))

  with open(file_name) as input_file:
    statistics_reader = csv.DictReader(input_file, delimiter=',')

    for statistic in statistics_reader:
      parent_function_name, child_function_name = \
          statistic['parent_child_functions'].split(
              PARENT_CHILD_FUNCTIONS_SEPARATOR)
      child_function_file_name = MakeCWPAndPprofFileNamesConsistent(
          os.path.normpath(statistic['child_function_file']))
      inclusive_count = statistic['inclusive_count']

      # There might be situations where a child function appears in
      # multiple files or objects. Such situations can occur when in the
      # Dremel queries are not specified the Chrome OS version and the
      # name of the board (i.e the files can belong to different kernel or
      # library versions), when the child function is a template function
      # that is declared in a header file or there are name collisions
      # between multiple executable objects.
      # If a pair of child and parent functions appears multiple times, we
      # add their inclusive count values.
      child_function_key = ','.join(
          [child_function_name, child_function_file_name])
      pairwise_inclusive_count_statistics[parent_function_name] \
          [child_function_key] += float(inclusive_count)

  return pairwise_inclusive_count_statistics
