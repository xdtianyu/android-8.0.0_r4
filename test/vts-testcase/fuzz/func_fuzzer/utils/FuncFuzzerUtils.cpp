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

#include "FuncFuzzerUtils.h"
#include <getopt.h>

namespace android {
namespace vts {

static void usage() {
  fprintf(
      stdout,
      "Usage:\n"
      "\n"
      "./<fuzzer> <vts flags> -- <libfuzzer flags>\n"
      "\n"
      "VTS flags (strictly in form --flag=value):\n"
      "\n"
      " vts_target_func \tName of function to be fuzzed.\n"
      "\n"
      "libfuzzer flags (strictly in form -flag=value):\n"
      " Use -help=1 to see libfuzzer flags\n"
      "Example:\n"
      "./<fuzzer_name> --vts_target_func=\"foo\" -- -max_len=128 -runs=100\n"
      "\n");
}

static struct option long_options[] = {
    {"help", no_argument, 0, 'h'},
    {"vts_target_function", required_argument, 0, 't'}
};


FuncFuzzerParams ExtractFuncFuzzerParams(int argc, char **argv) {
  FuncFuzzerParams params;
  int opt = 0;
  int index = 0;
  while ((opt = getopt_long_only(argc, argv, "", long_options, &index)) != -1) {
    switch (opt) {
      case 'h':
        usage();
        exit(0);
      case 't':
        params.target_func_ = optarg;
        break;
      default:
        // Ignore. This option will be handled by libfuzzer.
        break;
    }
  }
  return params;
}

}  // namespace vts
}  // namespace android

