#!/usr/bin/env bash
set -e
##
## Copyright (C) 2016 The Android Open Source Project
##
## Licensed under the Apache License, Version 2.0 (the "License");
## you may not use this file except in compliance with the License.
## You may obtain a copy of the License at
##
##      http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software
## distributed under the License is distributed on an "AS IS" BASIS,
## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
## See the License for the specific language governing permissions and
## limitations under the License.
##
##
## Build and stage the source.android.com website.
##
## Usage:
## From the repo's root directory (croot):
##   $ ./docs/source.android.com/scripts/build2stage.sh [options] server-number
##
## To build/stage from anywhere, add an alias or scripts/ to PATH.
##
## Examples:
## Build and stage on staging instance 13:
##   $ build2stage.sh 13
## Build and generate reference docs, stage on instance 13:
##   $ build2stage.sh -r 13
## Build only (outputs to out/target/common/docs/online-sac):
##   $ build2stage.sh -b
## Stage only (using existing build):
##   $ build2stage.sh -s 13
##
## The script uses some environmental variables that can be set locally or
## in /etc/profile.d/build2stage-conf.sh, or passed via the command-line:
##
## Change output directory used for generated files:
##   $ OUT_DIR=/path/to/dir build2stage.sh -b
## Change build target:
##   $ BUILD_TARGET=aosp_x86_64 build2stage.sh -b
## Location of staging tool and output directory:
##   $ AE_STAGING=/path/to/ae_staging OUT_DIR=/path/to/dir build2stage 13
##
## This script attempts to determine if called from within a gitc client and
## set the output directory accordingly. If it's not building correctly, you can
## try setting the REPO_ROOT and OUT_DIR variables in your environment.

usage() {
  echo "Usage: $(basename $0) [options] server-number"
  echo "Options:"
  echo " -r    Generate reference docs (HAL, TradeFed)"
  echo " -b    Build docs without staging"
  echo " -s    Stage only using an existing build"
  echo " -h    Print this help and exit"
}

# Arguments required
if [ $# -eq 0 ]; then
  usage
  exit 1
fi

##
## VARS
##

# Sourced for env vars
: ${AE_STAGING_CONF:="/etc/profile.d/build2stage-conf.sh"}

# Retrieve App Engine staging config 'AE_STAGING' (and other vars if there)
if [ -e "$AE_STAGING_CONF" ]; then
  source "$AE_STAGING_CONF"
fi

LOG_PREFIX="[$(basename $0)]"
# Lunch build config
: ${BUILD_TARGET:="aosp_arm-eng"}

# gitc clients should output to a different directory.
# Test if using gitc by checking user's current dir or this script's location
GITC_CLIENT_PREFIX="/gitc/manifest-rw"
GITC_CONF="/gitc/.config"
if [ -f "$GITC_CONF" ]; then
  GITC_OUT_PREFIX=$(grep 'gitc_dir' "$GITC_CONF" 2>/dev/null | cut -d '=' -f2)
fi

## SET REPO ROOT

# If user is currently within a gitc client dir, use that project as repo root
if [[ -z "$REPO_ROOT" && "$(pwd -P)" == "${GITC_CLIENT_PREFIX}/"* ]]; then
  gitc_client_name=$(echo "$(pwd -P)" | cut -d '/' -f4) #get 3rd dir name
  REPO_ROOT="${GITC_CLIENT_PREFIX}/${gitc_client_name}"
fi

# If not set, determine repo root relative to location of this script itself
: ${REPO_ROOT:="$(cd $(dirname $0)/../../..; pwd -P)"}

## SET OUTPUT DIR

# If repo root within gitc client, set gitc output directory
if [[ -z "$OUT_DIR" && "$REPO_ROOT" == "${GITC_CLIENT_PREFIX}/"* ]]; then
  gitc_client_name=$(echo "$REPO_ROOT" | cut -d '/' -f4) #get 3rd dir name
  OUT_DIR="${GITC_OUT_PREFIX}/${gitc_client_name}/out"
fi

# Directory for output files
: ${OUT_DIR:="${REPO_ROOT}/out"}
# Docs output directory and where to stage from
OUT_DIR_SAC="${OUT_DIR}/target/common/docs/online-sac"

## PARSE OPTIONS

while getopts "bsrh" opt; do
  case $opt in
    b) BUILD_ONLY_FLAG=1;;
    s) STAGE_ONLY_FLAG=1;;
    r) BUILD_REF_HAL=1; BUILD_REF_TRADEFED=1;;
    h | *)
      usage
      exit 0
      ;;
  esac
done

##
## CHECK ARGS
##

# Get final command-line arg
for last; do true; done
STAGING_NUM="$last"

