# Copyright (C) 2013 The Android Open Source Project
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

###################################################################
# Host build
###################################################################

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    $(call all-java-files-under, src/main/java)

LOCAL_JAVA_LIBRARIES := junit-host objenesis-host
LOCAL_STATIC_JAVA_LIBRARIES := \
    mockito-byte-buddy-host \
    mockito-byte-buddy-agent-host
LOCAL_MODULE := mockito-host
LOCAL_MODULE_TAGS := optional
LOCAL_JAVA_LANGUAGE_VERSION := 1.7
include $(BUILD_HOST_JAVA_LIBRARY)

###################################################################
# Target build
###################################################################

# Builds the Mockito source code, but does not include any run-time
# dependencies. Most projects should use mockito-target instead, which includes
# everything needed to run Mockito tests.
include $(CLEAR_VARS)

# Exclude source used to dynamically create classes since target builds use 
# dexmaker instead and including it causes conflicts.
target_src_files := \
    $(call all-java-files-under, src/main/java)
target_src_files := \
    $(filter-out src/main/java/org/mockito/internal/creation/bytebuddy/%, $(target_src_files))

LOCAL_SRC_FILES := $(target_src_files)
LOCAL_JAVA_LIBRARIES := junit objenesis-target
LOCAL_MODULE := mockito-api
LOCAL_SDK_VERSION := 16
LOCAL_MODULE_TAGS := optional
include $(BUILD_STATIC_JAVA_LIBRARY)

# Main target for dependent projects. Bundles all the run-time dependencies
# needed to run Mockito tests on the device.
include $(CLEAR_VARS)

LOCAL_MODULE := mockito-target
LOCAL_STATIC_JAVA_LIBRARIES := mockito-target-minus-junit4 junit
LOCAL_SDK_VERSION := 16
LOCAL_MODULE_TAGS := optional
include $(BUILD_STATIC_JAVA_LIBRARY)

# A mockito target that doesn't pull in junit. This is used to work around
# issues caused by multiple copies of junit in the classpath, usually when a test
# using mockito is run using android.test.runner.
include $(CLEAR_VARS)
LOCAL_MODULE := mockito-target-minus-junit4
LOCAL_STATIC_JAVA_LIBRARIES := mockito-api dexmaker dexmaker-mockmaker objenesis-target
LOCAL_JAVA_LIBRARIES := junit
LOCAL_SDK_VERSION := 16
LOCAL_MODULE_TAGS := optional
LOCAL_JAVA_LANGUAGE_VERSION := 1.7
include $(BUILD_STATIC_JAVA_LIBRARY)


###################################################################
# Host build
###################################################################

# Builds the Mockito source code, but does not include any run-time
# dependencies.
ifeq ($(HOST_OS),linux)
include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(target_src_files)
LOCAL_JAVA_LIBRARIES := \
    junit-hostdex \
    objenesis-hostdex
LOCAL_MODULE := mockito-api-hostdex
LOCAL_MODULE_TAGS := optional
include $(BUILD_HOST_DALVIK_JAVA_LIBRARY)
endif # HOST_OS == linux

# Host prebuilt dependencies.
# ============================================================
include $(CLEAR_VARS)

LOCAL_PREBUILT_JAVA_LIBRARIES := \
    mockito-byte-buddy-host:lib/byte-buddy-1.6.9$(COMMON_JAVA_PACKAGE_SUFFIX) \
    mockito-byte-buddy-agent-host:lib/byte-buddy-agent-1.6.9$(COMMON_JAVA_PACKAGE_SUFFIX) \

include $(BUILD_HOST_PREBUILT)

###################################################
# Clean up temp vars
###################################################
explicit_target_excludes :=
target_src_files :=
