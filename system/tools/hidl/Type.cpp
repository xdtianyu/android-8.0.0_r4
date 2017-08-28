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

#include "Type.h"

#include "Annotation.h"
#include "ScalarType.h"

#include <hidl-util/Formatter.h>
#include <android-base/logging.h>

namespace android {

Type::Type()
    : mAnnotations(nullptr) {
}

Type::~Type() {}

void Type::setAnnotations(std::vector<Annotation *> *annotations) {
    mAnnotations = annotations;
}

const std::vector<Annotation *> &Type::annotations() const {
    return *mAnnotations;
}

bool Type::isScope() const {
    return false;
}

bool Type::isInterface() const {
    return false;
}

bool Type::isScalar() const {
    return false;
}

bool Type::isString() const {
    return false;
}

bool Type::isEnum() const {
    return false;
}

bool Type::isBitField() const {
    return false;
}

bool Type::isHandle() const {
    return false;
}

bool Type::isTypeDef() const {
    return false;
}

bool Type::isBinder() const {
    return false;
}

bool Type::isNamedType() const {
    return false;
}

bool Type::isMemory() const {
    return false;
}

bool Type::isCompoundType() const {
    return false;
}

bool Type::isArray() const {
    return false;
}

bool Type::isVector() const {
    return false;
}

bool Type::isTemplatedType() const {
    return false;
}

bool Type::isPointer() const {
    return false;
}

std::string Type::typeName() const {
    return "";
}

const ScalarType *Type::resolveToScalarType() const {
    return NULL;
}

bool Type::isValidEnumStorageType() const {
    const ScalarType *scalarType = resolveToScalarType();

    if (scalarType == NULL) {
        return false;
    }

    return scalarType->isValidEnumStorageType();
}

bool Type::isElidableType() const {
    return false;
}

bool Type::canCheckEquality() const {
    return false;
}

std::string Type::getCppType(StorageMode, bool) const {
    CHECK(!"Should not be here");
    return std::string();
}

std::string Type::decorateCppName(
        const std::string &name, StorageMode mode, bool specifyNamespaces) const {
    return getCppType(mode, specifyNamespaces) + " " + name;
}

std::string Type::getJavaType(bool /* forInitializer */) const {
    CHECK(!"Should not be here");
    return std::string();
}

std::string Type::getJavaWrapperType() const {
    return getJavaType();
}

std::string Type::getJavaSuffix() const {
    CHECK(!"Should not be here");
    return std::string();
}

std::string Type::getVtsType() const {
    CHECK(!"Should not be here");
    return std::string();
}

std::string Type::getVtsValueName() const {
    CHECK(!"Should not be here");
    return std::string();
}

void Type::emitReaderWriter(
        Formatter &,
        const std::string &,
        const std::string &,
        bool,
        bool,
        ErrorMode) const {
    CHECK(!"Should not be here");
}

void Type::emitResolveReferences(
        Formatter &,
        const std::string &,
        bool,
        const std::string &,
        bool,
        bool,
        ErrorMode) const {
    CHECK(!"Should not be here");
}

void Type::emitResolveReferencesEmbedded(
        Formatter &,
        size_t,
        const std::string &,
        const std::string &,
        bool,
        const std::string &,
        bool,
        bool,
        ErrorMode,
        const std::string &,
        const std::string &) const {
    CHECK(!"Should not be here");
}

void Type::emitDump(
        Formatter &out,
        const std::string &streamName,
        const std::string &name) const {
    emitDumpWithMethod(out, streamName, "::android::hardware::toString", name);
}

void Type::emitDumpWithMethod(
        Formatter &out,
        const std::string &streamName,
        const std::string &methodName,
        const std::string &name) const {
    out << streamName
        << " += "
        << methodName
        << "("
        << name
        << ");\n";
}

void Type::emitJavaDump(
        Formatter &out,
        const std::string &streamName,
        const std::string &name) const {
    out << streamName << ".append(" << name << ");\n";
}

bool Type::useParentInEmitResolveReferencesEmbedded() const {
    return needsResolveReferences();
}

bool Type::useNameInEmitReaderWriterEmbedded(bool) const {
    return needsEmbeddedReadWrite();
}

void Type::emitReaderWriterEmbedded(
        Formatter &,
        size_t,
        const std::string &,
        const std::string &,
        bool,
        const std::string &,
        bool,
        bool,
        ErrorMode,
        const std::string &,
        const std::string &) const {
    CHECK(!"Should not be here");
}

void Type::emitJavaReaderWriter(
        Formatter &out,
        const std::string &parcelObj,
        const std::string &argName,
        bool isReader) const {
    emitJavaReaderWriterWithSuffix(
            out,
            parcelObj,
            argName,
            isReader,
            getJavaSuffix(),
            "" /* extra */);
}

void Type::emitJavaFieldInitializer(
        Formatter &out,
        const std::string &fieldName) const {
    out << getJavaType()
        << " "
        << fieldName
        << ";\n";
}

void Type::emitJavaFieldReaderWriter(
        Formatter &,
        size_t,
        const std::string &,
        const std::string &,
        const std::string &,
        const std::string &,
        bool) const {
    CHECK(!"Should not be here");
}

void Type::handleError(Formatter &out, ErrorMode mode) const {
    switch (mode) {
        case ErrorMode_Ignore:
        {
            out << "/* _hidl_err ignored! */\n\n";
            break;
        }

        case ErrorMode_Goto:
        {
            out << "if (_hidl_err != ::android::OK) { goto _hidl_error; }\n\n";
            break;
        }

        case ErrorMode_Break:
        {
            out << "if (_hidl_err != ::android::OK) { break; }\n\n";
            break;
        }

        case ErrorMode_Return:
        {
            out << "if (_hidl_err != ::android::OK) { return _hidl_err; }\n\n";
            break;
        }
    }
}

void Type::emitReaderWriterEmbeddedForTypeName(
        Formatter &out,
        const std::string &name,
        bool nameIsPointer,
        const std::string &parcelObj,
        bool parcelObjIsPointer,
        bool isReader,
        ErrorMode mode,
        const std::string &parentName,
        const std::string &offsetText,
        const std::string &typeName,
        const std::string &childName,
        const std::string &funcNamespace) const {

        const std::string parcelObjDeref =
        parcelObjIsPointer ? ("*" + parcelObj) : parcelObj;

    const std::string parcelObjPointer =
        parcelObjIsPointer ? parcelObj : ("&" + parcelObj);

    const std::string nameDerefed = nameIsPointer ? ("*" + name) : name;
    const std::string namePointer = nameIsPointer ? name : ("&" + name);

    out << "_hidl_err = ";

    if (!funcNamespace.empty()) {
        out << funcNamespace << "::";
    }

    out << (isReader ? "readEmbeddedFromParcel(\n" : "writeEmbeddedToParcel(\n");

    out.indent();
    out.indent();

    if (isReader) {
        out << "const_cast<"
            << typeName
            << " &>("
            << nameDerefed
            << "),\n";
    } else {
        out << nameDerefed
            << ",\n";
    }

    out << (isReader ? parcelObjDeref : parcelObjPointer)
        << ",\n"
        << parentName
        << ",\n"
        << offsetText;

    if (!childName.empty()) {
        out << ", &"
            << childName;
    }

    out << ");\n\n";

    out.unindent();
    out.unindent();

    handleError(out, mode);
}

status_t Type::emitTypeDeclarations(Formatter &) const {
    return OK;
}

status_t Type::emitGlobalTypeDeclarations(Formatter &) const {
    return OK;
}

status_t Type::emitGlobalHwDeclarations(Formatter &) const {
    return OK;
}

status_t Type::emitTypeDefinitions(
        Formatter &, const std::string) const {
    return OK;
}

status_t Type::emitJavaTypeDeclarations(Formatter &, bool) const {
    return OK;
}

bool Type::needsEmbeddedReadWrite() const {
    return false;
}

bool Type::needsResolveReferences() const {
    return false;
}

bool Type::resultNeedsDeref() const {
    return false;
}

std::string Type::getCppStackType(bool specifyNamespaces) const {
    return getCppType(StorageMode_Stack, specifyNamespaces);
}

std::string Type::getCppResultType(bool specifyNamespaces) const {
    return getCppType(StorageMode_Result, specifyNamespaces);
}

std::string Type::getCppArgumentType(bool specifyNamespaces) const {
    return getCppType(StorageMode_Argument, specifyNamespaces);
}

void Type::emitJavaReaderWriterWithSuffix(
        Formatter &out,
        const std::string &parcelObj,
        const std::string &argName,
        bool isReader,
        const std::string &suffix,
        const std::string &extra) const {
    out << parcelObj
        << "."
        << (isReader ? "read" : "write")
        << suffix
        << "(";

    if (isReader) {
        out << extra;
    } else {
        out << (extra.empty() ? "" : (extra + ", "));
        out << argName;
    }

    out << ");\n";
}

status_t Type::emitVtsTypeDeclarations(Formatter &) const {
    return OK;
}

status_t Type::emitVtsAttributeType(Formatter &out) const {
    return emitVtsTypeDeclarations(out);
}

bool Type::isJavaCompatible() const {
    return true;
}

void Type::getAlignmentAndSize(
        size_t * /* align */, size_t * /* size */) const {
    CHECK(!"Should not be here.");
}

bool Type::containsPointer() const {
    return false;
}

void Type::appendToExportedTypesVector(
        std::vector<const Type *> * /* exportedTypes */) const {
}

status_t Type::emitExportedHeader(
        Formatter & /* out */, bool /* forJava */) const {
    return OK;
}

////////////////////////////////////////

TemplatedType::TemplatedType() : mElementType(nullptr) {
}

void TemplatedType::setElementType(Type *elementType) {
    CHECK(mElementType == nullptr); // can only be set once.
    CHECK(isCompatibleElementType(elementType));
    mElementType = elementType;
}

Type *TemplatedType::getElementType() const {
    return mElementType;
}

bool TemplatedType::isTemplatedType() const {
    return true;
}

status_t TemplatedType::emitVtsTypeDeclarations(Formatter &out) const {
    out << "type: " << getVtsType() << "\n";
    out << getVtsValueName() << ": {\n";
    out.indent();
    status_t err = mElementType->emitVtsTypeDeclarations(out);
    if (err != OK) {
        return err;
    }
    out.unindent();
    out << "}\n";
    return OK;
}

status_t TemplatedType::emitVtsAttributeType(Formatter &out) const {
    out << "type: " << getVtsType() << "\n";
    out << getVtsValueName() << ": {\n";
    out.indent();
    status_t status = mElementType->emitVtsAttributeType(out);
    if (status != OK) {
        return status;
    }
    out.unindent();
    out << "}\n";
    return OK;
}
}  // namespace android

