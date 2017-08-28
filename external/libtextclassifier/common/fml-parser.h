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

// Feature modeling language (fml) parser.
//
// BNF grammar for fml:
//
// <feature model> ::= { <feature extractor> }
//
// <feature extractor> ::= <extractor spec> |
//                         <extractor spec> '.' <feature extractor> |
//                         <extractor spec> '{' { <feature extractor> } '}'
//
// <extractor spec> ::= <extractor type>
//                      [ '(' <parameter list> ')' ]
//                      [ ':' <extractor name> ]
//
// <parameter list> = ( <parameter> | <argument> ) { ',' <parameter> }
//
// <parameter> ::= <parameter name> '=' <parameter value>
//
// <extractor type> ::= NAME
// <extractor name> ::= NAME | STRING
// <argument> ::= NUMBER
// <parameter name> ::= NAME
// <parameter value> ::= NUMBER | STRING | NAME

#ifndef LIBTEXTCLASSIFIER_COMMON_FML_PARSER_H_
#define LIBTEXTCLASSIFIER_COMMON_FML_PARSER_H_

#include <string>
#include <vector>

#include "common/feature-descriptors.h"
#include "util/base/logging.h"

namespace libtextclassifier {
namespace nlp_core {

class FMLParser {
 public:
  // Parses fml specification into feature extractor descriptor.
  // Returns true on success, false on error (e.g., syntax errors).
  bool Parse(const std::string &source, FeatureExtractorDescriptor *result);

 private:
  // Initializes the parser with the source text.
  // Returns true on success, false on syntax error.
  bool Initialize(const std::string &source);

  // Outputs an error message, with context info, and sets error_ to true.
  void ReportError(const std::string &error_message);

  // Moves to the next input character.
  void Next();

  // Moves to the next input item.  Sets item_text_ and item_type_ accordingly.
  // Returns true on success, false on syntax error.
  bool NextItem();

  // Parses a feature descriptor.
  // Returns true on success, false on syntax error.
  bool ParseFeature(FeatureFunctionDescriptor *result);

  // Parses a parameter specification.
  // Returns true on success, false on syntax error.
  bool ParseParameter(FeatureFunctionDescriptor *result);

  // Returns true if end of source input has been reached.
  bool eos() const { return current_ >= source_.end(); }

  // Returns current character.  Other methods should access the current
  // character through this method (instead of using *current_ directly): this
  // method performs extra safety checks.
  //
  // In case of an unsafe access, returns '\0'.
  char CurrentChar() const {
    if ((current_ >= source_.begin()) && (current_ < source_.end())) {
      return *current_;
    } else {
      TC_LOG(ERROR) << "Unsafe char read";
      return '\0';
    }
  }

  // Item types.
  enum ItemTypes {
    END = 0,
    NAME = -1,
    NUMBER = -2,
    STRING = -3,
  };

  // Source text.
  std::string source_;

  // Current input position.
  std::string::iterator current_;

  // Line number for current input position.
  int line_number_;

  // Start position for current item.
  std::string::iterator item_start_;

  // Start position for current line.
  std::string::iterator line_start_;

  // Line number for current item.
  int item_line_number_;

  // Item type for current item. If this is positive it is interpreted as a
  // character. If it is negative it is interpreted as an item type.
  int item_type_;

  // Text for current item.
  std::string item_text_;
};

// Converts a FeatureFunctionDescriptor into an FML spec (reverse of parsing).
void ToFML(const FeatureFunctionDescriptor &function, std::string *output);

// Like ToFML, but doesn't go into the nested functions.  Instead, it generates
// a string that starts with the name of the feature extraction function and
// next, in-between parentheses, the parameters, separated by comma.
// Intuitively, the constructed string is the prefix of ToFML, before the "{"
// that starts the nested features.
void ToFMLFunction(const FeatureFunctionDescriptor &function,
                   std::string *output);

}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_COMMON_FML_PARSER_H_
