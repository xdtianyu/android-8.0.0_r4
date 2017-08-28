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

#include "common/memory_image/memory-image-common.h"

namespace libtextclassifier {
namespace nlp_core {

// IMPORTANT: this signature should never change.  If you change the protocol,
// update kCurrentVersion, *not* this signature.
const char MemoryImageConstants::kSignature[] = "Memory image $5%1#o3-1x32";

const int MemoryImageConstants::kCurrentVersion = 1;

const int MemoryImageConstants::kDefaultAlignment = 16;

}  // namespace nlp_core
}  // namespace libtextclassifier
