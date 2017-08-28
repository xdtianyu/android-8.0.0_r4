#include "test/vts/specification/hal/conventional/bluetooth/1.0/BluetoothHalV1.vts.h"
#include <hardware/hardware.h>
#include <hardware/bluetooth.h>
#include "vts_datatype.h"
#include "vts_measurement.h"
#include <iostream>


namespace android {
namespace vts {
bool FuzzerExtended_bluetooth_module_t::Fuzz(
    FunctionSpecificationMessage* func_msg,
    void** result, const string& callback_socket_name) {
    const char* func_name = func_msg->name().c_str();
    cout << "Function: " << __func__ << " '" << func_name << "'" << endl;
    bluetooth_module_t* local_device = reinterpret_cast<bluetooth_module_t*>(device_);
    if (local_device == NULL) {
        cout << "use hmi " << (uint64_t)hmi_ << endl;
        local_device = reinterpret_cast<bluetooth_module_t*>(hmi_);
    }
    if (local_device == NULL) {
        cerr << "both device_ and hmi_ are NULL." << endl;
        return false;
    }
    if (!strcmp(func_name, "get_bluetooth_interface")) {
        cout << "match" << endl;
        VtsMeasurement vts_measurement;
        vts_measurement.Start();
        cout << "hit2." << device_ << endl;
        if (reinterpret_cast<bluetooth_module_t*>(local_device)->get_bluetooth_interface == NULL) {
            cerr << "api not set." << endl;
            return false;
        }
        cout << "Call an API." << endl;
        cout << "local_device = " << local_device;
        *result = const_cast<void*>(reinterpret_cast<const void*>(local_device->get_bluetooth_interface()));
        vector<float>* measured = vts_measurement.Stop();
        cout << "time " << (*measured)[0] << endl;
        cout << "called" << endl;
        return true;
    }
    cerr << "func not found" << endl;
    return false;
}
bool FuzzerExtended_bluetooth_module_t::GetAttribute(
    FunctionSpecificationMessage* func_msg,
    void** result) {
    const char* func_name = func_msg->name().c_str();
    cout << "Function: " << __func__ << " '" << func_name << "'" << endl;
    bluetooth_module_t* local_device = reinterpret_cast<bluetooth_module_t*>(device_);
    if (local_device == NULL) {
        cout << "use hmi " << (uint64_t)hmi_ << endl;
        local_device = reinterpret_cast<bluetooth_module_t*>(hmi_);
    }
    if (local_device == NULL) {
        cerr << "both device_ and hmi_ are NULL." << endl;
        return false;
    }
    cerr << "attribute not found" << endl;
    return false;
}
bool FuzzerExtended_bluetooth_module_t::CallFunction(const FunctionSpecificationMessage&, const string&, FunctionSpecificationMessage* ) {
    /* No implementation yet. */
    return true;
}
bool FuzzerExtended_bluetooth_module_t::VerifyResults(const FunctionSpecificationMessage&, const FunctionSpecificationMessage&) {
    /* No implementation yet. */
    return true;
}
extern "C" {
android::vts::FuzzerBase* vts_func_1_7_1_() {
    return (android::vts::FuzzerBase*) new android::vts::FuzzerExtended_bluetooth_module_t();
}

}
}  // namespace vts
}  // namespace android
