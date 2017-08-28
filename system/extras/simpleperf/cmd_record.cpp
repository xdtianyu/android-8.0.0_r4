/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include <libgen.h>
#include <signal.h>
#include <sys/prctl.h>
#include <sys/utsname.h>
#include <unistd.h>
#include <set>
#include <string>
#include <unordered_map>
#include <vector>

#include <android-base/logging.h>
#include <android-base/file.h>
#include <android-base/parsedouble.h>
#include <android-base/parseint.h>
#include <android-base/strings.h>

#include "command.h"
#include "dwarf_unwind.h"
#include "environment.h"
#include "event_selection_set.h"
#include "event_type.h"
#include "IOEventLoop.h"
#include "perf_clock.h"
#include "read_apk.h"
#include "read_elf.h"
#include "record.h"
#include "record_file.h"
#include "thread_tree.h"
#include "tracing.h"
#include "utils.h"
#include "workload.h"

static std::string default_measured_event_type = "cpu-cycles";

static std::unordered_map<std::string, uint64_t> branch_sampling_type_map = {
    {"u", PERF_SAMPLE_BRANCH_USER},
    {"k", PERF_SAMPLE_BRANCH_KERNEL},
    {"any", PERF_SAMPLE_BRANCH_ANY},
    {"any_call", PERF_SAMPLE_BRANCH_ANY_CALL},
    {"any_ret", PERF_SAMPLE_BRANCH_ANY_RETURN},
    {"ind_call", PERF_SAMPLE_BRANCH_IND_CALL},
};

// The max size of records dumped by kernel is 65535, and dump stack size
// should be a multiply of 8, so MAX_DUMP_STACK_SIZE is 65528.
constexpr uint32_t MAX_DUMP_STACK_SIZE = 65528;

// The max allowed pages in mapped buffer is decided by rlimit(RLIMIT_MEMLOCK).
// Here 1024 is a desired value for pages in mapped buffer. If mapped
// successfully, the buffer size = 1024 * 4K (page size) = 4M.
constexpr size_t DESIRED_PAGES_IN_MAPPED_BUFFER = 1024;

class RecordCommand : public Command {
 public:
  RecordCommand()
      : Command(
            "record", "record sampling info in perf.data",
            // clang-format off
"Usage: simpleperf record [options] [command [command-args]]\n"
"       Gather sampling information of running [command]. And -a/-p/-t option\n"
"       can be used to change target of sampling information.\n"
"-a     System-wide collection.\n"
"-b     Enable take branch stack sampling. Same as '-j any'\n"
"-c count     Set event sample period. It means recording one sample when\n"
"             [count] events happen. Can't be used with -f/-F option.\n"
"             For tracepoint events, the default option is -c 1.\n"
"--call-graph fp | dwarf[,<dump_stack_size>]\n"
"             Enable call graph recording. Use frame pointer or dwarf debug\n"
"             frame as the method to parse call graph in stack.\n"
"             Default is dwarf,65528.\n"
"--cpu cpu_item1,cpu_item2,...\n"
"             Collect samples only on the selected cpus. cpu_item can be cpu\n"
"             number like 1, or cpu range like 0-3.\n"
"--dump-symbols  Dump symbols in perf.data. By default perf.data doesn't contain\n"
"                symbol information for samples. This option is used when there\n"
"                is no symbol information in report environment.\n"
"--duration time_in_sec  Monitor for time_in_sec seconds instead of running\n"
"                        [command]. Here time_in_sec may be any positive\n"
"                        floating point number.\n"
"-e event1[:modifier1],event2[:modifier2],...\n"
"             Select the event list to sample. Use `simpleperf list` to find\n"
"             all possible event names. Modifiers can be added to define how\n"
"             the event should be monitored.\n"
"             Possible modifiers are:\n"
"                u - monitor user space events only\n"
"                k - monitor kernel space events only\n"
"-f freq      Set event sample frequency. It means recording at most [freq]\n"
"             samples every second. For non-tracepoint events, the default\n"
"             option is -f 4000.\n"
"-F freq      Same as '-f freq'.\n"
"-g           Same as '--call-graph dwarf'.\n"
"--group event1[:modifier],event2[:modifier2],...\n"
"             Similar to -e option. But events specified in the same --group\n"
"             option are monitored as a group, and scheduled in and out at the\n"
"             same time.\n"
"-j branch_filter1,branch_filter2,...\n"
"             Enable taken branch stack sampling. Each sample captures a series\n"
"             of consecutive taken branches.\n"
"             The following filters are defined:\n"
"                any: any type of branch\n"
"                any_call: any function call or system call\n"
"                any_ret: any function return or system call return\n"
"                ind_call: any indirect branch\n"
"                u: only when the branch target is at the user level\n"
"                k: only when the branch target is in the kernel\n"
"             This option requires at least one branch type among any, any_call,\n"
"             any_ret, ind_call.\n"
"-m mmap_pages   Set the size of the buffer used to receiving sample data from\n"
"                the kernel. It should be a power of 2. If not set, the max\n"
"                possible value <= 1024 will be used.\n"
"--no-dump-kernel-symbols  Don't dump kernel symbols in perf.data. By default\n"
"                          kernel symbols will be dumped when needed.\n"
"--no-inherit  Don't record created child threads/processes.\n"
"--no-unwind   If `--call-graph dwarf` option is used, then the user's stack\n"
"              will be unwound by default. Use this option to disable the\n"
"              unwinding of the user's stack.\n"
"-o record_file_name    Set record file name, default is perf.data.\n"
"-p pid1,pid2,...       Record events on existing processes. Mutually exclusive\n"
"                       with -a.\n"
"--post-unwind  If `--call-graph dwarf` option is used, then the user's stack\n"
"               will be unwound while recording by default. But it may lose\n"
"               records as stacking unwinding can be time consuming. Use this\n"
"               option to unwind the user's stack after recording.\n"
"--symfs <dir>    Look for files with symbols relative to this directory.\n"
"                 This option is used to provide files with symbol table and\n"
"                 debug information, which are used by --dump-symbols and -g.\n"
"-t tid1,tid2,... Record events on existing threads. Mutually exclusive with -a.\n"
            // clang-format on
            ),
        use_sample_freq_(false),
        sample_freq_(0),
        use_sample_period_(false),
        sample_period_(0),
        system_wide_collection_(false),
        branch_sampling_(0),
        fp_callchain_sampling_(false),
        dwarf_callchain_sampling_(false),
        dump_stack_size_in_dwarf_sampling_(MAX_DUMP_STACK_SIZE),
        unwind_dwarf_callchain_(true),
        post_unwind_(false),
        child_inherit_(true),
        duration_in_sec_(0),
        can_dump_kernel_symbols_(true),
        dump_symbols_(false),
        event_selection_set_(false),
        mmap_page_range_(std::make_pair(1, DESIRED_PAGES_IN_MAPPED_BUFFER)),
        record_filename_("perf.data"),
        start_sampling_time_in_ns_(0),
        sample_record_count_(0),
        lost_record_count_(0) {
    // Stop profiling if parent exits.
    prctl(PR_SET_PDEATHSIG, SIGHUP, 0, 0, 0);
  }

