/*
 * Copyright (C) 2017 The Android Open Source Project
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


#ifndef ANDROID_VINTF_HAL_MANIFEST_H
#define ANDROID_VINTF_HAL_MANIFEST_H

#include <map>
#include <string>
#include <utils/Errors.h>
#include <vector>

#include "ManifestHal.h"
#include "MapValueIterator.h"
#include "SchemaType.h"
#include "Version.h"
#include "Vndk.h"

namespace android {
namespace vintf {

struct MatrixHal;
struct CompatibilityMatrix;

// A HalManifest is reported by the hardware and query-able from
// framework code. This is the API for the framework.
struct HalManifest {
public:

    // manifest.version
    constexpr static Version kVersion{1, 0};

    // Construct a device HAL manifest.
    HalManifest() : mType(SchemaType::DEVICE) {}

    // Given a component name (e.g. "android.hardware.camera"),
    // return getHal(name)->transport if the component exist and v exactly matches
    // one of the versions in that component, else EMPTY
    Transport getTransport(const std::string &name, const Version &v,
            const std::string &interfaceName, const std::string &instanceName) const;

    // Given a component name (e.g. "android.hardware.camera"),
    // return a list of version numbers that are supported by the hardware.
    // If the component is not found, empty list is returned.
    // If multiple matches, return a concatenation of version entries
    // (dupes removed)
    std::set<Version> getSupportedVersions(const std::string &name) const;

    // Given a component name (e.g. "android.hardware.camera") and an interface
    // name, return all instance names for that interface.
    // * If the component ("android.hardware.camera") does not exist, return empty list
    // * If the component ("android.hardware.camera") does exist,
    //    * If the interface (ICamera) does not exist, return empty list
    //    * Else return the list hal.interface.instance
    std::set<std::string> getInstances(
            const std::string &halName, const std::string &interfaceName) const;

    // Convenience method for checking if instanceName is in getInstances(halName, interfaceName)
    bool hasInstance(const std::string &halName,
            const std::string &interfaceName, const std::string &instanceName) const;

    // Return a list of component names that does NOT conform to
    // the given compatibility matrix. It contains components that are optional
    // for the framework if includeOptional = true.
    // Note: only HAL entries are checked. To check other entries as well, use
    // checkCompatibility.
    std::vector<std::string> checkIncompatibility(const CompatibilityMatrix &mat,
            bool includeOptional = true) const;

    // Check compatibility against a compatibility matrix. Considered compatible if
    // - framework manifest vs. device compat-mat
    //     - checkIncompatibility for HALs returns only optional HALs
    //     - one of manifest.vndk match compat-mat.vndk
    // - device manifest vs. framework compat-mat
    //     - checkIncompatibility for HALs returns only optional HALs
    //     - manifest.sepolicy.version match one of compat-mat.sepolicy.sepolicy-version
    bool checkCompatibility(const CompatibilityMatrix &mat, std::string *error = nullptr) const;

    // Generate a compatibility matrix such that checkCompatibility will return true.
    CompatibilityMatrix generateCompatibleMatrix() const;

    // Add an hal to this manifest so that a HalManifest can be constructed programatically.
    bool add(ManifestHal &&hal);

    // Returns all component names.
    std::set<std::string> getHalNames() const;

    // Returns all component names and versions, e.g.
    // "android.hardware.camera.device@1.0", "android.hardware.camera.device@3.2",
    // "android.hardware.nfc@1.0"]
    std::set<std::string> getHalNamesAndVersions() const;

    // Given a component name (e.g. "android.hardware.camera"),
    // return a list of interface names of that component.
    // If the component is not found, empty list is returned.
    std::set<std::string> getInterfaceNames(const std::string &name) const;

    // Type of the manifest. FRAMEWORK or DEVICE.
    SchemaType type() const;

    // Get all hals with the name
    std::vector<const ManifestHal *> getHals(const std::string &name) const;
    std::vector<ManifestHal *> getHals(const std::string &name);

    // device.mSepolicyVersion. Assume type == device.
    // Abort if type != device.
    const Version &sepolicyVersion() const;

    // framework.mVndks. Assume type == framework.
    // Abort if type != framework.
    const std::vector<Vndk> &vndks() const;

private:
    friend struct HalManifestConverter;
    friend class VintfObject;
    friend class AssembleVintf;
    friend struct LibVintfTest;
    friend std::string dump(const HalManifest &vm);
    friend bool operator==(const HalManifest &lft, const HalManifest &rgt);

    // Check before add()
    bool shouldAdd(const ManifestHal& toAdd) const;

    // Return an iterable to all ManifestHal objects. Call it as follows:
    // for (const ManifestHal &e : vm.getHals()) { }
    ConstMultiMapValueIterable<std::string, ManifestHal> getHals() const;

    // Get any HAL component based on the component name. Return any one
    // if multiple. Return nullptr if the component does not exist. This is only
    // for creating HalManifest objects programatically.
    // The component name looks like:
    // android.hardware.foo
    ManifestHal *getAnyHal(const std::string &name);

    status_t fetchAllInformation(const std::string &path);

    // Check if all instances in matrixHal is supported in this manifest.
    bool isCompatible(const MatrixHal& matrixHal) const;

    SchemaType mType;

    // sorted map from component name to the component.
    // The component name looks like: android.hardware.foo
    std::multimap<std::string, ManifestHal> mHals;

    // entries for device hal manifest only
    struct {
        Version mSepolicyVersion;
    } device;

    // entries for framework hal manifest only
    struct {
        std::vector<Vndk> mVndks;
    } framework;
};


} // namespace vintf
} // namespace android

#endif // ANDROID_VINTF_HAL_MANIFEST_H
