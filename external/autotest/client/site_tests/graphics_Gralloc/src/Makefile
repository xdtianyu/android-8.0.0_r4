# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

GRALLOCTEST = gralloctest
SOURCES += gralloctest.c

OBJS = $(SOURCES:.c=.o)
DEPS = $(SOURCES:.c=.d)

CCFLAGS += -g -O2 -Wall -fPIE
LIBS    += -lhardware -lsync -lcutils -pie

.PHONY: all clean

all: $(GRALLOCTEST)

$(GRALLOCTEST): $(OBJS)

clean:
	$(RM) $(GRALLOCTEST)
	$(RM) $(OBJS) $(DEPS)
	$(RM) *.o *.d .version

$(GRALLOCTEST):
	$(CC) $(CCFLAGS) $(LDFLAGS) $^ -o $@ $(LIBS)

$(OBJS): %.o: %.c
	$(CC) $(CCFLAGS) -c $^ -o $@ -MMD

-include $(DEPS)
