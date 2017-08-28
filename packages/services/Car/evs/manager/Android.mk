LOCAL_PATH:= $(call my-dir)

##################################
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    service.cpp \
    Enumerator.cpp \
    HalCamera.cpp \
    VirtualCamera.cpp \

LOCAL_C_INCLUDES += \
    frameworks/base/include \
    packages/services/Car/evs/manager \

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    liblog \
    libutils \
    libui \
    libhwbinder \
    libhidlbase \
    libhidltransport \
    libtinyalsa \
    libhardware \
    android.hardware.automotive.evs@1.0 \

LOCAL_STRIP_MODULE := keep_symbols

LOCAL_MODULE := android.automotive.evs.manager@1.0
LOCAL_MODULE_TAGS := optional

LOCAL_CFLAGS += -DGL_GLEXT_PROTOTYPES -DEGL_EGLEXT_PROTOTYPES
LOCAL_CFLAGS += -Wall -Werror -Wunused -Wunreachable-code

include $(BUILD_EXECUTABLE)
