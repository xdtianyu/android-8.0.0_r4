#
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
#

LOCAL_PATH := $(call my-dir)

include $(LOCAL_PATH)/list/vts_apk_package_list.mk
include $(LOCAL_PATH)/list/vts_bin_package_list.mk
include $(LOCAL_PATH)/list/vts_lib_package_list.mk
include $(LOCAL_PATH)/list/vts_spec_file_list.mk
include $(LOCAL_PATH)/list/vts_test_bin_package_list.mk
include $(LOCAL_PATH)/list/vts_test_lib_package_list.mk
include $(LOCAL_PATH)/list/vts_test_lib_hal_package_list.mk
include $(LOCAL_PATH)/list/vts_test_lib_hidl_package_list.mk
include $(LOCAL_PATH)/list/vts_test_lib_hidl_trace_list.mk
include $(LOCAL_PATH)/list/vts_func_fuzzer_package_list.mk
include $(LOCAL_PATH)/list/vts_test_host_lib_package_list.mk
include $(LOCAL_PATH)/list/vts_test_host_bin_package_list.mk
include $(LOCAL_PATH)/list/vts_test_hidl_hal_hash_list.mk
-include external/linux-kselftest/android/kselftest_test_list.mk
-include external/ltp/android/ltp_package_list.mk
-include vendor/google_vts/tools/build/tasks/list/vts_apk_package_list_vendor.mk

VTS_OUT_ROOT := $(HOST_OUT)/vts
VTS_TESTCASES_OUT := $(HOST_OUT)/vts/android-vts/testcases
VTS_TOOLS_OUT := $(VTS_OUT_ROOT)/android-vts/tools

# Packaging rule for android-vts.zip
test_suite_name := vts
test_suite_tradefed := vts-tradefed
test_suite_readme := test/vts/README.md

include $(BUILD_SYSTEM)/tasks/tools/compatibility.mk

.PHONY: vts
vts: $(compatibility_zip)
$(call dist-for-goals, vts, $(compatibility_zip))

# Packaging rule for android-vts.zip's testcases dir (DATA subdir).
target_native_modules := \
    $(kselftest_modules) \
    ltp \
    $(ltp_packages) \
    $(vts_apk_packages) \
    $(vts_bin_packages) \
    $(vts_lib_packages) \
    $(vts_test_bin_packages) \
    $(vts_test_lib_packages) \
    $(vts_test_lib_hal_packages) \
    $(vts_test_lib_hidl_packages) \
    $(vts_func_fuzzer_packages) \

target_native_copy_pairs :=
$(foreach m,$(target_native_modules),\
  $(eval _built_files := $(strip $(ALL_MODULES.$(m).BUILT_INSTALLED)\
  $(ALL_MODULES.$(m)$(TARGET_2ND_ARCH_MODULE_SUFFIX).BUILT_INSTALLED)))\
  $(foreach i, $(_built_files),\
    $(eval bui_ins := $(subst :,$(space),$(i)))\
    $(eval ins := $(word 2,$(bui_ins)))\
    $(if $(filter $(TARGET_OUT_ROOT)/%,$(ins)),\
      $(eval bui := $(word 1,$(bui_ins)))\
      $(eval my_built_modules += $(bui))\
      $(eval my_copy_dest := $(patsubst data/%,DATA/%,\
                               $(patsubst system/%,DATA/%,\
                                   $(patsubst $(PRODUCT_OUT)/%,%,$(ins)))))\
      $(eval target_native_copy_pairs += $(bui):$(VTS_TESTCASES_OUT)/$(my_copy_dest)))\
  ))

# Packaging rule for android-vts.zip's testcases dir (spec subdir).

target_spec_modules := \
  $(VTS_SPEC_FILE_LIST)

target_spec_copy_pairs :=
$(foreach m,$(target_spec_modules),\
  $(eval my_spec_copy_dir :=\
    spec/hardware/interfaces/$(word 2,$(subst android/hardware/, ,$(dir $(m))))/vts)\
  $(eval my_spec_copy_file := $(notdir $(m)))\
  $(eval my_spec_copy_dest := $(my_spec_copy_dir)/$(my_spec_copy_file))\
  $(eval target_spec_copy_pairs += $(m):$(VTS_TESTCASES_OUT)/$(my_spec_copy_dest)))

