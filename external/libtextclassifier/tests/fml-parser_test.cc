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

#include "common/fml-parser.h"

#include "common/feature-descriptors.h"
#include "gtest/gtest.h"

namespace libtextclassifier {
namespace nlp_core {

TEST(FMLParserTest, NoFeature) {
  FMLParser fml_parser;
  FeatureExtractorDescriptor descriptor;
  const std::string kFeatureName = "";
  EXPECT_TRUE(fml_parser.Parse(kFeatureName, &descriptor));
  EXPECT_EQ(0, descriptor.feature_size());
}

TEST(FMLParserTest, FeatureWithNoParams) {
  FMLParser fml_parser;
  FeatureExtractorDescriptor descriptor;
  const std::string kFeatureName = "continuous-bag-of-relevant-scripts";
  EXPECT_TRUE(fml_parser.Parse(kFeatureName, &descriptor));
  EXPECT_EQ(1, descriptor.feature_size());
  EXPECT_EQ(kFeatureName, descriptor.feature(0).type());
}

TEST(FMLParserTest, FeatureWithOneKeywordParameter) {
  FMLParser fml_parser;
  FeatureExtractorDescriptor descriptor;
  EXPECT_TRUE(fml_parser.Parse("myfeature(start=2)", &descriptor));
  EXPECT_EQ(1, descriptor.feature_size());
  EXPECT_EQ("myfeature", descriptor.feature(0).type());
  EXPECT_EQ(1, descriptor.feature(0).parameter_size());
  EXPECT_EQ("start", descriptor.feature(0).parameter(0).name());
  EXPECT_EQ("2", descriptor.feature(0).parameter(0).value());
  EXPECT_FALSE(descriptor.feature(0).has_argument());
}

TEST(FMLParserTest, FeatureWithDefaultArgumentNegative) {
  FMLParser fml_parser;
  FeatureExtractorDescriptor descriptor;
  EXPECT_TRUE(fml_parser.Parse("offset(-3)", &descriptor));
  EXPECT_EQ(1, descriptor.feature_size());
  EXPECT_EQ("offset", descriptor.feature(0).type());
  EXPECT_EQ(0, descriptor.feature(0).parameter_size());
  EXPECT_EQ(-3, descriptor.feature(0).argument());
}

TEST(FMLParserTest, FeatureWithDefaultArgumentPositive) {
  FMLParser fml_parser;
  FeatureExtractorDescriptor descriptor;
  EXPECT_TRUE(fml_parser.Parse("delta(7)", &descriptor));
  EXPECT_EQ(1, descriptor.feature_size());
  EXPECT_EQ("delta", descriptor.feature(0).type());
  EXPECT_EQ(0, descriptor.feature(0).parameter_size());
  EXPECT_EQ(7, descriptor.feature(0).argument());
}

TEST(FMLParserTest, FeatureWithDefaultArgumentZero) {
  FMLParser fml_parser;
  FeatureExtractorDescriptor descriptor;
  EXPECT_TRUE(fml_parser.Parse("delta(0)", &descriptor));
  EXPECT_EQ(1, descriptor.feature_size());
  EXPECT_EQ("delta", descriptor.feature(0).type());
  EXPECT_EQ(0, descriptor.feature(0).parameter_size());
  EXPECT_EQ(0, descriptor.feature(0).argument());
}

TEST(FMLParserTest, FeatureWithManyKeywordParameters) {
  FMLParser fml_parser;
  FeatureExtractorDescriptor descriptor;
  EXPECT_TRUE(fml_parser.Parse("myfeature(ratio=0.316,start=2,name=\"foo\")",
                               &descriptor));
  EXPECT_EQ(1, descriptor.feature_size());
  EXPECT_EQ("myfeature", descriptor.feature(0).type());
  EXPECT_EQ(3, descriptor.feature(0).parameter_size());
  EXPECT_EQ("ratio", descriptor.feature(0).parameter(0).name());
  EXPECT_EQ("0.316", descriptor.feature(0).parameter(0).value());
  EXPECT_EQ("start", descriptor.feature(0).parameter(1).name());
  EXPECT_EQ("2", descriptor.feature(0).parameter(1).value());
  EXPECT_EQ("name", descriptor.feature(0).parameter(2).name());
  EXPECT_EQ("foo", descriptor.feature(0).parameter(2).value());
  EXPECT_FALSE(descriptor.feature(0).has_argument());
}

TEST(FMLParserTest, FeatureWithAllKindsOfParameters) {
  FMLParser fml_parser;
  FeatureExtractorDescriptor descriptor;
  EXPECT_TRUE(
      fml_parser.Parse("myfeature(17,ratio=0.316,start=2)", &descriptor));
  EXPECT_EQ(1, descriptor.feature_size());
  EXPECT_EQ("myfeature", descriptor.feature(0).type());
  EXPECT_EQ(2, descriptor.feature(0).parameter_size());
  EXPECT_EQ("ratio", descriptor.feature(0).parameter(0).name());
  EXPECT_EQ("0.316", descriptor.feature(0).parameter(0).value());
  EXPECT_EQ("start", descriptor.feature(0).parameter(1).name());
  EXPECT_EQ("2", descriptor.feature(0).parameter(1).value());
  EXPECT_EQ(17, descriptor.feature(0).argument());
}

TEST(FMLParserTest, FeatureWithWhitespaces) {
  FMLParser fml_parser;
  FeatureExtractorDescriptor descriptor;
  EXPECT_TRUE(fml_parser.Parse(
      "  myfeature\t\t\t\n(17,\nratio=0.316  ,  start=2)  ", &descriptor));
  EXPECT_EQ(1, descriptor.feature_size());
  EXPECT_EQ("myfeature", descriptor.feature(0).type());
  EXPECT_EQ(2, descriptor.feature(0).parameter_size());
  EXPECT_EQ("ratio", descriptor.feature(0).parameter(0).name());
  EXPECT_EQ("0.316", descriptor.feature(0).parameter(0).value());
  EXPECT_EQ("start", descriptor.feature(0).parameter(1).name());
  EXPECT_EQ("2", descriptor.feature(0).parameter(1).value());
  EXPECT_EQ(17, descriptor.feature(0).argument());
}

TEST(FMLParserTest, Broken_ParamWithoutValue) {
  FMLParser fml_parser;
  FeatureExtractorDescriptor descriptor;
  EXPECT_FALSE(
      fml_parser.Parse("myfeature(17,ratio=0.316,start)", &descriptor));
}

TEST(FMLParserTest, Broken_MissingCloseParen) {
  FMLParser fml_parser;
  FeatureExtractorDescriptor descriptor;
  EXPECT_FALSE(fml_parser.Parse("myfeature(17,ratio=0.316", &descriptor));
}

TEST(FMLParserTest, Broken_MissingOpenParen) {
  FMLParser fml_parser;
  FeatureExtractorDescriptor descriptor;
  EXPECT_FALSE(fml_parser.Parse("myfeature17,ratio=0.316)", &descriptor));
}

TEST(FMLParserTest, Broken_MissingQuote) {
  FMLParser fml_parser;
  FeatureExtractorDescriptor descriptor;
  EXPECT_FALSE(fml_parser.Parse("count(17,name=\"foo)", &descriptor));
}

}  // namespace nlp_core
}  // namespace libtextclassifier
