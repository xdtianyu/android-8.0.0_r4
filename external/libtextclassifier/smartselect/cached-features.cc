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

#include "smartselect/cached-features.h"
#include "util/base/logging.h"

namespace libtextclassifier {

void CachedFeatures::Extract(
    const std::vector<std::vector<int>>& sparse_features,
    const std::vector<std::vector<float>>& dense_features,
    const std::function<bool(const std::vector<int>&, const std::vector<float>&,
                             float*)>& feature_vector_fn) {
  features_.resize(feature_vector_size_ * tokens_.size());
  for (int i = 0; i < tokens_.size(); ++i) {
    feature_vector_fn(sparse_features[i], dense_features[i],
                      features_.data() + i * feature_vector_size_);
  }
}

bool CachedFeatures::Get(int click_pos, VectorSpan<float>* features,
                         VectorSpan<Token>* output_tokens) {
  const int token_start = click_pos - context_size_;
  const int token_end = click_pos + context_size_ + 1;
  if (token_start < 0 || token_end > tokens_.size()) {
    TC_LOG(ERROR) << "Tokens out of range: " << token_start << " " << token_end;
    return false;
  }

  *features =
      VectorSpan<float>(features_.begin() + token_start * feature_vector_size_,
                        features_.begin() + token_end * feature_vector_size_);
  *output_tokens = VectorSpan<Token>(tokens_.begin() + token_start,
                                     tokens_.begin() + token_end);
  if (remap_v0_feature_vector_) {
    RemapV0FeatureVector(features);
  }

  return true;
}

void CachedFeatures::RemapV0FeatureVector(VectorSpan<float>* features) {
  if (!remap_v0_feature_vector_) {
    return;
  }

  auto it = features->begin();
  int num_suffix_features =
      feature_vector_size_ - remap_v0_chargram_embedding_size_;
  int num_tokens = context_size_ * 2 + 1;
  for (int t = 0; t < num_tokens; ++t) {
    for (int i = 0; i < remap_v0_chargram_embedding_size_; ++i) {
      v0_feature_storage_[t * remap_v0_chargram_embedding_size_ + i] = *it;
      ++it;
    }
    // Rest of the features are the dense features that come to the end.
    for (int i = 0; i < num_suffix_features; ++i) {
      // clang-format off
      v0_feature_storage_[num_tokens * remap_v0_chargram_embedding_size_
                      + t * num_suffix_features
                      + i] = *it;
      // clang-format on
      ++it;
    }
  }
  *features = VectorSpan<float>(v0_feature_storage_);
}

}  // namespace libtextclassifier
