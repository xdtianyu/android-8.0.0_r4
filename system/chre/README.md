# Context Hub Runtime Environment (CHRE)

## Build Instructions

Build targets are arranged in the form of a variant triple consisting of:

``vendor_arch_variant``

The vendor is the provider of the CHRE implementation (ex: google, qcom). The
arch is the CPU architecture (ie: hexagonv60, x86, cm4). The variant is the
target platform (ie: slpi, nanohub, linux, googletest).

### Linux

CHRE is compatible with Linux as a simulator.

#### Linux Build/Run

The build target for x86 linux is ``google_x86_linux``. You can build/run the
simulator with the following command:

    ./run_sim.sh

#### Linux Unit Tests

You can run all unit tests with the following command. Pass arguments to this
script and they are passed to the gtest framework. (example:
``--gtest_filter=DynamicVector.*``)

    ./run_tests.sh

### SLPI Hexagon

First, setup paths to the Hexagon Tools (v8.x.x), SDK (v3.0), and SLPI source
tree, for example:

    export HEXAGON_TOOLS_PREFIX=~/Qualcomm/HEXAGON_Tools/8.0
    export HEXAGON_SDK_PREFIX=~/Qualcomm/Hexagon_SDK/3.0
    export SLPI_PREFIX=~/Qualcomm/msm8998/slpi_proc

Then use the provided Makefiles to build:

    make google_hexagonv62_slpi -j

## Directory Structure

The CHRE project is organized as follows:

- ``chre_api``
    - The stable API exposed to nanoapps
- ``core``
    - Common code that applies to all CHRE platforms, most notably event
      management.
- ``pal``
    - An abstraction layer that implementers must supply to access
      device-specific functionality (such as GPS and Wi-Fi). The PAL is a C API
      which allows it to be implemented using a vendor-supplied library.
- ``platform``
    - Contains the system interface that all plaforms must implement, along with
      implementations for individual platforms. This includes the implementation
      of the CHRE API.
    - ``platform/shared``
        - Contains code that will apply to multiple platforms, but not
          necessarily all.
    - ``platform/linux``
        - This directory contains the canonical example for running CHRE on
          desktop machines, primarily for simulation and testing.
- ``apps``
    - A small number of sample applications are provided. These are intended to
      guide developers of new applications and help implementers test basic
      functionality quickly.
    - This is reference code and is not required for the CHRE to function.
- ``util``
    - Contains data structures used throughout CHRE and common utility code.

Within each of these directories, you may find a ``tests`` subdirectory
containing tests written against the googletest framework.

## Supplied Nanoapps

This project includes a number of nanoapps that serve as both examples of how to
use CHRE, debugging tools and can perform some useful function.

All nanoapps in the ``apps`` directory are placed in a namespace when built
statically with this CHRE implementation. When compiled as standalone nanoapps,
there is no outer namespace on their entry points. This allows testing various
CHRE subsystems without requiring dynamic loading and allows these nanoapps to
coexist within a CHRE binary. Refer to ``apps/hello_world/hello_world.cc`` for
a minimal example.

### FeatureWorld

Any of the nanoapps that end with the term World are intended to test some
feature of the system. The HelloWorld nanoapp simply exercises logging
functionality, TimerWorld exercises timers and WifiWorld uses wifi, for example.
These nanoapps log all results via chreLog which makes them effective tools when
bringing up a new CHRE implementation.

### ImuCal

This nanoapp implements IMU calibration.

## Porting CHRE

This codebase is intended to be ported to a variety of operating systems. If you
wish to port CHRE to a new OS, refer to the ``platform`` directory. An example of
the Linux port is provided under ``platform/linux``.

There are notes regarding initialization under
``platform/include/chre/platform/init.h`` that will also be helpful.

## Coding conventions

There are many well-established coding standards within Google. The official
C++ style guide is used with the exception of Android naming conventions for
methods and variables. This means 2 space indents, camelCase method names, an
mPrefix on class members and so on. Style rules that are not specified in the
Android style guide are inherited from Google.

* [Google C++ Style][1]

[1]: https://google.github.io/styleguide/cppguide.html

### Use of C++

This project uses C++11, but with two main caveats:

 1. General considerations for using C++ in an embedded environment apply. This
    means avoiding language features that can impose runtime overhead should
    be avoided, due to the relative scarcity of memory and CPU resources, and
    power considerations. Examples include RTTI, exceptions, overuse of dynamic
    memory allocation, etc. Refer to existing literature on this topic
    including this [Technical Report on C++ Performance][1] and so on.
 2. Support of C++ standard libraries are not generally expected to be
    extensive or widespread in the embedded environments where this code will
    run. That means that things like <thread> and <mutex> should not be used,
    in favor of simple platform abstractions that can be implemented directly
    with less effort (potentially using those libraries if they are known to be
    available).

[1]: http://www.open-std.org/jtc1/sc22/wg21/docs/TR18015.pdf
