#define LOG_TAG "hwservicemanager"

#include <utils/Log.h>

#include <inttypes.h>
#include <unistd.h>

#include <android/hidl/manager/1.0/BnHwServiceManager.h>
#include <android/hidl/manager/1.0/IServiceManager.h>
#include <android/hidl/token/1.0/ITokenManager.h>
#include <cutils/properties.h>
#include <hidl/Status.h>
#include <hwbinder/IPCThreadState.h>
#include <utils/Errors.h>
#include <utils/Looper.h>
#include <utils/StrongPointer.h>

#include "ServiceManager.h"
#include "TokenManager.h"

// libutils:
using android::BAD_TYPE;
using android::Looper;
using android::LooperCallback;
using android::OK;
using android::sp;
using android::status_t;

// libhwbinder:
using android::hardware::IPCThreadState;

// libhidl
using android::hardware::configureRpcThreadpool;
using android::hardware::hidl_string;
using android::hardware::hidl_vec;

// hidl types
using android::hidl::manager::V1_0::BnHwServiceManager;
using android::hidl::manager::V1_0::IServiceManager;
using android::hidl::token::V1_0::ITokenManager;

// implementations
using android::hidl::manager::V1_0::implementation::ServiceManager;
using android::hidl::token::V1_0::implementation::TokenManager;

static std::string serviceName = "default";

class BinderCallback : public LooperCallback {
public:
    BinderCallback() {}
    ~BinderCallback() override {}

    int handleEvent(int /* fd */, int /* events */, void* /* data */) override {
        IPCThreadState::self()->handlePolledCommands();
        return 1;  // Continue receiving callbacks.
    }
};

int main() {
    configureRpcThreadpool(1, true /* callerWillJoin */);

    ServiceManager *manager = new ServiceManager();

    if (!manager->add(serviceName, manager)) {
        ALOGE("Failed to register hwservicemanager with itself.");
    }

    TokenManager *tokenManager = new TokenManager();

    if (!manager->add(serviceName, tokenManager)) {
        ALOGE("Failed to register ITokenManager with hwservicemanager.");
    }

    sp<Looper> looper(Looper::prepare(0 /* opts */));

    int binder_fd = -1;

    IPCThreadState::self()->setupPolling(&binder_fd);
    if (binder_fd < 0) {
        ALOGE("Failed to aquire binder FD. Aborting...");
        return -1;
    }
    // Flush after setupPolling(), to make sure the binder driver
    // knows about this thread handling commands.
    IPCThreadState::self()->flushCommands();

    sp<BinderCallback> cb(new BinderCallback);
    if (looper->addFd(binder_fd, Looper::POLL_CALLBACK, Looper::EVENT_INPUT, cb,
            nullptr) != 1) {
        ALOGE("Failed to add hwbinder FD to Looper. Aborting...");
        return -1;
    }

    // Tell IPCThreadState we're the service manager
    sp<BnHwServiceManager> service = new BnHwServiceManager(manager);
    IPCThreadState::self()->setTheContextObject(service);
    // Then tell binder kernel
    ioctl(binder_fd, BINDER_SET_CONTEXT_MGR, 0);
    // Only enable FIFO inheritance for hwbinder
    // FIXME: remove define when in the kernel
#define BINDER_SET_INHERIT_FIFO_PRIO    _IO('b', 10)

    int rc = ioctl(binder_fd, BINDER_SET_INHERIT_FIFO_PRIO);
    if (rc) {
        ALOGE("BINDER_SET_INHERIT_FIFO_PRIO failed with error %d\n", rc);
    }

    rc = property_set("hwservicemanager.ready", "true");
    if (rc) {
        ALOGE("Failed to set \"hwservicemanager.ready\" (error %d). "\
              "HAL services will not start!\n", rc);
    }

    while (true) {
        looper->pollAll(-1 /* timeoutMillis */);
    }

    return 0;
}
