# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

LOCAL_MOJO_ROOT := $(call my-dir)

JNI_GENERATOR_TOOL := \
	$(LOCAL_MOJO_ROOT)/base/android/jni_generator/jni_generator.py

generated_sources_dir := $(local-generated-sources-dir)
generated_files :=

# $(1): a single Java file
define generate-jni-header

java_file := $(1)
local_path := $(LOCAL_PATH)
target_path := $(generated_sources_dir)/jni
gen_h := $$(target_path)/$$(basename $$(notdir $$(java_file)))_jni.h

$$(gen_h) : PRIVATE_PATH := $$(local_path)
$$(gen_h) : PRIVATE_TARGET := $$(target_path)
$$(gen_h) : PRIVATE_CUSTOM_TOOL = \
  (cd $$(PRIVATE_PATH) && \
   python $$(abspath $$(JNI_GENERATOR_TOOL)) \
	 --input_file=$$(subst $$(PRIVATE_PATH)/,,$$<) \
	 --output_dir=$$(abspath $$(PRIVATE_TARGET)) \
	 --includes base/android/jni_generator/jni_generator_helper.h \
	 --ptr_type long \
	 --native_exports_optional)
$$(gen_h) : $$(local_path)/$$(java_file) $$(JNI_GENERATOR_TOOL)
	$$(transform-generated-source)

generated_files += $$(gen_h)

endef  # define generate-jni-header

# Build each file separately since the build command needs to be done per-file.
$(foreach file,$(LOCAL_JAVA_JNI_FILES),$(eval $(call generate-jni-header,$(file))))

# Add the generated sources to the C includes.
LOCAL_C_INCLUDES += $(generated_sources_dir)

# Create a stamp file after all files have been generated.
gen := $(generated_sources_dir)/.jni.stamp
$(gen) : $(generated_files)
	$(hide) echo $^ | sed -e 's/ /\n/g' > $@

# Add the stamp file as a dependency to {import,export}_includes.
$(local-intermediates-dir)/import_includes: | $(generated_sources_dir)/.jni.stamp
