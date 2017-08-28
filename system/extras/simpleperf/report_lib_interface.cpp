/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <memory>
#include <utility>

#include <android-base/logging.h>
#include <android-base/file.h>

#include "dso.h"
#include "event_attr.h"
#include "record_file.h"
#include "thread_tree.h"
#include "utils.h"

class ReportLib;

extern "C" {

#define EXPORT __attribute__((visibility("default")))

struct Sample {
  uint64_t ip;
  uint32_t pid;
  uint32_t tid;
  const char* thread_comm;
  uint64_t time;
  uint32_t in_kernel;
  uint32_t cpu;
  uint64_t period;
};

struct Event {
  const char* name;
};

struct Mapping {
  uint64_t start;
  uint64_t end;
  uint64_t pgoff;
};

struct SymbolEntry {
  const char* dso_name;
  uint64_t vaddr_in_file;
  const char* symbol_name;
  uint64_t symbol_addr;
  Mapping* mapping;
};

struct CallChainEntry {
  uint64_t ip;
  SymbolEntry symbol;
};

struct CallChain {
  uint32_t nr;
  CallChainEntry* entries;
};

// Create a new instance,
// pass the instance to the other functions below.
ReportLib* CreateReportLib() EXPORT;
void DestroyReportLib(ReportLib* report_lib) EXPORT;

// Set log severity, different levels are:
// verbose, debug, info, warning, error, fatal.
bool SetLogSeverity(ReportLib* report_lib, const char* log_level) EXPORT;
bool SetSymfs(ReportLib* report_lib, const char* symfs_dir) EXPORT;
bool SetRecordFile(ReportLib* report_lib, const char* record_file) EXPORT;
bool SetKallsymsFile(ReportLib* report_lib, const char* kallsyms_file) EXPORT;
void ShowIpForUnknownSymbol(ReportLib* report_lib) EXPORT;

Sample* GetNextSample(ReportLib* report_lib) EXPORT;
Event* GetEventOfCurrentSample(ReportLib* report_lib) EXPORT;
SymbolEntry* GetSymbolOfCurrentSample(ReportLib* report_lib) EXPORT;
CallChain* GetCallChainOfCurrentSample(ReportLib* report_lib) EXPORT;

const char* GetBuildIdForPath(ReportLib* report_lib, const char* path) EXPORT;
}

struct EventAttrWithName {
  perf_event_attr attr;
  std::string name;
};

enum {
  UPDATE_FLAG_OF_SAMPLE = 1 << 0,
  UPDATE_FLAG_OF_EVENT = 1 << 1,
  UPDATE_FLAG_OF_SYMBOL = 1 << 2,
  UPDATE_FLAG_OF_CALLCHAIN = 1 << 3,
};

class ReportLib {
 public:
  ReportLib()
      : log_severity_(
            new android::base::ScopedLogSeverity(android::base::INFO)),
        record_filename_("perf.data"),
        current_thread_(nullptr),
        update_flag_(0)
         {}

  bool SetLogSeverity(const char* log_level);

  bool SetSymfs(const char* symfs_dir) { return Dso::SetSymFsDir(symfs_dir); }

  bool SetRecordFile(const char* record_file) {
    record_filename_ = record_file;
    return true;
  }

  bool SetKallsymsFile(const char* kallsyms_file);

  void ShowIpForUnknownSymbol() { thread_tree_.ShowIpForUnknownSymbol(); }

  Sample* GetNextSample();
  Event* GetEventOfCurrentSample();
  SymbolEntry* GetSymbolOfCurrentSample();
  CallChain* GetCallChainOfCurrentSample();

  const char* GetBuildIdForPath(const char* path);

 private:
  Sample* GetCurrentSample();
  bool OpenRecordFileIfNecessary();
  Mapping* AddMapping(const MapEntry& map);

  std::unique_ptr<android::base::ScopedLogSeverity> log_severity_;
  std::string record_filename_;
  std::unique_ptr<RecordFileReader> record_file_reader_;
  ThreadTree thread_tree_;
  std::unique_ptr<SampleRecord> current_record_;
  const ThreadEntry* current_thread_;
  Sample current_sample_;
  Event current_event_;
  SymbolEntry current_symbol_;
  CallChain current_callchain_;
  std::vector<std::unique_ptr<Mapping>> current_mappings_;
  std::vector<CallChainEntry> callchain_entries_;
  std::string build_id_string_;
  int update_flag_;
  std::vector<EventAttrWithName> event_attrs_;
};

