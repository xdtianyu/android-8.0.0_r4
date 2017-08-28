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

#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier {
namespace {

class TestingCachedFeatures : public CachedFeatures {
 public:
  using CachedFeatures::CachedFeatures;
  using CachedFeatures::RemapV0FeatureVector;
};

TEST(CachedFeaturesTest, Simple) {
  std::vector<Token> tokens;
  tokens.push_back(Token());
  tokens.push_back(Token());
  tokens.push_back(Token("Hello", 0, 1));
  tokens.push_back(Token("World", 1, 2));
  tokens.push_back(Token("today!", 2, 3));
  tokens.push_back(Token());
  tokens.push_back(Token());

  std::vector<std::vector<int>> sparse_features(tokens.size());
  for (int i = 0; i < sparse_features.size(); ++i) {
    sparse_features[i].push_back(i);
  }
  std::vector<std::vector<float>> dense_features(tokens.size());
  for (int i = 0; i < dense_features.size(); ++i) {
    dense_features[i].push_back(-i);
  }

  TestingCachedFeatures feature_extractor(
      tokens, /*context_size=*/2, sparse_features, dense_features,
      [](const std::vector<int>& sparse_features,
         const std::vector<float>& dense_features, float* features) {
        features[0] = sparse_features[0];
        features[1] = sparse_features[0];
        features[2] = dense_features[0];
        features[3] = dense_features[0];
        features[4] = 123;
        return true;
      },
      5);

  VectorSpan<float> features;
  VectorSpan<Token> output_tokens;
  EXPECT_TRUE(feature_extractor.Get(2, &features, &output_tokens));
  for (int i = 0; i < 5; i++) {
    EXPECT_EQ(features[i * 5 + 0], i) << "Feature " << i;
    EXPECT_EQ(features[i * 5 + 1], i) << "Feature " << i;
    EXPECT_EQ(features[i * 5 + 2], -i) << "Feature " << i;
    EXPECT_EQ(features[i * 5 + 3], -i) << "Feature " << i;
    EXPECT_EQ(features[i * 5 + 4], 123) << "Feature " << i;
  }
}

TEST(CachedFeaturesTest, InvalidInput) {
  std::vector<Token> tokens;
  tokens.push_back(Token());
  tokens.push_back(Token());
  tokens.push_back(Token("Hello", 0, 1));
  tokens.push_back(Token("World", 1, 2));
  tokens.push_back(Token("today!", 2, 3));
  tokens.push_back(Token());
  tokens.push_back(Token());

  std::vector<std::vector<int>> sparse_features(tokens.size());
  std::vector<std::vector<float>> dense_features(tokens.size());

  TestingCachedFeatures feature_extractor(
      tokens, /*context_size=*/2, sparse_features, dense_features,
      [](const std::vector<int>& sparse_features,
         const std::vector<float>& dense_features,
         float* features) { return true; },
      /*feature_vector_size=*/5);

  VectorSpan<float> features;
  VectorSpan<Token> output_tokens;
  EXPECT_FALSE(feature_extractor.Get(-1000, &features, &output_tokens));
  EXPECT_FALSE(feature_extractor.Get(-1, &features, &output_tokens));
  EXPECT_FALSE(feature_extractor.Get(0, &features, &output_tokens));
  EXPECT_TRUE(feature_extractor.Get(2, &features, &output_tokens));
  EXPECT_TRUE(feature_extractor.Get(4, &features, &output_tokens));
  EXPECT_FALSE(feature_extractor.Get(5, &features, &output_tokens));
  EXPECT_FALSE(feature_extractor.Get(500, &features, &output_tokens));
}

TEST(CachedFeaturesTest, RemapV0FeatureVector) {
  std::vector<Token> tokens;
  tokens.push_back(Token());
  tokens.push_back(Token());
  tokens.push_back(Token("Hello", 0, 1));
  tokens.push_back(Token("World", 1, 2));
  tokens.push_back(Token("today!", 2, 3));
  tokens.push_back(Token());
  tokens.push_back(Token());

  std::vector<std::vector<int>> sparse_features(tokens.size());
  std::vector<std::vector<float>> dense_features(tokens.size());

  TestingCachedFeatures feature_extractor(
      tokens, /*context_size=*/2, sparse_features, dense_features,
      [](const std::vector<int>& sparse_features,
         const std::vector<float>& dense_features,
         float* features) { return true; },
      /*feature_vector_size=*/5);

  std::vector<float> features_orig(5 * 5);
  for (int i = 0; i < features_orig.size(); i++) {
    features_orig[i] = i;
  }
  VectorSpan<float> features;

  feature_extractor.SetV0FeatureMode(0);
  features = VectorSpan<float>(features_orig);
  feature_extractor.RemapV0FeatureVector(&features);
  EXPECT_EQ(
      std::vector<float>({0,  1,  2,  3,  4,  5,  6,  7,  8,  9,  10, 11, 12,
                          13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24}),
      std::vector<float>(features.begin(), features.end()));

  feature_extractor.SetV0FeatureMode(2);
  features = VectorSpan<float>(features_orig);
  feature_extractor.RemapV0FeatureVector(&features);
  EXPECT_EQ(std::vector<float>({0, 1, 5, 6,  10, 11, 15, 16, 20, 21, 2,  3, 4,
                                7, 8, 9, 12, 13, 14, 17, 18, 19, 22, 23, 24}),
            std::vector<float>(features.begin(), features.end()));
}

}  // namespace
}  // namespace libtextclassifier
