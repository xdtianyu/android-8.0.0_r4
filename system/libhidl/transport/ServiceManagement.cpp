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

#define LOG_TAG "ServiceManagement"

#include <android/dlext.h>
#include <condition_variable>
#include <dlfcn.h>
#include <dirent.h>
#include <fstream>
#include <pthread.h>
#include <unistd.h>

#include <mutex>
#include <regex>

#include <hidl/HidlBinderSupport.h>
#include <hidl/ServiceManagement.h>
#include <hidl/Status.h>

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <hwbinder/IPCThreadState.h>
#include <hwbinder/Parcel.h>

#include <android/hidl/manager/1.0/IServiceManager.h>
#include <android/hidl/manager/1.0/BpHwServiceManager.h>
#include <android/hidl/manager/1.0/BnHwServiceManager.h>

#define RE_COMPONENT    "[a-zA-Z_][a-zA-Z_0-9]*"
#define RE_PATH         RE_COMPONENT "(?:[.]" RE_COMPONENT ")*"
static const std::regex gLibraryFileNamePattern("(" RE_PATH "@[0-9]+[.][0-9]+)-impl(.*?).so");

extern "C" {
    android_namespace_t* android_get_exported_namespace(const char*);
}

using android::base::WaitForProperty;

using android::hidl::manager::V1_0::IServiceManager;
using android::hidl::manager::V1_0::IServiceNotification;
using android::hidl::manager::V1_0::BpHwServiceManager;
using android::hidl::manager::V1_0::BnHwServiceManager;