  bool Run(const std::vector<std::string>& args);

 private:
  bool ParseOptions(const std::vector<std::string>& args,
                    std::vector<std::string>* non_option_args);
  bool SetEventSelectionFlags();
  bool CreateAndInitRecordFile();
  std::unique_ptr<RecordFileWriter> CreateRecordFile(
      const std::string& filename);
  bool DumpKernelSymbol();
  bool DumpTracingData();
  bool DumpKernelAndModuleMmaps(const perf_event_attr& attr, uint64_t event_id);
  bool DumpThreadCommAndMmaps(const perf_event_attr& attr, uint64_t event_id);
  bool ProcessRecord(Record* record);
  void UpdateRecordForEmbeddedElfPath(Record* record);
  bool UnwindRecord(Record* record);
  bool PostUnwind(const std::vector<std::string>& args);
  bool DumpAdditionalFeatures(const std::vector<std::string>& args);
  bool DumpBuildIdFeature();
  bool DumpFileFeature();
  void CollectHitFileInfo(const SampleRecord& r);

  bool use_sample_freq_;
  uint64_t sample_freq_;  // Sample 'sample_freq_' times per second.
  bool use_sample_period_;
  uint64_t sample_period_;  // Sample once when 'sample_period_' events occur.

  bool system_wide_collection_;
  uint64_t branch_sampling_;
  bool fp_callchain_sampling_;
  bool dwarf_callchain_sampling_;
  uint32_t dump_stack_size_in_dwarf_sampling_;
  bool unwind_dwarf_callchain_;
  bool post_unwind_;
  bool child_inherit_;
  double duration_in_sec_;
  bool can_dump_kernel_symbols_;
  bool dump_symbols_;
  std::vector<int> cpus_;
  EventSelectionSet event_selection_set_;

  std::pair<size_t, size_t> mmap_page_range_;

  ThreadTree thread_tree_;
  std::string record_filename_;
  std::unique_ptr<RecordFileWriter> record_file_writer_;

  uint64_t start_sampling_time_in_ns_;  // nanoseconds from machine starting

  uint64_t sample_record_count_;
  uint64_t lost_record_count_;
};

