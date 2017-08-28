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

#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier {
namespace {

using testing::ElementsAreArray;

class TestingTokenizer : public Tokenizer {
 public:
  explicit TestingTokenizer(
      const std::vector<TokenizationCodepointRange>& codepoint_range_configs)
      : Tokenizer(codepoint_range_configs) {}

  TokenizationCodepointRange::Role TestFindTokenizationRole(int c) const {
    return FindTokenizationRole(c);
  }
};

TEST(TokenizerTest, FindTokenizationRole) {
  std::vector<TokenizationCodepointRange> configs;
  TokenizationCodepointRange* config;

  configs.emplace_back();
  config = &configs.back();
  config->set_start(0);
  config->set_end(10);
  config->set_role(TokenizationCodepointRange::TOKEN_SEPARATOR);

  configs.emplace_back();
  config = &configs.back();
  config->set_start(32);
  config->set_end(33);
  config->set_role(TokenizationCodepointRange::WHITESPACE_SEPARATOR);

  configs.emplace_back();
  config = &configs.back();
  config->set_start(1234);
  config->set_end(12345);
  config->set_role(TokenizationCodepointRange::TOKEN_SEPARATOR);

  TestingTokenizer tokenizer(configs);

  // Test hits to the first group.
  EXPECT_EQ(tokenizer.TestFindTokenizationRole(0),
            TokenizationCodepointRange::TOKEN_SEPARATOR);
  EXPECT_EQ(tokenizer.TestFindTokenizationRole(5),
            TokenizationCodepointRange::TOKEN_SEPARATOR);
  EXPECT_EQ(tokenizer.TestFindTokenizationRole(10),
            TokenizationCodepointRange::DEFAULT_ROLE);

  // Test a hit to the second group.
  EXPECT_EQ(tokenizer.TestFindTokenizationRole(31),
            TokenizationCodepointRange::DEFAULT_ROLE);
  EXPECT_EQ(tokenizer.TestFindTokenizationRole(32),
            TokenizationCodepointRange::WHITESPACE_SEPARATOR);
  EXPECT_EQ(tokenizer.TestFindTokenizationRole(33),
            TokenizationCodepointRange::DEFAULT_ROLE);

  // Test hits to the third group.
  EXPECT_EQ(tokenizer.TestFindTokenizationRole(1233),
            TokenizationCodepointRange::DEFAULT_ROLE);
  EXPECT_EQ(tokenizer.TestFindTokenizationRole(1234),
            TokenizationCodepointRange::TOKEN_SEPARATOR);
  EXPECT_EQ(tokenizer.TestFindTokenizationRole(12344),
            TokenizationCodepointRange::TOKEN_SEPARATOR);
  EXPECT_EQ(tokenizer.TestFindTokenizationRole(12345),
            TokenizationCodepointRange::DEFAULT_ROLE);

  // Test a hit outside.
  EXPECT_EQ(tokenizer.TestFindTokenizationRole(99),
            TokenizationCodepointRange::DEFAULT_ROLE);
}

TEST(TokenizerTest, TokenizeOnSpace) {
  std::vector<TokenizationCodepointRange> configs;
  TokenizationCodepointRange* config;

  configs.emplace_back();
  config = &configs.back();
  // Space character.
  config->set_start(32);
  config->set_end(33);
  config->set_role(TokenizationCodepointRange::WHITESPACE_SEPARATOR);

  TestingTokenizer tokenizer(configs);
  std::vector<Token> tokens = tokenizer.Tokenize("Hello world!");

  EXPECT_THAT(tokens,
              ElementsAreArray({Token("Hello", 0, 5), Token("world!", 6, 12)}));
}

TEST(TokenizerTest, TokenizeComplex) {
  std::vector<TokenizationCodepointRange> configs;
  TokenizationCodepointRange* config;

  // Source: http://www.unicode.org/Public/10.0.0/ucd/Blocks-10.0.0d1.txt
  // Latin - cyrilic.
  //   0000..007F; Basic Latin
  //   0080..00FF; Latin-1 Supplement
  //   0100..017F; Latin Extended-A
  //   0180..024F; Latin Extended-B
  //   0250..02AF; IPA Extensions
  //   02B0..02FF; Spacing Modifier Letters
  //   0300..036F; Combining Diacritical Marks
  //   0370..03FF; Greek and Coptic
  //   0400..04FF; Cyrillic
  //   0500..052F; Cyrillic Supplement
  //   0530..058F; Armenian
  //   0590..05FF; Hebrew
  //   0600..06FF; Arabic
  //   0700..074F; Syriac
  //   0750..077F; Arabic Supplement
  configs.emplace_back();
  config = &configs.back();
  config->set_start(0);
  config->set_end(32);
  config->set_role(TokenizationCodepointRange::DEFAULT_ROLE);
  configs.emplace_back();
  config = &configs.back();
  config->set_start(32);
  config->set_end(33);
  config->set_role(TokenizationCodepointRange::WHITESPACE_SEPARATOR);
  configs.emplace_back();
  config = &configs.back();
  config->set_start(33);
  config->set_end(0x77F + 1);
  config->set_role(TokenizationCodepointRange::DEFAULT_ROLE);

  // CJK
  // 2E80..2EFF; CJK Radicals Supplement
  // 3000..303F; CJK Symbols and Punctuation
  // 3040..309F; Hiragana
  // 30A0..30FF; Katakana
  // 3100..312F; Bopomofo
  // 3130..318F; Hangul Compatibility Jamo
  // 3190..319F; Kanbun
  // 31A0..31BF; Bopomofo Extended
  // 31C0..31EF; CJK Strokes
  // 31F0..31FF; Katakana Phonetic Extensions
  // 3200..32FF; Enclosed CJK Letters and Months
  // 3300..33FF; CJK Compatibility
  // 3400..4DBF; CJK Unified Ideographs Extension A
  // 4DC0..4DFF; Yijing Hexagram Symbols
  // 4E00..9FFF; CJK Unified Ideographs
  // A000..A48F; Yi Syllables
  // A490..A4CF; Yi Radicals
  // A4D0..A4FF; Lisu
  // A500..A63F; Vai
  // F900..FAFF; CJK Compatibility Ideographs
  // FE30..FE4F; CJK Compatibility Forms
  // 20000..2A6DF; CJK Unified Ideographs Extension B
  // 2A700..2B73F; CJK Unified Ideographs Extension C
  // 2B740..2B81F; CJK Unified Ideographs Extension D
  // 2B820..2CEAF; CJK Unified Ideographs Extension E
  // 2CEB0..2EBEF; CJK Unified Ideographs Extension F
  // 2F800..2FA1F; CJK Compatibility Ideographs Supplement
  configs.emplace_back();
  config = &configs.back();
  config->set_start(0x2E80);
  config->set_end(0x2EFF + 1);
  config->set_role(TokenizationCodepointRange::TOKEN_SEPARATOR);
  configs.emplace_back();
  config = &configs.back();
  config->set_start(0x3000);
  config->set_end(0xA63F + 1);
  config->set_role(TokenizationCodepointRange::TOKEN_SEPARATOR);
  configs.emplace_back();
  config = &configs.back();
  config->set_start(0xF900);
  config->set_end(0xFAFF + 1);
  config->set_role(TokenizationCodepointRange::TOKEN_SEPARATOR);
  configs.emplace_back();
  config = &configs.back();
  config->set_start(0xFE30);
  config->set_end(0xFE4F + 1);
  config->set_role(TokenizationCodepointRange::TOKEN_SEPARATOR);
  configs.emplace_back();
  config = &configs.back();
  config->set_start(0x20000);
  config->set_end(0x2A6DF + 1);
  config->set_role(TokenizationCodepointRange::TOKEN_SEPARATOR);
  configs.emplace_back();
  config = &configs.back();
  config->set_start(0x2A700);
  config->set_end(0x2B73F + 1);
  config->set_role(TokenizationCodepointRange::TOKEN_SEPARATOR);
  configs.emplace_back();
  config = &configs.back();
  config->set_start(0x2B740);
  config->set_end(0x2B81F + 1);
  config->set_role(TokenizationCodepointRange::TOKEN_SEPARATOR);
  configs.emplace_back();
  config = &configs.back();
  config->set_start(0x2B820);
  config->set_end(0x2CEAF + 1);
  config->set_role(TokenizationCodepointRange::TOKEN_SEPARATOR);
  configs.emplace_back();
  config = &configs.back();
  config->set_start(0x2CEB0);
  config->set_end(0x2EBEF + 1);
  config->set_role(TokenizationCodepointRange::TOKEN_SEPARATOR);
  configs.emplace_back();
  config = &configs.back();
  config->set_start(0x2F800);
  config->set_end(0x2FA1F + 1);
  config->set_role(TokenizationCodepointRange::TOKEN_SEPARATOR);

  // Thai.
  // 0E00..0E7F; Thai
  configs.emplace_back();
  config = &configs.back();
  config->set_start(0x0E00);
  config->set_end(0x0E7F + 1);
  config->set_role(TokenizationCodepointRange::TOKEN_SEPARATOR);

  Tokenizer tokenizer(configs);
  std::vector<Token> tokens;

  tokens = tokenizer.Tokenize(
      "問少目木輸走猶術権自京門録球変。細開括省用掲情結傍走愛明氷。");
  EXPECT_EQ(tokens.size(), 30);

  tokens = tokenizer.Tokenize("問少目 hello 木輸ยามきゃ");
  // clang-format off
  EXPECT_THAT(
      tokens,
      ElementsAreArray({Token("問", 0, 1),
                        Token("少", 1, 2),
                        Token("目", 2, 3),
                        Token("hello", 4, 9),
                        Token("木", 10, 11),
                        Token("輸", 11, 12),
                        Token("ย", 12, 13),
                        Token("า", 13, 14),
                        Token("ม", 14, 15),
                        Token("き", 15, 16),
                        Token("ゃ", 16, 17)}));
  // clang-format on
}

}  // namespace
}  // namespace libtextclassifier
