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

#include "common/embedding-network.h"

#include <math.h>

#include "common/simple-adder.h"
#include "util/base/integral_types.h"
#include "util/base/logging.h"

namespace libtextclassifier {
namespace nlp_core {

namespace {

// Returns true if and only if matrix does not use any quantization.
bool CheckNoQuantization(const EmbeddingNetworkParams::Matrix &matrix) {
  if (matrix.quant_type != QuantizationType::NONE) {
    TC_LOG(ERROR) << "Unsupported quantization";
    TC_DCHECK(false);  // Crash in debug mode.
    return false;
  }
  return true;
}

// Initializes a Matrix object with the parameters from the MatrixParams
// source_matrix.  source_matrix should not use quantization.
//
// Returns true on success, false on error.
bool InitNonQuantizedMatrix(const EmbeddingNetworkParams::Matrix &source_matrix,
                            EmbeddingNetwork::Matrix *mat) {
  mat->resize(source_matrix.rows);

  // Before we access the weights as floats, we need to check that they are
  // really floats, i.e., no quantization is used.
  if (!CheckNoQuantization(source_matrix)) return false;
  const float *weights =
      reinterpret_cast<const float *>(source_matrix.elements);
  for (int r = 0; r < source_matrix.rows; ++r) {
    (*mat)[r] = EmbeddingNetwork::VectorWrapper(weights, source_matrix.cols);
    weights += source_matrix.cols;
  }
  return true;
}

// Initializes a VectorWrapper object with the parameters from the MatrixParams
// source_matrix.  source_matrix should have exactly one column and should not
// use quantization.
//
// Returns true on success, false on error.
bool InitNonQuantizedVector(const EmbeddingNetworkParams::Matrix &source_matrix,
                            EmbeddingNetwork::VectorWrapper *vector) {
  if (source_matrix.cols != 1) {
    TC_LOG(ERROR) << "wrong #cols " << source_matrix.cols;
    return false;
  }
  if (!CheckNoQuantization(source_matrix)) {
    TC_LOG(ERROR) << "unsupported quantization";
    return false;
  }
  // Before we access the weights as floats, we need to check that they are
  // really floats, i.e., no quantization is used.
  if (!CheckNoQuantization(source_matrix)) return false;
  const float *weights =
      reinterpret_cast<const float *>(source_matrix.elements);
  *vector = EmbeddingNetwork::VectorWrapper(weights, source_matrix.rows);
  return true;
}

// Computes y = weights * Relu(x) + b where Relu is optionally applied.
template <typename ScaleAdderClass>
bool SparseReluProductPlusBias(bool apply_relu,
                               const EmbeddingNetwork::Matrix &weights,
                               const EmbeddingNetwork::VectorWrapper &b,
                               const VectorSpan<float> &x,
                               EmbeddingNetwork::Vector *y) {
  // Check that dimensions match.
  if ((x.size() != weights.size()) || weights.empty()) {
    TC_LOG(ERROR) << x.size() << " != " << weights.size();
    return false;
  }
  if (weights[0].size() != b.size()) {
    TC_LOG(ERROR) << weights[0].size() << " != " << b.size();
    return false;
  }

  y->assign(b.data(), b.data() + b.size());
  ScaleAdderClass adder(y->data(), y->size());

  const int x_size = x.size();
  for (int i = 0; i < x_size; ++i) {
    const float &scale = x[i];
    if (apply_relu) {
      if (scale > 0) {
        adder.LazyScaleAdd(weights[i].data(), scale);
      }
    } else {
      adder.LazyScaleAdd(weights[i].data(), scale);
    }
  }
  return true;
}
}  // namespace

bool EmbeddingNetwork::ConcatEmbeddings(
    const std::vector<FeatureVector> &feature_vectors, Vector *concat) const {
  concat->resize(concat_layer_size_);

  // Invariant 1: feature_vectors contains exactly one element for each
  // embedding space.  That element is itself a FeatureVector, which may be
  // empty, but it should be there.
  if (feature_vectors.size() != embedding_matrices_.size()) {
    TC_LOG(ERROR) << feature_vectors.size()
                  << " != " << embedding_matrices_.size();
    return false;
  }

  // "es_index" stands for "embedding space index".
  for (int es_index = 0; es_index < feature_vectors.size(); ++es_index) {
    // Access is safe by es_index loop bounds and Invariant 1.
    EmbeddingMatrix *const embedding_matrix =
        embedding_matrices_[es_index].get();
    if (embedding_matrix == nullptr) {
      // Should not happen, hence our terse log error message.
      TC_LOG(ERROR) << es_index;
      return false;
    }

    // Access is safe due to es_index loop bounds.
    const FeatureVector &feature_vector = feature_vectors[es_index];

    // Access is safe by es_index loop bounds, Invariant 1, and Invariant 2.
    const int concat_offset = concat_offset_[es_index];

    if (!GetEmbeddingInternal(feature_vector, embedding_matrix, concat_offset,
                              concat->data(), concat->size())) {
      TC_LOG(ERROR) << es_index;
      return false;
    }
  }
  return true;
}

bool EmbeddingNetwork::GetEmbedding(const FeatureVector &feature_vector,
                                    int es_index, float *embedding) const {
  EmbeddingMatrix *const embedding_matrix = embedding_matrices_[es_index].get();
  if (embedding_matrix == nullptr) {
    // Should not happen, hence our terse log error message.
    TC_LOG(ERROR) << es_index;
    return false;
  }
  return GetEmbeddingInternal(feature_vector, embedding_matrix, 0, embedding,
                              embedding_matrices_[es_index]->dim());
}

bool EmbeddingNetwork::GetEmbeddingInternal(
    const FeatureVector &feature_vector,
    EmbeddingMatrix *const embedding_matrix, const int concat_offset,
    float *concat, int concat_size) const {
  const int embedding_dim = embedding_matrix->dim();
  const bool is_quantized =
      embedding_matrix->quant_type() != QuantizationType::NONE;
  const int num_features = feature_vector.size();
  for (int fi = 0; fi < num_features; ++fi) {
    // Both accesses below are safe due to loop bounds for fi.
    const FeatureType *feature_type = feature_vector.type(fi);
    const FeatureValue feature_value = feature_vector.value(fi);
    const int feature_offset =
        concat_offset + feature_type->base() * embedding_dim;

    // Code below updates max(0, embedding_dim) elements from concat, starting
    // with index feature_offset.  Check below ensures these updates are safe.
    if ((feature_offset < 0) ||
        (feature_offset + embedding_dim > concat_size)) {
      TC_LOG(ERROR) << fi << ": " << feature_offset << " " << embedding_dim
                    << " " << concat_size;
      return false;
    }

    // Pointer to float / uint8 weights for relevant embedding.
    const void *embedding_data;

    // Multiplier for each embedding weight.
    float multiplier;

    if (feature_type->is_continuous()) {
      // Continuous features (encoded as FloatFeatureValue).
      FloatFeatureValue float_feature_value(feature_value);
      const int id = float_feature_value.id;
      embedding_matrix->get_embedding(id, &embedding_data, &multiplier);
      multiplier *= float_feature_value.weight;
    } else {
      // Discrete features: every present feature has implicit value 1.0.
      // Hence, after we grab the multiplier below, we don't multiply it by
      // any weight.
      embedding_matrix->get_embedding(feature_value, &embedding_data,
                                      &multiplier);
    }

    // Weighted embeddings will be added starting from this address.
    float *concat_ptr = concat + feature_offset;

    if (is_quantized) {
      const uint8 *quant_weights =
          reinterpret_cast<const uint8 *>(embedding_data);
      for (int i = 0; i < embedding_dim; ++i, ++quant_weights, ++concat_ptr) {
        // 128 is bias for UINT8 quantization, only one we currently support.
        *concat_ptr += (static_cast<int>(*quant_weights) - 128) * multiplier;
      }
    } else {
      const float *weights = reinterpret_cast<const float *>(embedding_data);
      for (int i = 0; i < embedding_dim; ++i, ++weights, ++concat_ptr) {
        *concat_ptr += *weights * multiplier;
      }
    }
  }
  return true;
}

bool EmbeddingNetwork::ComputeLogits(const VectorSpan<float> &input,
                                     Vector *scores) const {
  return EmbeddingNetwork::ComputeLogitsInternal(input, scores);
}

bool EmbeddingNetwork::ComputeLogits(const Vector &input,
                                     Vector *scores) const {
  return EmbeddingNetwork::ComputeLogitsInternal(input, scores);
}

bool EmbeddingNetwork::ComputeLogitsInternal(const VectorSpan<float> &input,
                                             Vector *scores) const {
  return FinishComputeFinalScoresInternal<SimpleAdder>(input, scores);
}

template <typename ScaleAdderClass>
bool EmbeddingNetwork::FinishComputeFinalScoresInternal(
    const VectorSpan<float> &input, Vector *scores) const {
  // This vector serves as an alternating storage for activations of the
  // different layers. We can't use just one vector here because all of the
  // activations of  the previous layer are needed for computation of
  // activations of the next one.
  std::vector<Vector> h_storage(2);

  // Compute pre-logits activations.
  VectorSpan<float> h_in(input);
  Vector *h_out;
  for (int i = 0; i < hidden_weights_.size(); ++i) {
    const bool apply_relu = i > 0;
    h_out = &(h_storage[i % 2]);
    h_out->resize(hidden_bias_[i].size());
    if (!SparseReluProductPlusBias<ScaleAdderClass>(
            apply_relu, hidden_weights_[i], hidden_bias_[i], h_in, h_out)) {
      return false;
    }
    h_in = VectorSpan<float>(*h_out);
  }

  // Compute logit scores.
  if (!SparseReluProductPlusBias<ScaleAdderClass>(
          true, softmax_weights_, softmax_bias_, h_in, scores)) {
    return false;
  }

  return true;
}

bool EmbeddingNetwork::ComputeFinalScores(
    const std::vector<FeatureVector> &features, Vector *scores) const {
  return ComputeFinalScores(features, {}, scores);
}

bool EmbeddingNetwork::ComputeFinalScores(
    const std::vector<FeatureVector> &features,
    const std::vector<float> extra_inputs, Vector *scores) const {
  // If we haven't successfully initialized, return without doing anything.
  if (!is_valid()) return false;

  Vector concat;
  if (!ConcatEmbeddings(features, &concat)) return false;

  if (!extra_inputs.empty()) {
    concat.reserve(concat.size() + extra_inputs.size());
    for (int i = 0; i < extra_inputs.size(); i++) {
      concat.push_back(extra_inputs[i]);
    }
  }

  scores->resize(softmax_bias_.size());
  return ComputeLogits(concat, scores);
}

EmbeddingNetwork::EmbeddingNetwork(const EmbeddingNetworkParams *model) {
  // We'll set valid_ to true only if construction is successful.  If we detect
  // an error along the way, we log an informative message and return early, but
  // we do not crash.
  valid_ = false;

  // Fill embedding_matrices_, concat_offset_, and concat_layer_size_.
  const int num_embedding_spaces = model->GetNumEmbeddingSpaces();
  int offset_sum = 0;
  for (int i = 0; i < num_embedding_spaces; ++i) {
    concat_offset_.push_back(offset_sum);
    const EmbeddingNetworkParams::Matrix matrix = model->GetEmbeddingMatrix(i);
    if (matrix.quant_type != QuantizationType::UINT8) {
      TC_LOG(ERROR) << "Unsupported quantization for embedding #" << i << ": "
                    << static_cast<int>(matrix.quant_type);
      return;
    }

    // There is no way to accomodate an empty embedding matrix.  E.g., there is
    // no way for get_embedding to return something that can be read safely.
    // Hence, we catch that error here and return early.
    if (matrix.rows == 0) {
      TC_LOG(ERROR) << "Empty embedding matrix #" << i;
      return;
    }
    embedding_matrices_.emplace_back(new EmbeddingMatrix(matrix));
    const int embedding_dim = embedding_matrices_.back()->dim();
    offset_sum += embedding_dim * model->GetNumFeaturesInEmbeddingSpace(i);
  }
  concat_layer_size_ = offset_sum;

  // Invariant 2 (trivial by the code above).
  TC_DCHECK_EQ(concat_offset_.size(), embedding_matrices_.size());

  const int num_hidden_layers = model->GetNumHiddenLayers();
  if (num_hidden_layers < 1) {
    TC_LOG(ERROR) << "Wrong number of hidden layers: " << num_hidden_layers;
    return;
  }
  hidden_weights_.resize(num_hidden_layers);
  hidden_bias_.resize(num_hidden_layers);

  for (int i = 0; i < num_hidden_layers; ++i) {
    const EmbeddingNetworkParams::Matrix matrix =
        model->GetHiddenLayerMatrix(i);
    const EmbeddingNetworkParams::Matrix bias = model->GetHiddenLayerBias(i);
    if (!InitNonQuantizedMatrix(matrix, &hidden_weights_[i]) ||
        !InitNonQuantizedVector(bias, &hidden_bias_[i])) {
      TC_LOG(ERROR) << "Bad hidden layer #" << i;
      return;
    }
  }

  if (!model->HasSoftmaxLayer()) {
    TC_LOG(ERROR) << "Missing softmax layer";
    return;
  }
  const EmbeddingNetworkParams::Matrix softmax = model->GetSoftmaxMatrix();
  const EmbeddingNetworkParams::Matrix softmax_bias = model->GetSoftmaxBias();
  if (!InitNonQuantizedMatrix(softmax, &softmax_weights_) ||
      !InitNonQuantizedVector(softmax_bias, &softmax_bias_)) {
    TC_LOG(ERROR) << "Bad softmax layer";
    return;
  }

  // Everything looks good.
  valid_ = true;
}

int EmbeddingNetwork::EmbeddingSize(int es_index) const {
  return embedding_matrices_[es_index]->dim();
}

}  // namespace nlp_core
}  // namespace libtextclassifier
