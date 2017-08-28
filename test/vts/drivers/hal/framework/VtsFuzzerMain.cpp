/*
 * Copyright 2016 The Android Open Source Project
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

/*
 * Example usage (for angler 64-bit devices):
 *  $ fuzzer --class=hal_conventional --type=light --version=1.0
 * /system/lib64/hw/lights.angler.so
 *  $ fuzzer --class=hal_conventional --type=gps --version=1.0
 * /system/lib64/hw/gps.msm8994.so
 *
 *  $ LD_LIBRARY_PATH=/data/local/tmp/64 ./fuzzer64 --class=hal --type=light \
 *    --version=1.0 --spec_dir=/data/local/tmp/spec \
 *    /data/local/tmp/64/hal/lights.vts.so
 *
 * Example usage (for GCE virtual devices):
 *  $ fuzzer --class=hal_conventional --type=light --version=1.0
 * /system/lib/hw/lights.gce_x86.so
 *  $ fuzzer --class=hal_conventional --type=gps --version=1.0
 * /system/lib/hw/gps.gce_x86.so
 */

#include <getopt.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <algorithm>
#include <iostream>
#include <string>

#include "binder/VtsFuzzerBinderService.h"
#include "specification_parser/InterfaceSpecificationParser.h"
#include "specification_parser/SpecificationBuilder.h"
#include "replayer/VtsHidlHalReplayer.h"

#include "BinderServer.h"
#include "SocketServer.h"

using namespace std;
using namespace android;

#define INTERFACE_SPEC_LIB_FILENAME "libvts_interfacespecification.so"
#define PASSED_MARKER "[  PASSED  ]"

// the default epoch count where an epoch is the time for a fuzz test run
// (e.g., a function call).
static const int kDefaultEpochCount = 100;

// Dumps usage on stderr.
static void usage() {
  // TODO(zhuoyao): update the usage message.
  fprintf(
      stderr,
      "Usage: fuzzer [options] <target HAL file path>\n"
      "\n"
      "Android fuzzer v0.1.  To fuzz Android system.\n"
      "\n"
      "Options:\n"
      "--help\n"
      "    Show this message.\n"
      "\n"
      "Recording continues until Ctrl-C is hit or the time limit is reached.\n"
      "\n");
}

