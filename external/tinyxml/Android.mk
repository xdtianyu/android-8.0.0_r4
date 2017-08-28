# Copyright 2005 The Android Open Source Project
#
# Android.mk for TinyXml.
#
# Add -DTIXML_USE_STL to CFLAGS to use STL.
#

commonSources:= \
	tinyxml.cpp \
	tinyxmlparser.cpp \
	tinyxmlerror.cpp \
	tinystr.cpp

commonFlags := \
	-Wno-undefined-bool-conversion \
	-Wno-missing-braces \
        -Wno-logical-op-parentheses \
        -Werror

# For the host
# =====================================================
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	$(commonSources)

LOCAL_MODULE:= libtinyxml

LOCAL_CFLAGS+= $(TOOL_CFLAGS) $(commonFlags)
LOCAL_LDFLAGS:= $(TOOL_LDFLAGS) -lstdc++ -lc

LOCAL_MULTILIB := both

include $(BUILD_HOST_STATIC_LIBRARY)


# For the device (static)
# =====================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	$(commonSources)

LOCAL_MODULE:= libtinyxml

LOCAL_CFLAGS+= $(commonFlags)

include $(BUILD_STATIC_LIBRARY)


# For the device
# =====================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	$(commonSources)

LOCAL_MODULE:= libtinyxml

LOCAL_VENDOR_MODULE := true

LOCAL_CFLAGS+= $(commonFlags)

LOCAL_SHARED_LIBRARIES := \
    libc \
    libstdc++

include $(BUILD_SHARED_LIBRARY)


