LOCAL_PATH:=$(call my-dir)

ifneq (true,$(TARGET_BUILD_PDK))
include $(call all-makefiles-under,$(LOCAL_PATH))
endif
