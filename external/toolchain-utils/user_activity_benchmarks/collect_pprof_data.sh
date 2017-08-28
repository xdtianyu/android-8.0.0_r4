#!/bin/bash

# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
# Collects the pprof tree and top outputs.
# All the local_cwp symbolized profiles are taken from the
# local_cwp_results_path.
# The pprof top output is stored in the pprof_top_results_path and the pprof
# tree output is stored in the pprof_tree_results_path.

set -e

if [ "$#" -ne 3 ]; then
  echo "USAGE: collect_pprof_data.sh local_cwp_results_path " \
    "pprof_top_results_path pprof_tree_results_path"
  exit 1
fi

readonly LOCAL_CWP_RESULTS_PATH=$1
readonly PPROF_TOP_RESULTS_PATH=$2
readonly PPROF_TREE_RESULTS_PATH=$3
readonly SYMBOLIZED_PROFILES=`ls $LOCAL_CWP_RESULTS_PATH`

for symbolized_profile in "${SYMBOLIZED_PROFILES[@]}"
do
  pprof --top "$LOCAL_CWP_RESULTS_PATH/${symbolized_profile}" > \
    "$PPROF_TOP_RESULTS_PATH/${symbolized_profile}.pprof"
  if [ $? -ne 0 ]; then
    echo "Failed to extract the pprof top output for the $symbolized_profile."
    continue
  fi

  pprof --tree "$LOCAL_CWP_RESULTS_PATH/${symbolized_profile}" > \
    "$PPROF_TREE_RESULTS_PATH/${symbolized_profile}.pprof"
  if [ $? -ne 0 ]; then
    echo "Failed to extract the pprof tree output for the " \
      "$symbolized_profile."
    continue
  fi
done
