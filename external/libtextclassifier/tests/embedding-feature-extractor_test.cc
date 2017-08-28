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

#include "common/embedding-feature-extractor.h"

#include "lang_id/language-identifier-features.h"
#include "lang_id/light-sentence-features.h"
#include "lang_id/light-sentence.h"
#include "lang_id/relevant-script-feature.h"
#include "gtest/gtest.h"

namespace libtextclassifier {
namespace nlp_core {

class EmbeddingFeatureExtractorTest : public ::testing::Test {
 public:
  void SetUp() override {
    // Make sure all relevant features are registered:
    lang_id::ContinuousBagOfNgramsFunction::RegisterClass();
    lang_id::RelevantScriptFeature::RegisterClass();
  }
};

// Specialization of EmbeddingFeatureExtractor that extracts from LightSentence.
class TestEmbeddingFeatureExtractor
    : public EmbeddingFeatureExtractor<lang_id::LightSentenceExtractor,
                                       lang_id::LightSentence> {
 public:
  const std::string ArgPrefix() const override { return "test"; }
};

TEST_F(EmbeddingFeatureExtractorTest, NoEmbeddingSpaces) {
  TaskContext context;
  context.SetParameter("test_features", "");
  context.SetParameter("test_embedding_names", "");
  context.SetParameter("test_embedding_dims", "");
  TestEmbeddingFeatureExtractor tefe;
  ASSERT_TRUE(tefe.Init(&context));
  EXPECT_EQ(tefe.NumEmbeddings(), 0);
}

TEST_F(EmbeddingFeatureExtractorTest, GoodSpec) {
  TaskContext context;
  const std::string spec =
      "continuous-bag-of-ngrams(id_dim=5000,size=3);"
      "continuous-bag-of-ngrams(id_dim=7000,size=4)";
  context.SetParameter("test_features", spec);
  context.SetParameter("test_embedding_names", "trigram;quadgram");
  context.SetParameter("test_embedding_dims", "16;24");
  TestEmbeddingFeatureExtractor tefe;
  ASSERT_TRUE(tefe.Init(&context));
  EXPECT_EQ(tefe.NumEmbeddings(), 2);
  EXPECT_EQ(tefe.EmbeddingSize(0), 5000);
  EXPECT_EQ(tefe.EmbeddingDims(0), 16);
  EXPECT_EQ(tefe.EmbeddingSize(1), 7000);
  EXPECT_EQ(tefe.EmbeddingDims(1), 24);
}

TEST_F(EmbeddingFeatureExtractorTest, MissmatchFmlVsNames) {
  TaskContext context;
  const std::string spec =
      "continuous-bag-of-ngrams(id_dim=5000,size=3);"
      "continuous-bag-of-ngrams(id_dim=7000,size=4)";
  context.SetParameter("test_features", spec);
  context.SetParameter("test_embedding_names", "trigram");
  context.SetParameter("test_embedding_dims", "16;16");
  TestEmbeddingFeatureExtractor tefe;
  ASSERT_FALSE(tefe.Init(&context));
}

TEST_F(EmbeddingFeatureExtractorTest, MissmatchFmlVsDims) {
  TaskContext context;
  const std::string spec =
      "continuous-bag-of-ngrams(id_dim=5000,size=3);"
      "continuous-bag-of-ngrams(id_dim=7000,size=4)";
  context.SetParameter("test_features", spec);
  context.SetParameter("test_embedding_names", "trigram;quadgram");
  context.SetParameter("test_embedding_dims", "16;16;32");
  TestEmbeddingFeatureExtractor tefe;
  ASSERT_FALSE(tefe.Init(&context));
}

TEST_F(EmbeddingFeatureExtractorTest, BrokenSpec) {
  TaskContext context;
  const std::string spec =
      "continuous-bag-of-ngrams(id_dim=5000;"
      "continuous-bag-of-ngrams(id_dim=7000,size=4)";
  context.SetParameter("test_features", spec);
  context.SetParameter("test_embedding_names", "trigram;quadgram");
  context.SetParameter("test_embedding_dims", "16;16");
  TestEmbeddingFeatureExtractor tefe;
  ASSERT_FALSE(tefe.Init(&context));
}

TEST_F(EmbeddingFeatureExtractorTest, MissingFeature) {
  TaskContext context;
  const std::string spec =
      "continuous-bag-of-ngrams(id_dim=5000,size=3);"
      "no-such-feature";
  context.SetParameter("test_features", spec);
  context.SetParameter("test_embedding_names", "trigram;foo");
  context.SetParameter("test_embedding_dims", "16;16");
  TestEmbeddingFeatureExtractor tefe;
  ASSERT_FALSE(tefe.Init(&context));
}

TEST_F(EmbeddingFeatureExtractorTest, MultipleFeatures) {
  TaskContext context;
  const std::string spec =
      "continuous-bag-of-ngrams(id_dim=1000,size=3);"
      "continuous-bag-of-relevant-scripts";
  context.SetParameter("test_features", spec);
  context.SetParameter("test_embedding_names", "trigram;script");
  context.SetParameter("test_embedding_dims", "8;16");
  TestEmbeddingFeatureExtractor tefe;
  ASSERT_TRUE(tefe.Init(&context));
  EXPECT_EQ(tefe.NumEmbeddings(), 2);
  EXPECT_EQ(tefe.EmbeddingSize(0), 1000);
  EXPECT_EQ(tefe.EmbeddingDims(0), 8);

  // continuous-bag-of-relevant-scripts has its own hard-wired vocabulary size.
  // We don't want this test to depend on that value; we just check it's bigger
  // than 0.
  EXPECT_GT(tefe.EmbeddingSize(1), 0);
  EXPECT_EQ(tefe.EmbeddingDims(1), 16);
}

}  // namespace nlp_core
}  // namespace libtextclassifier