bool RecordCommand::Run(const std::vector<std::string>& args) {
  if (!CheckPerfEventLimit()) {
    return false;
  }
  if (!InitPerfClock()) {
    return false;
  }

  // 1. Parse options, and use default measured event type if not given.
  std::vector<std::string> workload_args;
  if (!ParseOptions(args, &workload_args)) {
    return false;
  }
  if (event_selection_set_.empty()) {
    if (!event_selection_set_.AddEventType(default_measured_event_type)) {
      return false;
    }
  }
  if (!SetEventSelectionFlags()) {
    return false;
  }
  ScopedCurrentArch scoped_arch(GetMachineArch());

  // 2. Create workload.
  std::unique_ptr<Workload> workload;
  if (!workload_args.empty()) {
    workload = Workload::CreateWorkload(workload_args);
    if (workload == nullptr) {
      return false;
    }
  }
  bool need_to_check_targets = false;
  if (system_wide_collection_) {
    event_selection_set_.AddMonitoredThreads({-1});
  } else if (!event_selection_set_.HasMonitoredTarget()) {
    if (workload != nullptr) {
      event_selection_set_.AddMonitoredProcesses({workload->GetPid()});
      event_selection_set_.SetEnableOnExec(true);
      if (event_selection_set_.HasInplaceSampler()) {
        // Start worker early, because the worker process has to setup inplace-sampler server
        // before we try to connect it.
        if (!workload->Start()) {
          return false;
        }
      }
    } else {
      LOG(ERROR)
          << "No threads to monitor. Try `simpleperf help record` for help";
      return false;
    }
  } else {
    need_to_check_targets = true;
  }

  // 3. Open perf_event_files, create mapped buffers for perf_event_files.
  if (!event_selection_set_.OpenEventFiles(cpus_)) {
    return false;
  }
  if (!event_selection_set_.MmapEventFiles(mmap_page_range_.first,
                                           mmap_page_range_.second)) {
    return false;
  }

  // 4. Create perf.data.
  if (!CreateAndInitRecordFile()) {
    return false;
  }

  // 5. Add read/signal/periodic Events.
  auto callback =
      std::bind(&RecordCommand::ProcessRecord, this, std::placeholders::_1);
  if (!event_selection_set_.PrepareToReadMmapEventData(callback)) {
    return false;
  }
  if (!event_selection_set_.HandleCpuHotplugEvents(cpus_)) {
    return false;
  }
  if (need_to_check_targets && !event_selection_set_.StopWhenNoMoreTargets()) {
    return false;
  }
  IOEventLoop* loop = event_selection_set_.GetIOEventLoop();
  if (!loop->AddSignalEvents({SIGCHLD, SIGINT, SIGTERM, SIGHUP},
                             [&]() { return loop->ExitLoop(); })) {
    return false;
  }
  if (duration_in_sec_ != 0) {
    if (!loop->AddPeriodicEvent(SecondToTimeval(duration_in_sec_),
                                [&]() { return loop->ExitLoop(); })) {
      return false;
    }
  }

  // 6. Write records in mapped buffers of perf_event_files to output file while
  //    workload is running.
  start_sampling_time_in_ns_ = GetPerfClock();
  LOG(VERBOSE) << "start_sampling_time is " << start_sampling_time_in_ns_
               << " ns";
  if (workload != nullptr && !workload->IsStarted() && !workload->Start()) {
    return false;
  }
  if (!loop->RunLoop()) {
    return false;
  }
  if (!event_selection_set_.FinishReadMmapEventData()) {
    return false;
  }

  // 7. Dump additional features, and close record file.
  if (!DumpAdditionalFeatures(args)) {
    return false;
  }
  if (!record_file_writer_->Close()) {
    return false;
  }

  // 8. Unwind dwarf callchain.
  if (post_unwind_) {
    if (!PostUnwind(args)) {
      return false;
    }
  }

  // 9. Show brief record result.
  LOG(INFO) << "Samples recorded: " << sample_record_count_
            << ". Samples lost: " << lost_record_count_ << ".";
  if (sample_record_count_ + lost_record_count_ != 0) {
    double lost_percent = static_cast<double>(lost_record_count_) /
                          (lost_record_count_ + sample_record_count_);
    constexpr double LOST_PERCENT_WARNING_BAR = 0.1;
    if (lost_percent >= LOST_PERCENT_WARNING_BAR) {
      LOG(WARNING) << "Lost " << (lost_percent * 100) << "% of samples, "
                   << "consider increasing mmap_pages(-m), "
                   << "or decreasing sample frequency(-f), "
                   << "or increasing sample period(-c).";
    }
  }
  return true;
}