$(foreach m,$(vts_spec_file_list),\
  $(if $(wildcard $(m)),\
    $(eval target_spec_copy_pairs += $(m):$(VTS_TESTCASES_OUT)/spec/$(m))))

target_trace_modules := \
    $(vts_test_lib_hidl_trace_list) \

target_trace_copy_pairs :=
$(foreach m,$(target_trace_modules),\
  $(if $(wildcard $(m)),\
    $(eval target_trace_copy_pairs += $(m):$(VTS_TESTCASES_OUT)/hal-hidl-trace/$(m))))

target_prebuilt_apk_modules := \
    $(vts_prebuilt_apk_packages) \

target_prebuilt_apk_copy_pairs :=
$(foreach m,$(target_prebuilt_apk_modules),\
  $(if $(wildcard $(m)),\
    $(eval target_prebuilt_apk_copy_pairs += $(m):$(VTS_TESTCASES_OUT)/prebuilt-apk/$(m))))

target_hal_hash_modules := \
    $(vts_test_hidl_hal_hash_list) \

target_hal_hash_copy_pairs :=
$(foreach m,$(target_hal_hash_modules),\
  $(if $(wildcard $(m)),\
    $(eval target_hal_hash_copy_pairs += $(m):$(VTS_TESTCASES_OUT)/hal-hidl-hash/$(m))))


# Packaging rule for host-side test native packages

target_hostdriven_modules := \
  $(vts_test_host_lib_packages) \
  $(vts_test_host_bin_packages) \

target_hostdriven_copy_pairs :=
$(foreach m,$(target_hostdriven_modules),\
  $(eval _built_files := $(strip $(ALL_MODULES.$(m).BUILT_INSTALLED)\
  $(ALL_MODULES.$(m)$(HOST_2ND_ARCH_MODULE_SUFFIX).BUILT_INSTALLED)))\
  $(foreach i, $(_built_files),\
    $(eval bui_ins := $(subst :,$(space),$(i)))\
    $(eval ins := $(word 2,$(bui_ins)))\
    $(if $(filter $(HOST_OUT)/%,$(ins)),\
      $(eval bui := $(word 1,$(bui_ins)))\
      $(eval my_built_modules += $(bui))\
      $(eval my_copy_dest := $(patsubst $(HOST_OUT)/%,%,$(ins)))\
      $(eval target_hostdriven_copy_pairs += $(bui):$(VTS_TESTCASES_OUT)/host/$(my_copy_dest)))\
  ))

host_additional_deps_copy_pairs := \
  test/vts/tools/vts-tradefed/etc/vts-tradefed_win.bat:$(VTS_TOOLS_OUT)/vts-tradefed_win.bat \
  $(foreach d,$(ADDITIONAL_VTS_JARS),\
    $(d):$(VTS_TOOLS_OUT)/$(notdir $(d)))

# Packaging rule for host-side Python logic, configs, and data files

host_framework_files := \
  $(call find-files-in-subdirs,test/vts,"*.py" -and -type f,.) \
  $(call find-files-in-subdirs,test/vts,"*.config" -and -type f,.) \
  $(call find-files-in-subdirs,test/vts,"*.push" -and -type f,.)

host_framework_copy_pairs := \
  $(foreach f,$(host_framework_files),\
    test/vts/$(f):$(VTS_TESTCASES_OUT)/vts/$(f))

host_testcase_files := \
  $(call find-files-in-subdirs,test/vts-testcase,"*.py" -and -type f,.) \
  $(call find-files-in-subdirs,test/vts-testcase,"*.config" -and -type f,.) \
  $(call find-files-in-subdirs,test/vts-testcase,"*.push" -and -type f,.) \
  $(call find-files-in-subdirs,test/vts-testcase,"android-base*.cfg" -and -type f,.)

