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

#ifndef LIBTEXTCLASSIFIER_LANG_ID_LANG_ID_H_
#define LIBTEXTCLASSIFIER_LANG_ID_LANG_ID_H_

// Clients who want to perform language identification should use this header.
//
// Note for lang id implementors: keep this header as linght as possible.  E.g.,
// any macro defined here (or in a transitively #included file) is a potential
// name conflict with our clients.

#include <memory>
#include <string>
#include <vector>

#include "util/base/macros.h"

namespace libtextclassifier {
namespace nlp_core {
namespace lang_id {

// Forward-declaration of the class that performs all underlying work.
class LangIdImpl;

// Class for detecting the language of a document.
//
// NOTE: this class is thread-unsafe.
class LangId {
 public:
  // Constructs a LangId object, loading an EmbeddingNetworkProto model from the
  // indicated file.
  //
  // Note: we don't crash if we detect a problem at construction time (e.g.,
  // file doesn't exist, or its content is corrupted).  Instead, we mark the
  // newly-constructed object as invalid; clients can invoke FindLanguage() on
  // an invalid object: nothing crashes, but accuracy will be bad.
  explicit LangId(const std::string &filename);

  // Same as above but uses a file descriptor.
  explicit LangId(int fd);

  // Same as above but uses already mapped memory region
  explicit LangId(const char *ptr, size_t length);

  virtual ~LangId();

  // Sets probability threshold for predictions.  If our likeliest prediction is
  // below this threshold, we report the default language (see
  // SetDefaultLanguage()).  Othewise, we report the likelist language.
  //
  // By default (if this method is not called) we use the probability threshold
  // stored in the model, as the task parameter "reliability_thresh".  If that
  // task parameter is not specified, we use 0.5.  A client can use this method
  // to get a different precision / recall trade-off.  The higher the threshold,
  // the higher the precision and lower the recall rate.
  void SetProbabilityThreshold(float threshold);

  // Sets default language to report if errors prevent running the real
  // inference code or if prediction confidence is too small.
  void SetDefaultLanguage(const std::string &lang);

  // Returns language code for the most likely language that text is written in.
  // Note: if this LangId object is not valid (see
  // is_valid()), this method returns the default language specified via
  // SetDefaultLanguage() or (if that method was never invoked), the empty
  // std::string.
  std::string FindLanguage(const std::string &text) const;

  // Returns a vector of language codes along with the probability for each
  // language.  The result contains at least one element.  The sum of
  // probabilities may be less than 1.0.
  std::vector<std::pair<std::string, float>> FindLanguages(
      const std::string &text) const;

  // Returns true if this object has been correctly initialized and is ready to
  // perform predictions.  For more info, see doc for LangId
  // constructor above.
  bool is_valid() const;

  // Returns version number for the model.
  int version() const;

 private:
  // Returns a vector of probabilities of languages of the text.
  std::vector<float> ScoreLanguages(const std::string &text) const;

  // Pimpl ("pointer to implementation") pattern, to hide all internals from our
  // clients.
  std::unique_ptr<LangIdImpl> pimpl_;

  TC_DISALLOW_COPY_AND_ASSIGN(LangId);
};

}  // namespace lang_id
}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_LANG_ID_LANG_ID_H_
