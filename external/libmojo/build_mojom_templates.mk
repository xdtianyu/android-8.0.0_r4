# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

LOCAL_MOJO_ROOT := $(call my-dir)

include $(LOCAL_MOJO_ROOT)/build_mojom_template_tools.mk

MOJOM_TEMPLATE_SOURCES := $(shell find \
	$(LOCAL_MOJO_ROOT)/mojo/public/tools/bindings/generators -name '*.tmpl')

generated_templates_dir := \
	$(call generated-sources-dir-for,SHARED_LIBRARIES,libmojo,,)/templates

gen := $(generated_templates_dir)/.stamp
sources := $(MOJOM_TEMPLATE_SOURCES)
$(gen) : PRIVATE_TOOL := $(MOJOM_BINDINGS_GENERATOR)
$(gen) : PRIVATE_OUT_DIR := $(generated_templates_dir)
$(gen) : $(sources) $(MOJOM_TEMPLATE_TOOLS)
	@echo generate_mojo_templates: $(PRIVATE_OUT_DIR)
	$(hide) rm -rf $(dir $@)
	$(hide) mkdir -p $(dir $@)
	$(hide) python $(PRIVATE_TOOL) --use_bundled_pylibs precompile \
		-o $(PRIVATE_OUT_DIR)
	$(hide) touch $@

# Make the files that are actually generated depend on the .stamp file.
$(generated_templates_dir)/cpp_templates.zip: $(gen)
	$(hide) touch $@

$(generated_templates_dir)/java_templates.zip: $(gen)
	$(hide) touch $@

$(generated_templates_dir)/js_templates.zip: $(gen)
	$(hide) touch $@
