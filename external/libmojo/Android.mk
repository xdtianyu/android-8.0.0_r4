# Copyright 2015 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# TODO(lhchavez): Convert this to Android.bp
LOCAL_PATH:= $(call my-dir)

# Build native shared library.
include $(CLEAR_VARS)

LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE := libmojo
LOCAL_MODULE_TAGS := optional
LOCAL_CPP_EXTENSION := .cc

LOCAL_MOJOM_FILES := \
	mojo/common/common_custom_types.mojom \
	mojo/common/string16.mojom \
	mojo/public/interfaces/bindings/interface_control_messages.mojom \
	mojo/public/interfaces/bindings/pipe_control_messages.mojom \

# This file was copied from out/Release in a Chrome checkout.
# TODO(lhchavez): Generate this file instead of hardcoding it.
LOCAL_MOJOM_TYPE_MAPPINGS := \
	gen/mojo/common/common_custom_types__type_mappings \

LOCAL_MOJOM_BINDINGS_GENERATOR_FLAGS := --use_new_wrapper_types

# Prepares all necessary templates.
include $(LOCAL_PATH)/build_mojom_templates.mk

# Compiles all local mojom files.
include $(LOCAL_PATH)/build_mojom.mk

# Runtime.class is added (instead of Runtime.java that lives in libcore) since
# the script that generates the JNI files does not handle compiling it well.
LOCAL_JAVA_JNI_FILES := \
	base/android/java/src/org/chromium/base/BuildInfo.java \
	base/android/java/src/org/chromium/base/ContentUriUtils.java \
	base/android/java/src/org/chromium/base/ContextUtils.java \
	base/android/java/src/org/chromium/base/PathUtils.java \
	base/android/java/src/org/chromium/base/SystemMessageHandler.java \
	jni/java/lang/Runtime.class \
	mojo/android/system/src/org/chromium/mojo/system/impl/BaseRunLoop.java \
	mojo/android/system/src/org/chromium/mojo/system/impl/CoreImpl.java \

# Generate all JNI header files.
include $(LOCAL_PATH)/build_generated_jni.mk

LOCAL_SRC_FILES := \
	base/android/build_info.cc \
	base/android/content_uri_utils.cc \
	base/android/context_utils.cc \
	base/android/java_runtime.cc \
	base/android/jni_android.cc \
	base/android/jni_string.cc \
	base/android/path_utils.cc \
	base/android/scoped_java_ref.cc \
	base/base_paths.cc \
	base/base_paths_android.cc \
	base/debug/proc_maps_linux.cc \
	base/debug/stack_trace_android.cc \
	base/files/file_util_android.cc \
	base/message_loop/message_pump_android.cc \
	base/path_service.cc \
	base/threading/thread_local_android.cc \
	base/trace_event/java_heap_dump_provider_android.cc \
	base/trace_event/trace_event_android.cc \
	ipc/brokerable_attachment.cc \
	ipc/ipc_message.cc \
	ipc/ipc_message_attachment.cc \
	ipc/ipc_message_attachment_set.cc \
	ipc/ipc_message_utils.cc \
	ipc/ipc_mojo_handle_attachment.cc \
	ipc/ipc_mojo_message_helper.cc \
	ipc/ipc_mojo_param_traits.cc \
	ipc/ipc_platform_file_attachment_posix.cc \
	ipc/placeholder_brokerable_attachment.cc \
	mojo/android/system/base_run_loop.cc \
	mojo/android/system/core_impl.cc \
	mojo/edk/embedder/embedder.cc \
	mojo/common/common_custom_types_struct_traits.cc \
	mojo/edk/embedder/entrypoints.cc \
	mojo/edk/embedder/platform_channel_pair.cc \
	mojo/edk/embedder/platform_channel_pair_posix.cc \
	mojo/edk/embedder/platform_channel_utils_posix.cc \
	mojo/edk/embedder/platform_handle.cc \
	mojo/edk/embedder/platform_handle_utils_posix.cc \
	mojo/edk/embedder/platform_shared_buffer.cc \
	mojo/edk/system/awakable_list.cc \
	mojo/edk/system/broker_host_posix.cc \
	mojo/edk/system/broker_posix.cc \
	mojo/edk/system/channel.cc \
	mojo/edk/system/channel_posix.cc \
	mojo/edk/system/configuration.cc \
	mojo/edk/system/core.cc \
	mojo/edk/system/data_pipe_consumer_dispatcher.cc \
	mojo/edk/system/data_pipe_control_message.cc \
	mojo/edk/system/data_pipe_producer_dispatcher.cc \
	mojo/edk/system/dispatcher.cc \
	mojo/edk/system/handle_table.cc \
	mojo/edk/system/mapping_table.cc \
	mojo/edk/system/message_for_transit.cc \
	mojo/edk/system/message_pipe_dispatcher.cc \
	mojo/edk/system/node_channel.cc \
	mojo/edk/system/node_controller.cc \
	mojo/edk/system/platform_handle_dispatcher.cc \
	mojo/edk/system/ports/event.cc \
	mojo/edk/system/ports/message.cc \
	mojo/edk/system/ports/message_queue.cc \
	mojo/edk/system/ports/name.cc \
	mojo/edk/system/ports/node.cc \
	mojo/edk/system/ports/port.cc \
	mojo/edk/system/ports/port_ref.cc \
	mojo/edk/system/ports_message.cc \
	mojo/edk/system/request_context.cc \
	mojo/edk/system/shared_buffer_dispatcher.cc \
	mojo/edk/system/wait_set_dispatcher.cc \
	mojo/edk/system/waiter.cc \
	mojo/edk/system/watcher.cc \
	mojo/edk/system/watcher_set.cc \
	mojo/message_pump/handle_watcher.cc \
	mojo/message_pump/message_pump_mojo.cc \
	mojo/message_pump/time_helper.cc \
	mojo/public/c/system/thunks.cc \
	mojo/public/cpp/bindings/lib/array_internal.cc \
	mojo/public/cpp/bindings/lib/associated_group.cc \
	mojo/public/cpp/bindings/lib/associated_group_controller.cc \
	mojo/public/cpp/bindings/lib/bindings_internal.cc \
	mojo/public/cpp/bindings/lib/connector.cc \
	mojo/public/cpp/bindings/lib/control_message_handler.cc \
	mojo/public/cpp/bindings/lib/control_message_proxy.cc \
	mojo/public/cpp/bindings/lib/filter_chain.cc \
	mojo/public/cpp/bindings/lib/fixed_buffer.cc \
	mojo/public/cpp/bindings/lib/interface_endpoint_client.cc \
	mojo/public/cpp/bindings/lib/message.cc \
	mojo/public/cpp/bindings/lib/message_buffer.cc \
	mojo/public/cpp/bindings/lib/message_builder.cc \
	mojo/public/cpp/bindings/lib/message_filter.cc \
	mojo/public/cpp/bindings/lib/message_header_validator.cc \
	mojo/public/cpp/bindings/lib/multiplex_router.cc \
	mojo/public/cpp/bindings/lib/native_struct.cc \
	mojo/public/cpp/bindings/lib/native_struct_data.cc \
	mojo/public/cpp/bindings/lib/native_struct_serialization.cc \
	mojo/public/cpp/bindings/lib/no_interface.cc \
	mojo/public/cpp/bindings/lib/pipe_control_message_handler.cc \
	mojo/public/cpp/bindings/lib/pipe_control_message_proxy.cc \
	mojo/public/cpp/bindings/lib/router.cc \
	mojo/public/cpp/bindings/lib/scoped_interface_endpoint_handle.cc \
	mojo/public/cpp/bindings/lib/serialization_context.cc \
	mojo/public/cpp/bindings/lib/sync_handle_registry.cc \
	mojo/public/cpp/bindings/lib/sync_handle_watcher.cc \
	mojo/public/cpp/bindings/lib/validation_context.cc \
	mojo/public/cpp/bindings/lib/validation_errors.cc \
	mojo/public/cpp/bindings/lib/validation_util.cc \
	mojo/public/cpp/system/watcher.cc \

