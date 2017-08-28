-- Collects the function, with its file, the object and inclusive count value.
-- The limits here are entirely arbitrary.
-- For more background, look at
-- https://sites.google.com/a/google.com/cwp/about/callgraphs.
SELECT
  frame.function_name AS function,
  frame.filename AS file,
  frame.load_module_path AS dso,
  sum(frame.inclusive_count) AS inclusive_count
FROM
  -- Collect the data stored in CWP over the last 30 days.
  FLATTEN(chromeos_wide_profiling.sampledb.cycles.callgraph.last30days, frame)
WHERE
  meta.cros.report_id % UINT64("1") == 0
  -- The reports were collected periodically.
  AND meta.cros.collection_info.trigger_event == 1
  AND `profile.duration_usec` < 2100000
  -- The reports were from a busy machine.
  AND session.total_count > 2000
  --  The reports are from the gnawty board, x86_64 architecture.
  AND meta.cros.board == "gnawty"
  AND meta.cros.cpu_architecture == "x86_64"
  -- The reports include callchain data.
  AND left(meta.cros.version, 4) > "6970"
  GROUP BY function, dso, file
ORDER BY `inclusive_count` DESC
LIMIT 50000 ;
