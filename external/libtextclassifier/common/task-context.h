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

#ifndef LIBTEXTCLASSIFIER_COMMON_TASK_CONTEXT_H_
#define LIBTEXTCLASSIFIER_COMMON_TASK_CONTEXT_H_

#include <string>
#include <vector>

#include "common/task-spec.pb.h"
#include "util/base/integral_types.h"

namespace libtextclassifier {
namespace nlp_core {

// A task context holds configuration information for a task. It is basically a
// wrapper around a TaskSpec protocol buffer.
class TaskContext {
 public:
  // Returns the underlying task specification protocol buffer for the context.
  const TaskSpec &spec() const { return spec_; }
  TaskSpec *mutable_spec() { return &spec_; }

  // Returns a named input descriptor for the task. A new input  is created if
  // the task context does not already have an input with that name.
  TaskInput *GetInput(const std::string &name);
  TaskInput *GetInput(const std::string &name,
                      const std::string &file_format,
                      const std::string &record_format);

  // Sets task parameter.
  void SetParameter(const std::string &name, const std::string &value);

  // Returns task parameter. If the parameter is not in the task configuration
  // the (default) value of the corresponding command line flag is returned.
  std::string GetParameter(const std::string &name) const;
  int GetIntParameter(const std::string &name) const;
  int64 GetInt64Parameter(const std::string &name) const;
  bool GetBoolParameter(const std::string &name) const;
  double GetFloatParameter(const std::string &name) const;

  // Returns task parameter. If the parameter is not in the task configuration
  // the default value is returned.
  std::string Get(const std::string &name, const std::string &defval) const;
  std::string Get(const std::string &name, const char *defval) const;
  int Get(const std::string &name, int defval) const;
  int64 Get(const std::string &name, int64 defval) const;
  double Get(const std::string &name, double defval) const;
  bool Get(const std::string &name, bool defval) const;

  // Returns input file name for a single-file task input.
  //
  // Special cases: returns the empty string if the TaskInput does not have any
  // input files.  Returns the first file if the TaskInput has multiple input
  // files.
  static std::string InputFile(const TaskInput &input);

  // Returns true if task input supports the file and record format.
  static bool Supports(const TaskInput &input, const std::string &file_format,
                       const std::string &record_format);

 private:
  // Underlying task specification protocol buffer.
  TaskSpec spec_;

  // Vector of parameters required by this task.  These must be specified in the
  // task rather than relying on default values.
  std::vector<std::string> required_parameters_;
};

}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_COMMON_TASK_CONTEXT_H_
