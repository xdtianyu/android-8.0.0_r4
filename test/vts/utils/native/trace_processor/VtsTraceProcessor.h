/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef TOOLS_TRACE_PROCESSOR_VTSTRACEPROCESSOR_H_
#define TOOLS_TRACE_PROCESSOR_VTSTRACEPROCESSOR_H_

#include <android-base/macros.h>
#include <test/vts/proto/VtsProfilingMessage.pb.h>

namespace android {
namespace vts {

class VtsTraceProcessor {
 public:
  VtsTraceProcessor() {};
  virtual ~VtsTraceProcessor() {};

  // Cleanups the given trace file to be used for replaying.
  // Current cleanup includes:
  // 1. remove duplicate trace item (e.g. passthrough event on the server side)
  // 2. remove trace item that could not be replayed (e.g. client event on the
  //    server side).
  void CleanupTraceForReplay(const std::string& trace_file);
  // Parses the given trace file and outputs the latency for each API call.
  void ProcessTraceForLatencyProfiling(const std::string& trace_file);
  // Parses all trace files under the the given trace directory and remove
  // duplicate trace file.
  void DedupTraces(const std::string& trace_dir);

 private:
  // Reads the trace file and parse each trace event into VtsProfilingRecord.
  bool ParseTrace(const std::string& trace_file, bool ignore_timestamp,
      bool entry_only, VtsProfilingMessage* profiling_msg);
  // Writes the given list of VtsProfilingRecord into an output file.
  bool WriteRecords(const std::string& output_file,
      const std::vector<VtsProfilingRecord>& records);

  DISALLOW_COPY_AND_ASSIGN (VtsTraceProcessor);
};

}  // namespace vts
}  // namespace android
#endif  // TOOLS_TRACE_PROCESSOR_VTSTRACEPROCESSOR_H_
