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

#include <functional>
#include <iostream>
#include <map>
#include <set>
#include <sstream>
#include <string>
#include <vector>

#include <android/hidl/manager/1.0/IServiceManager.h>
#include <gtest/gtest.h>
#include <hidl-hash/Hash.h>
#include <hidl-util/FQName.h>
#include <hidl/ServiceManagement.h>
#include <vintf/HalManifest.h>
#include <vintf/VintfObject.h>

using android::FQName;
using android::Hash;
using android::hardware::hidl_array;
using android::hardware::hidl_string;
using android::hardware::hidl_vec;
using android::hidl::manager::V1_0::IServiceManager;
using android::sp;
using android::vintf::HalManifest;
using android::vintf::Transport;
using android::vintf::Version;

using std::cout;
using std::endl;
using std::map;
using std::set;
using std::string;
using std::vector;
using HalVerifyFn =
    std::function<void(const FQName &fq_name, const string &instance_name)>;
using HashCharArray = hidl_array<unsigned char, 32>;

// Path to directory on target containing test data.
static const string kDataDir = "/data/local/tmp/";

// Name of file containing HAL hashes.
static const string kHashFileName = "current.txt";

// Map from package name to package root.
static const map<string, string> kPackageRoot = {
    {"android.frameworks", "frameworks/hardware/interfaces/"},
    {"android.hardware", "hardware/interfaces/"},
    {"android.hidl", "system/libhidl/transport/"},
    {"android.system", "system/hardware/interfaces/"},
};

// HALs that are allowed to be passthrough under Treble rules.
static const set<string> kPassthroughHals = {
    "android.hardware.graphics.mapper", "android.hardware.renderscript",
};

// For a given interface returns package root if known. Returns empty string
// otherwise.
static const string PackageRoot(const FQName &fq_iface_name) {
  for (const auto &package_root : kPackageRoot) {
    if (fq_iface_name.inPackage(package_root.first)) {
      return package_root.second;
    }
  }
  return "";
}

// Returns true iff HAL interface is Google-defined.
static bool IsGoogleDefinedIface(const FQName &fq_iface_name) {
  // Package roots are only known for Google-defined packages.
  return !PackageRoot(fq_iface_name).empty();
}

// Returns true iff HAL interface is exempt from following rules:
// 1. If an interface is declared in VINTF, it has to be served on the device.
// TODO(b/62547028): remove these exemptions in O-DR.
static bool IsExempt(const FQName &fq_iface_name) {
  static const set<string> exempt_hals_ = {
      "android.hardware.radio", "android.hardware.radio.deprecated",
  };
  string hal_name = fq_iface_name.package();
  // Radio-releated and non-Google HAL interfaces are given exemptions.
  return exempt_hals_.find(hal_name) != exempt_hals_.end() ||
         !IsGoogleDefinedIface(fq_iface_name);
}

// Returns the set of released hashes for a given HAL interface.
static set<string> ReleasedHashes(const FQName &fq_iface_name) {
  set<string> released_hashes{};
  string err = "";

  string file_path = kDataDir + PackageRoot(fq_iface_name) + kHashFileName;
  auto hashes = Hash::lookupHash(file_path, fq_iface_name.string(), &err);
  released_hashes.insert(hashes.begin(), hashes.end());
  return released_hashes;
}

class VtsTrebleVintfTest : public ::testing::Test {
 public:
  virtual void SetUp() override {
    default_manager_ = ::android::hardware::defaultServiceManager();
    ASSERT_NE(default_manager_, nullptr)
        << "Failed to get default service manager." << endl;

    passthrough_manager_ = ::android::hardware::getPassthroughServiceManager();
    ASSERT_NE(passthrough_manager_, nullptr)
        << "Failed to get passthrough service manager." << endl;

    vendor_manifest_ = ::android::vintf::VintfObject::GetDeviceHalManifest();
    ASSERT_NE(passthrough_manager_, nullptr)
        << "Failed to get vendor HAL manifest." << endl;
  }

  // Applies given function to each HAL instance in VINTF.
  void ForEachHalInstance(HalVerifyFn);
  // Retrieves an existing HAL service.
  sp<android::hidl::base::V1_0::IBase> GetHalService(
      const FQName &fq_name, const string &instance_name);

  // Default service manager.
  sp<IServiceManager> default_manager_;
  // Passthrough service manager.
  sp<IServiceManager> passthrough_manager_;
  // Vendor hal manifest.
  const HalManifest *vendor_manifest_;
};

void VtsTrebleVintfTest::ForEachHalInstance(HalVerifyFn fn) {
  auto hal_names = vendor_manifest_->getHalNames();
  for (const auto &hal_name : hal_names) {
    auto versions = vendor_manifest_->getSupportedVersions(hal_name);
    auto iface_names = vendor_manifest_->getInterfaceNames(hal_name);
    for (const auto &iface_name : iface_names) {
      auto instance_names =
          vendor_manifest_->getInstances(hal_name, iface_name);
      for (const auto &version : versions) {
        for (const auto &instance_name : instance_names) {
          string major_ver = std::to_string(version.majorVer);
          string minor_ver = std::to_string(version.minorVer);
          string full_ver = major_ver + "." + minor_ver;
          FQName fq_name{hal_name, full_ver, iface_name};
          fn(fq_name, instance_name);
        }
      }
    }
  }
}

