ifneq (false,$(BUILD_EMULATOR_OPENGL_DRIVER))

ifeq ($(TARGET_USES_HWC2), true)

LOCAL_PATH := $(call my-dir)

$(call emugl-begin-static-library,libsurfaceInterface)
$(call emugl-import,libOpenglSystemCommon)

LOCAL_SRC_FILES := surfaceInterface.cpp
LOCAL_SHARED_LIBRARIES := libgui

$(call emugl-end-module)

endif

endif # BUILD_EMULATOR_OPENGL_DRIVER != false
