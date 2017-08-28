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

#include "fuzz_tester/FuzzerBase.h"

#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

#include "test/vts/proto/ComponentSpecificationMessage.pb.h"

#include "component_loader/DllLoader.h"
#include "utils/InterfaceSpecUtil.h"

#include "GcdaParser.h"

using namespace std;
using namespace android;

#define USE_GCOV 1

#if SANCOV
extern "C" {
typedef unsigned long uptr;

#define SANITIZER_INTERFACE_ATTRIBUTE __attribute__((visibility("default")))
SANITIZER_INTERFACE_ATTRIBUTE
void __sanitizer_cov(uint32_t* guard) {
  printf("sancov\n");
  coverage_data.Add(StackTrace::GetPreviousInstructionPc(GET_CALLER_PC()),
                    guard);
}

SANITIZER_INTERFACE_ATTRIBUTE
void* __asan_memset(void* block, int c, uptr size) {
  ASAN_MEMSET_IMPL(nullptr, block, c, size);
  return block;
}

SANITIZER_INTERFACE_ATTRIBUTE
void __asan_register_globals() {}
SANITIZER_INTERFACE_ATTRIBUTE
void __asan_unregister_globals() {}
SANITIZER_INTERFACE_ATTRIBUTE
void __asan_handle_no_return() {}
SANITIZER_INTERFACE_ATTRIBUTE
void __asan_report_load1() {}
SANITIZER_INTERFACE_ATTRIBUTE
void __asan_report_load2() {}
SANITIZER_INTERFACE_ATTRIBUTE
void __asan_report_load4() {}
SANITIZER_INTERFACE_ATTRIBUTE
void __asan_report_load8() {}
SANITIZER_INTERFACE_ATTRIBUTE
void __asan_report_load16() {}
SANITIZER_INTERFACE_ATTRIBUTE
void __asan_report_store1() {}
SANITIZER_INTERFACE_ATTRIBUTE
void __asan_report_store2() {}
SANITIZER_INTERFACE_ATTRIBUTE
void __asan_report_store4() {}
SANITIZER_INTERFACE_ATTRIBUTE
void __asan_report_store8() {}
SANITIZER_INTERFACE_ATTRIBUTE
void __asan_report_store16() {}
SANITIZER_INTERFACE_ATTRIBUTE
void __asan_set_error_report_callback() {}

SANITIZER_INTERFACE_ATTRIBUTE
uptr __asan_stack_malloc_1(uptr size, uptr real_stack) {
  return (uptr)malloc(size);
}

SANITIZER_INTERFACE_ATTRIBUTE
uptr __asan_stack_malloc_2(uptr size, uptr real_stack) {
  return (uptr)malloc(size);
}

SANITIZER_INTERFACE_ATTRIBUTE
uptr __asan_stack_malloc_3(uptr size, uptr real_stack) {
  return (uptr)malloc(size);
}

SANITIZER_INTERFACE_ATTRIBUTE
uptr __asan_stack_malloc_4(uptr size, uptr real_stack) {
  return (uptr)malloc(size);
}

SANITIZER_INTERFACE_ATTRIBUTE
uptr __asan_stack_malloc_5(uptr size, uptr real_stack) {
  return (uptr)malloc(size);
}

SANITIZER_INTERFACE_ATTRIBUTE
uptr __asan_stack_malloc_6(uptr size, uptr real_stack) {
  return (uptr)malloc(size);
}

SANITIZER_INTERFACE_ATTRIBUTE
uptr __asan_stack_malloc_7(uptr size, uptr real_stack) {
  return (uptr)malloc(size);
}

SANITIZER_INTERFACE_ATTRIBUTE
uptr __asan_stack_malloc_8(uptr size, uptr real_stack) {
  return (uptr)malloc(size);
}

SANITIZER_INTERFACE_ATTRIBUTE
uptr __asan_stack_malloc_9(uptr size, uptr real_stack) {
  return (uptr)malloc(size);
}

SANITIZER_INTERFACE_ATTRIBUTE
uptr __asan_stack_malloc_10(uptr size, uptr real_stack) {
  return (uptr)malloc(size);
}

SANITIZER_INTERFACE_ATTRIBUTE
void __asan_stack_free_1(uptr ptr, uptr size, uptr real_stack) {
  free((void*)ptr);
}

SANITIZER_INTERFACE_ATTRIBUTE
void __asan_stack_free_2(uptr ptr, uptr size, uptr real_stack) {
  free((void*)ptr);
}

SANITIZER_INTERFACE_ATTRIBUTE
void __asan_stack_free_3(uptr ptr, uptr size, uptr real_stack) {
  free((void*)ptr);
}

SANITIZER_INTERFACE_ATTRIBUTE
void __asan_stack_free_4(uptr ptr, uptr size, uptr real_stack) {
  free((void*)ptr);
}

SANITIZER_INTERFACE_ATTRIBUTE
void __asan_stack_free_5(uptr ptr, uptr size, uptr real_stack) {
  free((void*)ptr);
}

SANITIZER_INTERFACE_ATTRIBUTE
void __asan_stack_free_6(uptr ptr, uptr size, uptr real_stack) {
  free((void*)ptr);
}

SANITIZER_INTERFACE_ATTRIBUTE
void __asan_stack_free_7(uptr ptr, uptr size, uptr real_stack) {
  free((void*)ptr);
}

SANITIZER_INTERFACE_ATTRIBUTE
void __asan_stack_free_8(uptr ptr, uptr size, uptr real_stack) {
  free((void*)ptr);
}

SANITIZER_INTERFACE_ATTRIBUTE
void __asan_stack_free_9(uptr ptr, uptr size, uptr real_stack) {
  free((void*)ptr);
}

SANITIZER_INTERFACE_ATTRIBUTE
void __asan_stack_free_10(uptr ptr, uptr size, uptr real_stack) {
  free((void*)ptr);
}

SANITIZER_INTERFACE_ATTRIBUTE
void __asan_version_mismatch_check_v6() {
  // Do nothing.
}

SANITIZER_INTERFACE_ATTRIBUTE void __sanitizer_cov_module_init(
    int32_t* guards, uptr npcs, uint8_t* counters, const char* comp_unit_name) {
}

SANITIZER_INTERFACE_ATTRIBUTE
void __asan_init() {
  static int inited = 0;
  if (inited) return;
  inited = 1;
#if __WORDSIZE == 64
  unsigned long start = 0x100000000000;
  unsigned long size = 0x100000000000;
#else
  unsigned long start = 0x20000000;
  unsigned long size = 0x20000000;
#endif
  void* res = mmap((void*)start, size, PROT_READ | PROT_WRITE,
                   MAP_PRIVATE | MAP_ANON | MAP_FIXED | MAP_NORESERVE, 0, 0);
  if (res == (void*)start) {
    fprintf(stderr, "Fake AddressSanitizer run-time initialized ok at %p\n",
            res);
  } else {
    fprintf(stderr,
            "Fake AddressSanitizer run-time failed to initialize.\n"
            "You have been warned. Aborting.");
    abort();
  }
}
}
#endif

