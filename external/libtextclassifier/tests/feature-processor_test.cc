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

#include "smartselect/feature-processor.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier {
namespace {

using testing::ElementsAreArray;
using testing::FloatEq;

TEST(FeatureProcessorTest, SplitTokensOnSelectionBoundariesMiddle) {
  std::vector<Token> tokens{Token("Hělló", 0, 5),
                            Token("fěěbař@google.com", 6, 23),
                            Token("heře!", 24, 29)};

  internal::SplitTokensOnSelectionBoundaries({9, 12}, &tokens);

  // clang-format off
  EXPECT_THAT(tokens, ElementsAreArray(
                          {Token("Hělló", 0, 5),
                           Token("fěě", 6, 9),
                           Token("bař", 9, 12),
                           Token("@google.com", 12, 23),
                           Token("heře!", 24, 29)}));
  // clang-format on
}

TEST(FeatureProcessorTest, SplitTokensOnSelectionBoundariesBegin) {
  std::vector<Token> tokens{Token("Hělló", 0, 5),
                            Token("fěěbař@google.com", 6, 23),
                            Token("heře!", 24, 29)};

  internal::SplitTokensOnSelectionBoundaries({6, 12}, &tokens);

  // clang-format off
  EXPECT_THAT(tokens, ElementsAreArray(
                          {Token("Hělló", 0, 5),
                           Token("fěěbař", 6, 12),
                           Token("@google.com", 12, 23),
                           Token("heře!", 24, 29)}));
  // clang-format on
}

TEST(FeatureProcessorTest, SplitTokensOnSelectionBoundariesEnd) {
  std::vector<Token> tokens{Token("Hělló", 0, 5),
                            Token("fěěbař@google.com", 6, 23),
                            Token("heře!", 24, 29)};

  internal::SplitTokensOnSelectionBoundaries({9, 23}, &tokens);

  // clang-format off
  EXPECT_THAT(tokens, ElementsAreArray(
                          {Token("Hělló", 0, 5),
                           Token("fěě", 6, 9),
                           Token("bař@google.com", 9, 23),
                           Token("heře!", 24, 29)}));
  // clang-format on
}

TEST(FeatureProcessorTest, SplitTokensOnSelectionBoundariesWhole) {
  std::vector<Token> tokens{Token("Hělló", 0, 5),
                            Token("fěěbař@google.com", 6, 23),
                            Token("heře!", 24, 29)};

  internal::SplitTokensOnSelectionBoundaries({6, 23}, &tokens);

  // clang-format off
  EXPECT_THAT(tokens, ElementsAreArray(
                          {Token("Hělló", 0, 5),
                           Token("fěěbař@google.com", 6, 23),
                           Token("heře!", 24, 29)}));
  // clang-format on
}

TEST(FeatureProcessorTest, SplitTokensOnSelectionBoundariesCrossToken) {
  std::vector<Token> tokens{Token("Hělló", 0, 5),
                            Token("fěěbař@google.com", 6, 23),
                            Token("heře!", 24, 29)};

  internal::SplitTokensOnSelectionBoundaries({2, 9}, &tokens);

  // clang-format off
  EXPECT_THAT(tokens, ElementsAreArray(
                          {Token("Hě", 0, 2),
                           Token("lló", 2, 5),
                           Token("fěě", 6, 9),
                           Token("bař@google.com", 9, 23),
                           Token("heře!", 24, 29)}));
  // clang-format on
}

TEST(FeatureProcessorTest, KeepLineWithClickFirst) {
  const std::string context = "Fiřst Lině\nSěcond Lině\nThiřd Lině";
  const CodepointSpan span = {0, 5};
  // clang-format off
  std::vector<Token> tokens = {Token("Fiřst", 0, 5),
                               Token("Lině", 6, 10),
                               Token("Sěcond", 11, 17),
                               Token("Lině", 18, 22),
                               Token("Thiřd", 23, 28),
                               Token("Lině", 29, 33)};
  // clang-format on

  // Keeps the first line.
  internal::StripTokensFromOtherLines(context, span, &tokens);
  EXPECT_THAT(tokens,
              ElementsAreArray({Token("Fiřst", 0, 5), Token("Lině", 6, 10)}));
}

TEST(FeatureProcessorTest, KeepLineWithClickSecond) {
  const std::string context = "Fiřst Lině\nSěcond Lině\nThiřd Lině";
  const CodepointSpan span = {18, 22};
  // clang-format off
  std::vector<Token> tokens = {Token("Fiřst", 0, 5),
                               Token("Lině", 6, 10),
                               Token("Sěcond", 11, 17),
                               Token("Lině", 18, 22),
                               Token("Thiřd", 23, 28),
                               Token("Lině", 29, 33)};
  // clang-format on

  // Keeps the first line.
  internal::StripTokensFromOtherLines(context, span, &tokens);
  EXPECT_THAT(tokens, ElementsAreArray(
                          {Token("Sěcond", 11, 17), Token("Lině", 18, 22)}));
}

TEST(FeatureProcessorTest, KeepLineWithClickThird) {
  const std::string context = "Fiřst Lině\nSěcond Lině\nThiřd Lině";
  const CodepointSpan span = {24, 33};
  // clang-format off
  std::vector<Token> tokens = {Token("Fiřst", 0, 5),
                               Token("Lině", 6, 10),
                               Token("Sěcond", 11, 17),
                               Token("Lině", 18, 22),
                               Token("Thiřd", 23, 28),
                               Token("Lině", 29, 33)};
  // clang-format on

  // Keeps the first line.
  internal::StripTokensFromOtherLines(context, span, &tokens);
  EXPECT_THAT(tokens, ElementsAreArray(
                          {Token("Thiřd", 23, 28), Token("Lině", 29, 33)}));
}

TEST(FeatureProcessorTest, KeepLineWithClickSecondWithPipe) {
  const std::string context = "Fiřst Lině|Sěcond Lině\nThiřd Lině";
  const CodepointSpan span = {18, 22};
  // clang-format off
  std::vector<Token> tokens = {Token("Fiřst", 0, 5),
                               Token("Lině", 6, 10),
                               Token("Sěcond", 11, 17),
                               Token("Lině", 18, 22),
                               Token("Thiřd", 23, 28),
                               Token("Lině", 29, 33)};
  // clang-format on

  // Keeps the first line.
  internal::StripTokensFromOtherLines(context, span, &tokens);
  EXPECT_THAT(tokens, ElementsAreArray(
                          {Token("Sěcond", 11, 17), Token("Lině", 18, 22)}));
}

TEST(FeatureProcessorTest, KeepLineWithCrosslineClick) {
  const std::string context = "Fiřst Lině\nSěcond Lině\nThiřd Lině";
  const CodepointSpan span = {5, 23};
  // clang-format off
  std::vector<Token> tokens = {Token("Fiřst", 0, 5),
                               Token("Lině", 6, 10),
                               Token("Sěcond", 18, 23),
                               Token("Lině", 19, 23),
                               Token("Thiřd", 23, 28),
                               Token("Lině", 29, 33)};
  // clang-format on

  // Keeps the first line.
  internal::StripTokensFromOtherLines(context, span, &tokens);
  EXPECT_THAT(tokens, ElementsAreArray(
                          {Token("Fiřst", 0, 5), Token("Lině", 6, 10),
                           Token("Sěcond", 18, 23), Token("Lině", 19, 23),
                           Token("Thiřd", 23, 28), Token("Lině", 29, 33)}));
}

class TestingFeatureProcessor : public FeatureProcessor {
 public:
  using FeatureProcessor::FeatureProcessor;
  using FeatureProcessor::SpanToLabel;
  using FeatureProcessor::SupportedCodepointsRatio;
  using FeatureProcessor::IsCodepointInRanges;
  using FeatureProcessor::ICUTokenize;
  using FeatureProcessor::supported_codepoint_ranges_;
};

TEST(FeatureProcessorTest, SpanToLabel) {
  FeatureProcessorOptions options;
  options.set_context_size(1);
  options.set_max_selection_span(1);
  options.set_snap_label_span_boundaries_to_containing_tokens(false);

  TokenizationCodepointRange* config =
      options.add_tokenization_codepoint_config();
  config->set_start(32);
  config->set_end(33);
  config->set_role(TokenizationCodepointRange::WHITESPACE_SEPARATOR);

  TestingFeatureProcessor feature_processor(options);
  std::vector<Token> tokens = feature_processor.Tokenize("one, two, three");
  ASSERT_EQ(3, tokens.size());
  int label;
  ASSERT_TRUE(feature_processor.SpanToLabel({5, 8}, tokens, &label));
  EXPECT_EQ(kInvalidLabel, label);
  ASSERT_TRUE(feature_processor.SpanToLabel({5, 9}, tokens, &label));
  EXPECT_NE(kInvalidLabel, label);
  TokenSpan token_span;
  feature_processor.LabelToTokenSpan(label, &token_span);
  EXPECT_EQ(0, token_span.first);
  EXPECT_EQ(0, token_span.second);

  // Reconfigure with snapping enabled.
  options.set_snap_label_span_boundaries_to_containing_tokens(true);
  TestingFeatureProcessor feature_processor2(options);
  int label2;
  ASSERT_TRUE(feature_processor2.SpanToLabel({5, 8}, tokens, &label2));
  EXPECT_EQ(label, label2);
  ASSERT_TRUE(feature_processor2.SpanToLabel({6, 9}, tokens, &label2));
  EXPECT_EQ(label, label2);
  ASSERT_TRUE(feature_processor2.SpanToLabel({5, 9}, tokens, &label2));
  EXPECT_EQ(label, label2);

  // Cross a token boundary.
  ASSERT_TRUE(feature_processor2.SpanToLabel({4, 9}, tokens, &label2));
  EXPECT_EQ(kInvalidLabel, label2);
  ASSERT_TRUE(feature_processor2.SpanToLabel({5, 10}, tokens, &label2));
  EXPECT_EQ(kInvalidLabel, label2);

  // Multiple tokens.
  options.set_context_size(2);
  options.set_max_selection_span(2);
  TestingFeatureProcessor feature_processor3(options);
  tokens = feature_processor3.Tokenize("zero, one, two, three, four");
  ASSERT_TRUE(feature_processor3.SpanToLabel({6, 15}, tokens, &label2));
  EXPECT_NE(kInvalidLabel, label2);
  feature_processor3.LabelToTokenSpan(label2, &token_span);
  EXPECT_EQ(1, token_span.first);
  EXPECT_EQ(0, token_span.second);

  int label3;
  ASSERT_TRUE(feature_processor3.SpanToLabel({6, 14}, tokens, &label3));
  EXPECT_EQ(label2, label3);
  ASSERT_TRUE(feature_processor3.SpanToLabel({6, 13}, tokens, &label3));
  EXPECT_EQ(label2, label3);
  ASSERT_TRUE(feature_processor3.SpanToLabel({7, 13}, tokens, &label3));
  EXPECT_EQ(label2, label3);
}

TEST(FeatureProcessorTest, CenterTokenFromClick) {
  int token_index;

  // Exactly aligned indices.
  token_index = internal::CenterTokenFromClick(
      {6, 11},
      {Token("Hělló", 0, 5), Token("world", 6, 11), Token("heře!", 12, 17)});
  EXPECT_EQ(token_index, 1);

  // Click is contained in a token.
  token_index = internal::CenterTokenFromClick(
      {13, 17},
      {Token("Hělló", 0, 5), Token("world", 6, 11), Token("heře!", 12, 17)});
  EXPECT_EQ(token_index, 2);

  // Click spans two tokens.
  token_index = internal::CenterTokenFromClick(
      {6, 17},
      {Token("Hělló", 0, 5), Token("world", 6, 11), Token("heře!", 12, 17)});
  EXPECT_EQ(token_index, kInvalidIndex);
}

TEST(FeatureProcessorTest, CenterTokenFromMiddleOfSelection) {
  int token_index;

  // Selection of length 3. Exactly aligned indices.
  token_index = internal::CenterTokenFromMiddleOfSelection(
      {7, 27},
      {Token("Token1", 0, 6), Token("Token2", 7, 13), Token("Token3", 14, 20),
       Token("Token4", 21, 27), Token("Token5", 28, 34)});
  EXPECT_EQ(token_index, 2);

  // Selection of length 1 token. Exactly aligned indices.
  token_index = internal::CenterTokenFromMiddleOfSelection(
      {21, 27},
      {Token("Token1", 0, 6), Token("Token2", 7, 13), Token("Token3", 14, 20),
       Token("Token4", 21, 27), Token("Token5", 28, 34)});
  EXPECT_EQ(token_index, 3);

  // Selection marks sub-token range, with no tokens in it.
  token_index = internal::CenterTokenFromMiddleOfSelection(
      {29, 33},
      {Token("Token1", 0, 6), Token("Token2", 7, 13), Token("Token3", 14, 20),
       Token("Token4", 21, 27), Token("Token5", 28, 34)});
  EXPECT_EQ(token_index, kInvalidIndex);

  // Selection of length 2. Sub-token indices.
  token_index = internal::CenterTokenFromMiddleOfSelection(
      {3, 25},
      {Token("Token1", 0, 6), Token("Token2", 7, 13), Token("Token3", 14, 20),
       Token("Token4", 21, 27), Token("Token5", 28, 34)});
  EXPECT_EQ(token_index, 1);

  // Selection of length 1. Sub-token indices.
  token_index = internal::CenterTokenFromMiddleOfSelection(
      {22, 34},
      {Token("Token1", 0, 6), Token("Token2", 7, 13), Token("Token3", 14, 20),
       Token("Token4", 21, 27), Token("Token5", 28, 34)});
  EXPECT_EQ(token_index, 4);

  // Some invalid ones.
  token_index = internal::CenterTokenFromMiddleOfSelection({7, 27}, {});
  EXPECT_EQ(token_index, -1);
}

TEST(FeatureProcessorTest, SupportedCodepointsRatio) {
  FeatureProcessorOptions options;
  options.set_context_size(2);
  options.set_max_selection_span(2);
  options.set_snap_label_span_boundaries_to_containing_tokens(false);

  TokenizationCodepointRange* config =
      options.add_tokenization_codepoint_config();
  config->set_start(32);
  config->set_end(33);
  config->set_role(TokenizationCodepointRange::WHITESPACE_SEPARATOR);

  FeatureProcessorOptions::CodepointRange* range;
  range = options.add_supported_codepoint_ranges();
  range->set_start(0);
  range->set_end(128);

  range = options.add_supported_codepoint_ranges();
  range->set_start(10000);
  range->set_end(10001);

  range = options.add_supported_codepoint_ranges();
  range->set_start(20000);
  range->set_end(30000);

  TestingFeatureProcessor feature_processor(options);
  EXPECT_THAT(feature_processor.SupportedCodepointsRatio(
                  1, feature_processor.Tokenize("aaa bbb ccc")),
              FloatEq(1.0));
  EXPECT_THAT(feature_processor.SupportedCodepointsRatio(
                  1, feature_processor.Tokenize("aaa bbb ěěě")),
              FloatEq(2.0 / 3));
  EXPECT_THAT(feature_processor.SupportedCodepointsRatio(
                  1, feature_processor.Tokenize("ěěě řřř ěěě")),
              FloatEq(0.0));
  EXPECT_FALSE(feature_processor.IsCodepointInRanges(
      -1, feature_processor.supported_codepoint_ranges_));
  EXPECT_TRUE(feature_processor.IsCodepointInRanges(
      0, feature_processor.supported_codepoint_ranges_));
  EXPECT_TRUE(feature_processor.IsCodepointInRanges(
      10, feature_processor.supported_codepoint_ranges_));
  EXPECT_TRUE(feature_processor.IsCodepointInRanges(
      127, feature_processor.supported_codepoint_ranges_));
  EXPECT_FALSE(feature_processor.IsCodepointInRanges(
      128, feature_processor.supported_codepoint_ranges_));
  EXPECT_FALSE(feature_processor.IsCodepointInRanges(
      9999, feature_processor.supported_codepoint_ranges_));
  EXPECT_TRUE(feature_processor.IsCodepointInRanges(
      10000, feature_processor.supported_codepoint_ranges_));
  EXPECT_FALSE(feature_processor.IsCodepointInRanges(
      10001, feature_processor.supported_codepoint_ranges_));
  EXPECT_TRUE(feature_processor.IsCodepointInRanges(
      25000, feature_processor.supported_codepoint_ranges_));

  std::vector<Token> tokens;
  int click_pos;
  std::vector<float> extra_features;
  std::unique_ptr<CachedFeatures> cached_features;

  auto feature_fn = [](const std::vector<int>& sparse_features,
                       const std::vector<float>& dense_features,
                       float* embedding) { return true; };

  options.set_min_supported_codepoint_ratio(0.0);
  TestingFeatureProcessor feature_processor2(options);
  EXPECT_TRUE(feature_processor2.ExtractFeatures("ěěě řřř eee", {4, 7}, {0, 0},
                                                 feature_fn, 2, &tokens,
                                                 &click_pos, &cached_features));

  options.set_min_supported_codepoint_ratio(0.2);
  TestingFeatureProcessor feature_processor3(options);
  EXPECT_TRUE(feature_processor3.ExtractFeatures("ěěě řřř eee", {4, 7}, {0, 0},
                                                 feature_fn, 2, &tokens,
                                                 &click_pos, &cached_features));

  options.set_min_supported_codepoint_ratio(0.5);
  TestingFeatureProcessor feature_processor4(options);
  EXPECT_FALSE(feature_processor4.ExtractFeatures(
      "ěěě řřř eee", {4, 7}, {0, 0}, feature_fn, 2, &tokens, &click_pos,
      &cached_features));
}

TEST(FeatureProcessorTest, StripUnusedTokensWithNoRelativeClick) {
  std::vector<Token> tokens_orig{
      Token("0", 0, 0), Token("1", 0, 0), Token("2", 0, 0),  Token("3", 0, 0),
      Token("4", 0, 0), Token("5", 0, 0), Token("6", 0, 0),  Token("7", 0, 0),
      Token("8", 0, 0), Token("9", 0, 0), Token("10", 0, 0), Token("11", 0, 0),
      Token("12", 0, 0)};

  std::vector<Token> tokens;
  int click_index;

  // Try to click first token and see if it gets padded from left.
  tokens = tokens_orig;
  click_index = 0;
  internal::StripOrPadTokens({0, 0}, 2, &tokens, &click_index);
  // clang-format off
  EXPECT_EQ(tokens, std::vector<Token>({Token(),
                                        Token(),
                                        Token("0", 0, 0),
                                        Token("1", 0, 0),
                                        Token("2", 0, 0)}));
  // clang-format on
  EXPECT_EQ(click_index, 2);

  // When we click the second token nothing should get padded.
  tokens = tokens_orig;
  click_index = 2;
  internal::StripOrPadTokens({0, 0}, 2, &tokens, &click_index);
  // clang-format off
  EXPECT_EQ(tokens, std::vector<Token>({Token("0", 0, 0),
                                        Token("1", 0, 0),
                                        Token("2", 0, 0),
                                        Token("3", 0, 0),
                                        Token("4", 0, 0)}));
  // clang-format on
  EXPECT_EQ(click_index, 2);

  // When we click the last token tokens should get padded from the right.
  tokens = tokens_orig;
  click_index = 12;
  internal::StripOrPadTokens({0, 0}, 2, &tokens, &click_index);
  // clang-format off
  EXPECT_EQ(tokens, std::vector<Token>({Token("10", 0, 0),
                                        Token("11", 0, 0),
                                        Token("12", 0, 0),
                                        Token(),
                                        Token()}));
  // clang-format on
  EXPECT_EQ(click_index, 2);
}

TEST(FeatureProcessorTest, StripUnusedTokensWithRelativeClick) {
  std::vector<Token> tokens_orig{
      Token("0", 0, 0), Token("1", 0, 0), Token("2", 0, 0),  Token("3", 0, 0),
      Token("4", 0, 0), Token("5", 0, 0), Token("6", 0, 0),  Token("7", 0, 0),
      Token("8", 0, 0), Token("9", 0, 0), Token("10", 0, 0), Token("11", 0, 0),
      Token("12", 0, 0)};

  std::vector<Token> tokens;
  int click_index;

  // Try to click first token and see if it gets padded from left to maximum
  // context_size.
  tokens = tokens_orig;
  click_index = 0;
  internal::StripOrPadTokens({2, 3}, 2, &tokens, &click_index);
  // clang-format off
  EXPECT_EQ(tokens, std::vector<Token>({Token(),
                                        Token(),
                                        Token("0", 0, 0),
                                        Token("1", 0, 0),
                                        Token("2", 0, 0),
                                        Token("3", 0, 0),
                                        Token("4", 0, 0),
                                        Token("5", 0, 0)}));
  // clang-format on
  EXPECT_EQ(click_index, 2);

  // Clicking to the middle with enough context should not produce any padding.
  tokens = tokens_orig;
  click_index = 6;
  internal::StripOrPadTokens({3, 1}, 2, &tokens, &click_index);
  // clang-format off
  EXPECT_EQ(tokens, std::vector<Token>({Token("1", 0, 0),
                                        Token("2", 0, 0),
                                        Token("3", 0, 0),
                                        Token("4", 0, 0),
                                        Token("5", 0, 0),
                                        Token("6", 0, 0),
                                        Token("7", 0, 0),
                                        Token("8", 0, 0),
                                        Token("9", 0, 0)}));
  // clang-format on
  EXPECT_EQ(click_index, 5);

  // Clicking at the end should pad right to maximum context_size.
  tokens = tokens_orig;
  click_index = 11;
  internal::StripOrPadTokens({3, 1}, 2, &tokens, &click_index);
  // clang-format off
  EXPECT_EQ(tokens, std::vector<Token>({Token("6", 0, 0),
                                        Token("7", 0, 0),
                                        Token("8", 0, 0),
                                        Token("9", 0, 0),
                                        Token("10", 0, 0),
                                        Token("11", 0, 0),
                                        Token("12", 0, 0),
                                        Token(),
                                        Token()}));
  // clang-format on
  EXPECT_EQ(click_index, 5);
}

TEST(FeatureProcessorTest, ICUTokenize) {
  FeatureProcessorOptions options;
  options.set_tokenization_type(
      libtextclassifier::FeatureProcessorOptions::ICU);

  TestingFeatureProcessor feature_processor(options);
  std::vector<Token> tokens = feature_processor.Tokenize("พระบาทสมเด็จพระปรมิ");
  ASSERT_EQ(tokens,
            // clang-format off
            std::vector<Token>({Token("พระบาท", 0, 6),
                                Token("สมเด็จ", 6, 12),
                                Token("พระ", 12, 15),
                                Token("ปร", 15, 17),
                                Token("มิ", 17, 19)}));
  // clang-format on
}

TEST(FeatureProcessorTest, ICUTokenizeWithWhitespaces) {
  FeatureProcessorOptions options;
  options.set_tokenization_type(
      libtextclassifier::FeatureProcessorOptions::ICU);
  options.set_icu_preserve_whitespace_tokens(true);

  TestingFeatureProcessor feature_processor(options);
  std::vector<Token> tokens =
      feature_processor.Tokenize("พระบาท สมเด็จ พระ ปร มิ");
  ASSERT_EQ(tokens,
            // clang-format off
            std::vector<Token>({Token("พระบาท", 0, 6),
                                Token(" ", 6, 7),
                                Token("สมเด็จ", 7, 13),
                                Token(" ", 13, 14),
                                Token("พระ", 14, 17),
                                Token(" ", 17, 18),
                                Token("ปร", 18, 20),
                                Token(" ", 20, 21),
                                Token("มิ", 21, 23)}));
  // clang-format on
}

TEST(FeatureProcessorTest, MixedTokenize) {
  FeatureProcessorOptions options;
  options.set_tokenization_type(
      libtextclassifier::FeatureProcessorOptions::MIXED);

  TokenizationCodepointRange* config =
      options.add_tokenization_codepoint_config();
  config->set_start(32);
  config->set_end(33);
  config->set_role(TokenizationCodepointRange::WHITESPACE_SEPARATOR);

  FeatureProcessorOptions::CodepointRange* range;
  range = options.add_internal_tokenizer_codepoint_ranges();
  range->set_start(0);
  range->set_end(128);

  range = options.add_internal_tokenizer_codepoint_ranges();
  range->set_start(128);
  range->set_end(256);

  range = options.add_internal_tokenizer_codepoint_ranges();
  range->set_start(256);
  range->set_end(384);

  range = options.add_internal_tokenizer_codepoint_ranges();
  range->set_start(384);
  range->set_end(592);

  TestingFeatureProcessor feature_processor(options);
  std::vector<Token> tokens = feature_processor.Tokenize(
      "こんにちはJapanese-ląnguagę text 世界 http://www.google.com/");
  ASSERT_EQ(tokens,
            // clang-format off
            std::vector<Token>({Token("こんにちは", 0, 5),
                                Token("Japanese-ląnguagę", 5, 22),
                                Token("text", 23, 27),
                                Token("世界", 28, 30),
                                Token("http://www.google.com/", 31, 53)}));
  // clang-format on
}

}  // namespace
}  // namespace libtextclassifier
