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

#ifndef COORDINATOR_H_

#define COORDINATOR_H_

#include <android-base/macros.h>
#include <functional>
#include <map>
#include <set>
#include <string>
#include <utils/Errors.h>
#include <vector>

namespace android {

struct AST;
struct FQName;
struct Type;

struct Coordinator {
    Coordinator(
            const std::vector<std::string> &packageRootPaths,
            const std::vector<std::string> &packageRoots);

    ~Coordinator();

    // Attempts to parse the interface/types referred to by fqName.
    // Parsing an interface also parses the associated package's types.hal
    // file if it exists.
    // If "parsedASTs" is non-NULL, successfully parsed ASTs are inserted
    // into the set.
    // If !enforce, enforceRestrictionsOnPackage won't be run.
    AST *parse(const FQName &fqName, std::set<AST *> *parsedASTs = nullptr,
            bool enforce = true);

    // Given package-root paths of ["hardware/interfaces",
    // "vendor/<something>/interfaces"], package roots of
    // ["android.hardware", "vendor.<something>.hardware"], and a
    // FQName of "android.hardware.nfc@1.0::INfc, then getPackagePath()
    // will return "hardware/interfaces/nfc/1.0" (if sanitized = false)
    // or "hardware/interfaces/nfc/V1_0" (if sanitized = true).

    std::string getPackagePath(
            const FQName &fqName, bool relative = false,
            bool sanitized = false) const;

    // Given package roots of ["android.hardware",
    // "vendor.<something>.hardware"] and a FQName of
    // "android.hardware.nfc@1.0::INfc, then getPackageRoot() will
    // return "android.hardware".

    std::string getPackageRoot(const FQName &fqName) const;

    // Given package-root paths of ["hardware/interfaces",
    // "vendor/<something>/interfaces"], package roots of
    // ["android.hardware", "vendor.<something>.hardware"], and a
    // FQName of "android.hardware.nfc@1.0::INfc, then getPackageRootPath()
    // will return "hardware/interfaces".

    std::string getPackageRootPath(const FQName &fqName) const;

    // return getPackageRoot + ":" + getPackageRootPath
    std::string getPackageRootOption(const FQName &fqName) const;

    // Given an FQName of "android.hardware.nfc@1.0::INfc", return
    // "android/hardware/".
    std::string convertPackageRootToPath(const FQName &fqName) const;

    status_t getPackageInterfaceFiles(
            const FQName &package,
            std::vector<std::string> *fileNames) const;

    status_t appendPackageInterfacesToVector(
            const FQName &package,
            std::vector<FQName> *packageInterfaces) const;

    // Enforce a set of restrictions on a set of packages. These include:
    //    - minor version upgrades
    // "packages" contains names like "android.hardware.nfc@1.1".
    //    - hashing restrictions
    status_t enforceRestrictionsOnPackage(const FQName &fqName);

    static bool MakeParentHierarchy(const std::string &path);

private:
    // A list of top-level directories (mPackageRootPaths)
    // corresponding to a list of package roots (mPackageRoots). For
    // example, if mPackageRootPaths[0] == "hardware/interfaces" and
    // mPackageRoots[0] == "android.hardware" this means that all
    // packages starting with "android.hardware" will be looked up in
    // "hardware/interfaces".
    std::vector<std::string> mPackageRootPaths;
    std::vector<std::string> mPackageRoots;

    // cache to parse().
    std::map<FQName, AST *> mCache;

    // cache to enforceRestrictionsOnPackage().
    std::set<FQName> mPackagesEnforced;

    std::vector<std::string>::const_iterator findPackageRoot(
            const FQName &fqName) const;

    // Rules of enforceRestrictionsOnPackage are listed below.
    status_t enforceMinorVersionUprevs(const FQName &fqName);
    status_t enforceHashes(const FQName &fqName);

    DISALLOW_COPY_AND_ASSIGN(Coordinator);
};

}  // namespace android

#endif  // COORDINATOR_H_