sp<android::hidl::base::V1_0::IBase> VtsTrebleVintfTest::GetHalService(
    const FQName &fq_name, const string &instance_name) {
  string hal_name = fq_name.package();
  Version version{fq_name.getPackageMajorVersion(),
                  fq_name.getPackageMinorVersion()};
  string iface_name = fq_name.name();
  string fq_iface_name = fq_name.string();
  cout << "Getting service of: " << fq_iface_name << endl;

  Transport transport = vendor_manifest_->getTransport(
      hal_name, version, iface_name, instance_name);

  android::sp<android::hidl::base::V1_0::IBase> hal_service = nullptr;
  if (transport == Transport::HWBINDER) {
    hal_service = default_manager_->get(fq_iface_name, instance_name);
  } else if (transport == Transport::PASSTHROUGH) {
    hal_service = passthrough_manager_->get(fq_iface_name, instance_name);
  }
  return hal_service;
}

// Tests that all HAL entries in VINTF has all required fields filled out.
TEST_F(VtsTrebleVintfTest, HalEntriesAreComplete) {
  auto hal_names = vendor_manifest_->getHalNames();
  for (const auto &hal_name : hal_names) {
    auto versions = vendor_manifest_->getSupportedVersions(hal_name);
    EXPECT_FALSE(versions.empty())
        << hal_name << " has no version specified in VINTF.";
    auto iface_names = vendor_manifest_->getInterfaceNames(hal_name);
    EXPECT_FALSE(iface_names.empty())
        << hal_name << " has no interface specified in VINTF.";
    for (const auto &iface_name : iface_names) {
      auto instances = vendor_manifest_->getInstances(hal_name, iface_name);
      EXPECT_FALSE(instances.empty())
          << hal_name << " has no instance specified in VINTF.";
    }
  }
}

// Tests that no HAL outside of the allowed set is specified as passthrough in
// VINTF.
TEST_F(VtsTrebleVintfTest, HalsAreBinderized) {
  // Verifies that HAL is binderized unless it's allowed to be passthrough.
  HalVerifyFn is_binderized = [this](const FQName &fq_name,
                                     const string &instance_name) {
    cout << "Verifying transport method of: " << fq_name.string() << endl;
    string hal_name = fq_name.package();
    Version version{fq_name.getPackageMajorVersion(),
                    fq_name.getPackageMinorVersion()};
    string iface_name = fq_name.name();

    Transport transport = vendor_manifest_->getTransport(
        hal_name, version, iface_name, instance_name);

    EXPECT_NE(transport, Transport::EMPTY)
        << hal_name << " has no transport specified in VINTF.";

    if (transport == Transport::PASSTHROUGH) {
      EXPECT_NE(kPassthroughHals.find(hal_name), kPassthroughHals.end())
          << hal_name << " can't be passthrough under Treble rules.";
    }
  };

  ForEachHalInstance(is_binderized);
}

// Tests that all HALs specified in the VINTF are available through service
// manager.
TEST_F(VtsTrebleVintfTest, VintfHalsAreServed) {
  // Verifies that HAL is available through service manager.
  HalVerifyFn is_available = [this](const FQName &fq_name,
                                    const string &instance_name) {
    if (IsExempt(fq_name)) {
      cout << fq_name.string() << " is exempt." << endl;
      return;
    }

    sp<android::hidl::base::V1_0::IBase> hal_service =
        GetHalService(fq_name, instance_name);
    EXPECT_NE(hal_service, nullptr)
        << fq_name.package() << " not available." << endl;
  };

  ForEachHalInstance(is_available);
}

// Tests that HAL interfaces are officially released.
TEST_F(VtsTrebleVintfTest, InterfacesAreReleased) {
  // Verifies that HAL are released by fetching the hash of the interface and
  // comparing it to the set of known hashes of released interfaces.
  HalVerifyFn is_released = [this](const FQName &fq_name,
                                   const string &instance_name) {
    sp<android::hidl::base::V1_0::IBase> hal_service =
        GetHalService(fq_name, instance_name);

    if (hal_service == nullptr) {
      if (IsExempt(fq_name)) {
        cout << fq_name.string() << " is exempt." << endl;
      } else {
        ADD_FAILURE() << fq_name.package() << " not available." << endl;
      }
      return;
    }

    vector<string> iface_chain{};
    hal_service->interfaceChain(
        [&iface_chain](const hidl_vec<hidl_string> &chain) {
          for (const auto &iface_name : chain) {
            iface_chain.push_back(iface_name);
          }
        });

    vector<string> hash_chain{};
    hal_service->getHashChain(
        [&hash_chain](const hidl_vec<HashCharArray> &chain) {
          for (const HashCharArray &hash_array : chain) {
            vector<uint8_t> hash{hash_array.data(),
                                 hash_array.data() + hash_array.size()};
            hash_chain.push_back(Hash::hexString(hash));
          }
        });

    ASSERT_EQ(iface_chain.size(), hash_chain.size());
    for (size_t i = 0; i < iface_chain.size(); ++i) {
      FQName fq_iface_name{iface_chain[i]};
      string hash = hash_chain[i];

      if (IsGoogleDefinedIface(fq_iface_name)) {
        set<string> released_hashes = ReleasedHashes(fq_iface_name);
        EXPECT_NE(released_hashes.find(hash), released_hashes.end())
            << "Hash not found. This interface was not released." << endl
            << "Interface name: " << fq_iface_name.string() << endl
            << "Hash: " << hash << endl;
      }
    }
  };

  ForEachHalInstance(is_released);
}

int main(int argc, char **argv) {
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
