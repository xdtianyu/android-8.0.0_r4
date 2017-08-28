#
# Nanoapp Makefile
#
# Include this file in your nanoapp Makefile to produce binary nanoapps to
# target a variety of architectures.
#

# Nanoapp Build Configuration Checks ###########################################

ifeq ($(NANOAPP_NAME),)
$(error "The NANOAPP_NAME variable must be set to the name of the nanoapp. \
         This should be assigned by the Makefile that includes app.mk.")
endif

ifeq ($(NANOAPP_ID),)
$(error "The NANOAPP_ID variable must be set to the ID of the nanoapp. \
         This should be assigned by the Makefile that includes app.mk.")
endif

ifeq ($(NANOAPP_VERSION),)
$(error "The NANOAPP_VERSION variable must be set to the version of the nanoapp. \
         This should be assigned by the Makefile that includes app.mk.")
endif

ifeq ($(NANOAPP_NAME_STRING),)
$(error The NANOAPP_NAME_STRING variable must be set to the friendly name of \
        the nanoapp. This should be assigned by the Makefile that includes \
        app.mk.)
endif

ifeq ($(NANOAPP_VENDOR_STRING),)
$(info NANOAPP_VENDOR_STRING not supplied, defaulting to "Google".)
NANOAPP_VENDOR_STRING = \"Google\"
endif

ifeq ($(NANOAPP_IS_SYSTEM_NANOAPP),)
$(info NANOAPP_IS_SYSTEM_NANOAPP not supplied, defaulting to 0.)
NANOAPP_IS_SYSTEM_NANOAPP = 0
endif

# Nanoapp Build ################################################################

# This variable indicates to the variants that some post-processing may be
# required as the target is a nanoapp.
IS_NANOAPP_BUILD = true

# Common App Build Configuration ###############################################

OUTPUT_NAME = $(NANOAPP_NAME)

# Common Compiler Flags ########################################################

# Add the CHRE API to the include search path.
COMMON_CFLAGS += -I$(CHRE_PREFIX)/chre_api/include/chre_api

# Variant-specific Nanoapp Support Source Files ################################

APP_SUPPORT_PATH = $(CHRE_PREFIX)/build/app_support
DSO_SUPPORT_LIB_PATH = $(CHRE_PREFIX)/platform/shared/nanoapp

GOOGLE_HEXAGONV60_SLPI_SRCS += $(DSO_SUPPORT_LIB_PATH)/nanoapp_support_lib_dso.c
GOOGLE_HEXAGONV62_SLPI_SRCS += $(DSO_SUPPORT_LIB_PATH)/nanoapp_support_lib_dso.c
QCOM_HEXAGONV60_NANOHUB_SRCS += $(APP_SUPPORT_PATH)/qcom_nanohub/app_support.cc

# Makefile Includes ############################################################

# Common includes
include $(CHRE_PREFIX)/build/common.mk

# Supported variants includes
include $(CHRE_PREFIX)/build/variant/google_cm4_nanohub.mk
include $(CHRE_PREFIX)/build/variant/google_hexagonv60_slpi.mk
include $(CHRE_PREFIX)/build/variant/google_hexagonv62_slpi.mk
include $(CHRE_PREFIX)/build/variant/qcom_hexagonv60_nanohub.mk
