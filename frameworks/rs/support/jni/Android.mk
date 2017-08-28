LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SDK_VERSION := 14

LOCAL_SRC_FILES:= \
    android_rscompat_usage_io.cpp \
    android_rscompat_usage_io_driver.cpp

LOCAL_C_INCLUDES += \
	$(JNI_H_INCLUDE) \
	frameworks/rs \
	frameworks/rs/cpp \
	frameworks/rs/driver

LOCAL_CFLAGS += -Werror -Wall -Wextra \
		-Wno-unused-parameter \
		-DRS_COMPATIBILITY_LIB \
		-std=c++11

LOCAL_MODULE:= libRSSupportIO

LOCAL_LDLIBS += -landroid
LOCAL_LDFLAGS += -ldl -Wl,--exclude-libs,libc++_static.a
LOCAL_NDK_STL_VARIANT := c++_static
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_SDK_VERSION := 9

LOCAL_SRC_FILES:= \
    android_renderscript_RenderScript.cpp

LOCAL_SHARED_LIBRARIES := \
        libjnigraphics

LOCAL_STATIC_LIBRARIES := \
        libRSDispatch

LOCAL_C_INCLUDES += \
	$(JNI_H_INCLUDE) \
	frameworks/rs \
	frameworks/rs/cpp

LOCAL_CFLAGS += -Werror -Wall -Wextra -Wno-unused-parameter -std=c++11

LOCAL_MODULE:= librsjni
LOCAL_REQUIRED_MODULES := libRSSupport

LOCAL_LDFLAGS += -ldl -llog -Wl,--exclude-libs,libc++_static.a
LOCAL_NDK_STL_VARIANT := c++_static

include $(BUILD_SHARED_LIBRARY)
