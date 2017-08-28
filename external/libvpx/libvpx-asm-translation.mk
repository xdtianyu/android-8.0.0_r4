# Rules to generate GAS compatible assembly from RVCT syntax files.
# Input variables:
#   libvpx_2nd_arch
#   libvpx_source_dir
#   libvpx_codec_srcs_asm_<arch>
#
# Output variables:
#   LOCAL_GENERATED_SOURCES_<arch>

ifneq ($(strip $(libvpx_codec_srcs_asm_$(TARGET_$(libvpx_2nd_arch)ARCH))),)
libvpx_intermediates := $(call local-intermediates-dir,,$(libvpx_2nd_arch))
# This step is only required for ARM. MIPS uses intrinsics exclusively and x86
# requires 'yasm' to pre-process its assembly files.
# The ARM assembly sources must be converted from ADS to GAS compatible format.
VPX_ASM := $(addprefix $(libvpx_intermediates)/, $(libvpx_codec_srcs_asm_$(TARGET_$(libvpx_2nd_arch)ARCH)))
# The build system will accept arm assembly which ends in '.s' or '.S'
# Other build systems prefer capital S so smooth things out here.
VPX_GEN := $(addsuffix .S, $(VPX_ASM))
$(VPX_GEN) : PRIVATE_SOURCE_DIR := $(libvpx_source_dir)
$(VPX_GEN) : PRIVATE_CUSTOM_TOOL = cat $< | perl $(PRIVATE_SOURCE_DIR)/build/make/ads2gas.pl > $@
$(VPX_GEN) : $(libvpx_intermediates)/%.S : $(libvpx_source_dir)/% \
		$(libvpx_source_dir)/build/make/ads2gas.pl \
		$(libvpx_source_dir)/build/make/thumb.pm
	$(transform-generated-source)

LOCAL_GENERATED_SOURCES_$(TARGET_$(libvpx_2nd_arch)ARCH) += $(VPX_GEN)
endif