namespace android {
namespace hardware {

namespace details {
extern Mutex gDefaultServiceManagerLock;
extern sp<android::hidl::manager::V1_0::IServiceManager> gDefaultServiceManager;
}  // namespace details

static const char* kHwServicemanagerReadyProperty = "hwservicemanager.ready";

void waitForHwServiceManager() {
    using std::literals::chrono_literals::operator""s;

    while (!WaitForProperty(kHwServicemanagerReadyProperty, "true", 1s)) {
        LOG(WARNING) << "Waited for hwservicemanager.ready for a second, waiting another...";
    }
}

bool endsWith(const std::string &in, const std::string &suffix) {
    return in.size() >= suffix.size() &&
           in.substr(in.size() - suffix.size()) == suffix;
}

bool startsWith(const std::string &in, const std::string &prefix) {
    return in.size() >= prefix.size() &&
           in.substr(0, prefix.size()) == prefix;
}

std::string binaryName() {
    std::ifstream ifs("/proc/self/cmdline");
    std::string cmdline;
    if (!ifs.is_open()) {
        return "";
    }
    ifs >> cmdline;

    size_t idx = cmdline.rfind("/");
    if (idx != std::string::npos) {
        cmdline = cmdline.substr(idx + 1);
    }

    return cmdline;
}

void tryShortenProcessName(const std::string &packageName) {
    std::string processName = binaryName();

    if (!startsWith(processName, packageName)) {
        return;
    }

    // e.x. android.hardware.module.foo@1.0 -> foo@1.0
    size_t lastDot = packageName.rfind('.');
    size_t secondDot = packageName.rfind('.', lastDot - 1);

    if (secondDot == std::string::npos) {
        return;
    }

    std::string newName = processName.substr(secondDot + 1,
            16 /* TASK_COMM_LEN */ - 1);
    ALOGI("Removing namespace from process name %s to %s.",
            processName.c_str(), newName.c_str());

    int rc = pthread_setname_np(pthread_self(), newName.c_str());
    ALOGI_IF(rc != 0, "Removing namespace from process name %s failed.",
            processName.c_str());
}

namespace details {

void onRegistration(const std::string &packageName,
                    const std::string& /* interfaceName */,
                    const std::string& /* instanceName */) {
    tryShortenProcessName(packageName);
}

}  // details

sp<IServiceManager> defaultServiceManager() {
    {
        AutoMutex _l(details::gDefaultServiceManagerLock);
        if (details::gDefaultServiceManager != NULL) {
            return details::gDefaultServiceManager;
        }

        if (access("/dev/hwbinder", F_OK|R_OK|W_OK) != 0) {
            // HwBinder not available on this device or not accessible to
            // this process.
            return nullptr;
        }

        waitForHwServiceManager();

        while (details::gDefaultServiceManager == NULL) {
            details::gDefaultServiceManager =
                    fromBinder<IServiceManager, BpHwServiceManager, BnHwServiceManager>(
                        ProcessState::self()->getContextObject(NULL));
            if (details::gDefaultServiceManager == NULL) {
                LOG(ERROR) << "Waited for hwservicemanager, but got nullptr.";
                sleep(1);
            }
        }
    }

    return details::gDefaultServiceManager;
}

std::vector<std::string> search(const std::string &path,
                              const std::string &prefix,
                              const std::string &suffix) {
    std::unique_ptr<DIR, decltype(&closedir)> dir(opendir(path.c_str()), closedir);
    if (!dir) return {};

    std::vector<std::string> results{};

    dirent* dp;
    while ((dp = readdir(dir.get())) != nullptr) {
        std::string name = dp->d_name;

        if (startsWith(name, prefix) &&
                endsWith(name, suffix)) {
            results.push_back(name);
        }
    }

    return results;
}

bool matchPackageName(const std::string &lib, std::string *matchedName) {
    std::smatch match;
    if (std::regex_match(lib, match, gLibraryFileNamePattern)) {
        *matchedName = match.str(1) + "::I*";
        return true;
    }
    return false;
}

static void registerReference(const hidl_string &interfaceName, const hidl_string &instanceName) {
    sp<IServiceManager> binderizedManager = defaultServiceManager();
    if (binderizedManager == nullptr) {
        LOG(WARNING) << "Could not registerReference for "
                     << interfaceName << "/" << instanceName
                     << ": null binderized manager.";
        return;
    }
    auto ret = binderizedManager->registerPassthroughClient(interfaceName, instanceName);
    if (!ret.isOk()) {
        LOG(WARNING) << "Could not registerReference for "
                     << interfaceName << "/" << instanceName
                     << ": " << ret.description();
        return;
    }
    LOG(VERBOSE) << "Successfully registerReference for "
                 << interfaceName << "/" << instanceName;
}

struct PassthroughServiceManager : IServiceManager {
    Return<sp<IBase>> get(const hidl_string& fqName,
                          const hidl_string& name) override {
        std::string stdFqName(fqName.c_str());

        //fqName looks like android.hardware.foo@1.0::IFoo
        size_t idx = stdFqName.find("::");

        if (idx == std::string::npos ||
                idx + strlen("::") + 1 >= stdFqName.size()) {
            LOG(ERROR) << "Invalid interface name passthrough lookup: " << fqName;
            return nullptr;
        }

        std::string packageAndVersion = stdFqName.substr(0, idx);
        std::string ifaceName = stdFqName.substr(idx + strlen("::"));

        const std::string prefix = packageAndVersion + "-impl";
        const std::string sym = "HIDL_FETCH_" + ifaceName;

        const android_namespace_t* sphal_namespace = android_get_exported_namespace("sphal");
        const int dlMode = RTLD_LAZY;
        void *handle = nullptr;

        // TODO: lookup in VINTF instead
        // TODO(b/34135607): Remove HAL_LIBRARY_PATH_SYSTEM

        dlerror(); // clear

        for (const std::string &path : {
            HAL_LIBRARY_PATH_ODM, HAL_LIBRARY_PATH_VENDOR, HAL_LIBRARY_PATH_SYSTEM
        }) {
            std::vector<std::string> libs = search(path, prefix, ".so");

            for (const std::string &lib : libs) {
                const std::string fullPath = path + lib;

                // If sphal namespace is available, try to load from the
                // namespace first. If it fails, fall back to the original
                // dlopen, which loads from the current namespace.
                if (sphal_namespace != nullptr && path != HAL_LIBRARY_PATH_SYSTEM) {
                    const android_dlextinfo dlextinfo = {
                        .flags = ANDROID_DLEXT_USE_NAMESPACE,
                        // const_cast is dirty but required because
                        // library_namespace field is non-const.
                        .library_namespace = const_cast<android_namespace_t*>(sphal_namespace),
                    };
                    handle = android_dlopen_ext(fullPath.c_str(), dlMode, &dlextinfo);
                    if (handle == nullptr) {
                        const char* error = dlerror();
                        LOG(WARNING) << "Failed to dlopen " << lib << " from sphal namespace:"
                                     << (error == nullptr ? "unknown error" : error);
                    } else {
                        LOG(DEBUG) << lib << " loaded from sphal namespace.";
                    }
                }
                if (handle == nullptr) {
                    handle = dlopen(fullPath.c_str(), dlMode);
                }

                if (handle == nullptr) {
                    const char* error = dlerror();
                    LOG(ERROR) << "Failed to dlopen " << lib << ": "
                               << (error == nullptr ? "unknown error" : error);
                    continue;
                }

                IBase* (*generator)(const char* name);
                *(void **)(&generator) = dlsym(handle, sym.c_str());
                if(!generator) {
                    const char* error = dlerror();
                    LOG(ERROR) << "Passthrough lookup opened " << lib
                               << " but could not find symbol " << sym << ": "
                               << (error == nullptr ? "unknown error" : error);
                    dlclose(handle);
                    continue;
                }

                IBase *interface = (*generator)(name.c_str());

                if (interface == nullptr) {
                    dlclose(handle);
                    continue; // this module doesn't provide this instance name
                }

                registerReference(fqName, name);

                return interface;
            }
        }

        return nullptr;
    }

