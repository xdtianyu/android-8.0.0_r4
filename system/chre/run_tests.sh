#!/bin/bash

# Quit if any command produces an error.
set -e

# Build and run the CHRE unit test binary.
make google_x86_googletest -j
./out/google_x86_googletest/libchre $1
