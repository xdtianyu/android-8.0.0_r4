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

#include "common/memory_image/data-store.h"

#include "util/base/logging.h"

namespace libtextclassifier {
namespace nlp_core {
namespace memory_image {

DataStore::DataStore(StringPiece bytes)
    : reader_(bytes.data(), bytes.size()) {
  if (!reader_.success_status()) {
    TC_LOG(ERROR) << "Unable to successfully initialize DataStore.";
  }
}

DataBlobView DataStore::GetData(const std::string &name) const {
  if (!reader_.success_status()) {
    TC_LOG(ERROR) << "DataStore::GetData(" << name << ") "
                  << "called on invalid DataStore; will return empty data "
                  << "chunk";
    return DataBlobView();
  }

  const auto &entries = reader_.trimmed_proto().entries();
  const auto &it = entries.find(name);
  if (it == entries.end()) {
    TC_LOG(ERROR) << "Unknown key: " << name
                  << "; will return empty data chunk";
    return DataBlobView();
  }

  const DataStoreEntryBytes &entry_bytes = it->second;
  if (!entry_bytes.has_blob_index()) {
    TC_LOG(ERROR) << "DataStoreEntryBytes with no blob_index; "
                  << "will return empty data chunk.";
    return DataBlobView();
  }

  int blob_index = entry_bytes.blob_index();
  return reader_.data_blob_view(blob_index);
}

}  // namespace memory_image
}  // namespace nlp_core
}  // namespace libtextclassifier
