
#include "perf_data_converter.h"
#include "quipper/perf_parser.h"
#include <map>

using std::map;

namespace wireless_android_logging_awp {

typedef quipper::ParsedEvent::DSOAndOffset DSOAndOffset;
typedef std::vector<DSOAndOffset> callchain;

struct callchain_lt {
  bool operator()(const callchain *c1, const callchain *c2) const {
    if (c1->size() != c2->size()) {
      return c1->size() < c2->size();
    }
    for (unsigned idx = 0; idx < c1->size(); ++idx) {
      const DSOAndOffset *do1 = &(*c1)[idx];
      const DSOAndOffset *do2 = &(*c2)[idx];
      if (do1->offset() != do2->offset()) {
        return do1->offset() < do2->offset();
      }
      int rc = do1->dso_name().compare(do2->dso_name());
      if (rc) {
        return rc < 0;
      }
    }
    return false;
  }
};

struct RangeTarget {
  RangeTarget(uint64 start, uint64 end, uint64 to)
      : start(start), end(end), to(to) {}

  bool operator<(const RangeTarget &r) const {
    if (start != r.start) {
      return start < r.start;
    } else if (end != r.end) {
      return end < r.end;
    } else {
      return to < r.to;
    }
  }
  uint64 start;
  uint64 end;
  uint64 to;
};

struct BinaryProfile {
  map<uint64, uint64> address_count_map;
  map<RangeTarget, uint64> range_count_map;
  map<const callchain *, uint64, callchain_lt> callchain_count_map;
};

wireless_android_play_playlog::AndroidPerfProfile
RawPerfDataToAndroidPerfProfile(const string &perf_file) {
  wireless_android_play_playlog::AndroidPerfProfile ret;
  quipper::PerfParser parser;
  if (!parser.ReadFile(perf_file) || !parser.ParseRawEvents()) {
    return ret;
  }

  typedef map<string, BinaryProfile> ModuleProfileMap;
  typedef map<string, ModuleProfileMap> ProgramProfileMap;

  // Note: the callchain_count_map member in BinaryProfile contains
  // pointers into callchains owned by "parser" above, meaning
  // that once the parser is destroyed, callchain pointers in
  // name_profile_map will become stale (e.g. keep these two
  // together in the same region).
  ProgramProfileMap name_profile_map;
  uint64 total_samples = 0;
  bool seen_branch_stack = false;
  bool seen_callchain = false;
  for (const auto &event : parser.parsed_events()) {
    if (!event.raw_event ||
        event.raw_event->header.type != PERF_RECORD_SAMPLE) {
      continue;
    }
    string dso_name = event.dso_and_offset.dso_name();
    string program_name = event.command();
    const string kernel_name = "[kernel.kallsyms]";
    if (dso_name.substr(0, kernel_name.length()) == kernel_name) {
      dso_name = kernel_name;
      if (program_name == "") {
        program_name = "kernel";
      }
    } else if (program_name == "") {
      program_name = "unknown_program";
    }
    total_samples++;
    // We expect to see either all callchain events, all branch stack
    // events, or all flat sample events, not a mix. For callchains,
    // however, it can be the case that none of the IPs in a chain
    // are mappable, in which case the parsed/mapped chain will appear
    // empty (appearing as a flat sample).
    if (!event.callchain.empty()) {
      CHECK(!seen_branch_stack && "examining callchain");
      seen_callchain = true;
      const callchain *cc = &event.callchain;
      name_profile_map[program_name][dso_name].callchain_count_map[cc]++;
    } else if (!event.branch_stack.empty()) {
      CHECK(!seen_callchain && "examining branch stack");
      seen_branch_stack = true;
      name_profile_map[program_name][dso_name].address_count_map[
          event.dso_and_offset.offset()]++;
    } else {
      name_profile_map[program_name][dso_name].address_count_map[
          event.dso_and_offset.offset()]++;
    }
    for (size_t i = 1; i < event.branch_stack.size(); i++) {
      if (dso_name == event.branch_stack[i - 1].to.dso_name()) {
        uint64 start = event.branch_stack[i].to.offset();
        uint64 end = event.branch_stack[i - 1].from.offset();
        uint64 to = event.branch_stack[i - 1].to.offset();
        // The interval between two taken branches should not be too large.
        if (end < start || end - start > (1 << 20)) {
          LOG(WARNING) << "Bogus LBR data: " << start << "->" << end;
          continue;
        }
        name_profile_map[program_name][dso_name].range_count_map[
            RangeTarget(start, end, to)]++;
      }
    }
  }

  map<string, int> name_id_map;
  for (const auto &program_profile : name_profile_map) {
    for (const auto &module_profile : program_profile.second) {
      name_id_map[module_profile.first] = 0;
    }
  }
  int current_index = 0;
  for (auto iter = name_id_map.begin(); iter != name_id_map.end(); ++iter) {
    iter->second = current_index++;
  }

  map<string, string> name_buildid_map;
  parser.GetFilenamesToBuildIDs(&name_buildid_map);
  ret.set_total_samples(total_samples);
  for (const auto &name_id : name_id_map) {
    auto load_module = ret.add_load_modules();
    load_module->set_name(name_id.first);
    auto nbmi = name_buildid_map.find(name_id.first);
    if (nbmi != name_buildid_map.end()) {
      const std::string &build_id = nbmi->second;
      if (build_id.size() == 40 && build_id.substr(32) == "00000000") {
        load_module->set_build_id(build_id.substr(0, 32));
      } else {
        load_module->set_build_id(build_id);
      }
    }
  }
  for (const auto &program_profile : name_profile_map) {
    auto program = ret.add_programs();
    program->set_name(program_profile.first);
    for (const auto &module_profile : program_profile.second) {
      int32 module_id = name_id_map[module_profile.first];
      auto module = program->add_modules();
      module->set_load_module_id(module_id);
      for (const auto &addr_count : module_profile.second.address_count_map) {
        auto address_samples = module->add_address_samples();
        address_samples->add_address(addr_count.first);
        address_samples->set_count(addr_count.second);
      }
      for (const auto &range_count : module_profile.second.range_count_map) {
        auto range_samples = module->add_range_samples();
        range_samples->set_start(range_count.first.start);
        range_samples->set_end(range_count.first.end);
        range_samples->set_to(range_count.first.to);
        range_samples->set_count(range_count.second);
      }
      for (const auto &callchain_count :
               module_profile.second.callchain_count_map) {
        auto address_samples = module->add_address_samples();
        address_samples->set_count(callchain_count.second);
        for (const auto &d_o : *callchain_count.first) {
          int32 module_id = name_id_map[d_o.dso_name()];
          address_samples->add_load_module_id(module_id);
          address_samples->add_address(d_o.offset());
        }
      }
    }
  }
  return ret;
}

}  // namespace wireless_android_logging_awp
