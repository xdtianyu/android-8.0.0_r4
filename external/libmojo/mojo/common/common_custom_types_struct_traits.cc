// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "mojo/common/common_custom_types_struct_traits.h"

namespace mojo {

// static
bool StructTraits<common::mojom::String16, base::string16>::Read(
    common::mojom::String16DataView data, base::string16* out) {
  std::vector<uint16_t> view;
  data.ReadData(&view);
  out->assign(reinterpret_cast<const base::char16*>(view.data()), view.size());
  return true;
}

}  // mojo
