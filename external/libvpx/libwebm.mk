# libwebm
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := libwebm/mkvparser/mkvparser.cc
LOCAL_CPP_EXTENSION := .cc
LOCAL_C_INCLUDES += $(LOCAL_PATH)/libwebm/

LOCAL_MODULE := libwebm

LOCAL_SANITIZE := cfi
LOCAL_SANITIZE_DIAG := cfi

include $(BUILD_STATIC_LIBRARY)
