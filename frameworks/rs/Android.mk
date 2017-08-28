
LOCAL_PATH:=$(call my-dir)

.PHONY: rs-prebuilts-full
rs-prebuilts-full: \
    bcc_compat \
    llvm-rs-cc \
    libRSSupport \
    libRSSupportIO \
    libRScpp_static \
    libblasV8 \
    libcompiler_rt \
    librsrt_arm.bc \
    librsrt_arm64.bc \
    librsrt_mips.bc \
    librsrt_x86.bc \
    librsrt_x86_64.bc

ifneq ($(HOST_OS),darwin)
rs-prebuilts-full: \
    host_cross_llvm-rs-cc \
    host_cross_bcc_compat
endif

# Not building RenderScript modules in PDK builds, as libmediandk
# is not available in PDK.
ifneq ($(TARGET_BUILD_PDK), true)

rs_base_CFLAGS := -Werror -Wall -Wextra \
	-Wno-unused-parameter -Wno-unused-variable

ifneq ($(OVERRIDE_RS_DRIVER),)
  rs_base_CFLAGS += -DOVERRIDE_RS_DRIVER=$(OVERRIDE_RS_DRIVER)
endif

ifneq ($(DISABLE_RS_64_BIT_DRIVER),)
  rs_base_CFLAGS += -DDISABLE_RS_64_BIT_DRIVER
endif

ifeq ($(RS_FIND_OFFSETS), true)
  rs_base_CFLAGS += -DRS_FIND_OFFSETS
endif

include $(CLEAR_VARS)
LOCAL_MODULE := libRSDriver

LOCAL_SRC_FILES:= \
	driver/rsdAllocation.cpp \
	driver/rsdBcc.cpp \
	driver/rsdCore.cpp \
	driver/rsdElement.cpp \
	driver/rsdFrameBuffer.cpp \
	driver/rsdFrameBufferObj.cpp \
	driver/rsdGL.cpp \
	driver/rsdMesh.cpp \
	driver/rsdMeshObj.cpp \
	driver/rsdProgram.cpp \
	driver/rsdProgramRaster.cpp \
	driver/rsdProgramStore.cpp \
	driver/rsdRuntimeStubs.cpp \
	driver/rsdSampler.cpp \
	driver/rsdScriptGroup.cpp \
	driver/rsdShader.cpp \
	driver/rsdShaderCache.cpp \
	driver/rsdType.cpp \
	driver/rsdVertexArray.cpp


LOCAL_SHARED_LIBRARIES += libRS_internal libRSCpuRef
LOCAL_SHARED_LIBRARIES += liblog libEGL libGLESv1_CM libGLESv2
LOCAL_SHARED_LIBRARIES += libnativewindow

LOCAL_SHARED_LIBRARIES += libbcinfo

LOCAL_C_INCLUDES += frameworks/compile/libbcc/include

LOCAL_CFLAGS += $(rs_base_CFLAGS)

include $(BUILD_SHARED_LIBRARY)

# Build rsg-generator ====================
include $(CLEAR_VARS)

LOCAL_MODULE := rsg-generator

# These symbols are normally defined by BUILD_XXX, but we need to define them
# here so that local-intermediates-dir works.

LOCAL_IS_HOST_MODULE := true
LOCAL_MODULE_CLASS := EXECUTABLES
intermediates := $(local-intermediates-dir)

LOCAL_SRC_FILES:= \
    spec.l \
    rsg_generator.c

LOCAL_CXX_STL := none
LOCAL_SANITIZE := never

include $(BUILD_HOST_EXECUTABLE)

RSG_GENERATOR:=$(LOCAL_BUILT_MODULE)

include $(CLEAR_VARS)
LOCAL_MODULE := libRS_internal

LOCAL_MODULE_CLASS := SHARED_LIBRARIES
generated_sources:= $(local-generated-sources-dir)

# Generate custom headers

GEN := $(addprefix $(generated_sources)/, \
            rsgApiStructs.h \
            rsgApiFuncDecl.h \
        )

$(GEN) : PRIVATE_PATH := $(LOCAL_PATH)
$(GEN) : PRIVATE_CUSTOM_TOOL = cat $(PRIVATE_PATH)/rs.spec $(PRIVATE_PATH)/rsg.spec | $(RSG_GENERATOR) $< $@
$(GEN) : $(RSG_GENERATOR) $(LOCAL_PATH)/rs.spec $(LOCAL_PATH)/rsg.spec
$(GEN): $(generated_sources)/%.h : $(LOCAL_PATH)/%.h.rsg
	$(transform-generated-source)

