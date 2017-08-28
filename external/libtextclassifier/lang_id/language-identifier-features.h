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

#ifndef LIBTEXTCLASSIFIER_LANG_ID_LANGUAGE_IDENTIFIER_FEATURES_H_
#define LIBTEXTCLASSIFIER_LANG_ID_LANGUAGE_IDENTIFIER_FEATURES_H_

#include <string>

#include "common/feature-extractor.h"
#include "common/task-context.h"
#include "common/workspace.h"
#include "lang_id/light-sentence-features.h"
#include "lang_id/light-sentence.h"

namespace libtextclassifier {
namespace nlp_core {
namespace lang_id {

// Class for computing continuous char ngram features.
//
// Feature function descriptor parameters:
//   id_dim(int, 10000):
//     The integer id of each char ngram is computed as follows:
//     Hash32WithDefaultSeed(char ngram) % id_dim.
//   size(int, 3):
//     Only ngrams of this size will be extracted.
//
// NOTE: this class is not thread-safe.  TODO(salcianu): make it thread-safe.
class ContinuousBagOfNgramsFunction : public LightSentenceFeature {
 public:
  bool Setup(TaskContext *context) override;
  bool Init(TaskContext *context) override;

  // Appends the features computed from the sentence to the feature vector.
  void Evaluate(const WorkspaceSet &workspaces, const LightSentence &sentence,
                FeatureVector *result) const override;

  TC_DEFINE_REGISTRATION_METHOD("continuous-bag-of-ngrams",
                                ContinuousBagOfNgramsFunction);

 private:
  // Auxiliary for Evaluate().  Fills counts_ and non_zero_count_indices_ (see
  // below), and returns the total ngram count.
  int ComputeNgramCounts(const LightSentence &sentence) const;

  // counts_[i] is the count of all ngrams with id i.  Work data for Evaluate().
  // NOTE: we declare this vector as a field, such that its underlying capacity
  // stays allocated in between calls to Evaluate().
  mutable std::vector<int> counts_;

  // Indices of non-zero elements of counts_.  See comments for counts_.
  mutable std::vector<int> non_zero_count_indices_;

  // The integer id of each char ngram is computed as follows:
  // Hash32WithDefaultSeed(char_ngram) % ngram_id_dimension_.
  int ngram_id_dimension_;

  // Only ngrams of size ngram_size_ will be extracted.
  int ngram_size_;
};

}  // namespace lang_id
}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_LANG_ID_LANGUAGE_IDENTIFIER_FEATURES_H_
