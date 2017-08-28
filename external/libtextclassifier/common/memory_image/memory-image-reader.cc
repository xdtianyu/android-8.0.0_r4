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

#include "common/memory_image/memory-image-reader.h"

#include <string>

#include "base.h"
#include "common/memory_image/low-level-memory-reader.h"
#include "common/memory_image/memory-image-common.h"
#include "common/memory_image/memory-image.pb.h"
#include "util/base/logging.h"

namespace libtextclassifier {
namespace nlp_core {

namespace {

// Checks that the memory area read by mem_reader starts with the expected
// signature.  Advances mem_reader past the signature and returns success
// status.
bool ReadAndCheckSignature(LowLevelMemReader *mem_reader) {
  const std::string expected_signature = MemoryImageConstants::kSignature;
  const int signature_size = expected_signature.size();
  if (mem_reader->GetNumAvailableBytes() < signature_size) {
    TC_LOG(ERROR) << "Not enough bytes to check signature";
    return false;
  }
  const std::string actual_signature(mem_reader->GetCurrent(), signature_size);
  if (!mem_reader->Advance(signature_size)) {
    TC_LOG(ERROR) << "Failed to advance past signature";
    return false;
  }
  if (actual_signature != expected_signature) {
    TC_LOG(ERROR) << "Different signature: actual \"" << actual_signature
                  << "\" != expected \"" << expected_signature << "\"";
    return false;
  }
  return true;
}

// Parses MemoryImageHeader from mem_reader.  Advances mem_reader past it.
// Returns success status.
bool ParseMemoryImageHeader(
    LowLevelMemReader *mem_reader, MemoryImageHeader *header) {
  std::string header_proto_str;
  if (!mem_reader->ReadString(&header_proto_str)) {
    TC_LOG(ERROR) << "Unable to read header_proto_str";
    return false;
  }
  if (!header->ParseFromString(header_proto_str)) {
    TC_LOG(ERROR) << "Unable to parse MemoryImageHeader";
    return false;
  }
  return true;
}

}  // namespace

bool GeneralMemoryImageReader::ReadMemoryImage() {
  LowLevelMemReader mem_reader(start_, num_bytes_);

  // Read and check signature.
  if (!ReadAndCheckSignature(&mem_reader)) {
    return false;
  }

  // Parse MemoryImageHeader header_.
  if (!ParseMemoryImageHeader(&mem_reader, &header_)) {
    return false;
  }

  // Check endianness.
  if (header_.is_little_endian() != LittleEndian::IsLittleEndian()) {
    // TODO(salcianu): implement conversion: it will take time, but it's better
    // than crashing.  Not very urgent: [almost] all current Android phones are
    // little-endian.
    TC_LOG(ERROR) << "Memory image is "
                  << (header_.is_little_endian() ? "little" : "big")
                  << " endian. "
                  << "Local system is different and we don't currently support "
                  << "conversion between the two.";
    return false;
  }

  // Read binary serialization of trimmed original proto.
  if (!mem_reader.ReadString(&trimmed_proto_serialization_)) {
    TC_LOG(ERROR) << "Unable to read trimmed proto binary serialization";
    return false;
  }

  // Fill vector of pointers to beginning of each data blob.
  for (int i = 0; i < header_.blob_info_size(); ++i) {
    const MemoryImageDataBlobInfo &blob_info = header_.blob_info(i);
    if (!mem_reader.SkipToAlign(header_.alignment())) {
      TC_LOG(ERROR) << "Unable to align for blob #i" << i;
      return false;
    }
    data_blob_views_.emplace_back(
        mem_reader.GetCurrent(),
        blob_info.num_bytes());
    if (!mem_reader.Advance(blob_info.num_bytes())) {
      TC_LOG(ERROR) << "Not enough bytes for blob #i" << i;
      return false;
    }
  }

  return true;
}

}  // namespace nlp_core
}  // namespace libtextclassifier
