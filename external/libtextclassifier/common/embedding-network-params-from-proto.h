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

#ifndef LIBTEXTCLASSIFIER_COMMON_EMBEDDING_NETWORK_PARAMS_FROM_PROTO_H_
#define LIBTEXTCLASSIFIER_COMMON_EMBEDDING_NETWORK_PARAMS_FROM_PROTO_H_

#include <algorithm>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "common/embedding-network-package.pb.h"
#include "common/embedding-network-params.h"
#include "common/embedding-network.pb.h"
#include "common/float16.h"
#include "common/little-endian-data.h"
#include "common/task-context.h"
#include "common/task-spec.pb.h"
#include "util/base/integral_types.h"
#include "util/base/logging.h"

namespace libtextclassifier {
namespace nlp_core {

// A wrapper class that owns and exposes an EmbeddingNetworkProto message via
// the EmbeddingNetworkParams interface.
//
// The EmbeddingNetworkParams interface encapsulates the weight matrices of the
// embeddings, hidden and softmax layers as transposed versions of their
// counterparts in the original EmbeddingNetworkProto. The matrices in the proto
// passed to this class' constructor must likewise already have been transposed.
// See embedding-network-params.h for details.
class EmbeddingNetworkParamsFromProto : public EmbeddingNetworkParams {
 public:
  // Constructor that takes ownership of the provided proto. See class-comment
  // for the requirements that certain weight matrices must satisfy.
  explicit EmbeddingNetworkParamsFromProto(
      std::unique_ptr<EmbeddingNetworkProto> proto)
      : proto_(std::move(proto)) {
    valid_ = true;

    // Initialize these vectors to have the required number of elements
    // regardless of quantization status. This is to support the unlikely case
    // where only some embeddings are quantized, along with the fact that
    // EmbeddingNetworkParams interface accesses them by index.
    embeddings_quant_scales_.resize(proto_->embeddings_size());
    embeddings_quant_weights_.resize(proto_->embeddings_size());
    for (int i = 0; i < proto_->embeddings_size(); ++i) {
      MatrixParams *embedding = proto_->mutable_embeddings()->Mutable(i);
      if (!embedding->is_quantized()) {
        continue;
      }

      bool success = FillVectorFromDataBytesInLittleEndian(
          embedding->bytes_for_quantized_values(),
          embedding->rows() * embedding->cols(),
          &(embeddings_quant_weights_[i]));
      if (!success) {
        TC_LOG(ERROR) << "Problem decoding quant_weights for embeddings #" << i;
        valid_ = false;
      }

      // The repeated field bytes_for_quantized_values uses a lot of memory.
      // Since it's no longer necessary (and we own the proto), we clear it.
      embedding->clear_bytes_for_quantized_values();

      success = FillVectorFromDataBytesInLittleEndian(
          embedding->bytes_for_col_scales(),
          embedding->rows(),
          &(embeddings_quant_scales_[i]));
      if (!success) {
        TC_LOG(ERROR) << "Problem decoding col_scales for embeddings #" << i;
        valid_ = false;
      }

      // See comments for clear_bytes_for_quantized_values().
      embedding->clear_bytes_for_col_scales();
    }
  }

  const TaskSpec *GetTaskSpec() override {
    if (!proto_) {
      return nullptr;
    }
    auto extension_id = task_spec_in_embedding_network_proto;
    if (proto_->HasExtension(extension_id)) {
      return &(proto_->GetExtension(extension_id));
    } else {
      TC_LOG(ERROR) << "Unable to get TaskSpec from EmbeddingNetworkProto";
      return nullptr;
    }
  }

  // Returns true if these params are valid.  False otherwise (e.g., if the
  // original proto data was corrupted).
  bool is_valid() { return valid_; }

 protected:
  int embeddings_size() const override { return proto_->embeddings_size(); }

  int embeddings_num_rows(int i) const override {
    TC_DCHECK(InRange(i, embeddings_size()));
    return proto_->embeddings(i).rows();
  }

  int embeddings_num_cols(int i) const override {
    TC_DCHECK(InRange(i, embeddings_size()));
    return proto_->embeddings(i).cols();
  }

