#
# Google Reference CHRE Implementation for Linux on x86
#

include $(CHRE_PREFIX)/build/clean_build_template_args.mk

TARGET_NAME = google_x86_linux
TARGET_CFLAGS = -DCHRE_MESSAGE_TO_HOST_MAX_SIZE=2048
TARGET_CFLAGS += $(GOOGLE_X86_LINUX_CFLAGS)
TARGET_VARIANT_SRCS = $(GOOGLE_X86_LINUX_SRCS)

ifneq ($(filter $(TARGET_NAME)% all, $(MAKECMDGOALS)),)
ifneq ($(IS_NANOAPP_BUILD),)
include $(CHRE_PREFIX)/build/nanoapp/google_linux.mk
else
# Instruct the build to link a final executable.
TARGET_BUILD_BIN = true

# Link in libraries for the final executable.
TARGET_BIN_LDFLAGS += -lrt
endif

include $(CHRE_PREFIX)/build/arch/x86.mk
include $(CHRE_PREFIX)/build/build_template.mk
endif