bool RecordCommand::ParseOptions(const std::vector<std::string>& args,
                                 std::vector<std::string>* non_option_args) {
  size_t i;
  for (i = 0; i < args.size() && !args[i].empty() && args[i][0] == '-'; ++i) {
    if (args[i] == "-a") {
      system_wide_collection_ = true;
    } else if (args[i] == "-b") {
      branch_sampling_ = branch_sampling_type_map["any"];
    } else if (args[i] == "-c") {
      if (!NextArgumentOrError(args, &i)) {
        return false;
      }
      char* endptr;
      sample_period_ = strtoull(args[i].c_str(), &endptr, 0);
      if (*endptr != '\0' || sample_period_ == 0) {
        LOG(ERROR) << "Invalid sample period: '" << args[i] << "'";
        return false;
      }
      use_sample_period_ = true;
    } else if (args[i] == "--call-graph") {
      if (!NextArgumentOrError(args, &i)) {
        return false;
      }
      std::vector<std::string> strs = android::base::Split(args[i], ",");
      if (strs[0] == "fp") {
        fp_callchain_sampling_ = true;
        dwarf_callchain_sampling_ = false;
      } else if (strs[0] == "dwarf") {
        fp_callchain_sampling_ = false;
        dwarf_callchain_sampling_ = true;
        if (strs.size() > 1) {
          char* endptr;
          uint64_t size = strtoull(strs[1].c_str(), &endptr, 0);
          if (*endptr != '\0' || size > UINT_MAX) {
            LOG(ERROR) << "invalid dump stack size in --call-graph option: "
                       << strs[1];
            return false;
          }
          if ((size & 7) != 0) {
            LOG(ERROR) << "dump stack size " << size
                       << " is not 8-byte aligned.";
            return false;
          }
          if (size >= MAX_DUMP_STACK_SIZE) {
            LOG(ERROR) << "dump stack size " << size
                       << " is bigger than max allowed size "
                       << MAX_DUMP_STACK_SIZE << ".";
            return false;
          }
          dump_stack_size_in_dwarf_sampling_ = static_cast<uint32_t>(size);
        }
      } else {
        LOG(ERROR) << "unexpected argument for --call-graph option: "
                   << args[i];
        return false;
      }
    } else if (args[i] == "--cpu") {
      if (!NextArgumentOrError(args, &i)) {
        return false;
      }
      cpus_ = GetCpusFromString(args[i]);
    } else if (args[i] == "--dump-symbols") {
      dump_symbols_ = true;
    } else if (args[i] == "--duration") {
      if (!NextArgumentOrError(args, &i)) {
        return false;
      }
      if (!android::base::ParseDouble(args[i].c_str(), &duration_in_sec_,
                                      1e-9)) {
        LOG(ERROR) << "Invalid duration: " << args[i].c_str();
        return false;
      }
    } else if (args[i] == "-e") {
      if (!NextArgumentOrError(args, &i)) {
        return false;
      }
      std::vector<std::string> event_types = android::base::Split(args[i], ",");
      for (auto& event_type : event_types) {
        if (!event_selection_set_.AddEventType(event_type)) {
          return false;
        }
      }
    } else if (args[i] == "-f" || args[i] == "-F") {
      if (!NextArgumentOrError(args, &i)) {
        return false;
      }
      if (!android::base::ParseUint(args[i].c_str(), &sample_freq_)) {
        LOG(ERROR) << "Invalid sample frequency: " << args[i];
        return false;
      }
      if (!CheckSampleFrequency(sample_freq_)) {
        return false;
      }
      use_sample_freq_ = true;
    } else if (args[i] == "-g") {
      fp_callchain_sampling_ = false;
      dwarf_callchain_sampling_ = true;
    } else if (args[i] == "--group") {
      if (!NextArgumentOrError(args, &i)) {
        return false;
      }
      std::vector<std::string> event_types = android::base::Split(args[i], ",");
      if (!event_selection_set_.AddEventGroup(event_types)) {
        return false;
      }
    } else if (args[i] == "-j") {
      if (!NextArgumentOrError(args, &i)) {
        return false;
      }
      std::vector<std::string> branch_sampling_types =
          android::base::Split(args[i], ",");
      for (auto& type : branch_sampling_types) {
        auto it = branch_sampling_type_map.find(type);
        if (it == branch_sampling_type_map.end()) {
          LOG(ERROR) << "unrecognized branch sampling filter: " << type;
          return false;
        }
        branch_sampling_ |= it->second;
      }
    } else if (args[i] == "-m") {
      if (!NextArgumentOrError(args, &i)) {
        return false;
      }
      char* endptr;
      uint64_t pages = strtoull(args[i].c_str(), &endptr, 0);
      if (*endptr != '\0' || !IsPowerOfTwo(pages)) {
        LOG(ERROR) << "Invalid mmap_pages: '" << args[i] << "'";
        return false;
      }
      mmap_page_range_.first = mmap_page_range_.second = pages;
    } else if (args[i] == "--no-dump-kernel-symbols") {
      can_dump_kernel_symbols_ = false;
    } else if (args[i] == "--no-inherit") {
      child_inherit_ = false;
    } else if (args[i] == "--no-unwind") {
      unwind_dwarf_callchain_ = false;
    } else if (args[i] == "-o") {
      if (!NextArgumentOrError(args, &i)) {
        return false;
      }
      record_filename_ = args[i];
    } else if (args[i] == "-p") {
      if (!NextArgumentOrError(args, &i)) {
        return false;
      }
      std::set<pid_t> pids;
      if (!GetValidThreadsFromThreadString(args[i], &pids)) {
        return false;
      }
      event_selection_set_.AddMonitoredProcesses(pids);
    } else if (args[i] == "--post-unwind") {
      post_unwind_ = true;
    } else if (args[i] == "--symfs") {
      if (!NextArgumentOrError(args, &i)) {
        return false;
      }
      if (!Dso::SetSymFsDir(args[i])) {
        return false;
      }
    } else if (args[i] == "-t") {
      if (!NextArgumentOrError(args, &i)) {
        return false;
      }
      std::set<pid_t> tids;
      if (!GetValidThreadsFromThreadString(args[i], &tids)) {
        return false;
      }
      event_selection_set_.AddMonitoredThreads(tids);
    } else {
      ReportUnknownOption(args, i);
      return false;
    }
  }

  if (use_sample_freq_ && use_sample_period_) {
    LOG(ERROR) << "-f option can't be used with -c option.";
    return false;
  }

  if (!dwarf_callchain_sampling_) {
    if (!unwind_dwarf_callchain_) {
      LOG(ERROR)
          << "--no-unwind is only used with `--call-graph dwarf` option.";
      return false;
    }
    unwind_dwarf_callchain_ = false;
  }
  if (post_unwind_) {
    if (!dwarf_callchain_sampling_) {
      LOG(ERROR)
          << "--post-unwind is only used with `--call-graph dwarf` option.";
      return false;
    }
    if (!unwind_dwarf_callchain_) {
      LOG(ERROR) << "--post-unwind can't be used with `--no-unwind` option.";
      return false;
    }
  }

  if (system_wide_collection_ && event_selection_set_.HasMonitoredTarget()) {
    LOG(ERROR) << "Record system wide and existing processes/threads can't be "
                  "used at the same time.";
    return false;
  }

  if (system_wide_collection_ && !IsRoot()) {
    LOG(ERROR) << "System wide profiling needs root privilege.";
    return false;
  }

  if (dump_symbols_ && can_dump_kernel_symbols_) {
    // No need to dump kernel symbols as we will dump all required symbols.
    can_dump_kernel_symbols_ = false;
  }

  non_option_args->clear();
  for (; i < args.size(); ++i) {
    non_option_args->push_back(args[i]);
  }
  return true;
}

