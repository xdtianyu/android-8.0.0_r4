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

// Feature processing for FFModel (feed-forward SmartSelection model).

#ifndef LIBTEXTCLASSIFIER_SMARTSELECT_FEATURE_PROCESSOR_H_
#define LIBTEXTCLASSIFIER_SMARTSELECT_FEATURE_PROCESSOR_H_

#include <memory>
#include <string>
#include <vector>

#include "smartselect/cached-features.h"
#include "smartselect/text-classification-model.pb.h"
#include "smartselect/token-feature-extractor.h"
#include "smartselect/tokenizer.h"
#include "smartselect/types.h"
#include "util/base/logging.h"
#include "util/utf8/unicodetext.h"

namespace libtextclassifier {

constexpr int kInvalidLabel = -1;

// Maps a vector of sparse features and a vector of dense features to a vector
// of features that combines both.
// The output is written to the memory location pointed to  by the last float*
// argument.
// Returns true on success false on failure.
using FeatureVectorFn = std::function<bool(const std::vector<int>&,
                                           const std::vector<float>&, float*)>;

namespace internal {

// Parses the serialized protocol buffer.
FeatureProcessorOptions ParseSerializedOptions(
    const std::string& serialized_options);

TokenFeatureExtractorOptions BuildTokenFeatureExtractorOptions(
    const FeatureProcessorOptions& options);

// Removes tokens that are not part of a line of the context which contains
// given span.
void StripTokensFromOtherLines(const std::string& context, CodepointSpan span,
                               std::vector<Token>* tokens);

// Splits tokens that contain the selection boundary inside them.
// E.g. "foo{bar}@google.com" -> "foo", "bar", "@google.com"
void SplitTokensOnSelectionBoundaries(CodepointSpan selection,
                                      std::vector<Token>* tokens);

// Returns the index of token that corresponds to the codepoint span.
int CenterTokenFromClick(CodepointSpan span, const std::vector<Token>& tokens);

// Returns the index of token that corresponds to the middle of the  codepoint
// span.
int CenterTokenFromMiddleOfSelection(
    CodepointSpan span, const std::vector<Token>& selectable_tokens);

// Strips the tokens from the tokens vector that are not used for feature
// extraction because they are out of scope, or pads them so that there is
// enough tokens in the required context_size for all inferences with a click
// in relative_click_span.
void StripOrPadTokens(TokenSpan relative_click_span, int context_size,
                      std::vector<Token>* tokens, int* click_pos);

}  // namespace internal

// Converts a codepoint span to a token span in the given list of tokens.
TokenSpan CodepointSpanToTokenSpan(const std::vector<Token>& selectable_tokens,
                                   CodepointSpan codepoint_span);

// Converts a token span to a codepoint span in the given list of tokens.
CodepointSpan TokenSpanToCodepointSpan(
    const std::vector<Token>& selectable_tokens, TokenSpan token_span);

// Takes care of preparing features for the span prediction model.
class FeatureProcessor {
 public:
  explicit FeatureProcessor(const FeatureProcessorOptions& options)
      : feature_extractor_(
            internal::BuildTokenFeatureExtractorOptions(options)),
        options_(options),
        tokenizer_({options.tokenization_codepoint_config().begin(),
                    options.tokenization_codepoint_config().end()}) {
    MakeLabelMaps();
    PrepareCodepointRanges({options.supported_codepoint_ranges().begin(),
                            options.supported_codepoint_ranges().end()},
                           &supported_codepoint_ranges_);
    PrepareCodepointRanges(
        {options.internal_tokenizer_codepoint_ranges().begin(),
         options.internal_tokenizer_codepoint_ranges().end()},
        &internal_tokenizer_codepoint_ranges_);
  }

  explicit FeatureProcessor(const std::string& serialized_options)
      : FeatureProcessor(internal::ParseSerializedOptions(serialized_options)) {
  }

  // Tokenizes the input string using the selected tokenization method.
  std::vector<Token> Tokenize(const std::string& utf8_text) const;

  // Converts a label into a token span.
  bool LabelToTokenSpan(int label, TokenSpan* token_span) const;

  // Gets the total number of selection labels.
  int GetSelectionLabelCount() const { return label_to_selection_.size(); }

  // Gets the string value for given collection label.
  std::string LabelToCollection(int label) const;

  // Gets the total number of collections of the model.
  int NumCollections() const { return collection_to_label_.size(); }

  // Gets the name of the default collection.
  std::string GetDefaultCollection() const;

  const FeatureProcessorOptions& GetOptions() const { return options_; }

  // Tokenizes the context and input span, and finds the click position.
  void TokenizeAndFindClick(const std::string& context,
                            CodepointSpan input_span,
                            std::vector<Token>* tokens, int* click_pos) const;