host_testcase_copy_pairs := \
  $(foreach f,$(host_testcase_files),\
    test/vts-testcase/$(f):$(VTS_TESTCASES_OUT)/vts/testcases/$(f))

host_camera_its_files := \
  $(call find-files-in-subdirs,cts/apps/CameraITS,"*.py" -and -type f,.) \
  $(call find-files-in-subdirs,cts/apps/CameraITS,"*.pdf" -and -type f,.) \
  $(call find-files-in-subdirs,cts/apps/CameraITS,"*.png" -and -type f,.)

host_camera_its_copy_pairs := \
  $(foreach f,$(host_camera_its_files),\
    cts/apps/CameraITS/$(f):$(VTS_TESTCASES_OUT)/CameraITS/$(f))

host_systrace_files := \
  $(filter-out .git/%, \
    $(call find-files-in-subdirs,external/chromium-trace,"*" -and -type f,.))

host_systrace_copy_pairs := \
  $(foreach f,$(host_systrace_files),\
    external/chromium-trace/$(f):$(VTS_OUT_ROOT)/android-vts/tools/external/chromium-trace/$(f))

media_test_res_files := \
  $(call find-files-in-subdirs,hardware/interfaces/media/res,"*.*" -and -type f,.) \

media_test_res_copy_pairs := \
  $(foreach f,$(media_test_res_files),\
    hardware/interfaces/media/res/$(f):$(VTS_TESTCASES_OUT)/DATA/media/res/$(f))

audio_test_res_files := \
  $(call find-files-in-subdirs,hardware/interfaces/audio,"*.xsd" -and -type f,.) \

audio_test_res_copy_pairs := \
  $(foreach f,$(audio_test_res_files),\
    hardware/interfaces/audio/$(f):$(VTS_TESTCASES_OUT)/DATA/hardware/interfaces/audio/$(f))

$(compatibility_zip): \
  $(call copy-many-files,$(target_native_copy_pairs)) \
  $(call copy-many-files,$(target_spec_copy_pairs)) \
  $(call copy-many-files,$(target_trace_copy_pairs)) \
  $(call copy-many-files,$(target_hostdriven_copy_pairs)) \
  $(call copy-many-files,$(target_prebuilt_apk_copy_pairs)) \
  $(call copy-many-files,$(target_hal_hash_copy_pairs)) \
  $(call copy-many-files,$(host_additional_deps_copy_pairs)) \
  $(call copy-many-files,$(host_framework_copy_pairs)) \
  $(call copy-many-files,$(host_testcase_copy_pairs)) \
  $(call copy-many-files,$(host_camera_its_copy_pairs)) \
  $(call copy-many-files,$(host_systrace_copy_pairs)) \
  $(call copy-many-files,$(media_test_res_copy_pairs)) \
  $(call copy-many-files,$(audio_test_res_copy_pairs))

# vendor-specific host logic
vendor_testcase_files := \
  $(call find-files-in-subdirs,vendor/google_vts/testcases,"*.py" -and -type f,.) \
  $(call find-files-in-subdirs,vendor/google_vts/testcases,"*.config" -and -type f,.) \
  $(call find-files-in-subdirs,vendor/google_vts/testcases,"*.push" -and -type f,.) \

vendor_testcase_copy_pairs := \
  $(foreach f,$(vendor_testcase_files),\
    vendor/google_vts/testcases/$(f):$(VTS_TESTCASES_OUT)/vts/testcases/$(f))

vendor_tf_files :=
ifeq ($(BUILD_GOOGLE_VTS), true)
  vendor_tf_files += \
    $(HOST_OUT_JAVA_LIBRARIES)/google-tradefed-vts-prebuilt.jar
endif

vendor_tf_copy_pairs := \
  $(foreach f,$(vendor_tf_files),\
    $(f):$(VTS_OUT_ROOT)/android-vts/tools/$(notdir $(f)))

$(compatibility_zip): \
  $(call copy-many-files,$(vendor_testcase_copy_pairs)) \
  $(call copy-many-files,$(vendor_tf_copy_pairs))