// Parses command args and kicks things off.
int main(int argc, char* const argv[]) {
  static const struct option longOptions[] = {
      {"help", no_argument, NULL, 'h'},
      {"class", required_argument, NULL, 'c'},
      {"type", required_argument, NULL, 't'},
      {"version", required_argument, NULL, 'v'},
      {"epoch_count", required_argument, NULL, 'e'},
      {"spec_dir", required_argument, NULL, 's'},
      {"target_package", optional_argument, NULL, 'k'},
      {"target_component_name", optional_argument, NULL, 'n'},
#ifndef VTS_AGENT_DRIVER_COMM_BINDER  // socket
      {"server_socket_path", optional_argument, NULL, 'f'},
#else  // binder
      {"service_name", required_argument, NULL, 'n'},
#endif
      {"server", optional_argument, NULL, 'd'},
      {"callback_socket_name", optional_argument, NULL, 'p'},
      // TODO(zhuoyao):make mode a required_argument to support different
      // execution mode. e.g.: fuzzer/driver/replayer.
      {"mode", optional_argument, NULL, 'm'},
      {"trace_path", optional_argument, NULL, 'r'},
      {"spec_path", optional_argument, NULL, 'a'},
      {"hal_service_name", optional_argument, NULL, 'j'},
      {NULL, 0, NULL, 0}};
  int target_class;
  int target_type;
  float target_version = 1.0;
  int epoch_count = kDefaultEpochCount;
  string spec_dir_path(DEFAULT_SPEC_DIR_PATH);
  bool server = false;
#ifndef VTS_AGENT_DRIVER_COMM_BINDER  // socket
  string server_socket_path;
#else  // binder
  string service_name(VTS_FUZZER_BINDER_SERVICE_NAME);
#endif
  string target_package;
  string target_component_name;
  string callback_socket_name;
  string mode;
  string trace_path;
  string spec_path;
  string hal_service_name = "default";

  while (true) {
    int optionIndex = 0;
    int ic = getopt_long(argc, argv, "", longOptions, &optionIndex);
    if (ic == -1) {
      break;
    }

    switch (ic) {
      case 'h':
        usage();
        return 0;
      case 'c': {
        string target_class_str = string(optarg);
        transform(target_class_str.begin(), target_class_str.end(),
                  target_class_str.begin(), ::tolower);
        if (!strcmp(target_class_str.c_str(), "hal_conventional")) {
          target_class = vts::HAL_CONVENTIONAL;
        } else if (!strcmp(target_class_str.c_str(), "hal_hidl")) {
          target_class = vts::HAL_HIDL;
        } else {
          target_class = 0;
        }
        break;
      }
      case 't': {
        string target_type_str = string(optarg);
        transform(target_type_str.begin(), target_type_str.end(),
                  target_type_str.begin(), ::tolower);
        if (!strcmp(target_type_str.c_str(), "camera")) {
          target_type = vts::CAMERA;
        } else if (!strcmp(target_type_str.c_str(), "gps")) {
          target_type = vts::GPS;
        } else if (!strcmp(target_type_str.c_str(), "audio")) {
          target_type = vts::AUDIO;
        } else if (!strcmp(target_type_str.c_str(), "light")) {
          target_type = vts::LIGHT;
        } else {
          target_type = 0;
        }
        break;
      }
      case 'v':
        target_version = atof(optarg);
        break;
      case 'p':
        callback_socket_name = string(optarg);
        break;
      case 'e':
        epoch_count = atoi(optarg);
        if (epoch_count <= 0) {
          fprintf(stderr, "epoch_count must be > 0");
          return 2;
        }
        break;
      case 's':
        spec_dir_path = string(optarg);
        break;
#ifndef VTS_AGENT_DRIVER_COMM_BINDER  // socket
      case 'f':
        server_socket_path = string(optarg);
        break;
#else  // binder
      case 'n':
        service_name = string(optarg);
        break;
#endif
      case 'd':
        server = true;
        break;
      case 'k':
        target_package = string(optarg);
        break;
      case 'n':
        target_component_name = string(optarg);
        break;
      case 'm':
        mode = string(optarg);
        break;
      case 'r':
        trace_path = string(optarg);
        break;
      case 'a':
        spec_path = string(optarg);
        break;
      case 'j':
        hal_service_name = string(optarg);
        break;
      default:
        if (ic != '?') {
          fprintf(stderr, "getopt_long returned unexpected value 0x%x\n", ic);
        }
        return 2;
    }
  }

  android::vts::SpecificationBuilder spec_builder(spec_dir_path, epoch_count,
                                                  callback_socket_name);
  if (!server) {
    if (optind != argc - 1) {
      fprintf(stderr, "Must specify output file (see --help).\n");
      return 2;
    }
    bool success;
    if (mode == "replay") {
      android::vts::VtsHidlHalReplayer replayer(spec_path,
                                                callback_socket_name);
      success = replayer.ReplayTrace(argv[optind], trace_path,
                                     hal_service_name);
    } else {
      success = spec_builder.Process(argv[optind],INTERFACE_SPEC_LIB_FILENAME,
                                     target_class, target_type, target_version,
                                     target_package.c_str(),
                                     target_component_name.c_str());
    }
    cout << "Result: " << success << endl;
    if (success) {
      cout << endl << PASSED_MARKER << endl;
    }
  } else {
#ifndef VTS_AGENT_DRIVER_COMM_BINDER  // socket
    android::vts::StartSocketServer(server_socket_path, spec_builder,
                                    INTERFACE_SPEC_LIB_FILENAME);
#else  // binder
    android::vts::StartBinderServer(service_name, spec_builder,
                                    INTERFACE_SPEC_LIB_FILENAME);
#endif
  }
  return 0;
}