bool RecordCommand::SetEventSelectionFlags() {
  if (use_sample_freq_) {
    event_selection_set_.SetSampleFreq(sample_freq_);
  } else if (use_sample_period_) {
    event_selection_set_.SetSamplePeriod(sample_period_);
  } else {
    event_selection_set_.UseDefaultSampleFreq();
  }
  event_selection_set_.SampleIdAll();
  if (!event_selection_set_.SetBranchSampling(branch_sampling_)) {
    return false;
  }
  if (fp_callchain_sampling_) {
    event_selection_set_.EnableFpCallChainSampling();
  } else if (dwarf_callchain_sampling_) {
    if (!event_selection_set_.EnableDwarfCallChainSampling(
            dump_stack_size_in_dwarf_sampling_)) {
      return false;
    }
  }
  event_selection_set_.SetInherit(child_inherit_);
  return true;
}

bool RecordCommand::CreateAndInitRecordFile() {
  record_file_writer_ = CreateRecordFile(record_filename_);
  if (record_file_writer_ == nullptr) {
    return false;
  }
  // Use first perf_event_attr and first event id to dump mmap and comm records.
  EventAttrWithId attr_id = event_selection_set_.GetEventAttrWithId()[0];
  if (!DumpKernelSymbol()) {
    return false;
  }
  if (!DumpTracingData()) {
    return false;
  }
  if (!DumpKernelAndModuleMmaps(*attr_id.attr, attr_id.ids[0])) {
    return false;
  }
  if (!DumpThreadCommAndMmaps(*attr_id.attr, attr_id.ids[0])) {
    return false;
  }
  return true;
}

std::unique_ptr<RecordFileWriter> RecordCommand::CreateRecordFile(
    const std::string& filename) {
  std::unique_ptr<RecordFileWriter> writer =
      RecordFileWriter::CreateInstance(filename);
  if (writer == nullptr) {
    return nullptr;
  }

  if (!writer->WriteAttrSection(event_selection_set_.GetEventAttrWithId())) {
    return nullptr;
  }
  return writer;
}

bool RecordCommand::DumpKernelSymbol() {
  if (can_dump_kernel_symbols_) {
    std::string kallsyms;
    if (event_selection_set_.NeedKernelSymbol() &&
        CheckKernelSymbolAddresses()) {
      if (!android::base::ReadFileToString("/proc/kallsyms", &kallsyms)) {
        PLOG(ERROR) << "failed to read /proc/kallsyms";
        return false;
      }
      KernelSymbolRecord r(kallsyms);
      if (!ProcessRecord(&r)) {
        return false;
      }
    }
  }
  return true;
}

bool RecordCommand::DumpTracingData() {
  std::vector<const EventType*> tracepoint_event_types =
      event_selection_set_.GetTracepointEvents();
  if (tracepoint_event_types.empty()) {
    return true;  // No need to dump tracing data.
  }
  std::vector<char> tracing_data;
  if (!GetTracingData(tracepoint_event_types, &tracing_data)) {
    return false;
  }
  TracingDataRecord record(tracing_data);
  if (!ProcessRecord(&record)) {
    return false;
  }
  return true;
}

