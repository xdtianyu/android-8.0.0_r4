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

#ifndef LIBTEXTCLASSIFIER_COMMON_MEMORY_IMAGE_LOW_LEVEL_MEMORY_READER_H_
#define LIBTEXTCLASSIFIER_COMMON_MEMORY_IMAGE_LOW_LEVEL_MEMORY_READER_H_

#include <string.h>

#include <string>

#include "base.h"
#include "common/memory_image/memory-image-common.h"
#include "util/base/integral_types.h"
#include "util/base/logging.h"

namespace libtextclassifier {
namespace nlp_core {

class LowLevelMemReader {
 public:
  // Constructs a MemReader instance that reads at most num_available_bytes
  // starting from address start.
  LowLevelMemReader(const void *start, uint64 num_available_bytes)
      : current_(reinterpret_cast<const char *>(start)),
        // 0 bytes available if start == nullptr
        num_available_bytes_(start ? num_available_bytes : 0),
        num_loaded_bytes_(0) {
  }

  // Copies length bytes of data to address target.  Advances current position
  // and returns true on success and false otherwise.
  bool Read(void *target, uint64 length) {
    if (length > num_available_bytes_) {
      TC_LOG(WARNING) << "Not enough bytes: available " << num_available_bytes_
                      << " < required " << length;
      return false;
    }
    memcpy(target, current_, length);
    Advance(length);
    return true;
  }

  // Reads the string encoded at the current position.  The bytes starting at
  // current position should contain (1) little-endian uint32 size (in bytes) of
  // the actual string and next (2) the actual bytes of the string.  Advances
  // the current position and returns true if successful, false otherwise.
  //
  // On success, sets *view to be a view of the relevant bytes: view.data()
  // points to the beginning of the string bytes, and view.size() is the number
  // of such bytes.
  bool ReadString(DataBlobView *view) {
    uint32 size;
    if (!Read(&size, sizeof(size))) {
      TC_LOG(ERROR) << "Unable to read std::string size";
      return false;
    }
    size = LittleEndian::ToHost32(size);
    if (size > num_available_bytes_) {
      TC_LOG(WARNING) << "Not enough bytes: " << num_available_bytes_
                      << " available < " << size << " required ";
      return false;
    }
    *view = DataBlobView(current_, size);
    Advance(size);
    return true;
  }

  // Like ReadString(DataBlobView *) but reads directly into a C++ string,
  // instead of a DataBlobView (StringPiece-like object).
  bool ReadString(std::string *target) {
    DataBlobView view;
    if (!ReadString(&view)) {
      return false;
    }
    *target = view.ToString();
    return true;
  }

  // Returns current position.
  const char *GetCurrent() const { return current_; }

  // Returns remaining number of available bytes.
  uint64 GetNumAvailableBytes() const { return num_available_bytes_; }

  // Returns number of bytes read ("loaded") so far.
  uint64 GetNumLoadedBytes() const { return num_loaded_bytes_; }

  // Advance the current read position by indicated number of bytes.  Returns
  // true on success, false otherwise (e.g., if there are not enough available
  // bytes to advance num_bytes).
  bool Advance(uint64 num_bytes) {
    if (num_bytes > num_available_bytes_) {
      return false;
    }

    // Next line never results in an underflow of the unsigned
    // num_available_bytes_, due to the previous if.
    num_available_bytes_ -= num_bytes;
    current_ += num_bytes;
    num_loaded_bytes_ += num_bytes;
    return true;
  }

  // Advance current position to nearest multiple of alignment.  Returns false
  // if not enough bytes available to do that, true (success) otherwise.
  bool SkipToAlign(int alignment) {
    int num_extra_bytes = num_loaded_bytes_ % alignment;
    if (num_extra_bytes == 0) {
      return true;
    }
    return Advance(alignment - num_extra_bytes);
  }

 private:
  // Current position in the in-memory data.  Next call to Read() will read from
  // this address.
  const char *current_;

  // Number of available bytes we can still read.
  uint64 num_available_bytes_;

  // Number of bytes read ("loaded") so far.
  uint64 num_loaded_bytes_;
};

}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_COMMON_MEMORY_IMAGE_LOW_LEVEL_MEMORY_READER_H_
