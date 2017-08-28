#Do NOT Convert to Android.bp since this needs to compile with
#device specific kernel headers, that Soong does not support.
#https://android-review.googlesource.com/#/q/topic:revert-ltc-soong
#b/38117654

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_CFLAGS := -Wno-macro-redefined
LOCAL_C_INCLUDES:= $(LOCAL_PATH)/include
LOCAL_SRC_FILES:= compress.c utils.c
LOCAL_MODULE := libtinycompress
LOCAL_SHARED_LIBRARIES:= libcutils libutils
LOCAL_MODULE_TAGS := optional
LOCAL_VENDOR_MODULE := true
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_CFLAGS := -Wno-macro-redefined
LOCAL_C_INCLUDES:= $(LOCAL_PATH)/include
LOCAL_SRC_FILES:= cplay.c
LOCAL_MODULE := cplay
LOCAL_SHARED_LIBRARIES:= libcutils libutils libtinycompress
LOCAL_MODULE_TAGS := optional

include $(BUILD_EXECUTABLE)