if [ -z "$BUILD_ONLY_FLAG" ]; then
  # Must be a number
  if ! [[ "$STAGING_NUM" =~ ^[0-9]+$ ]] ; then
    echo "${LOG_PREFIX} Error: Argument for server instance must be a number" 1>&2
    usage
    exit 1
  fi
fi

if [ -n "$STAGE_ONLY_FLAG" ] && [ ! -d "$OUT_DIR_SAC" ]; then
  echo "${LOG_PREFIX} Error: Unable to stage without a build" 1>&2
  exit 1
fi

# If staging, require staging config
if [ -z "$BUILD_ONLY_FLAG" ] && [ -z "$AE_STAGING" ]; then
  echo "${LOG_PREFIX} Error: No value for AE_STAGING" 1>&2
  echo "Set in local environment or ${AE_STAGING_CONF}" 1>&2
  exit 1
fi

if [ ! -d "$REPO_ROOT" ]; then
  echo "${LOG_PREFIX} Error: Repo directory doesn't exist: ${REPO_ROOT}" 1>&2
  exit 1
fi

if [ ! -d "$(dirname $OUT_DIR)" ]; then
  echo "${LOG_PREFIX} Error: Output root dir doesn't exist: $(dirname $OUT_DIR)" 1>&2
  exit 1
fi

##
## BUILD DOCS
##

echo "${LOG_PREFIX} Using repo: ${REPO_ROOT}"
echo "${LOG_PREFIX} Output dir: ${OUT_DIR}"

if [ -n "$STAGE_ONLY_FLAG" ]; then
  echo "${LOG_PREFIX} Not building"

else
  cd "$REPO_ROOT"

  # Delete old output
  if [ -d "$OUT_DIR_SAC" ]; then
    echo "${LOG_PREFIX} Removing old build: ${OUT_DIR_SAC}"
    rm -rf "$OUT_DIR_SAC"*
  fi

  # Initialize the build environment
  source build/make/envsetup.sh

  # Select a target and finish setting up the environment
  lunch "$BUILD_TARGET"

  # Build the docs and output to: out/target/common/docs/online-sac
  make online-sac-docs

  # Reference dir used for tradefed
  rm -rf "${OUT_DIR_SAC}/reference"
  rm "${OUT_DIR_SAC}/navtree_data.js"

  if [ -n "$BUILD_REF_HAL" ]; then
    # HAL reference
    if command -v doxygen >/dev/null 2>&1; then
      #run doxygen
      make setup-hal-ref #docs/source.android.com/Android.mk

      #will use central js files instead
      rm "${OUT_DIR_SAC}/devices/halref/jquery.js"
      rm "${OUT_DIR_SAC}/devices/halref/functions_~.html"
    else
      echo "${LOG_PREFIX} Error: Requires doxygen to build HAL reference" 1>&2
      exit 1
    fi
  fi

  if [ -n "$BUILD_REF_TRADEFED" ]; then
    # Trade Federation reference
    make tradefed-docs
    make setup-tradefed-ref #docs/source.android.com/Android.mk
  fi
fi

##
## STAGE DOCS
##

if [ -n "$BUILD_ONLY_FLAG" ]; then
  echo "${LOG_PREFIX} Not staging"

else
  # Make sure there's something to stage
  if [ ! -d "$OUT_DIR_SAC" ]; then
    echo "${LOG_PREFIX} Error: Unable to stage without a build directory" 1>&2
    exit 1
  fi

  ## Set staging server

  # Parse current value for yaml key 'application'
  STAGING_SERVER=$(cat "${OUT_DIR_SAC}/app.yaml" | grep "^application:" | \
                     cut -d ':' -f2- | tr -d ' ')
  # Remove any trailing numbers in case it's already been set
  STAGING_SERVER=$(echo "$STAGING_SERVER" | sed 's/[0-9]\{1,10\}$//')

  # Set new staging server
  STAGING_SERVER="${STAGING_SERVER}${STAGING_NUM}"

  tmpfile=$(mktemp /tmp/app.yaml.XXXX)

  # Replace application key in tmp app.yaml with specified staging server
  sed "s/^application:.*/application: ${STAGING_SERVER}/" \
      "${OUT_DIR_SAC}/app.yaml" > "$tmpfile"

  # Copy in new app.yaml content
  cp "$tmpfile" "${OUT_DIR_SAC}/app.yaml"
  rm "$tmpfile"

  echo "${LOG_PREFIX} Configured stage for ${STAGING_SERVER}"

  ## Stage
  ##
  echo "${LOG_PREFIX} Start staging ..."

  # Stage to specified server
  if $AE_STAGING update "$OUT_DIR_SAC"; then
    echo "${LOG_PREFIX} Content now available at staging instance ${STAGING_NUM}"
  else
    echo "${LOG_PREFIX} Error: Unable to stage to ${STAGING_SERVER}" 1>&2
    exit 1
  fi
fi
