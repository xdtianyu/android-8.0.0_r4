#
# Platform Makefile
#

include $(CHRE_PREFIX)/external/flatbuffers/flatbuffers.mk

# Common Compiler Flags ########################################################

# Include paths.
COMMON_CFLAGS += -Iplatform/include

# Common Compiler Flags ########################################################

# Hexagon-specific Compiler Flags ##############################################

# Include paths.
HEXAGON_CFLAGS += -Iplatform/shared/include
HEXAGON_CFLAGS += -Iplatform/slpi/include

# We use FlatBuffers in the Hexagon (SLPI) platform layer
HEXAGON_CFLAGS += $(FLATBUFFERS_CFLAGS)

# Hexagon-specific Source Files ################################################

HEXAGON_SRCS += platform/shared/chre_api_core.cc
HEXAGON_SRCS += platform/shared/chre_api_gnss.cc
HEXAGON_SRCS += platform/shared/chre_api_re.cc
HEXAGON_SRCS += platform/shared/chre_api_sensor.cc
HEXAGON_SRCS += platform/shared/chre_api_version.cc
HEXAGON_SRCS += platform/shared/chre_api_wifi.cc
HEXAGON_SRCS += platform/shared/chre_api_wwan.cc
HEXAGON_SRCS += platform/shared/host_protocol_chre.cc
HEXAGON_SRCS += platform/shared/host_protocol_common.cc
HEXAGON_SRCS += platform/shared/memory.cc
HEXAGON_SRCS += platform/shared/pal_system_api.cc
HEXAGON_SRCS += platform/shared/platform_gnss.cc
HEXAGON_SRCS += platform/shared/platform_sensor.cc
HEXAGON_SRCS += platform/shared/platform_wifi.cc
HEXAGON_SRCS += platform/shared/platform_wwan.cc
HEXAGON_SRCS += platform/shared/static_nanoapps.cc
HEXAGON_SRCS += platform/shared/system_time.cc
HEXAGON_SRCS += platform/slpi/host_link.cc
HEXAGON_SRCS += platform/slpi/init.cc
HEXAGON_SRCS += platform/slpi/platform_nanoapp.cc
HEXAGON_SRCS += platform/slpi/platform_sensor.cc
HEXAGON_SRCS += platform/slpi/platform_sensor_util.cc
HEXAGON_SRCS += platform/slpi/static_nanoapps.cc
HEXAGON_SRCS += platform/slpi/system_time.cc
HEXAGON_SRCS += platform/slpi/system_timer.cc

# x86-specific Compiler Flags ##################################################

X86_CFLAGS += -Iplatform/shared/include
X86_CFLAGS += -Iplatform/linux/include

# x86-specific Source Files ####################################################

X86_SRCS += platform/linux/event_loop.cc
X86_SRCS += platform/linux/host_link.cc
X86_SRCS += platform/linux/static_nanoapps.cc
X86_SRCS += platform/linux/system_time.cc
X86_SRCS += platform/linux/system_timer.cc
X86_SRCS += platform/linux/platform_nanoapp.cc
X86_SRCS += platform/linux/platform_sensor.cc
X86_SRCS += platform/shared/chre_api_core.cc
X86_SRCS += platform/shared/chre_api_gnss.cc
X86_SRCS += platform/shared/chre_api_re.cc
X86_SRCS += platform/shared/chre_api_sensor.cc
X86_SRCS += platform/shared/chre_api_version.cc
X86_SRCS += platform/shared/chre_api_wifi.cc
X86_SRCS += platform/shared/chre_api_wwan.cc
X86_SRCS += platform/shared/memory.cc
X86_SRCS += platform/shared/pal_gnss_stub.cc
X86_SRCS += platform/shared/pal_wifi_stub.cc
X86_SRCS += platform/shared/pal_wwan_stub.cc
X86_SRCS += platform/shared/pal_system_api.cc
X86_SRCS += platform/shared/platform_gnss.cc
X86_SRCS += platform/shared/platform_sensor.cc
X86_SRCS += platform/shared/platform_wifi.cc
X86_SRCS += platform/shared/platform_wwan.cc
X86_SRCS += platform/shared/static_nanoapps.cc
X86_SRCS += platform/shared/system_time.cc

GOOGLE_X86_LINUX_SRCS += platform/linux/init.cc

# GoogleTest Compiler Flags ####################################################

GOOGLETEST_CFLAGS += -Iplatform/slpi/include

# GoogleTest Source Files ######################################################

GOOGLETEST_SRCS += platform/linux/assert.cc
GOOGLETEST_SRCS += platform/slpi/platform_sensor_util.cc
GOOGLETEST_SRCS += platform/slpi/tests/platform_sensor_util_test.cc
