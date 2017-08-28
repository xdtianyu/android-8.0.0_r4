#include "test/vts/specification/hal/conventional/bluetooth/1.0/BluetoothHalV1bt_interface_t.vts.h"
#include <hardware/hardware.h>
#include <hardware/bluetooth.h>
#include "vts_datatype.h"
#include "vts_measurement.h"
#include <iostream>


namespace android {
namespace vts {
static string callback_socket_name_;

class vts_callback_FuzzerExtended_bt_interface_t_bt_callbacks_t : public FuzzerCallbackBase {
 public:
    vts_callback_FuzzerExtended_bt_interface_t_bt_callbacks_t(const string& callback_socket_name) {
        callback_socket_name_ = callback_socket_name;
    }

    static void
     adapter_state_changed_cb(bt_state_t arg0) {
        AndroidSystemCallbackRequestMessage callback_message;
        callback_message.set_id(GetCallbackID("adapter_state_changed_cb"));
        VariableSpecificationMessage* var_msg0 = callback_message.add_arg();
        var_msg0->set_type(TYPE_PREDEFINED);
        RpcCallToAgent(callback_message, callback_socket_name_);
    }


    static void
     adapter_properties_cb(bt_status_t arg0, int32_t arg1, bt_property_t* arg2) {
        AndroidSystemCallbackRequestMessage callback_message;
        callback_message.set_id(GetCallbackID("adapter_properties_cb"));
        VariableSpecificationMessage* var_msg0 = callback_message.add_arg();
        var_msg0->set_type(TYPE_PREDEFINED);
        VariableSpecificationMessage* var_msg1 = callback_message.add_arg();
        var_msg1->set_type(TYPE_SCALAR);
        var_msg1->set_scalar_type("int32_t");
        var_msg1->mutable_scalar_value()->set_int32_t(0);
        VariableSpecificationMessage* var_msg2 = callback_message.add_arg();
        var_msg2->set_type(TYPE_PREDEFINED);
        RpcCallToAgent(callback_message, callback_socket_name_);
    }


    static void
     remote_device_properties_cb(bt_status_t arg0, bt_bdaddr_t* arg1, int32_t arg2, bt_property_t* arg3) {
        AndroidSystemCallbackRequestMessage callback_message;
        callback_message.set_id(GetCallbackID("remote_device_properties_cb"));
        VariableSpecificationMessage* var_msg0 = callback_message.add_arg();
        var_msg0->set_type(TYPE_PREDEFINED);
        VariableSpecificationMessage* var_msg1 = callback_message.add_arg();
        var_msg1->set_type(TYPE_PREDEFINED);
        VariableSpecificationMessage* var_msg2 = callback_message.add_arg();
        var_msg2->set_type(TYPE_SCALAR);
        var_msg2->set_scalar_type("int32_t");
        var_msg2->mutable_scalar_value()->set_int32_t(0);
        VariableSpecificationMessage* var_msg3 = callback_message.add_arg();
        var_msg3->set_type(TYPE_PREDEFINED);
        RpcCallToAgent(callback_message, callback_socket_name_);
    }


    static void
     device_found_cb(int32_t arg0, bt_property_t* arg1) {
        AndroidSystemCallbackRequestMessage callback_message;
        callback_message.set_id(GetCallbackID("device_found_cb"));
        VariableSpecificationMessage* var_msg0 = callback_message.add_arg();
        var_msg0->set_type(TYPE_SCALAR);
        var_msg0->set_scalar_type("int32_t");
        var_msg0->mutable_scalar_value()->set_int32_t(0);
        VariableSpecificationMessage* var_msg1 = callback_message.add_arg();
        var_msg1->set_type(TYPE_PREDEFINED);
        RpcCallToAgent(callback_message, callback_socket_name_);
    }


    static void
     discovery_state_changed_cb(bt_discovery_state_t arg0) {
        AndroidSystemCallbackRequestMessage callback_message;
        callback_message.set_id(GetCallbackID("discovery_state_changed_cb"));
        VariableSpecificationMessage* var_msg0 = callback_message.add_arg();
        var_msg0->set_type(TYPE_PREDEFINED);
        RpcCallToAgent(callback_message, callback_socket_name_);
    }


    static void
     pin_request_cb(bt_bdaddr_t* arg0, bt_bdname_t* arg1, uint32_t arg2, bool arg3) {
        AndroidSystemCallbackRequestMessage callback_message;
        callback_message.set_id(GetCallbackID("pin_request_cb"));
        VariableSpecificationMessage* var_msg0 = callback_message.add_arg();
        var_msg0->set_type(TYPE_PREDEFINED);
        VariableSpecificationMessage* var_msg1 = callback_message.add_arg();
        var_msg1->set_type(TYPE_PREDEFINED);
        VariableSpecificationMessage* var_msg2 = callback_message.add_arg();
        var_msg2->set_type(TYPE_SCALAR);
        var_msg2->set_scalar_type("uint32_t");
        var_msg2->mutable_scalar_value()->set_uint32_t(0);
        VariableSpecificationMessage* var_msg3 = callback_message.add_arg();
        var_msg3->set_type(TYPE_SCALAR);
        var_msg3->set_scalar_type("bool_t");
        var_msg3->mutable_scalar_value()->set_bool_t(0);
        RpcCallToAgent(callback_message, callback_socket_name_);
    }


