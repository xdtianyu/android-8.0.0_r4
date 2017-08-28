# VTS Test Plans

A VTS test plan consists of a set of VTS tests. Typically the tests within a
plan are for the same target component or testing the same or similar aspect
(e.g., functionality, performance, robustness, or power). There are three kinds
of plans in VTS:

## 1. Official Plans

Official plans contain only verified tests, and are for all Android developers
and partners.

 * __vts__: For all default VTS tests.
 * __vts-hal__: For all default VTS HAL (hardware abstraction layer) module tests.
 * __vts-kernel__: For all default VTS kernel tests.
 * __vts-library__: For all default VTS library tests.
 * __vts-fuzz__: For all default VTS fuzz tests.
 * __vts-performance__: For all default VTS performance tests.
 * __vts-hal-hidl__: For all default VTS HIDL (Hardware Interface Definition Language)
 HAL tests.
 * __vts-hal-hidl-profiling__: For all default VTS HIDL HAL performance profiling tests.

## 2. Serving Plans

Serving plans contain both verified and experimental tests, and are for Android
partners to use as part of their continuous integration infrastructure. The
name of a serving plan always starts with 'vts-serving-'.

## 3. Other Plans

The following plans are also available for development purposes.

 * __vts-camera-its__: For camera ITS (Image Test Suite) tests ported to VTS.
 * __vts-codelab__: For VTS codelab.
 * __vts-gce__: For VTS tests which can be run on Google Compute Engine (GCE).
 * __vts-host__: For VTS host-driven tests.
 * __vts-presubmit__: For VTS pre-submit time tests.
 * __vts-security__: For VTS security tests.
 * __vts-system__: For VTS system tests.
