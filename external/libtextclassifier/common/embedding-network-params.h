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

#ifndef LIBTEXTCLASSIFIER_COMMON_EMBEDDING_NETWORK_PARAMS_H_
#define LIBTEXTCLASSIFIER_COMMON_EMBEDDING_NETWORK_PARAMS_H_

#include <algorithm>
#include <string>

#include "common/float16.h"
#include "common/task-context.h"
#include "common/task-spec.pb.h"
#include "util/base/logging.h"

namespace libtextclassifier {
namespace nlp_core {

enum class QuantizationType { NONE = 0, UINT8 };

// API for accessing parameters for a feed-forward neural network with
// embeddings.
//
// Note: this API is closely related to embedding-network.proto.  The reason we
// have a separate API is that the proto may not be the only way of packaging
// these parameters.
class EmbeddingNetworkParams {
 public:
  virtual ~EmbeddingNetworkParams() {}

  // **** High-level API.

  // Simple representation of a matrix.  This small struct that doesn't own any
  // resource intentionally supports copy / assign, to simplify our APIs.
  struct Matrix {
    // Number of rows.
    int rows;

    // Number of columns.
    int cols;

    QuantizationType quant_type;

    // Pointer to matrix elements, in row-major order
    // (https://en.wikipedia.org/wiki/Row-major_order) Not owned.
    const void *elements;

    // Quantization scales: one scale for each row.
    const float16 *quant_scales;
  };

  // Returns number of embedding spaces.
  int GetNumEmbeddingSpaces() const {
    if (embeddings_size() != embedding_num_features_size()) {
      TC_LOG(ERROR) << "Embedding spaces mismatch " << embeddings_size()
                    << " != " << embedding_num_features_size();
    }
    return std::max(0,
                    std::min(embeddings_size(), embedding_num_features_size()));
  }

  // Returns embedding matrix for the i-th embedding space.
  //
  // NOTE: i must be in [0, GetNumEmbeddingSpaces()).  Undefined behavior
  // otherwise.
  Matrix GetEmbeddingMatrix(int i) const {
    TC_DCHECK(InRange(i, embeddings_size()));
    Matrix matrix;
    matrix.rows = embeddings_num_rows(i);
    matrix.cols = embeddings_num_cols(i);
    matrix.elements = embeddings_weights(i);
    matrix.quant_type = embeddings_quant_type(i);
    matrix.quant_scales = embeddings_quant_scales(i);
    return matrix;
  }

  // Returns number of features in i-th embedding space.
  //
  // NOTE: i must be in [0, GetNumEmbeddingSpaces()).  Undefined behavior
  // otherwise.
  int GetNumFeaturesInEmbeddingSpace(int i) const {
    TC_DCHECK(InRange(i, embedding_num_features_size()));
    return std::max(0, embedding_num_features(i));
  }

  // Returns number of hidden layers in the neural network.  Each such layer has
  // weight matrix and a bias vector (a matrix with one column).
  int GetNumHiddenLayers() const {
    if (hidden_size() != hidden_bias_size()) {
      TC_LOG(ERROR) << "Hidden layer mismatch " << hidden_size()
                    << " != " << hidden_bias_size();
    }
    return std::max(0, std::min(hidden_size(), hidden_bias_size()));
  }

  // Returns weight matrix for i-th hidden layer.
  //
  // NOTE: i must be in [0, GetNumHiddenLayers()).  Undefined behavior
  // otherwise.
  Matrix GetHiddenLayerMatrix(int i) const {
    TC_DCHECK(InRange(i, hidden_size()));
    Matrix matrix;
    matrix.rows = hidden_num_rows(i);
    matrix.cols = hidden_num_cols(i);

    // Quantization not supported here.
    matrix.quant_type = QuantizationType::NONE;
    matrix.elements = hidden_weights(i);
    return matrix;
  }

  // Returns bias matrix for i-th hidden layer.  Technically a Matrix, but we
  // expect it to be a vector (i.e., num cols is 1).
  //
  // NOTE: i must be in [0, GetNumHiddenLayers()).  Undefined behavior
  // otherwise.
  Matrix GetHiddenLayerBias(int i) const {
    TC_DCHECK(InRange(i, hidden_bias_size()));
    Matrix matrix;
    matrix.rows = hidden_bias_num_rows(i);
    matrix.cols = hidden_bias_num_cols(i);

    // Quantization not supported here.
    matrix.quant_type = QuantizationType::NONE;
    matrix.elements = hidden_bias_weights(i);
    return matrix;
  }

  // Returns true if a softmax layer exists.
  bool HasSoftmaxLayer() const {
    if (softmax_size() != softmax_bias_size()) {
      TC_LOG(ERROR) << "Softmax layer mismatch " << softmax_size()
                    << " != " << softmax_bias_size();
    }
    return (softmax_size() == 1) && (softmax_bias_size() == 1);
  }

  // Returns weight matrix for the softmax layer.
  //
  // NOTE: Should be called only if HasSoftmaxLayer() is true.  Undefined
  // behavior otherwise.
  Matrix GetSoftmaxMatrix() const {
    TC_DCHECK(softmax_size() == 1);
    Matrix matrix;
    matrix.rows = softmax_num_rows(0);
    matrix.cols = softmax_num_cols(0);

    // Quantization not supported here.
    matrix.quant_type = QuantizationType::NONE;
    matrix.elements = softmax_weights(0);
    return matrix;
  }

