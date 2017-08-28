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

include $(call all-subdir-makefiles)

ifeq ($(HOST_OS),linux)

ACTS_DISTRO := $(HOST_OUT)/acts-dist/acts.zip

$(ACTS_DISTRO): $(sort $(shell find $(LOCAL_PATH)/acts/framework))
	@echo "Packaging ACTS into $(ACTS_DISTRO)"
	@mkdir -p $(HOST_OUT)/acts-dist/
	@rm -f $(HOST_OUT)/acts-dist/acts.zip
	$(hide) zip -r $(HOST_OUT)/acts-dist/acts.zip tools/test/connectivity/acts/*
acts: $(ACTS_DISTRO)

$(call dist-for-goals,tests,$(ACTS_DISTRO))

endif
