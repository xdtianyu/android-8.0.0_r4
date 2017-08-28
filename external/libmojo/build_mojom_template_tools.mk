# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

LOCAL_MOJO_ROOT := $(call my-dir)

MOJOM_BINDINGS_GENERATOR := \
	$(LOCAL_MOJO_ROOT)/mojo/public/tools/bindings/mojom_bindings_generator.py

MOJOM_TEMPLATE_TOOLS := \
	$(shell find $(LOCAL_MOJO_ROOT)/mojo/public/tools/bindings -name '*.py') \
	$(shell find $(LOCAL_MOJO_ROOT)/third_party -name '*.py')
