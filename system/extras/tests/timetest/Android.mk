# Copyright 2006 The Android Open Source Project

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := timetest.c
LOCAL_MODULE := timetest
LOCAL_MODULE_TAGS := tests
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_STATIC_LIBRARIES := libc
include $(BUILD_NATIVE_TEST)

# -----------------------------------------------------------------------------
# Unit tests.
# -----------------------------------------------------------------------------

test_c_flags := \
    -fstack-protector-all \
    -g \
    -Wall -Wextra \
    -Werror \
    -fno-builtin \

test_src_files := \
    rtc_test.cpp

include $(CLEAR_VARS)
LOCAL_MODULE := time-unit-tests
LOCAL_MODULE_TAGS := tests
LOCAL_CFLAGS += $(test_c_flags)
LOCAL_SRC_FILES := $(test_src_files)
include $(BUILD_NATIVE_TEST)

