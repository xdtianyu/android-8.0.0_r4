#
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
#

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := fmq_test
LOCAL_MODULE_CLASS := NATIVE_TESTS
LOCAL_SRC_FILES := fmq_test
LOCAL_REQUIRED_MODULES :=                           \
    mq_test_client                                  \
    android.hardware.tests.msgq@1.0-service-test    \
    mq_test_client_32                               \
    android.hardware.tests.msgq@1.0-service-test_32 \
    hidl_test_helper

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := \
    msgq_test_client.cpp

LOCAL_SHARED_LIBRARIES := \
    libhidlbase \
    libhidltransport  \
    libhwbinder \
    libcutils \
    libutils \
    libbase \
    libfmq \
    liblog
LOCAL_CFLAGS := -Wall -Werror
LOCAL_SHARED_LIBRARIES += android.hardware.tests.msgq@1.0 libfmq
LOCAL_MODULE := mq_test_client
LOCAL_REQUIRED_MODULES := \
    android.hardware.tests.msgq@1.0-impl_32 \
    android.hardware.tests.msgq@1.0-impl

include $(BUILD_NATIVE_TEST)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := \
    mq_test.cpp
LOCAL_STATIC_LIBRARIES := libutils libcutils liblog
LOCAL_SHARED_LIBRARIES := \
    libhidlbase \
    libhidltransport \
    libhwbinder \
    libbase \
    libfmq
LOCAL_MODULE := mq_test
LOCAL_CFLAGS := -Wall -Werror
include $(BUILD_NATIVE_TEST)

include $(CLEAR_VARS)

LOCAL_MODULE := VtsFmqUnitTests
VTS_CONFIG_SRC_DIR := system/libfmq/tests
include test/vts/tools/build/Android.host_config.mk
