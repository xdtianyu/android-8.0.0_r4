#!/bin/bash

# Quit if any command produces an error.
set -e

# Build and run the CHRE simulator.
make google_x86_linux -j
./out/google_x86_linux/libchre
