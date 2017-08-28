#
# Copyright (C) 2016 The Android Open Source Project
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

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

LOCAL_MODULE := car-apps-common
LOCAL_MODULE_TAGS := optional

LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_ENABLED := disabled

# Include android-support-v4, if not already included
ifeq (,$(findstring android-support-v4,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v4
endif

# Include android-support-annotations, if not already included
ifeq (,$(findstring android-support-annotations,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_STATIC_JAVA_LIBRARIES += android-support-annotations
endif

include $(BUILD_STATIC_JAVA_LIBRARY)
