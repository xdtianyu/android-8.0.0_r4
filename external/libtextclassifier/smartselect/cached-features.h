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

#ifndef LIBTEXTCLASSIFIER_SMARTSELECT_CACHED_FEATURES_H_
#define LIBTEXTCLASSIFIER_SMARTSELECT_CACHED_FEATURES_H_

#include <memory>
#include <vector>

#include "base.h"
#include "common/vector-span.h"
#include "smartselect/types.h"

namespace libtextclassifier {

// Holds state for extracting features across multiple calls and reusing them.
// Assumes that features for each Token are independent.
class CachedFeatures {
 public:
  // Extracts the features for the given sequence of tokens.
  //  - context_size: Specifies how many tokens to the left, and how many
  //                   tokens to the right spans the context.
  //  - sparse_features, dense_features: Extracted features for each token.
  //  - feature_vector_fn: Writes features for given Token to the specified
  //                       storage.
  //                       NOTE: The function can assume that the underlying
  //                       storage is initialized to all zeros.
  //  - feature_vector_size: Size of a feature vector for one Token.
  CachedFeatures(VectorSpan<Token> tokens, int context_size,
                 const std::vector<std::vector<int>>& sparse_features,
                 const std::vector<std::vector<float>>& dense_features,
                 const std::function<bool(const std::vector<int>&,
                                          const std::vector<float>&, float*)>&
                     feature_vector_fn,
                 int feature_vector_size)
      : tokens_(tokens),
        context_size_(context_size),
        feature_vector_size_(feature_vector_size),
        remap_v0_feature_vector_(false),
        remap_v0_chargram_embedding_size_(-1) {
    Extract(sparse_features, dense_features, feature_vector_fn);
  }

  // Gets a VectorSpan with the features for given click position.
  bool Get(int click_pos, VectorSpan<float>* features,
           VectorSpan<Token>* output_tokens);

  // Turns on a compatibility mode, which re-maps the extracted features to the
  // v0 feature format (where the dense features were at the end).
  // WARNING: Internally v0_feature_storage_ is used as a backing buffer for
  // VectorSpan<float>, so the output of Extract is valid only until the next
  // call or destruction of the current CachedFeatures object.
  // TODO(zilka): Remove when we'll have retrained models.
  void SetV0FeatureMode(int chargram_embedding_size) {
    remap_v0_feature_vector_ = true;
    remap_v0_chargram_embedding_size_ = chargram_embedding_size;
    v0_feature_storage_.resize(feature_vector_size_ * (context_size_ * 2 + 1));
  }

 protected:
  // Extracts features for all tokens and stores them for later retrieval.
  void Extract(const std::vector<std::vector<int>>& sparse_features,
               const std::vector<std::vector<float>>& dense_features,
               const std::function<bool(const std::vector<int>&,
                                        const std::vector<float>&, float*)>&
                   feature_vector_fn);

  // Remaps extracted features to V0 feature format. The mapping is using
  // the v0_feature_storage_ as the backing storage for the mapped features.
  // For each token the features consist of:
  //  - chargram embeddings
  //  - dense features
  // They are concatenated together as [chargram embeddings; dense features]
  // for each token independently.
  // The V0 features require that the chargram embeddings for tokens are
  // concatenated first together, and at the end, the dense features for the
  // tokens are concatenated to it.
  void RemapV0FeatureVector(VectorSpan<float>* features);

 private:
  const VectorSpan<Token> tokens_;
  const int context_size_;
  const int feature_vector_size_;
  bool remap_v0_feature_vector_;
  int remap_v0_chargram_embedding_size_;

  std::vector<float> features_;
  std::vector<float> v0_feature_storage_;
};

}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_SMARTSELECT_CACHED_FEATURES_H_
