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

#include "CompoundType.h"

#include "ArrayType.h"
#include "VectorType.h"
#include <hidl-util/Formatter.h>
#include <android-base/logging.h>

namespace android {

CompoundType::CompoundType(Style style, const char *localName, const Location &location)
    : Scope(localName, location),
      mStyle(style),
      mFields(NULL) {
}

CompoundType::Style CompoundType::style() const {
    return mStyle;
}

bool CompoundType::setFields(
        std::vector<CompoundField *> *fields, std::string *errorMsg) {
    mFields = fields;

    for (const auto &field : *fields) {
        const Type &type = field->type();

        if (type.isBinder()
                || (type.isVector()
                    && static_cast<const VectorType *>(
                        &type)->isVectorOfBinders())) {
            *errorMsg =
                "Structs/Unions must not contain references to interfaces.";

            return false;
        }

        if (mStyle == STYLE_UNION) {
            if (type.needsEmbeddedReadWrite()) {
                // Can't have those in a union.

                *errorMsg =
                    "Unions must not contain any types that need fixup.";

                return false;
            }
        }
    }

    return true;
}

bool CompoundType::isCompoundType() const {
    return true;
}

bool CompoundType::canCheckEquality() const {
    if (mStyle == STYLE_UNION) {
        return false;
    }
    for (const auto &field : *mFields) {
        if (!field->type().canCheckEquality()) {
            return false;
        }
    }
    return true;
}

std::string CompoundType::getCppType(
        StorageMode mode,
        bool specifyNamespaces) const {
    const std::string base =
        specifyNamespaces ? fullName() : partialCppName();

    switch (mode) {
        case StorageMode_Stack:
            return base;

        case StorageMode_Argument:
            return "const " + base + "&";

        case StorageMode_Result:
            return "const " + base + "*";
    }
}

std::string CompoundType::getJavaType(bool /* forInitializer */) const {
    return fullJavaName();
}

std::string CompoundType::getVtsType() const {
    switch (mStyle) {
        case STYLE_STRUCT:
        {
            return "TYPE_STRUCT";
        }
        case STYLE_UNION:
        {
            return "TYPE_UNION";
        }
    }
}

void CompoundType::emitReaderWriter(
        Formatter &out,
        const std::string &name,
        const std::string &parcelObj,
        bool parcelObjIsPointer,
        bool isReader,
        ErrorMode mode) const {
    const std::string parentName = "_hidl_" + name + "_parent";

    out << "size_t " << parentName << ";\n\n";

    const std::string parcelObjDeref =
        parcelObj + (parcelObjIsPointer ? "->" : ".");

    if (isReader) {
        out << "_hidl_err = "
            << parcelObjDeref
            << "readBuffer("
            << "sizeof(*"
            << name
            << "), &"
            << parentName
            << ", "
            << " reinterpret_cast<const void **>("
            << "&" << name
            << "));\n";

        handleError(out, mode);
    } else {
        out << "_hidl_err = "
            << parcelObjDeref
            << "writeBuffer(&"
            << name
            << ", sizeof("
            << name
            << "), &"
            << parentName
            << ");\n";

        handleError(out, mode);
    }

    if (mStyle != STYLE_STRUCT || !needsEmbeddedReadWrite()) {
        return;
    }

    emitReaderWriterEmbedded(
            out,
            0 /* depth */,
            name,
            name, /* sanitizedName */
            isReader /* nameIsPointer */,
            parcelObj,
            parcelObjIsPointer,
            isReader,
            mode,
            parentName,
            "0 /* parentOffset */");
}

void CompoundType::emitReaderWriterEmbedded(
        Formatter &out,
        size_t /* depth */,
        const std::string &name,
        const std::string & /*sanitizedName */,
        bool nameIsPointer,
        const std::string &parcelObj,
        bool parcelObjIsPointer,
        bool isReader,
        ErrorMode mode,
        const std::string &parentName,
        const std::string &offsetText) const {
    emitReaderWriterEmbeddedForTypeName(
            out,
            name,
            nameIsPointer,
            parcelObj,
            parcelObjIsPointer,
            isReader,
            mode,
            parentName,
            offsetText,
            fullName(),
            "" /* childName */,
            "" /* namespace */);
}

void CompoundType::emitJavaReaderWriter(
        Formatter &out,
        const std::string &parcelObj,
        const std::string &argName,
        bool isReader) const {
    if (isReader) {
        out << "new " << fullJavaName() << "();\n";
    }

    out << argName
        << "."
        << (isReader ? "readFromParcel" : "writeToParcel")
        << "("
        << parcelObj
        << ");\n";
}

void CompoundType::emitJavaFieldInitializer(
        Formatter &out, const std::string &fieldName) const {
    out << "final "
        << fullJavaName()
        << " "
        << fieldName
        << " = new "
        << fullJavaName()
        << "();\n";
}

void CompoundType::emitJavaFieldReaderWriter(
        Formatter &out,
        size_t /* depth */,
        const std::string &parcelName,
        const std::string &blobName,
        const std::string &fieldName,
        const std::string &offset,
        bool isReader) const {
    if (isReader) {
        out << fieldName
            << ".readEmbeddedFromParcel("
            << parcelName
            << ", "
            << blobName
            << ", "
            << offset
            << ");\n";

        return;
    }

    out << fieldName
        << ".writeEmbeddedToBlob("
        << blobName
        << ", "
        << offset
        << ");\n";
}
void CompoundType::emitResolveReferences(
            Formatter &out,
            const std::string &name,
            bool nameIsPointer,
            const std::string &parcelObj,
            bool parcelObjIsPointer,
            bool isReader,
            ErrorMode mode) const {
    emitResolveReferencesEmbedded(
        out,
        0 /* depth */,
        name,
        name /* sanitizedName */,
        nameIsPointer,
        parcelObj,
        parcelObjIsPointer,
        isReader,
        mode,
        "_hidl_" + name + "_parent",
        "0 /* parentOffset */");
}

void CompoundType::emitResolveReferencesEmbedded(
            Formatter &out,
            size_t /* depth */,
            const std::string &name,
            const std::string &/* sanitizedName */,
            bool nameIsPointer,
            const std::string &parcelObj,
            bool parcelObjIsPointer,
            bool isReader,
            ErrorMode mode,
            const std::string &parentName,
            const std::string &offsetText) const {
    CHECK(needsResolveReferences());

    const std::string parcelObjDeref =
        parcelObjIsPointer ? ("*" + parcelObj) : parcelObj;

    const std::string parcelObjPointer =
        parcelObjIsPointer ? parcelObj : ("&" + parcelObj);

    const std::string nameDerefed = nameIsPointer ? ("*" + name) : name;
    const std::string namePointer = nameIsPointer ? name : ("&" + name);

    out << "_hidl_err = ";

    if (isReader) {
        out << "readEmbeddedReferenceFromParcel(\n";
    } else {
        out << "writeEmbeddedReferenceToParcel(\n";
    }

    out.indent(2, [&]{
        if (isReader) {
            out << "const_cast<"
                << fullName()
                << " *"
                << ">("
                << namePointer
                << "),\n"
                << parcelObjDeref;
        } else {
            out << nameDerefed
                << ",\n"
                << parcelObjPointer;
        }

        out << ",\n"
            << parentName
            << ",\n"
            << offsetText
            << ");\n\n";
    });

    handleError(out, mode);
}

status_t CompoundType::emitTypeDeclarations(Formatter &out) const {
    out << ((mStyle == STYLE_STRUCT) ? "struct" : "union")
        << " "
        << localName()
        << " final {\n";

    out.indent();

    Scope::emitTypeDeclarations(out);

    if (containsPointer()) {
        for (const auto &field : *mFields) {
            out << field->type().getCppStackType()
                << " "
                << field->name()
                << ";\n";
        }

        out.unindent();
        out << "};\n\n";

        return OK;
    }

    for (int pass = 0; pass < 2; ++pass) {
        size_t offset = 0;
        for (const auto &field : *mFields) {
            size_t fieldAlign, fieldSize;
            field->type().getAlignmentAndSize(&fieldAlign, &fieldSize);

            size_t pad = offset % fieldAlign;
            if (pad > 0) {
                offset += fieldAlign - pad;
            }

            if (pass == 0) {
                out << field->type().getCppStackType()
                    << " "
                    << field->name()
                    << " __attribute__ ((aligned("
                    << fieldAlign
                    << ")));\n";
            } else {
                out << "static_assert(offsetof("
                    << fullName()
                    << ", "
                    << field->name()
                    << ") == "
                    << offset
                    << ", \"wrong offset\");\n";
            }

            if (mStyle == STYLE_STRUCT) {
                offset += fieldSize;
            }
        }

        if (pass == 0) {
            out.unindent();
            out << "};\n\n";
        }
    }

    size_t structAlign, structSize;
    getAlignmentAndSize(&structAlign, &structSize);

    out << "static_assert(sizeof("
        << fullName()
        << ") == "
        << structSize
        << ", \"wrong size\");\n";

    out << "static_assert(__alignof("
        << fullName()
        << ") == "
        << structAlign
        << ", \"wrong alignment\");\n\n";

    return OK;
}


status_t CompoundType::emitGlobalTypeDeclarations(Formatter &out) const {
    Scope::emitGlobalTypeDeclarations(out);

    out << "std::string toString("
        << getCppArgumentType()
        << ");\n\n";

    if (canCheckEquality()) {
        out << "bool operator==("
            << getCppArgumentType() << ", " << getCppArgumentType() << ");\n\n";

        out << "bool operator!=("
            << getCppArgumentType() << ", " << getCppArgumentType() << ");\n\n";
    } else {
        out << "// operator== and operator!= are not generated for " << localName() << "\n\n";
    }

    return OK;
}

status_t CompoundType::emitGlobalHwDeclarations(Formatter &out) const  {
    if (needsEmbeddedReadWrite()) {
        out << "::android::status_t readEmbeddedFromParcel(\n";

        out.indent(2);

        out << "const " << fullName() << " &obj,\n"
            << "const ::android::hardware::Parcel &parcel,\n"
            << "size_t parentHandle,\n"
            << "size_t parentOffset);\n\n";

        out.unindent(2);

        out << "::android::status_t writeEmbeddedToParcel(\n";

        out.indent(2);

        out << "const " << fullName() << " &obj,\n"
            << "::android::hardware::Parcel *parcel,\n"
            << "size_t parentHandle,\n"
            << "size_t parentOffset);\n\n";

        out.unindent(2);
    }

    if(needsResolveReferences()) {
        out << "::android::status_t readEmbeddedReferenceFromParcel(\n";
        out.indent(2);
        out << fullName() << " *obj,\n"
            << "const ::android::hardware::Parcel &parcel,\n"
            << "size_t parentHandle, size_t parentOffset);\n\n";
        out.unindent(2);
        out << "::android::status_t writeEmbeddedReferenceToParcel(\n";
        out.indent(2);
        out << "const " << fullName() << " &obj,\n"
            << "::android::hardware::Parcel *,\n"
            << "size_t parentHandle, size_t parentOffset);\n\n";
        out.unindent(2);
    }

    return OK;
}

status_t CompoundType::emitTypeDefinitions(
        Formatter &out, const std::string prefix) const {
    std::string space = prefix.empty() ? "" : (prefix + "::");
    status_t err = Scope::emitTypeDefinitions(out, space + localName());

    if (err != OK) {
        return err;
    }

    if (needsEmbeddedReadWrite()) {
        emitStructReaderWriter(out, prefix, true /* isReader */);
        emitStructReaderWriter(out, prefix, false /* isReader */);
    }

    if (needsResolveReferences()) {
        emitResolveReferenceDef(out, prefix, true /* isReader */);
        emitResolveReferenceDef(out, prefix, false /* isReader */);
    }

    out << "std::string toString("
        << getCppArgumentType()
        << (mFields->empty() ? "" : " o")
        << ") ";

    out.block([&] {
        // include toString for scalar types
        out << "using ::android::hardware::toString;\n"
            << "std::string os;\n";
        out << "os += \"{\";\n";

        for (const CompoundField *field : *mFields) {
            out << "os += \"";
            if (field != *(mFields->begin())) {
                out << ", ";
            }
            out << "." << field->name() << " = \";\n";
            field->type().emitDump(out, "os", "o." + field->name());
        }

        out << "os += \"}\"; return os;\n";
    }).endl().endl();

    if (canCheckEquality()) {
        out << "bool operator==("
            << getCppArgumentType() << " " << (mFields->empty() ? "/* lhs */" : "lhs") << ", "
            << getCppArgumentType() << " " << (mFields->empty() ? "/* rhs */" : "rhs") << ") ";
        out.block([&] {
            for (const auto &field : *mFields) {
                out.sIf("lhs." + field->name() + " != rhs." + field->name(), [&] {
                    out << "return false;\n";
                }).endl();
            }
            out << "return true;\n";
        }).endl().endl();

        out << "bool operator!=("
            << getCppArgumentType() << " lhs," << getCppArgumentType() << " rhs)";
        out.block([&] {
            out << "return !(lhs == rhs);\n";
        }).endl().endl();
    } else {
        out << "// operator== and operator!= are not generated for " << localName() << "\n";
    }

    return OK;
}

status_t CompoundType::emitJavaTypeDeclarations(
        Formatter &out, bool atTopLevel) const {
    out << "public final ";

    if (!atTopLevel) {
        out << "static ";
    }

    out << "class "
        << localName()
        << " {\n";

    out.indent();

    Scope::emitJavaTypeDeclarations(out, false /* atTopLevel */);

    for (const auto &field : *mFields) {
        out << "public ";

        field->type().emitJavaFieldInitializer(out, field->name());
    }

    if (!mFields->empty()) {
        out << "\n";
    }

    ////////////////////////////////////////////////////////////////////////////

    if (canCheckEquality()) {
        out << "@Override\npublic final boolean equals(Object otherObject) ";
        out.block([&] {
            out.sIf("this == otherObject", [&] {
                out << "return true;\n";
            }).endl();
            out.sIf("otherObject == null", [&] {
                out << "return false;\n";
            }).endl();
            // Though class is final, we use getClass instead of instanceof to be explicit.
            out.sIf("otherObject.getClass() != " + fullJavaName() + ".class", [&] {
                out << "return false;\n";
            }).endl();
            out << fullJavaName() << " other = (" << fullJavaName() << ")otherObject;\n";
            for (const auto &field : *mFields) {
                std::string condition = (field->type().isScalar() || field->type().isEnum())
                    ? "this." + field->name() + " != other." + field->name()
                    : ("!android.os.HidlSupport.deepEquals(this." + field->name()
                            + ", other." + field->name() + ")");
                out.sIf(condition, [&] {
                    out << "return false;\n";
                }).endl();
            }
            out << "return true;\n";
        }).endl().endl();

        out << "@Override\npublic final int hashCode() ";
        out.block([&] {
            out << "return java.util.Objects.hash(\n";
            out.indent(2, [&] {
                out.join(mFields->begin(), mFields->end(), ", \n", [&] (const auto &field) {
                    out << "android.os.HidlSupport.deepHashCode(this." << field->name() << ")";
                });
            });
            out << ");\n";
        }).endl().endl();
    } else {
        out << "// equals() is not generated for " << localName() << "\n";
    }

    ////////////////////////////////////////////////////////////////////////////

    out << "@Override\npublic final String toString() ";
    out.block([&] {
        out << "java.lang.StringBuilder builder = new java.lang.StringBuilder();\n"
            << "builder.append(\"{\");\n";
        for (const auto &field : *mFields) {
            out << "builder.append(\"";
            if (field != *(mFields->begin())) {
                out << ", ";
            }
            out << "." << field->name() << " = \");\n";
            field->type().emitJavaDump(out, "builder", "this." + field->name());
        }
        out << "builder.append(\"}\");\nreturn builder.toString();\n";
    }).endl().endl();

    size_t structAlign, structSize;
    getAlignmentAndSize(&structAlign, &structSize);

    ////////////////////////////////////////////////////////////////////////////

    out << "public final void readFromParcel(android.os.HwParcel parcel) {\n";
    out.indent();
    out << "android.os.HwBlob blob = parcel.readBuffer(";
    out << structSize << "/* size */);\n";
    out << "readEmbeddedFromParcel(parcel, blob, 0 /* parentOffset */);\n";
    out.unindent();
    out << "}\n\n";

    ////////////////////////////////////////////////////////////////////////////

    size_t vecAlign, vecSize;
    VectorType::getAlignmentAndSizeStatic(&vecAlign, &vecSize);

    out << "public static final java.util.ArrayList<"
        << localName()
        << "> readVectorFromParcel(android.os.HwParcel parcel) {\n";
    out.indent();

    out << "java.util.ArrayList<"
        << localName()
        << "> _hidl_vec = new java.util.ArrayList();\n";

    out << "android.os.HwBlob _hidl_blob = parcel.readBuffer(";
    out << vecSize << " /* sizeof hidl_vec<T> */);\n\n";

    VectorType::EmitJavaFieldReaderWriterForElementType(
            out,
            0 /* depth */,
            this,
            "parcel",
            "_hidl_blob",
            "_hidl_vec",
            "0",
            true /* isReader */);

    out << "\nreturn _hidl_vec;\n";

    out.unindent();
    out << "}\n\n";

    ////////////////////////////////////////////////////////////////////////////

    out << "public final void readEmbeddedFromParcel(\n";
    out.indent(2);
    out << "android.os.HwParcel parcel, android.os.HwBlob _hidl_blob, long _hidl_offset) {\n";
    out.unindent();

    size_t offset = 0;
    for (const auto &field : *mFields) {
        size_t fieldAlign, fieldSize;
        field->type().getAlignmentAndSize(&fieldAlign, &fieldSize);

        size_t pad = offset % fieldAlign;
        if (pad > 0) {
            offset += fieldAlign - pad;
        }

        field->type().emitJavaFieldReaderWriter(
                out,
                0 /* depth */,
                "parcel",
                "_hidl_blob",
                field->name(),
                "_hidl_offset + " + std::to_string(offset),
                true /* isReader */);

        offset += fieldSize;
    }

    out.unindent();
    out << "}\n\n";

    ////////////////////////////////////////////////////////////////////////////

    out << "public final void writeToParcel(android.os.HwParcel parcel) {\n";
    out.indent();

    out << "android.os.HwBlob _hidl_blob = new android.os.HwBlob("
        << structSize
        << " /* size */);\n";

    out << "writeEmbeddedToBlob(_hidl_blob, 0 /* parentOffset */);\n"
        << "parcel.writeBuffer(_hidl_blob);\n";

    out.unindent();
    out << "}\n\n";

    ////////////////////////////////////////////////////////////////////////////

    out << "public static final void writeVectorToParcel(\n";
    out.indent(2);
    out << "android.os.HwParcel parcel, java.util.ArrayList<"
        << localName()
        << "> _hidl_vec) {\n";
    out.unindent();

    out << "android.os.HwBlob _hidl_blob = new android.os.HwBlob("
        << vecSize << " /* sizeof(hidl_vec<T>) */);\n";

    VectorType::EmitJavaFieldReaderWriterForElementType(
            out,
            0 /* depth */,
            this,
            "parcel",
            "_hidl_blob",
            "_hidl_vec",
            "0",
            false /* isReader */);

    out << "\nparcel.writeBuffer(_hidl_blob);\n";

    out.unindent();
    out << "}\n\n";

    ////////////////////////////////////////////////////////////////////////////

    out << "public final void writeEmbeddedToBlob(\n";
    out.indent(2);
    out << "android.os.HwBlob _hidl_blob, long _hidl_offset) {\n";
    out.unindent();

    offset = 0;
    for (const auto &field : *mFields) {
        size_t fieldAlign, fieldSize;
        field->type().getAlignmentAndSize(&fieldAlign, &fieldSize);

        size_t pad = offset % fieldAlign;
        if (pad > 0) {
            offset += fieldAlign - pad;
        }

        field->type().emitJavaFieldReaderWriter(
                out,
                0 /* depth */,
                "parcel",
                "_hidl_blob",
                field->name(),
                "_hidl_offset + " + std::to_string(offset),
                false /* isReader */);

        offset += fieldSize;
    }

    out.unindent();
    out << "}\n";

    out.unindent();
    out << "};\n\n";

    return OK;
}

void CompoundType::emitStructReaderWriter(
        Formatter &out, const std::string &prefix, bool isReader) const {

    std::string space = prefix.empty() ? "" : (prefix + "::");

    out << "::android::status_t "
        << (isReader ? "readEmbeddedFromParcel"
                     : "writeEmbeddedToParcel")
        << "(\n";

    out.indent(2);

    bool useName = false;
    for (const auto &field : *mFields) {
        if (field->type().useNameInEmitReaderWriterEmbedded(isReader)) {
            useName = true;
            break;
        }
    }
    std::string name = useName ? "obj" : "/* obj */";
    // if not useName, then obj  should not be used at all,
    // then the #error should not be emitted.
    std::string error = useName ? "" : "\n#error\n";

    if (isReader) {
        out << "const " << space << localName() << " &" << name << ",\n";
        out << "const ::android::hardware::Parcel &parcel,\n";
    } else {
        out << "const " << space << localName() << " &" << name << ",\n";
        out << "::android::hardware::Parcel *parcel,\n";
    }

    out << "size_t parentHandle,\n"
        << "size_t parentOffset)";

    out << " {\n";

    out.unindent(2);
    out.indent();

    out << "::android::status_t _hidl_err = ::android::OK;\n\n";

    for (const auto &field : *mFields) {
        if (!field->type().needsEmbeddedReadWrite()) {
            continue;
        }

        field->type().emitReaderWriterEmbedded(
                out,
                0 /* depth */,
                name + "." + field->name() + error,
                field->name() /* sanitizedName */,
                false /* nameIsPointer */,
                "parcel",
                !isReader /* parcelObjIsPointer */,
                isReader,
                ErrorMode_Return,
                "parentHandle",
                "parentOffset + offsetof("
                    + fullName()
                    + ", "
                    + field->name()
                    + ")");
    }

    out << "return _hidl_err;\n";

    out.unindent();
    out << "}\n\n";
}

void CompoundType::emitResolveReferenceDef(
        Formatter &out, const std::string prefix, bool isReader) const {
    out << "::android::status_t ";
    const std::string space(prefix.empty() ? "" : (prefix + "::"));

    bool useParent = false;
    for (const auto &field : *mFields) {
        if (field->type().useParentInEmitResolveReferencesEmbedded()) {
            useParent = true;
            break;
        }
    }

    std::string parentHandleName = useParent ? "parentHandle" : "/* parentHandle */";
    std::string parentOffsetName = useParent ? "parentOffset" : "/* parentOffset */";

    if (isReader) {
        out << "readEmbeddedReferenceFromParcel(\n";
        out.indent(2);
        out << space + localName() + " *obj,\n"
            << "const ::android::hardware::Parcel &parcel,\n"
            << "size_t " << parentHandleName << ", "
            << "size_t " << parentOffsetName << ")\n";
        out.unindent(2);
    } else {
        out << "writeEmbeddedReferenceToParcel(\n";
        out.indent(2);
        out << "const " << space + localName() + " &obj,\n"
            << "::android::hardware::Parcel *parcel,\n"
            << "size_t " << parentHandleName << ", "
            << "size_t " << parentOffsetName << ")\n";
        out.unindent(2);
    }

    out << " {\n";

    out.indent();

    out << "::android::status_t _hidl_err = ::android::OK;\n\n";

    const std::string nameDeref(isReader ? "obj->" : "obj.");
    // if not useParent, then parentName and offsetText
    // should not be used at all, then the #error should not be emitted.
    std::string error = useParent ? "" : "\n#error\n";

    for (const auto &field : *mFields) {
        if (!field->type().needsResolveReferences()) {
            continue;
        }

        field->type().emitResolveReferencesEmbedded(
            out,
            0 /* depth */,
            nameDeref + field->name(),
            field->name() /* sanitizedName */,
            false,    // nameIsPointer
            "parcel", // const std::string &parcelObj,
            !isReader, // bool parcelObjIsPointer,
            isReader, // bool isReader,
            ErrorMode_Return,
            parentHandleName + error,
            parentOffsetName
                + " + offsetof("
                + fullName()
                + ", "
                + field->name()
                + ")"
                + error);
    }

    out << "return _hidl_err;\n";

    out.unindent();
    out << "}\n\n";
}

bool CompoundType::needsEmbeddedReadWrite() const {
    if (mStyle != STYLE_STRUCT) {
        return false;
    }

    for (const auto &field : *mFields) {
        if (field->type().needsEmbeddedReadWrite()) {
            return true;
        }
    }

    return false;
}

bool CompoundType::needsResolveReferences() const {
    if (mStyle != STYLE_STRUCT) {
        return false;
    }

    for (const auto &field : *mFields) {
        if (field->type().needsResolveReferences()) {
            return true;
        }
    }

    return false;
}

bool CompoundType::resultNeedsDeref() const {
    return true;
}

status_t CompoundType::emitVtsTypeDeclarations(Formatter &out) const {
    out << "name: \"" << fullName() << "\"\n";
    out << "type: " << getVtsType() << "\n";

    // Emit declaration for each subtype.
    for (const auto &type : getSubTypes()) {
        switch (mStyle) {
            case STYLE_STRUCT:
            {
                out << "sub_struct: {\n";
                break;
            }
            case STYLE_UNION:
            {
                out << "sub_union: {\n";
                break;
            }
        }
        out.indent();
        status_t status(type->emitVtsTypeDeclarations(out));
        if (status != OK) {
            return status;
        }
        out.unindent();
        out << "}\n";
    }

    // Emit declaration for each field.
    for (const auto &field : *mFields) {
        switch (mStyle) {
            case STYLE_STRUCT:
            {
                out << "struct_value: {\n";
                break;
            }
            case STYLE_UNION:
            {
                out << "union_value: {\n";
                break;
            }
        }
        out.indent();
        out << "name: \"" << field->name() << "\"\n";
        status_t status = field->type().emitVtsAttributeType(out);
        if (status != OK) {
            return status;
        }
        out.unindent();
        out << "}\n";
    }

    return OK;
}

status_t CompoundType::emitVtsAttributeType(Formatter &out) const {
    out << "type: " << getVtsType() << "\n";
    out << "predefined_type: \"" << fullName() << "\"\n";
    return OK;
}

bool CompoundType::isJavaCompatible() const {
    if (mStyle != STYLE_STRUCT || !Scope::isJavaCompatible()) {
        return false;
    }

    for (const auto &field : *mFields) {
        if (!field->type().isJavaCompatible()) {
            return false;
        }
    }

    return true;
}

bool CompoundType::containsPointer() const {
    if (Scope::containsPointer()) {
        return true;
    }

    for (const auto &field : *mFields) {
        if (field->type().containsPointer()) {
            return true;
        }
    }

    return false;
}

void CompoundType::getAlignmentAndSize(size_t *align, size_t *size) const {
    *align = 1;
    *size = 0;

    size_t offset = 0;
    for (const auto &field : *mFields) {
        // Each field is aligned according to its alignment requirement.
        // The surrounding structure's alignment is the maximum of its
        // fields' aligments.

        size_t fieldAlign, fieldSize;
        field->type().getAlignmentAndSize(&fieldAlign, &fieldSize);

        size_t pad = offset % fieldAlign;
        if (pad > 0) {
            offset += fieldAlign - pad;
        }

        if (mStyle == STYLE_STRUCT) {
            offset += fieldSize;
        } else {
            *size = std::max(*size, fieldSize);
        }

        if (fieldAlign > (*align)) {
            *align = fieldAlign;
        }
    }

    if (mStyle == STYLE_STRUCT) {
        *size = offset;
    }

    // Final padding to account for the structure's alignment.
    size_t pad = (*size) % (*align);
    if (pad > 0) {
        (*size) += (*align) - pad;
    }

    if (*size == 0) {
        // An empty struct still occupies a byte of space in C++.
        *size = 1;
    }
}

////////////////////////////////////////////////////////////////////////////////

CompoundField::CompoundField(const char *name, Type *type)
    : mName(name),
      mType(type) {
}

std::string CompoundField::name() const {
    return mName;
}

const Type &CompoundField::type() const {
    return *mType;
}

}  // namespace android

