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

#include "smartselect/token-feature-extractor.h"

#include <string>

#include "util/base/logging.h"
#include "util/hash/farmhash.h"
#include "util/strings/stringpiece.h"
#include "util/utf8/unicodetext.h"
#include "unicode/regex.h"
#include "unicode/uchar.h"

namespace libtextclassifier {

namespace {

std::string RemapTokenAscii(const std::string& token,
                            const TokenFeatureExtractorOptions& options) {
  if (!options.remap_digits && !options.lowercase_tokens) {
    return token;
  }

  std::string copy = token;
  for (int i = 0; i < token.size(); ++i) {
    if (options.remap_digits && isdigit(copy[i])) {
      copy[i] = '0';
    }
    if (options.lowercase_tokens) {
      copy[i] = tolower(copy[i]);
    }
  }
  return copy;
}

void RemapTokenUnicode(const std::string& token,
                       const TokenFeatureExtractorOptions& options,
                       UnicodeText* remapped) {
  if (!options.remap_digits && !options.lowercase_tokens) {
    // Leave remapped untouched.
    return;
  }

  UnicodeText word = UTF8ToUnicodeText(token, /*do_copy=*/false);
  icu::UnicodeString icu_string;
  for (auto it = word.begin(); it != word.end(); ++it) {
    if (options.remap_digits && u_isdigit(*it)) {
      icu_string.append('0');
    } else if (options.lowercase_tokens) {
      icu_string.append(u_tolower(*it));
    } else {
      icu_string.append(*it);
    }
  }
  std::string utf8_str;
  icu_string.toUTF8String(utf8_str);
  remapped->CopyUTF8(utf8_str.data(), utf8_str.length());
}

}  // namespace

TokenFeatureExtractor::TokenFeatureExtractor(
    const TokenFeatureExtractorOptions& options)
    : options_(options) {
  UErrorCode status;
  for (const std::string& pattern : options.regexp_features) {
    status = U_ZERO_ERROR;
    regex_patterns_.push_back(
        std::unique_ptr<icu::RegexPattern>(icu::RegexPattern::compile(
            icu::UnicodeString(pattern.c_str(), pattern.size(), "utf-8"), 0,
            status)));
    if (U_FAILURE(status)) {
      TC_LOG(WARNING) << "Failed to load pattern" << pattern;
    }
  }
}

int TokenFeatureExtractor::HashToken(StringPiece token) const {
  return tcfarmhash::Fingerprint64(token) % options_.num_buckets;
}

std::vector<int> TokenFeatureExtractor::ExtractCharactergramFeatures(
    const Token& token) const {
  if (options_.unicode_aware_features) {
    return ExtractCharactergramFeaturesUnicode(token);
  } else {
    return ExtractCharactergramFeaturesAscii(token);
  }
}

std::vector<int> TokenFeatureExtractor::ExtractCharactergramFeaturesAscii(
    const Token& token) const {
  std::vector<int> result;
  if (token.is_padding || token.value.empty()) {
    result.push_back(HashToken("<PAD>"));
  } else {
    const std::string word = RemapTokenAscii(token.value, options_);

    // Trim words that are over max_word_length characters.
    const int max_word_length = options_.max_word_length;
    std::string feature_word;
    if (word.size() > max_word_length) {
      feature_word =
          "^" + word.substr(0, max_word_length / 2) + "\1" +
          word.substr(word.size() - max_word_length / 2, max_word_length / 2) +
          "$";
    } else {
      // Add a prefix and suffix to the word.
      feature_word = "^" + word + "$";
    }

    // Upper-bound the number of charactergram extracted to avoid resizing.
    result.reserve(options_.chargram_orders.size() * feature_word.size());

    // Generate the character-grams.
    for (int chargram_order : options_.chargram_orders) {
      if (chargram_order == 1) {
        for (int i = 1; i < feature_word.size() - 1; ++i) {
          result.push_back(
              HashToken(StringPiece(feature_word, /*offset=*/i, /*len=*/1)));
        }
      } else {
        for (int i = 0;
             i < static_cast<int>(feature_word.size()) - chargram_order + 1;
             ++i) {
          result.push_back(HashToken(
              StringPiece(feature_word, /*offset=*/i, /*len=*/chargram_order)));
        }
      }
    }
  }
  return result;
}

std::vector<int> TokenFeatureExtractor::ExtractCharactergramFeaturesUnicode(
    const Token& token) const {
  std::vector<int> result;
  if (token.is_padding || token.value.empty()) {
    result.push_back(HashToken("<PAD>"));
  } else {
    UnicodeText word = UTF8ToUnicodeText(token.value, /*do_copy=*/false);
    RemapTokenUnicode(token.value, options_, &word);

    // Trim the word if needed by finding a left-cut point and right-cut point.
    auto left_cut = word.begin();
    auto right_cut = word.end();
    for (int i = 0; i < options_.max_word_length / 2; i++) {
      if (left_cut < right_cut) {
        ++left_cut;
      }
      if (left_cut < right_cut) {
        --right_cut;
      }
    }

    std::string feature_word;
    if (left_cut == right_cut) {
      feature_word = "^" + word.UTF8Substring(word.begin(), word.end()) + "$";
    } else {
      // clang-format off
      feature_word = "^" +
                     word.UTF8Substring(word.begin(), left_cut) +
                     "\1" +
                     word.UTF8Substring(right_cut, word.end()) +
                     "$";
      // clang-format on
    }

    const UnicodeText feature_word_unicode =
        UTF8ToUnicodeText(feature_word, /*do_copy=*/false);

    // Upper-bound the number of charactergram extracted to avoid resizing.
    result.reserve(options_.chargram_orders.size() * feature_word.size());

    // Generate the character-grams.
    for (int chargram_order : options_.chargram_orders) {
      UnicodeText::const_iterator it_start = feature_word_unicode.begin();
      UnicodeText::const_iterator it_end = feature_word_unicode.end();
      if (chargram_order == 1) {
        ++it_start;
        --it_end;
      }

      UnicodeText::const_iterator it_chargram_start = it_start;
      UnicodeText::const_iterator it_chargram_end = it_start;
      bool chargram_is_complete = true;
      for (int i = 0; i < chargram_order; ++i) {
        if (it_chargram_end == it_end) {
          chargram_is_complete = false;
          break;
        }
        ++it_chargram_end;
      }
      if (!chargram_is_complete) {
        continue;
      }

      for (; it_chargram_end <= it_end;
           ++it_chargram_start, ++it_chargram_end) {
        const int length_bytes =
            it_chargram_end.utf8_data() - it_chargram_start.utf8_data();
        result.push_back(HashToken(
            StringPiece(it_chargram_start.utf8_data(), length_bytes)));
      }
    }
  }
  return result;
}

bool TokenFeatureExtractor::Extract(const Token& token, bool is_in_span,
                                    std::vector<int>* sparse_features,
                                    std::vector<float>* dense_features) const {
  if (sparse_features == nullptr || dense_features == nullptr) {
    return false;
  }

  *sparse_features = ExtractCharactergramFeatures(token);

  if (options_.extract_case_feature) {
    if (options_.unicode_aware_features) {
      UnicodeText token_unicode =
          UTF8ToUnicodeText(token.value, /*do_copy=*/false);
      if (!token.value.empty() && u_isupper(*token_unicode.begin())) {
        dense_features->push_back(1.0);
      } else {
        dense_features->push_back(-1.0);
      }
    } else {
      if (!token.value.empty() && isupper(*token.value.begin())) {
        dense_features->push_back(1.0);
      } else {
        dense_features->push_back(-1.0);
      }
    }
  }

  if (options_.extract_selection_mask_feature) {
    if (is_in_span) {
      dense_features->push_back(1.0);
    } else {
      if (options_.unicode_aware_features) {
        dense_features->push_back(-1.0);
      } else {
        dense_features->push_back(0.0);
      }
    }
  }

  // Add regexp features.
  if (!regex_patterns_.empty()) {
    icu::UnicodeString unicode_str(token.value.c_str(), token.value.size(),
                                   "utf-8");
    for (int i = 0; i < regex_patterns_.size(); ++i) {
      if (!regex_patterns_[i].get()) {
        dense_features->push_back(-1.0);
        continue;
      }

      // Check for match.
      UErrorCode status = U_ZERO_ERROR;
      std::unique_ptr<icu::RegexMatcher> matcher(
          regex_patterns_[i]->matcher(unicode_str, status));
      if (matcher->find()) {
        dense_features->push_back(1.0);
      } else {
        dense_features->push_back(-1.0);
      }
    }
  }
  return true;
}

}  // namespace libtextclassifier
