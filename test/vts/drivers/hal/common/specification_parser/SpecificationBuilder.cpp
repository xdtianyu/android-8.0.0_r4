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

#include "specification_parser/SpecificationBuilder.h"

#include <dirent.h>

#include <iomanip>
#include <iostream>
#include <queue>
#include <string>
#include <sstream>

#include <cutils/properties.h>

#include "fuzz_tester/FuzzerBase.h"
#include "fuzz_tester/FuzzerWrapper.h"
#include "specification_parser/InterfaceSpecificationParser.h"
#include "utils/InterfaceSpecUtil.h"
#include "utils/StringUtil.h"

#include <google/protobuf/text_format.h>
#include "test/vts/proto/ComponentSpecificationMessage.pb.h"

namespace android {
namespace vts {

SpecificationBuilder::SpecificationBuilder(const string dir_path,
                                           int epoch_count,
                                           const string& callback_socket_name)
    : dir_path_(dir_path),
      epoch_count_(epoch_count),
      if_spec_msg_(NULL),
      module_name_(NULL),
      hw_binder_service_name_(NULL),
      callback_socket_name_(callback_socket_name) {}

vts::ComponentSpecificationMessage*
SpecificationBuilder::FindComponentSpecification(const int target_class,
                                                 const int target_type,
                                                 const float target_version,
                                                 const string submodule_name,
                                                 const string package,
                                                 const string component_name) {
  DIR* dir;
  struct dirent* ent;
  cerr << __func__ << ": component " << component_name << endl;

  // Derive the package-specific dir which contains .vts files
  string target_dir_path = dir_path_;
  if (!endsWith(target_dir_path, "/")) {
    target_dir_path += "/";
  }
  string target_subdir_path = package;
  ReplaceSubString(target_subdir_path, ".", "/");
  target_dir_path += target_subdir_path + "/";

  stringstream stream;
  stream << fixed << setprecision(1) << target_version;
  target_dir_path += stream.str();

  if (!(dir = opendir(target_dir_path.c_str()))) {
    cerr << __func__ << ": Can't opendir " << target_dir_path << endl;
    target_dir_path = "";
    return NULL;
  }

  while ((ent = readdir(dir))) {
    if (ent->d_type == DT_REG) {
      if (string(ent->d_name).find(SPEC_FILE_EXT) != std::string::npos) {
        cout << __func__ << ": Checking a file " << ent->d_name << endl;
        const string file_path = target_dir_path + "/" + string(ent->d_name);
        vts::ComponentSpecificationMessage* message =
            new vts::ComponentSpecificationMessage();
        if (InterfaceSpecificationParser::parse(file_path.c_str(), message)) {
          if (message->component_class() != target_class) continue;

          if (message->component_class() != HAL_HIDL) {
            if (message->component_type() == target_type &&
                message->component_type_version() == target_version) {
              if (submodule_name.length() > 0) {
                if (message->component_class() != HAL_CONVENTIONAL_SUBMODULE ||
                    message->original_data_structure_name() != submodule_name) {
                  continue;
                }
              }
              closedir(dir);
              return message;
            }
          } else {
            if (message->package() == package &&
                message->component_type_version() == target_version) {
              if (component_name.length() > 0) {
                if (message->component_name() != component_name) {
                  continue;
                }
              }
              closedir(dir);
              return message;
            }
          }
        }
        delete message;
      }
    }
  }
  closedir(dir);
  return NULL;
}

FuzzerBase* SpecificationBuilder::GetFuzzerBase(
    const vts::ComponentSpecificationMessage& iface_spec_msg,
    const char* dll_file_name, const char* /*target_func_name*/) {
  cout << __func__ << ":" << __LINE__ << " " << "entry" << endl;
  FuzzerBase* fuzzer = wrapper_.GetFuzzer(iface_spec_msg);
  if (!fuzzer) {
    cerr << __func__ << ": couldn't get a fuzzer base class" << endl;
    return NULL;
  }

  // TODO: don't load multiple times. reuse FuzzerBase*.
  cout << __func__ << ":" << __LINE__ << " " << "got fuzzer" << endl;
  if (iface_spec_msg.component_class() == HAL_HIDL) {
    char get_sub_property[PROPERTY_VALUE_MAX];
    bool get_stub = false;  /* default is binderized */
    if (property_get("vts.hidl.get_stub", get_sub_property, "") > 0) {
      if (!strcmp(get_sub_property, "true") ||
          !strcmp(get_sub_property, "True") ||
          !strcmp(get_sub_property, "1")) {
        get_stub = true;
      }
    }
    const char* service_name;
    if (hw_binder_service_name_ && strlen(hw_binder_service_name_) > 0) {
      service_name = hw_binder_service_name_;
    } else {
      service_name = iface_spec_msg.package().substr(
          iface_spec_msg.package().find_last_of(".") + 1).c_str();
    }
    if (!fuzzer->GetService(get_stub, service_name)) {
      cerr << __FUNCTION__ << ": couldn't get service" << endl;
      return NULL;
    }
  } else {
    if (!fuzzer->LoadTargetComponent(dll_file_name)) {
      cerr << __FUNCTION__ << ": couldn't load target component file, "
           << dll_file_name << endl;
      return NULL;
    }
  }
  cout << __func__ << ":" << __LINE__ << " "
       << "loaded target comp" << endl;

  return fuzzer;
  /*
   * TODO: now always return the fuzzer. this change is due to the difficulty
   * in checking nested apis although that's possible. need to check whether
   * Fuzz() found the function, while still distinguishing the difference
   * between that and defined but non-set api.
  if (!strcmp(target_func_name, "#Open")) return fuzzer;

  for (const vts::FunctionSpecificationMessage& func_msg : iface_spec_msg.api())
  {
    cout << "checking " << func_msg.name() << endl;
    if (!strcmp(target_func_name, func_msg.name().c_str())) {
      return fuzzer;
    }
  }
  return NULL;
  */
}

FuzzerBase* SpecificationBuilder::GetFuzzerBaseSubModule(
    const vts::ComponentSpecificationMessage& iface_spec_msg,
    void* object_pointer) {
  cout << __func__ << ":" << __LINE__ << " "
       << "entry object_pointer " << ((uint64_t)object_pointer) << endl;
  FuzzerWrapper wrapper;
  if (!wrapper.LoadInterfaceSpecificationLibrary(spec_lib_file_path_)) {
    cerr << __func__ << " can't load specification lib, "
         << spec_lib_file_path_ << endl;
    return NULL;
  }
  FuzzerBase* fuzzer = wrapper.GetFuzzer(iface_spec_msg);
  if (!fuzzer) {
    cerr << __FUNCTION__ << ": couldn't get a fuzzer base class" << endl;
    return NULL;
  }

  // TODO: don't load multiple times. reuse FuzzerBase*.
  cout << __func__ << ":" << __LINE__ << " "
       << "got fuzzer" << endl;
  if (iface_spec_msg.component_class() == HAL_HIDL) {
    cerr << __func__ << " HIDL not supported" << endl;
    return NULL;
  } else {
    if (!fuzzer->SetTargetObject(object_pointer)) {
      cerr << __FUNCTION__ << ": couldn't set target object" << endl;
      return NULL;
    }
  }
  cout << __func__ << ":" << __LINE__ << " "
       << "loaded target comp" << endl;
  return fuzzer;
}

FuzzerBase* SpecificationBuilder::GetFuzzerBaseAndAddAllFunctionsToQueue(
    const vts::ComponentSpecificationMessage& iface_spec_msg,
    const char* dll_file_name) {
  FuzzerBase* fuzzer = wrapper_.GetFuzzer(iface_spec_msg);
  if (!fuzzer) {
    cerr << __FUNCTION__ << ": couldn't get a fuzzer base class" << endl;
    return NULL;
  }

  if (iface_spec_msg.component_class() == HAL_HIDL) {
    char get_sub_property[PROPERTY_VALUE_MAX];
    bool get_stub = false; /* default is binderized */
    if (property_get("vts.hidl.get_stub", get_sub_property, "") > 0) {
      if (!strcmp(get_sub_property, "true") || !strcmp(get_sub_property, "True")
          || !strcmp(get_sub_property, "1")) {
        get_stub = true;
      }
    }
    const char* service_name;
    if (hw_binder_service_name_ && strlen(hw_binder_service_name_) > 0) {
      service_name = hw_binder_service_name_;
    } else {
      service_name = iface_spec_msg.package().substr(
          iface_spec_msg.package().find_last_of(".") + 1).c_str();
    }
    if (!fuzzer->GetService(get_stub, service_name)) {
      cerr << __FUNCTION__ << ": couldn't get service" << endl;
      return NULL;
    }
  } else {
    if (!fuzzer->LoadTargetComponent(dll_file_name)) {
      cerr << __FUNCTION__ << ": couldn't load target component file, "
          << dll_file_name << endl;
      return NULL;
    }
  }

  for (const vts::FunctionSpecificationMessage& func_msg :
       iface_spec_msg.interface().api()) {
    cout << "Add a job " << func_msg.name() << endl;
    FunctionSpecificationMessage* func_msg_copy = func_msg.New();
    func_msg_copy->CopyFrom(func_msg);
    job_queue_.push(make_pair(func_msg_copy, fuzzer));
  }
  return fuzzer;
}

bool SpecificationBuilder::LoadTargetComponent(
    const char* dll_file_name, const char* spec_lib_file_path, int target_class,
    int target_type, float target_version, const char* target_package,
    const char* target_component_name,
    const char* hw_binder_service_name, const char* module_name) {
  cout << __func__ << " entry dll_file_name = " << dll_file_name << endl;
  if_spec_msg_ =
      FindComponentSpecification(target_class, target_type, target_version,
                                 module_name, target_package,
                                 target_component_name);
  if (!if_spec_msg_) {
    cerr << __func__ << ": no interface specification file found for "
         << "class " << target_class << " type " << target_type << " version "
         << target_version << endl;
    return false;
  }

  if (target_class == HAL_HIDL) {
    asprintf(&spec_lib_file_path_, "%s@%s-vts.driver.so", target_package,
             GetVersionString(target_version).c_str());
    cout << __func__ << " spec lib path " << spec_lib_file_path_ << endl;
  } else {
    spec_lib_file_path_ = (char*)malloc(strlen(spec_lib_file_path) + 1);
    strcpy(spec_lib_file_path_, spec_lib_file_path);
  }

  dll_file_name_ = (char*)malloc(strlen(dll_file_name) + 1);
  strcpy(dll_file_name_, dll_file_name);

  string output;
  if_spec_msg_->SerializeToString(&output);
  cout << "loaded ifspec length " << output.length() << endl;

  module_name_ = (char*)malloc(strlen(module_name) + 1);
  strcpy(module_name_, module_name);
  cout << __func__ << ":" << __LINE__ << " module_name " << module_name_
       << endl;

  if (hw_binder_service_name) {
    hw_binder_service_name_ = (char*)malloc(strlen(hw_binder_service_name) + 1);
    strcpy(hw_binder_service_name_, hw_binder_service_name);
    cout << __func__ << ":" << __LINE__ << " hw_binder_service_name "
         << hw_binder_service_name_ << endl;
  }
  return true;
}

const string empty_string = string();

const string& SpecificationBuilder::CallFunction(
    FunctionSpecificationMessage* func_msg) {
  cout << __func__ << ":" << __LINE__ << " entry" << endl;
  if (!wrapper_.LoadInterfaceSpecificationLibrary(spec_lib_file_path_)) {
    cerr << __func__ << ":" << __LINE__ << " lib loading failed" << endl;
    return empty_string;
  }
  cout << __func__ << ":" << __LINE__ << " "
       << "loaded if_spec lib " << func_msg << endl;
  cout << __func__ << " " << dll_file_name_ << " " << func_msg->name() << endl;

  FuzzerBase* func_fuzzer;
  if (func_msg->submodule_name().size() > 0) {
    string submodule_name = func_msg->submodule_name();
    cout << __func__ << " submodule name " << submodule_name << endl;
    if (submodule_fuzzerbase_map_.find(submodule_name)
        != submodule_fuzzerbase_map_.end()) {
      cout << __func__ << " call is for a submodule" << endl;
      func_fuzzer = submodule_fuzzerbase_map_[submodule_name];
    } else {
      cerr << __func__ << " called an API of a non-loaded submodule." << endl;
      return empty_string;
    }
  } else {
    func_fuzzer = GetFuzzerBase(*if_spec_msg_, dll_file_name_,
                                func_msg->name().c_str());
  }
  cout << __func__ << ":" << __LINE__ << endl;
  if (!func_fuzzer) {
    cerr << "can't find FuzzerBase for '" << func_msg->name() << "' using '"
         << dll_file_name_ << "'" << endl;
    return empty_string;
  }

  if (func_msg->name() == "#Open") {
    cout << __func__ << ":" << __LINE__ << " #Open" << endl;
    if (func_msg->arg().size() > 0) {
      cout << __func__ << " " << func_msg->arg(0).string_value().message()
           << endl;
      func_fuzzer->OpenConventionalHal(
          func_msg->arg(0).string_value().message().c_str());
    } else {
      cout << __func__ << " no arg" << endl;
      func_fuzzer->OpenConventionalHal();
    }
    cout << __func__ << " opened" << endl;
    // return the return value from open;
    if (func_msg->return_type().has_type()) {
      cout << __func__ << " return_type exists" << endl;
      // TODO handle when the size > 1.
      if (!strcmp(func_msg->return_type().scalar_type().c_str(), "int32_t")) {
        cout << __func__ << " return_type is int32_t" << endl;
        func_msg->mutable_return_type()->mutable_scalar_value()->set_int32_t(0);
        cout << "result " << endl;
        // todo handle more types;
        string* output = new string();
        google::protobuf::TextFormat::PrintToString(*func_msg, output);
        return *output;
      }
    }
    cerr << __func__ << " return_type unknown" << endl;
    string* output = new string();
    google::protobuf::TextFormat::PrintToString(*func_msg, output);
    return *output;
  }
  cout << __func__ << ":" << __LINE__ << endl;

  void* result;
  FunctionSpecificationMessage result_msg;
  func_fuzzer->FunctionCallBegin();
  cout << __func__ << " Call Function " << func_msg->name() << " parent_path("
       << func_msg->parent_path() << ")" << endl;
  // For Hidl HAL, use CallFunction method.
  if (if_spec_msg_ && if_spec_msg_->component_class() == HAL_HIDL) {
    if (!func_fuzzer->CallFunction(*func_msg, callback_socket_name_,
                                   &result_msg)) {
      cerr << __func__ << " function not found - todo handle more explicitly"
           << endl;
      return *(new string("error"));
    }
  } else {
    if (!func_fuzzer->Fuzz(func_msg, &result, callback_socket_name_)) {
      cerr << __func__ << " function not found - todo handle more explicitly"
           << endl;
      return *(new string("error"));
    }
  }
  cout << __func__ << ": called" << endl;

  // set coverage data.
  func_fuzzer->FunctionCallEnd(func_msg);

  if (if_spec_msg_ && if_spec_msg_->component_class() == HAL_HIDL) {
    string* output = new string();
    google::protobuf::TextFormat::PrintToString(result_msg, output);
    return *output;
  } else {
    if (func_msg->return_type().type() == TYPE_PREDEFINED) {
      // TODO: actually handle this case.
      if (result != NULL) {
        // loads that interface spec and enqueues all functions.
        cout << __func__ << " return type: " << func_msg->return_type().type()
             << endl;
      } else {
        cout << __func__ << " return value = NULL" << endl;
      }
      cerr << __func__ << " todo: support aggregate" << endl;
      string* output = new string();
      google::protobuf::TextFormat::PrintToString(*func_msg, output);
      return *output;
    } else if (func_msg->return_type().type() == TYPE_SCALAR) {
      // TODO handle when the size > 1.
      if (!strcmp(func_msg->return_type().scalar_type().c_str(), "int32_t")) {
        func_msg->mutable_return_type()->mutable_scalar_value()->set_int32_t(
            *((int*)(&result)));
        cout << "result " << endl;
        // todo handle more types;
        string* output = new string();
        google::protobuf::TextFormat::PrintToString(*func_msg, output);
        return *output;
      }
    } else if (func_msg->return_type().type() == TYPE_SUBMODULE) {
      cerr << __func__ << "[driver:hal] return type TYPE_SUBMODULE" << endl;
      if (result != NULL) {
        // loads that interface spec and enqueues all functions.
        cout << __func__ << " return type: " << func_msg->return_type().type()
             << endl;
      } else {
        cout << __func__ << " return value = NULL" << endl;
      }
      // find a VTS spec for that module
      string submodule_name = func_msg->return_type().predefined_type().substr(
          0, func_msg->return_type().predefined_type().size() - 1);
      vts::ComponentSpecificationMessage* submodule_iface_spec_msg;
      if (submodule_if_spec_map_.find(submodule_name)
          != submodule_if_spec_map_.end()) {
        cout << __func__ << " submodule InterfaceSpecification already loaded"
             << endl;
        submodule_iface_spec_msg = submodule_if_spec_map_[submodule_name];
        func_msg->set_allocated_return_type_submodule_spec(
            submodule_iface_spec_msg);
      } else {
        submodule_iface_spec_msg =
            FindComponentSpecification(
                if_spec_msg_->component_class(), if_spec_msg_->component_type(),
                if_spec_msg_->component_type_version(), submodule_name,
                if_spec_msg_->package(), if_spec_msg_->component_name());
        if (!submodule_iface_spec_msg) {
          cerr << __func__ << " submodule InterfaceSpecification not found" << endl;
        } else {
          cout << __func__ << " submodule InterfaceSpecification found" << endl;
          func_msg->set_allocated_return_type_submodule_spec(
              submodule_iface_spec_msg);
          FuzzerBase* func_fuzzer = GetFuzzerBaseSubModule(
              *submodule_iface_spec_msg, result);
          submodule_if_spec_map_[submodule_name] = submodule_iface_spec_msg;
          submodule_fuzzerbase_map_[submodule_name] = func_fuzzer;
        }
      }
      string* output = new string();
      google::protobuf::TextFormat::PrintToString(*func_msg, output);
      return *output;
    }
  }
  return *(new string("void"));
}

const string& SpecificationBuilder::GetAttribute(
    FunctionSpecificationMessage* func_msg) {
  if (!wrapper_.LoadInterfaceSpecificationLibrary(spec_lib_file_path_)) {
    return empty_string;
  }
  cout << __func__ << " "
       << "loaded if_spec lib" << endl;
  cout << __func__ << " " << dll_file_name_ << " " << func_msg->name() << endl;

  FuzzerBase* func_fuzzer;
  if (func_msg->submodule_name().size() > 0) {
    string submodule_name = func_msg->submodule_name();
    cout << __func__ << " submodule name " << submodule_name << endl;
    if (submodule_fuzzerbase_map_.find(submodule_name)
        != submodule_fuzzerbase_map_.end()) {
      cout << __func__ << " call is for a submodule" << endl;
      func_fuzzer = submodule_fuzzerbase_map_[submodule_name];
    } else {
      cerr << __func__ << " called an API of a non-loaded submodule." << endl;
      return empty_string;
    }
  } else {
    func_fuzzer = GetFuzzerBase(*if_spec_msg_, dll_file_name_,
                                func_msg->name().c_str());
  }
  cout << __func__ << ":" << __LINE__ << endl;
  if (!func_fuzzer) {
    cerr << "can't find FuzzerBase for " << func_msg->name() << " using "
         << dll_file_name_ << endl;
    return empty_string;
  }

  void* result;
  cout << __func__ << " Get Atrribute " << func_msg->name() << " parent_path("
       << func_msg->parent_path() << ")" << endl;
  if (!func_fuzzer->GetAttribute(func_msg, &result)) {
    cerr << __func__ << " attribute not found - todo handle more explicitly"
         << endl;
    return *(new string("error"));
  }
  cout << __func__ << ": called" << endl;

  if (if_spec_msg_ && if_spec_msg_->component_class() == HAL_HIDL) {
    cout << __func__ << ": for a HIDL HAL" << endl;
    func_msg->mutable_return_type()->set_type(TYPE_STRING);
    func_msg->mutable_return_type()->mutable_string_value()->set_message(
        *(string*)result);
    func_msg->mutable_return_type()->mutable_string_value()->set_length(
        ((string*)result)->size());
    free(result);
    string* output = new string();
    google::protobuf::TextFormat::PrintToString(*func_msg, output);
    return *output;
  } else {
    cout << __func__ << ": for a non-HIDL HAL" << endl;
    if (func_msg->return_type().type() == TYPE_PREDEFINED) {
      // TODO: actually handle this case.
      if (result != NULL) {
        // loads that interface spec and enqueues all functions.
        cout << __func__ << " return type: " << func_msg->return_type().type()
             << endl;
      } else {
        cout << __func__ << " return value = NULL" << endl;
      }
      cerr << __func__ << " todo: support aggregate" << endl;
      string* output = new string();
      google::protobuf::TextFormat::PrintToString(*func_msg, output);
      return *output;
    } else if (func_msg->return_type().type() == TYPE_SCALAR) {
      // TODO handle when the size > 1.
      if (!strcmp(func_msg->return_type().scalar_type().c_str(), "int32_t")) {
        func_msg->mutable_return_type()->mutable_scalar_value()->set_int32_t(
            *((int*)(&result)));
        cout << "result " << endl;
        // todo handle more types;
        string* output = new string();
        google::protobuf::TextFormat::PrintToString(*func_msg, output);
        return *output;
      } else if (!strcmp(func_msg->return_type().scalar_type().c_str(), "uint32_t")) {
        func_msg->mutable_return_type()->mutable_scalar_value()->set_uint32_t(
            *((int*)(&result)));
        cout << "result " << endl;
        // todo handle more types;
        string* output = new string();
        google::protobuf::TextFormat::PrintToString(*func_msg, output);
        return *output;
      } else if (!strcmp(func_msg->return_type().scalar_type().c_str(), "int16_t")) {
        func_msg->mutable_return_type()->mutable_scalar_value()->set_int16_t(
            *((int*)(&result)));
        cout << "result " << endl;
        // todo handle more types;
        string* output = new string();
        google::protobuf::TextFormat::PrintToString(*func_msg, output);
        return *output;
      } else if (!strcmp(func_msg->return_type().scalar_type().c_str(), "uint16_t")) {
        func_msg->mutable_return_type()->mutable_scalar_value()->set_uint16_t(
            *((int*)(&result)));
        cout << "result " << endl;
        // todo handle more types;
        string* output = new string();
        google::protobuf::TextFormat::PrintToString(*func_msg, output);
        return *output;
      }
    } else if (func_msg->return_type().type() == TYPE_SUBMODULE) {
      cerr << __func__ << "[driver:hal] return type TYPE_SUBMODULE" << endl;
      if (result != NULL) {
        // loads that interface spec and enqueues all functions.
        cout << __func__ << " return type: " << func_msg->return_type().type()
             << endl;
      } else {
        cout << __func__ << " return value = NULL" << endl;
      }
      // find a VTS spec for that module
      string submodule_name = func_msg->return_type().predefined_type().substr(
          0, func_msg->return_type().predefined_type().size() - 1);
      vts::ComponentSpecificationMessage* submodule_iface_spec_msg;
      if (submodule_if_spec_map_.find(submodule_name)
          != submodule_if_spec_map_.end()) {
        cout << __func__ << " submodule InterfaceSpecification already loaded"
             << endl;
        submodule_iface_spec_msg = submodule_if_spec_map_[submodule_name];
        func_msg->set_allocated_return_type_submodule_spec(
            submodule_iface_spec_msg);
      } else {
        submodule_iface_spec_msg =
            FindComponentSpecification(
                if_spec_msg_->component_class(), if_spec_msg_->component_type(),
                if_spec_msg_->component_type_version(), submodule_name,
                if_spec_msg_->package(), if_spec_msg_->component_name());
        if (!submodule_iface_spec_msg) {
          cerr << __func__ << " submodule InterfaceSpecification not found" << endl;
        } else {
          cout << __func__ << " submodule InterfaceSpecification found" << endl;
          func_msg->set_allocated_return_type_submodule_spec(
              submodule_iface_spec_msg);
          FuzzerBase* func_fuzzer = GetFuzzerBaseSubModule(
              *submodule_iface_spec_msg, result);
          submodule_if_spec_map_[submodule_name] = submodule_iface_spec_msg;
          submodule_fuzzerbase_map_[submodule_name] = func_fuzzer;
        }
      }
      string* output = new string();
      google::protobuf::TextFormat::PrintToString(*func_msg, output);
      return *output;
    }
  }
  return *(new string("void"));
}

bool SpecificationBuilder::Process(const char* dll_file_name,
                                   const char* spec_lib_file_path,
                                   int target_class, int target_type,
                                   float target_version,
                                   const char* target_package,
                                   const char* target_component_name) {
  vts::ComponentSpecificationMessage* interface_specification_message =
      FindComponentSpecification(target_class, target_type, target_version,
                                 "", target_package, target_component_name);
  cout << "ifspec addr " << interface_specification_message << endl;

  if (!interface_specification_message) {
    cerr << __func__ << ": no interface specification file found for class "
         << target_class << " type " << target_type << " version "
         << target_version << endl;
    return false;
  }

  if (!wrapper_.LoadInterfaceSpecificationLibrary(spec_lib_file_path)) {
    return false;
  }

  if (!GetFuzzerBaseAndAddAllFunctionsToQueue(*interface_specification_message,
                                              dll_file_name))
    return false;

  for (int i = 0; i < epoch_count_; i++) {
    // by default, breath-first-searching is used.
    if (job_queue_.empty()) {
      cout << "no more job to process; stopping after epoch " << i << endl;
      break;
    }

    pair<vts::FunctionSpecificationMessage*, FuzzerBase*> curr_job =
        job_queue_.front();
    job_queue_.pop();

    vts::FunctionSpecificationMessage* func_msg = curr_job.first;
    FuzzerBase* func_fuzzer = curr_job.second;

    void* result;
    FunctionSpecificationMessage result_msg;
    cout << "Iteration " << (i + 1) << " Function " << func_msg->name() << endl;
    // For Hidl HAL, use CallFunction method.
    if (interface_specification_message->component_class() == HAL_HIDL) {
      func_fuzzer->CallFunction(*func_msg, callback_socket_name_, &result_msg);
    } else {
      func_fuzzer->Fuzz(func_msg, &result, callback_socket_name_);
    }
    if (func_msg->return_type().type() == TYPE_PREDEFINED) {
      if (result != NULL) {
        // loads that interface spec and enqueues all functions.
        cout << __FUNCTION__
             << " return type: " << func_msg->return_type().predefined_type()
             << endl;
        // TODO: handle the case when size > 1
        string submodule_name = func_msg->return_type().predefined_type();
        while (!submodule_name.empty() &&
               (std::isspace(submodule_name.back()) ||
                submodule_name.back() == '*')) {
          submodule_name.pop_back();
        }
        vts::ComponentSpecificationMessage* iface_spec_msg =
            FindComponentSpecification(target_class, target_type,
                                       target_version, submodule_name);
        if (iface_spec_msg) {
          cout << __FUNCTION__ << " submodule found - " << submodule_name
               << endl;
          if (!GetFuzzerBaseAndAddAllFunctionsToQueue(*iface_spec_msg,
                                                      dll_file_name)) {
            return false;
          }
        } else {
          cout << __FUNCTION__ << " submodule not found - " << submodule_name
               << endl;
        }
      } else {
        cout << __FUNCTION__ << " return value = NULL" << endl;
      }
    }
  }

  return true;
}

vts::ComponentSpecificationMessage*
SpecificationBuilder::GetComponentSpecification() const {
  cout << "ifspec addr get " << if_spec_msg_ << endl;
  return if_spec_msg_;
}

}  // namespace vts
}  // namespace android
