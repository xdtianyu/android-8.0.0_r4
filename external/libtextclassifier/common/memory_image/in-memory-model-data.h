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

#ifndef LIBTEXTCLASSIFIER_COMMON_MEMORY_IMAGE_IN_MEMORY_MODEL_DATA_H_
#define LIBTEXTCLASSIFIER_COMMON_MEMORY_IMAGE_IN_MEMORY_MODEL_DATA_H_

#include "common/memory_image/data-store.h"
#include "common/task-spec.pb.h"
#include "util/strings/stringpiece.h"

namespace libtextclassifier {
namespace nlp_core {

// In-memory representation of data for a Saft model.  Provides access to a
// TaskSpec object (produced by the "spec" stage of the Saft training model) and
// to the bytes of the TaskInputs mentioned in that spec (all these bytes are in
// memory, no file I/O required).
//
// Technically, an InMemoryModelData is a DataStore that maps the special string
// kTaskSpecDataStoreEntryName to the binary serialization of a TaskSpec.  For
// each TaskInput (of the TaskSpec) with a file_pattern that starts with
// kFilePatternPrefix (see below), the same DataStore maps file_pattern to some
// content bytes.  This way, it is possible to have all TaskInputs in memory,
// while still allowing classic, on-disk TaskInputs.
class InMemoryModelData {
 public:
  // Name for the DataStore entry that stores the serialized TaskSpec for the
  // entire model.
  static const char kTaskSpecDataStoreEntryName[];

  // Returns prefix for TaskInput::Part::file_pattern, to distinguish those
  // "files" from other files.
  static const char kFilePatternPrefix[];

  // Constructs an InMemoryModelData based on a chunk of bytes.  Those bytes
  // should have been produced by a DataStoreBuilder.
  explicit InMemoryModelData(StringPiece bytes) : data_store_(bytes) {}

  // Fills *task_spec with a TaskSpec similar to the one used by
  // DataStoreBuilder (when building the bytes used to construct this
  // InMemoryModelData) except that each file name
  // (TaskInput::Part::file_pattern) is replaced with a name that can be used to
  // retrieve the corresponding file content bytes via GetBytesForInputFile().
  //
  // Returns true on success, false otherwise.
  bool GetTaskSpec(TaskSpec *task_spec) const;

  // Gets content bytes for a file.  The file_name argument should be the
  // file_pattern for a TaskInput from the TaskSpec (see GetTaskSpec()).
  // Returns a StringPiece indicating a memory area with the content bytes.  On
  // error, returns StringPiece(nullptr, 0).
  StringPiece GetBytesForInputFile(const std::string &file_name) const;

 private:
  const memory_image::DataStore data_store_;
};

}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_COMMON_MEMORY_IMAGE_IN_MEMORY_MODEL_DATA_H_
