#
# Common global compiler configuration
#

# Common Compiler Flags ########################################################

# CHRE requires C++11 and C99 support.
COMMON_CXX_CFLAGS += -std=c++11
COMMON_C_CFLAGS += -std=c99

# Configure warnings.
COMMON_CFLAGS += -Wall
COMMON_CFLAGS += -Wextra
COMMON_CFLAGS += -Wno-unused-parameter
COMMON_CFLAGS += -Wshadow
COMMON_CFLAGS += -Werror

# Disable exceptions and RTTI.
COMMON_CFLAGS += -fno-exceptions
COMMON_CFLAGS += -fno-rtti

# Enable the linker to garbage collect unused code and variables.
COMMON_CFLAGS += -fdata-sections
COMMON_CFLAGS += -ffunction-sections

# Common Archive Flags #########################################################

COMMON_ARFLAGS += rsc
