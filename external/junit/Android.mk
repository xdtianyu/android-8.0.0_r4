# Copyright (C) 2009 The Android Open Source Project
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
#

LOCAL_PATH := $(call my-dir)

# build a junit jar
# ----------------------
include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(call all-java-files-under, src/main/java)
LOCAL_MODULE := junit
LOCAL_MODULE_TAGS := tests
LOCAL_SDK_VERSION := 25
LOCAL_STATIC_JAVA_LIBRARIES := hamcrest
# The following is needed by external/apache-harmony/jdwp/Android_debug_config.mk
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA)/junit
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk
include $(BUILD_STATIC_JAVA_LIBRARY)

# build a junit-host jar
# ----------------------

include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(call all-java-files-under, src/main/java)
LOCAL_MODULE := junit-host
LOCAL_MODULE_TAGS := tests
LOCAL_STATIC_JAVA_LIBRARIES := hamcrest-host
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk
include $(BUILD_HOST_JAVA_LIBRARY)

# build a junit-hostdex jar
# -------------------------

ifeq ($(HOST_OS),linux)
include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(call all-java-files-under, src/main/java)
LOCAL_MODULE := junit-hostdex
LOCAL_MODULE_TAGS := tests
LOCAL_STATIC_JAVA_LIBRARIES := hamcrest-hostdex
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk
include $(BUILD_HOST_DALVIK_STATIC_JAVA_LIBRARY)
endif # HOST_OS == linux
