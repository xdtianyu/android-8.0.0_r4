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

#include "AST.h"

#include "Coordinator.h"
#include "EnumType.h"
#include "HandleType.h"
#include "Interface.h"
#include "Location.h"
#include "FmqType.h"
#include "Scope.h"
#include "TypeDef.h"

#include <hidl-util/Formatter.h>
#include <hidl-util/FQName.h>
#include <android-base/logging.h>
#include <iostream>
#include <stdlib.h>

namespace android {

AST::AST(Coordinator *coordinator, const std::string &path)
    : mCoordinator(coordinator),
      mPath(path),
      mScanner(NULL),
      mRootScope(new Scope("" /* localName */, Location::startOf(path))) {
    enterScope(mRootScope);
}

AST::~AST() {
    delete mRootScope;
    mRootScope = nullptr;

    CHECK(mScanner == NULL);

    // Ownership of "coordinator" was NOT transferred.
}

// used by the parser.
void AST::addSyntaxError() {
    mSyntaxErrors++;
}

size_t AST::syntaxErrors() const {
    return mSyntaxErrors;
}

void *AST::scanner() {
    return mScanner;
}

void AST::setScanner(void *scanner) {
    mScanner = scanner;
}

const std::string &AST::getFilename() const {
    return mPath;
}

bool AST::setPackage(const char *package) {
    mPackage.setTo(package);
    CHECK(mPackage.isValid());

    if (mPackage.package().empty()
            || mPackage.version().empty()
            || !mPackage.name().empty()) {
        return false;
    }

    return true;
}

FQName AST::package() const {
    return mPackage;
}

bool AST::isInterface(std::string *ifaceName) const {
    return mRootScope->containsSingleInterface(ifaceName);
}

bool AST::containsInterfaces() const {
    return mRootScope->containsInterfaces();
}

bool AST::addImport(const char *import) {
    FQName fqName(import);
    CHECK(fqName.isValid());

    fqName.applyDefaults(mPackage.package(), mPackage.version());

    // LOG(INFO) << "importing " << fqName.string();

    if (fqName.name().empty()) {
        // import a package
        std::vector<FQName> packageInterfaces;

        status_t err =
            mCoordinator->appendPackageInterfacesToVector(fqName,
                                                          &packageInterfaces);

        if (err != OK) {
            return false;
        }

        for (const auto &subFQName : packageInterfaces) {
            // Do not enforce restrictions on imports.
            AST *ast = mCoordinator->parse(subFQName, &mImportedASTs, false /* enforce */);
            if (ast == nullptr) {
                return false;
            }
            // all previous single type imports are ignored.
            mImportedTypes.erase(ast);
        }

        return true;
    }

    AST *importAST;

    // cases like android.hardware.foo@1.0::IFoo.Internal
    //            android.hardware.foo@1.0::Abc.Internal

    // assume it is an interface, and try to import it.
    const FQName interfaceName = fqName.getTopLevelType();
    // Do not enforce restrictions on imports.
    importAST = mCoordinator->parse(interfaceName, &mImportedASTs, false /* enforce */);

    if (importAST != nullptr) {
        // cases like android.hardware.foo@1.0::IFoo.Internal
        //        and android.hardware.foo@1.0::IFoo
        if (fqName == interfaceName) {
            // import a single file.
            // all previous single type imports are ignored.
            // cases like android.hardware.foo@1.0::IFoo
            //        and android.hardware.foo@1.0::types
            mImportedTypes.erase(importAST);
            return true;
        }

        // import a single type from this file
        // cases like android.hardware.foo@1.0::IFoo.Internal
        FQName matchingName;
        Type *match = importAST->findDefinedType(fqName, &matchingName);
        if (match == nullptr) {
            return false;
        }
        // will automatically create a set if it does not exist
        mImportedTypes[importAST].insert(match);
        return true;
    }

    // probably a type in types.hal, like android.hardware.foo@1.0::Abc.Internal
    FQName typesFQName = fqName.getTypesForPackage();

    // Do not enforce restrictions on imports.
    importAST = mCoordinator->parse(typesFQName, &mImportedASTs, false /* enforce */);

    if (importAST != nullptr) {
        // Attempt to find Abc.Internal in types.
        FQName matchingName;
        Type *match = importAST->findDefinedType(fqName, &matchingName);
        if (match == nullptr) {
            return false;
        }
        // will automatically create a set if not exist
        mImportedTypes[importAST].insert(match);
        return true;
    }

    // can't find an appropriate AST for fqName.
    return false;
}

void AST::addImportedAST(AST *ast) {
    mImportedASTs.insert(ast);
}

void AST::enterScope(Scope *container) {
    mScopePath.push_back(container);
}

void AST::leaveScope() {
    mScopePath.pop_back();
}

Scope *AST::scope() {
    CHECK(!mScopePath.empty());
    return mScopePath.back();
}

bool AST::addTypeDef(const char *localName, Type *type, const Location &location,
        std::string *errorMsg) {
    // The reason we wrap the given type in a TypeDef is simply to suppress
    // emitting any type definitions later on, since this is just an alias
    // to a type defined elsewhere.
    return addScopedTypeInternal(
            new TypeDef(localName, location, type), errorMsg);
}

bool AST::addScopedType(NamedType *type, std::string *errorMsg) {
    return addScopedTypeInternal(
            type, errorMsg);
}

bool AST::addScopedTypeInternal(
        NamedType *type,
        std::string *errorMsg) {

    bool success = scope()->addType(type, errorMsg);
    if (!success) {
        return false;
    }

    std::string path;
    for (size_t i = 1; i < mScopePath.size(); ++i) {
        path.append(mScopePath[i]->localName());
        path.append(".");
    }
    path.append(type->localName());

    FQName fqName(mPackage.package(), mPackage.version(), path);

    type->setFullName(fqName);

    mDefinedTypesByFullName[fqName] = type;

    return true;
}

EnumValue *AST::lookupEnumValue(const FQName &fqName, std::string *errorMsg) {

    FQName enumTypeName = fqName.typeName();
    std::string enumValueName = fqName.valueName();

    CHECK(enumTypeName.isValid());
    CHECK(!enumValueName.empty());

    Type *type = lookupType(enumTypeName);
    if(type == nullptr) {
        *errorMsg = "Cannot find type " + enumTypeName.string();
        return nullptr;
    }
    if(!type->isEnum()) {
        *errorMsg = "Type " + enumTypeName.string() + " is not an enum type";
        return nullptr;
    }

    EnumType *enumType = static_cast<EnumType *>(type);
    EnumValue *v = static_cast<EnumValue *>(enumType->lookupIdentifier(enumValueName));
    if(v == nullptr) {
        *errorMsg = "Enum type " + enumTypeName.string() + " does not have " + enumValueName;
        return nullptr;
    }
    return v;
}

Type *AST::lookupType(const FQName &fqName) {
    CHECK(fqName.isValid());

    if (fqName.name().empty()) {
        // Given a package and version???
        return nullptr;
    }

    Type *returnedType = nullptr;

    if (fqName.package().empty() && fqName.version().empty()) {
        // resolve locally first if possible.
        returnedType = lookupTypeLocally(fqName);
        if (returnedType != nullptr) {
            return returnedType;
        }
    }

    if (!fqName.isFullyQualified()) {
        status_t status = lookupAutofilledType(fqName, &returnedType);
        if (status != OK) {
            return nullptr;
        }
        if (returnedType != nullptr) {
            return returnedType;
        }
    }

    return lookupTypeFromImports(fqName);
}

// Rule 0: try resolve locally
Type *AST::lookupTypeLocally(const FQName &fqName) {
    CHECK(fqName.package().empty() && fqName.version().empty()
        && !fqName.name().empty() && fqName.valueName().empty());

    for (size_t i = mScopePath.size(); i-- > 0;) {
        Type *type = mScopePath[i]->lookupType(fqName);

        if (type != nullptr) {
            // Resolve typeDefs to the target type.
            while (type->isTypeDef()) {
                type = static_cast<TypeDef *>(type)->referencedType();
            }

            return type;
        }
    }

    return nullptr;
}

// Rule 1: auto-fill with current package
status_t AST::lookupAutofilledType(const FQName &fqName, Type **returnedType) {
    CHECK(!fqName.isFullyQualified() && !fqName.name().empty() && fqName.valueName().empty());

    FQName autofilled = fqName;
    autofilled.applyDefaults(mPackage.package(), mPackage.version());
    FQName matchingName;
    // Given this fully-qualified name, the type may be defined in this AST, or other files
    // in import.
    Type *local = findDefinedType(autofilled, &matchingName);
    CHECK(local == nullptr || autofilled == matchingName);
    Type *fromImport = lookupType(autofilled);

    if (local != nullptr && fromImport != nullptr && local != fromImport) {
        // Something bad happen; two types have the same FQName.
        std::cerr << "ERROR: Unable to resolve type name '"
                  << fqName.string()
                  << "' (i.e. '"
                  << autofilled.string()
                  << "'), multiple definitions found.\n";

        return UNKNOWN_ERROR;
    }
    if (local != nullptr) {
        *returnedType = local;
        return OK;
    }
    // If fromImport is nullptr as well, return nullptr to fall through to next rule.
    *returnedType = fromImport;
    return OK;
}

// Rule 2: look at imports
Type *AST::lookupTypeFromImports(const FQName &fqName) {

    Type *resolvedType = nullptr;
    Type *returnedType = nullptr;
    FQName resolvedName;

    for (const auto &importedAST : mImportedASTs) {
        if (mImportedTypes.find(importedAST) != mImportedTypes.end()) {
            // ignore single type imports
            continue;
        }
        FQName matchingName;
        Type *match = importedAST->findDefinedType(fqName, &matchingName);

        if (match != nullptr) {
            if (resolvedType != nullptr) {
                std::cerr << "ERROR: Unable to resolve type name '"
                          << fqName.string()
                          << "', multiple matches found:\n";

                std::cerr << "  " << resolvedName.string() << "\n";
                std::cerr << "  " << matchingName.string() << "\n";

                return nullptr;
            }

            resolvedType = match;
            returnedType = resolvedType;
            resolvedName = matchingName;

            // Keep going even after finding a match.
        }
    }

    for (const auto &pair : mImportedTypes) {
        AST *importedAST = pair.first;
        std::set<Type *> importedTypes = pair.second;

        FQName matchingName;
        Type *match = importedAST->findDefinedType(fqName, &matchingName);
        if (match != nullptr &&
                importedTypes.find(match) != importedTypes.end()) {
            if (resolvedType != nullptr) {
                std::cerr << "ERROR: Unable to resolve type name '"
                          << fqName.string()
                          << "', multiple matches found:\n";

                std::cerr << "  " << resolvedName.string() << "\n";
                std::cerr << "  " << matchingName.string() << "\n";

                return nullptr;
            }

            resolvedType = match;
            returnedType = resolvedType;
            resolvedName = matchingName;

            // Keep going even after finding a match.
        }
    }

    if (resolvedType) {
#if 0
        LOG(INFO) << "found '"
                  << resolvedName.string()
                  << "' after looking for '"
                  << fqName.string()
                  << "'.";
#endif

        // Resolve typeDefs to the target type.
        while (resolvedType->isTypeDef()) {
            resolvedType =
                static_cast<TypeDef *>(resolvedType)->referencedType();
        }

        returnedType = resolvedType;

        // If the resolved type is not an interface, we need to determine
        // whether it is defined in types.hal, or in some other interface.  In
        // the latter case, we need to emit a dependency for the interface in
        // which the type is defined.
        //
        // Consider the following:
        //    android.hardware.tests.foo@1.0::Record
        //    android.hardware.tests.foo@1.0::IFoo.Folder
        //    android.hardware.tests.foo@1.0::Folder
        //
        // If Record is an interface, then we keep track of it for the purpose
        // of emitting dependencies in the target language (for example #include
        // in C++).  If Record is a UDT, then we assume it is defined in
        // types.hal in android.hardware.tests.foo@1.0.
        //
        // In the case of IFoo.Folder, the same applies.  If IFoo is an
        // interface, we need to track this for the purpose of emitting
        // dependencies.  If not, then it must have been defined in types.hal.
        //
        // In the case of just specifying Folder, the resolved type is
        // android.hardware.tests.foo@1.0::Folder, and the same logic as
        // above applies.

        if (!resolvedType->isInterface()) {
            FQName ifc = resolvedName.getTopLevelType();
            for (const auto &importedAST : mImportedASTs) {
                FQName matchingName;
                Type *match = importedAST->findDefinedType(ifc, &matchingName);
                if (match != nullptr && match->isInterface()) {
                    resolvedType = match;
                }
            }
        }

        if (!resolvedType->isInterface()) {
            // Non-interface types are declared in the associated types header.
            FQName typesName = resolvedName.getTypesForPackage();

            mImportedNames.insert(typesName);
        } else {
            // Do _not_ use fqName, i.e. the name we used to look up the type,
            // but instead use the name of the interface we found.
            // This is necessary because if fqName pointed to a typedef which
            // in turn referenced the found interface we'd mistakenly use the
            // name of the typedef instead of the proper name of the interface.

            mImportedNames.insert(
                    static_cast<Interface *>(resolvedType)->fqName());
        }
    }

    return returnedType;
}

Type *AST::findDefinedType(const FQName &fqName, FQName *matchingName) const {
    for (const auto &pair : mDefinedTypesByFullName) {
        const FQName &key = pair.first;
        Type* type = pair.second;

        if (key.endsWith(fqName)) {
            *matchingName = key;
            return type;
        }
    }

    return nullptr;
}

void AST::getImportedPackages(std::set<FQName> *importSet) const {
    for (const auto &fqName : mImportedNames) {
        FQName packageName = fqName.getPackageAndVersion();

        if (packageName == mPackage) {
            // We only care about external imports, not our own package.
            continue;
        }

        importSet->insert(packageName);
    }
}

void AST::getImportedPackagesHierarchy(std::set<FQName> *importSet) const {
    getImportedPackages(importSet);
    std::set<FQName> newSet;
    for (const auto &ast : mImportedASTs) {
        if (importSet->find(ast->package()) != importSet->end()) {
            ast->getImportedPackagesHierarchy(&newSet);
        }
    }
    importSet->insert(newSet.begin(), newSet.end());
}

void AST::getAllImportedNames(std::set<FQName> *allImportNames) const {
    for (const auto& name : mImportedNames) {
        allImportNames->insert(name);
        AST *ast = mCoordinator->parse(name, nullptr /* imported */, false /* enforce */);
        ast->getAllImportedNames(allImportNames);
    }
}

bool AST::isJavaCompatible() const {
    std::string ifaceName;
    if (!AST::isInterface(&ifaceName)) {
        for (const auto *type : mRootScope->getSubTypes()) {
            if (!type->isJavaCompatible()) {
                return false;
            }
        }

        return true;
    }

    const Interface *iface = mRootScope->getInterface();
    return iface->isJavaCompatible();
}

void AST::appendToExportedTypesVector(
        std::vector<const Type *> *exportedTypes) const {
    mRootScope->appendToExportedTypesVector(exportedTypes);
}

bool AST::isIBase() const {
    Interface *iface = mRootScope->getInterface();
    return iface != nullptr && iface->isIBase();
}

const Interface *AST::getInterface() const {
    return mRootScope->getInterface();
}

}  // namespace android;
