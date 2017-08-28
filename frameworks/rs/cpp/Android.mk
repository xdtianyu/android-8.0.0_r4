LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

rs_cpp_SRC_FILES := \
	RenderScript.cpp \
	BaseObj.cpp \
	Element.cpp \
	Type.cpp \
	Allocation.cpp \
	Script.cpp \
	ScriptC.cpp \
	ScriptIntrinsics.cpp \
	ScriptIntrinsicBLAS.cpp \
	Sampler.cpp

rs_cpp_SRC_FILES += ../rsCppUtils.cpp

LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk
include frameworks/compile/slang/rs_version.mk
local_cflags_for_rs_cpp += $(RS_VERSION_DEFINE) \
	-Werror -Wall -Wextra \
	-Wno-unused-parameter -Wno-unused-variable
	-std=c++11

LOCAL_SRC_FILES := $(rs_cpp_SRC_FILES)

LOCAL_CFLAGS += $(local_cflags_for_rs_cpp)

LOCAL_SHARED_LIBRARIES := \
	libz \
	libutils \
	liblog \
	libdl \
	libgui

LOCAL_STATIC_LIBRARIES := \
        libRSDispatch

LOCAL_MODULE:= libRScpp

LOCAL_C_INCLUDES += frameworks/rs
LOCAL_C_INCLUDES += $(intermediates)

# We need to export not just rs/cpp but also rs.  This is because
# RenderScript.h includes rsCppStructs.h, which includes rs/rsDefines.h.
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH) $(LOCAL_PATH)/..

include $(BUILD_SHARED_LIBRARY)

####################################################################

include $(CLEAR_VARS)
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk

LOCAL_CFLAGS += $(local_cflags_for_rs_cpp)

ifeq ($(my_32_64_bit_suffix),32)
LOCAL_SDK_VERSION := 9
else
LOCAL_SDK_VERSION := 21
endif
LOCAL_CFLAGS += -DRS_COMPATIBILITY_LIB

LOCAL_SRC_FILES := $(rs_cpp_SRC_FILES)

LOCAL_WHOLE_STATIC_LIBRARIES := \
	libRSDispatch

LOCAL_MODULE:= libRScpp_static

LOCAL_C_INCLUDES += frameworks/rs
LOCAL_C_INCLUDES += $(intermediates)

LOCAL_LDFLAGS := -llog -lz -ldl -Wl,--exclude-libs,libc++_static.a
LOCAL_NDK_STL_VARIANT := c++_static

LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH) $(LOCAL_PATH)/..

include $(BUILD_STATIC_LIBRARY)
