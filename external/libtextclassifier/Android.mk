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

# Useful environment variables that can be set on the mmma command line, as
# <key>=<value> pairs:
#
# LIBTEXTCLASSIFIER_STRIP_OPTS: (optional) value for LOCAL_STRIP_MODULE (for all
#   modules we build).  NOT for prod builds.  Can be set to keep_symbols for
#   debug / treemap purposes.


LOCAL_PATH := $(call my-dir)

# Custom C/C++ compilation flags:
MY_LIBTEXTCLASSIFIER_CFLAGS := \
    -Wno-unused-parameter \
    -Wno-sign-compare \
    -Wno-missing-field-initializers \
    -Wno-ignored-qualifiers \
    -Wno-undefined-var-template \
    -fvisibility=hidden

# Only enable debug logging in userdebug/eng builds.
ifneq (,$(filter userdebug eng, $(TARGET_BUILD_VARIANT)))
  MY_LIBTEXTCLASSIFIER_CFLAGS += -DTC_DEBUG_LOGGING=1
endif

# ------------------------
# libtextclassifier_protos
# ------------------------

include $(CLEAR_VARS)

LOCAL_MODULE := libtextclassifier_protos

LOCAL_STRIP_MODULE := $(LIBTEXTCLASSIFIER_STRIP_OPTS)

LOCAL_SRC_FILES := $(call all-proto-files-under, .)
LOCAL_SHARED_LIBRARIES := libprotobuf-cpp-lite

include $(BUILD_STATIC_LIBRARY)

# -----------------
# libtextclassifier
# -----------------

include $(CLEAR_VARS)
LOCAL_MODULE := libtextclassifier

proto_sources_dir := $(generated_sources_dir)

LOCAL_CPP_EXTENSION := .cc
LOCAL_CFLAGS += $(MY_LIBTEXTCLASSIFIER_CFLAGS)
LOCAL_STRIP_MODULE := $(LIBTEXTCLASSIFIER_STRIP_OPTS)

LOCAL_SRC_FILES := $(filter-out tests/%,$(call all-subdir-cpp-files))
LOCAL_C_INCLUDES += $(proto_sources_dir)/proto/external/libtextclassifier

LOCAL_STATIC_LIBRARIES += libtextclassifier_protos
LOCAL_SHARED_LIBRARIES += libprotobuf-cpp-lite
LOCAL_SHARED_LIBRARIES += liblog
LOCAL_SHARED_LIBRARIES += libicuuc libicui18n
LOCAL_REQUIRED_MODULES := textclassifier.langid.model
LOCAL_REQUIRED_MODULES += textclassifier.smartselection.en.model

LOCAL_ADDITIONAL_DEPENDENCIES += $(LOCAL_PATH)/jni.lds
LOCAL_LDFLAGS += -Wl,-version-script=$(LOCAL_PATH)/jni.lds

include $(BUILD_SHARED_LIBRARY)

# -----------------------
# libtextclassifier_tests
# -----------------------

include $(CLEAR_VARS)

LOCAL_MODULE := libtextclassifier_tests
LOCAL_MODULE_TAGS := tests

LOCAL_CPP_EXTENSION := .cc
LOCAL_CFLAGS += $(MY_LIBTEXTCLASSIFIER_CFLAGS)
LOCAL_STRIP_MODULE := $(LIBTEXTCLASSIFIER_STRIP_OPTS)

LOCAL_TEST_DATA := $(call find-test-data-in-subdirs, $(LOCAL_PATH), *, tests/testdata)

LOCAL_CPPFLAGS_32 += -DTEST_DATA_DIR="\"/data/nativetest/libtextclassifier_tests/tests/testdata/\""
LOCAL_CPPFLAGS_64 += -DTEST_DATA_DIR="\"/data/nativetest64/libtextclassifier_tests/tests/testdata/\""

LOCAL_SRC_FILES := $(call all-subdir-cpp-files)
LOCAL_C_INCLUDES += $(proto_sources_dir)/proto/external/libtextclassifier

LOCAL_STATIC_LIBRARIES += libtextclassifier_protos libgmock
LOCAL_SHARED_LIBRARIES += libprotobuf-cpp-lite
LOCAL_SHARED_LIBRARIES += liblog
LOCAL_SHARED_LIBRARIES += libicuuc libicui18n

include $(BUILD_NATIVE_TEST)

# ------------
# LangId model
# ------------

include $(CLEAR_VARS)
LOCAL_MODULE        := textclassifier.langid.model
LOCAL_MODULE_CLASS  := ETC
LOCAL_MODULE_OWNER := google
LOCAL_SRC_FILES     := ./models/textclassifier.langid.model
LOCAL_MODULE_PATH   := $(TARGET_OUT_ETC)/textclassifier
include $(BUILD_PREBUILT)

# ----------------------
# Smart Selection models
# ----------------------

