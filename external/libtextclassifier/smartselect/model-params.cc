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

#include "smartselect/model-params.h"

#include "common/memory_image/memory-image-reader.h"

namespace libtextclassifier {

using nlp_core::EmbeddingNetworkProto;
using nlp_core::MemoryImageReader;

ModelParams* ModelParamsBuilder(
    const void* start, uint64 num_bytes,
    std::shared_ptr<EmbeddingParams> external_embedding_params) {
  MemoryImageReader<EmbeddingNetworkProto> reader(start, num_bytes);

  ModelOptions model_options;
  auto model_options_extension_id = model_options_in_embedding_network_proto;
  if (reader.trimmed_proto().HasExtension(model_options_extension_id)) {
    model_options =
        reader.trimmed_proto().GetExtension(model_options_extension_id);
  }

  FeatureProcessorOptions feature_processor_options;
  auto feature_processor_extension_id =
      feature_processor_options_in_embedding_network_proto;
  if (reader.trimmed_proto().HasExtension(feature_processor_extension_id)) {
    feature_processor_options =
        reader.trimmed_proto().GetExtension(feature_processor_extension_id);

    // If no tokenization codepoint config is present, tokenize on space.
    if (feature_processor_options.tokenization_codepoint_config_size() == 0) {
      TokenizationCodepointRange* config;
      // New line character.
      config = feature_processor_options.add_tokenization_codepoint_config();
      config->set_start(10);
      config->set_end(11);
      config->set_role(TokenizationCodepointRange::WHITESPACE_SEPARATOR);

      // Space character.
      config = feature_processor_options.add_tokenization_codepoint_config();
      config->set_start(32);
      config->set_end(33);
      config->set_role(TokenizationCodepointRange::WHITESPACE_SEPARATOR);
    }
  } else {
    return nullptr;
  }

  SelectionModelOptions selection_options;
  auto selection_options_extension_id =
      selection_model_options_in_embedding_network_proto;
  if (reader.trimmed_proto().HasExtension(selection_options_extension_id)) {
    selection_options =
        reader.trimmed_proto().GetExtension(selection_options_extension_id);
  } else {
    // Default values when SelectionModelOptions is not present.
    for (const auto codepoint_pair : std::vector<std::pair<int, int>>(
             {{33, 35},       {37, 39},       {42, 42},       {44, 47},
              {58, 59},       {63, 64},       {91, 93},       {95, 95},
              {123, 123},     {125, 125},     {161, 161},     {171, 171},
              {183, 183},     {187, 187},     {191, 191},     {894, 894},
              {903, 903},     {1370, 1375},   {1417, 1418},   {1470, 1470},
              {1472, 1472},   {1475, 1475},   {1478, 1478},   {1523, 1524},
              {1548, 1549},   {1563, 1563},   {1566, 1567},   {1642, 1645},
              {1748, 1748},   {1792, 1805},   {2404, 2405},   {2416, 2416},
              {3572, 3572},   {3663, 3663},   {3674, 3675},   {3844, 3858},
              {3898, 3901},   {3973, 3973},   {4048, 4049},   {4170, 4175},
              {4347, 4347},   {4961, 4968},   {5741, 5742},   {5787, 5788},
              {5867, 5869},   {5941, 5942},   {6100, 6102},   {6104, 6106},
              {6144, 6154},   {6468, 6469},   {6622, 6623},   {6686, 6687},
              {8208, 8231},   {8240, 8259},   {8261, 8273},   {8275, 8286},
              {8317, 8318},   {8333, 8334},   {9001, 9002},   {9140, 9142},
              {10088, 10101}, {10181, 10182}, {10214, 10219}, {10627, 10648},
              {10712, 10715}, {10748, 10749}, {11513, 11516}, {11518, 11519},
              {11776, 11799}, {11804, 11805}, {12289, 12291}, {12296, 12305},
              {12308, 12319}, {12336, 12336}, {12349, 12349}, {12448, 12448},
              {12539, 12539}, {64830, 64831}, {65040, 65049}, {65072, 65106},
              {65108, 65121}, {65123, 65123}, {65128, 65128}, {65130, 65131},
              {65281, 65283}, {65285, 65290}, {65292, 65295}, {65306, 65307},
              {65311, 65312}, {65339, 65341}, {65343, 65343}, {65371, 65371},
              {65373, 65373}, {65375, 65381}, {65792, 65793}, {66463, 66463},
              {68176, 68184}})) {
      for (int i = codepoint_pair.first; i <= codepoint_pair.second; i++) {
        selection_options.add_punctuation_to_strip(i);
      }
      selection_options.set_strip_punctuation(true);
      selection_options.set_enforce_symmetry(true);
      selection_options.set_symmetry_context_size(
          feature_processor_options.context_size() * 2);
    }
  }

  SharingModelOptions sharing_options;
  auto sharing_options_extension_id =
      sharing_model_options_in_embedding_network_proto;
  if (reader.trimmed_proto().HasExtension(sharing_options_extension_id)) {
    sharing_options =
        reader.trimmed_proto().GetExtension(sharing_options_extension_id);
  } else {
    // Default values when SharingModelOptions is not present.
    sharing_options.set_always_accept_url_hint(true);
    sharing_options.set_always_accept_email_hint(true);
  }

  if (!model_options.use_shared_embeddings()) {
    std::shared_ptr<EmbeddingParams> embedding_params(new EmbeddingParams(
        start, num_bytes, feature_processor_options.context_size()));
    return new ModelParams(start, num_bytes, embedding_params,
                           selection_options, sharing_options,
                           feature_processor_options);
  } else {
    return new ModelParams(
        start, num_bytes, std::move(external_embedding_params),
        selection_options, sharing_options, feature_processor_options);
  }
}

}  // namespace libtextclassifier