  // Returns bias for the softmax layer.  Technically a Matrix, but we expect it
  // to be a row/column vector (i.e., num cols is 1).
  //
  // NOTE: Should be called only if HasSoftmaxLayer() is true.  Undefined
  // behavior otherwise.
  Matrix GetSoftmaxBias() const {
    TC_DCHECK(softmax_bias_size() == 1);
    Matrix matrix;
    matrix.rows = softmax_bias_num_rows(0);
    matrix.cols = softmax_bias_num_cols(0);

    // Quantization not supported here.
    matrix.quant_type = QuantizationType::NONE;
    matrix.elements = softmax_bias_weights(0);
    return matrix;
  }

  // Updates the EmbeddingNetwork-related parameters from task_context.  Returns
  // true on success, false on error.
  virtual bool UpdateTaskContextParameters(TaskContext *task_context) {
    const TaskSpec *task_spec = GetTaskSpec();
    if (task_spec == nullptr) {
      TC_LOG(ERROR) << "Unable to get TaskSpec";
      return false;
    }
    for (const TaskSpec::Parameter &parameter : task_spec->parameter()) {
      task_context->SetParameter(parameter.name(), parameter.value());
    }
    return true;
  }

  // Returns a pointer to a TaskSpec with the EmbeddingNetwork-related
  // parameters.  Returns nullptr in case of problems.  Ownership with the
  // returned pointer is *not* transfered to the caller.
  virtual const TaskSpec *GetTaskSpec() {
    TC_LOG(ERROR) << "Not implemented";
    return nullptr;
  }

 protected:
  // **** Low-level API.
  //
  // * Most low-level API methods are documented by giving an equivalent
  //   function call on proto, the original proto (of type
  //   EmbeddingNetworkProto) which was used to generate the C++ code.
  //
  // * To simplify our generation code, optional proto fields of message type
  //   are treated as repeated fields with 0 or 1 instances.  As such, we have
  //   *_size() methods for such optional fields: they return 0 or 1.
  //
  // * "transpose(M)" denotes the transpose of a matrix M.
  //
  // * Behavior is undefined when trying to retrieve a piece of data that does
  //   not exist: e.g., embeddings_num_rows(5) if embeddings_size() == 2.

  // ** Access methods for repeated MatrixParams embeddings.
  //
  // Returns proto.embeddings_size().
  virtual int embeddings_size() const = 0;

  // Returns number of rows of transpose(proto.embeddings(i)).
  virtual int embeddings_num_rows(int i) const = 0;

  // Returns number of columns of transpose(proto.embeddings(i)).
  virtual int embeddings_num_cols(int i) const = 0;

  // Returns pointer to elements of transpose(proto.embeddings(i)), in row-major
  // order.  NOTE: for unquantized embeddings, this returns a pointer to float;
  // for quantized embeddings, this returns a pointer to uint8.
  virtual const void *embeddings_weights(int i) const = 0;

  virtual QuantizationType embeddings_quant_type(int i) const {
    return QuantizationType::NONE;
  }

  virtual const float16 *embeddings_quant_scales(int i) const {
    return nullptr;
  }

  // ** Access methods for repeated MatrixParams hidden.
  //
  // Returns embedding_network_proto.hidden_size().
  virtual int hidden_size() const = 0;

  // Returns embedding_network_proto.hidden(i).rows().
  virtual int hidden_num_rows(int i) const = 0;

  // Returns embedding_network_proto.hidden(i).rows().
  virtual int hidden_num_cols(int i) const = 0;

  // Returns pointer to beginning of array of floats with all values from
  // embedding_network_proto.hidden(i).
  virtual const void *hidden_weights(int i) const = 0;

  // ** Access methods for repeated MatrixParams hidden_bias.
  //
  // Returns proto.hidden_bias_size().
  virtual int hidden_bias_size() const = 0;

  // Returns number of rows of proto.hidden_bias(i).
  virtual int hidden_bias_num_rows(int i) const = 0;

  // Returns number of columns of proto.hidden_bias(i).
  virtual int hidden_bias_num_cols(int i) const = 0;

  // Returns pointer to elements of proto.hidden_bias(i), in row-major order.
  virtual const void *hidden_bias_weights(int i) const = 0;

  // ** Access methods for optional MatrixParams softmax.
  //
  // Returns 1 if proto has optional field softmax, 0 otherwise.
  virtual int softmax_size() const = 0;

  // Returns number of rows of transpose(proto.softmax()).
  virtual int softmax_num_rows(int i) const = 0;

  // Returns number of columns of transpose(proto.softmax()).
  virtual int softmax_num_cols(int i) const = 0;

  // Returns pointer to elements of transpose(proto.softmax()), in row-major
  // order.
  virtual const void *softmax_weights(int i) const = 0;

  // ** Access methods for optional MatrixParams softmax_bias.
  //
  // Returns 1 if proto has optional field softmax_bias, 0 otherwise.
  virtual int softmax_bias_size() const = 0;

  // Returns number of rows of proto.softmax_bias().
  virtual int softmax_bias_num_rows(int i) const = 0;

  // Returns number of columns of proto.softmax_bias().
  virtual int softmax_bias_num_cols(int i) const = 0;

  // Returns pointer to elements of proto.softmax_bias(), in row-major order.
  virtual const void *softmax_bias_weights(int i) const = 0;

  // ** Access methods for repeated int32 embedding_num_features.
  //
  // Returns proto.embedding_num_features_size().
  virtual int embedding_num_features_size() const = 0;

  // Returns proto.embedding_num_features(i).
  virtual int embedding_num_features(int i) const = 0;

  // Returns true if and only if index is in range [0, size).  Log an error
  // message otherwise.
  static bool InRange(int index, int size) {
    if ((index < 0) || (index >= size)) {
      TC_LOG(ERROR) << "Index " << index << " outside [0, " << size << ")";
      return false;
    }
    return true;
  }
};  // class EmbeddingNetworkParams

}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_COMMON_EMBEDDING_NETWORK_PARAMS_H_
