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

#ifndef LIBTEXTCLASSIFIER_COMMON_MEMORY_IMAGE_EMBEDDING_NETWORK_PARAMS_FROM_IMAGE_H_
#define LIBTEXTCLASSIFIER_COMMON_MEMORY_IMAGE_EMBEDDING_NETWORK_PARAMS_FROM_IMAGE_H_

#include "common/embedding-network-package.pb.h"
#include "common/embedding-network-params.h"
#include "common/embedding-network.pb.h"
#include "common/memory_image/memory-image-reader.h"
#include "util/base/integral_types.h"

namespace libtextclassifier {
namespace nlp_core {

// EmbeddingNetworkParams backed by a memory image.
//
// In this context, a memory image is like an EmbeddingNetworkProto, but with
// all repeated weights (>99% of the size) directly usable (with no parsing
// required).
class EmbeddingNetworkParamsFromImage : public EmbeddingNetworkParams {
 public:
  // Constructs an EmbeddingNetworkParamsFromImage, using the memory image that
  // starts at address start and contains num_bytes bytes.
  EmbeddingNetworkParamsFromImage(const void *start, uint64 num_bytes)
      : memory_reader_(start, num_bytes),
        trimmed_proto_(memory_reader_.trimmed_proto()) {
    embeddings_blob_offset_ = 0;

    hidden_blob_offset_ = embeddings_blob_offset_ + embeddings_size();
    if (trimmed_proto_.embeddings_size() &&
        trimmed_proto_.embeddings(0).is_quantized()) {
      // Adjust for quantization: each quantized matrix takes two blobs (instead
      // of one): one for the quantized values and one for the scales.
      hidden_blob_offset_ += embeddings_size();
    }

    hidden_bias_blob_offset_ = hidden_blob_offset_ + hidden_size();
    softmax_blob_offset_ = hidden_bias_blob_offset_ + hidden_bias_size();
    softmax_bias_blob_offset_ = softmax_blob_offset_ + softmax_size();
  }

  ~EmbeddingNetworkParamsFromImage() override {}

  const TaskSpec *GetTaskSpec() override {
    auto extension_id = task_spec_in_embedding_network_proto;
    if (trimmed_proto_.HasExtension(extension_id)) {
      return &(trimmed_proto_.GetExtension(extension_id));
    } else {
      return nullptr;
    }
  }

 protected:
  int embeddings_size() const override {
    return trimmed_proto_.embeddings_size();
  }

  int embeddings_num_rows(int i) const override {
    TC_DCHECK(InRange(i, embeddings_size()));
    return trimmed_proto_.embeddings(i).rows();
  }

  int embeddings_num_cols(int i) const override {
    TC_DCHECK(InRange(i, embeddings_size()));
    return trimmed_proto_.embeddings(i).cols();
  }

  const void *embeddings_weights(int i) const override {
    TC_DCHECK(InRange(i, embeddings_size()));
    const int blob_index = trimmed_proto_.embeddings(i).is_quantized()
                               ? (embeddings_blob_offset_ + 2 * i)
                               : (embeddings_blob_offset_ + i);
    DataBlobView data_blob_view = memory_reader_.data_blob_view(blob_index);
    return data_blob_view.data();
  }

  QuantizationType embeddings_quant_type(int i) const override {
    TC_DCHECK(InRange(i, embeddings_size()));
    if (trimmed_proto_.embeddings(i).is_quantized()) {
      return QuantizationType::UINT8;
    } else {
      return QuantizationType::NONE;
    }
  }

  const float16 *embeddings_quant_scales(int i) const override {
    TC_DCHECK(InRange(i, embeddings_size()));
    if (trimmed_proto_.embeddings(i).is_quantized()) {
      // Each embedding matrix has two atttached data blobs (hence the "2 * i"):
      // one blob with the quantized values and (immediately after it, hence the
      // "+ 1") one blob with the scales.
      int blob_index = embeddings_blob_offset_ + 2 * i + 1;
      DataBlobView data_blob_view = memory_reader_.data_blob_view(blob_index);
      return reinterpret_cast<const float16 *>(data_blob_view.data());
    } else {
      return nullptr;
    }
  }

