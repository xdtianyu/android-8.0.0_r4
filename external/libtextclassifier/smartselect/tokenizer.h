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

#ifndef LIBTEXTCLASSIFIER_SMARTSELECT_TOKENIZER_H_
#define LIBTEXTCLASSIFIER_SMARTSELECT_TOKENIZER_H_

#include <string>
#include <vector>

#include "smartselect/tokenizer.pb.h"
#include "smartselect/types.h"
#include "util/base/integral_types.h"

namespace libtextclassifier {

// Tokenizer splits the input string into a sequence of tokens, according to the
// configuration.
class Tokenizer {
 public:
  explicit Tokenizer(
      const std::vector<TokenizationCodepointRange>& codepoint_range_configs) {
    PrepareTokenizationCodepointRanges(codepoint_range_configs);
  }

  // Tokenizes the input string using the selected tokenization method.
  std::vector<Token> Tokenize(const std::string& utf8_text) const;

 protected:
  // Represents a codepoint range [start, end) with its role for tokenization.
  struct CodepointRange {
    int32 start;
    int32 end;
    TokenizationCodepointRange::Role role;

    CodepointRange(int32 arg_start, int32 arg_end,
                   TokenizationCodepointRange::Role arg_role)
        : start(arg_start), end(arg_end), role(arg_role) {}
  };

  // Prepares tokenization codepoint ranges for use in tokenization.
  void PrepareTokenizationCodepointRanges(
      const std::vector<TokenizationCodepointRange>& codepoint_range_configs);

  // Finds the tokenization role for given codepoint.
  // If the character is not found returns DEFAULT_ROLE.
  // Internally uses binary search so should be O(log(# of codepoint_ranges)).
  TokenizationCodepointRange::Role FindTokenizationRole(int codepoint) const;

 private:
  // Codepoint ranges that determine how different codepoints are tokenized.
  // The ranges must not overlap.
  std::vector<CodepointRange> codepoint_ranges_;
};

}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_SMARTSELECT_TOKENIZER_H_
