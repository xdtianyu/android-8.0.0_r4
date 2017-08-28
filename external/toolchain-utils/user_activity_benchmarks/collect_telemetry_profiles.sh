#!/bin/bash

# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
# Runs the Telemetry benchmarks with AutoTest and collects their perf profiles.
# Reads the benchmark names from the telemetry_benchmark_file. Each benchmark
# should be placed on a separate line.
# The profile results are placed in the results_path.

set -e

if [ "$#" -ne 5 ]; then
  echo "USAGE: collect_telemetry_profiles.sh board chrome_root_path " \
  "machine_ip results_path telemetry_benchmarks_file"
  exit 1
fi

# CHROME_ROOT should contain the path with the source of Chrome. This is used by
# AutoTest.
export CHROME_ROOT=$2

readonly BOARD=$1
readonly IP=$3
readonly RESULTS_PATH=$4
readonly TELEMETRY_BENCHMARKS_FILE=$5

# The following Telemetry benchmarks failed for the R52-8350.68.0 Chrome OS
# version: page_cycler_v2.top_10_mobile,
# page_cycler_v2.basic_oopif, smoothness.tough_filters_cases,
# page_cycler_v2.intl_hi_ru,
# image_decoding.image_decoding_measurement, system_health.memory_mobile,
# memory.top_7_stress, smoothness.tough_path_rendering_cases,
# page_cycler_v2.tough_layout_cases,
# memory.long_running_idle_gmail_background_tbmv2, smoothness.tough_webgl_cases,
# smoothness.tough_canvas_cases, smoothness.tough_texture_upload_cases,
# top_10_mobile_memory_ignition, startup.large_profile.cold.blank_page,
# page_cycler_v2.intl_ar_fa_he, start_with_ext.cold.blank_page,
# start_with_ext.warm.blank_page, page_cycler_v2.intl_ko_th_vi,
# smoothness.scrolling_tough_ad_case, page_cycler_v2_site_isolation.basic_oopif,
# smoothness.tough_scrolling_cases, startup.large_profile.warm.blank_page,
# page_cycler_v2.intl_es_fr_pt-BR, page_cycler_v2.intl_ja_zh,
# memory.long_running_idle_gmail_tbmv2, smoothness.scrolling_tough_ad_cases,
# page_cycler_v2.typical_25, smoothness.tough_webgl_ad_cases,
# smoothness.tough_image_decode_cases.
#
# However, we did not manage to collect the profiles only from the following
# benchmarks: smoothness.tough_filters_cases,
# smoothness.tough_path_rendering_cases, page_cycler_v2.tough_layout_cases,
# smoothness.tough_webgl_cases, smoothness.tough_canvas_cases,
# smoothness.tough_texture_upload_cases, smoothness.tough_scrolling_cases,
# smoothness.tough_webgl_ad_cases, smoothness.tough_image_decode_cases.
#
# Use ./run_benchmark --browser=cros-chrome --remote=$IP list to get the list of
# Telemetry benchmarks.
readonly LATEST_PERF_PROFILE=/tmp/test_that_latest/results-1-telemetry_Crosperf/telemetry_Crosperf/profiling/perf.data

while read benchmark
do
  # TODO(evelinad): We should add -F 4000000 to the list of profiler_args
  # arguments because we need to use the same sampling period as the one used
  # to collect the CWP user data (4M number of cycles for cycles.callgraph).
  test_that --debug  --board=${BOARD} --args=" profiler=custom_perf \
    profiler_args='record -g -a -e cycles,instructions' \
    run_local=False test=$benchmark " $IP telemetry_Crosperf
  if [ $? -ne 0 ]; then
    echo "Failed to run the $benchmark telemetry benchmark with Autotest."
    continue
  fi
  echo "Warning: Sampling period is too high. It should be set to 4M samples."

  cp "$LATEST_PERF_PROFILE" "$RESULTS_PATH/${benchmark}.data"
  if [ $? -ne 0 ]; then
    echo "Failed to move the perf profile file from $LATEST_PERF_PROFILE to " \
      "$PERF_DATA_RESULTS_PATH/${benchmark}.data for the $benchmark " \
      "telemetry benchmark."
    continue
  fi

  # The ssh connection should be configured without password. We need to do
  # this step because we might run out of disk space if we run multiple
  # benchmarks.
  ssh root@$IP "rm -rf /usr/local/profilers/*"
  if [ $? -ne 0 ]; then
    echo "Failed to remove the output files from /usr/local/profilers/ for " \
      "the $benchmark telemetry benchmark."
    continue
  fi
done < $TELEMETRY_BENCHMARKS_FILE

