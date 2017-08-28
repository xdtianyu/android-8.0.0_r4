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

#include <dirent.h>
#include <fstream>
#include <iostream>
#include <map>
#include <string>
#include <vector>

#include <google/protobuf/text_format.h>
#include <test/vts/proto/ComponentSpecificationMessage.pb.h>
#include "VtsTraceProcessor.h"

using namespace std;
using google::protobuf::TextFormat;

namespace android {
namespace vts {

bool VtsTraceProcessor::ParseTrace(const string& trace_file,
    bool ignore_timestamp, bool entry_only,
    VtsProfilingMessage* profiling_msg) {
  ifstream in(trace_file, std::ios::in);
  bool new_record = true;
  string record_str, line;

  while (getline(in, line)) {
    // Assume records are separated by '\n'.
    if (line.empty()) {
      new_record = false;
    }
    if (new_record) {
      record_str += line + "\n";
    } else {
      VtsProfilingRecord record;
      if (!TextFormat::MergeFromString(record_str, &record)) {
        cerr << "Can't parse a given record: " << record_str << endl;
        return false;
      }
      if (ignore_timestamp) {
        record.clear_timestamp();
      }
      if (entry_only) {
        if (record.event() == InstrumentationEventType::SERVER_API_ENTRY
            || record.event() == InstrumentationEventType::CLIENT_API_ENTRY
            || record.event() == InstrumentationEventType::PASSTHROUGH_ENTRY)
          *profiling_msg->add_records() = record;
      } else {
        *profiling_msg->add_records() = record;
      }
      new_record = true;
      record_str.clear();
    }
  }
  return true;
}

bool VtsTraceProcessor::WriteRecords(const string& output_file,
    const std::vector<VtsProfilingRecord>& records) {
  ofstream output = std::ofstream(output_file, std::fstream::out);
  for (const auto& record : records) {
    string record_str;
    if (!TextFormat::PrintToString(record, &record_str)) {
      cerr << "Can't print the message" << endl;
      return false;
    }
    output << record_str << "\n";
  }
  output.close();
  return true;
}

void VtsTraceProcessor::CleanupTraceForReplay(const string& trace_file) {
  VtsProfilingMessage profiling_msg;
  if (!ParseTrace(trace_file, false, false, &profiling_msg)) {
    cerr << "Failed to parse trace file: " << trace_file << endl;
    return;
  }
  vector<VtsProfilingRecord> clean_records;
  for (const auto& record : profiling_msg.records()) {
    if (record.event() == InstrumentationEventType::SERVER_API_ENTRY
        || record.event() == InstrumentationEventType::SERVER_API_EXIT) {
      clean_records.push_back(record);
    }
  }
  string tmp_file = trace_file + "_tmp";
  if (!WriteRecords(tmp_file, clean_records)) {
    cerr << "Failed to write new trace file: " << tmp_file << endl;
    return;
  }
  if (rename(tmp_file.c_str(), trace_file.c_str())) {
    cerr << "Failed to replace old trace file: " << trace_file << endl;
    return;
  }
}

void VtsTraceProcessor::ProcessTraceForLatencyProfiling(
    const string& trace_file) {
  VtsProfilingMessage profiling_msg;
  if (!ParseTrace(trace_file, false, false, &profiling_msg)) {
    cerr << ": Failed to parse trace file: " << trace_file << endl;
    return;
  }
  if (!profiling_msg.records_size()) return;
  if (profiling_msg.records(0).event()
      == InstrumentationEventType::PASSTHROUGH_ENTRY
      || profiling_msg.records(0).event()
          == InstrumentationEventType::PASSTHROUGH_EXIT) {
    cout << "hidl_hal_mode:passthrough" << endl;
  } else {
    cout << "hidl_hal_mode:binder" << endl;
  }

  for (int i = 0; i < profiling_msg.records_size() - 1; i += 2) {
    string api = profiling_msg.records(i).func_msg().name();
    int64_t start_timestamp = profiling_msg.records(i).timestamp();
    int64_t end_timestamp = profiling_msg.records(i + 1).timestamp();
    int64_t latency = end_timestamp - start_timestamp;
    cout << api << ":" << latency << endl;
  }
}

void VtsTraceProcessor::DedupTraces(const string& trace_dir) {
  DIR *dir = opendir(trace_dir.c_str());
  if (dir == 0) {
    cerr << trace_dir << "does not exist." << endl;
    return;
  }
  vector<VtsProfilingMessage> seen_msgs;
  vector<string> duplicate_trace_files;
  struct dirent *file;
  long total_trace_num = 0;
  long duplicat_trace_num = 0;
  while ((file = readdir(dir)) != NULL) {
    if (file->d_type == DT_REG) {
      total_trace_num++;
      string trace_file = trace_dir + file->d_name;
      VtsProfilingMessage profiling_msg;
      if (!ParseTrace(trace_file, true, true, &profiling_msg)) {
        cerr << "Failed to parse trace file: " << trace_file << endl;
        return;
      }
      if (!profiling_msg.records_size()) {  // empty trace file
        duplicate_trace_files.push_back(trace_file);
        duplicat_trace_num++;
        continue;
      }
      auto found = find_if(
          seen_msgs.begin(), seen_msgs.end(),
          [&profiling_msg] (const VtsProfilingMessage& seen_msg) {
            std::string str_profiling_msg;
            std::string str_seen_msg;
            profiling_msg.SerializeToString(&str_profiling_msg);
            seen_msg.SerializeToString(&str_seen_msg);
            return (str_profiling_msg == str_seen_msg);
          });
      if (found == seen_msgs.end()) {
        seen_msgs.push_back(profiling_msg);
      } else {
        duplicate_trace_files.push_back(trace_file);
        duplicat_trace_num++;
      }
    }
  }
  for (const string& duplicate_trace : duplicate_trace_files) {
    cout << "deleting duplicate trace file: " << duplicate_trace << endl;
    remove(duplicate_trace.c_str());
  }
  cout << "Num of traces processed: " << total_trace_num << endl;
  cout << "Num of duplicate trace deleted: " << duplicat_trace_num << endl;
  cout << "Duplicate percentage: "
       << float(duplicat_trace_num) / total_trace_num << endl;
}

}  // namespace vts
}  // namespace android
