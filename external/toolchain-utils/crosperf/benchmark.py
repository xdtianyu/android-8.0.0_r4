
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Define a type that wraps a Benchmark instance."""

class Benchmark(object):
  """Class representing a benchmark to be run.

  Contains details of the benchmark suite, arguments to pass to the suite,
  iterations to run the benchmark suite and so on. Note that the benchmark name
  can be different to the test suite name. For example, you may want to have
  two different benchmarks which run the same test_name with different
  arguments.
  """

  def __init__(self,
               name,
               test_name,
               test_args,
               iterations,
               rm_chroot_tmp,
               perf_args,
               suite='',
               show_all_results=False,
               retries=0,
               run_local=False):
    self.name = name
    #For telemetry, this is the benchmark name.
    self.test_name = test_name
    #For telemetry, this is the data.
    self.test_args = test_args
    self.iterations = iterations
    self.perf_args = perf_args
    self.rm_chroot_tmp = rm_chroot_tmp
    self.iteration_adjusted = False
    self.suite = suite
    self.show_all_results = show_all_results
    self.retries = retries
    if self.suite == 'telemetry':
      self.show_all_results = True
    if run_local and self.suite != 'telemetry_Crosperf':
      raise RuntimeError('run_local is only supported by telemetry_Crosperf.')
    self.run_local = run_local
