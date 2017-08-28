#
# External Projects Makefile
#

#
# GoogleTest/GoogleMock
#

GOOGLETEST_PATH = $(GOOGLETEST_PREFIX)/googletest
GOOGLEMOCK_PATH = $(GOOGLETEST_PREFIX)/googlemock

# Common Compiler Flags ########################################################

# Include paths.
GOOGLE_X86_GOOGLETEST_CFLAGS += -I$(GOOGLETEST_PATH)
GOOGLE_X86_GOOGLETEST_CFLAGS += -I$(GOOGLETEST_PATH)/include
GOOGLE_X86_GOOGLETEST_CFLAGS += -I$(GOOGLEMOCK_PATH)
GOOGLE_X86_GOOGLETEST_CFLAGS += -I$(GOOGLEMOCK_PATH)/include

# x86 GoogleTest Source Files ##################################################

GOOGLE_X86_GOOGLETEST_SRCS += $(GOOGLETEST_PATH)/src/gtest-all.cc
GOOGLE_X86_GOOGLETEST_SRCS += $(GOOGLETEST_PATH)/src/gtest_main.cc

# x86 GoogleMock Source Files ##################################################

GOOGLE_X86_GOOGLETEST_SRCS += $(GOOGLEMOCK_PATH)/src/gmock-all.cc
