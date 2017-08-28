#include "test/vts/specification/hal/NfcClientCallback.vts.h"
#include "vts_measurement.h"
#include <iostream>
#include <hidl/HidlSupport.h>
#include <android/hardware/nfc/1.0/INfcClientCallback.h>
#include "test/vts/specification/hal/types.vts.h"
#include <android/hidl/base/1.0/types.h>


using namespace android::hardware::nfc::V1_0;
namespace android {
namespace vts {

::android::hardware::Return<void> Vts_android_hardware_nfc_V1_0_INfcClientCallback::sendEvent(
    ::android::hardware::nfc::V1_0::NfcEvent arg0,
    ::android::hardware::nfc::V1_0::NfcStatus arg1) {
    cout << "sendEvent called" << endl;
    AndroidSystemCallbackRequestMessage callback_message;
    callback_message.set_id(GetCallbackID("sendEvent"));
    callback_message.set_name("Vts_android_hardware_nfc_V1_0_INfcClientCallback::sendEvent");
    VariableSpecificationMessage* var_msg0 = callback_message.add_arg();
    var_msg0->set_type(TYPE_ENUM);
    SetResult__android__hardware__nfc__V1_0__NfcEvent(var_msg0, arg0);
    VariableSpecificationMessage* var_msg1 = callback_message.add_arg();
    var_msg1->set_type(TYPE_ENUM);
    SetResult__android__hardware__nfc__V1_0__NfcStatus(var_msg1, arg1);
    RpcCallToAgent(callback_message, callback_socket_name_);
    return ::android::hardware::Void();
}

::android::hardware::Return<void> Vts_android_hardware_nfc_V1_0_INfcClientCallback::sendData(
    const ::android::hardware::hidl_vec<uint8_t>& arg0) {
    cout << "sendData called" << endl;
    AndroidSystemCallbackRequestMessage callback_message;
    callback_message.set_id(GetCallbackID("sendData"));
    callback_message.set_name("Vts_android_hardware_nfc_V1_0_INfcClientCallback::sendData");
    VariableSpecificationMessage* var_msg0 = callback_message.add_arg();
    var_msg0->set_type(TYPE_VECTOR);
    var_msg0->set_vector_size(arg0.size());
    for (int i = 0; i < (int)arg0.size(); i++) {
        auto *var_msg0_vector_i = var_msg0->add_vector_value();
        var_msg0_vector_i->set_type(TYPE_SCALAR);
        var_msg0_vector_i->set_scalar_type("uint8_t");
        var_msg0_vector_i->mutable_scalar_value()->set_uint8_t(arg0[i]);
    }
    RpcCallToAgent(callback_message, callback_socket_name_);
    return ::android::hardware::Void();
}

sp<::android::hardware::nfc::V1_0::INfcClientCallback> VtsFuzzerCreateVts_android_hardware_nfc_V1_0_INfcClientCallback(const string& callback_socket_name) {
    static sp<::android::hardware::nfc::V1_0::INfcClientCallback> result;
    result = new Vts_android_hardware_nfc_V1_0_INfcClientCallback(callback_socket_name);
    return result;
}

}  // namespace vts
}  // namespace android
