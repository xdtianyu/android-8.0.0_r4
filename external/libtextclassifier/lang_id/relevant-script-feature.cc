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

#include "lang_id/relevant-script-feature.h"

#include <string>

#include "common/feature-extractor.h"
#include "common/feature-types.h"
#include "common/task-context.h"
#include "common/workspace.h"
#include "lang_id/script-detector.h"
#include "util/base/logging.h"
#include "util/strings/utf8.h"

namespace libtextclassifier {
namespace nlp_core {
namespace lang_id {

bool RelevantScriptFeature::Setup(TaskContext *context) { return true; }

bool RelevantScriptFeature::Init(TaskContext *context) {
  set_feature_type(new NumericFeatureType(name(), kNumRelevantScripts));
  return true;
}

void RelevantScriptFeature::Evaluate(const WorkspaceSet &workspaces,
                                     const LightSentence &sentence,
                                     FeatureVector *result) const {
  // We expect kNumRelevantScripts to be small, so we stack-allocate the array
  // of counts.  Still, if that changes, we want to find out.
  static_assert(
      kNumRelevantScripts < 25,
      "switch counts to vector<int>: too big for stack-allocated int[]");

  // counts[s] is the number of characters with script s.
  // Note: {} "value-initializes" the array to zero.
  int counts[kNumRelevantScripts]{};
  int total_count = 0;
  for (int i = 0; i < sentence.num_words(); ++i) {
    const std::string &word = sentence.word(i);
    const char *const word_end = word.data() + word.size();
    const char *curr = word.data();

    // Skip over token start '^'.
    TC_DCHECK_EQ(*curr, '^');
    curr += GetNumBytesForNonZeroUTF8Char(curr);
    while (true) {
      const int num_bytes = GetNumBytesForNonZeroUTF8Char(curr);
      Script script = GetScript(curr, num_bytes);

      // We do this update and the if (...) break below *before* incrementing
      // counts[script] in order to skip the token end '$'.
      curr += num_bytes;
      if (curr >= word_end) {
        TC_DCHECK_EQ(*(curr - num_bytes), '$');
        break;
      }
      TC_DCHECK_GE(script, 0);
      TC_DCHECK_LT(script, kNumRelevantScripts);
      counts[script]++;
      total_count++;
    }
  }

  for (int script_id = 0; script_id < kNumRelevantScripts; ++script_id) {
    int count = counts[script_id];
    if (count > 0) {
      const float weight = static_cast<float>(count) / total_count;
      FloatFeatureValue value(script_id, weight);
      result->add(feature_type(), value.discrete_value);
    }
  }
}

}  // namespace lang_id
}  // namespace nlp_core
}  // namespace libtextclassifier
