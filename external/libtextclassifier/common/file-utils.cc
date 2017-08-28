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

#include "common/file-utils.h"

#include <fcntl.h>
#include <stdio.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <fstream>
#include <memory>
#include <string>

#include "util/base/logging.h"

namespace libtextclassifier {
namespace nlp_core {

namespace file_utils {

bool GetFileContent(const std::string &filename, std::string *content) {
  std::ifstream input_stream(filename, std::ifstream::binary);
  if (input_stream.fail()) {
    TC_LOG(INFO) << "Error opening " << filename;
    return false;
  }

  content->assign(
      std::istreambuf_iterator<char>(input_stream),
      std::istreambuf_iterator<char>());

  if (input_stream.fail()) {
    TC_LOG(ERROR) << "Error reading " << filename;
    return false;
  }

  TC_LOG(INFO) << "Successfully read " << filename;
  return true;
}

bool FileExists(const std::string &filename) {
  struct stat s = {0};
  if (!stat(filename.c_str(), &s)) {
    return s.st_mode & S_IFREG;
  } else {
    return false;
  }
}

bool DirectoryExists(const std::string &dirpath) {
  struct stat s = {0};
  if (!stat(dirpath.c_str(), &s)) {
    return s.st_mode & S_IFDIR;
  } else {
    return false;
  }
}

}  // namespace file_utils

}  // namespace nlp_core
}  // namespace libtextclassifier
