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

#ifndef LIBTEXTCLASSIFIER_LANG_ID_RELEVANT_SCRIPT_FEATURE_H_
#define LIBTEXTCLASSIFIER_LANG_ID_RELEVANT_SCRIPT_FEATURE_H_

#include "common/feature-extractor.h"
#include "common/task-context.h"
#include "common/workspace.h"
#include "lang_id/light-sentence-features.h"
#include "lang_id/light-sentence.h"

namespace libtextclassifier {
namespace nlp_core {
namespace lang_id {

// Given a sentence, generates one FloatFeatureValue for each "relevant" Unicode
// script (see below): each such feature indicates the script and the ratio of
// UTF8 characters in that script, in the given sentence.
//
// What is a relevant script?  Recognizing all 100+ Unicode scripts would
// require too much code size and runtime.  Instead, we focus only on a few
// scripts that communicate a lot of language information: e.g., the use of
// Hiragana characters almost always indicates Japanese, so Hiragana is a
// "relevant" script for us.  The Latin script is used by dozens of language, so
// Latin is not relevant in this context.
class RelevantScriptFeature : public LightSentenceFeature {
 public:
  // Idiomatic SAFT Setup() and Init().
  bool Setup(TaskContext *context) override;
  bool Init(TaskContext *context) override;

  // Appends the features computed from the sentence to the feature vector.
  void Evaluate(const WorkspaceSet &workspaces, const LightSentence &sentence,
                FeatureVector *result) const override;

  TC_DEFINE_REGISTRATION_METHOD("continuous-bag-of-relevant-scripts",
                                RelevantScriptFeature);
};

}  // namespace lang_id
}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_LANG_ID_RELEVANT_SCRIPT_FEATURE_H_