include $(CLEAR_VARS)
LOCAL_MODULE        := textclassifier.smartselection.ar.model
LOCAL_MODULE_CLASS  := ETC
LOCAL_MODULE_OWNER  := google
LOCAL_SRC_FILES     := ./models/textclassifier.smartselection.ar.model
LOCAL_MODULE_PATH   := $(TARGET_OUT_ETC)/textclassifier
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE        := textclassifier.smartselection.de.model
LOCAL_MODULE_CLASS  := ETC
LOCAL_MODULE_OWNER  := google
LOCAL_SRC_FILES     := ./models/textclassifier.smartselection.de.model
LOCAL_MODULE_PATH   := $(TARGET_OUT_ETC)/textclassifier
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE        := textclassifier.smartselection.en.model
LOCAL_MODULE_CLASS  := ETC
LOCAL_MODULE_OWNER  := google
LOCAL_SRC_FILES     := ./models/textclassifier.smartselection.en.model
LOCAL_MODULE_PATH   := $(TARGET_OUT_ETC)/textclassifier
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE        := textclassifier.smartselection.es.model
LOCAL_MODULE_CLASS  := ETC
LOCAL_MODULE_OWNER  := google
LOCAL_SRC_FILES     := ./models/textclassifier.smartselection.es.model
LOCAL_MODULE_PATH   := $(TARGET_OUT_ETC)/textclassifier
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE        := textclassifier.smartselection.fr.model
LOCAL_MODULE_CLASS  := ETC
LOCAL_MODULE_OWNER  := google
LOCAL_SRC_FILES     := ./models/textclassifier.smartselection.fr.model
LOCAL_MODULE_PATH   := $(TARGET_OUT_ETC)/textclassifier
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE        := textclassifier.smartselection.it.model
LOCAL_MODULE_CLASS  := ETC
LOCAL_MODULE_OWNER  := google
LOCAL_SRC_FILES     := ./models/textclassifier.smartselection.it.model
LOCAL_MODULE_PATH   := $(TARGET_OUT_ETC)/textclassifier
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE        := textclassifier.smartselection.ja.model
LOCAL_MODULE_CLASS  := ETC
LOCAL_MODULE_OWNER  := google
LOCAL_SRC_FILES     := ./models/textclassifier.smartselection.ja.model
LOCAL_MODULE_PATH   := $(TARGET_OUT_ETC)/textclassifier
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE        := textclassifier.smartselection.ko.model
LOCAL_MODULE_CLASS  := ETC
LOCAL_MODULE_OWNER  := google
LOCAL_SRC_FILES     := ./models/textclassifier.smartselection.ko.model
LOCAL_MODULE_PATH   := $(TARGET_OUT_ETC)/textclassifier
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE        := textclassifier.smartselection.nl.model
LOCAL_MODULE_CLASS  := ETC
LOCAL_MODULE_OWNER  := google
LOCAL_SRC_FILES     := ./models/textclassifier.smartselection.nl.model
LOCAL_MODULE_PATH   := $(TARGET_OUT_ETC)/textclassifier
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE        := textclassifier.smartselection.pl.model
LOCAL_MODULE_CLASS  := ETC
LOCAL_MODULE_OWNER  := google
LOCAL_SRC_FILES     := ./models/textclassifier.smartselection.pl.model
LOCAL_MODULE_PATH   := $(TARGET_OUT_ETC)/textclassifier
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE        := textclassifier.smartselection.pt-PT.model
LOCAL_MODULE_CLASS  := ETC
LOCAL_MODULE_OWNER  := google
LOCAL_SRC_FILES     := ./models/textclassifier.smartselection.pt-PT.model
LOCAL_MODULE_PATH   := $(TARGET_OUT_ETC)/textclassifier
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE        := textclassifier.smartselection.ru.model
LOCAL_MODULE_CLASS  := ETC
LOCAL_MODULE_OWNER  := google
LOCAL_SRC_FILES     := ./models/textclassifier.smartselection.ru.model
LOCAL_MODULE_PATH   := $(TARGET_OUT_ETC)/textclassifier
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE        := textclassifier.smartselection.th.model
LOCAL_MODULE_CLASS  := ETC
LOCAL_MODULE_OWNER  := google
LOCAL_SRC_FILES     := ./models/textclassifier.smartselection.th.model
LOCAL_MODULE_PATH   := $(TARGET_OUT_ETC)/textclassifier
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE        := textclassifier.smartselection.tr.model
LOCAL_MODULE_CLASS  := ETC
LOCAL_MODULE_OWNER  := google
LOCAL_SRC_FILES     := ./models/textclassifier.smartselection.tr.model
LOCAL_MODULE_PATH   := $(TARGET_OUT_ETC)/textclassifier
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE        := textclassifier.smartselection.zh-Hant.model
LOCAL_MODULE_CLASS  := ETC
LOCAL_MODULE_OWNER  := google
LOCAL_SRC_FILES     := ./models/textclassifier.smartselection.zh-Hant.model
LOCAL_MODULE_PATH   := $(TARGET_OUT_ETC)/textclassifier
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE        := textclassifier.smartselection.zh.model
LOCAL_MODULE_CLASS  := ETC
LOCAL_MODULE_OWNER  := google
LOCAL_SRC_FILES     := ./models/textclassifier.smartselection.zh.model
LOCAL_MODULE_PATH   := $(TARGET_OUT_ETC)/textclassifier
include $(BUILD_PREBUILT)

# -----------------------
# Smart Selection bundles
# -----------------------

include $(CLEAR_VARS)
LOCAL_MODULE           := textclassifier.smartselection.bundle1
LOCAL_REQUIRED_MODULES := textclassifier.smartselection.en.model
LOCAL_REQUIRED_MODULES += textclassifier.smartselection.es.model
LOCAL_REQUIRED_MODULES += textclassifier.smartselection.de.model
LOCAL_REQUIRED_MODULES += textclassifier.smartselection.fr.model
include $(BUILD_STATIC_LIBRARY)
