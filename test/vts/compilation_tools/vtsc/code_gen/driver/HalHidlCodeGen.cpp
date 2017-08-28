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

#include "code_gen/driver/HalHidlCodeGen.h"

#include <fstream>
#include <iostream>
#include <set>
#include <sstream>
#include <string>

#include "test/vts/proto/ComponentSpecificationMessage.pb.h"

#include "VtsCompilerUtils.h"
#include "utils/InterfaceSpecUtil.h"
#include "utils/StringUtil.h"

using namespace std;
using namespace android;

namespace android {
namespace vts {

const char* const HalHidlCodeGen::kInstanceVariableName = "hw_binder_proxy_";

void HalHidlCodeGen::GenerateCppBodyCallbackFunction(Formatter& out,
    const ComponentSpecificationMessage& message,
    const string& /*fuzzer_extended_class_name*/) {
  if (endsWith(message.component_name(), "Callback")) {
    out << "\n";
    FQName component_fq_name = GetFQName(message);
    for (const auto& api : message.interface().api()) {
      // Generate return statement.
      if (CanElideCallback(api)) {
        out << "::android::hardware::Return<"
            << GetCppVariableType(api.return_type_hidl(0), &message) << "> ";
      } else {
        out << "::android::hardware::Return<void> ";
      }
      // Generate function call.
      string full_method_name = "Vts_" + component_fq_name.tokenName() + "::"
          + api.name();
      out << full_method_name << "(\n";
      out.indent();
      for (int index = 0; index < api.arg_size(); index++) {
        const auto& arg = api.arg(index);
        if (!isConstType(arg.type())) {
          out << GetCppVariableType(arg, &message);
        } else {
          out << GetCppVariableType(arg, &message, true);
        }
        out << " arg" << index;
        if (index != (api.arg_size() - 1))
          out << ",\n";
      }
      if (api.return_type_hidl_size() == 0 || CanElideCallback(api)) {
        out << ") {" << "\n";
      } else {  // handle the case of callbacks.
        out << (api.arg_size() != 0 ? ", " : "");
        out << "std::function<void(";
        for (int index = 0; index < api.return_type_hidl_size(); index++) {
          const auto& return_val = api.return_type_hidl(index);
          if (!isConstType(return_val.type())) {
            out << GetCppVariableType(return_val, &message);
          } else {
            out << GetCppVariableType(return_val, &message, true);
          }
          out << " arg" << index;
          if (index != (api.return_type_hidl_size() - 1))
            out << ",";
        }
        out << ")>) {" << "\n";
      }
      out << "cout << \"" << api.name() << " called\" << endl;" << "\n";
      out << "AndroidSystemCallbackRequestMessage callback_message;" << "\n";
      out << "callback_message.set_id(GetCallbackID(\"" << api.name() << "\"));" << "\n";
      out << "callback_message.set_name(\"" << full_method_name << "\");" << "\n";
      for (int index = 0; index < api.arg_size(); index++) {
        out << "VariableSpecificationMessage* var_msg" << index << " = "
            << "callback_message.add_arg();\n";
        GenerateSetResultCodeForTypedVariable(out, api.arg(index),
                                              "var_msg" + std::to_string(index),
                                              "arg" + std::to_string(index));
      }
      out << "RpcCallToAgent(callback_message, callback_socket_name_);" << "\n";

      if (api.return_type_hidl_size() == 0
          || api.return_type_hidl(0).type() == TYPE_VOID) {
        out << "return ::android::hardware::Void();" << "\n";
      } else {
        out << "return hardware::Status::ok();" << "\n";
      }
      out.unindent();
      out << "}" << "\n";
      out << "\n";
    }

    string component_name_token = "Vts_" + component_fq_name.tokenName();
    out << "sp<" << component_fq_name.cppName() << "> VtsFuzzerCreate"
        << component_name_token << "(const string& callback_socket_name)";
    out << " {" << "\n";
    out.indent();
    out << "static sp<" << component_fq_name.cppName() << "> result;\n";
    out << "result = new " << component_name_token << "(callback_socket_name);"
        << "\n";
    out << "return result;\n";
    out.unindent();
    out << "}" << "\n" << "\n";
  }
}

void HalHidlCodeGen::GenerateScalarTypeInC(Formatter& out, const string& type) {
  if (type == "bool_t") {
    out << "bool";
  } else if (type == "int8_t" ||
             type == "uint8_t" ||
             type == "int16_t" ||
             type == "uint16_t" ||
             type == "int32_t" ||
             type == "uint32_t" ||
             type == "int64_t" ||
             type == "uint64_t" ||
             type == "size_t") {
    out << type;
  } else if (type == "float_t") {
    out << "float";
  } else if (type == "double_t") {
    out << "double";
  } else if (type == "char_pointer") {
    out << "char*";
  } else if (type == "void_pointer") {
    out << "void*";
  } else {
    cerr << __func__ << ":" << __LINE__
         << " unsupported scalar type " << type << "\n";
    exit(-1);
  }
}


void HalHidlCodeGen::GenerateCppBodyFuzzFunction(
    Formatter& out, const ComponentSpecificationMessage& /*message*/,
    const string& fuzzer_extended_class_name) {
    out << "bool " << fuzzer_extended_class_name << "::Fuzz(" << "\n";
    out << "    FunctionSpecificationMessage* func_msg," << "\n";
    out << "    void** result, const string& callback_socket_name) {\n";
    out.indent();
    out << "return true;\n";
    out.unindent();
    out << "}\n";
}

void HalHidlCodeGen::GenerateDriverFunctionImpl(Formatter& out,
    const ComponentSpecificationMessage& message,
    const string& fuzzer_extended_class_name) {
  if (message.component_name() != "types"
      && !endsWith(message.component_name(), "Callback")) {
    out << "bool " << fuzzer_extended_class_name << "::CallFunction("
        << "const FunctionSpecificationMessage& func_msg, "
        << "const string& callback_socket_name, "
        << "FunctionSpecificationMessage* result_msg) {\n";
    out.indent();

    out << "const char* func_name = func_msg.name().c_str();" << "\n";
    out << "cout << \"Function: \" << __func__ << \" \" << func_name << endl;"
        << "\n";

    for (auto const& api : message.interface().api()) {
      GenerateDriverImplForMethod(out, message, api);
    }

    GenerateDriverImplForReservedMethods(out);

    out << "return false;\n";
    out.unindent();
    out << "}\n";
  }
}

void HalHidlCodeGen::GenerateDriverImplForReservedMethods(Formatter& out) {
  // Generate call for reserved method: notifySyspropsChanged.
  out << "if (!strcmp(func_name, \"notifySyspropsChanged\")) {\n";
  out.indent();

  out << "cout << \"Call notifySyspropsChanged\" << endl;" << "\n";
  out << kInstanceVariableName << "->notifySyspropsChanged();\n";
  out << "result_msg->set_name(\"notifySyspropsChanged\");\n";
  out << "cout << \"called\" << endl;\n";
  out << "return true;\n";

  out.unindent();
  out << "}\n";
  // TODO(zhuoyao): Add generation code for other reserved method,
  // e.g interfaceChain
}

void HalHidlCodeGen::GenerateDriverImplForMethod(Formatter& out,
    const ComponentSpecificationMessage& message,
    const FunctionSpecificationMessage& func_msg) {
  out << "if (!strcmp(func_name, \"" << func_msg.name() << "\")) {\n";
  out.indent();
  // Process the arguments.
  for (int i = 0; i < func_msg.arg_size(); i++) {
    const auto& arg = func_msg.arg(i);
    string cur_arg_name = "arg" + std::to_string(i);
    string var_type;
    if (arg.type() == TYPE_ARRAY || arg.type() == TYPE_VECTOR) {
      var_type = GetCppVariableType(arg, &message, true);
      var_type = var_type.substr(5, var_type.length() - 6);
    } else {
      var_type = GetCppVariableType(arg, &message);
    }
    out << var_type << " " << cur_arg_name << ";\n";
    if (arg.type() == TYPE_SCALAR) {
      out << cur_arg_name << " = 0;\n";
    }
    GenerateDriverImplForTypedVariable(
        out, arg, cur_arg_name, "func_msg.arg(" + std::to_string(i) + ")");
  }

  GenerateCodeToStartMeasurement(out);
  // may need to check whether the function is actually defined.
  out << "cout << \"Call an API\" << endl;" << "\n";
  out << "cout << \"local_device = \" << " << kInstanceVariableName << ".get()"
      << " << endl;\n";

  // Define the return results and call the HAL function.
  for (int index = 0; index < func_msg.return_type_hidl_size(); index++) {
    const auto& return_type = func_msg.return_type_hidl(index);
    out << GetCppVariableType(return_type, &message) << " result" << index
        << ";\n";
  }
  if (CanElideCallback(func_msg)) {
    out << "result0 = ";
    GenerateHalFunctionCall(out, message, func_msg);
  } else {
    GenerateHalFunctionCall(out, message, func_msg);
  }

  GenerateCodeToStopMeasurement(out);

  // Set the return results value to the proto message.
  out << "result_msg->set_name(\"" << func_msg.name() << "\");\n";
  for (int index = 0; index < func_msg.return_type_hidl_size(); index++) {
    out << "VariableSpecificationMessage* result_val_" << index << " = "
        << "result_msg->add_return_type_hidl();\n";
    GenerateSetResultCodeForTypedVariable(out, func_msg.return_type_hidl(index),
                                          "result_val_" + std::to_string(index),
                                          "result" + std::to_string(index));
  }

  out << "cout << \"called\" << endl;\n";
  out << "return true;\n";
  out.unindent();
  out << "}\n";
}

void HalHidlCodeGen::GenerateHalFunctionCall(Formatter& out,
    const ComponentSpecificationMessage& message,
    const FunctionSpecificationMessage& func_msg) {
  out << kInstanceVariableName << "->" << func_msg.name() << "(";
  for (int index = 0; index < func_msg.arg_size(); index++) {
    out << "arg" << index;
    if (index != (func_msg.arg_size() - 1)) out << ",";
  }
  if (func_msg.return_type_hidl_size()== 0 || CanElideCallback(func_msg)) {
    out << ");\n";
  } else {
    out << (func_msg.arg_size() != 0 ? ", " : "");
    GenerateSyncCallbackFunctionImpl(out, message, func_msg);
    out << ");\n";
  }
}

void HalHidlCodeGen::GenerateSyncCallbackFunctionImpl(Formatter& out,
    const ComponentSpecificationMessage& message,
    const FunctionSpecificationMessage& func_msg) {
  out << "[&](";
  for (int index = 0; index < func_msg.return_type_hidl_size(); index++) {
    const auto& return_val = func_msg.return_type_hidl(index);
    if (!isConstType(return_val.type())) {
      out << GetCppVariableType(return_val, &message);
    } else {
      out << GetCppVariableType(return_val, &message, true);
    }
    out << " arg" << index;
    if (index != (func_msg.return_type_hidl_size() - 1)) out << ",";
  }
  out << "){\n";
  out.indent();
  out << "cout << \"callback " << func_msg.name() << " called\""
      << " << endl;\n";

  for (int index = 0; index < func_msg.return_type_hidl_size(); index++) {
    const auto& return_val = func_msg.return_type_hidl(index);
    if (return_val.type() != TYPE_FMQ_SYNC
        && return_val.type() != TYPE_FMQ_UNSYNC)
      out << "result" << index << " = arg" << index << ";\n";
  }
  out.unindent();
  out << "}";
}

void HalHidlCodeGen::GenerateCppBodyGetAttributeFunction(
    Formatter& out, const ComponentSpecificationMessage& message,
    const string& fuzzer_extended_class_name) {
  if (message.component_name() != "types" &&
      !endsWith(message.component_name(), "Callback")) {
    out << "bool " << fuzzer_extended_class_name << "::GetAttribute(" << "\n";
    out << "    FunctionSpecificationMessage* func_msg," << "\n";
    out << "    void** result) {" << "\n";

    // TOOD: impl
    out << "  cerr << \"attribute not found\" << endl;" << "\n";
    out << "  return false;" << "\n";
    out << "}" << "\n";
  }
}

void HalHidlCodeGen::GenerateClassConstructionFunction(Formatter& out,
    const ComponentSpecificationMessage& message,
    const string& fuzzer_extended_class_name) {
  out << fuzzer_extended_class_name << "() : FuzzerBase(";
  if (message.component_name() != "types") {
    out << "HAL_HIDL), " << kInstanceVariableName << "()";
  } else {
    out << "HAL_HIDL)";
  }
  out << " {}" << "\n";
}

void HalHidlCodeGen::GenerateHeaderGlobalFunctionDeclarations(Formatter& out,
    const ComponentSpecificationMessage& message) {
  if (message.component_name() != "types"
      && !endsWith(message.component_name(), "Callback")) {
    DriverCodeGenBase::GenerateHeaderGlobalFunctionDeclarations(out, message);
  }
}

void HalHidlCodeGen::GenerateCppBodyGlobalFunctions(Formatter& out,
    const ComponentSpecificationMessage& message,
    const string& fuzzer_extended_class_name) {
  if (message.component_name() != "types"
      && !endsWith(message.component_name(), "Callback")) {
    DriverCodeGenBase::GenerateCppBodyGlobalFunctions(
        out, message, fuzzer_extended_class_name);
  }
}

void HalHidlCodeGen::GenerateClassHeader(Formatter& out,
    const ComponentSpecificationMessage& message,
    const string& fuzzer_extended_class_name) {
  if (message.component_name() != "types"
      && !endsWith(message.component_name(), "Callback")) {
    for (const auto attribute : message.interface().attribute()) {
      GenerateAllFunctionDeclForAttribute(out, attribute);
    }
    DriverCodeGenBase::GenerateClassHeader(out, message,
                                           fuzzer_extended_class_name);
  } else if (message.component_name() == "types") {
    for (const auto attribute : message.attribute()) {
      GenerateAllFunctionDeclForAttribute(out, attribute);
    };
  } else if (endsWith(message.component_name(), "Callback")) {
    for (const auto attribute : message.interface().attribute()) {
      GenerateAllFunctionDeclForAttribute(out, attribute);
    }

    out << "\n";
    FQName component_fq_name = GetFQName(message);
    string component_name_token = "Vts_" + component_fq_name.tokenName();;
    out << "class " << component_name_token << " : public "
        << component_fq_name.cppName() << ", public FuzzerCallbackBase {" << "\n";
    out << " public:" << "\n";
    out.indent();
    out << component_name_token << "(const string& callback_socket_name)\n"
        << "    : callback_socket_name_(callback_socket_name) {};" << "\n";
    out << "\n";
    out << "virtual ~" << component_name_token << "()"
        << " = default;" << "\n";
    out << "\n";
    for (const auto& api : message.interface().api()) {
      // Generate return statement.
      if (CanElideCallback(api)) {
        out << "::android::hardware::Return<"
            << GetCppVariableType(api.return_type_hidl(0), &message) << "> ";
      } else {
        out << "::android::hardware::Return<void> ";
      }
      // Generate function call.
      out << api.name() << "(\n";
      out.indent();
      for (int index = 0; index < api.arg_size(); index++) {
        const auto& arg = api.arg(index);
        if (!isConstType(arg.type())) {
          out << GetCppVariableType(arg, &message);
        } else {
          out << GetCppVariableType(arg, &message, true);
        }
        out << " arg" << index;
        if (index != (api.arg_size() - 1))
          out << ",\n";
      }
      if (api.return_type_hidl_size() == 0 || CanElideCallback(api)) {
        out << ") override;" << "\n\n";
      } else {  // handle the case of callbacks.
        out << (api.arg_size() != 0 ? ", " : "");
        out << "std::function<void(";
        for (int index = 0; index < api.return_type_hidl_size(); index++) {
          const auto& return_val = api.return_type_hidl(index);
          if (!isConstType(return_val.type())) {
            out << GetCppVariableType(return_val, &message);
          } else {
            out << GetCppVariableType(return_val, &message, true);
          }
          out << " arg" << index;
          if (index != (api.return_type_hidl_size() - 1))
            out << ",";
        }
        out << ")>) override;" << "\n\n";
      }
      out.unindent();
    }
    out << "\n";
    out.unindent();
    out << " private:" << "\n";
    out.indent();
    out << "const string& callback_socket_name_;" << "\n";
    out.unindent();
    out << "};" << "\n";
    out << "\n";

    out << "sp<" << component_fq_name.cppName() << "> VtsFuzzerCreate"
        << component_name_token << "(const string& callback_socket_name);"
        << "\n";
    out << "\n";
  }
}

void HalHidlCodeGen::GenerateClassImpl(Formatter& out,
    const ComponentSpecificationMessage& message,
    const string& fuzzer_extended_class_name) {
  if (message.component_name() != "types"
      && !endsWith(message.component_name(), "Callback")) {
    for (auto attribute : message.interface().attribute()) {
      GenerateAllFunctionImplForAttribute(out, attribute);
    }
    GenerateGetServiceImpl(out, message, fuzzer_extended_class_name);
    DriverCodeGenBase::GenerateClassImpl(out, message,
                                         fuzzer_extended_class_name);
  } else if (message.component_name() == "types") {
    for (auto attribute : message.attribute()) {
      GenerateAllFunctionImplForAttribute(out, attribute);
    }
  } else if (endsWith(message.component_name(), "Callback")) {
    for (auto attribute : message.interface().attribute()) {
      GenerateAllFunctionImplForAttribute(out, attribute);
    }
    GenerateCppBodyCallbackFunction(out, message, fuzzer_extended_class_name);
  }
}

void HalHidlCodeGen::GenerateHeaderIncludeFiles(Formatter& out,
    const ComponentSpecificationMessage& message,
    const string& fuzzer_extended_class_name) {
  DriverCodeGenBase::GenerateHeaderIncludeFiles(out, message,
                                                fuzzer_extended_class_name);

  string package_path_self = message.package();
  ReplaceSubString(package_path_self, ".", "/");
  string version_self = GetVersionString(message.component_type_version());

  out << "#include <" << package_path_self << "/"
      << version_self << "/"
      << message.component_name() << ".h>" << "\n";
  out << "#include <hidl/HidlSupport.h>" << "\n";

  for (const auto& import : message.import()) {
    FQName import_name = FQName(import);
    string package_path = import_name.package();
    string package_version = import_name.version();
    string component_name = import_name.name();
    ReplaceSubString(package_path, ".", "/");

    out << "#include <" << package_path << "/" << package_version << "/"
        << component_name << ".h>\n";
    if (package_path.find("android/hardware") != std::string::npos) {
      if (component_name[0] == 'I') {
        component_name = component_name.substr(1);
      }
      out << "#include <" << package_path << "/" << package_version << "/"
          << component_name << ".vts.h>\n";
    }
  }
  out << "\n\n";
}

void HalHidlCodeGen::GenerateSourceIncludeFiles(Formatter& out,
    const ComponentSpecificationMessage& message,
    const string& fuzzer_extended_class_name) {
  DriverCodeGenBase::GenerateSourceIncludeFiles(out, message,
                                                fuzzer_extended_class_name);
  out << "#include <hidl/HidlSupport.h>\n";
  string input_vfs_file_path(input_vts_file_path_);
  string package_path = message.package();
  ReplaceSubString(package_path, ".", "/");
  out << "#include <" << package_path << "/"
      << GetVersionString(message.component_type_version()) << "/"
      << message.component_name() << ".h>" << "\n";
  for (const auto& import : message.import()) {
    FQName import_name = FQName(import);
    string package_name = import_name.package();
    string package_version = import_name.version();
    string component_name = import_name.name();
    string package_path = package_name;
    ReplaceSubString(package_path, ".", "/");
    if (package_name == message.package()
        && package_version
            == GetVersionString(message.component_type_version())) {
      if (component_name == "types") {
        out << "#include \""
            << input_vfs_file_path.substr(
                0, input_vfs_file_path.find_last_of("\\/"))
            << "/types.vts.h\"\n";
      } else {
        out << "#include \""
            << input_vfs_file_path.substr(
                0, input_vfs_file_path.find_last_of("\\/")) << "/"
            << component_name.substr(1) << ".vts.h\"\n";
      }
    } else {
      out << "#include <" << package_path << "/" << package_version << "/"
          << component_name << ".h>\n";
    }
  }
}

void HalHidlCodeGen::GenerateAdditionalFuctionDeclarations(Formatter& out,
    const ComponentSpecificationMessage& message,
    const string& /*fuzzer_extended_class_name*/) {
  if (message.component_name() != "types"
      && !endsWith(message.component_name(), "Callback")) {
    out << "bool GetService(bool get_stub, const char* service_name);"
        << "\n\n";
  }
}

void HalHidlCodeGen::GeneratePrivateMemberDeclarations(Formatter& out,
    const ComponentSpecificationMessage& message) {
  FQName fqname = GetFQName(message);
  out << "sp<" << fqname.cppName() << "> " << kInstanceVariableName << ";\n";
}

void HalHidlCodeGen::GenerateRandomFunctionDeclForAttribute(Formatter& out,
    const VariableSpecificationMessage& attribute) {
  if (attribute.type() == TYPE_ENUM) {
    if (attribute.enum_value().enumerator_size() == 0) {
      // empty enum without any actual enumerator.
      return;
    }
    string attribute_name = ClearStringWithNameSpaceAccess(attribute.name());
    out << attribute.name() << " " << "Random" << attribute_name << "();\n";
  }
}

void HalHidlCodeGen::GenerateRandomFunctionImplForAttribute(Formatter& out,
    const VariableSpecificationMessage& attribute) {
  // Random value generator
  if (attribute.type() == TYPE_ENUM) {
    if (attribute.enum_value().enumerator_size() == 0) {
      // empty enum without any actual enumerator.
      return;
    }
    string attribute_name = ClearStringWithNameSpaceAccess(attribute.name());
    out << attribute.name() << " " << "Random" << attribute_name << "() {"
        << "\n";
    out.indent();
    out << attribute.enum_value().scalar_type() << " choice = " << "("
        << attribute.enum_value().scalar_type() << ") " << "rand() / "
        << attribute.enum_value().enumerator().size() << ";" << "\n";
    if (attribute.enum_value().scalar_type().find("u") != 0) {
      out << "if (choice < 0) choice *= -1;" << "\n";
    }
    for (int index = 0; index < attribute.enum_value().enumerator().size();
        index++) {
      out << "if (choice == ";
      out << "(" << attribute.enum_value().scalar_type() << ") ";
      if (attribute.enum_value().scalar_type() == "int8_t") {
        out << attribute.enum_value().scalar_value(index).int8_t();
      } else if (attribute.enum_value().scalar_type() == "uint8_t") {
        out << attribute.enum_value().scalar_value(index).uint8_t();
      } else if (attribute.enum_value().scalar_type() == "int16_t") {
        out << attribute.enum_value().scalar_value(index).int16_t();
      } else if (attribute.enum_value().scalar_type() == "uint16_t") {
        out << attribute.enum_value().scalar_value(index).uint16_t();
      } else if (attribute.enum_value().scalar_type() == "int32_t") {
        out << attribute.enum_value().scalar_value(index).int32_t();
      } else if (attribute.enum_value().scalar_type() == "uint32_t") {
        out << attribute.enum_value().scalar_value(index).uint32_t();
      } else if (attribute.enum_value().scalar_type() == "int64_t") {
        out << attribute.enum_value().scalar_value(index).int64_t();
      } else if (attribute.enum_value().scalar_type() == "uint64_t") {
        out << attribute.enum_value().scalar_value(index).uint64_t();
      } else {
        cerr << __func__ << ":" << __LINE__ << " ERROR unsupported enum type "
            << attribute.enum_value().scalar_type() << "\n";
        exit(-1);
      }
      out << ") return " << attribute.name() << "::"
          << attribute.enum_value().enumerator(index) << ";" << "\n";
    }
    out << "return " << attribute.name() << "::"
        << attribute.enum_value().enumerator(0) << ";" << "\n";
    out.unindent();
    out << "}" << "\n";
  }
}

void HalHidlCodeGen::GenerateDriverDeclForAttribute(Formatter& out,
    const VariableSpecificationMessage& attribute) {
  if (attribute.type() == TYPE_STRUCT || attribute.type() == TYPE_UNION) {
    // Recursively generate SetResult method implementation for all sub_types.
    for (const auto sub_struct : attribute.sub_struct()) {
      GenerateDriverDeclForAttribute(out, sub_struct);
    }
    for (const auto sub_union : attribute.sub_union()) {
      GenerateDriverDeclForAttribute(out, sub_union);
    }
    string func_name = "MessageTo"
        + ClearStringWithNameSpaceAccess(attribute.name());
    out << "void " << func_name
        << "(const VariableSpecificationMessage& var_msg, " << attribute.name()
        << "* arg);\n";
  } else if (attribute.type() == TYPE_ENUM) {
    string func_name = "EnumValue"
            + ClearStringWithNameSpaceAccess(attribute.name());
    // Message to value converter
    out << attribute.name() << " " << func_name
        << "(const ScalarDataValueMessage& arg);\n";
  } else {
    cerr << __func__ << " unsupported attribute type " << attribute.type()
         << "\n";
    exit(-1);
  }
}

void HalHidlCodeGen::GenerateDriverImplForAttribute(Formatter& out,
    const VariableSpecificationMessage& attribute) {
  switch (attribute.type()) {
    case TYPE_ENUM:
    {
      string func_name = "EnumValue"
          + ClearStringWithNameSpaceAccess(attribute.name());
      // Message to value converter
      out << attribute.name() << " " << func_name
          << "(const ScalarDataValueMessage& arg) {\n";
      out.indent();
      out << "return (" << attribute.name() << ") arg."
          << attribute.enum_value().scalar_type() << "();\n";
      out.unindent();
      out << "}" << "\n";
      break;
    }
    case TYPE_STRUCT:
    {
      // Recursively generate driver implementation method for all sub_types.
      for (const auto sub_struct : attribute.sub_struct()) {
        GenerateDriverImplForAttribute(out, sub_struct);
      }
      string func_name = "MessageTo"
          + ClearStringWithNameSpaceAccess(attribute.name());
      out << "void " << func_name
          << "(const VariableSpecificationMessage& var_msg, "
          << attribute.name() << "* arg) {" << "\n";
      out.indent();
      int struct_index = 0;
      for (const auto& struct_value : attribute.struct_value()) {
        GenerateDriverImplForTypedVariable(
            out, struct_value, "arg->" + struct_value.name(),
            "var_msg.struct_value(" + std::to_string(struct_index) + ")");
        struct_index++;
      }
      out.unindent();
      out << "}\n";
      break;
    }
    case TYPE_UNION:
    {
      // Recursively generate driver implementation method for all sub_types.
      for (const auto sub_union : attribute.sub_union()) {
        GenerateDriverImplForAttribute(out, sub_union);
      }
      string func_name = "MessageTo"
          + ClearStringWithNameSpaceAccess(attribute.name());
      out << "void " << func_name
          << "(const VariableSpecificationMessage& var_msg, "
          << attribute.name() << "* arg) {" << "\n";
      out.indent();
      int union_index = 0;
      for (const auto& union_value : attribute.union_value()) {
        out << "if (var_msg.union_value(" << union_index << ").name() == \""
            << union_value.name() << "\") {" << "\n";
        out.indent();
        GenerateDriverImplForTypedVariable(
            out, union_value, "arg->" + union_value.name(),
            "var_msg.union_value(" + std::to_string(union_index) + ")");
        union_index++;
        out.unindent();
        out << "}" << "\n";
      }
      out.unindent();
      out << "}\n";
      break;
    }
    default:
    {
      cerr << __func__ << " unsupported attribute type " << attribute.type()
           << "\n";
      exit(-1);
    }
  }
}

void HalHidlCodeGen::GenerateGetServiceImpl(Formatter& out,
    const ComponentSpecificationMessage& message,
    const string& fuzzer_extended_class_name) {
  out << "bool " << fuzzer_extended_class_name
      << "::GetService(bool get_stub, const char* service_name) {" << "\n";
  out.indent();
  out << "static bool initialized = false;" << "\n";
  out << "if (!initialized) {" << "\n";
  out.indent();
  out << "cout << \"[agent:hal] HIDL getService\" << endl;" << "\n";
  out << "if (service_name) {\n"
      << "  cout << \"  - service name: \" << service_name << endl;" << "\n"
      << "}\n";
  FQName fqname = GetFQName(message);
  out << kInstanceVariableName << " = " << fqname.cppName() << "::getService("
      << "service_name, get_stub);" << "\n";
  out << "cout << \"[agent:hal] " << kInstanceVariableName << " = \" << "
      << kInstanceVariableName << ".get() << endl;" << "\n";
  out << "initialized = true;" << "\n";
  out.unindent();
  out << "}" << "\n";
  out << "return true;" << "\n";
  out.unindent();
  out << "}" << "\n" << "\n";
}

void HalHidlCodeGen::GenerateDriverImplForTypedVariable(Formatter& out,
    const VariableSpecificationMessage& val, const string& arg_name,
    const string& arg_value_name) {
  switch (val.type()) {
    case TYPE_SCALAR:
    {
      out << arg_name << " = " << arg_value_name << ".scalar_value()."
          << val.scalar_type() << "();\n";
      break;
    }
    case TYPE_STRING:
    {
      out << arg_name << " = ::android::hardware::hidl_string("
          << arg_value_name << ".string_value().message());\n";
      break;
    }
    case TYPE_ENUM:
    {
      if (val.has_predefined_type()) {
        string func_name = "EnumValue"
            + ClearStringWithNameSpaceAccess(val.predefined_type());
        out << arg_name << " = " << func_name << "(" << arg_value_name
            << ".scalar_value());\n";
      } else {
        out << arg_name << " = (" << val.name() << ")" << arg_value_name << "."
            << "enum_value().scalar_value(0)." << val.enum_value().scalar_type()
            << "();\n";
      }
      break;
    }
    case TYPE_MASK:
    {
      out << arg_name << " = " << arg_value_name << ".scalar_value()."
          << val.scalar_type() << "();\n";
      break;
    }
    case TYPE_VECTOR:
    {
      out << arg_name << ".resize(" << arg_value_name
          << ".vector_value_size());\n";
      out << "for (int i = 0; i <" << arg_value_name
          << ".vector_value_size(); i++) {\n";
      out.indent();
      GenerateDriverImplForTypedVariable(out, val.vector_value(0),
                                         arg_name + "[i]",
                                         arg_value_name + ".vector_value(i)");
      out.unindent();
      out << "}\n";
      break;
    }
    case TYPE_ARRAY:
    {
      out << "for (int i = 0; i < " << arg_value_name
          << ".vector_value_size(); i++) {\n";
      out.indent();
      GenerateDriverImplForTypedVariable(out, val.vector_value(0),
                                         arg_name + "[i]",
                                         arg_value_name + ".vector_value(i)");
      out.unindent();
      out << "}\n";
      break;
    }
    case TYPE_STRUCT:
    {
      if (val.has_predefined_type()) {
        string func_name = "MessageTo"
            + ClearStringWithNameSpaceAccess(val.predefined_type());
        out << func_name << "(" << arg_value_name << ", &("
            << arg_name << "));\n";
      } else {
        int struct_index = 0;
        for (const auto struct_field : val.struct_value()) {
          string struct_field_name = arg_name + "." + struct_field.name();
          string struct_field_value_name = arg_value_name + ".struct_value("
              + std::to_string(struct_index) + ")";
          GenerateDriverImplForTypedVariable(out, struct_field,
                                             struct_field_name,
                                             struct_field_value_name);
          struct_index++;
        }
      }
      break;
    }
    case TYPE_UNION:
    {
      if (val.has_predefined_type()) {
        string func_name = "MessageTo"
            + ClearStringWithNameSpaceAccess(val.predefined_type());
        out << func_name << "(" << arg_value_name << ", &(" << arg_name
            << "));\n";
      } else {
        int union_index = 0;
        for (const auto union_field : val.union_value()) {
          string union_field_name = arg_name + "." + union_field.name();
          string union_field_value_name = arg_value_name + ".union_value("
              + std::to_string(union_index) + ")";
          GenerateDriverImplForTypedVariable(out, union_field, union_field_name,
                                             union_field_value_name);
          union_index++;
        }
      }
      break;
    }
    case TYPE_HIDL_CALLBACK:
    {
      string type_name = val.predefined_type();
      ReplaceSubString(type_name, "::", "_");

      out << arg_name << " = VtsFuzzerCreateVts" << type_name
          << "(callback_socket_name);\n";
      out << "static_cast<" << "Vts" + type_name << "*>(" << arg_name
          << ".get())->Register(" << arg_value_name << ");\n";
      break;
    }
    case TYPE_HANDLE:
    {
      out << "/* ERROR: TYPE_HANDLE is not supported yet. */\n";
      break;
    }
    case TYPE_HIDL_INTERFACE:
    {
      out << "/* ERROR: TYPE_HIDL_INTERFACE is not supported yet. */\n";
      break;
    }
    case TYPE_HIDL_MEMORY:
    {
      out << "/* ERROR: TYPE_HIDL_MEMORY is not supported yet. */\n";
      break;
    }
    case TYPE_POINTER:
    {
      out << "/* ERROR: TYPE_POINTER is not supported yet. */\n";
      break;
    }
    case TYPE_FMQ_SYNC:
    {
      out << "/* ERROR: TYPE_FMQ_SYNC is not supported yet. */\n";
      break;
    }
    case TYPE_FMQ_UNSYNC:
    {
      out << "/* ERROR: TYPE_FMQ_UNSYNC is not supported yet. */\n";
      break;
    }
    case TYPE_REF:
    {
      out << "/* ERROR: TYPE_REF is not supported yet. */\n";
      break;
    }
    default:
    {
      cerr << __func__ << " ERROR: unsupported type " << val.type() << ".\n";
      exit(-1);
    }
  }
}

// TODO(zhuoyao): Verify results based on verification rules instead of perform
// an exact match.
void HalHidlCodeGen::GenerateVerificationFunctionImpl(Formatter& out,
    const ComponentSpecificationMessage& message,
    const string& fuzzer_extended_class_name) {
  if (message.component_name() != "types"
      && !endsWith(message.component_name(), "Callback")) {
    // Generate the main profiler function.
    out << "\nbool " << fuzzer_extended_class_name
        << "::VerifyResults(const FunctionSpecificationMessage& expected_result, "
        << "const FunctionSpecificationMessage& actual_result) {\n";
    out.indent();
    for (const FunctionSpecificationMessage api : message.interface().api()) {
      out << "if (!strcmp(actual_result.name().c_str(), \"" << api.name()
          << "\")) {\n";
      out.indent();
      out << "if (actual_result.return_type_hidl_size() != "
          << "expected_result.return_type_hidl_size() "
          << ") { return false; }\n";
      for (int i = 0; i < api.return_type_hidl_size(); i++) {
        std::string expected_result = "expected_result.return_type_hidl("
            + std::to_string(i) + ")";
        std::string actual_result = "actual_result.return_type_hidl("
            + std::to_string(i) + ")";
        GenerateVerificationCodeForTypedVariable(out, api.return_type_hidl(i),
                                                 expected_result,
                                                 actual_result);
      }
      out << "return true;\n";
      out.unindent();
      out << "}\n";
    }
    out << "return false;\n";
    out.unindent();
    out << "}\n\n";
  }
}

void HalHidlCodeGen::GenerateVerificationCodeForTypedVariable(Formatter& out,
    const VariableSpecificationMessage& val, const string& expected_result,
    const string& actual_result) {
  switch (val.type()) {
    case TYPE_SCALAR:
    {
      out << "if (" << actual_result << ".scalar_value()." << val.scalar_type()
          << "() != " << expected_result << ".scalar_value()."
          << val.scalar_type() << "()) { return false; }\n";
      break;
    }
    case TYPE_STRING:
    {
      out << "if (strcmp(" << actual_result
          << ".string_value().message().c_str(), " << expected_result
          << ".string_value().message().c_str())!= 0)" << "{ return false; }\n";
      break;
    }
    case TYPE_ENUM:
    {
      if (val.has_predefined_type()) {
        string func_name = "Verify"
            + ClearStringWithNameSpaceAccess(val.predefined_type());
        out << "if(!" << func_name << "(" << expected_result << ", "
            << actual_result << ")) { return false; }\n";
      } else {
        out << "if (" << actual_result << ".scalar_value()."
            << val.enum_value().scalar_type() << "() != " << expected_result
            << ".scalar_value()." << val.enum_value().scalar_type()
            << "()) { return false; }\n";
      }
      break;
    }
    case TYPE_MASK:
    {
      out << "if (" << actual_result << ".scalar_value()." << val.scalar_type()
          << "() != " << expected_result << ".scalar_value()."
          << val.scalar_type() << "()) { return false; }\n";
      break;
    }
    case TYPE_VECTOR:
    {
      out << "if (" << actual_result << ".vector_value_size() != "
          << expected_result << ".vector_value_size()) {\n";
      out.indent();
      out << "cerr << \"Verification failed for vector size. expected: \" << "
             << expected_result << ".vector_value_size() << \" actual: \" << "
             << actual_result << ".vector_value_size();\n";
      out << "return false;\n";
      out.unindent();
      out << "}\n";
      out << "for (int i = 0; i <" << expected_result
          << ".vector_value_size(); i++) {\n";
      out.indent();
      GenerateVerificationCodeForTypedVariable(
          out, val.vector_value(0), expected_result + ".vector_value(i)",
          actual_result + ".vector_value(i)");
      out.unindent();
      out << "}\n";
      break;
    }
    case TYPE_ARRAY:
    {
      out << "if (" << actual_result << ".vector_value_size() != "
          << expected_result << ".vector_value_size()) {\n";
      out.indent();
      out << "cerr << \"Verification failed for vector size. expected: \" << "
          << expected_result << ".vector_value_size() << \" actual: \" << "
          << actual_result << ".vector_value_size();\n";
      out << "return false;\n";
      out.unindent();
      out << "}\n";
      out << "for (int i = 0; i < " << expected_result
          << ".vector_value_size(); i++) {\n";
      out.indent();
      GenerateVerificationCodeForTypedVariable(
          out, val.vector_value(0), expected_result + ".vector_value(i)",
          actual_result + ".vector_value(i)");
      out.unindent();
      out << "}\n";
      break;
    }
    case TYPE_STRUCT:
    {
      if (val.has_predefined_type()) {
        string func_name = "Verify"
            + ClearStringWithNameSpaceAccess(val.predefined_type());
        out << "if (!" << func_name << "(" << expected_result << ", "
            << actual_result << ")) { return false; }\n";
      } else {
        for (int i = 0; i < val.struct_value_size(); i++) {
          string struct_field_actual_result = actual_result + ".struct_value("
              + std::to_string(i) + ")";
          string struct_field_expected_result = expected_result
              + ".struct_value(" + std::to_string(i) + ")";
          GenerateVerificationCodeForTypedVariable(out, val.struct_value(i),
                                                   struct_field_expected_result,
                                                   struct_field_actual_result);
        }
      }
      break;
    }
    case TYPE_UNION:
    {
      if (val.has_predefined_type()) {
        string func_name = "Verify"
            + ClearStringWithNameSpaceAccess(val.predefined_type());
        out << "if (!" << func_name << "(" << expected_result << ", "
            << actual_result << ")) {return false; }\n";
      } else {
        for (int i = 0; i < val.union_value_size(); i++) {
          string union_field_actual_result = actual_result + ".union_value("
              + std::to_string(i) + ")";
          string union_field_expected_result = expected_result + ".union_value("
              + std::to_string(i) + ")";
          GenerateVerificationCodeForTypedVariable(out, val.union_value(i),
                                                   union_field_expected_result,
                                                   union_field_actual_result);
        }
      }
      break;
    }
    case TYPE_HIDL_CALLBACK:
    {
      out << "/* ERROR: TYPE_HIDL_CALLBACK is not supported yet. */\n";
      break;
    }
    case TYPE_HANDLE:
    {
      out << "/* ERROR: TYPE_HANDLE is not supported yet. */\n";
      break;
    }
    case TYPE_HIDL_INTERFACE:
    {
      out << "/* ERROR: TYPE_HIDL_INTERFACE is not supported yet. */\n";
      break;
    }
    case TYPE_HIDL_MEMORY:
    {
      out << "/* ERROR: TYPE_HIDL_MEMORY is not supported yet. */\n";
      break;
    }
    case TYPE_POINTER:
    {
      out << "/* ERROR: TYPE_POINTER is not supported yet. */\n";
      break;
    }
    case TYPE_FMQ_SYNC:
    {
      out << "/* ERROR: TYPE_FMQ_SYNC is not supported yet. */\n";
      break;
    }
    case TYPE_FMQ_UNSYNC:
    {
      out << "/* ERROR: TYPE_FMQ_UNSYNC is not supported yet. */\n";
      break;
    }
    case TYPE_REF:
    {
      out << "/* ERROR: TYPE_REF is not supported yet. */\n";
      break;
    }
    default:
    {
      cerr << __func__ << " ERROR: unsupported type " << val.type() << ".\n";
      exit(-1);
    }
  }
}

void HalHidlCodeGen::GenerateVerificationDeclForAttribute(Formatter& out,
    const VariableSpecificationMessage& attribute) {
  if (attribute.type() == TYPE_STRUCT || attribute.type() == TYPE_UNION) {
    // Recursively generate verification method implementation for all sub_types.
    for (const auto sub_struct : attribute.sub_struct()) {
      GenerateVerificationDeclForAttribute(out, sub_struct);
    }
    for (const auto sub_union : attribute.sub_union()) {
      GenerateVerificationDeclForAttribute(out, sub_union);
    }
  }
  std::string func_name = "bool Verify"
      + ClearStringWithNameSpaceAccess(attribute.name());
  out << func_name << "(const VariableSpecificationMessage& expected_result, "
      << "const VariableSpecificationMessage& actual_result);\n";
}

void HalHidlCodeGen::GenerateVerificationImplForAttribute(Formatter& out,
    const VariableSpecificationMessage& attribute) {
  if (attribute.type() == TYPE_STRUCT || attribute.type() == TYPE_UNION) {
    // Recursively generate verification method implementation for all sub_types.
    for (const auto sub_struct : attribute.sub_struct()) {
      GenerateVerificationImplForAttribute(out, sub_struct);
    }
    for (const auto sub_union : attribute.sub_union()) {
      GenerateVerificationImplForAttribute(out, sub_union);
    }
  }
  std::string func_name = "bool Verify"
      + ClearStringWithNameSpaceAccess(attribute.name());
  out << func_name << "(const VariableSpecificationMessage& expected_result, "
      << "const VariableSpecificationMessage& actual_result){\n";
  out.indent();
  GenerateVerificationCodeForTypedVariable(out, attribute, "expected_result",
                                           "actual_result");
  out << "return true;\n";
  out.unindent();
  out << "}\n\n";
}

// TODO(zhuoyao): consider to generalize the pattern for
// Verification/SetResult/DriverImpl.
void HalHidlCodeGen::GenerateSetResultCodeForTypedVariable(Formatter& out,
    const VariableSpecificationMessage& val, const string& result_msg,
    const string& result_value) {
  switch (val.type()) {
    case TYPE_SCALAR:
    {
      out << result_msg << "->set_type(TYPE_SCALAR);\n";
      out << result_msg << "->set_scalar_type(\"" << val.scalar_type()
          << "\");\n";
      out << result_msg << "->mutable_scalar_value()->set_" << val.scalar_type()
          << "(" << result_value << ");\n";
      break;
    }
    case TYPE_STRING:
    {
      out << result_msg << "->set_type(TYPE_STRING);\n";
      out << result_msg << "->mutable_string_value()->set_message" << "("
          << result_value << ".c_str());\n";
      out << result_msg << "->mutable_string_value()->set_length" << "("
          << result_value << ".size());\n";
      break;
    }
    case TYPE_ENUM:
    {
      out << result_msg << "->set_type(TYPE_ENUM);\n";
      if (val.has_predefined_type()) {
        string func_name = "SetResult"
            + ClearStringWithNameSpaceAccess(val.predefined_type());
        out << func_name << "(" << result_msg << ", " << result_value << ");\n";
      } else {
        const string scalar_type = val.enum_value().scalar_type();
        out << result_msg << "->set_scalar_type(\"" << scalar_type << "\");\n";
        out << result_msg << "->mutable_scalar_value()->set_" << scalar_type
            << "(static_cast<" << scalar_type << ">(" << result_value
            << "));\n";
      }
      break;
    }
    case TYPE_MASK:
    {
      out << result_msg << "->set_type(TYPE_MASK);\n";
      out << result_msg << "->set_scalar_type(\"" << val.scalar_type()
          << "\");\n";
      out << result_msg << "->mutable_scalar_value()->set_" << val.scalar_type()
          << "(" << result_value << ");\n";
      break;
    }
    case TYPE_VECTOR:
    {
      out << result_msg << "->set_type(TYPE_VECTOR);\n";
      out << result_msg << "->set_vector_size(" << result_value
          << ".size());\n";
      out << "for (int i = 0; i < (int)" << result_value << ".size(); i++) {\n";
      out.indent();
      string vector_element_name = result_msg + "_vector_i";
      out << "auto *" << vector_element_name << " = " << result_msg
          << "->add_vector_value();\n";
      GenerateSetResultCodeForTypedVariable(out, val.vector_value(0),
                                            vector_element_name,
                                            result_value + "[i]");
      out.unindent();
      out << "}\n";
      break;
    }
    case TYPE_ARRAY:
    {
      out << result_msg << "->set_type(TYPE_ARRAY);\n";
      out << result_msg << "->set_vector_size(" << val.vector_value_size()
          << ");\n";
      out << "for (int i = 0; i < " << val.vector_value_size() << "; i++) {\n";
      out.indent();
      string array_element_name = result_msg + "_array_i";
      out << "auto *" << array_element_name << " = " << result_msg
          << "->add_vector_value();\n";
      GenerateSetResultCodeForTypedVariable(out, val.vector_value(0),
                                            array_element_name,
                                            result_value + "[i]");
      out.unindent();
      out << "}\n";
      break;
    }
    case TYPE_STRUCT:
    {
      out << result_msg << "->set_type(TYPE_STRUCT);\n";
      if (val.has_predefined_type()) {
        string func_name = "SetResult"
            + ClearStringWithNameSpaceAccess(val.predefined_type());
        out << func_name << "(" << result_msg << ", " << result_value << ");\n";
      } else {
        for (const auto struct_field : val.struct_value()) {
          string struct_field_name = result_msg + "_" + struct_field.name();
          out << "auto *" << struct_field_name << " = " << result_msg
              << "->add_struct_value();\n";
          GenerateSetResultCodeForTypedVariable(
              out, struct_field, struct_field_name,
              result_value + "." + struct_field.name());
          if (struct_field.has_name()) {
            out << struct_field_name << "->set_name(\""
                << struct_field.name() << "\");\n";
          }
        }
      }
      break;
    }
    case TYPE_UNION:
    {
      out << result_msg << "->set_type(TYPE_UNION);\n";
      if (val.has_predefined_type()) {
        string func_name = "SetResult"
            + ClearStringWithNameSpaceAccess(val.predefined_type());
        out << func_name << "(" << result_msg << ", " << result_value << ");\n";
      } else {
        for (const auto union_field : val.union_value()) {
          string union_field_name = result_msg + "_" + union_field.name();
          out << "auto *" << union_field_name << " = " << result_msg
              << "->add_union_value();\n";
          GenerateSetResultCodeForTypedVariable(
              out, union_field, union_field_name,
              result_value + "." + union_field.name());
        }
      }
      break;
    }
    case TYPE_HIDL_CALLBACK:
    {
      out << result_msg << "->set_type(TYPE_HIDL_CALLBACK);\n";
      out << "/* ERROR: TYPE_HIDL_CALLBACK is not supported yet. */\n";
      break;
    }
    case TYPE_HANDLE:
    {
      out << result_msg << "->set_type(TYPE_HANDLE);\n";
      out << "/* ERROR: TYPE_HANDLE is not supported yet. */\n";
      break;
    }
    case TYPE_HIDL_INTERFACE:
    {
      out << result_msg << "->set_type(TYPE_HIDL_INTERFACE);\n";
      out << "/* ERROR: TYPE_HIDL_INTERFACE is not supported yet. */\n";
      break;
    }
    case TYPE_HIDL_MEMORY:
    {
      out << result_msg << "->set_type(TYPE_HIDL_MEMORY);\n";
      out << "/* ERROR: TYPE_HIDL_MEMORY is not supported yet. */\n";
      break;
    }
    case TYPE_POINTER:
    {
      out << result_msg << "->set_type(TYPE_POINTER);\n";
      out << "/* ERROR: TYPE_POINTER is not supported yet. */\n";
      break;
    }
    case TYPE_FMQ_SYNC:
    {
      out << result_msg << "->set_type(TYPE_FMQ_SYNC);\n";
      out << "/* ERROR: TYPE_FMQ_SYNC is not supported yet. */\n";
      break;
    }
    case TYPE_FMQ_UNSYNC:
    {
      out << result_msg << "->set_type(TYPE_FMQ_UNSYNC);\n";
      out << "/* ERROR: TYPE_FMQ_UNSYNC is not supported yet. */\n";
      break;
    }
    case TYPE_REF:
    {
      out << result_msg << "->set_type(TYPE_REF);\n";
      out << "/* ERROR: TYPE_REF is not supported yet. */\n";
      break;
    }
    default:
    {
      cerr << __func__ << " ERROR: unsupported type " << val.type() << ".\n";
      exit(-1);
    }
  }
}

void HalHidlCodeGen::GenerateSetResultDeclForAttribute(Formatter& out,
    const VariableSpecificationMessage& attribute) {
  if (attribute.type() == TYPE_STRUCT || attribute.type() == TYPE_UNION) {
    // Recursively generate SetResult method implementation for all sub_types.
    for (const auto sub_struct : attribute.sub_struct()) {
      GenerateSetResultDeclForAttribute(out, sub_struct);
    }
    for (const auto sub_union : attribute.sub_union()) {
      GenerateSetResultDeclForAttribute(out, sub_union);
    }
  }
  string func_name = "void SetResult"
      + ClearStringWithNameSpaceAccess(attribute.name());
  out << func_name << "(VariableSpecificationMessage* result_msg, "
      << attribute.name() << " result_value);\n";
}

void HalHidlCodeGen::GenerateSetResultImplForAttribute(Formatter& out,
    const VariableSpecificationMessage& attribute) {
  if (attribute.type() == TYPE_STRUCT || attribute.type() == TYPE_UNION) {
    // Recursively generate SetResult method implementation for all sub_types.
    for (const auto sub_struct : attribute.sub_struct()) {
      GenerateSetResultImplForAttribute(out, sub_struct);
    }
    for (const auto sub_union : attribute.sub_union()) {
      GenerateSetResultImplForAttribute(out, sub_union);
    }
  }
  string func_name = "void SetResult"
      + ClearStringWithNameSpaceAccess(attribute.name());
  out << func_name << "(VariableSpecificationMessage* result_msg, "
      << attribute.name() << " result_value){\n";
  out.indent();
  GenerateSetResultCodeForTypedVariable(out, attribute, "result_msg",
                                        "result_value");
  out.unindent();
  out << "}\n\n";
}

void HalHidlCodeGen::GenerateAllFunctionDeclForAttribute(Formatter& out,
    const VariableSpecificationMessage& attribute) {
  GenerateDriverDeclForAttribute(out, attribute);
  GenerateRandomFunctionDeclForAttribute(out, attribute);
  GenerateVerificationDeclForAttribute(out, attribute);
  GenerateSetResultDeclForAttribute(out, attribute);
}

void HalHidlCodeGen::GenerateAllFunctionImplForAttribute(Formatter& out,
    const VariableSpecificationMessage& attribute) {
  GenerateDriverImplForAttribute(out, attribute);
  GenerateRandomFunctionImplForAttribute(out, attribute);
  GenerateVerificationImplForAttribute(out, attribute);
  GenerateSetResultImplForAttribute(out, attribute);
}

bool HalHidlCodeGen::CanElideCallback(
    const FunctionSpecificationMessage& func_msg) {
  // Can't elide callback for void or tuple-returning methods
  if (func_msg.return_type_hidl_size() != 1) {
    return false;
  }
  const VariableType& type = func_msg.return_type_hidl(0).type();
  if (type == TYPE_ARRAY || type == TYPE_VECTOR || type == TYPE_REF) {
    return false;
  }
  return isElidableType(type);
}

bool HalHidlCodeGen::isElidableType(const VariableType& type) {
  if (type == TYPE_SCALAR || type == TYPE_ENUM || type == TYPE_MASK
      || type == TYPE_POINTER || type == TYPE_HIDL_INTERFACE
      || type == TYPE_VOID) {
    return true;
  }
  return false;
}

bool HalHidlCodeGen::isConstType(const VariableType& type) {
  if (type == TYPE_ARRAY || type == TYPE_VECTOR || type == TYPE_REF) {
    return true;
  }
  if (isElidableType(type)) {
    return false;
  }
  return true;
}

}  // namespace vts
}  // namespace android
