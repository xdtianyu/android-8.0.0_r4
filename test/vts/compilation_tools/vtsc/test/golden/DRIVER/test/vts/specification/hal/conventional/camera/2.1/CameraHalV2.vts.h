#ifndef __VTS_DRIVER__CameraHalV2.driver__
#define __VTS_DRIVER__CameraHalV2.driver__

#undef LOG_TAG
#define LOG_TAG "FuzzerExtended_camera_module_t"
#include <hardware/hardware.h>
#include <hardware/camera_common.h>
#include <hardware/camera.h>

#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include <utils/Log.h>

#include <fuzz_tester/FuzzerBase.h>
#include <fuzz_tester/FuzzerCallbackBase.h>

namespace android {
namespace vts {
class FuzzerExtended_camera_module_t : public FuzzerBase {
 public:
    FuzzerExtended_camera_module_t() : FuzzerBase(HAL_CONVENTIONAL) {}
 protected:
    bool Fuzz(FunctionSpecificationMessage* func_msg, void** result, const string& callback_socket_name);
    bool CallFunction(const FunctionSpecificationMessage& func_msg, const string& callback_socket_name, FunctionSpecificationMessage* result_msg);
    bool VerifyResults(const FunctionSpecificationMessage& expected_result, const FunctionSpecificationMessage& actual_result);
    bool GetAttribute(FunctionSpecificationMessage* func_msg, void** result);
        bool Fuzz__common(FunctionSpecificationMessage* func_msg,
                    void** result, const string& callback_socket_name);
        bool GetAttribute__common(FunctionSpecificationMessage* func_msg,
                    void** result);
            bool Fuzz__common_methods(FunctionSpecificationMessage* func_msg,
                        void** result, const string& callback_socket_name);
            bool GetAttribute__common_methods(FunctionSpecificationMessage* func_msg,
                        void** result);
 private:
};


extern "C" {
extern android::vts::FuzzerBase* vts_func_1_2_2_();
}
}  // namespace vts
}  // namespace android
#endif
