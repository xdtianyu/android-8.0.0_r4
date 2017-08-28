/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "component_loader/DllLoader.h"

#include <dlfcn.h>

#include <iostream>

#include "hardware/hardware.h"

using namespace std;

namespace android {
namespace vts {

DllLoader::DllLoader() : handle_(NULL), hmi_(NULL), device_(NULL) {}

DllLoader::~DllLoader() {
  if (!handle_) {
    dlclose(handle_);
    handle_ = NULL;
  }
}

void* DllLoader::Load(const char* file_path, bool is_conventional_hal) {
  if (!file_path) {
    cerr << __func__ << ": file_path is NULL" << endl;
    return NULL;
  }

  // consider using the load mechanism in hardware/libhardware/hardware.c
  handle_ = dlopen(file_path, RTLD_LAZY);
  if (!handle_) {
    cerr << __func__ << ": " << dlerror() << endl;
    cerr << __func__ << ": Can't load a shared library, " << file_path << "."
         << endl;
    return NULL;
  }
  cout << __func__ << ": DLL loaded " << file_path << endl;
  if (is_conventional_hal) {
    cout << __func__ << ": setting hmi" << endl;
    hmi_ = (struct hw_module_t*)LoadSymbol(HAL_MODULE_INFO_SYM_AS_STR);
  }
  return handle_;
}

struct hw_module_t* DllLoader::InitConventionalHal() {
  if (!handle_) {
    cerr << __func__ << ": handle_ is NULL" << endl;
    return NULL;
  }
  hmi_ = (struct hw_module_t*)LoadSymbol(HAL_MODULE_INFO_SYM_AS_STR);
  if (!hmi_) {
    return NULL;
  }
  cout << __func__ << ":" << __LINE__ << endl;
  hmi_->dso = handle_;
  device_ = NULL;
  cout << __func__ << ": version " << hmi_->module_api_version << endl;
  return hmi_;
}

struct hw_device_t* DllLoader::OpenConventionalHal(const char* module_name) {
  cout << __func__ << endl;
  if (!handle_) {
    cerr << __func__ << ": handle_ is NULL" << endl;
    return NULL;
  }
  if (!hmi_) {
    cerr << __func__ << ": hmi_ is NULL" << endl;
    return NULL;
  }

  device_ = NULL;
  int ret;
  if (module_name && strlen(module_name) > 0) {
    cout << __func__ << ":" << __LINE__ << ": module_name |" << module_name
         << "|" << endl;
    ret =
        hmi_->methods->open(hmi_, module_name, (struct hw_device_t**)&device_);
  } else {
    cout << __func__ << ":" << __LINE__ << ": (default) " << hmi_->name
         << endl;
    ret = hmi_->methods->open(hmi_, hmi_->name, (struct hw_device_t**)&device_);
  }
  if (ret != 0) {
    cout << "returns " << ret << " " << strerror(errno) << endl;
  }
  cout << __func__ << ":" << __LINE__ << " device_ " << device_ << endl;
  return device_;
}

loader_function DllLoader::GetLoaderFunction(const char* function_name) {
  loader_function func = (loader_function)LoadSymbol(function_name);
  return func;
}

bool DllLoader::SancovResetCoverage() {
  void (*func)() = (void (*)())LoadSymbol("__sanitizer_reset_coverage");
  if (func == NULL) {
    return false;
  }
  func();
  return true;
}

bool DllLoader::GcovInit(writeout_fn wfn, flush_fn ffn) {
  void (*func)(writeout_fn, flush_fn) =
      (void (*)(writeout_fn, flush_fn))LoadSymbol("llvm_gcov_init");
  if (func == NULL) {
    return false;
  }
  func(wfn, ffn);
  return true;
}

bool DllLoader::GcovFlush() {
  void (*func)() = (void (*)()) LoadSymbol("__gcov_flush");
  if (func == NULL) {
    return false;
  }
  func();
  return true;
}

void* DllLoader::LoadSymbol(const char* symbol_name) {
  const char* error = dlerror();
  if (error != NULL) {
    cerr << __func__ << ": existing error message before loading "
         << symbol_name << endl;
    cerr << __func__ << ": " << error << endl;
  }
  void* sym = dlsym(handle_, symbol_name);
  if ((error = dlerror()) != NULL) {
    cerr << __func__ << ": Can't find " << symbol_name << endl;
    cerr << __func__ << ": " << error << endl;
    return NULL;
  }
  return sym;
}

}  // namespace vts
}  // namespace android
