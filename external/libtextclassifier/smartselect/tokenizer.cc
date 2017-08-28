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

#include "smartselect/tokenizer.h"

#include "util/strings/utf8.h"
#include "util/utf8/unicodetext.h"

namespace libtextclassifier {

void Tokenizer::PrepareTokenizationCodepointRanges(
    const std::vector<TokenizationCodepointRange>& codepoint_range_configs) {
  codepoint_ranges_.clear();
  codepoint_ranges_.reserve(codepoint_range_configs.size());
  for (const TokenizationCodepointRange& range : codepoint_range_configs) {
    codepoint_ranges_.push_back(
        CodepointRange(range.start(), range.end(), range.role()));
  }

  std::sort(codepoint_ranges_.begin(), codepoint_ranges_.end(),
            [](const CodepointRange& a, const CodepointRange& b) {
              return a.start < b.start;
            });
}

TokenizationCodepointRange::Role Tokenizer::FindTokenizationRole(
    int codepoint) const {
  auto it = std::lower_bound(codepoint_ranges_.begin(), codepoint_ranges_.end(),
                             codepoint,
                             [](const CodepointRange& range, int codepoint) {
                               // This function compares range with the
                               // codepoint for the purpose of finding the first
                               // greater or equal range. Because of the use of
                               // std::lower_bound it needs to return true when
                               // range < codepoint; the first time it will
                               // return false the lower bound is found and
                               // returned.
                               //
                               // It might seem weird that the condition is
                               // range.end <= codepoint here but when codepoint
                               // == range.end it means it's actually just
                               // outside of the range, thus the range is less
                               // than the codepoint.
                               return range.end <= codepoint;
                             });
  if (it != codepoint_ranges_.end() && it->start <= codepoint &&
      it->end > codepoint) {
    return it->role;
  } else {
    return TokenizationCodepointRange::DEFAULT_ROLE;
  }
}

std::vector<Token> Tokenizer::Tokenize(const std::string& utf8_text) const {
  UnicodeText context_unicode = UTF8ToUnicodeText(utf8_text, /*do_copy=*/false);

  std::vector<Token> result;
  Token new_token("", 0, 0);
  int codepoint_index = 0;
  for (auto it = context_unicode.begin(); it != context_unicode.end();
       ++it, ++codepoint_index) {
    TokenizationCodepointRange::Role role = FindTokenizationRole(*it);
    if (role & TokenizationCodepointRange::SPLIT_BEFORE) {
      if (!new_token.value.empty()) {
        result.push_back(new_token);
      }
      new_token = Token("", codepoint_index, codepoint_index);
    }
    if (!(role & TokenizationCodepointRange::DISCARD_CODEPOINT)) {
      new_token.value += std::string(
          it.utf8_data(),
          it.utf8_data() + GetNumBytesForNonZeroUTF8Char(it.utf8_data()));
      ++new_token.end;
    }
    if (role & TokenizationCodepointRange::SPLIT_AFTER) {
      if (!new_token.value.empty()) {
        result.push_back(new_token);
      }
      new_token = Token("", codepoint_index + 1, codepoint_index + 1);
    }
  }
  if (!new_token.value.empty()) {
    result.push_back(new_token);
  }

  return result;
}

}  // namespace libtextclassifier
