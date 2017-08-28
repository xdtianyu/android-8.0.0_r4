#include "test/vts/specification/hal/conventional/camera/2.1/CameraHalV2.vts.h"
#include <hardware/hardware.h>
#include <hardware/camera_common.h>
#include <hardware/camera.h>
#include "vts_datatype.h"
#include "vts_measurement.h"
#include <iostream>


namespace android {
namespace vts {
static string callback_socket_name_;

class vts_callback_FuzzerExtended_camera_module_t_camera_module_callbacks_t : public FuzzerCallbackBase {
 public:
    vts_callback_FuzzerExtended_camera_module_t_camera_module_callbacks_t(const string& callback_socket_name) {
        callback_socket_name_ = callback_socket_name;
    }

    static void
     camera_device_status_change(const const struct camera_module_callbacks* arg0, int32_t arg1, int32_t arg2) {
        AndroidSystemCallbackRequestMessage callback_message;
        callback_message.set_id(GetCallbackID("camera_device_status_change"));
        VariableSpecificationMessage* var_msg0 = callback_message.add_arg();
        var_msg0->set_type(TYPE_PREDEFINED);
        VariableSpecificationMessage* var_msg1 = callback_message.add_arg();
        var_msg1->set_type(TYPE_SCALAR);
        var_msg1->set_scalar_type("int32_t");
        var_msg1->mutable_scalar_value()->set_int32_t(0);
        VariableSpecificationMessage* var_msg2 = callback_message.add_arg();
        var_msg2->set_type(TYPE_SCALAR);
        var_msg2->set_scalar_type("int32_t");
        var_msg2->mutable_scalar_value()->set_int32_t(0);
        RpcCallToAgent(callback_message, callback_socket_name_);
    }


    static void
     torch_mode_status_change(const struct camera_module_callbacks* arg0, const char* arg1, int32_t arg2) {
        AndroidSystemCallbackRequestMessage callback_message;
        callback_message.set_id(GetCallbackID("torch_mode_status_change"));
        VariableSpecificationMessage* var_msg0 = callback_message.add_arg();
        var_msg0->set_type(TYPE_PREDEFINED);
        VariableSpecificationMessage* var_msg1 = callback_message.add_arg();
        var_msg1->set_type(TYPE_SCALAR);
        var_msg1->set_scalar_type("char_pointer");
        var_msg1->mutable_scalar_value()->set_char_pointer(0);
        VariableSpecificationMessage* var_msg2 = callback_message.add_arg();
        var_msg2->set_type(TYPE_SCALAR);
        var_msg2->set_scalar_type("int32_t");
        var_msg2->mutable_scalar_value()->set_int32_t(0);
        RpcCallToAgent(callback_message, callback_socket_name_);
    }


