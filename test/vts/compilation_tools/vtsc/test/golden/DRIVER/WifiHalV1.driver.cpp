#include "test/vts/specification/hal/conventional/wifi/1.0/WifiHalV1.vts.h"
#include <hardware/hardware.h>
#include <hardware_legacy/wifi_hal.h>
#include "vts_datatype.h"
#include "vts_measurement.h"
#include <iostream>


namespace android {
namespace vts {
bool FuzzerExtended_wifi::Fuzz(
    FunctionSpecificationMessage* func_msg,
    void** result, const string& callback_socket_name) {
    const char* func_name = func_msg->name().c_str();
    cout << "Function: " << func_name << endl;
    if (!strcmp(func_name, "wifi_initialize")) {
        wifi_handle* arg0 = (wifi_handle*) malloc(sizeof(wifi_handle));
        cout << "arg0 = " << arg0 << endl;
        typedef void* (*func_type_wifi_initialize)(...);
    *result = const_cast<void*>(reinterpret_cast<const void*>(    ((func_type_wifi_initialize) target_loader_.GetLoaderFunction("wifi_initialize"))(
          arg0)));
        return true;
      }
    return false;
}
bool FuzzerExtended_wifi::GetAttribute(
    FunctionSpecificationMessage* func_msg,
    void** result) {
    const char* func_name = func_msg->name().c_str();
    cout << "Function: " << __func__ << " '" << func_name << "'" << endl;
    cerr << "attribute not supported for legacy hal yet" << endl;
    return false;
}
bool FuzzerExtended_wifi::CallFunction(const FunctionSpecificationMessage&, const string&, FunctionSpecificationMessage* ) {
    /* No implementation yet. */
    return true;
}
bool FuzzerExtended_wifi::VerifyResults(const FunctionSpecificationMessage&, const FunctionSpecificationMessage&) {
    /* No implementation yet. */
    return true;
}
extern "C" {
android::vts::FuzzerBase* vts_func_3_5_1_() {
    return (android::vts::FuzzerBase*) new android::vts::FuzzerExtended_wifi();
}

}
}  // namespace vts
}  // namespace android
