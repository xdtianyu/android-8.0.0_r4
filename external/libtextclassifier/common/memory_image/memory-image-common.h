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

// Common utils for memory images.

#ifndef LIBTEXTCLASSIFIER_COMMON_MEMORY_IMAGE_MEMORY_IMAGE_COMMON_H_
#define LIBTEXTCLASSIFIER_COMMON_MEMORY_IMAGE_MEMORY_IMAGE_COMMON_H_

#include <stddef.h>

#include <string>

#include "util/strings/stringpiece.h"

namespace libtextclassifier {
namespace nlp_core {

class MemoryImageConstants {
 public:
  static const char kSignature[];
  static const int kCurrentVersion;
  static const int kDefaultAlignment;
};

// Read-only "view" of a data blob.  Does not own the underlying data; instead,
// just a small object that points to an area of a memory image.
//
// TODO(salcianu): replace this class with StringPiece.
class DataBlobView {
 public:
  DataBlobView() : DataBlobView(nullptr, 0) {}

  DataBlobView(const void *start, size_t size)
      : start_(start), size_(size) {}

  // Returns start address of a data blob from a memory image.
  const void *data() const { return start_; }

  // Returns number of bytes from the data blob starting at start().
  size_t size() const { return size_; }

  StringPiece to_stringpiece() const {
    return StringPiece(reinterpret_cast<const char *>(data()),
                       size());
  }

  // Returns a std::string containing a copy of the data blob bytes.
  std::string ToString() const {
    return to_stringpiece().ToString();
  }

 private:
  const void *start_;  // Not owned.
  size_t size_;
};

}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_COMMON_MEMORY_IMAGE_MEMORY_IMAGE_COMMON_H_