LOCAL_CFLAGS := \
	-Wno-unused-parameter \
	-Wno-missing-field-initializers \
	-DMOJO_EDK_LEGACY_PROTOCOL \

# We use OS_POSIX since we need to communicate with Chrome.
# We also pass NO_ASHMEM to make base::SharedMemory avoid using it and prefer
# the POSIX versions.
LOCAL_CPPFLAGS := \
	-Wno-sign-promo \
	-Wno-non-virtual-dtor \
	-Wno-ignored-qualifiers \
	-Wno-extra \
	-DOS_POSIX \
	-DNO_ASHMEM \
	-DNO_TCMALLOC \

LOCAL_SHARED_LIBRARIES := libevent liblog libchrome libchrome-crypto

LOCAL_C_INCLUDES := \
	external/gtest/include \

LOCAL_EXPORT_C_INCLUDE_DIRS += \
	external/gtest/include \
	$(LOCAL_PATH) \

include $(BUILD_SHARED_LIBRARY)

# Build Java library.
include $(CLEAR_VARS)

LOCAL_MODULE := android.mojo
LOCAL_MODULE_CLASS := JAVA_LIBRARIES

# We manually add a few classes explicitly rather than using the
# |all-java-files-under| macro because base/ includes some stuff that we don't
# want to compile since it requires a lot of extra gyp-generated files
LOCAL_SRC_FILES := \
	base/android/java/src/org/chromium/base/BuildInfo.java \
	base/android/java/src/org/chromium/base/ContextUtils.java \
	base/android/java/src/org/chromium/base/PackageUtils.java \
	base/android/java/src/org/chromium/base/VisibleForTesting.java \
	$(call all-java-files-under, mojo/android/system/src) \
	$(call all-java-files-under, mojo/public/java/system/src) \
	$(call all-java-files-under, mojo/public/java/bindings/src) \
	$(call all-java-files-under, base/android/java/src/org/chromium/base/annotations) \

# Adds all .mojom files Java sources to compilation.
original_module_class := SHARED_LIBRARIES
original_module := libmojo
include $(LOCAL_PATH)/build_mojom_jar.mk

include $(BUILD_STATIC_JAVA_LIBRARY)
