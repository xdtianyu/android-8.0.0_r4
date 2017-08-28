# Output variables:
# libvpx_config_dir_mips64
# libvpx_codec_srcs_c_mips64

ifneq ($(ARCH_HAS_BIGENDIAN),true)
  ifeq ($(ARCH_MIPS_HAS_MSA),true)
    libvpx_target := config/mips64-msa
  else
    libvpx_target := config/mips64
  endif
else
  libvpx_target := config/generic
endif

libvpx_config_dir_mips64 := $(LOCAL_PATH)/$(libvpx_target)
libvpx_codec_srcs := $(sort $(shell cat $(libvpx_config_dir_mips64)/libvpx_srcs.txt))

# vpx_config.c is an auto-generated file in $(libvpx_target).
libvpx_codec_srcs_c_mips64 := $(addprefix libvpx/, $(filter-out vpx_config.c, \
    $(filter %.c, $(libvpx_codec_srcs)))) \
    $(libvpx_target)/vpx_config.c
