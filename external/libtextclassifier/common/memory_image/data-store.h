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

#ifndef LIBTEXTCLASSIFIER_COMMON_MEMORY_IMAGE_DATA_STORE_H_
#define LIBTEXTCLASSIFIER_COMMON_MEMORY_IMAGE_DATA_STORE_H_

#include <string>

#include "common/memory_image/data-store.pb.h"
#include "common/memory_image/memory-image-common.h"
#include "common/memory_image/memory-image-reader.h"
#include "util/strings/stringpiece.h"

namespace libtextclassifier {
namespace nlp_core {
namespace memory_image {

// Class to access a data store.  See usage example in comments for
// DataStoreBuilder.
class DataStore {
 public:
  // Constructs a DataStore using the indicated bytes, i.e., bytes.size() bytes
  // starting at address bytes.data().  These bytes should contain the
  // serialization of a data store, see DataStoreBuilder::SerializeAsString().
  explicit DataStore(StringPiece bytes);

  // Retrieves (start_addr, num_bytes) info for piece of memory that contains
  // the data associated with the indicated name.  Note: this piece of memory is
  // inside the [start, start + size) (see constructor).  This piece of memory
  // starts at an offset from start which is a multiple of the alignment
  // specified when the data store was built using DataStoreBuilder.
  //
  // If the alignment is a low power of 2 (e..g, 4, 8, or 16) and "start" passed
  // to constructor corresponds to the beginning of a memory page or an address
  // returned by new or malloc(), then start_addr is divisible with alignment.
  DataBlobView GetData(const std::string &name) const;

 private:
  MemoryImageReader<DataStoreProto> reader_;
};

}  // namespace memory_image
}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_COMMON_MEMORY_IMAGE_DATA_STORE_H_
