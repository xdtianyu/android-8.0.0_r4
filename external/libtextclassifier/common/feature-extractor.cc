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

#include "common/feature-extractor.h"

#include "common/feature-types.h"
#include "common/fml-parser.h"
#include "util/base/integral_types.h"
#include "util/base/logging.h"
#include "util/gtl/stl_util.h"
#include "util/strings/numbers.h"

namespace libtextclassifier {
namespace nlp_core {

constexpr FeatureValue GenericFeatureFunction::kNone;

GenericFeatureExtractor::GenericFeatureExtractor() {}

GenericFeatureExtractor::~GenericFeatureExtractor() {}

bool GenericFeatureExtractor::Parse(const std::string &source) {
  // Parse feature specification into descriptor.
  FMLParser parser;
  if (!parser.Parse(source, mutable_descriptor())) return false;

  // Initialize feature extractor from descriptor.
  if (!InitializeFeatureFunctions()) return false;
  return true;
}

bool GenericFeatureExtractor::InitializeFeatureTypes() {
  // Register all feature types.
  GetFeatureTypes(&feature_types_);
  for (size_t i = 0; i < feature_types_.size(); ++i) {
    FeatureType *ft = feature_types_[i];
    ft->set_base(i);

    // Check for feature space overflow.
    double domain_size = ft->GetDomainSize();
    if (domain_size < 0) {
      TC_LOG(ERROR) << "Illegal domain size for feature " << ft->name() << ": "
                    << domain_size;
      return false;
    }
  }
  return true;
}

FeatureValue GenericFeatureExtractor::GetDomainSize() const {
  // Domain size of the set of features is equal to:
  //   [largest domain size of any feature types] * [number of feature types]
  FeatureValue max_feature_type_dsize = 0;
  for (size_t i = 0; i < feature_types_.size(); ++i) {
    FeatureType *ft = feature_types_[i];
    const FeatureValue feature_type_dsize = ft->GetDomainSize();
    if (feature_type_dsize > max_feature_type_dsize) {
      max_feature_type_dsize = feature_type_dsize;
    }
  }

  return max_feature_type_dsize * feature_types_.size();
}

std::string GenericFeatureFunction::GetParameter(
    const std::string &name) const {
  // Find named parameter in feature descriptor.
  for (int i = 0; i < descriptor_->parameter_size(); ++i) {
    if (name == descriptor_->parameter(i).name()) {
      return descriptor_->parameter(i).value();
    }
  }
  return "";
}

GenericFeatureFunction::GenericFeatureFunction() {}

GenericFeatureFunction::~GenericFeatureFunction() { delete feature_type_; }

int GenericFeatureFunction::GetIntParameter(const std::string &name,
                                            int default_value) const {
  int32 parsed_value = default_value;
  std::string value = GetParameter(name);
  if (!value.empty()) {
    if (!ParseInt32(value.c_str(), &parsed_value)) {
      // A parameter value has been specified, but it can't be parsed as an int.
      // We don't crash: instead, we long an error and return the default value.
      TC_LOG(ERROR) << "Value of param " << name << " is not an int: " << value;
    }
  }
  return parsed_value;
}

bool GenericFeatureFunction::GetBoolParameter(const std::string &name,
                                              bool default_value) const {
  std::string value = GetParameter(name);
  if (value.empty()) return default_value;
  if (value == "true") return true;
  if (value == "false") return false;
  TC_LOG(ERROR) << "Illegal value '" << value << "' for bool parameter '"
                << name << "'"
                << " will assume default " << default_value;
  return default_value;
}

void GenericFeatureFunction::GetFeatureTypes(
    std::vector<FeatureType *> *types) const {
  if (feature_type_ != nullptr) types->push_back(feature_type_);
}

FeatureType *GenericFeatureFunction::GetFeatureType() const {
  // If a single feature type has been registered return it.
  if (feature_type_ != nullptr) return feature_type_;

  // Get feature types for function.
  std::vector<FeatureType *> types;
  GetFeatureTypes(&types);

  // If there is exactly one feature type return this, else return null.
  if (types.size() == 1) return types[0];
  return nullptr;
}

std::string GenericFeatureFunction::name() const {
  std::string output;
  if (descriptor_->name().empty()) {
    if (!prefix_.empty()) {
      output.append(prefix_);
      output.append(".");
    }
    ToFML(*descriptor_, &output);
  } else {
    output = descriptor_->name();
  }
  return output;
}

}  // namespace nlp_core
}  // namespace libtextclassifier
