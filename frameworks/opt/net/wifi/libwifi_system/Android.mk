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

wifi_system_cflags := \
    -Wall \
    -Werror \
    -Wextra \
    -Winit-self \
    -Wno-unused-function \
    -Wno-unused-parameter \
    -Wshadow \
    -Wunused-variable \
    -Wwrite-strings

# Device independent wifi system logic.
# ============================================================
include $(CLEAR_VARS)
LOCAL_MODULE := libwifi-system
LOCAL_CFLAGS := $(wifi_system_cflags)
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/include
LOCAL_EXPORT_SHARED_LIBRARY_HEADERS := libbase
LOCAL_SHARED_LIBRARIES := \
    libbase \
    libcrypto \
    libcutils

LOCAL_SRC_FILES := \
    hostapd_manager.cpp \
    interface_tool.cpp \
    supplicant_manager.cpp
include $(BUILD_SHARED_LIBRARY)

# Test utilities (e.g. mock classes) for libwifi-system
# ============================================================
include $(CLEAR_VARS)
LOCAL_MODULE := libwifi-system-test
LOCAL_CFLAGS := $(wifi_system_cflags)
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/include \
    $(LOCAL_PATH)/testlib/include
LOCAL_STATIC_LIBRARIES := libgmock
LOCAL_EXPORT_C_INCLUDE_DIRS := \
    $(LOCAL_PATH)/include \
    $(LOCAL_PATH)/testlib/include
include $(BUILD_STATIC_LIBRARY)


# Unit tests for libwifi-system
# ============================================================
include $(CLEAR_VARS)
LOCAL_MODULE := libwifi-system_tests
LOCAL_CPPFLAGS := $(wificond_cpp_flags)
LOCAL_SRC_FILES := \
    tests/main.cpp \
    tests/hostapd_manager_unittest.cpp
LOCAL_STATIC_LIBRARIES := \
    libgmock \
    libgtest
LOCAL_SHARED_LIBRARIES := \
    libbase \
    libwifi-system
include $(BUILD_NATIVE_TEST)