bool ReportLib::SetLogSeverity(const char* log_level) {
  android::base::LogSeverity severity;
  if (!GetLogSeverity(log_level, &severity)) {
    LOG(ERROR) << "Unknown log severity: " << log_level;
    return false;
  }
  log_severity_ = nullptr;
  log_severity_.reset(new android::base::ScopedLogSeverity(severity));
  return true;
}

bool ReportLib::SetKallsymsFile(const char* kallsyms_file) {
  std::string kallsyms;
  if (!android::base::ReadFileToString(kallsyms_file, &kallsyms)) {
    LOG(WARNING) << "Failed to read in kallsyms file from " << kallsyms_file;
    return false;
  }
  Dso::SetKallsyms(std::move(kallsyms));
  return true;
}

bool ReportLib::OpenRecordFileIfNecessary() {
  if (record_file_reader_ == nullptr) {
    record_file_reader_ = RecordFileReader::CreateInstance(record_filename_);
    if (record_file_reader_ == nullptr) {
      return false;
    }
    record_file_reader_->LoadBuildIdAndFileFeatures(thread_tree_);
  }
  return true;
}

Sample* ReportLib::GetNextSample() {
  if (!OpenRecordFileIfNecessary()) {
    return nullptr;
  }
  while (true) {
    std::unique_ptr<Record> record;
    if (!record_file_reader_->ReadRecord(record)) {
      return nullptr;
    }
    if (record == nullptr) {
      return nullptr;
    }
    thread_tree_.Update(*record);
    if (record->type() == PERF_RECORD_SAMPLE) {
      current_record_.reset(static_cast<SampleRecord*>(record.release()));
      break;
    }
  }
  update_flag_ = 0;
  current_mappings_.clear();
  return GetCurrentSample();
}

Sample* ReportLib::GetCurrentSample() {
  if (!(update_flag_ & UPDATE_FLAG_OF_SAMPLE)) {
    SampleRecord& r = *current_record_;
    current_sample_.ip = r.ip_data.ip;
    current_sample_.pid = r.tid_data.pid;
    current_sample_.tid = r.tid_data.tid;
    current_thread_ =
        thread_tree_.FindThreadOrNew(r.tid_data.pid, r.tid_data.tid);
    current_sample_.thread_comm = current_thread_->comm;
    current_sample_.time = r.time_data.time;
    current_sample_.in_kernel = r.InKernel();
    current_sample_.cpu = r.cpu_data.cpu;
    current_sample_.period = r.period_data.period;
    update_flag_ |= UPDATE_FLAG_OF_SAMPLE;
  }
  return &current_sample_;
}

Event* ReportLib::GetEventOfCurrentSample() {
  if (!(update_flag_ & UPDATE_FLAG_OF_EVENT)) {
    if (event_attrs_.empty()) {
      std::vector<EventAttrWithId> attrs = record_file_reader_->AttrSection();
      for (const auto& attr_with_id : attrs) {
        EventAttrWithName attr;
        attr.attr = *attr_with_id.attr;
        attr.name = GetEventNameByAttr(attr.attr);
        event_attrs_.push_back(attr);
      }
    }
    size_t attr_index =
        record_file_reader_->GetAttrIndexOfRecord(current_record_.get());
    current_event_.name = event_attrs_[attr_index].name.c_str();
    update_flag_ |= UPDATE_FLAG_OF_EVENT;
  }
  return &current_event_;
}

SymbolEntry* ReportLib::GetSymbolOfCurrentSample() {
  if (!(update_flag_ & UPDATE_FLAG_OF_SYMBOL)) {
    SampleRecord& r = *current_record_;
    const MapEntry* map =
        thread_tree_.FindMap(current_thread_, r.ip_data.ip, r.InKernel());
    uint64_t vaddr_in_file;
    const Symbol* symbol =
        thread_tree_.FindSymbol(map, r.ip_data.ip, &vaddr_in_file);
    current_symbol_.dso_name = map->dso->Path().c_str();
    current_symbol_.vaddr_in_file = vaddr_in_file;
    current_symbol_.symbol_name = symbol->DemangledName();
    current_symbol_.symbol_addr = symbol->addr;
    current_symbol_.mapping = AddMapping(*map);
    update_flag_ |= UPDATE_FLAG_OF_SYMBOL;
  }
  return &current_symbol_;
}

