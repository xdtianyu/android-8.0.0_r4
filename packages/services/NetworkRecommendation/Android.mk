LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-annotations \
    guava \
    jsr305 \

# Don't build anything, the needed system APIs have been removed.
#LOCAL_SRC_FILES := $(call all-java-files-under, src)
#LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

LOCAL_PACKAGE_NAME := NetworkRecommendation
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true

LOCAL_SDK_VERSION := system_current

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

# Set to false to allow iteration via adb install rather than having to
# continaully reflash a test device.
LOCAL_DEX_PREOPT := false

include $(BUILD_PACKAGE)

# This finds and builds the test apk as well, so a single make does both.
#include $(call all-makefiles-under,$(LOCAL_PATH))
