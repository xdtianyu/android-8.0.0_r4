#ifndef __VTS_DRIVER__BluetoothHalV1bt_interface_t.driver__
#define __VTS_DRIVER__BluetoothHalV1bt_interface_t.driver__

#undef LOG_TAG
#define LOG_TAG "FuzzerExtended_bt_interface_t"
#include <hardware/hardware.h>
#include <hardware/bluetooth.h>

#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include <utils/Log.h>

#include <fuzz_tester/FuzzerBase.h>
#include <fuzz_tester/FuzzerCallbackBase.h>

namespace android {
namespace vts {
class FuzzerExtended_bt_interface_t : public FuzzerBase {
 public:
    FuzzerExtended_bt_interface_t() : FuzzerBase(HAL_CONVENTIONAL_SUBMODULE) {}
 protected:
    bool Fuzz(FunctionSpecificationMessage* func_msg, void** result, const string& callback_socket_name);
    bool CallFunction(const FunctionSpecificationMessage& func_msg, const string& callback_socket_name, FunctionSpecificationMessage* result_msg);
    bool VerifyResults(const FunctionSpecificationMessage& expected_result, const FunctionSpecificationMessage& actual_result);
    bool GetAttribute(FunctionSpecificationMessage* func_msg, void** result);
    void SetSubModule(bt_interface_t* submodule) {
        submodule_ = submodule;
    }

 private:
    bt_interface_t* submodule_;
};


extern "C" {
extern android::vts::FuzzerBase* vts_func_2_7_1_();
}
}  // namespace vts
}  // namespace android
#endif
