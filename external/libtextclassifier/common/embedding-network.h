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

#ifndef LIBTEXTCLASSIFIER_COMMON_EMBEDDING_NETWORK_H_
#define LIBTEXTCLASSIFIER_COMMON_EMBEDDING_NETWORK_H_

#include <memory>
#include <vector>

#include "common/embedding-network-params.h"
#include "common/feature-extractor.h"
#include "common/vector-span.h"
#include "util/base/integral_types.h"
#include "util/base/logging.h"
#include "util/base/macros.h"

namespace libtextclassifier {
namespace nlp_core {

// Classifier using a hand-coded feed-forward neural network.
//
// No gradient computation, just inference.
//
// Classification works as follows:
//
// Discrete features -> Embeddings -> Concatenation -> Hidden+ -> Softmax
//
// In words: given some discrete features, this class extracts the embeddings
// for these features, concatenates them, passes them through one or two hidden
// layers (each layer uses Relu) and next through a softmax layer that computes
// an unnormalized score for each possible class.  Note: there is always a
// softmax layer.
class EmbeddingNetwork {
 public:
  // Class used to represent an embedding matrix.  Each row is the embedding on
  // a vocabulary element.  Number of columns = number of embedding dimensions.
  class EmbeddingMatrix {
   public:
    explicit EmbeddingMatrix(const EmbeddingNetworkParams::Matrix source_matrix)
        : rows_(source_matrix.rows),
          cols_(source_matrix.cols),
          quant_type_(source_matrix.quant_type),
          data_(source_matrix.elements),
          row_size_in_bytes_(GetRowSizeInBytes(cols_, quant_type_)),
          quant_scales_(source_matrix.quant_scales) {}

    // Returns vocabulary size; one embedding for each vocabulary element.
    int size() const { return rows_; }

    // Returns number of weights in embedding of each vocabulary element.
    int dim() const { return cols_; }

    // Returns quantization type for this embedding matrix.
    QuantizationType quant_type() const { return quant_type_; }

    // Gets embedding for k-th vocabulary element: on return, sets *data to
    // point to the embedding weights and *scale to the quantization scale (1.0
    // if no quantization).
    void get_embedding(int k, const void **data, float *scale) const {
      if ((k < 0) || (k >= size())) {
        TC_LOG(ERROR) << "Index outside [0, " << size() << "): " << k;

        // In debug mode, crash.  In prod, pretend that k is 0.
        TC_DCHECK(false);
        k = 0;
      }
      *data = reinterpret_cast<const char *>(data_) + k * row_size_in_bytes_;
      if (quant_type_ == QuantizationType::NONE) {
        *scale = 1.0;
      } else {
        *scale = Float16To32(quant_scales_[k]);
      }
    }

   private:
    static int GetRowSizeInBytes(int cols, QuantizationType quant_type) {
      switch (quant_type) {
        case QuantizationType::NONE:
          return cols * sizeof(float);
        case QuantizationType::UINT8:
          return cols * sizeof(uint8);
        default:
          TC_LOG(ERROR) << "Unknown quant type: "
                        << static_cast<int>(quant_type);
          return 0;
      }
    }

    // Vocabulary size.
    const int rows_;

    // Number of elements in each embedding.
    const int cols_;

    const QuantizationType quant_type_;

    // Pointer to the embedding weights, in row-major order.  This is a pointer
    // to an array of floats / uint8, depending on the quantization type.
    // Not owned.
    const void *const data_;

    // Number of bytes for one row.  Used to jump to next row in data_.
    const int row_size_in_bytes_;

    // Pointer to quantization scales.  nullptr if no quantization.  Otherwise,
    // quant_scales_[i] is scale for embedding of i-th vocabulary element.
    const float16 *const quant_scales_;

    TC_DISALLOW_COPY_AND_ASSIGN(EmbeddingMatrix);
  };

  // An immutable vector that doesn't own the memory that stores the underlying
  // floats.  Can be used e.g., as a wrapper around model weights stored in the
  // static memory.
  class VectorWrapper {
   public:
    VectorWrapper() : VectorWrapper(nullptr, 0) {}

