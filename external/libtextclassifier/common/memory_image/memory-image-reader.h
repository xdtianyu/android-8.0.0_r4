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

// MemoryImageReader, class for reading a memory image.

#ifndef LIBTEXTCLASSIFIER_COMMON_MEMORY_IMAGE_MEMORY_IMAGE_READER_H_
#define LIBTEXTCLASSIFIER_COMMON_MEMORY_IMAGE_MEMORY_IMAGE_READER_H_

#include <string>
#include <vector>

#include "common/memory_image/memory-image-common.h"
#include "common/memory_image/memory-image.pb.h"
#include "util/base/integral_types.h"
#include "util/base/logging.h"
#include "util/base/macros.h"

namespace libtextclassifier {
namespace nlp_core {

// General, non-templatized class, to reduce code duplication.
//
// Given a memory area (pointer to start + size in bytes) parses a memory image
// from there into (1) MemoryImageHeader proto (it includes the serialized form
// of the trimmed down original proto) and (2) a list of void* pointers to the
// beginning of all data blobs.
//
// In case of parsing errors, we prefer to log the error and set the
// success_status() to false, instead of CHECK-failing .  This way, the client
// has the option of performing error recovery or crashing.  Some mobile apps
// don't like crashing (a restart is very slow) so, if possible, we try to avoid
// that.
class GeneralMemoryImageReader {
 public:
  // Constructs this object.  See class-level comments.  Note: the memory area
  // pointed to by start should not be deallocated while this object is used:
  // this object does not copy it; instead, it keeps pointers inside that memory
  // area.
  GeneralMemoryImageReader(const void *start, uint64 num_bytes)
      : start_(start), num_bytes_(num_bytes) {
    success_ = ReadMemoryImage();
  }

  virtual ~GeneralMemoryImageReader() {}

  // Returns true if reading the memory image has been successful.  If this
  // returns false, then none of the other accessors should be used.
  bool success_status() const { return success_; }

  // Returns number of data blobs from the memory image.
  int num_data_blobs() const {
    return data_blob_views_.size();
  }

  // Returns pointer to the beginning of the data blob #i.
  DataBlobView data_blob_view(int i) const {
    if ((i < 0) || (i >= num_data_blobs())) {
      TC_LOG(ERROR) << "Blob index " << i << " outside range [0, "
                    << num_data_blobs() << "); will return empty data chunk";
      return DataBlobView();
    }
    return data_blob_views_[i];
  }

  // Returns std::string with binary serialization of the original proto, but
  // trimmed of the large fields (those were placed in the data blobs).
  std::string trimmed_proto_str() const {
    return trimmed_proto_serialization_.ToString();
  }

  const MemoryImageHeader &header() { return header_; }

 protected:
  void set_as_failed() {
    success_ = false;
  }

 private:
  bool ReadMemoryImage();

  // Pointer to beginning of memory image.  Not owned.
  const void *const start_;

  // Number of bytes in the memory image.  This class will not read more bytes.
  const uint64 num_bytes_;

  // MemoryImageHeader parsed from the memory image.
  MemoryImageHeader header_;

  // Binary serialization of the trimmed version of the original proto.
  // Represented as a DataBlobView backed up by the underlying memory image
  // bytes.
  DataBlobView trimmed_proto_serialization_;

  // List of DataBlobView objects for all data blobs from the memory image (in
  // order).
  std::vector<DataBlobView> data_blob_views_;

  // Memory reading success status.
  bool success_;

  TC_DISALLOW_COPY_AND_ASSIGN(GeneralMemoryImageReader);
};

// Like GeneralMemoryImageReader, but has knowledge about the type of the
// original proto.  As such, it can parse it (well, the trimmed version) and
// offer access to it.
//
// Template parameter T should be the type of the original proto.
template<class T>
class MemoryImageReader : public GeneralMemoryImageReader {
 public:
  MemoryImageReader(const void *start, uint64 num_bytes)
      : GeneralMemoryImageReader(start, num_bytes) {
    if (!trimmed_proto_.ParseFromString(trimmed_proto_str())) {
      TC_LOG(INFO) << "Unable to parse the trimmed proto";
      set_as_failed();
    }
  }

  // Returns const reference to the trimmed version of the original proto.
  // Useful for retrieving the many small fields that are not converted into
  // data blobs.
  const T &trimmed_proto() const { return trimmed_proto_; }

 private:
  T trimmed_proto_;

  TC_DISALLOW_COPY_AND_ASSIGN(MemoryImageReader);
};

}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_COMMON_MEMORY_IMAGE_MEMORY_IMAGE_READER_H_
