# Copyright 2016 The Android Open Source Project
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

audio_service_shared_libraries := \
  libbinder \
  libbinderwrapper \
  libbrillo \
  libbrillo-binder \
  libc \
  libchrome \
  libaudioclient \
  libutils

audio_client_sources := \
  aidl/android/brillo/brilloaudioservice/IAudioServiceCallback.aidl \
  aidl/android/brillo/brilloaudioservice/IBrilloAudioService.aidl \
  audio_service_callback.cpp \
  brillo_audio_client.cpp \
  brillo_audio_client_helpers.cpp \
  brillo_audio_device_info.cpp \
  brillo_audio_device_info_internal.cpp \
  brillo_audio_manager.cpp

audio_service_sources := \
  aidl/android/brillo/brilloaudioservice/IAudioServiceCallback.aidl \
  aidl/android/brillo/brilloaudioservice/IBrilloAudioService.aidl \
  audio_daemon.cpp \
  audio_device_handler.cpp \
  audio_volume_handler.cpp \
  brillo_audio_service_impl.cpp

# Audio service.
# =============================================================================
include $(CLEAR_VARS)
LOCAL_MODULE := brilloaudioservice
LOCAL_SRC_FILES := \
  $(audio_service_sources) \
  main_audio_service.cpp
LOCAL_AIDL_INCLUDES := $(LOCAL_PATH)/aidl
LOCAL_SHARED_LIBRARIES := $(audio_service_shared_libraries)
LOCAL_CFLAGS := -Werror -Wall
LOCAL_INIT_RC := brilloaudioserv.rc
include $(BUILD_EXECUTABLE)

# Audio client library.
# =============================================================================
include $(CLEAR_VARS)
LOCAL_MODULE := libbrilloaudio
LOCAL_SRC_FILES := \
  $(audio_client_sources)
LOCAL_AIDL_INCLUDES := $(LOCAL_PATH)/aidl
LOCAL_SHARED_LIBRARIES := $(audio_service_shared_libraries)
LOCAL_CFLAGS := -Wall -Werror -std=c++14
include $(BUILD_SHARED_LIBRARY)

# Unit tests for the Brillo audio service.
# =============================================================================
include $(CLEAR_VARS)
LOCAL_MODULE := brilloaudioservice_test
LOCAL_SRC_FILES := \
  $(audio_service_sources) \
  test/audio_daemon_test.cpp \
  test/audio_device_handler_test.cpp \
  test/audio_volume_handler_test.cpp
LOCAL_AIDL_INCLUDES := $(LOCAL_PATH)/aidl
LOCAL_SHARED_LIBRARIES := \
  $(audio_service_shared_libraries)
LOCAL_STATIC_LIBRARIES := \
  libBionicGtestMain \
  libbinderwrapper_test_support \
  libchrome_test_helpers \
  libgmock
LOCAL_CFLAGS := -Werror -Wall
LOCAL_CFLAGS += -Wno-sign-compare
include $(BUILD_NATIVE_TEST)

# Unit tests for the Brillo audio client.
# =============================================================================
include $(CLEAR_VARS)
LOCAL_MODULE := brilloaudioclient_test
LOCAL_SRC_FILES := \
  $(audio_client_sources) \
  test/audio_service_callback_test.cpp \
  test/brillo_audio_client_test.cpp \
  test/brillo_audio_device_info_internal_test.cpp \
  test/brillo_audio_manager_test.cpp
LOCAL_AIDL_INCLUDES := $(LOCAL_PATH)/aidl
LOCAL_SHARED_LIBRARIES := \
  $(audio_service_shared_libraries)
LOCAL_STATIC_LIBRARIES := \
  libBionicGtestMain \
  libbinderwrapper_test_support \
  libchrome_test_helpers \
  libgmock
LOCAL_CFLAGS := -Wno-sign-compare -Wall -Werror
include $(BUILD_NATIVE_TEST)
