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

#include "smartselect/text-classification-model.h"

#include <fcntl.h>
#include <stdio.h>
#include <memory>
#include <string>

#include "base.h"
#include "gtest/gtest.h"

namespace libtextclassifier {
namespace {

std::string GetModelPath() {
  return TEST_DATA_DIR "smartselection.model";
}

TEST(TextClassificationModelTest, ReadModelOptions) {
  const std::string model_path = GetModelPath();
  int fd = open(model_path.c_str(), O_RDONLY);
  ModelOptions model_options;
  ASSERT_TRUE(ReadSelectionModelOptions(fd, &model_options));
  close(fd);

  EXPECT_EQ("en", model_options.language());
  EXPECT_GT(model_options.version(), 0);
}

TEST(TextClassificationModelTest, SuggestSelection) {
  const std::string model_path = GetModelPath();
  int fd = open(model_path.c_str(), O_RDONLY);
  std::unique_ptr<TextClassificationModel> model(
      new TextClassificationModel(fd));
  close(fd);

  EXPECT_EQ(model->SuggestSelection(
                "this afternoon Barack Obama gave a speech at", {15, 21}),
            std::make_pair(15, 27));

  // Try passing whole string.
  // If more than 1 token is specified, we should return back what entered.
  EXPECT_EQ(model->SuggestSelection("350 Third Street, Cambridge", {0, 27}),
            std::make_pair(0, 27));

  // Single letter.
  EXPECT_EQ(std::make_pair(0, 1), model->SuggestSelection("a", {0, 1}));

  // Single word.
  EXPECT_EQ(std::make_pair(0, 4), model->SuggestSelection("asdf", {0, 4}));
}

TEST(TextClassificationModelTest, SuggestSelectionsAreSymmetric) {
  const std::string model_path = GetModelPath();
  int fd = open(model_path.c_str(), O_RDONLY);
  std::unique_ptr<TextClassificationModel> model(
      new TextClassificationModel(fd));
  close(fd);

  EXPECT_EQ(std::make_pair(0, 27),
            model->SuggestSelection("350 Third Street, Cambridge", {0, 3}));
  EXPECT_EQ(std::make_pair(0, 27),
            model->SuggestSelection("350 Third Street, Cambridge", {4, 9}));
  EXPECT_EQ(std::make_pair(0, 27),
            model->SuggestSelection("350 Third Street, Cambridge", {10, 16}));
  EXPECT_EQ(std::make_pair(6, 33),
            model->SuggestSelection("a\nb\nc\n350 Third Street, Cambridge",
                                    {16, 22}));
}

TEST(TextClassificationModelTest, SuggestSelectionWithNewLine) {
  const std::string model_path = GetModelPath();
  int fd = open(model_path.c_str(), O_RDONLY);
  std::unique_ptr<TextClassificationModel> model(
      new TextClassificationModel(fd));
  close(fd);

  std::tuple<int, int> selection;
  selection = model->SuggestSelection("abc\nBarack Obama", {4, 10});
  EXPECT_EQ(4, std::get<0>(selection));
  EXPECT_EQ(16, std::get<1>(selection));

  selection = model->SuggestSelection("Barack Obama\nabc", {0, 6});
  EXPECT_EQ(0, std::get<0>(selection));
  EXPECT_EQ(12, std::get<1>(selection));
}

TEST(TextClassificationModelTest, SuggestSelectionWithPunctuation) {
  const std::string model_path = GetModelPath();
  int fd = open(model_path.c_str(), O_RDONLY);
  std::unique_ptr<TextClassificationModel> model(
      new TextClassificationModel(fd));
  close(fd);

  std::tuple<int, int> selection;

  // From the right.
  selection = model->SuggestSelection(
      "this afternoon Barack Obama, gave a speech at", {15, 21});
  EXPECT_EQ(15, std::get<0>(selection));
  EXPECT_EQ(27, std::get<1>(selection));

  // From the right multiple.
  selection = model->SuggestSelection(
      "this afternoon Barack Obama,.,.,, gave a speech at", {15, 21});
  EXPECT_EQ(15, std::get<0>(selection));
  EXPECT_EQ(27, std::get<1>(selection));

  // From the left multiple.
  selection = model->SuggestSelection(
      "this afternoon ,.,.,,Barack Obama gave a speech at", {21, 27});
  EXPECT_EQ(21, std::get<0>(selection));
  EXPECT_EQ(27, std::get<1>(selection));

  // From both sides.
  selection = model->SuggestSelection(
      "this afternoon !Barack Obama,- gave a speech at", {16, 22});
  EXPECT_EQ(16, std::get<0>(selection));
  EXPECT_EQ(28, std::get<1>(selection));
}

class TestingTextClassificationModel
    : public libtextclassifier::TextClassificationModel {
 public:
  explicit TestingTextClassificationModel(int fd)
      : libtextclassifier::TextClassificationModel(fd) {}

  using libtextclassifier::TextClassificationModel::StripPunctuation;

  void DisableClassificationHints() {
    sharing_options_.set_always_accept_url_hint(false);
    sharing_options_.set_always_accept_email_hint(false);
  }
};

TEST(TextClassificationModelTest, StripPunctuation) {
  const std::string model_path = GetModelPath();
  int fd = open(model_path.c_str(), O_RDONLY);
  std::unique_ptr<TestingTextClassificationModel> model(
      new TestingTextClassificationModel(fd));
  close(fd);

  EXPECT_EQ(std::make_pair(3, 10),
            model->StripPunctuation({0, 10}, ".,-abcd.()"));
  EXPECT_EQ(std::make_pair(0, 6), model->StripPunctuation({0, 6}, "(abcd)"));
  EXPECT_EQ(std::make_pair(1, 5), model->StripPunctuation({0, 6}, "[abcd]"));
  EXPECT_EQ(std::make_pair(1, 5), model->StripPunctuation({0, 6}, "{abcd}"));

  // Empty result.
  EXPECT_EQ(std::make_pair(0, 0), model->StripPunctuation({0, 1}, "&"));
  EXPECT_EQ(std::make_pair(0, 0), model->StripPunctuation({0, 4}, "&-,}"));

  // Invalid indices
  EXPECT_EQ(std::make_pair(-1, 523), model->StripPunctuation({-1, 523}, "a"));
  EXPECT_EQ(std::make_pair(-1, -1), model->StripPunctuation({-1, -1}, "a"));
  EXPECT_EQ(std::make_pair(0, -1), model->StripPunctuation({0, -1}, "a"));
}

TEST(TextClassificationModelTest, SuggestSelectionNoCrashWithJunk) {
  const std::string model_path = GetModelPath();
  int fd = open(model_path.c_str(), O_RDONLY);
  std::unique_ptr<TextClassificationModel> ff_model(
      new TextClassificationModel(fd));
  close(fd);

  std::tuple<int, int> selection;

  // Try passing in bunch of invalid selections.
  selection = ff_model->SuggestSelection("", {0, 27});
  // If more than 1 token is specified, we should return back what entered.
  EXPECT_EQ(0, std::get<0>(selection));
  EXPECT_EQ(27, std::get<1>(selection));

  selection = ff_model->SuggestSelection("", {-10, 27});
  // If more than 1 token is specified, we should return back what entered.
  EXPECT_EQ(-10, std::get<0>(selection));
  EXPECT_EQ(27, std::get<1>(selection));

  selection = ff_model->SuggestSelection("Word 1 2 3 hello!", {0, 27});
  // If more than 1 token is specified, we should return back what entered.
  EXPECT_EQ(0, std::get<0>(selection));
  EXPECT_EQ(27, std::get<1>(selection));

  selection = ff_model->SuggestSelection("Word 1 2 3 hello!", {-30, 300});
  // If more than 1 token is specified, we should return back what entered.
  EXPECT_EQ(-30, std::get<0>(selection));
  EXPECT_EQ(300, std::get<1>(selection));

  selection = ff_model->SuggestSelection("Word 1 2 3 hello!", {-10, -1});
  // If more than 1 token is specified, we should return back what entered.
  EXPECT_EQ(-10, std::get<0>(selection));
  EXPECT_EQ(-1, std::get<1>(selection));

  selection = ff_model->SuggestSelection("Word 1 2 3 hello!", {100, 17});
  // If more than 1 token is specified, we should return back what entered.
  EXPECT_EQ(100, std::get<0>(selection));
  EXPECT_EQ(17, std::get<1>(selection));
}

namespace {

std::string FindBestResult(std::vector<std::pair<std::string, float>> results) {
  if (results.empty()) {
    return "<INVALID RESULTS>";
  }

  std::sort(results.begin(), results.end(),
            [](const std::pair<std::string, float> a,
               const std::pair<std::string, float> b) {
              return a.second > b.second;
            });
  return results[0].first;
}

}  // namespace

TEST(TextClassificationModelTest, ClassifyText) {
  const std::string model_path = GetModelPath();
  int fd = open(model_path.c_str(), O_RDONLY);
  std::unique_ptr<TestingTextClassificationModel> model(
      new TestingTextClassificationModel(fd));
  close(fd);

  model->DisableClassificationHints();
  EXPECT_EQ("other",
            FindBestResult(model->ClassifyText(
                "this afternoon Barack Obama gave a speech at", {15, 27})));
  EXPECT_EQ("other",
            FindBestResult(model->ClassifyText("you@android.com", {0, 15})));
  EXPECT_EQ("other", FindBestResult(model->ClassifyText(
                         "Contact me at you@android.com", {14, 29})));
  EXPECT_EQ("phone", FindBestResult(model->ClassifyText(
                         "Call me at (800) 123-456 today", {11, 24})));
  EXPECT_EQ("other", FindBestResult(model->ClassifyText(
                         "Visit www.google.com every today!", {6, 20})));

  // More lines.
  EXPECT_EQ("other",
            FindBestResult(model->ClassifyText(
                "this afternoon Barack Obama gave a speech at|Visit "
                "www.google.com every today!|Call me at (800) 123-456 today.",
                {15, 27})));
  EXPECT_EQ("other",
            FindBestResult(model->ClassifyText(
                "this afternoon Barack Obama gave a speech at|Visit "
                "www.google.com every today!|Call me at (800) 123-456 today.",
                {51, 65})));
  EXPECT_EQ("phone",
            FindBestResult(model->ClassifyText(
                "this afternoon Barack Obama gave a speech at|Visit "
                "www.google.com every today!|Call me at (800) 123-456 today.",
                {90, 103})));

  // Single word.
  EXPECT_EQ("other", FindBestResult(model->ClassifyText("obama", {0, 5})));
  EXPECT_EQ("other", FindBestResult(model->ClassifyText("asdf", {0, 4})));
  EXPECT_EQ("<INVALID RESULTS>",
            FindBestResult(model->ClassifyText("asdf", {0, 0})));

  // Junk.
  EXPECT_EQ("<INVALID RESULTS>",
            FindBestResult(model->ClassifyText("", {0, 0})));
  EXPECT_EQ("<INVALID RESULTS>", FindBestResult(model->ClassifyText(
                                     "a\n\n\n\nx x x\n\n\n\n\n\n", {1, 5})));
}

TEST(TextClassificationModelTest, ClassifyTextWithHints) {
  const std::string model_path = GetModelPath();
  int fd = open(model_path.c_str(), O_RDONLY);
  std::unique_ptr<TestingTextClassificationModel> model(
      new TestingTextClassificationModel(fd));
  close(fd);

  // When EMAIL hint is passed, the result should be email.
  EXPECT_EQ("email",
            FindBestResult(model->ClassifyText(
                "x", {0, 1}, TextClassificationModel::SELECTION_IS_EMAIL)));
  // When URL hint is passed, the result should be email.
  EXPECT_EQ("url",
            FindBestResult(model->ClassifyText(
                "x", {0, 1}, TextClassificationModel::SELECTION_IS_URL)));
  // When both hints are passed, the result should be url (as it's probably
  // better to let Chrome handle this case).
  EXPECT_EQ("url", FindBestResult(model->ClassifyText(
                       "x", {0, 1},
                       TextClassificationModel::SELECTION_IS_EMAIL |
                           TextClassificationModel::SELECTION_IS_URL)));

  // With disabled hints, we should get the same prediction regardless of the
  // hint.
  model->DisableClassificationHints();
  EXPECT_EQ(model->ClassifyText("x", {0, 1}, 0),
            model->ClassifyText("x", {0, 1},
                                TextClassificationModel::SELECTION_IS_EMAIL));

  EXPECT_EQ(model->ClassifyText("x", {0, 1}, 0),
            model->ClassifyText("x", {0, 1},
                                TextClassificationModel::SELECTION_IS_URL));
}

TEST(TextClassificationModelTest, PhoneFiltering) {
  const std::string model_path = GetModelPath();
  int fd = open(model_path.c_str(), O_RDONLY);
  std::unique_ptr<TestingTextClassificationModel> model(
      new TestingTextClassificationModel(fd));
  close(fd);

  EXPECT_EQ("phone", FindBestResult(model->ClassifyText("phone: (123) 456 789",
                                                        {7, 20}, 0)));
  EXPECT_EQ("phone", FindBestResult(model->ClassifyText(
                         "phone: (123) 456 789,0001112", {7, 25}, 0)));
  EXPECT_EQ("other", FindBestResult(model->ClassifyText(
                         "phone: (123) 456 789,0001112", {7, 28}, 0)));
}

}  // namespace
}  // namespace libtextclassifier
