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

// Model parameter loading.

#ifndef LIBTEXTCLASSIFIER_SMARTSELECT_MODEL_PARAMS_H_
#define LIBTEXTCLASSIFIER_SMARTSELECT_MODEL_PARAMS_H_

#include "common/embedding-network.h"
#include "common/memory_image/embedding-network-params-from-image.h"
#include "smartselect/text-classification-model.pb.h"

namespace libtextclassifier {

class EmbeddingParams : public nlp_core::EmbeddingNetworkParamsFromImage {
 public:
  EmbeddingParams(const void* start, uint64 num_bytes, int context_size)
      : EmbeddingNetworkParamsFromImage(start, num_bytes),
        context_size_(context_size) {}

  int embeddings_size() const override { return context_size_ * 2 + 1; }

  int embedding_num_features_size() const override {
    return context_size_ * 2 + 1;
  }

  int embedding_num_features(int i) const override { return 1; }

  int embeddings_num_rows(int i) const override {
    return EmbeddingNetworkParamsFromImage::embeddings_num_rows(0);
  };

  int embeddings_num_cols(int i) const override {
    return EmbeddingNetworkParamsFromImage::embeddings_num_cols(0);
  };

  const void* embeddings_weights(int i) const override {
    return EmbeddingNetworkParamsFromImage::embeddings_weights(0);
  };

  nlp_core::QuantizationType embeddings_quant_type(int i) const override {
    return EmbeddingNetworkParamsFromImage::embeddings_quant_type(0);
  }

  const nlp_core::float16* embeddings_quant_scales(int i) const override {
    return EmbeddingNetworkParamsFromImage::embeddings_quant_scales(0);
  }

 private:
  int context_size_;
};

// Loads and holds the parameters of the inference network.
//
// This class overrides a couple of methods of EmbeddingNetworkParamsFromImage
// because we only have one embedding matrix for all positions of context,
// whereas the original class would have a separate one for each.
class ModelParams : public nlp_core::EmbeddingNetworkParamsFromImage {
 public:
  const FeatureProcessorOptions& GetFeatureProcessorOptions() const {
    return feature_processor_options_;
  }

  const SelectionModelOptions& GetSelectionModelOptions() const {
    return selection_options_;
  }

  const SharingModelOptions& GetSharingModelOptions() const {
    return sharing_options_;
  }

  std::shared_ptr<EmbeddingParams> GetEmbeddingParams() const {
    return embedding_params_;
  }

 protected:
  int embeddings_size() const override {
    return embedding_params_->embeddings_size();
  }

  int embedding_num_features_size() const override {
    return embedding_params_->embedding_num_features_size();
  }

  int embedding_num_features(int i) const override {
    return embedding_params_->embedding_num_features(i);
  }

  int embeddings_num_rows(int i) const override {
    return embedding_params_->embeddings_num_rows(i);
  };

  int embeddings_num_cols(int i) const override {
    return embedding_params_->embeddings_num_cols(i);
  };

  const void* embeddings_weights(int i) const override {
    return embedding_params_->embeddings_weights(i);
  };

  nlp_core::QuantizationType embeddings_quant_type(int i) const override {
    return embedding_params_->embeddings_quant_type(i);
  }

  const nlp_core::float16* embeddings_quant_scales(int i) const override {
    return embedding_params_->embeddings_quant_scales(i);
  }

 private:
  friend ModelParams* ModelParamsBuilder(
      const void* start, uint64 num_bytes,
      std::shared_ptr<EmbeddingParams> external_embedding_params);

  ModelParams(const void* start, uint64 num_bytes,
              std::shared_ptr<EmbeddingParams> embedding_params,
              const SelectionModelOptions& selection_options,
              const SharingModelOptions& sharing_options,
              const FeatureProcessorOptions& feature_processor_options)
      : EmbeddingNetworkParamsFromImage(start, num_bytes),
        selection_options_(selection_options),
        sharing_options_(sharing_options),
        feature_processor_options_(feature_processor_options),
        context_size_(feature_processor_options_.context_size()),
        embedding_params_(std::move(embedding_params)) {}

  SelectionModelOptions selection_options_;
  SharingModelOptions sharing_options_;
  FeatureProcessorOptions feature_processor_options_;
  int context_size_;
  std::shared_ptr<EmbeddingParams> embedding_params_;
};

ModelParams* ModelParamsBuilder(
    const void* start, uint64 num_bytes,
    std::shared_ptr<EmbeddingParams> external_embedding_params);

}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_SMARTSELECT_MODEL_PARAMS_H_
