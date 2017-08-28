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

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libhwbinder_benchmark
LOCAL_MODULE_STEM_64 := libhwbinder_benchmark64
LOCAL_MODULE_STEM_32 := libhwbinder_benchmark32

LOCAL_MODULE_TAGS := eng tests

LOCAL_SRC_FILES := Benchmark.cpp
LOCAL_SHARED_LIBRARIES := \
    libhwbinder \
    libhidlbase \
    libhidltransport \
    liblog \
    libutils \
    android.hardware.tests.libhwbinder@1.0
LOCAL_REQUIRED_MODULES := android.hardware.tests.libhwbinder@1.0-impl

LOCAL_MULTILIB := both

include $(BUILD_NATIVE_BENCHMARK)


# build for benchmark test based on binder.
include $(CLEAR_VARS)
LOCAL_MODULE := libbinder_benchmark
LOCAL_MODULE_STEM_64 := libbinder_benchmark64
LOCAL_MODULE_STEM_32 := libbinder_benchmark32

LOCAL_MODULE_TAGS := eng tests

LOCAL_SRC_FILES := Benchmark_binder.cpp
LOCAL_SHARED_LIBRARIES := \
    libbinder \
    libutils \
    android.hardware.tests.libbinder

LOCAL_MULTILIB := both

include $(BUILD_NATIVE_BENCHMARK)

# build for throughput benchmark test for hwbinder.
include $(CLEAR_VARS)
LOCAL_MODULE := hwbinderThroughputTest

LOCAL_MODULE_TAGS := eng tests

LOCAL_SRC_FILES := Benchmark_throughput.cpp
LOCAL_SHARED_LIBRARIES := \
    libhwbinder \
    libhidlbase \
    libhidltransport \
    liblog \
    libutils \
    android.hardware.tests.libhwbinder@1.0

LOCAL_REQUIRED_MODULES := android.hardware.tests.libhwbinder@1.0-impl
LOCAL_C_INCLUDES := system/libhwbinder/include

LOCAL_MULTILIB := both
include $(BUILD_NATIVE_TEST)

# build for latency benchmark test for hwbinder.
include $(CLEAR_VARS)
LOCAL_MODULE := libhwbinder_latency

LOCAL_MODULE_TAGS := eng tests

LOCAL_SRC_FILES := Latency.cpp
LOCAL_SHARED_LIBRARIES := \
    libhwbinder \
    libhidlbase \
    libhidltransport \
    liblog \
    libutils \
    android.hardware.tests.libhwbinder@1.0

LOCAL_REQUIRED_MODULES := android.hardware.tests.libhwbinder@1.0-impl
LOCAL_C_INCLUDES := system/libhwbinder/include

LOCAL_MULTILIB := both
include $(BUILD_NATIVE_TEST)
