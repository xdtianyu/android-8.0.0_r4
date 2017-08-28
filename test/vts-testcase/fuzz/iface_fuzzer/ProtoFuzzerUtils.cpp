/*
 * Copyright 2016 The Android Open Source Project
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

#include "ProtoFuzzerUtils.h"

#include <dirent.h>
#include <dlfcn.h>
#include <getopt.h>
#include <algorithm>
#include <sstream>

#include "specification_parser/InterfaceSpecificationParser.h"
#include "utils/InterfaceSpecUtil.h"

using std::cout;
using std::cerr;
using std::string;
using std::unordered_map;
using std::vector;

namespace android {
namespace vts {
namespace fuzzer {

static void usage() {
  fprintf(
      stdout,
      "Usage:\n"
      "\n"
      "./<fuzzer> <vts flags> -- <libfuzzer flags>\n"
      "\n"
      "VTS flags (strictly in form --flag=value):\n"
      "\n"
      " vts_spec_files \tColumn-separated list of paths to vts spec files.\n"
      " vts_exec_size \t\tNumber of function calls per fuzzer execution.\n"
      "\n"
      "libfuzzer flags (strictly in form -flag=value):\n"
      " Use -help=1 to see libfuzzer flags\n"
      "\n");
}

static struct option long_options[] = {
    {"help", no_argument, 0, 'h'},
    {"vts_binder_mode", no_argument, 0, 'b'},
    {"vts_spec_dir", required_argument, 0, 'd'},
    {"vts_exec_size", required_argument, 0, 'e'},
    {"vts_service_name", required_argument, 0, 's'},
    {"vts_target_iface", required_argument, 0, 't'}};

static string GetDriverName(const CompSpec &comp_spec) {
  stringstream version;
  version.precision(1);
  version << fixed << comp_spec.component_type_version();
  string driver_name =
      comp_spec.package() + "@" + version.str() + "-vts.driver.so";
  return driver_name;
}

static string GetServiceName(const CompSpec &comp_spec) {
  // Infer HAL service name from its package name.
  string prefix = "android.hardware.";
  string service_name = comp_spec.package().substr(prefix.size());
  return service_name;
}

// Removes information from CompSpec not needed by fuzzer.
static void TrimCompSpec(CompSpec *comp_spec) {
  if (comp_spec == nullptr) {
    cerr << __func__ << ": empty CompSpec." << endl;
    return;
  }
  if (comp_spec->has_interface()) {
    auto *iface_spec = comp_spec->mutable_interface();
    for (auto i = 0; i < iface_spec->api_size(); ++i) {
      iface_spec->mutable_api(i)->clear_callflow();
    }
  }
}

static vector<CompSpec> ExtractCompSpecs(string dir_path) {
  vector<CompSpec> result{};
  DIR *dir;
  struct dirent *ent;
  if (!(dir = opendir(dir_path.c_str()))) {
    cerr << "Could not open directory: " << dir_path << endl;
    exit(1);
  }
  while ((ent = readdir(dir))) {
    string vts_spec_name{ent->d_name};
    if (vts_spec_name.find(".vts") != string::npos) {
      string vts_spec_path = dir_path + "/" + vts_spec_name;
      CompSpec comp_spec{};
      InterfaceSpecificationParser::parse(vts_spec_path.c_str(), &comp_spec);
      TrimCompSpec(&comp_spec);
      result.emplace_back(std::move(comp_spec));
    }
  }
  return result;
}

static void ExtractPredefinedTypesFromVar(
    const TypeSpec &var_spec,
    unordered_map<string, TypeSpec> &predefined_types) {
  predefined_types[var_spec.name()] = var_spec;
  for (const auto &sub_var_spec : var_spec.sub_struct()) {
    ExtractPredefinedTypesFromVar(sub_var_spec, predefined_types);
  }
}

ProtoFuzzerParams ExtractProtoFuzzerParams(int argc, char **argv) {
  ProtoFuzzerParams params;
  int opt = 0;
  int index = 0;
  while ((opt = getopt_long_only(argc, argv, "", long_options, &index)) != -1) {
    switch (opt) {
      case 'h':
        usage();
        exit(0);
      case 'b':
        params.get_stub_ = false;
        break;
      case 'd':
        params.comp_specs_ = ExtractCompSpecs(optarg);
        break;
      case 'e':
        params.exec_size_ = atoi(optarg);
        break;
      case 's':
        params.service_name_ = optarg;
        break;
      case 't':
        params.target_iface_ = optarg;
        break;
      default:
        // Ignore. This option will be handled by libfuzzer.
        break;
    }
  }
  return params;
}

CompSpec FindTargetCompSpec(const vector<CompSpec> &specs,
                            const string &target_iface) {
  if (target_iface.empty()) {
    cerr << "Target interface not specified." << endl;
    exit(1);
  }
  auto spec = std::find_if(specs.begin(), specs.end(), [target_iface](auto x) {
    return x.component_name().compare(target_iface) == 0;
  });
  if (spec == specs.end()) {
    cerr << "Target interface doesn't match any of the loaded .vts files."
         << endl;
    exit(1);
  }
  return *spec;
}

// TODO(trong): this should be done using FuzzerWrapper.
FuzzerBase *InitHalDriver(const CompSpec &comp_spec, string service_name,
                          bool get_stub) {
  const char *error;
  string driver_name = GetDriverName(comp_spec);
  void *handle = dlopen(driver_name.c_str(), RTLD_LAZY);
  if (!handle) {
    cerr << __func__ << ": " << dlerror() << endl;
    cerr << __func__ << ": Can't load shared library: " << driver_name << endl;
    exit(-1);
  }

  // Clear dlerror().
  dlerror();
  string function_name = GetFunctionNamePrefix(comp_spec);
  using loader_func = FuzzerBase *(*)();
  auto hal_loader = (loader_func)dlsym(handle, function_name.c_str());
  if ((error = dlerror()) != NULL) {
    cerr << __func__ << ": Can't find: " << function_name << endl;
    cerr << error << endl;
    exit(1);
  }

  FuzzerBase *hal = hal_loader();
  // For fuzzing, only passthrough mode provides coverage.
  if (get_stub) {
    cout << "HAL used in passthrough mode." << endl;
  } else {
    cout << "HAL used in binderized mode." << endl;
  }
  if (!hal->GetService(get_stub, service_name.c_str())) {
    cerr << __func__ << ": GetService(true, " << service_name << ") failed."
         << endl;
    exit(1);
  }
  return hal;
}

unordered_map<string, TypeSpec> ExtractPredefinedTypes(
    const vector<CompSpec> &specs) {
  unordered_map<string, TypeSpec> predefined_types;
  for (const auto &comp_spec : specs) {
    for (const auto &var_spec : comp_spec.attribute()) {
      ExtractPredefinedTypesFromVar(var_spec, predefined_types);
    }
  }
  return predefined_types;
}

void Execute(FuzzerBase *hal, const ExecSpec &exec_spec) {
  FuncSpec result{};
  for (const auto &func_spec : exec_spec.api()) {
    hal->CallFunction(func_spec, "", &result);
  }
}

}  // namespace fuzzer
}  // namespace vts
}  // namespace android
