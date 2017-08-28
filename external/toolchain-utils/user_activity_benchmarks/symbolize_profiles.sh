#!/bin/bash

# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
# Uses local_cwp to do the profile symbolization.
# The profiles that need to be symbolized are placed in the profiles_path.
# The results are placed in the local_cwp_results_path.

set -e

if [ "$#" -ne 3 ]; then
  echo "USAGE: symbolize_profiles.sh profiles_path local_cwp_binary_path " \
    "local_cwp_results_path"
  exit 1
fi

readonly PROFILES_PATH=$1
readonly LOCAL_CWP_BINARY_PATH=$2
readonly LOCAL_CWP_RESULTS_PATH=$3
readonly PROFILES=$(ls $PROFILES_PATH)

for profile in "${PROFILES[@]}"
do
  $LOCAL_CWP_BINARY_PATH --output="$LOCAL_CWP_RESULTS_PATH/${profile}.pb.gz" \
    "$PROFILES_PATH/$profile"
  if [ $? -ne 0 ]; then
    echo "Failed to symbolize the perf profile output with local_cwp for " \
      "$profile."
    continue
  fi
done
