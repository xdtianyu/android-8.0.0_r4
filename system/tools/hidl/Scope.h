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

#ifndef SCOPE_H_

#define SCOPE_H_

#include "NamedType.h"

#include <map>
#include <vector>

namespace android {

struct Formatter;
struct Interface;
struct LocalIdentifier;

struct Scope : public NamedType {
    Scope(const char *localName,
          const Location &location);
    virtual ~Scope();

    bool addType(NamedType *type, std::string *errorMsg);

    // lookup a type given an FQName.
    // Assume fqName.package(), fqName.version(), fqName.valueName() is empty.
    NamedType *lookupType(const FQName &fqName) const;

    virtual LocalIdentifier *lookupIdentifier(const std::string &name) const;

    bool isScope() const override;

    // Returns the single interface or NULL.
    Interface *getInterface() const;

    bool containsSingleInterface(std::string *ifaceName) const;
    bool containsInterfaces() const;

    status_t emitTypeDeclarations(Formatter &out) const override;
    status_t emitGlobalTypeDeclarations(Formatter &out) const override;
    status_t emitGlobalHwDeclarations(Formatter &out) const override;

    status_t emitJavaTypeDeclarations(
            Formatter &out, bool atTopLevel) const override;

    status_t emitTypeDefinitions(
            Formatter &out, const std::string prefix) const override;

    const std::vector<NamedType *> &getSubTypes() const;

    status_t emitVtsTypeDeclarations(Formatter &out) const override;

    bool isJavaCompatible() const override;
    bool containsPointer() const override;

    void appendToExportedTypesVector(
            std::vector<const Type *> *exportedTypes) const override;

private:
    std::vector<NamedType *> mTypes;
    std::map<std::string, size_t> mTypeIndexByName;

    status_t forEachType(std::function<status_t(Type *)> func) const;

    DISALLOW_COPY_AND_ASSIGN(Scope);
};

struct LocalIdentifier {
    LocalIdentifier();
    virtual ~LocalIdentifier();
    virtual bool isEnumValue() const;
};

}  // namespace android

#endif  // SCOPE_H_

