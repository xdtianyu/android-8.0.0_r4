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

#include "lang_id/custom-tokenizer.h"

#include <ctype.h>

#include <string>

#include "util/strings/utf8.h"

namespace libtextclassifier {
namespace nlp_core {
namespace lang_id {

namespace {
inline bool IsTokenSeparator(int num_bytes, const char *curr) {
  if (num_bytes != 1) {
    return false;
  }
  return !isalpha(*curr);
}
}  // namespace

const char *GetSafeEndOfString(const char *data, size_t size) {
  const char *const hard_end = data + size;
  const char *curr = data;
  while (curr < hard_end) {
    int num_bytes = GetNumBytesForUTF8Char(curr);
    if (num_bytes == 0) {
      break;
    }
    const char *new_curr = curr + num_bytes;
    if (new_curr > hard_end) {
      return curr;
    }
    curr = new_curr;
  }
  return curr;
}

void TokenizeTextForLangId(const std::string &text, LightSentence *sentence) {
  const char *const start = text.data();
  const char *curr = start;
  const char *end = GetSafeEndOfString(start, text.size());

  // Corner case: empty safe part of the text.
  if (curr >= end) {
    return;
  }

  // Number of bytes for UTF8 character starting at *curr.  Note: the loop below
  // is guaranteed to terminate because in each iteration, we move curr by at
  // least num_bytes, and num_bytes is guaranteed to be > 0.
  int num_bytes = GetNumBytesForNonZeroUTF8Char(curr);
  while (curr < end) {
    // Jump over consecutive token separators.
    while (IsTokenSeparator(num_bytes, curr)) {
      curr += num_bytes;
      if (curr >= end) {
        return;
      }
      num_bytes = GetNumBytesForNonZeroUTF8Char(curr);
    }

    // If control reaches this point, we are at beginning of a non-empty token.
    std::string *word = sentence->add_word();

    // Add special token-start character.
    word->push_back('^');

    // Add UTF8 characters to word, until we hit the end of the safe text or a
    // token separator.
    while (true) {
      word->append(curr, num_bytes);
      curr += num_bytes;
      if (curr >= end) {
        break;
      }
      num_bytes = GetNumBytesForNonZeroUTF8Char(curr);
      if (IsTokenSeparator(num_bytes, curr)) {
        curr += num_bytes;
        num_bytes = GetNumBytesForNonZeroUTF8Char(curr);
        break;
      }
    }
    word->push_back('$');

    // Note: we intentionally do not token.set_start()/end(), as those fields
    // are not used by the langid model.
  }
}

}  // namespace lang_id
}  // namespace nlp_core
}  // namespace libtextclassifier
