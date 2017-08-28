#
# Nanoapp Build Rules for Google Generic CHRE on SLPI
#

################################################################################
#
# Google Generic CHRE on SLPI  Nanoapp Build Template
#
# Invoke this to instantiate a set of Nanoapp post processing build targets.
#
# TARGET_NAME_nanoapp - The resulting nanoapp output.
#
# Argument List:
#     $1 - TARGET_NAME         - The name of the target being built.
#
################################################################################

TARGET_CFLAGS += -DNANOAPP_ID=$(NANOAPP_ID)
TARGET_CFLAGS += -DNANOAPP_VERSION=$(NANOAPP_VERSION)
TARGET_CFLAGS += -DNANOAPP_VENDOR_STRING=$(NANOAPP_VENDOR_STRING)
TARGET_CFLAGS += -DNANOAPP_NAME_STRING=$(NANOAPP_NAME_STRING)
TARGET_CFLAGS += -DNANOAPP_IS_SYSTEM_NANOAPP=$(NANOAPP_IS_SYSTEM_NANOAPP)
TARGET_CFLAGS += -I$(CHRE_PREFIX)/platform/shared/include
TARGET_CFLAGS += -I$(CHRE_PREFIX)/util/include

ifndef GOOGLE_SLPI_NANOAPP_BUILD_TEMPLATE
define GOOGLE_SLPI_NANOAPP_BUILD_TEMPLATE

# TODO: Invoke signing/formatting post-processing tools. This simply adds the
# underlying shared object and archive to the nanoapp target.

.PHONY: $(1)_nanoapp
all: $(1)_nanoapp

$(1)_nanoapp: $(1)

endef
endif

# Template Invocation ##########################################################

$(eval $(call GOOGLE_SLPI_NANOAPP_BUILD_TEMPLATE, $(TARGET_NAME)))
