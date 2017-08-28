/*
 * Copyright 2017 The Android Open Source Project
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

#ifndef __VTS_FUZZ_FUNC_FUZZER_UTILS_H__
#define __VTS_FUZZ_FUNC_FUZZER_UTILS_H__

#include <string>

namespace android {
namespace vts {

// Additional non-libfuzzer parameters passed to the fuzzer.
struct FuncFuzzerParams {
  // Name of function to fuzz.
  std::string target_func_;
};

// Parses command-line flags to create a FuncFuzzerParams instance.
FuncFuzzerParams ExtractFuncFuzzerParams(int argc, char **argv);

}  // namespace vts
}  // namespace android

#endif  // __VTS_FUZZ_FUNC_FUZZER_UTILS_H__