bool RecordCommand::DumpKernelAndModuleMmaps(const perf_event_attr& attr,
                                             uint64_t event_id) {
  KernelMmap kernel_mmap;
  std::vector<KernelMmap> module_mmaps;
  GetKernelAndModuleMmaps(&kernel_mmap, &module_mmaps);

  MmapRecord mmap_record(attr, true, UINT_MAX, 0, kernel_mmap.start_addr,
                         kernel_mmap.len, 0, kernel_mmap.filepath, event_id);
  if (!ProcessRecord(&mmap_record)) {
    return false;
  }
  for (auto& module_mmap : module_mmaps) {
    MmapRecord mmap_record(attr, true, UINT_MAX, 0, module_mmap.start_addr,
                           module_mmap.len, 0, module_mmap.filepath, event_id);
    if (!ProcessRecord(&mmap_record)) {
      return false;
    }
  }
  return true;
}

bool RecordCommand::DumpThreadCommAndMmaps(const perf_event_attr& attr,
                                           uint64_t event_id) {
  // Decide which processes and threads to dump.
  // For system_wide profiling, dump all threads.
  // For non system wide profiling, build dump_threads.
  bool all_threads = system_wide_collection_;
  std::set<pid_t> dump_threads = event_selection_set_.GetMonitoredThreads();
  for (const auto& pid : event_selection_set_.GetMonitoredProcesses()) {
    std::vector<pid_t> tids = GetThreadsInProcess(pid);
    dump_threads.insert(tids.begin(), tids.end());
  }

  // Collect processes to dump.
  std::vector<pid_t> processes;
  if (all_threads) {
    processes = GetAllProcesses();
  } else {
    std::set<pid_t> process_set;
    for (const auto& tid : dump_threads) {
      pid_t pid;
      if (!GetProcessForThread(tid, &pid)) {
        continue;
      }
      process_set.insert(pid);
    }
    processes.insert(processes.end(), process_set.begin(), process_set.end());
  }

  // Dump each process and its threads.
  for (auto& pid : processes) {
    // Dump mmap records.
    std::vector<ThreadMmap> thread_mmaps;
    if (!GetThreadMmapsInProcess(pid, &thread_mmaps)) {
      // The process may exit before we get its info.
      continue;
    }
    for (const auto& map : thread_mmaps) {
      if (map.executable == 0) {
        continue;  // No need to dump non-executable mmap info.
      }
      MmapRecord record(attr, false, pid, pid, map.start_addr, map.len,
                        map.pgoff, map.name, event_id);
      if (!ProcessRecord(&record)) {
        return false;
      }
    }
    // Dump process name.
    std::string name;
    if (GetThreadName(pid, &name)) {
      CommRecord record(attr, pid, pid, name, event_id, 0);
      if (!ProcessRecord(&record)) {
        return false;
      }
    }
    // Dump thread info.
    std::vector<pid_t> threads = GetThreadsInProcess(pid);
    for (const auto& tid : threads) {
      if (tid == pid) {
        continue;
      }
      if (all_threads || dump_threads.find(tid) != dump_threads.end()) {
        ForkRecord fork_record(attr, pid, tid, pid, pid, event_id);
        if (!ProcessRecord(&fork_record)) {
          return false;
        }
        if (GetThreadName(tid, &name)) {
          CommRecord comm_record(attr, pid, tid, name, event_id, 0);
          if (!ProcessRecord(&comm_record)) {
            return false;
          }
        }
      }
    }
  }
  return true;
}

bool RecordCommand::ProcessRecord(Record* record) {
  if (system_wide_collection_ && record->type() == PERF_RECORD_SAMPLE) {
    auto& r = *static_cast<SampleRecord*>(record);
    // Omit samples get before start sampling time.
    if (r.time_data.time < start_sampling_time_in_ns_) {
      return true;
    }
  }
  UpdateRecordForEmbeddedElfPath(record);
  if (unwind_dwarf_callchain_ && !post_unwind_) {
    thread_tree_.Update(*record);
    if (!UnwindRecord(record)) {
      return false;
    }
  }
  if (record->type() == PERF_RECORD_SAMPLE) {
    sample_record_count_++;
  } else if (record->type() == PERF_RECORD_LOST) {
    lost_record_count_ += static_cast<LostRecord*>(record)->lost;
  }
  bool result = record_file_writer_->WriteRecord(*record);
  return result;
}

template <class RecordType>
void UpdateMmapRecordForEmbeddedElfPath(RecordType* record) {
  RecordType& r = *record;
  if (!r.InKernel() && r.data->pgoff != 0) {
    // For the case of a shared library "foobar.so" embedded
    // inside an APK, we rewrite the original MMAP from
    // ["path.apk" offset=X] to ["path.apk!/foobar.so" offset=W]
    // so as to make the library name explicit. This update is
    // done here (as part of the record operation) as opposed to
    // on the host during the report, since we want to report
    // the correct library name even if the the APK in question
    // is not present on the host. The new offset W is
    // calculated to be with respect to the start of foobar.so,
    // not to the start of path.apk.
    EmbeddedElf* ee =
        ApkInspector::FindElfInApkByOffset(r.filename, r.data->pgoff);
    if (ee != nullptr) {
      // Compute new offset relative to start of elf in APK.
      auto data = *r.data;
      data.pgoff -= ee->entry_offset();
      r.SetDataAndFilename(data, GetUrlInApk(r.filename, ee->entry_name()));
    }
  }
}

