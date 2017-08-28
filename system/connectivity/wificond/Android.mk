# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := $(call my-dir)
wificond_cpp_flags := -Wall -Werror -Wno-unused-parameter
wificond_parent_dir := $(LOCAL_PATH)/../
wificond_includes := \
    $(wificond_parent_dir)


###
### wificond daemon.
###
include $(CLEAR_VARS)
LOCAL_MODULE := wificond
LOCAL_CPPFLAGS := $(wificond_cpp_flags)
LOCAL_INIT_RC := wificond.rc
LOCAL_C_INCLUDES := $(wificond_includes)
LOCAL_SRC_FILES := \
    main.cpp
LOCAL_SHARED_LIBRARIES := \
    libbinder \
    libbase \
    libcutils \
    libminijail \
    libutils \
    libwifi-system
LOCAL_STATIC_LIBRARIES := \
    libwificond
include $(BUILD_EXECUTABLE)

###
### wificond static library
###
include $(CLEAR_VARS)
LOCAL_MODULE := libwificond
LOCAL_CPPFLAGS := $(wificond_cpp_flags)
LOCAL_C_INCLUDES := $(wificond_includes)
LOCAL_SRC_FILES := \
    ap_interface_binder.cpp \
    ap_interface_impl.cpp \
    client_interface_binder.cpp \
    client_interface_impl.cpp \
    logging_utils.cpp \
    looper_backed_event_loop.cpp \
    rtt/rtt_controller_binder.cpp \
    rtt/rtt_controller_impl.cpp \
    scanning/channel_settings.cpp \
    scanning/hidden_network.cpp \
    scanning/pno_network.cpp \
    scanning/pno_settings.cpp \
    scanning/scan_result.cpp \
    scanning/single_scan_settings.cpp \
    scanning/scan_utils.cpp \
    scanning/scanner_impl.cpp \
    server.cpp
LOCAL_SHARED_LIBRARIES := \
    libbase \
    libutils \
    libwifi-system
LOCAL_WHOLE_STATIC_LIBRARIES := \
    libwificond_ipc \
    libwificond_nl
include $(BUILD_STATIC_LIBRARY)

###
### wificond netlink library
###
include $(CLEAR_VARS)
LOCAL_MODULE := libwificond_nl
LOCAL_CPPFLAGS := $(wificond_cpp_flags)
LOCAL_C_INCLUDES := $(wificond_includes)
LOCAL_SRC_FILES := \
    net/mlme_event.cpp \
    net/netlink_manager.cpp \
    net/netlink_utils.cpp \
    net/nl80211_attribute.cpp \
    net/nl80211_packet.cpp
LOCAL_SHARED_LIBRARIES := \
    libbase
include $(BUILD_STATIC_LIBRARY)

###
### wificond IPC interface library
###
include $(CLEAR_VARS)
LOCAL_MODULE := libwificond_ipc
LOCAL_AIDL_INCLUDES += $(LOCAL_PATH)/aidl
LOCAL_C_INCLUDES := $(wificond_includes)
LOCAL_CPPFLAGS := $(wificond_cpp_flags)
LOCAL_SRC_FILES := \
    ipc_constants.cpp \
    aidl/android/net/wifi/IApInterface.aidl \
    aidl/android/net/wifi/IANQPDoneCallback.aidl \
    aidl/android/net/wifi/IClientInterface.aidl \
    aidl/android/net/wifi/IInterfaceEventCallback.aidl \
    aidl/android/net/wifi/IPnoScanEvent.aidl \
    aidl/android/net/wifi/IRttClient.aidl \
    aidl/android/net/wifi/IRttController.aidl \
    aidl/android/net/wifi/IScanEvent.aidl \
    aidl/android/net/wifi/IWificond.aidl \
    aidl/android/net/wifi/IWifiScannerImpl.aidl \
    scanning/channel_settings.cpp \
    scanning/hidden_network.cpp \
    scanning/pno_network.cpp \
    scanning/pno_settings.cpp \
    scanning/scan_result.cpp \
    scanning/single_scan_settings.cpp
LOCAL_SHARED_LIBRARIES := \
    libbinder
include $(BUILD_STATIC_LIBRARY)

###
### test util library
###
include $(CLEAR_VARS)
LOCAL_MODULE := libwificond_test_utils
LOCAL_CPPFLAGS := $(wificond_cpp_flags)
LOCAL_C_INCLUDES := $(wificond_includes)
LOCAL_SRC_FILES := \
    looper_backed_event_loop.cpp \
    tests/integration/binder_dispatcher.cpp \
    tests/integration/process_utils.cpp \
    tests/shell_utils.cpp
LOCAL_SHARED_LIBRARIES := \
    libbase
LOCAL_WHOLE_STATIC_LIBRARIES := \
    libwificond_ipc
include $(BUILD_STATIC_LIBRARY)

###
### wificond unit tests.
###
include $(CLEAR_VARS)
LOCAL_MODULE := wificond_unit_test
LOCAL_CPPFLAGS := $(wificond_cpp_flags)
LOCAL_C_INCLUDES := $(wificond_includes)
LOCAL_SRC_FILES := \
    tests/ap_interface_impl_unittest.cpp \
    tests/client_interface_impl_unittest.cpp \
    tests/looper_backed_event_loop_unittest.cpp \
    tests/main.cpp \
    tests/mock_netlink_manager.cpp \
    tests/mock_netlink_utils.cpp \
    tests/mock_scan_utils.cpp \
    tests/netlink_manager_unittest.cpp \
    tests/netlink_utils_unittest.cpp \
    tests/nl80211_attribute_unittest.cpp \
    tests/nl80211_packet_unittest.cpp \
    tests/scan_result_unittest.cpp \
    tests/scan_settings_unittest.cpp \
    tests/scan_utils_unittest.cpp \
    tests/server_unittest.cpp
LOCAL_STATIC_LIBRARIES := \
    libgmock \
    libgtest \
    libwifi-system-test \
    libwificond \
    libwificond_nl
LOCAL_SHARED_LIBRARIES := \
    libbase \
    libbinder \
    liblog \
    libutils \
    libwifi-system
include $(BUILD_NATIVE_TEST)

###
### wificond device integration tests.
###
include $(CLEAR_VARS)
LOCAL_MODULE := wificond_integration_test
LOCAL_CPPFLAGS := $(wificond_cpp_flags)
LOCAL_C_INCLUDES := $(wificond_includes)
LOCAL_SRC_FILES := \
    tests/integration/ap_interface_test.cpp \
    tests/integration/client_interface_test.cpp \
    tests/integration/life_cycle_test.cpp \
    tests/integration/scanner_test.cpp \
    tests/integration/service_test.cpp \
    tests/main.cpp \
    tests/shell_unittest.cpp
LOCAL_SHARED_LIBRARIES := \
    libbase \
    libbinder \
    libcutils \
    libutils \
    libwifi-system
LOCAL_STATIC_LIBRARIES := \
    libgmock \
    libwificond_ipc \
    libwificond_test_utils
include $(BUILD_NATIVE_TEST)
