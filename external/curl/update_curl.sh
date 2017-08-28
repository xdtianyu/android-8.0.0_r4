#!/bin/bash
#
# Copyright (C) 2016 The Android Open-Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -e -u

base_dir=$(realpath $(dirname $0))
cd "${base_dir}"

UPSTREAM_GIT="https://github.com/curl/curl"
UPSTREAM="github_curl"
README_FILE="README.version"

TEMP_FILES=()
cleanup() {
  trap - INT TERM ERR EXIT
  if [[ ${#TEMP_FILES[@]} -ne 0 ]]; then
    rm -f "${TEMP_FILES[@]}"
  fi
}
trap cleanup INT TERM ERR EXIT

# Prints the URL to the package for the passed version.
upstream_url() {
  local version="$1"
  echo "https://curl.haxx.se/download/curl-${version}.tar.gz"
}

# Update the contents of the README.version with the new version string passed
# as the first parameter.
update_readme() {
  local version="$1"
  local sha="$2"
  local tmp_readme=$(mktemp update_readme.XXXXXX)
  TEMP_FILES+=("${tmp_readme}")
  local url
  url=$(upstream_url "${version}")

  cat >"${tmp_readme}" <<EOF
URL: ${url}
Version: ${version}
Upstream commit: ${sha}
EOF
  grep -v -E '^(URL|Version|Upstream commit):' "${README_FILE}" \
    >>"${tmp_readme}" 2>/dev/null || true
  cp "${tmp_readme}" "${README_FILE}"
}


# Print the current branch name.
git_current_branch() {
  git rev-parse --abbrev-ref HEAD
}

# Setup and fetch the upstream remote. While we have mirroring setup of the
# remote, we need to fetch all the tags from upstream to identify the latest
# release.
setup_git() {
  local current_branch
  current_branch=$(git_current_branch)
  if [[ "${current_branch}" == "HEAD" ]]; then
    echo "Not working on a branch, please run 'repo start [branch_name] .'" \
      " first." >&2
    exit 1
  fi

  # Setup and fetch the remote.
  if ! git remote show | grep "^${UPSTREAM}\$" >/dev/null; then
    echo "Adding remote ${UPSTREAM} to ${UPSTREAM_GIT}"
    git remote add -t master "${UPSTREAM}" "${UPSTREAM_GIT}"
  fi

  TRACKING_BRANCH=$(git rev-parse --abbrev-ref --symbolic-full-name @{u})
  OUR_REMOTE="${TRACKING_BRANCH%/*}"

  echo "Fetching latest upstream code..."
  git fetch --quiet "${UPSTREAM}" master
}


main() {
  setup_git

  # Load the current version's upstream hash.
  local current_sha current_version
  current_version=$(grep '^Version: ' "${README_FILE}" | cut -d ' ' -f 2-)
  current_sha=$(grep '^Upstream commit: ' "${README_FILE}" | cut -d ' ' -f 3)

  if [[ -z "${current_sha}" ]]; then
    echo "Couldn't determine the upstream commit the current code is at." \
      "Please update ${README_FILE} to include it." >&2
    exit 1
  fi

  local target_sha target_versio
  target_sha="${1:-}"
  if [[ -z "${target_sha}" ]]; then
    cat >&2 <<EOF
Usage: $0 [target_sha]

Pass the upstream commit SHA of the release you want to update to.
Candidate values are:
EOF
    # Find the list of potential target_version values.
    git --no-pager log "${UPSTREAM}/master" --not "${current_sha}" --oneline \
      --grep=RELEASE-NOTES --grep=THANKS -- RELEASE-NOTES docs/THANKS
    exit 1
  fi

  # Expand the hash to the full value:
  target_sha=$(git rev-list -n 1 "${target_sha}")
  target_version=$(git show ${target_sha}:include/curl/curlver.h | \
    grep '^#define LIBCURL_VERSION ')
  target_version=$(echo "${target_version}" | cut -f 2 -d '"')
  target_version="${target_version%-DEV}"

  # Sanity check that the passed hash is forward in the chain.
  if [[ $(git log --oneline "${target_sha}" --not "${current_sha}" | wc -l) \
      == 0 ]]; then
    echo "The target SHA (${target_sha}) is not forward from ${current_sha}.">&2
    exit 1
  fi

  echo "Current version: ${current_version} / ${current_sha}"
  echo "Target version:  ${target_version} / ${target_sha}"
  echo

  # Generate the log message.
  local log_message=$(mktemp log_message.XXXXXX)
  TEMP_FILES+=("${log_message}")

  (cat <<EOF
Update libcurl from ${current_version} to ${target_version}.

Bug: COMPLETE
Test: COMPLETE

Note: This patch includes the following squashed commits from upstream:

EOF
   git --no-pager log "${target_sha}" \
     --not "${current_sha}")>"${log_message}"

  # Land the changes and commit.
  update_readme "${target_version}" "${target_sha}"
  git add "README.version"

  git cherry-pick --no-commit "${target_sha}" --not "${current_sha}"
  git commit --file="${log_message}"

  cat <<EOF

  Run:
    git commit --amend

  edit the message to add the bug number and test it.

EOF
}

main "$@"