void RecordCommand::UpdateRecordForEmbeddedElfPath(Record* record) {
  if (record->type() == PERF_RECORD_MMAP) {
    UpdateMmapRecordForEmbeddedElfPath(static_cast<MmapRecord*>(record));
  } else if (record->type() == PERF_RECORD_MMAP2) {
    UpdateMmapRecordForEmbeddedElfPath(static_cast<Mmap2Record*>(record));
  }
}

bool RecordCommand::UnwindRecord(Record* record) {
  if (record->type() == PERF_RECORD_SAMPLE) {
    SampleRecord& r = *static_cast<SampleRecord*>(record);
    if ((r.sample_type & PERF_SAMPLE_CALLCHAIN) &&
        (r.sample_type & PERF_SAMPLE_REGS_USER) &&
        (r.regs_user_data.reg_mask != 0) &&
        (r.sample_type & PERF_SAMPLE_STACK_USER) &&
        (r.GetValidStackSize() > 0)) {
      ThreadEntry* thread =
          thread_tree_.FindThreadOrNew(r.tid_data.pid, r.tid_data.tid);
      RegSet regs = CreateRegSet(r.regs_user_data.abi,
                                 r.regs_user_data.reg_mask,
                                 r.regs_user_data.regs);
      // Normally do strict arch check when unwinding stack. But allow unwinding
      // 32-bit processes on 64-bit devices for system wide profiling.
      bool strict_arch_check = !system_wide_collection_;
      std::vector<uint64_t> unwind_ips =
          UnwindCallChain(r.regs_user_data.abi, *thread, regs,
                          r.stack_user_data.data,
                          r.GetValidStackSize(), strict_arch_check);
      r.ReplaceRegAndStackWithCallChain(unwind_ips);
    }
  }
  return true;
}

bool RecordCommand::PostUnwind(const std::vector<std::string>& args) {
  thread_tree_.ClearThreadAndMap();
  std::unique_ptr<RecordFileReader> reader =
      RecordFileReader::CreateInstance(record_filename_);
  if (reader == nullptr) {
    return false;
  }
  std::string tmp_filename = record_filename_ + ".tmp";
  record_file_writer_ = CreateRecordFile(tmp_filename);
  if (record_file_writer_ == nullptr) {
    return false;
  }
  bool result = reader->ReadDataSection(
      [this](std::unique_ptr<Record> record) {
        thread_tree_.Update(*record);
        if (!UnwindRecord(record.get())) {
          return false;
        }
        return record_file_writer_->WriteRecord(*record);
      },
      false);
  if (!result) {
    return false;
  }
  if (!DumpAdditionalFeatures(args)) {
    return false;
  }
  if (!record_file_writer_->Close()) {
    return false;
  }

  if (unlink(record_filename_.c_str()) != 0) {
    PLOG(ERROR) << "failed to remove " << record_filename_;
    return false;
  }
  if (rename(tmp_filename.c_str(), record_filename_.c_str()) != 0) {
    PLOG(ERROR) << "failed to rename " << tmp_filename << " to "
                << record_filename_;
    return false;
  }
  return true;
}

bool RecordCommand::DumpAdditionalFeatures(
    const std::vector<std::string>& args) {
  // Read data section of perf.data to collect hit file information.
  thread_tree_.ClearThreadAndMap();
  Dso::ReadKernelSymbolsFromProc();
  auto callback = [&](const Record* r) {
    thread_tree_.Update(*r);
    if (r->type() == PERF_RECORD_SAMPLE) {
      CollectHitFileInfo(*reinterpret_cast<const SampleRecord*>(r));
    }
  };
  if (!record_file_writer_->ReadDataSection(callback)) {
    return false;
  }

  size_t feature_count = 4;
  if (branch_sampling_) {
    feature_count++;
  }
  if (dump_symbols_) {
    feature_count++;
  }
  if (!record_file_writer_->BeginWriteFeatures(feature_count)) {
    return false;
  }
  if (!DumpBuildIdFeature()) {
    return false;
  }
  if (dump_symbols_ && !DumpFileFeature()) {
    return false;
  }
  utsname uname_buf;
  if (TEMP_FAILURE_RETRY(uname(&uname_buf)) != 0) {
    PLOG(ERROR) << "uname() failed";
    return false;
  }
  if (!record_file_writer_->WriteFeatureString(PerfFileFormat::FEAT_OSRELEASE,
                                               uname_buf.release)) {
    return false;
  }
  if (!record_file_writer_->WriteFeatureString(PerfFileFormat::FEAT_ARCH,
                                               uname_buf.machine)) {
    return false;
  }

  std::string exec_path = android::base::GetExecutablePath();
  if (exec_path.empty()) exec_path = "simpleperf";
  std::vector<std::string> cmdline;
  cmdline.push_back(exec_path);
  cmdline.push_back("record");
  cmdline.insert(cmdline.end(), args.begin(), args.end());
  if (!record_file_writer_->WriteCmdlineFeature(cmdline)) {
    return false;
  }
  if (branch_sampling_ != 0 &&
      !record_file_writer_->WriteBranchStackFeature()) {
    return false;
  }
  if (!record_file_writer_->EndWriteFeatures()) {
    return false;
  }
  return true;
}