  const void *embeddings_weights(int i) const override {
    TC_DCHECK(InRange(i, embeddings_size()));
    if (proto_->embeddings(i).is_quantized()) {
      return static_cast<const void *>(embeddings_quant_weights_.at(i).data());
    } else {
      return static_cast<const void *>(proto_->embeddings(i).value().data());
    }
  }

  QuantizationType embeddings_quant_type(int i) const override {
    TC_DCHECK(InRange(i, embeddings_size()));
    return proto_->embeddings(i).is_quantized() ? QuantizationType::UINT8
                                                : QuantizationType::NONE;
  }

  const float16 *embeddings_quant_scales(int i) const override {
    TC_DCHECK(InRange(i, embeddings_size()));
    return proto_->embeddings(i).is_quantized()
               ? embeddings_quant_scales_.at(i).data()
               : nullptr;
  }

  int hidden_size() const override { return proto_->hidden_size(); }

  int hidden_num_rows(int i) const override {
    TC_DCHECK(InRange(i, hidden_size()));
    return proto_->hidden(i).rows();
  }

  int hidden_num_cols(int i) const override {
    TC_DCHECK(InRange(i, hidden_size()));
    return proto_->hidden(i).cols();
  }

  const void *hidden_weights(int i) const override {
    TC_DCHECK(InRange(i, hidden_size()));
    return proto_->hidden(i).value().data();
  }

  int hidden_bias_size() const override { return proto_->hidden_bias_size(); }

  int hidden_bias_num_rows(int i) const override {
    TC_DCHECK(InRange(i, hidden_bias_size()));
    return proto_->hidden_bias(i).rows();
  }

  int hidden_bias_num_cols(int i) const override {
    TC_DCHECK(InRange(i, hidden_bias_size()));
    return proto_->hidden_bias(i).cols();
  }

  const void *hidden_bias_weights(int i) const override {
    TC_DCHECK(InRange(i, hidden_bias_size()));
    return proto_->hidden_bias(i).value().data();
  }

  int softmax_size() const override { return proto_->has_softmax() ? 1 : 0; }

  int softmax_num_rows(int i) const override {
    TC_DCHECK(InRange(i, softmax_size()));
    return proto_->has_softmax() ? proto_->softmax().rows() : 0;
  }

  int softmax_num_cols(int i) const override {
    TC_DCHECK(InRange(i, softmax_size()));
    return proto_->has_softmax() ? proto_->softmax().cols() : 0;
  }

  const void *softmax_weights(int i) const override {
    TC_DCHECK(InRange(i, softmax_size()));
    return proto_->has_softmax() ? proto_->softmax().value().data() : nullptr;
  }

  int softmax_bias_size() const override {
    return proto_->has_softmax_bias() ? 1 : 0;
  }

  int softmax_bias_num_rows(int i) const override {
    TC_DCHECK(InRange(i, softmax_bias_size()));
    return proto_->has_softmax_bias() ? proto_->softmax_bias().rows() : 0;
  }

  int softmax_bias_num_cols(int i) const override {
    TC_DCHECK(InRange(i, softmax_bias_size()));
    return proto_->has_softmax_bias() ? proto_->softmax_bias().cols() : 0;
  }

  const void *softmax_bias_weights(int i) const override {
    TC_DCHECK(InRange(i, softmax_bias_size()));
    return proto_->has_softmax_bias() ? proto_->softmax_bias().value().data()
                                      : nullptr;
  }

  int embedding_num_features_size() const override {
    return proto_->embedding_num_features_size();
  }

  int embedding_num_features(int i) const override {
    TC_DCHECK(InRange(i, embedding_num_features_size()));
    return proto_->embedding_num_features(i);
  }

 private:
  std::unique_ptr<EmbeddingNetworkProto> proto_;

  // True if these params are valid.  May be false if the original proto was
  // corrupted.  We prefer to set this to false to CHECK-failing.
  bool valid_;

  // When the embeddings are quantized, these members are used to store their
  // numeric values using the types expected by the rest of the class. Due to
  // technical reasons, the proto stores this info using larger types (i.e.,
  // more bits).
  std::vector<std::vector<float16>> embeddings_quant_scales_;
  std::vector<std::vector<uint8>> embeddings_quant_weights_;
};

}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_COMMON_EMBEDDING_NETWORK_PARAMS_FROM_PROTO_H_
