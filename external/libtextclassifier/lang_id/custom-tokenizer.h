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

#ifndef LIBTEXTCLASSIFIER_LANG_ID_CUSTOM_TOKENIZER_H_
#define LIBTEXTCLASSIFIER_LANG_ID_CUSTOM_TOKENIZER_H_

#include <cstddef>
#include <string>

#include "lang_id/light-sentence.h"

namespace libtextclassifier {
namespace nlp_core {
namespace lang_id {

// Perform custom tokenization of text.  Customized for the language
// identification project.  Currently (Sep 15, 2016) we tokenize on space,
// newline, and tab, ignore all empty tokens, and (for each of the remaining
// tokens) prepend "^" (special token begin marker) and append "$" (special
// token end marker).
//
// Tokens are stored into the words of the LightSentence *sentence.
void TokenizeTextForLangId(const std::string &text, LightSentence *sentence);

// Returns a pointer "end" inside [data, data + size) such that the prefix from
// [data, end) is the largest one that does not contain '\0' and offers the
// following guarantee: if one starts with
//
//   curr = text.data()
//
// and keeps executing
//
//   curr += utils::GetNumBytesForNonZeroUTF8Char(curr)
//
// one would eventually reach curr == end (the pointer returned by this
// function) without accessing data outside the std::string.  This guards
// against scenarios like a broken UTF-8 string which has only e.g., the first 2
// bytes from a 3-byte UTF8 sequence.
const char *GetSafeEndOfString(const char *data, size_t size);

static inline const char *GetSafeEndOfString(const std::string &text) {
  return GetSafeEndOfString(text.data(), text.size());
}

}  // namespace lang_id
}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_LANG_ID_CUSTOM_TOKENIZER_H_
