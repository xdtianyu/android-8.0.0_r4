# Copyright (C) 2014 The Android Open Source Project
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

# Unit tests for libkeymaster
include $(CLEAR_VARS)
LOCAL_MODULE := keymaster_tests
LOCAL_SRC_FILES := \
	android_keymaster_messages_test.cpp \
	android_keymaster_test.cpp \
	android_keymaster_test_utils.cpp \
	attestation_record_test.cpp \
	authorization_set_test.cpp \
	hkdf_test.cpp \
	hmac_test.cpp \
	kdf1_test.cpp \
	kdf2_test.cpp \
	kdf_test.cpp \
	key_blob_test.cpp \
	keymaster_enforcement_test.cpp

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/include
LOCAL_CFLAGS = -Wall -Werror -Wunused -DKEYMASTER_NAME_TAGS
LOCAL_CLANG := true
LOCAL_CLANG_CFLAGS += -Wno-error=unused-const-variable -Wno-error=unused-private-field
# TODO(krasin): reenable coverage flags, when the new Clang toolchain is released.
# Currently, if enabled, these flags will cause an internal error in Clang.
LOCAL_CLANG_CFLAGS += -fno-sanitize-coverage=edge,indirect-calls,8bit-counters,trace-cmp
LOCAL_MODULE_TAGS := tests
LOCAL_SHARED_LIBRARIES := \
	libsoftkeymasterdevice \
	libkeymaster_messages \
	libkeymaster1 \
	libcrypto \
	libsoftkeymaster
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk
include $(BUILD_NATIVE_TEST)
