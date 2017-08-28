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

#include "common/task-context.h"

#include <stdlib.h>

#include <string>

#include "util/base/integral_types.h"
#include "util/base/logging.h"
#include "util/strings/numbers.h"

namespace libtextclassifier {
namespace nlp_core {

namespace {
int32 ParseInt32WithDefault(const std::string &s, int32 defval) {
  int32 value = defval;
  return ParseInt32(s.c_str(), &value) ? value : defval;
}

int64 ParseInt64WithDefault(const std::string &s, int64 defval) {
  int64 value = defval;
  return ParseInt64(s.c_str(), &value) ? value : defval;
}

double ParseDoubleWithDefault(const std::string &s, double defval) {
  double value = defval;
  return ParseDouble(s.c_str(), &value) ? value : defval;
}
}  // namespace

TaskInput *TaskContext::GetInput(const std::string &name) {
  // Return existing input if it exists.
  for (int i = 0; i < spec_.input_size(); ++i) {
    if (spec_.input(i).name() == name) return spec_.mutable_input(i);
  }

  // Create new input.
  TaskInput *input = spec_.add_input();
  input->set_name(name);
  return input;
}

TaskInput *TaskContext::GetInput(const std::string &name,
                                 const std::string &file_format,
                                 const std::string &record_format) {
  TaskInput *input = GetInput(name);
  if (!file_format.empty()) {
    bool found = false;
    for (int i = 0; i < input->file_format_size(); ++i) {
      if (input->file_format(i) == file_format) found = true;
    }
    if (!found) input->add_file_format(file_format);
  }
  if (!record_format.empty()) {
    bool found = false;
    for (int i = 0; i < input->record_format_size(); ++i) {
      if (input->record_format(i) == record_format) found = true;
    }
    if (!found) input->add_record_format(record_format);
  }
  return input;
}

void TaskContext::SetParameter(const std::string &name,
                               const std::string &value) {
  TC_LOG(INFO) << "SetParameter(" << name << ", " << value << ")";

  // If the parameter already exists update the value.
  for (int i = 0; i < spec_.parameter_size(); ++i) {
    if (spec_.parameter(i).name() == name) {
      spec_.mutable_parameter(i)->set_value(value);
      return;
    }
  }

  // Add new parameter.
  TaskSpec::Parameter *param = spec_.add_parameter();
  param->set_name(name);
  param->set_value(value);
}

std::string TaskContext::GetParameter(const std::string &name) const {
  // First try to find parameter in task specification.
  for (int i = 0; i < spec_.parameter_size(); ++i) {
    if (spec_.parameter(i).name() == name) return spec_.parameter(i).value();
  }

  // Parameter not found, return empty std::string.
  return "";
}

int TaskContext::GetIntParameter(const std::string &name) const {
  std::string value = GetParameter(name);
  return ParseInt32WithDefault(value, 0);
}

int64 TaskContext::GetInt64Parameter(const std::string &name) const {
  std::string value = GetParameter(name);
  return ParseInt64WithDefault(value, 0);
}

bool TaskContext::GetBoolParameter(const std::string &name) const {
  std::string value = GetParameter(name);
  return value == "true";
}

double TaskContext::GetFloatParameter(const std::string &name) const {
  std::string value = GetParameter(name);
  return ParseDoubleWithDefault(value, 0.0);
}

std::string TaskContext::Get(const std::string &name,
                             const char *defval) const {
  // First try to find parameter in task specification.
  for (int i = 0; i < spec_.parameter_size(); ++i) {
    if (spec_.parameter(i).name() == name) return spec_.parameter(i).value();
  }

  // Parameter not found, return default value.
  return defval;
}

std::string TaskContext::Get(const std::string &name,
                             const std::string &defval) const {
  return Get(name, defval.c_str());
}

int TaskContext::Get(const std::string &name, int defval) const {
  std::string value = Get(name, "");
  return ParseInt32WithDefault(value, defval);
}

int64 TaskContext::Get(const std::string &name, int64 defval) const {
  std::string value = Get(name, "");
  return ParseInt64WithDefault(value, defval);
}

double TaskContext::Get(const std::string &name, double defval) const {
  std::string value = Get(name, "");
  return ParseDoubleWithDefault(value, defval);
}

bool TaskContext::Get(const std::string &name, bool defval) const {
  std::string value = Get(name, "");
  return value.empty() ? defval : value == "true";
}

std::string TaskContext::InputFile(const TaskInput &input) {
  if (input.part_size() == 0) {
    TC_LOG(ERROR) << "No file for TaskInput " << input.name();
    return "";
  }
  if (input.part_size() > 1) {
    TC_LOG(ERROR) << "Ambiguous: multiple files for TaskInput " << input.name();
  }
  return input.part(0).file_pattern();
}

bool TaskContext::Supports(const TaskInput &input,
                           const std::string &file_format,
                           const std::string &record_format) {
  // Check file format.
  if (input.file_format_size() > 0) {
    bool found = false;
    for (int i = 0; i < input.file_format_size(); ++i) {
      if (input.file_format(i) == file_format) {
        found = true;
        break;
      }
    }
    if (!found) return false;
  }

  // Check record format.
  if (input.record_format_size() > 0) {
    bool found = false;
    for (int i = 0; i < input.record_format_size(); ++i) {
      if (input.record_format(i) == record_format) {
        found = true;
        break;
      }
    }
    if (!found) return false;
  }

  return true;
}

}  // namespace nlp_core
}  // namespace libtextclassifier
