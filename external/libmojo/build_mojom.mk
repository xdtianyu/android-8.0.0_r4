# Copyright 2015 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

LOCAL_MOJO_ROOT := $(call my-dir)

include $(LOCAL_MOJO_ROOT)/build_mojom_template_tools.mk

mojo_generated_sources_dir := \
	$(call generated-sources-dir-for,SHARED_LIBRARIES,libmojo,,)
generated_templates_dir := $(mojo_generated_sources_dir)/templates
generated_sources_dir := $(local-generated-sources-dir)
generated_files :=

# $(1): a single mojom file
define generate-mojom-source

mojom_file := $(1)
local_path := $(LOCAL_PATH)
target_path := $(generated_sources_dir)
gen_cc := $$(target_path)/$$(mojom_file).cc
gen_h := $$(target_path)/$$(mojom_file).h
gen_internal_h := $$(target_path)/$$(mojom_file)-internal.h
gen_srcjar := $$(target_path)/$$(mojom_file).srcjar
gen_src := $$(gen_cc) $$(gen_h) $$(gen_internal_h) $$(gen_srcjar)
mojom_bindings_generator_flags := $$(LOCAL_MOJOM_BINDINGS_GENERATOR_FLAGS)
# TODO(lhchavez): Generate these files instead of expecting them to be there.
mojom_type_mappings :=
ifneq ($$(LOCAL_MOJOM_TYPE_MAPPINGS),)
	mojom_type_mappings := $$(local_path)/$$(LOCAL_MOJOM_TYPE_MAPPINGS)
	mojom_bindings_generator_flags += --typemap $$(abspath $$(mojom_type_mappings))
endif

$$(gen_cc) : PRIVATE_PATH := $$(local_path)
$$(gen_cc) : PRIVATE_MOJO_ROOT := $$(LOCAL_MOJO_ROOT)
$$(gen_cc) : PRIVATE_TARGET := $$(target_path)
$$(gen_cc) : PRIVATE_FLAGS := $$(mojom_bindings_generator_flags)
$$(gen_cc) : PRIVATE_CUSTOM_TOOL = \
  (cd $$(PRIVATE_PATH) && \
   python $$(abspath $$(MOJOM_BINDINGS_GENERATOR)) \
   --use_bundled_pylibs generate \
	     $$(subst $$(PRIVATE_PATH)/,,$$<) \
	 -I $$(abspath $$(PRIVATE_MOJO_ROOT)):$$(abspath $$(PRIVATE_MOJO_ROOT)) \
	 -o $$(abspath $$(PRIVATE_TARGET)) \
	 --bytecode_path $$(abspath $$(generated_templates_dir)) \
	 -g c++,java \
	 $$(PRIVATE_FLAGS))
$$(gen_cc) : $$(local_path)/$$(mojom_file) $$(mojom_type_mappings) \
		$$(MOJOM_TEMPLATE_TOOLS) $$(generated_templates_dir)/.stamp
	$$(transform-generated-source)

# Make the other generated files depend on the .cc file. Unfortunately, the
# Make->ninja translation would generate one individual rule for each generated
# file, resulting in the files being (racily) generated multiple times.
$$(gen_internal_h): $$(gen_cc)
	$$(hide) touch $$@

$$(gen_h): $$(gen_cc)
	$$(hide) touch $$@

$$(gen_srcjar): $$(gen_cc)
	$$(hide) touch $$@

generated_files += $$(gen_src)

# LOCAL_GENERATED_SOURCES will filter out anything that's not a C/C++ source
# file.
LOCAL_GENERATED_SOURCES += $$(gen_src)

endef  # define generate-mojom-source

# Build each file separately since the build command needs to be done per-file.
$(foreach file,$(LOCAL_MOJOM_FILES),$(eval $(call generate-mojom-source,$(file))))

# Add the generated sources to the C includes.
LOCAL_C_INCLUDES += $(generated_sources_dir)

# Also add the generated sources to the C exports.
LOCAL_EXPORT_C_INCLUDE_DIRS += $(generated_sources_dir)