    static void
     ssp_request_cb(bt_bdaddr_t* arg0, bt_bdname_t* arg1, uint32_t arg2, bt_ssp_variant_t arg3, uint32_t arg4) {
        AndroidSystemCallbackRequestMessage callback_message;
        callback_message.set_id(GetCallbackID("ssp_request_cb"));
        VariableSpecificationMessage* var_msg0 = callback_message.add_arg();
        var_msg0->set_type(TYPE_PREDEFINED);
        VariableSpecificationMessage* var_msg1 = callback_message.add_arg();
        var_msg1->set_type(TYPE_PREDEFINED);
        VariableSpecificationMessage* var_msg2 = callback_message.add_arg();
        var_msg2->set_type(TYPE_SCALAR);
        var_msg2->set_scalar_type("uint32_t");
        var_msg2->mutable_scalar_value()->set_uint32_t(0);
        VariableSpecificationMessage* var_msg3 = callback_message.add_arg();
        var_msg3->set_type(TYPE_PREDEFINED);
        VariableSpecificationMessage* var_msg4 = callback_message.add_arg();
        var_msg4->set_type(TYPE_SCALAR);
        var_msg4->set_scalar_type("uint32_t");
        var_msg4->mutable_scalar_value()->set_uint32_t(0);
        RpcCallToAgent(callback_message, callback_socket_name_);
    }


    static void
     bond_state_changed_cb(bt_status_t arg0, bt_bdaddr_t* arg1, bt_bond_state_t arg2) {
        AndroidSystemCallbackRequestMessage callback_message;
        callback_message.set_id(GetCallbackID("bond_state_changed_cb"));
        VariableSpecificationMessage* var_msg0 = callback_message.add_arg();
        var_msg0->set_type(TYPE_PREDEFINED);
        VariableSpecificationMessage* var_msg1 = callback_message.add_arg();
        var_msg1->set_type(TYPE_PREDEFINED);
        VariableSpecificationMessage* var_msg2 = callback_message.add_arg();
        var_msg2->set_type(TYPE_PREDEFINED);
        RpcCallToAgent(callback_message, callback_socket_name_);
    }


    static void
     acl_state_changed_cb(bt_status_t arg0, bt_bdaddr_t* arg1, bt_acl_state_t arg2) {
        AndroidSystemCallbackRequestMessage callback_message;
        callback_message.set_id(GetCallbackID("acl_state_changed_cb"));
        VariableSpecificationMessage* var_msg0 = callback_message.add_arg();
        var_msg0->set_type(TYPE_PREDEFINED);
        VariableSpecificationMessage* var_msg1 = callback_message.add_arg();
        var_msg1->set_type(TYPE_PREDEFINED);
        VariableSpecificationMessage* var_msg2 = callback_message.add_arg();
        var_msg2->set_type(TYPE_PREDEFINED);
        RpcCallToAgent(callback_message, callback_socket_name_);
    }


    static void
     thread_evt_cb(bt_cb_thread_evt arg0) {
        AndroidSystemCallbackRequestMessage callback_message;
        callback_message.set_id(GetCallbackID("thread_evt_cb"));
        VariableSpecificationMessage* var_msg0 = callback_message.add_arg();
        var_msg0->set_type(TYPE_PREDEFINED);
        RpcCallToAgent(callback_message, callback_socket_name_);
    }


    static void
     dut_mode_recv_cb(uint16_t arg0, unsigned char* arg1, uint8_t arg2) {
        AndroidSystemCallbackRequestMessage callback_message;
        callback_message.set_id(GetCallbackID("dut_mode_recv_cb"));
        VariableSpecificationMessage* var_msg0 = callback_message.add_arg();
        var_msg0->set_type(TYPE_SCALAR);
        var_msg0->set_scalar_type("uint16_t");
        var_msg0->mutable_scalar_value()->set_uint16_t(0);
        VariableSpecificationMessage* var_msg1 = callback_message.add_arg();
        var_msg1->set_type(TYPE_SCALAR);
        var_msg1->set_scalar_type("uchar_pointer");
        var_msg1->mutable_scalar_value()->set_uchar_pointer(0);
        VariableSpecificationMessage* var_msg2 = callback_message.add_arg();
        var_msg2->set_type(TYPE_SCALAR);
        var_msg2->set_scalar_type("uint8_t");
        var_msg2->mutable_scalar_value()->set_uint8_t(0);
        RpcCallToAgent(callback_message, callback_socket_name_);
    }


    static void
     le_test_mode_cb(bt_status_t arg0, uint16_t arg1) {
        AndroidSystemCallbackRequestMessage callback_message;
        callback_message.set_id(GetCallbackID("le_test_mode_cb"));
        VariableSpecificationMessage* var_msg0 = callback_message.add_arg();
        var_msg0->set_type(TYPE_PREDEFINED);
        VariableSpecificationMessage* var_msg1 = callback_message.add_arg();
        var_msg1->set_type(TYPE_SCALAR);
        var_msg1->set_scalar_type("uint16_t");
        var_msg1->mutable_scalar_value()->set_uint16_t(0);
        RpcCallToAgent(callback_message, callback_socket_name_);
    }


