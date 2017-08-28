# Copyright 2016 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
#
# Defines a docker image that can build fuzzers.
#
FROM ossfuzz/base-libfuzzer
RUN apt-get install -y make libssl-dev binutils libgcc-5-dev

COPY . /src/tpm2/
COPY fuzz/build.sh /src/
