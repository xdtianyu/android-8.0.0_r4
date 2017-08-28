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

#ifndef TYPE_H_

#define TYPE_H_

#include <android-base/macros.h>
#include <string>
#include <utils/Errors.h>
#include <vector>
#include <set>

namespace android {

struct Annotation;
struct Formatter;
struct ScalarType;
struct FQName;

struct Type {
    Type();
    virtual ~Type();

    virtual bool isArray() const;
    virtual bool isBinder() const;
    virtual bool isBitField() const;
    virtual bool isCompoundType() const;
    virtual bool isEnum() const;
    virtual bool isHandle() const;
    virtual bool isInterface() const;
    virtual bool isNamedType() const;
    virtual bool isMemory() const;
    virtual bool isPointer() const;
    virtual bool isScope() const;
    virtual bool isScalar() const;
    virtual bool isString() const;
    virtual bool isTemplatedType() const;
    virtual bool isTypeDef() const;
    virtual bool isVector() const;

    virtual const ScalarType *resolveToScalarType() const;

    virtual std::string typeName() const;

    bool isValidEnumStorageType() const;
    virtual bool isElidableType() const;
    virtual bool canCheckEquality() const;

    enum StorageMode {
        StorageMode_Stack,
        StorageMode_Argument,
        StorageMode_Result,
    };

    virtual std::string getCppType(
            StorageMode mode,
            bool specifyNamespaces) const;

    std::string decorateCppName(
            const std::string &name,
            StorageMode mode,
            bool specifyNamespaces) const;

    /* gets all hidl-defined types used when this item is
     * printed using getCppType or getJavaType. Examples:
     *
     * vec<vec<vec<IFoo>>>: IFoo is added to the set
     * (the hypothetical type pair)
     * pair<IFoo, IBar>: IFoo and IBar are added to the set
     * int32_t: nothing is added to the set
     * string: nothing is added to the set
     * IFoo: IFoo is added to the set
     */
    virtual void addNamedTypesToSet(std::set<const FQName> &set) const = 0;

    std::string getCppStackType(bool specifyNamespaces = true) const;

    std::string getCppResultType(bool specifyNamespaces = true) const;

    std::string getCppArgumentType(bool specifyNamespaces = true) const;

    // For an array type, dimensionality information will be accumulated at the
    // end of the returned string.
    // if forInitializer == true, actual dimensions are included, i.e. [3][5],
    // otherwise (and by default), they are omitted, i.e. [][].
    virtual std::string getJavaType(bool forInitializer = false) const;

    virtual std::string getJavaWrapperType() const;
    virtual std::string getJavaSuffix() const;

    virtual std::string getVtsType() const;
    virtual std::string getVtsValueName() const;

    enum ErrorMode {
        ErrorMode_Ignore,
        ErrorMode_Goto,
        ErrorMode_Break,
        ErrorMode_Return,
    };
    virtual void emitReaderWriter(
            Formatter &out,
            const std::string &name,
            const std::string &parcelObj,
            bool parcelObjIsPointer,
            bool isReader,
            ErrorMode mode) const;

    virtual void emitReaderWriterEmbedded(
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
            const std::string &offsetText) const;

    virtual void emitResolveReferences(
            Formatter &out,
            const std::string &name,
            bool nameIsPointer,
            const std::string &parcelObj,
            bool parcelObjIsPointer,
            bool isReader,
            ErrorMode mode) const;

    virtual void emitResolveReferencesEmbedded(
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
            const std::string &offsetText) const;

    virtual void emitDump(
            Formatter &out,
            const std::string &streamName,
            const std::string &name) const;

    virtual void emitJavaDump(
            Formatter &out,
            const std::string &streamName,
            const std::string &name) const;

    virtual bool useParentInEmitResolveReferencesEmbedded() const;

    virtual bool useNameInEmitReaderWriterEmbedded(bool isReader) const;

    virtual void emitJavaReaderWriter(
            Formatter &out,
            const std::string &parcelObj,
            const std::string &argName,
            bool isReader) const;

    virtual void emitJavaFieldInitializer(
            Formatter &out,
            const std::string &fieldName) const;

    virtual void emitJavaFieldReaderWriter(
            Formatter &out,
            size_t depth,
            const std::string &parcelName,
            const std::string &blobName,
            const std::string &fieldName,
            const std::string &offset,
            bool isReader) const;

    virtual status_t emitTypeDeclarations(Formatter &out) const;

    // Emit any declarations pertaining to this type that have to be
    // at global scope, i.e. enum class operators.
    virtual status_t emitGlobalTypeDeclarations(Formatter &out) const;

    // Emit any declarations pertaining to this type that have to be
    // at global scope for transport, e.g. read/writeEmbeddedTo/FromParcel
    virtual status_t emitGlobalHwDeclarations(Formatter &out) const;

    virtual status_t emitTypeDefinitions(
            Formatter &out, const std::string prefix) const;

    virtual status_t emitJavaTypeDeclarations(
            Formatter &out, bool atTopLevel) const;

    virtual bool needsEmbeddedReadWrite() const;
    virtual bool needsResolveReferences() const;
    virtual bool resultNeedsDeref() const;

    // Generates type declaration for vts proto file.
    // TODO (b/30844146): make it a pure virtual method.
    virtual status_t emitVtsTypeDeclarations(Formatter &out) const;
    // Generates type declaration as attribute of method (return value or method
    // argument) or attribute of compound type for vts proto file.
    virtual status_t emitVtsAttributeType(Formatter &out) const;

    // Returns true iff this type is supported through the Java backend.
    virtual bool isJavaCompatible() const;
    virtual bool containsPointer() const;
    virtual void getAlignmentAndSize(size_t *align, size_t *size) const;

    void setAnnotations(std::vector<Annotation *> *annotations);
    const std::vector<Annotation *> &annotations() const;

    virtual void appendToExportedTypesVector(
            std::vector<const Type *> *exportedTypes) const;

    virtual status_t emitExportedHeader(Formatter &out, bool forJava) const;

protected:
    void handleError(Formatter &out, ErrorMode mode) const;

    void emitReaderWriterEmbeddedForTypeName(
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
            const std::string &funcNamespace) const;

    void emitJavaReaderWriterWithSuffix(
            Formatter &out,
            const std::string &parcelObj,
            const std::string &argName,
            bool isReader,
            const std::string &suffix,
            const std::string &extra) const;

    void emitDumpWithMethod(
            Formatter &out,
            const std::string &streamName,
            const std::string &methodName,
            const std::string &name) const;

private:
    std::vector<Annotation *> *mAnnotations;

    DISALLOW_COPY_AND_ASSIGN(Type);
};

/* Base type for VectorType and RefType. */
struct TemplatedType : public Type {
    void setElementType(Type *elementType);
    Type *getElementType() const;
    bool isTemplatedType() const override;
    virtual bool isCompatibleElementType(Type *elementType) const = 0;
    status_t emitVtsTypeDeclarations(Formatter &out) const override;
    status_t emitVtsAttributeType(Formatter &out) const override;
protected:
    TemplatedType();
    Type *mElementType;
private:
    DISALLOW_COPY_AND_ASSIGN(TemplatedType);
};

}  // namespace android

#endif  // TYPE_H_

