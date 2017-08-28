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

#include "ArrayType.h"

#include <hidl-util/Formatter.h>
#include <android-base/logging.h>

#include "ConstantExpression.h"

namespace android {

ArrayType::ArrayType(ArrayType *srcArray, ConstantExpression *size)
    : mElementType(srcArray->mElementType),
      mSizes(srcArray->mSizes) {
    prependDimension(size);
}

ArrayType::ArrayType(Type *elementType, ConstantExpression *size)
    : mElementType(elementType) {
    prependDimension(size);
}

void ArrayType::prependDimension(ConstantExpression *size) {
    mSizes.insert(mSizes.begin(), size);
}

void ArrayType::appendDimension(ConstantExpression *size) {
    mSizes.push_back(size);
}

size_t ArrayType::countDimensions() const {
    return mSizes.size();
}

void ArrayType::addNamedTypesToSet(std::set<const FQName> &set) const {
    mElementType->addNamedTypesToSet(set);
}

bool ArrayType::isArray() const {
    return true;
}

bool ArrayType::canCheckEquality() const {
    return mElementType->canCheckEquality();
}

Type *ArrayType::getElementType() const {
    return mElementType;
}

std::string ArrayType::getCppType(StorageMode mode,
                                  bool specifyNamespaces) const {
    const std::string base = mElementType->getCppStackType(specifyNamespaces);

    std::string space = specifyNamespaces ? "::android::hardware::" : "";
    std::string arrayType = space + "hidl_array<" + base;

    for (size_t i = 0; i < mSizes.size(); ++i) {
        arrayType += ", ";
        arrayType += mSizes[i]->cppValue();

        if (!mSizes[i]->descriptionIsTrivial()) {
            arrayType += " /* ";
            arrayType += mSizes[i]->description();
            arrayType += " */";
        }
    }

    arrayType += ">";

    switch (mode) {
        case StorageMode_Stack:
            return arrayType;

        case StorageMode_Argument:
            return "const " + arrayType + "&";

        case StorageMode_Result:
            return "const " + arrayType + "*";
    }

    CHECK(!"Should not be here");
}

std::string ArrayType::getInternalDataCppType() const {
    std::string result = mElementType->getCppStackType();
    for (size_t i = 0; i < mSizes.size(); ++i) {
        result += "[";
        result += mSizes[i]->cppValue();
        result += "]";
    }
    return result;
}

std::string ArrayType::getJavaType(bool forInitializer) const {
    std::string base =
        mElementType->getJavaType(forInitializer);

    for (size_t i = 0; i < mSizes.size(); ++i) {
        base += "[";

        if (forInitializer) {
            base += mSizes[i]->javaValue();
        }

        if (!forInitializer || !mSizes[i]->descriptionIsTrivial()) {
            if (forInitializer)
                base += " ";
            base += "/* " + mSizes[i]->description() + " */";
        }

        base += "]";
    }

    return base;
}

std::string ArrayType::getJavaWrapperType() const {
    return mElementType->getJavaWrapperType();
}

std::string ArrayType::getVtsType() const {
    return "TYPE_ARRAY";
}

void ArrayType::emitReaderWriter(
        Formatter &out,
        const std::string &name,
        const std::string &parcelObj,
        bool parcelObjIsPointer,
        bool isReader,
        ErrorMode mode) const {
    std::string baseType = mElementType->getCppStackType();

    const std::string parentName = "_hidl_" + name + "_parent";

    out << "size_t " << parentName << ";\n\n";

    const std::string parcelObjDeref =
        parcelObj + (parcelObjIsPointer ? "->" : ".");

    size_t numArrayElements = 1;
    for (auto size : mSizes) {
        numArrayElements *= size->castSizeT();
    }
    if (isReader) {
        out << "_hidl_err = "
            << parcelObjDeref
            << "readBuffer("
            << numArrayElements
            << " * sizeof("
            << baseType
            << "), &"
            << parentName
            << ", "
            << " reinterpret_cast<const void **>("
            << "&" << name
            << "));\n\n";

        handleError(out, mode);
    } else {

        out << "_hidl_err = "
            << parcelObjDeref
            << "writeBuffer("
            << name
            << ".data(), "
            << numArrayElements
            << " * sizeof("
            << baseType
            << "), &"
            << parentName
            << ");\n";

        handleError(out, mode);
    }

    emitReaderWriterEmbedded(
            out,
            0 /* depth */,
            name,
            name /* sanitizedName */,
            isReader /* nameIsPointer */,
            parcelObj,
            parcelObjIsPointer,
            isReader,
            mode,
            parentName,
            "0 /* parentOffset */");
}

void ArrayType::emitReaderWriterEmbedded(
        Formatter &out,
        size_t depth,
        const std::string &name,
        const std::string &sanitizedName,
        bool nameIsPointer,
        const std::string &parcelObj,
        bool parcelObjIsPointer,
        bool isReader,
        ErrorMode mode,
        const std::string &parentName,
        const std::string &offsetText) const {
    if (!mElementType->needsEmbeddedReadWrite()) {
        return;
    }

    const std::string nameDeref = name + (nameIsPointer ? "->" : ".");

    std::string baseType = mElementType->getCppStackType();

    std::string iteratorName = "_hidl_index_" + std::to_string(depth);

    out << "for (size_t "
        << iteratorName
        << " = 0; "
        << iteratorName
        << " < "
        << dimension()
        << "; ++"
        << iteratorName
        << ") {\n";

    out.indent();

    mElementType->emitReaderWriterEmbedded(
            out,
            depth + 1,
            nameDeref + "data()[" + iteratorName + "]",
            sanitizedName + "_indexed",
            false /* nameIsPointer */,
            parcelObj,
            parcelObjIsPointer,
            isReader,
            mode,
            parentName,
            offsetText
                + " + " + iteratorName + " * sizeof("
                + baseType
                + ")");

    out.unindent();

    out << "}\n\n";
}

void ArrayType::emitResolveReferences(
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

void ArrayType::emitResolveReferencesEmbedded(
            Formatter &out,
            size_t depth,
            const std::string &name,
            const std::string &sanitizedName,
            bool nameIsPointer,
            const std::string &parcelObj,
            bool parcelObjIsPointer,
            bool isReader,
            ErrorMode mode,
            const std::string &parentName,
            const std::string &offsetText) const {
    CHECK(needsResolveReferences() && mElementType->needsResolveReferences());

    const std::string nameDeref = name + (nameIsPointer ? "->" : ".");

    std::string baseType = mElementType->getCppStackType();

    std::string iteratorName = "_hidl_index_" + std::to_string(depth);

    out << "for (size_t "
        << iteratorName
        << " = 0; "
        << iteratorName
        << " < "
        << dimension()
        << "; ++"
        << iteratorName
        << ") {\n";

    out.indent();

    mElementType->emitResolveReferencesEmbedded(
        out,
        depth + 1,
        nameDeref + "data()[" + iteratorName + "]",
        sanitizedName + "_indexed",
        false /* nameIsPointer */,
        parcelObj,
        parcelObjIsPointer,
        isReader,
        mode,
        parentName,
        offsetText + " + " + iteratorName + " * sizeof("
        + baseType
        + ")");

    out.unindent();

    out << "}\n\n";
}

void ArrayType::emitJavaDump(
        Formatter &out,
        const std::string &streamName,
        const std::string &name) const {
    out << streamName << ".append(java.util.Arrays."
        << (countDimensions() > 1 ? "deepToString" : "toString")
        << "("
        << name << "));\n";
}


bool ArrayType::needsEmbeddedReadWrite() const {
    return mElementType->needsEmbeddedReadWrite();
}

bool ArrayType::needsResolveReferences() const {
    return mElementType->needsResolveReferences();
}

bool ArrayType::resultNeedsDeref() const {
    return true;
}

void ArrayType::emitJavaReaderWriter(
        Formatter &out,
        const std::string &parcelObj,
        const std::string &argName,
        bool isReader) const {
    size_t align, size;
    getAlignmentAndSize(&align, &size);

    if (isReader) {
        out << "new "
            << getJavaType(true /* forInitializer */)
            << ";\n";
    }

    out << "{\n";
    out.indent();

    out << "android.os.HwBlob _hidl_blob = ";

    if (isReader) {
        out << parcelObj
            << ".readBuffer("
            << size
            << " /* size */);\n";
    } else {
        out << "new android.os.HwBlob("
            << size
            << " /* size */);\n";
    }

    emitJavaFieldReaderWriter(
            out,
            0 /* depth */,
            parcelObj,
            "_hidl_blob",
            argName,
            "0 /* offset */",
            isReader);

    if (!isReader) {
        out << parcelObj << ".writeBuffer(_hidl_blob);\n";
    }

    out.unindent();
    out << "}\n";
}

void ArrayType::emitJavaFieldInitializer(
        Formatter &out, const std::string &fieldName) const {
    std::string typeName = getJavaType(false /* forInitializer */);
    std::string initName = getJavaType(true /* forInitializer */);

    out << "final "
        << typeName
        << " "
        << fieldName
        << " = new "
        << initName
        << ";\n";
}

void ArrayType::emitJavaFieldReaderWriter(
        Formatter &out,
        size_t depth,
        const std::string &parcelName,
        const std::string &blobName,
        const std::string &fieldName,
        const std::string &offset,
        bool isReader) const {
    out << "{\n";
    out.indent();

    std::string offsetName = "_hidl_array_offset_" + std::to_string(depth);
    out << "long " << offsetName << " = " << offset << ";\n";

    std::string indexString;
    for (size_t dim = 0; dim < mSizes.size(); ++dim) {
        std::string iteratorName =
            "_hidl_index_" + std::to_string(depth) + "_" + std::to_string(dim);

        out << "for (int "
            << iteratorName
            << " = 0; "
            << iteratorName
            << " < "
            << mSizes[dim]->javaValue()
            << "; ++"
            << iteratorName
            << ") {\n";

        out.indent();

        indexString += "[" + iteratorName + "]";
    }

    if (isReader && mElementType->isCompoundType()) {
        std::string typeName =
            mElementType->getJavaType(false /* forInitializer */);

        out << fieldName
            << indexString
            << " = new "
            << typeName
            << "();\n";
    }

    mElementType->emitJavaFieldReaderWriter(
            out,
            depth + 1,
            parcelName,
            blobName,
            fieldName + indexString,
            offsetName,
            isReader);

    size_t elementAlign, elementSize;
    mElementType->getAlignmentAndSize(&elementAlign, &elementSize);

    out << offsetName << " += " << std::to_string(elementSize) << ";\n";

    for (size_t dim = 0; dim < mSizes.size(); ++dim) {
        out.unindent();
        out << "}\n";
    }

    out.unindent();
    out << "}\n";
}

status_t ArrayType::emitVtsTypeDeclarations(Formatter &out) const {
    out << "type: " << getVtsType() << "\n";
    out << "vector_size: " << mSizes[0]->value() << "\n";
    out << "vector_value: {\n";
    out.indent();
    // Simple array case.
    if (mSizes.size() == 1) {
        status_t err = mElementType->emitVtsTypeDeclarations(out);
        if (err != OK) {
            return err;
        }
    } else {  // Multi-dimension array case.
        for (size_t index = 1; index < mSizes.size(); index++) {
            out << "type: " << getVtsType() << "\n";
            out << "vector_size: " << mSizes[index]->value() << "\n";
            out << "vector_value: {\n";
            out.indent();
            if (index == mSizes.size() - 1) {
                status_t err = mElementType->emitVtsTypeDeclarations(out);
                if (err != OK) {
                    return err;
                }
            }
        }
    }
    for (size_t index = 0; index < mSizes.size(); index++) {
        out.unindent();
        out << "}\n";
    }
    return OK;
}

bool ArrayType::isJavaCompatible() const {
    return mElementType->isJavaCompatible();
}

bool ArrayType::containsPointer() const {
    return mElementType->containsPointer();
}

void ArrayType::getAlignmentAndSize(size_t *align, size_t *size) const {
    mElementType->getAlignmentAndSize(align, size);

    for (auto sizeInDimension : mSizes) {
        (*size) *= sizeInDimension->castSizeT();
    }
}

size_t ArrayType::dimension() const {
    size_t numArrayElements = 1;
    for (auto size : mSizes) {
        numArrayElements *= size->castSizeT();
    }
    return numArrayElements;
}

}  // namespace android

