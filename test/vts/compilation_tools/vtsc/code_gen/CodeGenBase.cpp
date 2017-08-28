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

#include "code_gen/CodeGenBase.h"

#include <limits.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <cstdint>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>

#include <hidl-util/Formatter.h>

#include "specification_parser/InterfaceSpecificationParser.h"
#include "utils/InterfaceSpecUtil.h"

#include "test/vts/proto/ComponentSpecificationMessage.pb.h"

#include "VtsCompilerUtils.h"
#include "code_gen/driver/HalCodeGen.h"
#include "code_gen/driver/HalSubmoduleCodeGen.h"
#include "code_gen/driver/HalHidlCodeGen.h"
#include "code_gen/driver/LegacyHalCodeGen.h"
#include "code_gen/driver/LibSharedCodeGen.h"
#include "code_gen/fuzzer/FuzzerCodeGenBase.h"
#include "code_gen/fuzzer/HalHidlFuzzerCodeGen.h"
#include "code_gen/profiler/ProfilerCodeGenBase.h"
#include "code_gen/profiler/HalHidlProfilerCodeGen.h"

using namespace std;

namespace android {
namespace vts {

CodeGenBase::CodeGenBase(const char* input_vts_file_path, const string& vts_name)
    : input_vts_file_path_(input_vts_file_path), vts_name_(vts_name) {}

CodeGenBase::~CodeGenBase() {}

// TODO(yim): deprecate this function after type specific translate functions
//            are used.
void Translate(VtsCompileMode mode,
               const char* input_vts_file_path,
               const char* output_header_dir_path,
               const char* output_cpp_file_path) {
  string output_cpp_file_path_str = string(output_cpp_file_path);

  size_t found;
  found = output_cpp_file_path_str.find_last_of("/");
  string vts_name = output_cpp_file_path_str
      .substr(found + 1, output_cpp_file_path_str.length() - found - 5);

  ComponentSpecificationMessage message;
  if (!InterfaceSpecificationParser::parse(input_vts_file_path, &message)) {
    cerr << "can't parse " << input_vts_file_path << endl;
    exit(-1);
  }

  string output_header_file_path = string(output_header_dir_path) + "/"
      + string(input_vts_file_path);
  output_header_file_path = output_header_file_path + ".h";

  vts_fs_mkdirs(&output_header_file_path[0], 0777);

  FILE* header_file = fopen(output_header_file_path.c_str(), "w");
  if (header_file == NULL) {
    cerr << "could not open file " << output_header_file_path;
    exit(-1);
  }
  Formatter header_out(header_file);

  FILE* source_file = fopen(output_cpp_file_path, "w");
  if (source_file == NULL) {
    cerr << "could not open file " << output_cpp_file_path;
    exit(-1);
  }
  Formatter source_out(source_file);

  if (mode == kDriver) {
    unique_ptr<CodeGenBase> code_generator;
    switch (message.component_class()) {
      case HAL_CONVENTIONAL:
        code_generator.reset(new HalCodeGen(input_vts_file_path, vts_name));
        break;
      case HAL_CONVENTIONAL_SUBMODULE:
        code_generator.reset(
            new HalSubmoduleCodeGen(input_vts_file_path, vts_name));
        break;
      case HAL_LEGACY:
        code_generator.reset(
            new LegacyHalCodeGen(input_vts_file_path, vts_name));
        break;
      case LIB_SHARED:
        code_generator.reset(
            new LibSharedCodeGen(input_vts_file_path, vts_name));
        break;
      case HAL_HIDL:
        code_generator.reset(new HalHidlCodeGen(input_vts_file_path, vts_name));
        break;
      default:
        cerr << "not yet supported component_class "
             << message.component_class();
        exit(-1);
    }
    code_generator->GenerateAll(header_out, source_out, message);
  } else if (mode == kFuzzer) {
    unique_ptr<FuzzerCodeGenBase> fuzzer_generator;
    switch (message.component_class()) {
      case HAL_HIDL:
        fuzzer_generator = make_unique<HalHidlFuzzerCodeGen>(message);
        break;
      default:
        cerr << "not yet supported component_class "
             << message.component_class();
        exit(-1);
    }
    fuzzer_generator->GenerateAll(header_out, source_out);
  } else if (mode == kProfiler) {
    unique_ptr<ProfilerCodeGenBase> profiler_generator;
    switch (message.component_class()) {
      case HAL_HIDL:
        profiler_generator.reset(
            new HalHidlProfilerCodeGen(input_vts_file_path));
        break;
      default:
        cerr << "not yet supported component_class "
             << message.component_class();
        exit(-1);
    }
    profiler_generator->GenerateAll(header_out, source_out, message);
  }
}

void TranslateToFile(VtsCompileMode mode,
                     const char* input_vts_file_path,
                     const char* output_file_path,
                     VtsCompileFileType file_type) {
  string output_cpp_file_path_str = string(output_file_path);

  size_t found;
  found = output_cpp_file_path_str.find_last_of("/");
  string vts_name = output_cpp_file_path_str
      .substr(found + 1, output_cpp_file_path_str.length() - found - 5);

  ComponentSpecificationMessage message;
  if (!InterfaceSpecificationParser::parse(input_vts_file_path, &message)) {
    cerr << __func__ << " can't parse " << input_vts_file_path << endl;
  }

  FILE* output_file = fopen(output_file_path, "w");
  if (output_file == NULL) {
    cerr << __func__ << " could not open file " << output_file_path << endl;
    exit(-1);
  }
  Formatter out(output_file);

  if (mode == kDriver) {
    unique_ptr<CodeGenBase> code_generator;
    switch (message.component_class()) {
      case HAL_CONVENTIONAL:
        code_generator.reset(new HalCodeGen(input_vts_file_path, vts_name));
        break;
      case HAL_CONVENTIONAL_SUBMODULE:
        code_generator.reset(
            new HalSubmoduleCodeGen(input_vts_file_path, vts_name));
        break;
      case HAL_LEGACY:
        code_generator.reset(
            new LegacyHalCodeGen(input_vts_file_path, vts_name));
        break;
      case LIB_SHARED:
        code_generator.reset(
            new LibSharedCodeGen(input_vts_file_path, vts_name));
        break;
      case HAL_HIDL:
        code_generator.reset(new HalHidlCodeGen(input_vts_file_path, vts_name));
        break;
      default:
        cerr << "not yet supported component_class "
             << message.component_class();
        exit(-1);
    }
    if (file_type == kHeader) {
      code_generator->GenerateHeaderFile(out, message);
    } else if (file_type == kSource){
      code_generator->GenerateSourceFile(out, message);
    } else {
      cerr << __func__ << " doesn't support file_type = kBoth." << endl;
      exit(-1);
    }
  } else if (mode == kFuzzer) {
    unique_ptr<FuzzerCodeGenBase> fuzzer_generator;
    switch (message.component_class()) {
      case HAL_HIDL:
        {
          fuzzer_generator = make_unique<HalHidlFuzzerCodeGen>(message);
          break;
        }
      default:
        cerr << "not yet supported component_class "
             << message.component_class();
        exit(-1);
    }
    if (file_type == kHeader) {
      fuzzer_generator->GenerateHeaderFile(out);
    } else if (file_type == kSource){
      fuzzer_generator->GenerateSourceFile(out);
    } else {
      cerr << __func__ << " doesn't support file_type = kBoth." << endl;
      exit(-1);
    }
  } else if (mode == kProfiler) {
    unique_ptr<ProfilerCodeGenBase> profiler_generator;
    switch (message.component_class()) {
      case HAL_HIDL:
        profiler_generator.reset(
            new HalHidlProfilerCodeGen(input_vts_file_path));
        break;
      default:
        cerr << "not yet supported component_class "
             << message.component_class();
        exit(-1);
    }
    if (file_type == kHeader) {
      profiler_generator->GenerateHeaderFile(out, message);
    } else if (file_type == kSource){
      profiler_generator->GenerateSourceFile(out, message);
    } else {
      cerr << __func__ << " doesn't support file_type = kBoth." << endl;
      exit(-1);
    }
  }
}

}  // namespace vts
}  // namespace android
