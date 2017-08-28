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

#ifndef ARRAY_TYPE_H_

#define ARRAY_TYPE_H_

#include "Type.h"

#include <vector>

namespace android {

struct ConstantExpression;

struct ArrayType : public Type {
    // Extends existing array by adding another dimension.
    ArrayType(ArrayType *srcArray, ConstantExpression *size);

    ArrayType(Type *elementType, ConstantExpression *size);

    bool isArray() const override;
    bool canCheckEquality() const override;

    Type *getElementType() const;

    void prependDimension(ConstantExpression *size);
    void appendDimension(ConstantExpression *size);
    size_t countDimensions() const;

    std::string getCppType(StorageMode mode,
                           bool specifyNamespaces) const override;

    std::string getInternalDataCppType() const;

    void addNamedTypesToSet(std::set<const FQName> &set) const override;

    std::string getJavaType(bool forInitializer) const override;

    std::string getJavaWrapperType() const override;

    std::string getVtsType() const override;

    void emitReaderWriter(
            Formatter &out,
            const std::string &name,
            const std::string &parcelObj,
            bool parcelObjIsPointer,
            bool isReader,
            ErrorMode mode) const override;

    void emitReaderWriterEmbedded(
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
            const std::string &offsetText) const override;

    void emitResolveReferences(
            Formatter &out,
            const std::string &name,
            bool nameIsPointer,
            const std::string &parcelObj,
            bool parcelObjIsPointer,
            bool isReader,
            ErrorMode mode) const override;

    void emitResolveReferencesEmbedded(
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
            const std::string &offsetText) const override;

    void emitJavaDump(
            Formatter &out,
            const std::string &streamName,
            const std::string &name) const override;

    bool needsEmbeddedReadWrite() const override;
    bool needsResolveReferences() const override;
    bool resultNeedsDeref() const override;

    void emitJavaReaderWriter(
            Formatter &out,
            const std::string &parcelObj,
            const std::string &argName,
            bool isReader) const override;

    void emitJavaFieldInitializer(
            Formatter &out, const std::string &fieldName) const override;

    void emitJavaFieldReaderWriter(
            Formatter &out,
            size_t depth,
            const std::string &parcelName,
            const std::string &blobName,
            const std::string &fieldName,
            const std::string &offset,
            bool isReader) const override;

    status_t emitVtsTypeDeclarations(Formatter &out) const override;

    bool isJavaCompatible() const override;
    bool containsPointer() const override;

    void getAlignmentAndSize(size_t *align, size_t *size) const override;

private:
    Type *mElementType;
    std::vector<ConstantExpression *> mSizes;

    size_t dimension() const;

    DISALLOW_COPY_AND_ASSIGN(ArrayType);
};

}  // namespace android

#endif  // ARRAY_TYPE_H_