# used in jni/Android.mk
rs_generated_source += $(GEN)
LOCAL_GENERATED_SOURCES += $(GEN)

# Generate custom source files

GEN := $(addprefix $(generated_sources)/, \
            rsgApi.cpp \
            rsgApiReplay.cpp \
        )

$(GEN) : PRIVATE_PATH := $(LOCAL_PATH)
$(GEN) : PRIVATE_CUSTOM_TOOL = cat $(PRIVATE_PATH)/rs.spec $(PRIVATE_PATH)/rsg.spec | $(RSG_GENERATOR) $< $@
$(GEN) : $(RSG_GENERATOR) $(LOCAL_PATH)/rs.spec $(LOCAL_PATH)/rsg.spec
$(GEN): $(generated_sources)/%.cpp : $(LOCAL_PATH)/%.cpp.rsg
	$(transform-generated-source)

# used in jni/Android.mk
rs_generated_source += $(GEN)

LOCAL_GENERATED_SOURCES += $(GEN)

LOCAL_SRC_FILES:= \
	rsApiAllocation.cpp \
	rsApiContext.cpp \
	rsApiDevice.cpp \
	rsApiElement.cpp \
	rsApiFileA3D.cpp \
	rsApiMesh.cpp \
	rsApiType.cpp \
	rsAllocation.cpp \
	rsAnimation.cpp \
	rsComponent.cpp \
	rsContext.cpp \
	rsClosure.cpp \
	rsCppUtils.cpp \
	rsDevice.cpp \
	rsDriverLoader.cpp \
	rsElement.cpp \
	rsFBOCache.cpp \
	rsFifoSocket.cpp \
	rsFileA3D.cpp \
	rsFont.cpp \
	rsGrallocConsumer.cpp \
	rsObjectBase.cpp \
	rsMatrix2x2.cpp \
	rsMatrix3x3.cpp \
	rsMatrix4x4.cpp \
	rsMesh.cpp \
	rsMutex.cpp \
	rsProgram.cpp \
	rsProgramFragment.cpp \
	rsProgramStore.cpp \
	rsProgramRaster.cpp \
	rsProgramVertex.cpp \
	rsSampler.cpp \
	rsScript.cpp \
	rsScriptC.cpp \
	rsScriptC_Lib.cpp \
	rsScriptC_LibGL.cpp \
	rsScriptGroup.cpp \
	rsScriptGroup2.cpp \
	rsScriptIntrinsic.cpp \
	rsSignal.cpp \
	rsStream.cpp \
	rsThreadIO.cpp \
	rsType.cpp

LOCAL_SHARED_LIBRARIES += liblog libutils libEGL libGLESv1_CM libGLESv2
LOCAL_SHARED_LIBRARIES += libdl libnativewindow
LOCAL_SHARED_LIBRARIES += libft2

LOCAL_SHARED_LIBRARIES += libbcinfo libmediandk

LOCAL_C_INCLUDES += frameworks/av/include/ndk

LOCAL_CFLAGS += $(rs_base_CFLAGS)

# These runtime modules, including libcompiler_rt.so, are required for
# RenderScript.
LOCAL_REQUIRED_MODULES := \
	libclcore.bc \
	libclcore_debug.bc \
	libclcore_g.bc \
	libcompiler_rt

LOCAL_REQUIRED_MODULES_x86 += libclcore_x86.bc
LOCAL_REQUIRED_MODULES_x86_64 += libclcore_x86.bc

ifeq ($(ARCH_ARM_HAVE_NEON),true)
  LOCAL_REQUIRED_MODULES_arm += libclcore_neon.bc
endif

LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libRS

LOCAL_MODULE_CLASS := SHARED_LIBRARIES

LOCAL_SRC_FILES:= \
	rsApiStubs.cpp \
	rsHidlAdaptation.cpp \
	rsFallbackAdaptation.cpp

# Default CPU fallback
LOCAL_REQUIRED_MODULES := libRS_internal libRSDriver

# Treble configuration
LOCAL_SHARED_LIBRARIES += libhidlbase libhidltransport libhwbinder libutils android.hardware.renderscript@1.0

LOCAL_SHARED_LIBRARIES += liblog libcutils libandroid_runtime

LOCAL_STATIC_LIBRARIES := \
        libRSDispatch

LOCAL_CFLAGS += $(rs_base_CFLAGS)

LOCAL_LDFLAGS += -Wl,--version-script,${LOCAL_PATH}/libRS.map

include $(BUILD_SHARED_LIBRARY)

endif # TARGET_BUILD_PDK

include $(call all-makefiles-under,$(LOCAL_PATH))

