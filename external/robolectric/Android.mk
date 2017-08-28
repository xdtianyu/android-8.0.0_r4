# Copyright (C) 2017 The Android Open Source Project
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

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := robolectric_android-all
LOCAL_JACK_ENABLED := disabled

# Re-package icudata under android.icu.**.
LOCAL_JARJAR_RULES := external/icu/icu4j/liblayout-jarjar-rules.txt

LOCAL_STATIC_JAVA_LIBRARIES := \
    conscrypt \
    core-libart \
    ext \
    framework \
    icu4j-icudata \
    icu4j-icutzdata \
    ims-common \
    legacy-test \
    libphonenumber \
    okhttp \
    services \
    services.accessibility \
    telephony-common

LOCAL_JAVA_RESOURCE_FILES := \
    frameworks/base/core/res/assets \
    frameworks/base/core/res/res

include $(BUILD_STATIC_JAVA_LIBRARY)

# Copy the tzdata, preserving its path.
$(LOCAL_INTERMEDIATE_TARGETS): $(call copy-many-files,\
    bionic/libc/zoneinfo/tzdata:$(intermediates.COMMON)/usr/share/zoneinfo/tzdata \
    bionic/libc/zoneinfo/tzlookup.xml:$(intermediates.COMMON)/usr/share/zoneinfo/tzlookup.xml)
$(LOCAL_INTERMEDIATE_TARGETS): PRIVATE_EXTRA_JAR_ARGS += \
    -C "$(intermediates.COMMON)" "usr/share/zoneinfo"

# Distribute the android-all artifact with SDK artifacts.
ifneq ($(filter eng.%,$(BUILD_NUMBER)),)
$(call dist-for-goals,sdk win_sdk,\
    $(LOCAL_BUILT_MODULE):android-all-$(PLATFORM_VERSION)-robolectric-eng.$(USER).jar)
else
$(call dist-for-goals,sdk win_sdk,\
    $(LOCAL_BUILT_MODULE):android-all-$(PLATFORM_VERSION)-robolectric-$(BUILD_NUMBER).jar)
endif
