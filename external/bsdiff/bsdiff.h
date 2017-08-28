// Copyright 2015 The Chromium OS Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef _BSDIFF_BSDIFF_H_
#define _BSDIFF_BSDIFF_H_

#include <sys/types.h>

#if _FILE_OFFSET_BITS == 64
#include "divsufsort64.h"
#define saidx_t saidx64_t
#define divsufsort divsufsort64
#else
#include "divsufsort.h"
#endif

namespace bsdiff {

int bsdiff(const char* old_filename,
           const char* new_filename,
           const char* patch_filename);

int bsdiff(const u_char* old_buf,
           off_t oldsize,
           const u_char* new_buf,
           off_t newsize,
           const char* patch_filename,
           saidx_t** I_cache);

}  // namespace bsdiff

#endif  // _BSDIFF_BSDIFF_H_