    static void
     energy_info_cb(bt_activity_energy_info* arg0, bt_uid_traffic_t* arg1) {
        AndroidSystemCallbackRequestMessage callback_message;
        callback_message.set_id(GetCallbackID("energy_info_cb"));
        VariableSpecificationMessage* var_msg0 = callback_message.add_arg();
        var_msg0->set_type(TYPE_PREDEFINED);
        VariableSpecificationMessage* var_msg1 = callback_message.add_arg();
        var_msg1->set_type(TYPE_PREDEFINED);
        RpcCallToAgent(callback_message, callback_socket_name_);
    }


 private:
};

bool FuzzerExtended_bt_interface_t::Fuzz(
    FunctionSpecificationMessage* func_msg,
    void** result, const string& callback_socket_name) {
    const char* func_name = func_msg->name().c_str();
    cout << "Function: " << __func__ << " '" << func_name << "'" << endl;
    bt_interface_t* local_device = reinterpret_cast<bt_interface_t*>(device_);
    if (local_device == NULL) {
        cout << "use hmi " << (uint64_t)hmi_ << endl;
        local_device = reinterpret_cast<bt_interface_t*>(hmi_);
    }
    if (local_device == NULL) {
        cerr << "both device_ and hmi_ are NULL." << endl;
        return false;
    }
    if (!strcmp(func_name, "init")) {
        cout << "match" << endl;
        vts_callback_FuzzerExtended_bt_interface_t_bt_callbacks_t* arg0callback = new vts_callback_FuzzerExtended_bt_interface_t_bt_callbacks_t(callback_socket_name);
        arg0callback->Register(func_msg->arg(0));
        bt_callbacks_t* arg0 = (bt_callbacks_t*) malloc(sizeof(bt_callbacks_t*));
        arg0->adapter_state_changed_cb = arg0callback->adapter_state_changed_cb;
        arg0->adapter_properties_cb = arg0callback->adapter_properties_cb;
        arg0->remote_device_properties_cb = arg0callback->remote_device_properties_cb;
        arg0->device_found_cb = arg0callback->device_found_cb;
        arg0->discovery_state_changed_cb = arg0callback->discovery_state_changed_cb;
        arg0->pin_request_cb = arg0callback->pin_request_cb;
        arg0->ssp_request_cb = arg0callback->ssp_request_cb;
        arg0->bond_state_changed_cb = arg0callback->bond_state_changed_cb;
        arg0->acl_state_changed_cb = arg0callback->acl_state_changed_cb;
        arg0->thread_evt_cb = arg0callback->thread_evt_cb;
        arg0->dut_mode_recv_cb = arg0callback->dut_mode_recv_cb;
        arg0->le_test_mode_cb = arg0callback->le_test_mode_cb;
        arg0->energy_info_cb = arg0callback->energy_info_cb;
        cout << "arg0 = " << arg0 << endl;
        VtsMeasurement vts_measurement;
        vts_measurement.Start();
        cout << "hit2." << device_ << endl;
        if (reinterpret_cast<bt_interface_t*>(local_device)->init == NULL) {
            cerr << "api not set." << endl;
            return false;
        }
        cout << "Call an API." << endl;
        cout << "local_device = " << local_device;
        *result = const_cast<void*>(reinterpret_cast<const void*>(local_device->init(
        arg0)));
        vector<float>* measured = vts_measurement.Stop();
        cout << "time " << (*measured)[0] << endl;
        cout << "called" << endl;
        return true;
    }
    cerr << "func not found" << endl;
    return false;
}
bool FuzzerExtended_bt_interface_t::GetAttribute(
    FunctionSpecificationMessage* func_msg,
    void** result) {
    const char* func_name = func_msg->name().c_str();
    cout << "Function: " << __func__ << " '" << func_name << "'" << endl;
    bt_interface_t* local_device = reinterpret_cast<bt_interface_t*>(device_);
    if (local_device == NULL) {
        cout << "use hmi " << (uint64_t)hmi_ << endl;
        local_device = reinterpret_cast<bt_interface_t*>(hmi_);
    }
    if (local_device == NULL) {
        cerr << "both device_ and hmi_ are NULL." << endl;
        return false;
    }
    cerr << "attribute not found" << endl;
    return false;
}
bool FuzzerExtended_bt_interface_t::CallFunction(const FunctionSpecificationMessage&, const string&, FunctionSpecificationMessage* ) {
    /* No implementation yet. */
    return true;
}
bool FuzzerExtended_bt_interface_t::VerifyResults(const FunctionSpecificationMessage&, const FunctionSpecificationMessage&) {
    /* No implementation yet. */
    return true;
}
extern "C" {
android::vts::FuzzerBase* vts_func_2_7_1_() {
    return (android::vts::FuzzerBase*) new android::vts::FuzzerExtended_bt_interface_t();
}

}
}  // namespace vts
}  // namespace android