  int hidden_size() const override { return trimmed_proto_.hidden_size(); }

  int hidden_num_rows(int i) const override {
    TC_DCHECK(InRange(i, hidden_size()));
    return trimmed_proto_.hidden(i).rows();
  }

  int hidden_num_cols(int i) const override {
    TC_DCHECK(InRange(i, hidden_size()));
    return trimmed_proto_.hidden(i).cols();
  }

  const void *hidden_weights(int i) const override {
    TC_DCHECK(InRange(i, hidden_size()));
    DataBlobView data_blob_view =
        memory_reader_.data_blob_view(hidden_blob_offset_ + i);
    return data_blob_view.data();
  }

  int hidden_bias_size() const override {
    return trimmed_proto_.hidden_bias_size();
  }

  int hidden_bias_num_rows(int i) const override {
    TC_DCHECK(InRange(i, hidden_bias_size()));
    return trimmed_proto_.hidden_bias(i).rows();
  }

  int hidden_bias_num_cols(int i) const override {
    TC_DCHECK(InRange(i, hidden_bias_size()));
    return trimmed_proto_.hidden_bias(i).cols();
  }

  const void *hidden_bias_weights(int i) const override {
    TC_DCHECK(InRange(i, hidden_bias_size()));
    DataBlobView data_blob_view =
        memory_reader_.data_blob_view(hidden_bias_blob_offset_ + i);
    return data_blob_view.data();
  }

  int softmax_size() const override {
    return trimmed_proto_.has_softmax() ? 1 : 0;
  }

  int softmax_num_rows(int i) const override {
    TC_DCHECK(InRange(i, softmax_size()));
    return trimmed_proto_.softmax().rows();
  }

  int softmax_num_cols(int i) const override {
    TC_DCHECK(InRange(i, softmax_size()));
    return trimmed_proto_.softmax().cols();
  }

  const void *softmax_weights(int i) const override {
    TC_DCHECK(InRange(i, softmax_size()));
    DataBlobView data_blob_view =
        memory_reader_.data_blob_view(softmax_blob_offset_ + i);
    return data_blob_view.data();
  }

  int softmax_bias_size() const override {
    return trimmed_proto_.has_softmax_bias() ? 1 : 0;
  }

  int softmax_bias_num_rows(int i) const override {
    TC_DCHECK(InRange(i, softmax_bias_size()));
    return trimmed_proto_.softmax_bias().rows();
  }

  int softmax_bias_num_cols(int i) const override {
    TC_DCHECK(InRange(i, softmax_bias_size()));
    return trimmed_proto_.softmax_bias().cols();
  }

  const void *softmax_bias_weights(int i) const override {
    TC_DCHECK(InRange(i, softmax_bias_size()));
    DataBlobView data_blob_view =
        memory_reader_.data_blob_view(softmax_bias_blob_offset_ + i);
    return data_blob_view.data();
  }

  int embedding_num_features_size() const override {
    return trimmed_proto_.embedding_num_features_size();
  }

  int embedding_num_features(int i) const override {
    TC_DCHECK(InRange(i, embedding_num_features_size()));
    return trimmed_proto_.embedding_num_features(i);
  }

 private:
  MemoryImageReader<EmbeddingNetworkProto> memory_reader_;

  const EmbeddingNetworkProto &trimmed_proto_;

  // 0-based offsets in the list of data blobs for the different MatrixParams
  // fields.  E.g., the 1st hidden MatrixParams has its weights stored in the
  // data blob number hidden_blob_offset_, the 2nd one in hidden_blob_offset_ +
  // 1, and so on.
  int embeddings_blob_offset_;
  int hidden_blob_offset_;
  int hidden_bias_blob_offset_;
  int softmax_blob_offset_;
  int softmax_bias_blob_offset_;
};

}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_COMMON_MEMORY_IMAGE_EMBEDDING_NETWORK_PARAMS_FROM_IMAGE_H_
