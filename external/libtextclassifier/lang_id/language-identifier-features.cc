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

#include "lang_id/language-identifier-features.h"

#include <utility>
#include <vector>

#include "common/feature-extractor.h"
#include "common/feature-types.h"
#include "common/task-context.h"
#include "util/hash/hash.h"
#include "util/strings/utf8.h"

namespace libtextclassifier {
namespace nlp_core {
namespace lang_id {

bool ContinuousBagOfNgramsFunction::Setup(TaskContext *context) {
  // Parameters in the feature function descriptor.
  ngram_id_dimension_ = GetIntParameter("id_dim", 10000);
  ngram_size_ = GetIntParameter("size", 3);

  counts_.assign(ngram_id_dimension_, 0);
  return true;
}

bool ContinuousBagOfNgramsFunction::Init(TaskContext *context) {
  set_feature_type(new NumericFeatureType(name(), ngram_id_dimension_));
  return true;
}

int ContinuousBagOfNgramsFunction::ComputeNgramCounts(
    const LightSentence &sentence) const {
  // Invariant 1: counts_.size() == ngram_id_dimension_.  Holds at the end of
  // the constructor.  After that, no method changes counts_.size().
  TC_DCHECK_EQ(counts_.size(), ngram_id_dimension_);

  // Invariant 2: the vector non_zero_count_indices_ is empty.  The vector
  // non_zero_count_indices_ is empty at construction time and gets emptied at
  // the end of each call to Evaluate().  Hence, this invariant holds at the
  // beginning of each run of Evaluate(), where the only call to this code takes
  // place.
  TC_DCHECK(non_zero_count_indices_.empty());

  int total_count = 0;

  for (int i = 0; i < sentence.num_words(); ++i) {
    const std::string &word = sentence.word(i);
    const char *const word_end = word.data() + word.size();

    // Set ngram_start at the start of the current token (word).
    const char *ngram_start = word.data();

    // Set ngram_end ngram_size UTF8 characters after ngram_start.  Note: each
    // UTF8 character contains between 1 and 4 bytes.
    const char *ngram_end = ngram_start;
    int num_utf8_chars = 0;
    do {
      ngram_end += GetNumBytesForNonZeroUTF8Char(ngram_end);
      num_utf8_chars++;
    } while ((num_utf8_chars < ngram_size_) && (ngram_end < word_end));

    if (num_utf8_chars < ngram_size_) {
      // Current token is so small, it does not contain a single ngram of
      // ngram_size UTF8 characters.  Not much we can do in this case ...
      continue;
    }

    // At this point, [ngram_start, ngram_end) is the first ngram of ngram_size
    // UTF8 characters from current token.
    while (true) {
      // Compute ngram_id: hash(ngram) % ngram_id_dimension
      int ngram_id =
          (Hash32WithDefaultSeed(ngram_start, ngram_end - ngram_start) %
           ngram_id_dimension_);

      // Use a reference to the actual count, such that we can both test whether
      // the count was 0 and increment it without perfoming two lookups.
      //
      // Due to the way we compute ngram_id, 0 <= ngram_id < ngram_id_dimension.
      // Hence, by Invariant 1 (above), the access counts_[ngram_id] is safe.
      int &ref_to_count_for_ngram = counts_[ngram_id];
      if (ref_to_count_for_ngram == 0) {
        non_zero_count_indices_.push_back(ngram_id);
      }
      ref_to_count_for_ngram++;
      total_count++;
      if (ngram_end >= word_end) {
        break;
      }

      // Advance both ngram_start and ngram_end by one UTF8 character.  This
      // way, the number of UTF8 characters between them remains constant
      // (ngram_size).
      ngram_start += GetNumBytesForNonZeroUTF8Char(ngram_start);
      ngram_end += GetNumBytesForNonZeroUTF8Char(ngram_end);
    }
  }  // end of loop over tokens.

  return total_count;
}

void ContinuousBagOfNgramsFunction::Evaluate(const WorkspaceSet &workspaces,
                                             const LightSentence &sentence,
                                             FeatureVector *result) const {
  // Find the char ngram counts.
  int total_count = ComputeNgramCounts(sentence);

  // Populate the feature vector.
  const float norm = static_cast<float>(total_count);

  for (int ngram_id : non_zero_count_indices_) {
    const float weight = counts_[ngram_id] / norm;
    FloatFeatureValue value(ngram_id, weight);
    result->add(feature_type(), value.discrete_value);

    // Clear up counts_, for the next invocation of Evaluate().
    counts_[ngram_id] = 0;
  }

  // Clear up non_zero_count_indices_, for the next invocation of Evaluate().
  non_zero_count_indices_.clear();
}

}  // namespace lang_id
}  // namespace nlp_core
}  // namespace libtextclassifier
