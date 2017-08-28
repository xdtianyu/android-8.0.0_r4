#
# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

vts_test_bin_packages := \
    android.hardware.tests.msgq@1.0-service-benchmark \
    android.hardware.tests.msgq@1.0-service-test \
    fmq_test \
    hidl_test \
    hidl_test_client \
    hidl_test_helper \
    hidl_test_servers \
    mq_benchmark_client \
    mq_test_client \
    libhwbinder_benchmark \
    libbinder_benchmark \
    vts_codelab_target_binary \
    vts_test_binary_crash_app \
    vts_test_binary_syscall_exists \
    simpleperf_cpu_hotplug_test \
    binderThroughputTest \
    hwbinderThroughputTest \
    bionic-unit-tests \
    bionic-unit-tests-gcc \
    bionic-unit-tests-static \
    stressapptest \
    libcutils_test \
    vts_test_binary_qtaguid_module \

# some CTS packages for record-and-replay test development purpose
vts_test_bin_packages += \
    CtsAccelerationTestCases \
    CtsSensorTestCases \

# Proto fuzzer executable
vts_test_bin_packages += \
    vts_proto_fuzzer \

# VTS Treble VINTF Test
vts_test_bin_packages += \
    vts_treble_vintf_test \

# Netd tests
vts_test_bin_packages += \
    netd_integration_test \

# Tun device tests.
vts_test_bin_packages += \
    vts_kernel_tun_test \

# Binder tests.
vts_test_bin_packages += \
    binderDriverInterfaceTest \
    binderValueTypeTest \
    binderLibTest \
    binderTextOutputTest \
    binderSafeInterfaceTest \

# VTS security PoC tests
vts_test_bin_packages += \
    30149612 \
    28838221 \
    32219453 \
    31707909 \
    32402310

