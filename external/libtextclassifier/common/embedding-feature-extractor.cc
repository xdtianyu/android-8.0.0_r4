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

#include "common/embedding-feature-extractor.h"

#include <stddef.h>

#include <vector>

#include "common/feature-extractor.h"
#include "common/feature-types.h"
#include "common/task-context.h"
#include "util/base/integral_types.h"
#include "util/base/logging.h"
#include "util/strings/numbers.h"
#include "util/strings/split.h"

namespace libtextclassifier {
namespace nlp_core {

bool GenericEmbeddingFeatureExtractor::Init(TaskContext *context) {
  // Don't use version to determine how to get feature FML.
  const std::string features = context->Get(GetParamName("features"), "");
  TC_LOG(INFO) << "Features: " << features;

  const std::string embedding_names =
      context->Get(GetParamName("embedding_names"), "");
  TC_LOG(INFO) << "Embedding names: " << embedding_names;

  const std::string embedding_dims =
      context->Get(GetParamName("embedding_dims"), "");
  TC_LOG(INFO) << "Embedding dims: " << embedding_dims;

  embedding_fml_ = strings::Split(features, ';');
  embedding_names_ = strings::Split(embedding_names, ';');
  for (const std::string &dim : strings::Split(embedding_dims, ';')) {
    int32 parsed_dim = 0;
    if (!ParseInt32(dim.c_str(), &parsed_dim)) {
      TC_LOG(ERROR) << "Unable to parse dim " << dim;
      return false;
    }
    embedding_dims_.push_back(parsed_dim);
  }
  if ((embedding_fml_.size() != embedding_names_.size()) ||
      (embedding_fml_.size() != embedding_dims_.size())) {
    TC_LOG(ERROR) << "Mismatch: #fml specs = " << embedding_fml_.size()
                  << "; #names = " << embedding_names_.size()
                  << "; #dims = " << embedding_dims_.size();
    return false;
  }
  return true;
}

}  // namespace nlp_core
}  // namespace libtextclassifier
