#ifndef __VTS_DRIVER__android_hardware_nfc_V1_0_INfcClientCallback__
#define __VTS_DRIVER__android_hardware_nfc_V1_0_INfcClientCallback__

#undef LOG_TAG
#define LOG_TAG "FuzzerExtended_android_hardware_nfc_V1_0_INfcClientCallback"

#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include <utils/Log.h>

#include <fuzz_tester/FuzzerBase.h>
#include <fuzz_tester/FuzzerCallbackBase.h>

#include <VtsDriverCommUtil.h>

#include <android/hardware/nfc/1.0/INfcClientCallback.h>
#include <hidl/HidlSupport.h>
#include <android/hardware/nfc/1.0/types.h>
#include <android/hardware/nfc/1.0/types.vts.h>
#include <android/hidl/base/1.0/types.h>


using namespace android::hardware::nfc::V1_0;
namespace android {
namespace vts {

class Vts_android_hardware_nfc_V1_0_INfcClientCallback : public ::android::hardware::nfc::V1_0::INfcClientCallback, public FuzzerCallbackBase {
 public:
    Vts_android_hardware_nfc_V1_0_INfcClientCallback(const string& callback_socket_name)
        : callback_socket_name_(callback_socket_name) {};

    virtual ~Vts_android_hardware_nfc_V1_0_INfcClientCallback() = default;

    ::android::hardware::Return<void> sendEvent(
        ::android::hardware::nfc::V1_0::NfcEvent arg0,
        ::android::hardware::nfc::V1_0::NfcStatus arg1) override;

    ::android::hardware::Return<void> sendData(
        const ::android::hardware::hidl_vec<uint8_t>& arg0) override;


 private:
    const string& callback_socket_name_;
};

sp<::android::hardware::nfc::V1_0::INfcClientCallback> VtsFuzzerCreateVts_android_hardware_nfc_V1_0_INfcClientCallback(const string& callback_socket_name);



}  // namespace vts
}  // namespace android
#endif