bool RecordCommand::DumpBuildIdFeature() {
  std::vector<BuildIdRecord> build_id_records;
  BuildId build_id;
  std::vector<Dso*> dso_v = thread_tree_.GetAllDsos();
  for (Dso* dso : dso_v) {
    if (!dso->HasDumpId()) {
      continue;
    }
    if (dso->type() == DSO_KERNEL) {
      if (!GetKernelBuildId(&build_id)) {
        continue;
      }
      build_id_records.push_back(
          BuildIdRecord(true, UINT_MAX, build_id, dso->Path()));
    } else if (dso->type() == DSO_KERNEL_MODULE) {
      std::string path = dso->Path();
      std::string module_name = basename(&path[0]);
      if (android::base::EndsWith(module_name, ".ko")) {
        module_name = module_name.substr(0, module_name.size() - 3);
      }
      if (!GetModuleBuildId(module_name, &build_id)) {
        LOG(DEBUG) << "can't read build_id for module " << module_name;
        continue;
      }
      build_id_records.push_back(BuildIdRecord(true, UINT_MAX, build_id, path));
    } else {
      if (dso->Path() == DEFAULT_EXECNAME_FOR_THREAD_MMAP) {
        continue;
      }
      auto tuple = SplitUrlInApk(dso->Path());
      if (std::get<0>(tuple)) {
        ElfStatus result = GetBuildIdFromApkFile(std::get<1>(tuple),
                                                 std::get<2>(tuple), &build_id);
        if (result != ElfStatus::NO_ERROR) {
          LOG(DEBUG) << "can't read build_id from file " << dso->Path() << ": "
                     << result;
          continue;
        }
      } else {
        ElfStatus result = GetBuildIdFromElfFile(dso->Path(), &build_id);
        if (result != ElfStatus::NO_ERROR) {
          LOG(DEBUG) << "can't read build_id from file " << dso->Path() << ": "
                     << result;
          continue;
        }
      }
      build_id_records.push_back(
          BuildIdRecord(false, UINT_MAX, build_id, dso->Path()));
    }
  }
  if (!record_file_writer_->WriteBuildIdFeature(build_id_records)) {
    return false;
  }
  return true;
}

bool RecordCommand::DumpFileFeature() {
  std::vector<Dso*> dso_v = thread_tree_.GetAllDsos();
  for (Dso* dso : dso_v) {
    if (!dso->HasDumpId()) {
      continue;
    }
    uint32_t dso_type = dso->type();
    uint64_t min_vaddr = dso->MinVirtualAddress();

    // Dumping all symbols in hit files takes too much space, so only dump
    // needed symbols.
    const std::vector<Symbol>& symbols = dso->GetSymbols();
    std::vector<const Symbol*> dump_symbols;
    for (const auto& sym : symbols) {
      if (sym.HasDumpId()) {
        dump_symbols.push_back(&sym);
      }
    }
    std::sort(dump_symbols.begin(), dump_symbols.end(), Symbol::CompareByAddr);

    if (!record_file_writer_->WriteFileFeature(dso->Path(), dso_type, min_vaddr,
                                               dump_symbols)) {
      return false;
    }
  }
  return true;
}

void RecordCommand::CollectHitFileInfo(const SampleRecord& r) {
  const ThreadEntry* thread =
      thread_tree_.FindThreadOrNew(r.tid_data.pid, r.tid_data.tid);
  const MapEntry* map =
      thread_tree_.FindMap(thread, r.ip_data.ip, r.InKernel());
  Dso* dso = map->dso;
  const Symbol* symbol;
  if (dump_symbols_) {
    symbol = thread_tree_.FindSymbol(map, r.ip_data.ip, nullptr, &dso);
    if (!symbol->HasDumpId()) {
      dso->CreateSymbolDumpId(symbol);
    }
  }
  if (!dso->HasDumpId()) {
    dso->CreateDumpId();
  }
  if (r.sample_type & PERF_SAMPLE_CALLCHAIN) {
    bool in_kernel = r.InKernel();
    bool first_ip = true;
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
        map = thread_tree_.FindMap(thread, ip, in_kernel);
        dso = map->dso;
        if (dump_symbols_) {
          symbol = thread_tree_.FindSymbol(map, ip, nullptr, &dso);
          if (!symbol->HasDumpId()) {
            dso->CreateSymbolDumpId(symbol);
          }
        }
        if (!dso->HasDumpId()) {
          dso->CreateDumpId();
        }
      }
    }
  }
}

void RegisterRecordCommand() {
  RegisterCommand("record",
                  [] { return std::unique_ptr<Command>(new RecordCommand()); });
}