    Return<bool> add(const hidl_string& /* name */,
                     const sp<IBase>& /* service */) override {
        LOG(FATAL) << "Cannot register services with passthrough service manager.";
        return false;
    }

    Return<Transport> getTransport(const hidl_string& /* fqName */,
                                   const hidl_string& /* name */) {
        LOG(FATAL) << "Cannot getTransport with passthrough service manager.";
        return Transport::EMPTY;
    }

    Return<void> list(list_cb /* _hidl_cb */) override {
        LOG(FATAL) << "Cannot list services with passthrough service manager.";
        return Void();
    }
    Return<void> listByInterface(const hidl_string& /* fqInstanceName */,
                                 listByInterface_cb /* _hidl_cb */) override {
        // TODO: add this functionality
        LOG(FATAL) << "Cannot list services with passthrough service manager.";
        return Void();
    }

    Return<bool> registerForNotifications(const hidl_string& /* fqName */,
                                          const hidl_string& /* name */,
                                          const sp<IServiceNotification>& /* callback */) override {
        // This makes no sense.
        LOG(FATAL) << "Cannot register for notifications with passthrough service manager.";
        return false;
    }

    Return<void> debugDump(debugDump_cb _hidl_cb) override {
        using Arch = ::android::hidl::base::V1_0::DebugInfo::Architecture;
        static std::vector<std::pair<Arch, std::vector<const char *>>> sAllPaths{
            {Arch::IS_64BIT, {HAL_LIBRARY_PATH_ODM_64BIT,
                                      HAL_LIBRARY_PATH_VENDOR_64BIT,
                                      HAL_LIBRARY_PATH_SYSTEM_64BIT}},
            {Arch::IS_32BIT, {HAL_LIBRARY_PATH_ODM_32BIT,
                                      HAL_LIBRARY_PATH_VENDOR_32BIT,
                                      HAL_LIBRARY_PATH_SYSTEM_32BIT}}
        };
        std::vector<InstanceDebugInfo> vec;
        for (const auto &pair : sAllPaths) {
            Arch arch = pair.first;
            for (const auto &path : pair.second) {
                std::vector<std::string> libs = search(path, "", ".so");
                for (const std::string &lib : libs) {
                    std::string matchedName;
                    if (matchPackageName(lib, &matchedName)) {
                        vec.push_back({
                            .interfaceName = matchedName,
                            .instanceName = "*",
                            .clientPids = {},
                            .arch = arch
                        });
                    }
                }
            }
        }
        _hidl_cb(vec);
        return Void();
    }

    Return<void> registerPassthroughClient(const hidl_string &, const hidl_string &) override {
        // This makes no sense.
        LOG(FATAL) << "Cannot call registerPassthroughClient on passthrough service manager. "
                   << "Call it on defaultServiceManager() instead.";
        return Void();
    }

};

sp<IServiceManager> getPassthroughServiceManager() {
    static sp<PassthroughServiceManager> manager(new PassthroughServiceManager());
    return manager;
}

namespace details {

struct Waiter : IServiceNotification {
    Return<void> onRegistration(const hidl_string& /* fqName */,
                                const hidl_string& /* name */,
                                bool /* preexisting */) override {
        std::unique_lock<std::mutex> lock(mMutex);
        if (mRegistered) {
            return Void();
        }
        mRegistered = true;
        lock.unlock();

        mCondition.notify_one();
        return Void();
    }

    void wait(const std::string &interface, const std::string &instanceName) {
        using std::literals::chrono_literals::operator""s;

        std::unique_lock<std::mutex> lock(mMutex);
        while(true) {
            mCondition.wait_for(lock, 1s, [this]{
                return mRegistered;
            });

            if (mRegistered) {
                break;
            }

            LOG(WARNING) << "Waited one second for "
                         << interface << "/" << instanceName
                         << ". Waiting another...";
        }
    }

private:
    std::mutex mMutex;
    std::condition_variable mCondition;
    bool mRegistered = false;
};

void waitForHwService(
        const std::string &interface, const std::string &instanceName) {
    const sp<IServiceManager> manager = defaultServiceManager();

    if (manager == nullptr) {
        LOG(ERROR) << "Could not get default service manager.";
        return;
    }

    sp<Waiter> waiter = new Waiter();
    Return<bool> ret = manager->registerForNotifications(interface, instanceName, waiter);

    if (!ret.isOk()) {
        LOG(ERROR) << "Transport error, " << ret.description()
            << ", during notification registration for "
            << interface << "/" << instanceName << ".";
        return;
    }

    if (!ret) {
        LOG(ERROR) << "Could not register for notifications for "
            << interface << "/" << instanceName << ".";
        return;
    }

    waiter->wait(interface, instanceName);
}

}; // namespace details

}; // namespace hardware
}; // namespace android