  // Extracts features as a CachedFeatures object that can be used for repeated
  // inference over token spans in the given context.
  bool ExtractFeatures(const std::string& context, CodepointSpan input_span,
                       TokenSpan relative_click_span,
                       const FeatureVectorFn& feature_vector_fn,
                       int feature_vector_size, std::vector<Token>* tokens,
                       int* click_pos,
                       std::unique_ptr<CachedFeatures>* cached_features) const;

  // Fills selection_label_spans with CodepointSpans that correspond to the
  // selection labels. The CodepointSpans are based on the codepoint ranges of
  // given tokens.
  bool SelectionLabelSpans(
      VectorSpan<Token> tokens,
      std::vector<CodepointSpan>* selection_label_spans) const;

  int DenseFeaturesCount() const {
    return feature_extractor_.DenseFeaturesCount();
  }

 protected:
  // Represents a codepoint range [start, end).
  struct CodepointRange {
    int32 start;
    int32 end;

    CodepointRange(int32 arg_start, int32 arg_end)
        : start(arg_start), end(arg_end) {}
  };

  // Returns the class id corresponding to the given string collection
  // identifier. There is a catch-all class id that the function returns for
  // unknown collections.
  int CollectionToLabel(const std::string& collection) const;

  // Prepares mapping from collection names to labels.
  void MakeLabelMaps();

  // Gets the number of spannable tokens for the model.
  //
  // Spannable tokens are those tokens of context, which the model predicts
  // selection spans over (i.e., there is 1:1 correspondence between the output
  // classes of the model and each of the spannable tokens).
  int GetNumContextTokens() const { return options_.context_size() * 2 + 1; }

  // Converts a label into a span of codepoint indices corresponding to it
  // given output_tokens.
  bool LabelToSpan(int label, const VectorSpan<Token>& output_tokens,
                   CodepointSpan* span) const;

  // Converts a span to the corresponding label given output_tokens.
  bool SpanToLabel(const std::pair<CodepointIndex, CodepointIndex>& span,
                   const std::vector<Token>& output_tokens, int* label) const;

  // Converts a token span to the corresponding label.
  int TokenSpanToLabel(const std::pair<TokenIndex, TokenIndex>& span) const;

  void PrepareCodepointRanges(
      const std::vector<FeatureProcessorOptions::CodepointRange>&
          codepoint_ranges,
      std::vector<CodepointRange>* prepared_codepoint_ranges);

  // Returns the ratio of supported codepoints to total number of codepoints in
  // the input context around given click position.
  float SupportedCodepointsRatio(int click_pos,
                                 const std::vector<Token>& tokens) const;

  // Returns true if given codepoint is covered by the given sorted vector of
  // codepoint ranges.
  bool IsCodepointInRanges(
      int codepoint, const std::vector<CodepointRange>& codepoint_ranges) const;

  // Finds the center token index in tokens vector, using the method defined
  // in options_.
  int FindCenterToken(CodepointSpan span,
                      const std::vector<Token>& tokens) const;

  // Tokenizes the input text using ICU tokenizer.
  bool ICUTokenize(const std::string& context,
                   std::vector<Token>* result) const;

  // Takes the result of ICU tokenization and retokenizes stretches of tokens
  // made of a specific subset of characters using the internal tokenizer.
  void InternalRetokenize(const std::string& context,
                          std::vector<Token>* tokens) const;

  // Tokenizes a substring of the unicode string, appending the resulting tokens
  // to the output vector. The resulting tokens have bounds relative to the full
  // string. Does nothing if the start of the span is negative.
  void TokenizeSubstring(const UnicodeText& unicode_text, CodepointSpan span,
                         std::vector<Token>* result) const;

  const TokenFeatureExtractor feature_extractor_;

  // Codepoint ranges that define what codepoints are supported by the model.
  // NOTE: Must be sorted.
  std::vector<CodepointRange> supported_codepoint_ranges_;

  // Codepoint ranges that define which tokens (consisting of which codepoints)
  // should be re-tokenized with the internal tokenizer in the mixed
  // tokenization mode.
  // NOTE: Must be sorted.
  std::vector<CodepointRange> internal_tokenizer_codepoint_ranges_;

 private:
  const FeatureProcessorOptions options_;

  // Mapping between token selection spans and labels ids.
  std::map<TokenSpan, int> selection_to_label_;
  std::vector<TokenSpan> label_to_selection_;

  // Mapping between collections and labels.
  std::map<std::string, int> collection_to_label_;

  Tokenizer tokenizer_;
};

}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_SMARTSELECT_FEATURE_PROCESSOR_H_
