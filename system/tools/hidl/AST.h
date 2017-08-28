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

#ifndef AST_H_

#define AST_H_

#include <android-base/macros.h>
#include <hidl-util/FQName.h>
#include <map>
#include <set>
#include <string>
#include <vector>

#include "Type.h"

namespace android {

struct Coordinator;
struct Formatter;
struct Interface;
struct Location;
struct Method;
struct NamedType;
struct TypedVar;
struct Scope;
struct EnumValue;

struct AST {
    AST(Coordinator *coordinator, const std::string &path);
    ~AST();

    bool setPackage(const char *package);
    bool addImport(const char *import);

    // package and version really.
    FQName package() const;
    bool isInterface(std::string *ifaceName) const;
    bool containsInterfaces() const;

    void enterScope(Scope *container);
    void leaveScope();
    Scope *scope();

    // Returns true iff successful.
    bool addTypeDef(const char *localName, Type *type, const Location &location,
            std::string *errorMsg);

    // Returns true iff successful.
    bool addScopedType(NamedType *type, std::string *errorMsg);

    void *scanner();
    void setScanner(void *scanner);

    const std::string &getFilename() const;

    // Look up an enum value by "FQName:valueName".
    EnumValue *lookupEnumValue(const FQName &fqName, std::string *errorMsg);

    // Look up a type by FQName, "pure" names, i.e. those without package
    // or version are first looked up in the current scope chain.
    // After that lookup proceeds to imports.
    Type *lookupType(const FQName &fqName);

    void addImportedAST(AST *ast);

    status_t generateCpp(const std::string &outputPath) const;
    status_t generateCppHeaders(const std::string &outputPath) const;
    status_t generateCppSources(const std::string &outputPath) const;
    status_t generateCppImpl(const std::string &outputPath) const;

    status_t generateJava(
            const std::string &outputPath,
            const std::string &limitToType) const;

    status_t generateJavaTypes(
            const std::string &outputPath,
            const std::string &limitToType) const;

    void getImportedPackages(std::set<FQName> *importSet) const;

    // Run getImportedPackages on this, then run getImportedPackages on
    // each AST in each package referenced in importSet.
    void getImportedPackagesHierarchy(std::set<FQName> *importSet) const;

    status_t generateVts(const std::string &outputPath) const;

    bool isJavaCompatible() const;

    // Return the set of FQNames for those interfaces and types that are
    // actually referenced in the AST, not merely imported.
    const std::set<FQName>& getImportedNames() const {
        return mImportedNames;
    }

    // Get transitive closure of imported interface/types.
    void getAllImportedNames(std::set<FQName> *allImportSet) const;

    void appendToExportedTypesVector(
            std::vector<const Type *> *exportedTypes) const;

    // used by the parser.
    void addSyntaxError();
    size_t syntaxErrors() const;

    bool isIBase() const;

    const Interface *getInterface() const;

private:
    Coordinator *mCoordinator;
    std::string mPath;
    std::vector<Scope *> mScopePath;

    void *mScanner;
    Scope *mRootScope;

    FQName mPackage;

    // A set of all external interfaces/types that are _actually_ referenced
    // in this AST, this is a subset of those specified in import statements.
    std::set<FQName> mImportedNames;

    // A set of all ASTs we explicitly or implicitly (types.hal) import.
    std::set<AST *> mImportedASTs;

    // If a single type (instead of the whole AST) is imported, the AST will be
    // present as a key to this map, with the value being a list of types
    // imported from this AST. If an AST appears in mImportedASTs but not in
    // mImportedTypes, then the whole AST is imported.
    std::map<AST *, std::set<Type *>> mImportedTypes;

    // Types keyed by full names defined in this AST.
    std::map<FQName, Type *> mDefinedTypesByFullName;

    // used by the parser.
    size_t mSyntaxErrors = 0;

    bool addScopedTypeInternal(
            NamedType *type,
            std::string *errorMsg);

    // Helper functions for lookupType.
    Type *lookupTypeLocally(const FQName &fqName);
    status_t lookupAutofilledType(const FQName &fqName, Type **returnedType);
    Type *lookupTypeFromImports(const FQName &fqName);

