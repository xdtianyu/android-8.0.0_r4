#
# Nanohub CHRE Implementation for Cortex-M4 Architecture
#

include $(CHRE_PREFIX)/build/clean_build_template_args.mk

TARGET_NAME = google_cm4_nanohub
TARGET_CFLAGS = -DCHRE_MESSAGE_TO_HOST_MAX_SIZE=128
TARGET_CFLAGS += $(GOOGLE_CM4_NANOHUB_CFLAGS)
TARGET_VARIANT_SRCS = $(GOOGLE_CM4_NANOHUB_SRCS)
CORTEXM_ARCH = m4

ifneq ($(filter $(TARGET_NAME)% all, $(MAKECMDGOALS)),)
include $(CHRE_PREFIX)/build/arch/cortexm.mk
include $(CHRE_PREFIX)/build/build_template.mk

ifneq ($(IS_NANOAPP_BUILD),)
include $(CHRE_PREFIX)/build/nanoapp/google_nanohub.mk
endif
endif
