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

#include "HalHidlProfilerCodeGen.h"
#include "utils/InterfaceSpecUtil.h"
#include "utils/StringUtil.h"
#include "VtsCompilerUtils.h"

namespace android {
namespace vts {

void HalHidlProfilerCodeGen::GenerateProfilerForScalarVariable(Formatter& out,
  const VariableSpecificationMessage& val, const std::string& arg_name,
  const std::string& arg_value) {
  out << arg_name << "->set_type(TYPE_SCALAR);\n";
  out << arg_name << "->mutable_scalar_value()->set_" << val.scalar_type()
      << "(" << arg_value << ");\n";
}

void HalHidlProfilerCodeGen::GenerateProfilerForStringVariable(Formatter& out,
  const VariableSpecificationMessage&, const std::string& arg_name,
  const std::string& arg_value) {
  out << arg_name << "->set_type(TYPE_STRING);\n";
  out << arg_name << "->mutable_string_value()->set_message" << "(" << arg_value
      << ".c_str());\n";
  out << arg_name << "->mutable_string_value()->set_length" << "(" << arg_value
      << ".size());\n";
}

void HalHidlProfilerCodeGen::GenerateProfilerForEnumVariable(Formatter& out,
  const VariableSpecificationMessage& val, const std::string& arg_name,
  const std::string& arg_value) {
  out << arg_name << "->set_type(TYPE_ENUM);\n";

  // For predefined type, call the corresponding profile method.
  if (val.has_predefined_type()) {
    std::string predefined_type = val.predefined_type();
    ReplaceSubString(predefined_type, "::", "__");
    out << "profile__" << predefined_type << "(" << arg_name << ", "
        << arg_value << ");\n";
  } else {
    const std::string scalar_type = val.enum_value().scalar_type();
    out << arg_name << "->mutable_scalar_value()->set_" << scalar_type
        << "(static_cast<" << scalar_type << ">(" << arg_value << "));\n";
    out << arg_name << "->set_scalar_type(\"" << scalar_type << "\");\n";
  }
}

void HalHidlProfilerCodeGen::GenerateProfilerForVectorVariable(Formatter& out,
  const VariableSpecificationMessage& val, const std::string& arg_name,
  const std::string& arg_value) {
  out << arg_name << "->set_type(TYPE_VECTOR);\n";
  out << arg_name << "->set_vector_size(" << arg_value << ".size());\n";
  out << "for (int i = 0; i < (int)" << arg_value << ".size(); i++) {\n";
  out.indent();
  std::string vector_element_name = arg_name + "_vector_i";
  out << "auto *" << vector_element_name << " = " << arg_name
      << "->add_vector_value();\n";
  GenerateProfilerForTypedVariable(out, val.vector_value(0),
                                   vector_element_name, arg_value + "[i]");
  out.unindent();
  out << "}\n";
}

void HalHidlProfilerCodeGen::GenerateProfilerForArrayVariable(Formatter& out,
  const VariableSpecificationMessage& val, const std::string& arg_name,
  const std::string& arg_value) {
  out << arg_name << "->set_type(TYPE_ARRAY);\n";
  out << arg_name << "->set_vector_size(" << val.vector_size() << ");\n";
  out << "for (int i = 0; i < " << val.vector_size() << "; i++) {\n";
  out.indent();
  std::string array_element_name = arg_name + "_array_i";
  out << "auto *" << array_element_name << " = " << arg_name
      << "->add_vector_value();\n";
  GenerateProfilerForTypedVariable(out, val.vector_value(0), array_element_name,
                                   arg_value + "[i]");
  out.unindent();
  out << "}\n";
}

void HalHidlProfilerCodeGen::GenerateProfilerForStructVariable(Formatter& out,
  const VariableSpecificationMessage& val, const std::string& arg_name,
  const std::string& arg_value) {
  out << arg_name << "->set_type(TYPE_STRUCT);\n";
  // For predefined type, call the corresponding profile method.
  if (val.struct_value().size() == 0 && val.has_predefined_type()) {
    std::string predefined_type = val.predefined_type();
    ReplaceSubString(predefined_type, "::", "__");
    out << "profile__" << predefined_type << "(" << arg_name << ", "
        << arg_value << ");\n";
  } else {
    for (const auto struct_field : val.struct_value()) {
      std::string struct_field_name = arg_name + "_" + struct_field.name();
      out << "auto *" << struct_field_name << " = " << arg_name
          << "->add_struct_value();\n";
      GenerateProfilerForTypedVariable(out, struct_field, struct_field_name,
                                       arg_value + "." + struct_field.name());
    }
  }
}

void HalHidlProfilerCodeGen::GenerateProfilerForUnionVariable(Formatter& out,
  const VariableSpecificationMessage& val, const std::string& arg_name,
  const std::string& arg_value) {
  out << arg_name << "->set_type(TYPE_UNION);\n";
  // For predefined type, call the corresponding profile method.
  if (val.union_value().size() == 0 && val.has_predefined_type()) {
    std::string predefined_type = val.predefined_type();
    ReplaceSubString(predefined_type, "::", "__");
    out << "profile__" << predefined_type << "(" << arg_name << ", "
        << arg_value << ");\n";
  } else {
    for (const auto union_field : val.union_value()) {
      std::string union_field_name = arg_name + "_" + union_field.name();
      out << "auto *" << union_field_name << " = " << arg_name
          << "->add_union_value();\n";
      GenerateProfilerForTypedVariable(out, union_field, union_field_name,
                                       arg_value + "." + union_field.name());
    }
  }
}

void HalHidlProfilerCodeGen::GenerateProfilerForHidlCallbackVariable(
  Formatter& out, const VariableSpecificationMessage&,
  const std::string& arg_name, const std::string&) {
  out << arg_name << "->set_type(TYPE_HIDL_CALLBACK);\n";
  // TODO(zhuoyao): figure the right way to profile hidl callback type.
}

void HalHidlProfilerCodeGen::GenerateProfilerForHidlInterfaceVariable(
  Formatter& out, const VariableSpecificationMessage&,
  const std::string& arg_name, const std::string&) {
  out << arg_name << "->set_type(TYPE_HIDL_INTERFACE);\n";
  // TODO(zhuoyao): figure the right way to profile hidl interface type.
}

void HalHidlProfilerCodeGen::GenerateProfilerForMaskVariable(Formatter& out,
    const VariableSpecificationMessage&, const std::string& arg_name,
    const std::string&) {
  out << arg_name << "->set_type(TYPE_MASK);\n";
  // TODO(zhuoyao): figure the right way to profile mask type.
}

void HalHidlProfilerCodeGen::GenerateProfilerForHidlMemoryVariable(
    Formatter& out, const VariableSpecificationMessage&,
    const std::string& arg_name, const std::string&) {
  out << arg_name << "->set_type(TYPE_HIDL_MEMORY);\n";
  // TODO(zhuoyao): figure the right way to profile hidl memory type.
}

void HalHidlProfilerCodeGen::GenerateProfilerForPointerVariable(Formatter& out,
    const VariableSpecificationMessage&, const std::string& arg_name,
    const std::string&) {
  out << arg_name << "->set_type(TYPE_POINTER);\n";
  // TODO(zhuoyao): figure the right way to profile pointer type.
}

void HalHidlProfilerCodeGen::GenerateProfilerForFMQSyncVariable(Formatter& out,
    const VariableSpecificationMessage&, const std::string& arg_name,
    const std::string&) {
  out << arg_name << "->set_type(TYPE_FMQ_SYNC);\n";
  // TODO(zhuoyao): figure the right way to profile fmq sync type.
}

void HalHidlProfilerCodeGen::GenerateProfilerForFMQUnsyncVariable(
    Formatter& out, const VariableSpecificationMessage&,
    const std::string& arg_name, const std::string&) {
  out << arg_name << "->set_type(TYPE_FMQ_UNSYNC);\n";
  // TODO(zhuoyao): figure the right way to profile fmq unsync type.
}

void HalHidlProfilerCodeGen::GenerateProfilerForMethod(Formatter& out,
  const FunctionSpecificationMessage& method) {
  out << "FunctionSpecificationMessage msg;\n";
  out << "msg.set_name(\"" << method.name() << "\");\n";
  out << "if (!args) {\n";
  out.indent();
  out << "LOG(WARNING) << \"no argument passed\";\n";
  out.unindent();
  out << "} else {\n";
  out.indent();
  out << "switch (event) {\n";
  out.indent();
  // TODO(b/32141398): Support profiling in passthrough mode.
  out << "case details::HidlInstrumentor::CLIENT_API_ENTRY:\n";
  out << "case details::HidlInstrumentor::SERVER_API_ENTRY:\n";
  out << "case details::HidlInstrumentor::PASSTHROUGH_ENTRY:\n";
  out << "{\n";
  out.indent();
  ComponentSpecificationMessage message;
  out << "if ((*args).size() != " <<  method.arg().size() << ") {\n";
  out.indent();
  out << "LOG(ERROR) << \"Number of arguments does not match. expect: "
      << method.arg().size()
      << ", actual: \" << (*args).size() << \", method name: "
      << method.name()
      << ", event type: \" << event;\n";
  out << "break;\n";
  out.unindent();
  out << "}\n";
  for (int i = 0; i < method.arg().size(); i++) {
    const VariableSpecificationMessage arg = method.arg(i);
    std::string arg_name = "arg_" + std::to_string(i);
    std::string arg_value = "arg_val_" + std::to_string(i);
    out << "auto *" << arg_name << " = msg.add_arg();\n";
    // TODO(zhuoyao): GetCppVariableType does not support array type for now.
    out << GetCppVariableType(arg, &message) << " *" << arg_value
        << " = reinterpret_cast<" << GetCppVariableType(arg, &message)
        << "*> ((*args)[" << i << "]);\n";
    GenerateProfilerForTypedVariable(out, arg, arg_name,
                                     "(*" + arg_value + ")");
  }
  out << "break;\n";
  out.unindent();
  out << "}\n";

  // TODO(b/32141398): Support profiling in passthrough mode.
  out << "case details::HidlInstrumentor::CLIENT_API_EXIT:\n";
  out << "case details::HidlInstrumentor::SERVER_API_EXIT:\n";
  out << "case details::HidlInstrumentor::PASSTHROUGH_EXIT:\n";
  out << "{\n";
  out.indent();
  out << "if ((*args).size() != " <<  method.return_type_hidl().size() << ") {\n";
  out.indent();
  out << "LOG(ERROR) << \"Number of return values does not match. expect: "
      << method.return_type_hidl().size()
      << ", actual: \" << (*args).size() << \", method name: "
      << method.name()
      << ", event type: \" << event;\n";
  out << "break;\n";
  out.unindent();
  out << "}\n";
  for (int i = 0; i < method.return_type_hidl().size(); i++) {
    const VariableSpecificationMessage arg = method.return_type_hidl(i);
    std::string result_name = "result_" + std::to_string(i);
    std::string result_value = "result_val_" + std::to_string(i);
    out << "auto *" << result_name << " = msg.add_return_type_hidl();\n";
    out << GetCppVariableType(arg, &message) << " *" << result_value
        << " = reinterpret_cast<" << GetCppVariableType(arg, &message)
        << "*> ((*args)[" << i << "]);\n";
    GenerateProfilerForTypedVariable(out, arg, result_name,
                                     "(*" + result_value + ")");
  }
  out << "break;\n";
  out.unindent();
  out << "}\n";
  out << "default:\n";
  out << "{\n";
  out.indent();
  out << "LOG(WARNING) << \"not supported. \";\n";
  out << "break;\n";
  out.unindent();
  out << "}\n";
  out.unindent();
  out << "}\n";
  out.unindent();
  out << "}\n";
  out << "profiler.AddTraceEvent(event, package, version, interface, msg);\n";
}

void HalHidlProfilerCodeGen::GenerateHeaderIncludeFiles(Formatter& out,
    const ComponentSpecificationMessage& message) {
  // Basic includes.
  out << "#include <android-base/logging.h>\n";
  out << "#include <hidl/HidlSupport.h>\n";
  out << "#include <linux/limits.h>\n";
  out << "#include <test/vts/proto/ComponentSpecificationMessage.pb.h>\n";
  out << "#include \"VtsProfilingInterface.h\"\n";
  out << "\n";

  std::string package_path = GetPackage(message);
  ReplaceSubString(package_path, ".", "/");

  // Include generated hal classes.
  out << "#include <" << package_path << "/" << GetPackageVersion(message)
      << "/" << GetComponentName(message) << ".h>\n";

  // Include imported classes.
  for (const auto& import : message.import()) {
    FQName import_name = FQName(import);
    string imported_package_name = import_name.package();
    string imported_package_version = import_name.version();
    string imported_component_name = import_name.name();
    string imported_package_path = imported_package_name;
    ReplaceSubString(imported_package_path, ".", "/");
    out << "#include <" << imported_package_path << "/"
        << imported_package_version << "/" << imported_component_name
        << ".h>\n";
    if (imported_package_name.find("android.hardware") != std::string::npos) {
      if (imported_component_name[0] == 'I') {
        imported_component_name = imported_component_name.substr(1);
      }
      out << "#include <" << imported_package_path << "/"
          << imported_package_version << "/" << imported_component_name
          << ".vts.h>\n";
    }
  }
  out << "\n\n";
}

void HalHidlProfilerCodeGen::GenerateSourceIncludeFiles(Formatter& out,
    const ComponentSpecificationMessage& /*message*/) {
  // Include the corresponding profiler header file.
  out << "#include \"" << input_vts_file_path_ << ".h\"\n";
  out << "\n";
}

void HalHidlProfilerCodeGen::GenerateUsingDeclaration(Formatter& out,
  const ComponentSpecificationMessage& message) {
  std::string package_path = GetPackage(message);
  ReplaceSubString(package_path, ".", "::");

  out << "using namespace ";
  out << package_path << "::"
      << GetVersionString(message.component_type_version(), true) << ";\n";
  out << "using namespace android::hardware;\n";
  out << "\n";
}

void HalHidlProfilerCodeGen::GenerateMacros(Formatter& out,
    const ComponentSpecificationMessage&) {
  out << "#define TRACEFILEPREFIX \"/data/local/tmp\"\n";
  out << "\n";
}

void HalHidlProfilerCodeGen::GenerateProfierSanityCheck(Formatter& out,
  const ComponentSpecificationMessage& message) {
  out << "if (strcmp(package, \"" << GetPackage(message) << "\") != 0) {\n";
  out.indent();
  out << "LOG(WARNING) << \"incorrect package.\";\n";
  out << "return;\n";
  out.unindent();
  out << "}\n";

  out << "if (strcmp(version, \"" << GetPackageVersion(message)
      << "\") != 0) {\n";
  out.indent();
  out << "LOG(WARNING) << \"incorrect version.\";\n";
  out << "return;\n";
  out.unindent();
  out << "}\n";

  out << "if (strcmp(interface, \"" << GetComponentName(message)
      << "\") != 0) {\n";
  out.indent();
  out << "LOG(WARNING) << \"incorrect interface.\";\n";
  out << "return;\n";
  out.unindent();
  out << "}\n";
  out << "\n";
}

void HalHidlProfilerCodeGen::GenerateLocalVariableDefinition(Formatter& out,
  const ComponentSpecificationMessage&) {
  // generate the name of file to store the trace.
  out << "char trace_file[PATH_MAX];\n";
  out << "sprintf(trace_file, \"%s/%s_%s\", TRACEFILEPREFIX, package, version);"
      << "\n";

  // create and initialize the VTS profiler interface.
  out << "VtsProfilingInterface& profiler = "
      << "VtsProfilingInterface::getInstance(trace_file);\n";
  out << "profiler.Init();\n";
  out << "\n";
}

}  // namespace vts
}  // namespace android
