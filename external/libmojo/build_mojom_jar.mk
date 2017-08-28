# Copyright 2015 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

LOCAL_MOJO_ROOT := $(call my-dir)
original_generated_sources_dir := $(call generated-sources-dir-for,$(original_module_class),$(original_module))
LOCAL_SRCJAR_LIST :=

define collect-srcjar-list

mojom_file := $(1)
target_path := $(original_generated_sources_dir)
LOCAL_SRCJAR_LIST += $$(target_path)/$$(mojom_file).srcjar

endef

# Collect all .srcjar files
$(foreach file,$(LOCAL_MOJOM_FILES),$(eval $(call collect-srcjar-list,$(file))))

local_target_path := $(call local-intermediates-dir,true)/src

$(local_target_path) : PRIVATE_SRCJAR_LIST := $(LOCAL_SRCJAR_LIST)
$(local_target_path) : PRIVATE_TARGET := $(local_target_path)
$(local_target_path) : PRIVATE_CUSTOM_TOOL = \
	(rm -rf $(PRIVATE_TARGET) && mkdir -p $(PRIVATE_TARGET) && \
	 for f in $(PRIVATE_SRCJAR_LIST); do unzip -qo -d $(PRIVATE_TARGET) $$f; done)
$(local_target_path) : $(LOCAL_SRCJAR_LIST)
	$(transform-generated-source)

LOCAL_ADDITIONAL_DEPENDENCIES += $(local_target_path) $(LOCAL_SRCJAR_LIST)
