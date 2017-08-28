// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef MOJO_COMMON_COMMON_CUSTOM_TYPES_STRUCT_TRAITS_H_
#define MOJO_COMMON_COMMON_CUSTOM_TYPES_STRUCT_TRAITS_H_

#include <stdint.h>
#include <vector>

#include "mojo/common/string16.mojom.h"
#include "mojo/public/cpp/bindings/struct_traits.h"

namespace mojo {

template <>
struct StructTraits<common::mojom::String16, base::string16> {
  static std::vector<uint16_t> data(const base::string16& str) {
    const uint16_t* base = str.data();
    return std::vector<uint16_t>(base, base + str.size());
  }
  static bool Read(common::mojom::String16DataView data, base::string16* output);
};

}  // mojo

#endif  // MOJO_COMMON_COMMON_CUSTOM_TYPES_STRUCT_TRAITS_H_
