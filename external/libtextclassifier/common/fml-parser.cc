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

#include "common/fml-parser.h"

#include <ctype.h>
#include <string>

#include "util/base/logging.h"
#include "util/strings/numbers.h"

namespace libtextclassifier {
namespace nlp_core {

namespace {
inline bool IsValidCharAtStartOfIdentifier(char c) {
  return isalpha(c) || (c == '_') || (c == '/');
}

// Returns true iff character c can appear inside an identifier.
inline bool IsValidCharInsideIdentifier(char c) {
  return isalnum(c) || (c == '_') || (c == '-') || (c == '/');
}

// Returns true iff character c can appear at the beginning of a number.
inline bool IsValidCharAtStartOfNumber(char c) {
  return isdigit(c) || (c == '+') || (c == '-');
}

// Returns true iff character c can appear inside a number.
inline bool IsValidCharInsideNumber(char c) {
  return isdigit(c) || (c == '.');
}
}  // namespace

bool FMLParser::Initialize(const std::string &source) {
  // Initialize parser state.
  source_ = source;
  current_ = source_.begin();
  item_start_ = line_start_ = current_;
  line_number_ = item_line_number_ = 1;

  // Read first input item.
  return NextItem();
}

void FMLParser::ReportError(const std::string &error_message) {
  const int position = item_start_ - line_start_ + 1;
  const std::string line(line_start_, current_);

  TC_LOG(ERROR) << "Error in feature model, line " << item_line_number_
                << ", position " << position << ": " << error_message
                << "\n    " << line << " <--HERE";
}

void FMLParser::Next() {
  // Move to the next input character. If we are at a line break update line
  // number and line start position.
  if (CurrentChar() == '\n') {
    ++line_number_;
    ++current_;
    line_start_ = current_;
  } else {
    ++current_;
  }
}

bool FMLParser::NextItem() {
  // Skip white space and comments.
  while (!eos()) {
    if (CurrentChar() == '#') {
      // Skip comment.
      while (!eos() && CurrentChar() != '\n') Next();
    } else if (isspace(CurrentChar())) {
      // Skip whitespace.
      while (!eos() && isspace(CurrentChar())) Next();
    } else {
      break;
    }
  }

  // Record start position for next item.
  item_start_ = current_;
  item_line_number_ = line_number_;

  // Check for end of input.
  if (eos()) {
    item_type_ = END;
    return true;
  }

  // Parse number.
  if (IsValidCharAtStartOfNumber(CurrentChar())) {
    std::string::iterator start = current_;
    Next();
    while (!eos() && IsValidCharInsideNumber(CurrentChar())) Next();
    item_text_.assign(start, current_);
    item_type_ = NUMBER;
    return true;
  }

  // Parse std::string.
  if (CurrentChar() == '"') {
    Next();
    std::string::iterator start = current_;
    while (CurrentChar() != '"') {
      if (eos()) {
        ReportError("Unterminated string");
        return false;
      }
      Next();
    }
    item_text_.assign(start, current_);
    item_type_ = STRING;
    Next();
    return true;
  }

  // Parse identifier name.
  if (IsValidCharAtStartOfIdentifier(CurrentChar())) {
    std::string::iterator start = current_;
    while (!eos() && IsValidCharInsideIdentifier(CurrentChar())) {
      Next();
    }
    item_text_.assign(start, current_);
    item_type_ = NAME;
    return true;
  }

  // Single character item.
  item_type_ = CurrentChar();
  Next();
  return true;
}

bool FMLParser::Parse(const std::string &source,
                      FeatureExtractorDescriptor *result) {
  // Initialize parser.
  if (!Initialize(source)) {
    return false;
  }

  while (item_type_ != END) {
    // Current item should be a feature name.
    if (item_type_ != NAME) {
      ReportError("Feature type name expected");
      return false;
    }
    std::string name = item_text_;
    if (!NextItem()) {
      return false;
    }

    // Parse feature.
    FeatureFunctionDescriptor *descriptor = result->add_feature();
    descriptor->set_type(name);
    if (!ParseFeature(descriptor)) {
      return false;
    }
  }

  return true;
}

bool FMLParser::ParseFeature(FeatureFunctionDescriptor *result) {
  // Parse argument and parameters.
  if (item_type_ == '(') {
    if (!NextItem()) return false;
    if (!ParseParameter(result)) return false;
    while (item_type_ == ',') {
      if (!NextItem()) return false;
      if (!ParseParameter(result)) return false;
    }

    if (item_type_ != ')') {
      ReportError(") expected");
      return false;
    }
    if (!NextItem()) return false;
  }

  // Parse feature name.
  if (item_type_ == ':') {
    if (!NextItem()) return false;
    if (item_type_ != NAME && item_type_ != STRING) {
      ReportError("Feature name expected");
      return false;
    }
    std::string name = item_text_;
    if (!NextItem()) return false;

    // Set feature name.
    result->set_name(name);
  }

  // Parse sub-features.
  if (item_type_ == '.') {
    // Parse dotted sub-feature.
    if (!NextItem()) return false;
    if (item_type_ != NAME) {
      ReportError("Feature type name expected");
      return false;
    }
    std::string type = item_text_;
    if (!NextItem()) return false;

    // Parse sub-feature.
    FeatureFunctionDescriptor *subfeature = result->add_feature();
    subfeature->set_type(type);
    if (!ParseFeature(subfeature)) return false;
  } else if (item_type_ == '{') {
    // Parse sub-feature block.
    if (!NextItem()) return false;
    while (item_type_ != '}') {
      if (item_type_ != NAME) {
        ReportError("Feature type name expected");
        return false;
      }
      std::string type = item_text_;
      if (!NextItem()) return false;

      // Parse sub-feature.
      FeatureFunctionDescriptor *subfeature = result->add_feature();
      subfeature->set_type(type);
      if (!ParseFeature(subfeature)) return false;
    }
    if (!NextItem()) return false;
  }
  return true;
}

bool FMLParser::ParseParameter(FeatureFunctionDescriptor *result) {
  if (item_type_ == NUMBER) {
    int32 argument;
    if (!ParseInt32(item_text_.c_str(), &argument)) {
      ReportError("Unable to parse number");
      return false;
    }
    if (!NextItem()) return false;

    // Set default argument for feature.
    result->set_argument(argument);
  } else if (item_type_ == NAME) {
    std::string name = item_text_;
    if (!NextItem()) return false;
    if (item_type_ != '=') {
      ReportError("= expected");
      return false;
    }
    if (!NextItem()) return false;
    if (item_type_ >= END) {
      ReportError("Parameter value expected");
      return false;
    }
    std::string value = item_text_;
    if (!NextItem()) return false;

    // Add parameter to feature.
    Parameter *parameter;
    parameter = result->add_parameter();
    parameter->set_name(name);
    parameter->set_value(value);
  } else {
    ReportError("Syntax error in parameter list");
    return false;
  }
  return true;
}

void ToFMLFunction(const FeatureFunctionDescriptor &function,
                   std::string *output) {
  output->append(function.type());
  if (function.argument() != 0 || function.parameter_size() > 0) {
    output->append("(");
    bool first = true;
    if (function.argument() != 0) {
      output->append(IntToString(function.argument()));
      first = false;
    }
    for (int i = 0; i < function.parameter_size(); ++i) {
      if (!first) output->append(",");
      output->append(function.parameter(i).name());
      output->append("=");
      output->append("\"");
      output->append(function.parameter(i).value());
      output->append("\"");
      first = false;
    }
    output->append(")");
  }
}

void ToFML(const FeatureFunctionDescriptor &function, std::string *output) {
  ToFMLFunction(function, output);
  if (function.feature_size() == 1) {
    output->append(".");
    ToFML(function.feature(0), output);
  } else if (function.feature_size() > 1) {
    output->append(" { ");
    for (int i = 0; i < function.feature_size(); ++i) {
      if (i > 0) output->append(" ");
      ToFML(function.feature(i), output);
    }
    output->append(" } ");
  }
}

void ToFML(const FeatureExtractorDescriptor &extractor, std::string *output) {
  for (int i = 0; i < extractor.feature_size(); ++i) {
    ToFML(extractor.feature(i), output);
    output->append("\n");
  }
}

}  // namespace nlp_core
}  // namespace libtextclassifier
