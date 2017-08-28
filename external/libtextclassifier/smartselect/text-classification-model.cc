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

#include "smartselect/text-classification-model.h"

#include <cmath>
#include <iterator>
#include <numeric>

#include "common/embedding-network.h"
#include "common/feature-extractor.h"
#include "common/memory_image/embedding-network-params-from-image.h"
#include "common/memory_image/memory-image-reader.h"
#include "common/mmap.h"
#include "common/softmax.h"
#include "smartselect/text-classification-model.pb.h"
#include "util/base/logging.h"
#include "util/utf8/unicodetext.h"
#include "unicode/uchar.h"

namespace libtextclassifier {

using nlp_core::EmbeddingNetwork;
using nlp_core::EmbeddingNetworkProto;
using nlp_core::FeatureVector;
using nlp_core::MemoryImageReader;
using nlp_core::MmapFile;
using nlp_core::MmapHandle;
using nlp_core::ScopedMmap;

namespace {

int CountDigits(const std::string& str, CodepointSpan selection_indices) {
  int count = 0;
  int i = 0;
  const UnicodeText unicode_str = UTF8ToUnicodeText(str, /*do_copy=*/false);
  for (auto it = unicode_str.begin(); it != unicode_str.end(); ++it, ++i) {
    if (i >= selection_indices.first && i < selection_indices.second &&
        u_isdigit(*it)) {
      ++count;
    }
  }
  return count;
}

}  // namespace

CodepointSpan TextClassificationModel::StripPunctuation(
    CodepointSpan selection, const std::string& context) const {
  UnicodeText context_unicode = UTF8ToUnicodeText(context, /*do_copy=*/false);
  int context_length =
      std::distance(context_unicode.begin(), context_unicode.end());

  // Check that the indices are valid.
  if (selection.first < 0 || selection.first > context_length ||
      selection.second < 0 || selection.second > context_length) {
    return selection;
  }

  // Move the left border until we encounter a non-punctuation character.
  UnicodeText::const_iterator it_from_begin = context_unicode.begin();
  std::advance(it_from_begin, selection.first);
  for (; punctuation_to_strip_.find(*it_from_begin) !=
         punctuation_to_strip_.end();
       ++it_from_begin, ++selection.first) {
  }

  // Unless we are already at the end, move the right border until we encounter
  // a non-punctuation character.
  UnicodeText::const_iterator it_from_end = context_unicode.begin();
  std::advance(it_from_end, selection.second);
  if (it_from_begin != it_from_end) {
    --it_from_end;
    for (; punctuation_to_strip_.find(*it_from_end) !=
           punctuation_to_strip_.end();
         --it_from_end, --selection.second) {
    }
    return selection;
  } else {
    // When the token is all punctuation.
    return {0, 0};
  }
}

TextClassificationModel::TextClassificationModel(int fd) : mmap_(fd) {
  initialized_ = LoadModels(mmap_.handle());
  if (!initialized_) {
    TC_LOG(ERROR) << "Failed to load models";
    return;
  }

  selection_options_ = selection_params_->GetSelectionModelOptions();
  for (const int codepoint : selection_options_.punctuation_to_strip()) {
    punctuation_to_strip_.insert(codepoint);
  }

  sharing_options_ = selection_params_->GetSharingModelOptions();
}

namespace {

// Converts sparse features vector to nlp_core::FeatureVector.
void SparseFeaturesToFeatureVector(
    const std::vector<int> sparse_features,
    const nlp_core::NumericFeatureType& feature_type,
    nlp_core::FeatureVector* result) {
  for (int feature_id : sparse_features) {
    const int64 feature_value =
        nlp_core::FloatFeatureValue(feature_id, 1.0 / sparse_features.size())
            .discrete_value;
    result->add(const_cast<nlp_core::NumericFeatureType*>(&feature_type),
                feature_value);
  }
}

// Returns a function that can be used for mapping sparse and dense features
// to a float feature vector.
// NOTE: The network object needs to be available at the time when the returned
// function object is used.
FeatureVectorFn CreateFeatureVectorFn(const EmbeddingNetwork& network,
                                      int sparse_embedding_size) {
  const nlp_core::NumericFeatureType feature_type("chargram_continuous", 0);
  return [&network, sparse_embedding_size, feature_type](
             const std::vector<int>& sparse_features,
             const std::vector<float>& dense_features, float* embedding) {
    nlp_core::FeatureVector feature_vector;
    SparseFeaturesToFeatureVector(sparse_features, feature_type,
                                  &feature_vector);

    if (network.GetEmbedding(feature_vector, 0, embedding)) {
      for (int i = 0; i < dense_features.size(); i++) {
        embedding[sparse_embedding_size + i] = dense_features[i];
      }
      return true;
    } else {
      return false;
    }
  };
}

void ParseMergedModel(const MmapHandle& mmap_handle,
                      const char** selection_model, int* selection_model_length,
                      const char** sharing_model, int* sharing_model_length) {
  // Read the length of the selection model.
  const char* model_data = reinterpret_cast<const char*>(mmap_handle.start());
  *selection_model_length =
      LittleEndian::ToHost32(*reinterpret_cast<const uint32*>(model_data));
  model_data += sizeof(*selection_model_length);
  *selection_model = model_data;
  model_data += *selection_model_length;

  *sharing_model_length =
      LittleEndian::ToHost32(*reinterpret_cast<const uint32*>(model_data));
  model_data += sizeof(*sharing_model_length);
  *sharing_model = model_data;
}

}  // namespace

bool TextClassificationModel::LoadModels(const MmapHandle& mmap_handle) {
  if (!mmap_handle.ok()) {
    return false;
  }

  const char *selection_model, *sharing_model;
  int selection_model_length, sharing_model_length;
  ParseMergedModel(mmap_handle, &selection_model, &selection_model_length,
                   &sharing_model, &sharing_model_length);

  selection_params_.reset(
      ModelParamsBuilder(selection_model, selection_model_length, nullptr));
  if (!selection_params_.get()) {
    return false;
  }
  selection_network_.reset(new EmbeddingNetwork(selection_params_.get()));
  selection_feature_processor_.reset(
      new FeatureProcessor(selection_params_->GetFeatureProcessorOptions()));
  selection_feature_fn_ = CreateFeatureVectorFn(
      *selection_network_, selection_network_->EmbeddingSize(0));

  sharing_params_.reset(
      ModelParamsBuilder(sharing_model, sharing_model_length,
                         selection_params_->GetEmbeddingParams()));
  if (!sharing_params_.get()) {
    return false;
  }
  sharing_network_.reset(new EmbeddingNetwork(sharing_params_.get()));
  sharing_feature_processor_.reset(
      new FeatureProcessor(sharing_params_->GetFeatureProcessorOptions()));
  sharing_feature_fn_ = CreateFeatureVectorFn(
      *sharing_network_, sharing_network_->EmbeddingSize(0));

  return true;
}

bool ReadSelectionModelOptions(int fd, ModelOptions* model_options) {
  ScopedMmap mmap = ScopedMmap(fd);
  if (!mmap.handle().ok()) {
    TC_LOG(ERROR) << "Can't mmap.";
    return false;
  }

  const char *selection_model, *sharing_model;
  int selection_model_length, sharing_model_length;
  ParseMergedModel(mmap.handle(), &selection_model, &selection_model_length,
                   &sharing_model, &sharing_model_length);

  MemoryImageReader<EmbeddingNetworkProto> reader(selection_model,
                                                  selection_model_length);

  auto model_options_extension_id = model_options_in_embedding_network_proto;
  if (reader.trimmed_proto().HasExtension(model_options_extension_id)) {
    *model_options =
        reader.trimmed_proto().GetExtension(model_options_extension_id);
    return true;
  } else {
    return false;
  }
}

EmbeddingNetwork::Vector TextClassificationModel::InferInternal(
    const std::string& context, CodepointSpan span,
    const FeatureProcessor& feature_processor, const EmbeddingNetwork& network,
    const FeatureVectorFn& feature_vector_fn,
    std::vector<CodepointSpan>* selection_label_spans) const {
  std::vector<Token> tokens;
  int click_pos;
  std::unique_ptr<CachedFeatures> cached_features;
  const int embedding_size = network.EmbeddingSize(0);
  if (!feature_processor.ExtractFeatures(
          context, span, /*relative_click_span=*/{0, 0},
          CreateFeatureVectorFn(network, embedding_size),
          embedding_size + feature_processor.DenseFeaturesCount(), &tokens,
          &click_pos, &cached_features)) {
    TC_LOG(ERROR) << "Could not extract features.";
    return {};
  }

  VectorSpan<float> features;
  VectorSpan<Token> output_tokens;
  if (!cached_features->Get(click_pos, &features, &output_tokens)) {
    TC_LOG(ERROR) << "Could not extract features.";
    return {};
  }

  if (selection_label_spans != nullptr) {
    if (!feature_processor.SelectionLabelSpans(output_tokens,
                                               selection_label_spans)) {
      TC_LOG(ERROR) << "Could not get spans for selection labels.";
      return {};
    }
  }

  std::vector<float> scores;
  network.ComputeLogits(features, &scores);
  return scores;
}

CodepointSpan TextClassificationModel::SuggestSelection(
    const std::string& context, CodepointSpan click_indices) const {
  if (!initialized_) {
    TC_LOG(ERROR) << "Not initialized";
    return click_indices;
  }

  if (std::get<0>(click_indices) >= std::get<1>(click_indices)) {
    TC_LOG(ERROR) << "Trying to run SuggestSelection with invalid indices:"
                  << std::get<0>(click_indices) << " "
                  << std::get<1>(click_indices);
    return click_indices;
  }

  const UnicodeText context_unicode =
      UTF8ToUnicodeText(context, /*do_copy=*/false);
  const int context_length =
      std::distance(context_unicode.begin(), context_unicode.end());
  if (std::get<0>(click_indices) >= context_length ||
      std::get<1>(click_indices) > context_length) {
    return click_indices;
  }

  CodepointSpan result;
  if (selection_options_.enforce_symmetry()) {
    result = SuggestSelectionSymmetrical(context, click_indices);
  } else {
    float score;
    std::tie(result, score) = SuggestSelectionInternal(context, click_indices);
  }

  if (selection_options_.strip_punctuation()) {
    result = StripPunctuation(result, context);
  }

  return result;
}

namespace {

std::pair<CodepointSpan, float> BestSelectionSpan(
    CodepointSpan original_click_indices, const std::vector<float>& scores,
    const std::vector<CodepointSpan>& selection_label_spans) {
  if (!scores.empty()) {
    const int prediction =
        std::max_element(scores.begin(), scores.end()) - scores.begin();
    std::pair<CodepointIndex, CodepointIndex> selection =
        selection_label_spans[prediction];

    if (selection.first == kInvalidIndex || selection.second == kInvalidIndex) {
      TC_LOG(ERROR) << "Invalid indices predicted, returning input: "
                    << prediction << " " << selection.first << " "
                    << selection.second;
      return {original_click_indices, -1.0};
    }

    return {{selection.first, selection.second}, scores[prediction]};
  } else {
    TC_LOG(ERROR) << "Returning default selection: scores.size() = "
                  << scores.size();
    return {original_click_indices, -1.0};
  }
}

}  // namespace

std::pair<CodepointSpan, float>
TextClassificationModel::SuggestSelectionInternal(
    const std::string& context, CodepointSpan click_indices) const {
  if (!initialized_) {
    TC_LOG(ERROR) << "Not initialized";
    return {click_indices, -1.0};
  }

  std::vector<CodepointSpan> selection_label_spans;
  EmbeddingNetwork::Vector scores = InferInternal(
      context, click_indices, *selection_feature_processor_,
      *selection_network_, selection_feature_fn_, &selection_label_spans);
  scores = nlp_core::ComputeSoftmax(scores);

  return BestSelectionSpan(click_indices, scores, selection_label_spans);
}

// Implements a greedy-search-like algorithm for making selections symmetric.
//
// Steps:
// 1. Get a set of selection proposals from places around the clicked word.
// 2. For each proposal (going from highest-scoring), check if the tokens that
//    the proposal selects are still free, in which case it claims them, if a
//    proposal that contains the clicked token is found, it is returned as the
//    suggestion.
//
// This algorithm should ensure that if a selection is proposed, it does not
// matter which word of it was tapped - all of them will lead to the same
// selection.
CodepointSpan TextClassificationModel::SuggestSelectionSymmetrical(
    const std::string& context, CodepointSpan click_indices) const {
  const int symmetry_context_size = selection_options_.symmetry_context_size();
  std::vector<Token> tokens;
  std::unique_ptr<CachedFeatures> cached_features;
  int click_index;
  int embedding_size = selection_network_->EmbeddingSize(0);
  if (!selection_feature_processor_->ExtractFeatures(
          context, click_indices, /*relative_click_span=*/
          {symmetry_context_size, symmetry_context_size + 1},
          selection_feature_fn_,
          embedding_size + selection_feature_processor_->DenseFeaturesCount(),
          &tokens, &click_index, &cached_features)) {
    TC_LOG(ERROR) << "Couldn't ExtractFeatures.";
    return click_indices;
  }

  // Scan in the symmetry context for selection span proposals.
  std::vector<std::pair<CodepointSpan, float>> proposals;

  for (int i = -symmetry_context_size; i < symmetry_context_size + 1; ++i) {
    const int token_index = click_index + i;
    if (token_index >= 0 && token_index < tokens.size() &&
        !tokens[token_index].is_padding) {
      float score;
      VectorSpan<float> features;
      VectorSpan<Token> output_tokens;

      CodepointSpan span;
      if (cached_features->Get(token_index, &features, &output_tokens)) {
        std::vector<float> scores;
        selection_network_->ComputeLogits(features, &scores);

        std::vector<CodepointSpan> selection_label_spans;
        if (selection_feature_processor_->SelectionLabelSpans(
                output_tokens, &selection_label_spans)) {
          scores = nlp_core::ComputeSoftmax(scores);
          std::tie(span, score) =
              BestSelectionSpan(click_indices, scores, selection_label_spans);
          if (span.first != kInvalidIndex && span.second != kInvalidIndex &&
              score >= 0) {
            proposals.push_back({span, score});
          }
        }
      }
    }
  }

  // Sort selection span proposals by their respective probabilities.
  std::sort(
      proposals.begin(), proposals.end(),
      [](std::pair<CodepointSpan, float> a, std::pair<CodepointSpan, float> b) {
        return a.second > b.second;
      });

  // Go from the highest-scoring proposal and claim tokens. Tokens are marked as
  // claimed by the higher-scoring selection proposals, so that the
  // lower-scoring ones cannot use them. Returns the selection proposal if it
  // contains the clicked token.
  std::vector<int> used_tokens(tokens.size(), 0);
  for (auto span_result : proposals) {
    TokenSpan span = CodepointSpanToTokenSpan(tokens, span_result.first);
    if (span.first != kInvalidIndex && span.second != kInvalidIndex) {
      bool feasible = true;
      for (int i = span.first; i < span.second; i++) {
        if (used_tokens[i] != 0) {
          feasible = false;
          break;
        }
      }

      if (feasible) {
        if (span.first <= click_index && span.second > click_index) {
          return {span_result.first.first, span_result.first.second};
        }
        for (int i = span.first; i < span.second; i++) {
          used_tokens[i] = 1;
        }
      }
    }
  }

  return {click_indices.first, click_indices.second};
}

std::vector<std::pair<std::string, float>>
TextClassificationModel::ClassifyText(const std::string& context,
                                      CodepointSpan selection_indices,
                                      int hint_flags) const {
  if (!initialized_) {
    TC_LOG(ERROR) << "Not initialized";
    return {};
  }

  if (std::get<0>(selection_indices) >= std::get<1>(selection_indices)) {
    TC_LOG(ERROR) << "Trying to run ClassifyText with invalid indices: "
                  << std::get<0>(selection_indices) << " "
                  << std::get<1>(selection_indices);
    return {};
  }

  if (hint_flags & SELECTION_IS_URL &&
      sharing_options_.always_accept_url_hint()) {
    return {{kUrlHintCollection, 1.0}};
  }

  if (hint_flags & SELECTION_IS_EMAIL &&
      sharing_options_.always_accept_email_hint()) {
    return {{kEmailHintCollection, 1.0}};
  }

  EmbeddingNetwork::Vector scores =
      InferInternal(context, selection_indices, *sharing_feature_processor_,
                    *sharing_network_, sharing_feature_fn_, nullptr);
  if (scores.empty() ||
      scores.size() != sharing_feature_processor_->NumCollections()) {
    TC_LOG(ERROR) << "Using default class: scores.size() = " << scores.size();
    return {};
  }

  scores = nlp_core::ComputeSoftmax(scores);

  std::vector<std::pair<std::string, float>> result;
  for (int i = 0; i < scores.size(); i++) {
    result.push_back(
        {sharing_feature_processor_->LabelToCollection(i), scores[i]});
  }
  std::sort(result.begin(), result.end(),
            [](const std::pair<std::string, float>& a,
               const std::pair<std::string, float>& b) {
              return a.second > b.second;
            });

  // Phone class sanity check.
  if (result.begin()->first == kPhoneCollection) {
    const int digit_count = CountDigits(context, selection_indices);
    if (digit_count < sharing_options_.phone_min_num_digits() ||
        digit_count > sharing_options_.phone_max_num_digits()) {
      return {{kOtherCollection, 1.0}};
    }
  }

  return result;
}

}  // namespace libtextclassifier
