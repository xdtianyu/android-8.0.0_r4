#
# Copyright (C) 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

include $(LOCAL_PATH)/version.mk

LOCAL_SRC_FILES := \
    $(call all-java-files-under, src) \
    $(call all-proto-files-under, proto)

LOCAL_PACKAGE_NAME := LiveTv

# It is required for com.android.providers.tv.permission.ALL_EPG_DATA
LOCAL_PRIVILEGED_MODULE := true

LOCAL_SDK_VERSION := system_current
LOCAL_MIN_SDK_VERSION := 23  # M
LOCAL_RESOURCE_DIR := \
    $(LOCAL_PATH)/res \
    $(LOCAL_PATH)/usbtuner-res \
    $(LOCAL_PATH)/common/res

ifdef TARGET_BUILD_APPS
LOCAL_RESOURCE_DIR += \
    $(TOP)/prebuilts/sdk/current/support/v17/leanback/res \
    $(TOP)/prebuilts/sdk/current/support/v7/recyclerview/res
else # !TARGET_BUILD_APPS
LOCAL_RESOURCE_DIR += \
    $(TOP)/frameworks/support/v17/leanback/res \
    $(TOP)/frameworks/support/v7/recyclerview/res
endif

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-annotations \
    android-support-v4 \
    android-support-v7-palette \
    android-support-v7-recyclerview \
    android-support-v17-leanback \
    icu4j-usbtuner \
    lib-exoplayer \
    tv-common \
    legacy-android-test \
    junit


LOCAL_JAVACFLAGS := -Xlint:deprecation -Xlint:unchecked

LOCAL_AAPT_FLAGS := --auto-add-overlay \
    --extra-packages android.support.v7.recyclerview \
    --extra-packages android.support.v17.leanback \
    --extra-packages com.android.tv.common \
    --version-name "$(version_name_package)" \
    --version-code $(version_code_package) \

LOCAL_PROGUARD_FLAG_FILES := proguard.flags


LOCAL_JNI_SHARED_LIBRARIES := libtunertvinput_jni
LOCAL_AAPT_FLAGS += --extra-packages com.android.tv.tuner

LOCAL_PROTOC_OPTIMIZE_TYPE := nano
LOCAL_PROTOC_FLAGS := --proto_path=$(LOCAL_PATH)/proto/

include $(BUILD_PACKAGE)

# --------------------------------------------------------------
# Build a tiny icu4j library out of the classes necessary for the project.

include $(CLEAR_VARS)

LOCAL_MODULE := icu4j-usbtuner
LOCAL_MODULE_TAGS := optional
icu4j_path := icu/icu4j
LOCAL_SRC_FILES := \
    $(icu4j_path)/main/classes/core/src/com/ibm/icu/text/SCSU.java \
    $(icu4j_path)/main/classes/core/src/com/ibm/icu/text/UnicodeDecompressor.java
LOCAL_SDK_VERSION := system_current

include $(BUILD_STATIC_JAVA_LIBRARY)

#############################################################
# Pre-built dependency jars
#############################################################

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
    lib-exoplayer:libs/exoplayer.jar \


include $(BUILD_MULTI_PREBUILT)


include $(call all-makefiles-under,$(LOCAL_PATH))
