#include <android-base/logging.h>
#include <hidl-util/FQName.h>
#include <log/log.h>

#include "AccessControl.h"

namespace android {

static const char *kPermissionAdd = "add";
static const char *kPermissionGet = "find";
static const char *kPermissionList = "list";

struct audit_data {
    const char* interfaceName;
    pid_t       pid;
};

using android::FQName;

AccessControl::AccessControl() {
    mSeHandle = selinux_android_hw_service_context_handle();
    LOG_ALWAYS_FATAL_IF(mSeHandle == NULL, "Failed to acquire SELinux handle.");

    if (getcon(&mSeContext) != 0) {
        LOG_ALWAYS_FATAL("Failed to acquire hwservicemanager context.");
    }

    selinux_status_open(true);

    mSeCallbacks.func_audit = AccessControl::auditCallback;
    selinux_set_callback(SELINUX_CB_AUDIT, mSeCallbacks);

    mSeCallbacks.func_log = selinux_log_callback; /* defined in libselinux */
    selinux_set_callback(SELINUX_CB_LOG, mSeCallbacks);
}

bool AccessControl::canAdd(const std::string& fqName, pid_t pid) {
    FQName fqIface(fqName);

    if (!fqIface.isValid()) {
        return false;
    }
    const std::string checkName = fqIface.package() + "::" + fqIface.name();

    return checkPermission(pid, kPermissionAdd, checkName.c_str());
}

bool AccessControl::canGet(const std::string& fqName, pid_t pid) {
    FQName fqIface(fqName);

    if (!fqIface.isValid()) {
        return false;
    }
    const std::string checkName = fqIface.package() + "::" + fqIface.name();

    return checkPermission(pid, kPermissionGet, checkName.c_str());
}

bool AccessControl::canList(pid_t pid) {
    return checkPermission(pid, mSeContext, kPermissionList, nullptr);
}

bool AccessControl::checkPermission(pid_t sourcePid, const char *targetContext,
                                    const char *perm, const char *interface) {
    char *sourceContext = NULL;
    bool allowed = false;
    struct audit_data ad;

    if (getpidcon(sourcePid, &sourceContext) < 0) {
        ALOGE("SELinux: failed to retrieved process context for pid %d", sourcePid);
        return false;
    }

    ad.pid = sourcePid;
    ad.interfaceName = interface;

    allowed = (selinux_check_access(sourceContext, targetContext, "hwservice_manager",
                                    perm, (void *) &ad) == 0);

    freecon(sourceContext);

    return allowed;
}

bool AccessControl::checkPermission(pid_t sourcePid, const char *perm, const char *interface) {
    char *targetContext = NULL;
    bool allowed = false;

    // Lookup service in hwservice_contexts
    if (selabel_lookup(mSeHandle, &targetContext, interface, 0) != 0) {
        ALOGE("No match for interface %s in hwservice_contexts", interface);
        return false;
    }

    allowed = checkPermission(sourcePid, targetContext, perm, interface);

    freecon(targetContext);

    return allowed;
}

int AccessControl::auditCallback(void *data, security_class_t /*cls*/, char *buf, size_t len) {
    struct audit_data *ad = (struct audit_data *)data;

    if (!ad || !ad->interfaceName) {
        ALOGE("No valid hwservicemanager audit data");
        return 0;
    }

    snprintf(buf, len, "interface=%s pid=%d", ad->interfaceName, ad->pid);
    return 0;
}

} // namespace android