namespace android {
namespace vts {

const string default_gcov_output_basepath = "/data/misc/gcov";

static void RemoveDir(char* path) {
  struct dirent* entry = NULL;
  DIR* dir = opendir(path);

  while ((entry = readdir(dir)) != NULL) {
    DIR* sub_dir = NULL;
    FILE* file = NULL;
    char abs_path[4096] = {0};

    if (*(entry->d_name) != '.') {
      sprintf(abs_path, "%s/%s", path, entry->d_name);
      if ((sub_dir = opendir(abs_path)) != NULL) {
        closedir(sub_dir);
        RemoveDir(abs_path);
      } else if ((file = fopen(abs_path, "r")) != NULL) {
        fclose(file);
        remove(abs_path);
      }
    }
  }
  remove(path);
}

FuzzerBase::FuzzerBase(int target_class)
    : device_(NULL),
      hmi_(NULL),
      target_dll_path_(NULL),
      target_class_(target_class),
      component_filename_(NULL),
      gcov_output_basepath_(NULL) {}

FuzzerBase::~FuzzerBase() { free(component_filename_); }

void wfn() {
  cout << __func__ << endl;
}

void ffn() {
  cout << __func__ << endl;
}

bool FuzzerBase::LoadTargetComponent(const char* target_dll_path) {
  cout << __func__ << ":" << __LINE__ << " entry" << endl;
  if (target_dll_path && target_dll_path_ &&
      !strcmp(target_dll_path, target_dll_path_)) {
    cout << __func__ << " skip loading" << endl;
    return true;
  }

  if (!target_loader_.Load(target_dll_path)) return false;
  target_dll_path_ = (char*)malloc(strlen(target_dll_path) + 1);
  strcpy(target_dll_path_, target_dll_path);
  cout << __FUNCTION__ << ":" << __LINE__ << " loaded the target" << endl;
  if (target_class_ == HAL_LEGACY) return true;
  cout << __FUNCTION__ << ":" << __LINE__ << " loaded a non-legacy HAL file."
       << endl;
  if (target_class_ == HAL_CONVENTIONAL) {
    hmi_ = target_loader_.InitConventionalHal();
    if (!hmi_) {
      free(target_dll_path_);
      target_dll_path_ = NULL;
      return false;
    }
  }
#if SANCOV
  cout << __FUNCTION__ << "sancov reset "
       << target_loader_.SancovResetCoverage() << endl;
#endif

  if (target_dll_path_) {
    cout << __func__ << ":" << __LINE__ << " target DLL path "
         << target_dll_path_ << endl;
    string target_path(target_dll_path_);

    size_t offset = target_path.rfind("/", target_path.length());
    if (offset != string::npos) {
      string filename =
          target_path.substr(offset + 1, target_path.length() - offset);
      filename = filename.substr(0, filename.length() - 3 /* for .so */);
      component_filename_ = (char*)malloc(filename.length() + 1);
      strcpy(component_filename_, filename.c_str());
      cout << __FUNCTION__ << ":" << __LINE__
           << " module file name: " << component_filename_ << endl;
    }
    cout << __FUNCTION__ << ":" << __LINE__ << " target_dll_path "
         << target_dll_path_ << endl;
  }

#if USE_GCOV
  cout << __FUNCTION__ << ": gcov init " << target_loader_.GcovInit(wfn, ffn)
       << endl;
#endif
  return true;
}

bool FuzzerBase::SetTargetObject(void* object_pointer) {
  device_ = NULL;
  hmi_ = reinterpret_cast<struct hw_module_t*>(object_pointer);
  return true;
}

bool FuzzerBase::GetService(bool /*get_stub*/, const char* /*service_name*/) {
  cerr << __func__ << " not impl" << endl;
  return false;
}

int FuzzerBase::OpenConventionalHal(const char* module_name) {
  cout << __func__ << endl;
  if (module_name) cout << __func__ << " " << module_name << endl;
  device_ = target_loader_.OpenConventionalHal(module_name);
  if (!device_) return -1;
  cout << __func__ << " device_" << device_ << endl;
  return 0;
}

bool FuzzerBase::Fuzz(vts::ComponentSpecificationMessage* message,
                      void** result) {
  cout << __func__ << " Fuzzing target component: "
       << "class " << message->component_class() << " type "
       << message->component_type() << " version "
       << message->component_type_version() << endl;

  string function_name_prefix = GetFunctionNamePrefix(*message);
  function_name_prefix_ = function_name_prefix.c_str();
  for (vts::FunctionSpecificationMessage func_msg :
       *message->mutable_interface()->mutable_api()) {
    Fuzz(&func_msg, result, "");
  }
  return true;
}

void FuzzerBase::FunctionCallBegin() {
  char product_path[4096];
  char product[128];
  char module_basepath[4096];

  cout << __func__ << ":" << __LINE__ << " begin" << endl;
  snprintf(product_path, 4096, "%s/%s", default_gcov_output_basepath.c_str(),
           "proc/self/cwd/out/target/product");
  DIR* srcdir = opendir(product_path);
  if (!srcdir) {
    cerr << __func__ << " couldn't open " << product_path << endl;
    return;
  }

  int dir_count = 0;
  struct dirent* dent;
  while ((dent = readdir(srcdir)) != NULL) {
    struct stat st;
    if (strcmp(dent->d_name, ".") == 0 || strcmp(dent->d_name, "..") == 0) {
      continue;
    }
    if (fstatat(dirfd(srcdir), dent->d_name, &st, 0) < 0) {
      cerr << __func__ << " error " << dent->d_name << endl;
      continue;
    }
    if (S_ISDIR(st.st_mode)) {
      cout << __func__ << ":" << __LINE__ << " dir " << dent->d_name << endl;
      strcpy(product, dent->d_name);
      dir_count++;
    }
  }
  closedir(srcdir);
  if (dir_count != 1) {
    cerr << __func__ << " more than one product dir found." << endl;
    return;
  }

  int n = snprintf(module_basepath, 4096, "%s/%s/obj/SHARED_LIBRARIES",
                   product_path, product);
  if (n <= 0 || n >= 4096) {
    cerr << __func__ << " couln't get module_basepath" << endl;
    return;
  }
  srcdir = opendir(module_basepath);
  if (!srcdir) {
    cerr << __func__ << " couln't open " << module_basepath << endl;
    return;
  }

  if (component_filename_ != NULL) {
    dir_count = 0;
    string target = string(component_filename_) + "_intermediates";
    bool hit = false;
    cout << __func__ << ":" << __LINE__ << endl;
    while ((dent = readdir(srcdir)) != NULL) {
      struct stat st;
      if (strcmp(dent->d_name, ".") == 0 || strcmp(dent->d_name, "..") == 0) {
        continue;
      }
      if (fstatat(dirfd(srcdir), dent->d_name, &st, 0) < 0) {
        cerr << __func__ << " error " << dent->d_name << endl;
        continue;
      }
      if (S_ISDIR(st.st_mode)) {
        cout << "module_basepath " << string(dent->d_name) << endl;
        if (string(dent->d_name) == target) {
          cout << "hit" << endl;
          hit = true;
        }
        dir_count++;
      }
    }
    if (hit) {
      free(gcov_output_basepath_);
      gcov_output_basepath_ =
          (char*)malloc(strlen(module_basepath) + target.length() + 2);
      if (!gcov_output_basepath_) {
        cerr << __FUNCTION__ << ": couldn't alloc memory" << endl;
        return;
      }
      sprintf(gcov_output_basepath_, "%s/%s", module_basepath, target.c_str());
      RemoveDir(gcov_output_basepath_);
    }
  } else {
    cerr << __func__ << ":" << __LINE__ << " component_filename_ is NULL"
         << endl;
  }
  // TODO: check how it works when there already is a file.
  closedir(srcdir);
  cout << __func__ << ":" << __LINE__ << " end" << endl;
}

bool FuzzerBase::ReadGcdaFile(
    const string& basepath, const string& filename,
    FunctionSpecificationMessage* msg) {
#if VTS_GCOV_DEBUG
      cout << __func__ << ":" << __LINE__
           << " file = " << dent->d_name << endl;
#endif
  if (string(filename).rfind(".gcda") != string::npos) {
    string buffer = basepath + "/" + filename;
    vector<unsigned> processed_data =
      android::vts::GcdaRawCoverageParser(buffer.c_str()).Parse();
    for (const auto& data : processed_data) {
      msg->mutable_processed_coverage_data()->Add(data);
    }

    FILE* gcda_file = fopen(buffer.c_str(), "rb");
    if (!gcda_file) {
      cerr << __func__ << ":" << __LINE__
           << " Unable to open a gcda file. " << buffer << endl;
    } else {
      cout << __func__ << ":" << __LINE__
           << " Opened a gcda file. " << buffer << endl;
      fseek(gcda_file, 0, SEEK_END);
      long gcda_file_size = ftell(gcda_file);
#if VTS_GCOV_DEBUG
      cout << __func__ << ":" << __LINE__
           << " File size " << gcda_file_size << " bytes" << endl;
#endif
      fseek(gcda_file, 0, SEEK_SET);

      char* gcda_file_buffer = (char*)malloc(gcda_file_size + 1);
      if (!gcda_file_buffer) {
        cerr << __func__ << ":" << __LINE__
             << "Unable to allocate memory to read a gcda file. " << endl;
      } else {
        if (fread(gcda_file_buffer, gcda_file_size, 1, gcda_file) != 1) {
          cerr << __func__ << ":" << __LINE__
               << "File read error" << endl;
        } else {
#if VTS_GCOV_DEBUG
          cout << __func__ << ":" << __LINE__
               << " GCDA field populated." << endl;
#endif
          gcda_file_buffer[gcda_file_size] = '\0';
          NativeCodeCoverageRawDataMessage* raw_msg =
              msg->mutable_raw_coverage_data()->Add();
          raw_msg->set_file_path(filename.c_str());
          string gcda_file_buffer_string(gcda_file_buffer, gcda_file_size);
          raw_msg->set_gcda(gcda_file_buffer_string);
          free(gcda_file_buffer);
        }
      }
      fclose(gcda_file);
    }
#if USE_GCOV_DEBUG
    if (result) {
      for (unsigned int index = 0; index < result->size(); index++) {
        cout << result->at(index) << endl;
      }
    }
#endif
    return true;
  }
  return false;
}

bool FuzzerBase::ScanAllGcdaFiles(
    const string& basepath, FunctionSpecificationMessage* msg) {
  DIR* srcdir = opendir(basepath.c_str());
  if (!srcdir) {
    cerr << __func__ << ":" << __LINE__
         << " couln't open " << basepath << endl;
    return false;
  }

  struct dirent* dent;
  while ((dent = readdir(srcdir)) != NULL) {
#if VTS_GCOV_DEBUG
    cout << __func__ << ":" << __LINE__
         << " readdir(" << basepath << ") for " << dent->d_name << endl;
#endif
    struct stat st;
    if (strcmp(dent->d_name, ".") == 0 || strcmp(dent->d_name, "..") == 0) {
      continue;
    }
    if (fstatat(dirfd(srcdir), dent->d_name, &st, 0) < 0) {
      cerr << "error " << dent->d_name << endl;
      continue;
    }
    if (S_ISDIR(st.st_mode)) {
      ScanAllGcdaFiles(basepath + "/" + dent->d_name, msg);
    } else {
      ReadGcdaFile(basepath, dent->d_name, msg);
    }
  }
#if VTS_GCOV_DEBUG
  cout << __func__ << ":" << __LINE__
       << " closedir(" << srcdir << ")" << endl;
#endif
  closedir(srcdir);
  return true;
}

bool FuzzerBase::FunctionCallEnd(FunctionSpecificationMessage* msg) {
  cout << __func__ << ": gcov flush " << endl;
#if USE_GCOV
  target_loader_.GcovFlush();
  // find the file.
  if (!gcov_output_basepath_) {
    cerr << __FUNCTION__ << ": no gcov basepath set" << endl;
    return ScanAllGcdaFiles(default_gcov_output_basepath, msg);
  }
  DIR* srcdir = opendir(gcov_output_basepath_);
  if (!srcdir) {
    cerr << __func__ << " couln't open " << gcov_output_basepath_ << endl;
    return false;
  }

  struct dirent* dent;
  while ((dent = readdir(srcdir)) != NULL) {
    cout << __func__ << ": readdir(" << srcdir << ") for " << dent->d_name
         << endl;

    struct stat st;
    if (strcmp(dent->d_name, ".") == 0 || strcmp(dent->d_name, "..") == 0) {
      continue;
    }
    if (fstatat(dirfd(srcdir), dent->d_name, &st, 0) < 0) {
      cerr << "error " << dent->d_name << endl;
      continue;
    }
    if (!S_ISDIR(st.st_mode)
        && ReadGcdaFile(gcov_output_basepath_, dent->d_name, msg)) {
      break;
    }
  }
  cout << __func__ << ": closedir(" << srcdir << ")" << endl;
  closedir(srcdir);
#endif
  return true;
}

}  // namespace vts
}  // namespace android
