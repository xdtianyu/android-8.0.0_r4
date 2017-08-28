# Copyright (C) 2008 The Android Open Source Project
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

# Common project flags.
bsdiff_common_cflags := \
    -D_FILE_OFFSET_BITS=64 \
    -Wall \
    -Werror \
    -Wextra \
    -Wno-unused-parameter

bsdiff_common_static_libs := \
    libbz

# "bsdiff" program.
bsdiff_static_libs := \
    libdivsufsort64 \
    libdivsufsort \
    $(bsdiff_common_static_libs)

bsdiff_src_files := \
    bsdiff.cc

# "bspatch" program.
bspatch_src_files := \
    bspatch.cc \
    buffer_file.cc \
    extents.cc \
    extents_file.cc \
    file.cc \
    memory_file.cc \
    sink_file.cc

# Unit test files.
bsdiff_common_unittests := \
    bsdiff_unittest.cc \
    bspatch_unittest.cc \
    extents_file_unittest.cc \
    extents_unittest.cc \
    test_utils.cc

# Target static libraries.

include $(CLEAR_VARS)
LOCAL_MODULE := libbspatch
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := $(bspatch_src_files)
LOCAL_CFLAGS := $(bsdiff_common_cflags)
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)
LOCAL_STATIC_LIBRARIES := $(bsdiff_common_static_libs)
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libbsdiff
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := $(bsdiff_src_files)
LOCAL_CFLAGS := $(bsdiff_common_cflags)
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)
LOCAL_STATIC_LIBRARIES := $(bsdiff_static_libs)
include $(BUILD_STATIC_LIBRARY)

# Host static libraries.

include $(CLEAR_VARS)
LOCAL_MODULE := libbspatch
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := $(bspatch_src_files)
LOCAL_CFLAGS := $(bsdiff_common_cflags)
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)
LOCAL_STATIC_LIBRARIES := $(bsdiff_common_static_libs)
include $(BUILD_HOST_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libbsdiff
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := $(bsdiff_src_files)
LOCAL_CFLAGS := $(bsdiff_common_cflags)
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)
LOCAL_STATIC_LIBRARIES := $(bsdiff_static_libs)
include $(BUILD_HOST_STATIC_LIBRARY)

# Target executables.

include $(CLEAR_VARS)
LOCAL_MODULE := bspatch
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := bspatch_main.cc
LOCAL_CFLAGS := $(bsdiff_common_cflags)
LOCAL_STATIC_LIBRARIES := \
    libbspatch \
    $(bsdiff_common_static_libs)
include $(BUILD_EXECUTABLE)

# Host executables.

include $(CLEAR_VARS)
LOCAL_MODULE := bsdiff
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := bsdiff_main.cc
LOCAL_CFLAGS := $(bsdiff_common_cflags)
LOCAL_STATIC_LIBRARIES := \
    libbsdiff \
    $(bsdiff_static_libs)
include $(BUILD_HOST_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE := bspatch
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := bspatch_main.cc
LOCAL_CFLAGS := $(bsdiff_common_cflags)
LOCAL_STATIC_LIBRARIES := \
    libbspatch \
    $(bsdiff_common_static_libs)
include $(BUILD_HOST_EXECUTABLE)

# Unit tests.

include $(CLEAR_VARS)
LOCAL_MODULE := bsdiff_unittest
LOCAL_MODULE_TAGS := debug tests
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := $(bsdiff_common_unittests) \
    testrunner.cc
LOCAL_CFLAGS := $(bsdiff_common_cflags) \
    -DBSDIFF_TARGET_UNITTEST
LOCAL_STATIC_LIBRARIES := \
    libbsdiff \
    libbspatch \
    libgmock \
    $(bsdiff_static_libs)
include $(BUILD_NATIVE_TEST)