    // Constructs a vector wrapper around the size consecutive floats that start
    // at address data.  Note: the underlying data should be alive for at least
    // the lifetime of this VectorWrapper object.  That's trivially true if data
    // points to statically allocated data :)
    VectorWrapper(const float *data, int size) : data_(data), size_(size) {}

    int size() const { return size_; }

    const float *data() const { return data_; }

   private:
    const float *data_;  // Not owned.
    int size_;

    // Doesn't own anything, so it can be copied and assigned at will :)
  };

  typedef std::vector<VectorWrapper> Matrix;
  typedef std::vector<float> Vector;

  // Constructs an embedding network using the parameters from model.
  //
  // Note: model should stay alive for at least the lifetime of this
  // EmbeddingNetwork object.
  explicit EmbeddingNetwork(const EmbeddingNetworkParams *model);

  virtual ~EmbeddingNetwork() {}

  // Returns true if this EmbeddingNetwork object has been correctly constructed
  // and is ready to use.  Idea: in case of errors, mark this EmbeddingNetwork
  // object as invalid, but do not crash.
  bool is_valid() const { return valid_; }

  // Runs forward computation to fill scores with unnormalized output unit
  // scores. This is useful for making predictions.
  //
  // Returns true on success, false on error (e.g., if !is_valid()).
  bool ComputeFinalScores(const std::vector<FeatureVector> &features,
                          Vector *scores) const;

  // Same as above, but allows specification of extra neural network inputs that
  // will be appended to the embedding vector build from features.
  bool ComputeFinalScores(const std::vector<FeatureVector> &features,
                          const std::vector<float> extra_inputs,
                          Vector *scores) const;

  // Constructs the concatenated input embedding vector in place in output
  // vector concat.  Returns true on success, false on error.
  bool ConcatEmbeddings(const std::vector<FeatureVector> &features,
                        Vector *concat) const;

  // Sums embeddings for all features from |feature_vector| and adds result
  // to values from the array pointed-to by |output|.  Embeddings for continuous
  // features are weighted by the feature weight.
  //
  // NOTE: output should point to an array of EmbeddingSize(es_index) floats.
  bool GetEmbedding(const FeatureVector &feature_vector, int es_index,
                    float *embedding) const;

  // Runs the feed-forward neural network for |input| and computes logits for
  // softmax layer.
  bool ComputeLogits(const Vector &input, Vector *scores) const;

  // Same as above but uses a view of the feature vector.
  bool ComputeLogits(const VectorSpan<float> &input, Vector *scores) const;

  // Returns the size (the number of columns) of the embedding space es_index.
  int EmbeddingSize(int es_index) const;

 protected:
  // Builds an embedding for given feature vector, and places it from
  // concat_offset to the concat vector.
  bool GetEmbeddingInternal(const FeatureVector &feature_vector,
                            EmbeddingMatrix *embedding_matrix,
                            int concat_offset, float *concat,
                            int embedding_size) const;

  // Templated function that computes the logit scores given the concatenated
  // input embeddings.
  bool ComputeLogitsInternal(const VectorSpan<float> &concat,
                             Vector *scores) const;

  // Computes the softmax scores (prior to normalization) from the concatenated
  // representation.  Returns true on success, false on error.
  template <typename ScaleAdderClass>
  bool FinishComputeFinalScoresInternal(const VectorSpan<float> &concat,
                                        Vector *scores) const;

  // Set to true on successful construction, false otherwise.
  bool valid_ = false;

  // Network parameters.

  // One weight matrix for each embedding space.
  std::vector<std::unique_ptr<EmbeddingMatrix>> embedding_matrices_;

  // concat_offset_[i] is the input layer offset for i-th embedding space.
  std::vector<int> concat_offset_;

  // Size of the input ("concatenation") layer.
  int concat_layer_size_;

  // One weight matrix and one vector of bias weights for each hiden layer.
  std::vector<Matrix> hidden_weights_;
  std::vector<VectorWrapper> hidden_bias_;

  // Weight matrix and bias vector for the softmax layer.
  Matrix softmax_weights_;
  VectorWrapper softmax_bias_;
};

}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_COMMON_EMBEDDING_NETWORK_H_
