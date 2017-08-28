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

#include "lang_id/lang-id.h"

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "base.h"
#include "util/base/logging.h"
#include "gtest/gtest.h"

namespace libtextclassifier {
namespace nlp_core {
namespace lang_id {

namespace {

std::string GetModelPath() {
  return TEST_DATA_DIR "langid.model";
}

// Creates a LangId with default model.  Passes ownership to
// the caller.
LangId *CreateLanguageDetector() { return new LangId(GetModelPath()); }

}  // namespace

TEST(LangIdTest, Normal) {
  std::unique_ptr<LangId> lang_id(CreateLanguageDetector());

  EXPECT_EQ("en", lang_id->FindLanguage("This text is written in English."));
  EXPECT_EQ("en",
            lang_id->FindLanguage("This text   is written in   English.  "));
  EXPECT_EQ("en",
            lang_id->FindLanguage("  This text is written in English.  "));
  EXPECT_EQ("fr", lang_id->FindLanguage("Vive la France!  Vive la France!"));
  EXPECT_EQ("ro", lang_id->FindLanguage("Sunt foarte foarte foarte fericit!"));
}

// Test that for very small queries, we return the default language and a low
// confidence score.
TEST(LangIdTest, SuperSmallQueries) {
  std::unique_ptr<LangId> lang_id(CreateLanguageDetector());

  // Use a default language different from any real language: to be sure the
  // result is the default language, not a language that happens to be the
  // default language.
  const std::string kDefaultLanguage = "dflt-lng";
  lang_id->SetDefaultLanguage(kDefaultLanguage);

  // Test the simple FindLanguage() method: that method returns a single
  // language.
  EXPECT_EQ(kDefaultLanguage, lang_id->FindLanguage("y"));
  EXPECT_EQ(kDefaultLanguage, lang_id->FindLanguage("j"));
  EXPECT_EQ(kDefaultLanguage, lang_id->FindLanguage("l"));
  EXPECT_EQ(kDefaultLanguage, lang_id->FindLanguage("w"));
  EXPECT_EQ(kDefaultLanguage, lang_id->FindLanguage("z"));
  EXPECT_EQ(kDefaultLanguage, lang_id->FindLanguage("zulu"));

  // Test the more complex FindLanguages() method: that method returns a vector
  // of (language, confidence_score) pairs.
  std::vector<std::pair<std::string, float>> languages;
  languages = lang_id->FindLanguages("y");
  EXPECT_EQ(1, languages.size());
  EXPECT_EQ(kDefaultLanguage, languages[0].first);
  EXPECT_GT(0.01f, languages[0].second);

  languages = lang_id->FindLanguages("Todoist");
  EXPECT_EQ(1, languages.size());
  EXPECT_EQ(kDefaultLanguage, languages[0].first);
  EXPECT_GT(0.01f, languages[0].second);

  // A few tests with a default language that is a real language code.
  const std::string kJapanese = "ja";
  lang_id->SetDefaultLanguage(kJapanese);
  EXPECT_EQ(kJapanese, lang_id->FindLanguage("y"));
  EXPECT_EQ(kJapanese, lang_id->FindLanguage("j"));
  EXPECT_EQ(kJapanese, lang_id->FindLanguage("l"));
  languages = lang_id->FindLanguages("y");
  EXPECT_EQ(1, languages.size());
  EXPECT_EQ(kJapanese, languages[0].first);
  EXPECT_GT(0.01f, languages[0].second);

  // Make sure the min text size limit is applied to the number of real
  // characters (e.g., without spaces and punctuation chars, which don't
  // influence language identification).
  const std::string kWhitespaces = "   \t   \n   \t\t\t\n    \t";
  const std::string kPunctuation = "... ?!!--- -%%^...-";
  std::string still_small_string = kWhitespaces + "y" + kWhitespaces +
                                   kPunctuation + kWhitespaces + kPunctuation +
                                   kPunctuation;
  EXPECT_LE(100, still_small_string.size());
  lang_id->SetDefaultLanguage(kDefaultLanguage);
  EXPECT_EQ(kDefaultLanguage, lang_id->FindLanguage(still_small_string));
  languages = lang_id->FindLanguages(still_small_string);
  EXPECT_EQ(1, languages.size());
  EXPECT_EQ(kDefaultLanguage, languages[0].first);
  EXPECT_GT(0.01f, languages[0].second);
}

namespace {
void CheckPredictionForGibberishStrings(const std::string &default_language) {
  static const char *const kGibberish[] = {
    "",
    " ",
    "       ",
    "  ___  ",
    "123 456 789",
    "><> (-_-) <><",
    nullptr,
  };

  std::unique_ptr<LangId> lang_id(CreateLanguageDetector());
  TC_LOG(INFO) << "Default language: " << default_language;
  lang_id->SetDefaultLanguage(default_language);
  for (int i = 0; true; ++i) {
    const char *gibberish = kGibberish[i];
    if (gibberish == nullptr) {
      break;
    }
    const std::string predicted_language = lang_id->FindLanguage(gibberish);
    TC_LOG(INFO) << "Predicted " << predicted_language << " for \"" << gibberish
                 << "\"";
    EXPECT_EQ(default_language, predicted_language);
  }
}
}  // namespace

TEST(LangIdTest, CornerCases) {
  CheckPredictionForGibberishStrings("en");
  CheckPredictionForGibberishStrings("ro");
  CheckPredictionForGibberishStrings("fr");
}

}  // namespace lang_id
}  // namespace nlp_core
}  // namespace libtextclassifier
