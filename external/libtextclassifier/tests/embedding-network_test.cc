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
#include "common/embedding-network-params-from-proto.h"
#include "common/embedding-network.pb.h"
#include "common/simple-adder.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier {
namespace nlp_core {
namespace {

using testing::ElementsAreArray;

class TestingEmbeddingNetwork : public EmbeddingNetwork {
 public:
  using EmbeddingNetwork::EmbeddingNetwork;
  using EmbeddingNetwork::FinishComputeFinalScoresInternal;
};

void DiagonalAndBias3x3(int diagonal_value, int bias_value,
                        MatrixParams* weights, MatrixParams* bias) {
  weights->set_rows(3);
  weights->set_cols(3);
  weights->add_value(diagonal_value);
  weights->add_value(0);
  weights->add_value(0);
  weights->add_value(0);
  weights->add_value(diagonal_value);
  weights->add_value(0);
  weights->add_value(0);
  weights->add_value(0);
  weights->add_value(diagonal_value);

  bias->set_rows(3);
  bias->set_cols(1);
  bias->add_value(bias_value);
  bias->add_value(bias_value);
  bias->add_value(bias_value);
}

TEST(EmbeddingNetworkTest, IdentityThroughMultipleLayers) {
  std::unique_ptr<EmbeddingNetworkProto> proto;
  proto.reset(new EmbeddingNetworkProto);

  // These layers should be an identity with bias.
  DiagonalAndBias3x3(/*diagonal_value=*/1, /*bias_value=*/1,
                     proto->add_hidden(), proto->add_hidden_bias());
  DiagonalAndBias3x3(/*diagonal_value=*/1, /*bias_value=*/2,
                     proto->add_hidden(), proto->add_hidden_bias());
  DiagonalAndBias3x3(/*diagonal_value=*/1, /*bias_value=*/3,
                     proto->add_hidden(), proto->add_hidden_bias());
  DiagonalAndBias3x3(/*diagonal_value=*/1, /*bias_value=*/4,
                     proto->add_hidden(), proto->add_hidden_bias());
  DiagonalAndBias3x3(/*diagonal_value=*/1, /*bias_value=*/5,
                     proto->mutable_softmax(), proto->mutable_softmax_bias());

  EmbeddingNetworkParamsFromProto params(std::move(proto));
  TestingEmbeddingNetwork network(&params);

  std::vector<float> input({-2, -1, 0});
  std::vector<float> output;
  network.FinishComputeFinalScoresInternal<SimpleAdder>(
      VectorSpan<float>(input), &output);

  EXPECT_THAT(output, ElementsAreArray({14, 14, 15}));
}

}  // namespace
}  // namespace nlp_core
}  // namespace libtextclassifier
