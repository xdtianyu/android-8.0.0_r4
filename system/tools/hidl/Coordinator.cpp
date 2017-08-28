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

#include "Coordinator.h"

#include <dirent.h>
#include <sys/stat.h>

#include <algorithm>
#include <iterator>

#include <android-base/logging.h>
#include <hidl-hash/Hash.h>
#include <hidl-util/StringHelper.h>

#include "AST.h"
#include "Interface.h"

extern android::status_t parseFile(android::AST *ast);

static bool existdir(const char *name) {
    DIR *dir = opendir(name);
    if (dir == NULL) {
        return false;
    }
    closedir(dir);
    return true;
}

namespace android {

Coordinator::Coordinator(
        const std::vector<std::string> &packageRootPaths,
        const std::vector<std::string> &packageRoots)
    : mPackageRootPaths(packageRootPaths),
      mPackageRoots(packageRoots) {
    // empty
}

Coordinator::~Coordinator() {
    // empty
}

AST *Coordinator::parse(const FQName &fqName, std::set<AST *> *parsedASTs, bool enforce) {
    CHECK(fqName.isFullyQualified());

    auto it = mCache.find(fqName);
    if (it != mCache.end()) {
        AST *ast = (*it).second;

        if (ast != nullptr && parsedASTs != nullptr) {
            parsedASTs->insert(ast);
        }

        return ast;
    }

    // Add this to the cache immediately, so we can discover circular imports.
    mCache[fqName] = nullptr;

    AST *typesAST = nullptr;

    if (fqName.name() != "types") {
        // Any interface file implicitly imports its package's types.hal.
        FQName typesName = fqName.getTypesForPackage();
        // Do not enforce on imports.
        typesAST = parse(typesName, parsedASTs, false /* enforce */);

        // fall through.
    }

    std::string path = getPackagePath(fqName);

    path.append(fqName.name());
    path.append(".hal");

    AST *ast = new AST(this, path);

    if (typesAST != NULL) {
        // If types.hal for this AST's package existed, make it's defined
        // types available to the (about to be parsed) AST right away.
        ast->addImportedAST(typesAST);
    }

    status_t err = parseFile(ast);

    if (err != OK) {
        // LOG(ERROR) << "parsing '" << path << "' FAILED.";

        delete ast;
        ast = nullptr;

        return nullptr;
    }

    if (ast->package().package() != fqName.package()
            || ast->package().version() != fqName.version()) {
        fprintf(stderr,
                "ERROR: File at '%s' does not match expected package and/or "
                "version.\n",
                path.c_str());

        err = UNKNOWN_ERROR;
    } else {
        std::string ifaceName;
        if (ast->isInterface(&ifaceName)) {
            if (fqName.name() == "types") {
                fprintf(stderr,
                        "ERROR: File at '%s' declares an interface '%s' "
                        "instead of the expected types common to the package.\n",
                        path.c_str(),
                        ifaceName.c_str());

                err = UNKNOWN_ERROR;
            } else if (ifaceName != fqName.name()) {
                fprintf(stderr,
                        "ERROR: File at '%s' does not declare interface type "
                        "'%s'.\n",
                        path.c_str(),
                        fqName.name().c_str());

                err = UNKNOWN_ERROR;
            }
        } else if (fqName.name() != "types") {
            fprintf(stderr,
                    "ERROR: File at '%s' declares types rather than the "
                    "expected interface type '%s'.\n",
                    path.c_str(),
                    fqName.name().c_str());

            err = UNKNOWN_ERROR;
        } else if (ast->containsInterfaces()) {
            fprintf(stderr,
                    "ERROR: types.hal file at '%s' declares at least one "
                    "interface type.\n",
                    path.c_str());

            err = UNKNOWN_ERROR;
        }
    }

    if (err != OK) {
        delete ast;
        ast = nullptr;

        return nullptr;
    }

    if (parsedASTs != nullptr) { parsedASTs->insert(ast); }

    // put it into the cache now, so that enforceRestrictionsOnPackage can
    // parse fqName.
    mCache[fqName] = ast;

    if (enforce) {
        // For each .hal file that hidl-gen parses, the whole package will be checked.
        err = enforceRestrictionsOnPackage(fqName);
        if (err != OK) {
            mCache[fqName] = nullptr;
            delete ast;
            ast = nullptr;
            return nullptr;
        }
    }

    return ast;
}

std::vector<std::string>::const_iterator
Coordinator::findPackageRoot(const FQName &fqName) const {
    CHECK(!fqName.package().empty());
    CHECK(!fqName.version().empty());

    // Find the right package prefix and path for this FQName.  For
    // example, if FQName is "android.hardware.nfc@1.0::INfc", and the
    // prefix:root is set to [ "android.hardware:hardware/interfaces",
    // "vendor.qcom.hardware:vendor/qcom"], then we will identify the
    // prefix "android.hardware" and the package root
    // "hardware/interfaces".

    auto it = mPackageRoots.begin();
    auto ret = mPackageRoots.end();
    for (; it != mPackageRoots.end(); it++) {
        if (!fqName.inPackage(*it)) {
            continue;
        }

        CHECK(ret == mPackageRoots.end())
            << "Multiple package roots found for " << fqName.string()
            << " (" << *it << " and " << *ret << ")";

        ret = it;
    }
    CHECK(ret != mPackageRoots.end())
        << "Unable to find package root for " << fqName.string();

    return ret;
}

std::string Coordinator::getPackageRoot(const FQName &fqName) const {
    auto it = findPackageRoot(fqName);
    auto prefix = *it;
    return prefix;
}

std::string Coordinator::getPackageRootPath(const FQName &fqName) const {
    auto it = findPackageRoot(fqName);
    auto root = mPackageRootPaths[std::distance(mPackageRoots.begin(), it)];
    return root;
}

std::string Coordinator::getPackageRootOption(const FQName &fqName) const {
    return getPackageRoot(fqName) + ":" + getPackageRootPath(fqName);
}

std::string Coordinator::getPackagePath(
        const FQName &fqName, bool relative, bool sanitized) const {

    auto it = findPackageRoot(fqName);
    auto prefix = *it;
    auto root = mPackageRootPaths[std::distance(mPackageRoots.begin(), it)];

    // Make sure the prefix ends on a '.' and the root path on a '/'
    if ((*--prefix.end()) != '.') {
        prefix += '.';
    }

    if ((*--root.end()) != '/') {
        root += '/';
    }

    // Given FQName of "android.hardware.nfc@1.0::IFoo" and a prefix
    // "android.hardware.", the suffix is "nfc@1.0::IFoo".
    const std::string packageSuffix = fqName.package().substr(prefix.length());

    std::string packagePath;
    if (!relative) {
        packagePath = root;
    }

    size_t startPos = 0;
    size_t dotPos;
    while ((dotPos = packageSuffix.find('.', startPos)) != std::string::npos) {
        packagePath.append(packageSuffix.substr(startPos, dotPos - startPos));
        packagePath.append("/");

        startPos = dotPos + 1;
    }
    CHECK_LT(startPos + 1, packageSuffix.length());
    packagePath.append(packageSuffix.substr(startPos));
    packagePath.append("/");

    packagePath.append(sanitized ? fqName.sanitizedVersion() : fqName.version());
    packagePath.append("/");

    return packagePath;
}

status_t Coordinator::getPackageInterfaceFiles(
        const FQName &package,
        std::vector<std::string> *fileNames) const {
    fileNames->clear();

    const std::string packagePath = getPackagePath(package);

    DIR *dir = opendir(packagePath.c_str());

    if (dir == NULL) {
        LOG(ERROR) << "Could not open package path: " << packagePath;
        return -errno;
    }

    struct dirent *ent;
    while ((ent = readdir(dir)) != NULL) {
        if (ent->d_type != DT_REG) {
            continue;
        }

        const auto suffix = ".hal";
        const auto suffix_len = std::strlen(suffix);
        const auto d_namelen = strlen(ent->d_name);

        if (d_namelen < suffix_len
                || strcmp(ent->d_name + d_namelen - suffix_len, suffix)) {
            continue;
        }

        fileNames->push_back(std::string(ent->d_name, d_namelen - suffix_len));
    }

    closedir(dir);
    dir = NULL;

    std::sort(fileNames->begin(), fileNames->end(),
              [](const std::string& lhs, const std::string& rhs) -> bool {
                  if (lhs == "types") {
                      return true;
                  }
                  if (rhs == "types") {
                      return false;
                  }
                  return lhs < rhs;
              });

    return OK;
}

status_t Coordinator::appendPackageInterfacesToVector(
        const FQName &package,
        std::vector<FQName> *packageInterfaces) const {
    packageInterfaces->clear();

    std::vector<std::string> fileNames;
    status_t err = getPackageInterfaceFiles(package, &fileNames);

    if (err != OK) {
        return err;
    }

    for (const auto &fileName : fileNames) {
        FQName subFQName(
                package.package() + package.atVersion() + "::" + fileName);

        if (!subFQName.isValid()) {
            LOG(WARNING)
                << "Whole-package import encountered invalid filename '"
                << fileName
                << "' in package "
                << package.package()
                << package.atVersion();

            continue;
        }

        packageInterfaces->push_back(subFQName);
    }

    return OK;
}

std::string Coordinator::convertPackageRootToPath(const FQName &fqName) const {
    std::string packageRoot = getPackageRoot(fqName);

    if (*(packageRoot.end()--) != '.') {
        packageRoot += '.';
    }

    std::replace(packageRoot.begin(), packageRoot.end(), '.', '/');

    return packageRoot; // now converted to a path
}


status_t Coordinator::enforceRestrictionsOnPackage(const FQName &fqName) {
    // need fqName to be something like android.hardware.foo@1.0.
    // name and valueName is ignored.
    if (fqName.package().empty() || fqName.version().empty()) {
        LOG(ERROR) << "Cannot enforce restrictions on package " << fqName.string()
                   << ": package or version is missing.";
        return BAD_VALUE;
    }
    FQName package = fqName.getPackageAndVersion();
    // look up cache.
    if (mPackagesEnforced.find(package) != mPackagesEnforced.end()) {
        return OK;
    }

    // enforce all rules.
    status_t err;

    err = enforceMinorVersionUprevs(package);
    if (err != OK) {
        return err;
    }

    err = enforceHashes(package);
    if (err != OK) {
        return err;
    }

    // cache it so that it won't need to be enforced again.
    mPackagesEnforced.insert(package);
    return OK;
}

status_t Coordinator::enforceMinorVersionUprevs(const FQName &currentPackage) {
    if(!currentPackage.hasVersion()) {
        LOG(ERROR) << "Cannot enforce minor version uprevs for " << currentPackage.string()
                   << ": missing version.";
        return UNKNOWN_ERROR;
    }

    if (currentPackage.getPackageMinorVersion() == 0) {
        return OK; // ignore for @x.0
    }

    bool hasPrevPackage = false;
    FQName prevPacakge = currentPackage;
    while (prevPacakge.getPackageMinorVersion() > 0) {
        prevPacakge = prevPacakge.downRev();
        if (existdir(getPackagePath(prevPacakge).c_str())) {
            hasPrevPackage = true;
            break;
        }
    }
    if (!hasPrevPackage) {
        // no @x.z, where z < y, exist.
        return OK;
    }

    if (prevPacakge != currentPackage.downRev()) {
        LOG(ERROR) << "Cannot enforce minor version uprevs for " << currentPackage.string()
                   << ": Found package " << prevPacakge.string() << " but missing "
                   << currentPackage.downRev().string() << "; you cannot skip a minor version.";
        return UNKNOWN_ERROR;
    }

    status_t err;
    std::vector<FQName> packageInterfaces;
    err = appendPackageInterfacesToVector(currentPackage, &packageInterfaces);
    if (err != OK) {
        return err;
    }

    bool extendedInterface = false;
    for (const FQName &currentFQName : packageInterfaces) {
        if (currentFQName.name() == "types") {
            continue; // ignore types.hal
        }
        // Assume that currentFQName == android.hardware.foo@2.2::IFoo.
        // Then prevFQName == android.hardware.foo@2.1::IFoo.
        const Interface *iface = nullptr;
        AST *currentAST = parse(currentFQName);
        if (currentAST != nullptr) {
            iface = currentAST->getInterface();
        }
        if (iface == nullptr) {
            if (currentAST == nullptr) {
                LOG(WARNING) << "Warning: Skipping " << currentFQName.string()
                             << " because it could not be found or parsed"
                             << " or " << currentPackage.string()
                             << " doesn't pass all requirements.";
            } else {
                LOG(WARNING) << "Warning: Skipping " << currentFQName.string()
                             << " because the file might contain more than one interface.";
            }
            continue;
        }

        // android.hardware.foo@2.2::IFoo exists. Now make sure
        // @2.2::IFoo extends @2.1::IFoo. If any interface IFoo in @2.2
        // ensures this, @2.2 passes the enforcement.
        FQName prevFQName(prevPacakge.package(), prevPacakge.version(),
                currentFQName.name());
        if (iface->superType() == nullptr) {
            // @2.2::IFoo doesn't extend anything. (This is probably IBase.)
            continue;
        }
        if (iface->superType()->fqName() != prevFQName) {
            // @2.2::IFoo doesn't extend @2.1::IFoo.
            if (iface->superType()->fqName().getPackageAndVersion() ==
                    prevPacakge.getPackageAndVersion()) {
                LOG(ERROR) << "Cannot enforce minor version uprevs for " << currentPackage.string()
                           << ": " << iface->fqName().string() << " extends "
                           << iface->superType()->fqName().string() << ", which is not allowed.";
                return UNKNOWN_ERROR;
            }
            // @2.2::IFoo extends something from a package with a different package name.
            // Check the next interface.
            continue;
        }

        // @2.2::IFoo passes. Check next interface.
        extendedInterface = true;
        LOG(VERBOSE) << "enforceMinorVersionUprevs: " << currentFQName.string() << " passes.";
    }

    if (!extendedInterface) {
        // No interface extends the interface with the same name in @x.(y-1).
        LOG(ERROR) << currentPackage.string() << " doesn't pass minor version uprev requirement. "
                   << "Requires at least one interface to extend an interface with the same name "
                   << "from " << prevPacakge.string() << ".";
        return UNKNOWN_ERROR;
    }

    return OK;
}

status_t Coordinator::enforceHashes(const FQName &currentPackage) {
    status_t err = OK;
    std::vector<FQName> packageInterfaces;
    err = appendPackageInterfacesToVector(currentPackage, &packageInterfaces);
    if (err != OK) {
        return err;
    }

    for (const FQName &currentFQName : packageInterfaces) {
        AST *ast = parse(currentFQName);

        if (ast == nullptr) {
            err = UNKNOWN_ERROR;
            continue;
        }

        std::string hashPath = getPackageRootPath(currentFQName) + "/current.txt";
        std::string error;
        std::vector<std::string> frozen = Hash::lookupHash(hashPath, currentFQName.string(), &error);

        if (error.size() > 0) {
            LOG(ERROR) << error;
            err = UNKNOWN_ERROR;
            continue;
        }

        // hash not define, interface not frozen
        if (frozen.size() == 0) {
            continue;
        }

        std::string currentHash = Hash::getHash(ast->getFilename()).hexString();

        if(std::find(frozen.begin(), frozen.end(), currentHash) == frozen.end()) {
            LOG(ERROR) << currentFQName.string() << " has hash " << currentHash
                       << " which does not match hash on record. This interface has "
                       << "been frozen. Do not change it!";
            err = UNKNOWN_ERROR;
            continue;
        }
    }

    return err;
}

// static
bool Coordinator::MakeParentHierarchy(const std::string &path) {
    static const mode_t kMode = 0755;

    size_t start = 1;  // Ignore leading '/'
    size_t slashPos;
    while ((slashPos = path.find("/", start)) != std::string::npos) {
        std::string partial = path.substr(0, slashPos);

        struct stat st;
        if (stat(partial.c_str(), &st) < 0) {
            if (errno != ENOENT) {
                return false;
            }

            int res = mkdir(partial.c_str(), kMode);
            if (res < 0) {
                return false;
            }
        } else if (!S_ISDIR(st.st_mode)) {
            return false;
        }

        start = slashPos + 1;
    }

    return true;
}

}  // namespace android

