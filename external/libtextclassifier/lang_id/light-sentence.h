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

#ifndef LIBTEXTCLASSIFIER_LANG_ID_LIGHT_SENTENCE_H_
#define LIBTEXTCLASSIFIER_LANG_ID_LIGHT_SENTENCE_H_

#include <string>
#include <vector>

#include "util/base/logging.h"
#include "util/base/macros.h"

namespace libtextclassifier {
namespace nlp_core {
namespace lang_id {

// Simplified replacement for the Sentence proto, for internal use in the
// language identification code.
//
// In this simplified form, a sentence is a vector of words, each word being a
// string.
class LightSentence {
 public:
  LightSentence() {}

  // Adds a new word after all existing ones, and returns a pointer to it.  The
  // new word is initialized to the empty string.
  std::string *add_word() {
    words_.emplace_back();
    return &(words_.back());
  }

  // Returns number of words from this LightSentence.
  int num_words() const { return words_.size(); }

  // Returns the ith word from this LightSentence.  Note: undefined behavior if
  // i is out of bounds.
  const std::string &word(int i) const {
    TC_DCHECK((i >= 0) && (i < num_words()));
    return words_[i];
  }

 private:
  std::vector<std::string> words_;

  TC_DISALLOW_COPY_AND_ASSIGN(LightSentence);
};

}  // namespace lang_id
}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_LANG_ID_LIGHT_SENTENCE_H_