CallChain* ReportLib::GetCallChainOfCurrentSample() {
  if (!(update_flag_ & UPDATE_FLAG_OF_CALLCHAIN)) {
    SampleRecord& r = *current_record_;
    callchain_entries_.clear();

    if (r.sample_type & PERF_SAMPLE_CALLCHAIN) {
      bool first_ip = true;
      bool in_kernel = r.InKernel();
      for (uint64_t i = 0; i < r.callchain_data.ip_nr; ++i) {
        uint64_t ip = r.callchain_data.ips[i];
        if (ip >= PERF_CONTEXT_MAX) {
          switch (ip) {
            case PERF_CONTEXT_KERNEL:
              in_kernel = true;
              break;
            case PERF_CONTEXT_USER:
              in_kernel = false;
              break;
            default:
              LOG(DEBUG) << "Unexpected perf_context in callchain: " << std::hex
                         << ip;
          }
        } else {
          if (first_ip) {
            first_ip = false;
            // Remove duplication with sample ip.
            if (ip == r.ip_data.ip) {
              continue;
            }
          }
          const MapEntry* map =
              thread_tree_.FindMap(current_thread_, ip, in_kernel);
          uint64_t vaddr_in_file;
          const Symbol* symbol =
              thread_tree_.FindSymbol(map, ip, &vaddr_in_file);
          CallChainEntry entry;
          entry.ip = ip;
          entry.symbol.dso_name = map->dso->Path().c_str();
          entry.symbol.vaddr_in_file = vaddr_in_file;
          entry.symbol.symbol_name = symbol->DemangledName();
          entry.symbol.symbol_addr = symbol->addr;
          entry.symbol.mapping = AddMapping(*map);
          callchain_entries_.push_back(entry);
        }
      }
    }
    current_callchain_.nr = callchain_entries_.size();
    current_callchain_.entries = callchain_entries_.data();
    update_flag_ |= UPDATE_FLAG_OF_CALLCHAIN;
  }
  return &current_callchain_;
}

Mapping* ReportLib::AddMapping(const MapEntry& map) {
  current_mappings_.emplace_back(std::unique_ptr<Mapping>(new Mapping));
  Mapping* mapping = current_mappings_.back().get();
  mapping->start = map.start_addr;
  mapping->end = map.start_addr + map.len;
  mapping->pgoff = map.pgoff;
  return mapping;
}

const char* ReportLib::GetBuildIdForPath(const char* path) {
  if (!OpenRecordFileIfNecessary()) {
    build_id_string_.clear();
    return build_id_string_.c_str();
  }
  BuildId build_id = Dso::FindExpectedBuildIdForPath(path);
  if (build_id.IsEmpty()) {
    build_id_string_.clear();
  } else {
    build_id_string_ = build_id.ToString();
  }
  return build_id_string_.c_str();
}

// Exported methods working with a client created instance
ReportLib* CreateReportLib() {
  return new ReportLib();
}

void DestroyReportLib(ReportLib* report_lib) {
  delete report_lib;
}

bool SetLogSeverity(ReportLib* report_lib, const char* log_level) {
  return report_lib->SetLogSeverity(log_level);
}

bool SetSymfs(ReportLib* report_lib, const char* symfs_dir) {
  return report_lib->SetSymfs(symfs_dir);
}

bool SetRecordFile(ReportLib* report_lib, const char* record_file) {
  return report_lib->SetRecordFile(record_file);
}

void ShowIpForUnknownSymbol(ReportLib* report_lib) {
  return report_lib->ShowIpForUnknownSymbol();
}

bool SetKallsymsFile(ReportLib* report_lib, const char* kallsyms_file) {
  return report_lib->SetKallsymsFile(kallsyms_file);
}

Sample* GetNextSample(ReportLib* report_lib) {
  return report_lib->GetNextSample();
}

Event* GetEventOfCurrentSample(ReportLib* report_lib) {
  return report_lib->GetEventOfCurrentSample();
}

SymbolEntry* GetSymbolOfCurrentSample(ReportLib* report_lib) {
  return report_lib->GetSymbolOfCurrentSample();
}

CallChain* GetCallChainOfCurrentSample(ReportLib* report_lib) {
  return report_lib->GetCallChainOfCurrentSample();
}

const char* GetBuildIdForPath(ReportLib* report_lib, const char* path) {
  return report_lib->GetBuildIdForPath(path);
}
