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

#ifndef LIBTEXTCLASSIFIER_LANG_ID_LANG_ID_BRAIN_INTERFACE_H_
#define LIBTEXTCLASSIFIER_LANG_ID_LANG_ID_BRAIN_INTERFACE_H_

#include <string>
#include <vector>

#include "common/embedding-feature-extractor.h"
#include "common/feature-extractor.h"
#include "common/task-context.h"
#include "common/workspace.h"
#include "lang_id/light-sentence-features.h"
#include "lang_id/light-sentence.h"
#include "util/base/macros.h"

namespace libtextclassifier {
namespace nlp_core {
namespace lang_id {

// Specialization of EmbeddingFeatureExtractor that extracts from LightSentence.
class LangIdEmbeddingFeatureExtractor
    : public EmbeddingFeatureExtractor<LightSentenceExtractor, LightSentence> {
 public:
  LangIdEmbeddingFeatureExtractor() {}
  const std::string ArgPrefix() const override { return "language_identifier"; }

  TC_DISALLOW_COPY_AND_ASSIGN(LangIdEmbeddingFeatureExtractor);
};

// Handles sentence -> numeric_features and numeric_prediction -> language
// conversions.
class LangIdBrainInterface {
 public:
  LangIdBrainInterface() {}

  // Initializes resources and parameters.
  bool Init(TaskContext *context) {
    if (!feature_extractor_.Init(context)) {
      return false;
    }
    feature_extractor_.RequestWorkspaces(&workspace_registry_);
    return true;
  }

  // Extract features from sentence.  On return, FeatureVector features[i]
  // contains the features for the embedding space #i.
  void GetFeatures(LightSentence *sentence,
                   std::vector<FeatureVector> *features) const {
    WorkspaceSet workspace;
    workspace.Reset(workspace_registry_);
    feature_extractor_.Preprocess(&workspace, sentence);
    return feature_extractor_.ExtractFeatures(workspace, *sentence, features);
  }

  int NumEmbeddings() const {
    return feature_extractor_.NumEmbeddings();
  }

 private:
  // Typed feature extractor for embeddings.
  LangIdEmbeddingFeatureExtractor feature_extractor_;

  // The registry of shared workspaces in the feature extractor.
  WorkspaceRegistry workspace_registry_;

  TC_DISALLOW_COPY_AND_ASSIGN(LangIdBrainInterface);
};

}  // namespace lang_id
}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_LANG_ID_LANG_ID_BRAIN_INTERFACE_H_
