LOCAL_PATH:=$(call my-dir)

# Not building RenderScript modules in PDK builds, as libmediandk
# is not available in PDK.
ifneq ($(TARGET_BUILD_PDK), true)

rs_base_CFLAGS := -Werror -Wall -Wextra \
				  -Wno-unused-parameter -Wno-unused-variable

ifneq ($(OVERRIDE_RS_DRIVER),)
  rs_base_CFLAGS += -DOVERRIDE_RS_DRIVER=$(OVERRIDE_RS_DRIVER)
endif

ifeq ($(BUILD_ARM_FOR_X86),true)
  rs_base_CFLAGS += -DBUILD_ARM_FOR_X86
endif

include $(CLEAR_VARS)
ifneq ($(HOST_OS),windows)
endif
LOCAL_MODULE := libRSCpuRef
LOCAL_MODULE_TARGET_ARCH := arm mips mips64 x86 x86_64 arm64

LOCAL_SRC_FILES:= \
        rsCpuCore.cpp \
        rsCpuExecutable.cpp \
        rsCpuScript.cpp \
        rsCpuRuntimeMath.cpp \
        rsCpuScriptGroup.cpp \
        rsCpuScriptGroup2.cpp \
        rsCpuIntrinsic.cpp \
        rsCpuIntrinsic3DLUT.cpp \
        rsCpuIntrinsicBLAS.cpp \
        rsCpuIntrinsicBlend.cpp \
        rsCpuIntrinsicBlur.cpp \
        rsCpuIntrinsicColorMatrix.cpp \
        rsCpuIntrinsicConvolve3x3.cpp \
        rsCpuIntrinsicConvolve5x5.cpp \
        rsCpuIntrinsicHistogram.cpp \
        rsCpuIntrinsicResize.cpp \
        rsCpuIntrinsicLUT.cpp \
        rsCpuIntrinsicYuvToRGB.cpp

LOCAL_CFLAGS_arm64 += -DARCH_ARM_USE_INTRINSICS -DARCH_ARM64_USE_INTRINSICS -DARCH_ARM64_HAVE_NEON

ifeq ($(RS_DISABLE_A53_WORKAROUND),true)
LOCAL_CFLAGS_arm64 += -DDISABLE_A53_WORKAROUND
endif

LOCAL_SRC_FILES_arm64 += \
    rsCpuIntrinsics_advsimd_3DLUT.S \
    rsCpuIntrinsics_advsimd_Convolve.S \
    rsCpuIntrinsics_advsimd_Blur.S \
    rsCpuIntrinsics_advsimd_ColorMatrix.S \
    rsCpuIntrinsics_advsimd_Resize.S \
    rsCpuIntrinsics_advsimd_YuvToRGB.S \
    rsCpuIntrinsics_advsimd_Blend.S

ifeq ($(ARCH_ARM_HAVE_NEON),true)
    LOCAL_CFLAGS_arm += -DARCH_ARM_HAVE_NEON
endif

ifeq ($(ARCH_ARM_HAVE_VFP),true)
    LOCAL_CFLAGS_arm += -DARCH_ARM_HAVE_VFP -DARCH_ARM_USE_INTRINSICS
    LOCAL_SRC_FILES_arm += \
    rsCpuIntrinsics_neon_3DLUT.S \
    rsCpuIntrinsics_neon_Blend.S \
    rsCpuIntrinsics_neon_Blur.S \
    rsCpuIntrinsics_neon_Convolve.S \
    rsCpuIntrinsics_neon_ColorMatrix.S \
    rsCpuIntrinsics_neon_Resize.S \
    rsCpuIntrinsics_neon_YuvToRGB.S \

    LOCAL_ASFLAGS_arm := -mfpu=neon
endif

ifeq ($(ARCH_X86_HAVE_SSSE3),true)
    LOCAL_CFLAGS_x86 += -DARCH_X86_HAVE_SSSE3
    LOCAL_SRC_FILES_x86 += \
    rsCpuIntrinsics_x86.cpp
    LOCAL_CFLAGS_x86_64 += -DARCH_X86_HAVE_SSSE3
    LOCAL_SRC_FILES_x86_64 += \
    rsCpuIntrinsics_x86.cpp
endif

LOCAL_SHARED_LIBRARIES += libRS_internal libc++ liblog libz
LOCAL_SHARED_LIBRARIES += libbcinfo libblas
LOCAL_STATIC_LIBRARIES := libbnnmlowp

LOCAL_C_INCLUDES += frameworks/compile/libbcc/include
LOCAL_C_INCLUDES += frameworks/rs
LOCAL_C_INCLUDES += external/cblas/include
LOCAL_C_INCLUDES += external/gemmlowp/eight_bit_int_gemm
LOCAL_C_INCLUDES += external/zlib

include frameworks/compile/libbcc/libbcc-targets.mk

LOCAL_CFLAGS += $(rs_base_CFLAGS)

include $(BUILD_SHARED_LIBRARY)

endif # TARGET_BUILD_PDK
