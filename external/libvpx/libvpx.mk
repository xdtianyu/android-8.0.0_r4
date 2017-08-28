LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# Clang arm assembler cannot compile libvpx .s files yet.
LOCAL_CLANG_ASFLAGS_arm += -no-integrated-as

libvpx_source_dir := $(LOCAL_PATH)/libvpx

## Arch-common settings
LOCAL_MODULE := libvpx
LOCAL_MODULE_CLASS := STATIC_LIBRARIES

LOCAL_CFLAGS := -DHAVE_CONFIG_H=vpx_config.h

# Want arm, not thumb, optimized
LOCAL_ARM_MODE := arm
LOCAL_CFLAGS += -O3

LOCAL_CFLAGS += -Wno-unused-parameter

LOCAL_C_INCLUDES := $(libvpx_source_dir)

LOCAL_SANITIZE := cfi
LOCAL_SANITIZE_DIAG := cfi

# Load the arch-specific settings
include $(LOCAL_PATH)/config.$(TARGET_ARCH).mk
LOCAL_SRC_FILES_$(TARGET_ARCH) := $(libvpx_codec_srcs_c_$(TARGET_ARCH))
LOCAL_C_INCLUDES_$(TARGET_ARCH) := $(libvpx_config_dir_$(TARGET_ARCH))
libvpx_2nd_arch :=
include $(LOCAL_PATH)/libvpx-asm-translation.mk

ifdef TARGET_2ND_ARCH
include $(LOCAL_PATH)/config.$(TARGET_2ND_ARCH).mk
LOCAL_SRC_FILES_$(TARGET_2ND_ARCH) := $(libvpx_codec_srcs_c_$(TARGET_2ND_ARCH))
LOCAL_C_INCLUDES_$(TARGET_2ND_ARCH) := $(libvpx_config_dir_$(TARGET_2ND_ARCH))
libvpx_2nd_arch := $(TARGET_2ND_ARCH_VAR_PREFIX)
include $(LOCAL_PATH)/libvpx-asm-translation.mk
libvpx_2nd_arch :=
endif

libvpx_target :=
libvpx_source_dir :=
libvpx_intermediates :=
libvpx_asm_offsets_intermediates :=
libvpx_asm_offsets_files :=

include $(BUILD_STATIC_LIBRARY)
