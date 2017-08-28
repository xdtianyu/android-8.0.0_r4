#!/bin/bash

# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
# Uses Dremel queries to collect the inclusive and pairwise inclusive statistics
# for odd/even profile collection session ids.
# The data is collected for an odd or even collection session id.

set -e

if [ $# -lt 8 ]; then
  echo "Usage: collect_experiment_data_odd_even_session.sh cwp_table board " \
    "board_arch Chrome_version Chrome_OS_version odd_even " \
    "inclusive_output_file pairwise_inclusive_output_file"
  exit 1
fi

readonly TABLE=$1
readonly INCLUSIVE_OUTPUT_FILE=$7
readonly PAIRWISE_INCLUSIVE_OUTPUT_FILE=$8
readonly PERIODIC_COLLECTION=1
WHERE_CLAUSE_SPECIFICATIONS="meta.cros.board = '$2' AND \
  meta.cros.cpu_architecture = '$3' AND \
  meta.cros.chrome_version LIKE '%$4%' AND \
  meta.cros.version = '$5' AND \
  meta.cros.collection_info.trigger_event = $PERIODIC_COLLECTION AND \
  MOD(session.id, 2) = $6 AND \
  session.total_count > 2000"

# Collects the function, with its file, the object and inclusive count
# fraction out of the total amount of inclusive count values.
echo "
SELECT
  replace(frame.function_name, \", \", \"; \") AS function,
  frame.filename AS file,
  frame.load_module_path AS dso,
  SUM(frame.inclusive_count) AS inclusive_count,
  SUM(frame.inclusive_count)/ANY_VALUE(total.value) AS inclusive_count_fraction
FROM
  $TABLE table,
  table.frame frame
CROSS JOIN (
  SELECT
    SUM(count) AS value
  FROM $TABLE
  WHERE
    $WHERE_CLAUSE_SPECIFICATIONS
) AS total
WHERE
    $WHERE_CLAUSE_SPECIFICATIONS
GROUP BY
  function,
  file,
  dso
HAVING
  inclusive_count_fraction > 0.0
ORDER BY
  inclusive_count_fraction DESC;
" | dremel --sql_dialect=GoogleSQL --min_completion_ratio=1.0 --output=csv > \
  "$INCLUSIVE_OUTPUT_FILE"

# Collects the pair of parent and child functions, with the file and object
# where the child function is declared and the inclusive count fraction of the
# pair out of the total amount of inclusive count values.
echo "
SELECT
  CONCAT(replace(frame.parent_function_name, \", \", \"; \"), \";;\",
    replace(frame.function_name, \", \", \"; \")) AS parent_child_functions,
  frame.filename AS child_function_file,
  frame.load_module_path AS child_function_dso,
  SUM(frame.inclusive_count)/ANY_VALUE(total.value) AS inclusive_count
FROM
  $TABLE table,
  table.frame frame
CROSS JOIN (
  SELECT
    SUM(count) AS value
  FROM
    $TABLE
  WHERE
    $WHERE_CLAUSE_SPECIFICATIONS
) AS total
WHERE
  $WHERE_CLAUSE_SPECIFICATIONS
GROUP BY
  parent_child_functions,
  child_function_file,
  child_function_dso
HAVING
  inclusive_count > 0.0
ORDER BY
  inclusive_count DESC;
" | dremel --sql_dialect=GoogleSQL --min_completion_ratio=1.0 --output=csv > \
  "$PAIRWISE_INCLUSIVE_OUTPUT_FILE"