 private:
};

bool FuzzerExtended_camera_module_t::Fuzz__common_methods(
FunctionSpecificationMessage* func_msg,
void** result, const string& callback_socket_name) {
    const char* func_name = func_msg->name().c_str();
    cout << "Function: " << __func__ << " " << func_name << endl;
    if (!strcmp(func_name, "open")) {
        camera_module_t* local_device = reinterpret_cast<camera_module_t*>(device_);
        if (local_device == NULL) {
            cout << "use hmi" << endl;
            local_device = reinterpret_cast<camera_module_t*>(hmi_);
        }
        if (local_device == NULL) {
            cerr << "both device_ and hmi_ are NULL." << endl;
            return false;
        }
        hw_module_t* arg0 = hmi_;
        ;
        cout << "arg0 = " << arg0 << endl;

        char* arg1 = (func_msg->arg(1).type() == TYPE_SCALAR && func_msg->arg(1).scalar_value().has_pointer())? reinterpret_cast<char*>(func_msg->arg(1).scalar_value().pointer() ) : ((hmi_) ? const_cast<char*>(hmi_->name) : NULL)
        ;
        cout << "arg1 = " << arg1 << endl;

        hw_device_t** arg2 = (struct hw_device_t**) &device_
        ;
        cout << "arg2 = " << arg2 << endl;

        VtsMeasurement vts_measurement;
        vts_measurement.Start();
        cout << "hit2." << device_ << endl;
        if (reinterpret_cast<camera_module_t*>(local_device)->common.methods->open == NULL) {
            cerr << "api not set." << endl;
            return false;
        }
        cout << "Call an API." << endl;
        *result = const_cast<void*>(reinterpret_cast<const void*>(local_device->common.methods->open(
        arg0,
        arg1,
        arg2)));
        vector<float>* measured = vts_measurement.Stop();
        cout << "time " << (*measured)[0] << endl;
        cout << "called" << endl;
        (arg2, func_msg->mutable_arg(2));
        return true;
    }
    return false;
}
bool FuzzerExtended_camera_module_t::Fuzz__common(
FunctionSpecificationMessage* func_msg,
void** result, const string& callback_socket_name) {
    const char* func_name = func_msg->name().c_str();
    cout << "Function: " << __func__ << " " << func_name << endl;
    return false;
}
bool FuzzerExtended_camera_module_t::Fuzz(
    FunctionSpecificationMessage* func_msg,
    void** result, const string& callback_socket_name) {
    const char* func_name = func_msg->name().c_str();
    cout << "Function: " << __func__ << " '" << func_name << "'" << endl;
    if (func_msg->parent_path().length() > 0) {
        if (func_msg->parent_path() == "common") {
            return Fuzz__common(func_msg, result, callback_socket_name);
        }
        if (func_msg->parent_path() == "common.methods") {
            return Fuzz__common_methods(func_msg, result, callback_socket_name);
        }
    }
    camera_module_t* local_device = reinterpret_cast<camera_module_t*>(device_);
    if (local_device == NULL) {
        cout << "use hmi " << (uint64_t)hmi_ << endl;
        local_device = reinterpret_cast<camera_module_t*>(hmi_);
    }
    if (local_device == NULL) {
        cerr << "both device_ and hmi_ are NULL." << endl;
        return false;
    }
    if (!strcmp(func_name, "get_number_of_cameras")) {
        cout << "match" << endl;
        VtsMeasurement vts_measurement;
        vts_measurement.Start();
        cout << "hit2." << device_ << endl;
        if (reinterpret_cast<camera_module_t*>(local_device)->get_number_of_cameras == NULL) {
            cerr << "api not set." << endl;
            return false;
        }
        cout << "Call an API." << endl;
        cout << "local_device = " << local_device;
        *result = const_cast<void*>(reinterpret_cast<const void*>(local_device->get_number_of_cameras()));
        vector<float>* measured = vts_measurement.Stop();
        cout << "time " << (*measured)[0] << endl;
        cout << "called" << endl;
        return true;
    }
    if (!strcmp(func_name, "get_camera_info")) {
        cout << "match" << endl;
        int32_t arg0 = (func_msg->arg(0).type() == TYPE_SCALAR)? (func_msg->arg(0).scalar_value().int32_t() ) : ( (func_msg->arg(0).type() == TYPE_PREDEFINED || func_msg->arg(0).type() == TYPE_STRUCT || func_msg->arg(0).type() == TYPE_SCALAR)? RandomInt32() : RandomInt32() );
        cout << "arg0 = " << arg0 << endl;
        camera_info_t* arg1 = ( (func_msg->arg(1).type() == TYPE_PREDEFINED || func_msg->arg(1).type() == TYPE_STRUCT || func_msg->arg(1).type() == TYPE_SCALAR)? GenerateCameraInfoUsingMessage(func_msg->arg(1)) : GenerateCameraInfo() );
        cout << "arg1 = " << arg1 << endl;
        VtsMeasurement vts_measurement;
        vts_measurement.Start();
        cout << "hit2." << device_ << endl;
        if (reinterpret_cast<camera_module_t*>(local_device)->get_camera_info == NULL) {
            cerr << "api not set." << endl;
            return false;
        }
        cout << "Call an API." << endl;
        cout << "local_device = " << local_device;
        *result = const_cast<void*>(reinterpret_cast<const void*>(local_device->get_camera_info(
        arg0,
        arg1)));
        vector<float>* measured = vts_measurement.Stop();
        cout << "time " << (*measured)[0] << endl;
        cout << "called" << endl;
        ConvertCameraInfoToProtobuf(arg1, func_msg->mutable_arg(1));
        return true;
    }
    if (!strcmp(func_name, "set_callbacks")) {
        cout << "match" << endl;
        vts_callback_FuzzerExtended_camera_module_t_camera_module_callbacks_t* arg0callback = new vts_callback_FuzzerExtended_camera_module_t_camera_module_callbacks_t(callback_socket_name);
        arg0callback->Register(func_msg->arg(0));
        camera_module_callbacks_t* arg0 = (camera_module_callbacks_t*) malloc(sizeof(camera_module_callbacks_t*));
        arg0->camera_device_status_change = arg0callback->camera_device_status_change;
        arg0->torch_mode_status_change = arg0callback->torch_mode_status_change;
        cout << "arg0 = " << arg0 << endl;
        VtsMeasurement vts_measurement;
        vts_measurement.Start();
        cout << "hit2." << device_ << endl;
        if (reinterpret_cast<camera_module_t*>(local_device)->set_callbacks == NULL) {
            cerr << "api not set." << endl;
            return false;
        }
        cout << "Call an API." << endl;
        cout << "local_device = " << local_device;
        *result = const_cast<void*>(reinterpret_cast<const void*>(local_device->set_callbacks(
        arg0)));
        vector<float>* measured = vts_measurement.Stop();
        cout << "time " << (*measured)[0] << endl;
        cout << "called" << endl;
        return true;
    }
    if (!strcmp(func_name, "init")) {
        cout << "match" << endl;
        VtsMeasurement vts_measurement;
        vts_measurement.Start();
        cout << "hit2." << device_ << endl;
        if (reinterpret_cast<camera_module_t*>(local_device)->init == NULL) {
            cerr << "api not set." << endl;
            return false;
        }
        cout << "Call an API." << endl;
        cout << "local_device = " << local_device;
        *result = const_cast<void*>(reinterpret_cast<const void*>(local_device->init()));
        vector<float>* measured = vts_measurement.Stop();
        cout << "time " << (*measured)[0] << endl;
        cout << "called" << endl;
        return true;
    }
    cerr << "func not found" << endl;
    return false;
}
bool FuzzerExtended_camera_module_t::GetAttribute__common_methods(
    FunctionSpecificationMessage* func_msg,
    void** result) {
    const char* func_name = func_msg->name().c_str();
    cout << "Function: " << __func__ << " " << func_name << endl;
    camera_module_t* local_device = reinterpret_cast<camera_module_t*>(device_);
    if (local_device == NULL) {
          cout << "use hmi " << (uint64_t)hmi_ << endl;
          local_device = reinterpret_cast<camera_module_t*>(hmi_);
    }
    if (local_device == NULL) {
        cerr << "both device_ and hmi_ are NULL." << endl;
        return false;
    }
    cerr << "attribute not found" << endl;
    return false;
}
bool FuzzerExtended_camera_module_t::GetAttribute__common(
    FunctionSpecificationMessage* func_msg,
    void** result) {
    const char* func_name = func_msg->name().c_str();
    cout << "Function: " << __func__ << " " << func_name << endl;
    camera_module_t* local_device = reinterpret_cast<camera_module_t*>(device_);
    if (local_device == NULL) {
          cout << "use hmi " << (uint64_t)hmi_ << endl;
          local_device = reinterpret_cast<camera_module_t*>(hmi_);
    }
    if (local_device == NULL) {
        cerr << "both device_ and hmi_ are NULL." << endl;
        return false;
    }
    if (!strcmp(func_name, "module_api_version")) {
        cout << "match" << endl;
        cout << "hit2." << device_ << endl;
        cout << "ok. let's read attribute." << endl;
        *result = const_cast<void*>(reinterpret_cast<const void*>(local_device->common.module_api_version));
        cout << "got" << endl;
        return true;
    }
    cerr << "attribute not found" << endl;
    return false;
}
bool FuzzerExtended_camera_module_t::GetAttribute(
    FunctionSpecificationMessage* func_msg,
    void** result) {
    const char* func_name = func_msg->name().c_str();
    cout << "Function: " << __func__ << " '" << func_name << "'" << endl;
      if (func_msg->parent_path().length() > 0) {
        if (func_msg->parent_path() == "common") {
                  return GetAttribute__common(func_msg, result);
        }
        if (func_msg->parent_path() == "common.methods") {
                  return GetAttribute__common_methods(func_msg, result);
        }
    }
    camera_module_t* local_device = reinterpret_cast<camera_module_t*>(device_);
    if (local_device == NULL) {
        cout << "use hmi " << (uint64_t)hmi_ << endl;
        local_device = reinterpret_cast<camera_module_t*>(hmi_);
    }
    if (local_device == NULL) {
        cerr << "both device_ and hmi_ are NULL." << endl;
        return false;
    }
    cerr << "attribute not found" << endl;
    return false;
}
bool FuzzerExtended_camera_module_t::CallFunction(const FunctionSpecificationMessage&, const string&, FunctionSpecificationMessage* ) {
    /* No implementation yet. */
    return true;
}
bool FuzzerExtended_camera_module_t::VerifyResults(const FunctionSpecificationMessage&, const FunctionSpecificationMessage&) {
    /* No implementation yet. */
    return true;
}
extern "C" {
android::vts::FuzzerBase* vts_func_1_2_2_() {
    return (android::vts::FuzzerBase*) new android::vts::FuzzerExtended_camera_module_t();
}

}
}  // namespace vts
}  // namespace android