    // Find a type matching fqName (which may be partial) and if found
    // return the associated type and fill in the full "matchingName".
    // Only types defined in this very AST are considered.
    Type *findDefinedType(const FQName &fqName, FQName *matchingName) const;

    void getPackageComponents(std::vector<std::string> *components) const;

    void getPackageAndVersionComponents(
            std::vector<std::string> *components, bool cpp_compatible) const;

    static void generateCppPackageInclude(
            Formatter &out,
            const FQName &package,
            const std::string &klass);

    std::string makeHeaderGuard(const std::string &baseName,
                                bool indicateGenerated = true) const;
    void enterLeaveNamespace(Formatter &out, bool enter) const;

    static void generateCheckNonNull(Formatter &out, const std::string &nonNull);

    status_t generateInterfaceHeader(const std::string &outputPath) const;
    status_t generateHwBinderHeader(const std::string &outputPath) const;
    status_t generateStubHeader(const std::string &outputPath) const;
    status_t generateProxyHeader(const std::string &outputPath) const;
    status_t generatePassthroughHeader(const std::string &outputPath) const;

    status_t generateTypeSource(
            Formatter &out, const std::string &ifaceName) const;

    // a method, and in which interface is it originally defined.
    // be careful of the case where method.isHidlReserved(), where interface
    // is effectively useless.
    using MethodGenerator = std::function<status_t(const Method *, const Interface *)>;

    status_t generateStubImplHeader(const std::string &outputPath) const;
    status_t generateStubImplSource(const std::string &outputPath) const;

    status_t generateMethods(Formatter &out, MethodGenerator gen) const;
    status_t generateStubImplMethod(Formatter &out,
                                    const std::string &className,
                                    const Method *method) const;
    status_t generatePassthroughMethod(Formatter &out,
                                       const Method *method) const;
    status_t generateProxyMethodSource(Formatter &out,
                                       const std::string &className,
                                       const Method *method,
                                       const Interface *superInterface) const;

    void generateFetchSymbol(Formatter &out, const std::string &ifaceName) const;

    status_t generateProxySource(
            Formatter &out, const FQName &fqName) const;

    status_t generateStubSource(
            Formatter &out, const Interface *iface) const;

    status_t generateStubSourceForMethod(
            Formatter &out, const Interface *iface, const Method *method) const;

    status_t generatePassthroughSource(Formatter &out) const;

    status_t generateInterfaceSource(Formatter &out) const;

    enum InstrumentationEvent {
        SERVER_API_ENTRY = 0,
        SERVER_API_EXIT,
        CLIENT_API_ENTRY,
        CLIENT_API_EXIT,
        SYNC_CALLBACK_ENTRY,
        SYNC_CALLBACK_EXIT,
        ASYNC_CALLBACK_ENTRY,
        ASYNC_CALLBACK_EXIT,
        PASSTHROUGH_ENTRY,
        PASSTHROUGH_EXIT,
    };

    status_t generateCppAtraceCall(
            Formatter &out,
            InstrumentationEvent event,
            const Method *method) const;

    status_t generateCppInstrumentationCall(
            Formatter &out,
            InstrumentationEvent event,
            const Method *method) const;

    void declareCppReaderLocals(
            Formatter &out,
            const std::vector<TypedVar *> &arg,
            bool forResults) const;

    void emitCppReaderWriter(
            Formatter &out,
            const std::string &parcelObj,
            bool parcelObjIsPointer,
            const TypedVar *arg,
            bool isReader,
            Type::ErrorMode mode,
            bool addPrefixToName) const;

    void emitCppResolveReferences(
            Formatter &out,
            const std::string &parcelObj,
            bool parcelObjIsPointer,
            const TypedVar *arg,
            bool isReader,
            Type::ErrorMode mode,
            bool addPrefixToName) const;

    void emitJavaReaderWriter(
            Formatter &out,
            const std::string &parcelObj,
            const TypedVar *arg,
            bool isReader,
            bool addPrefixToName) const;

    status_t emitTypeDeclarations(Formatter &out) const;
    status_t emitJavaTypeDeclarations(Formatter &out) const;
    status_t emitVtsTypeDeclarations(Formatter &out) const;

    DISALLOW_COPY_AND_ASSIGN(AST);
};

}  // namespace android

#endif  // AST_H_
