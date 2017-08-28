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
#
"""This module has the global key values that are used across framework
modules.
"""


class ConfigKeys(object):
    """Enum values for test config related lookups.
    """
    # Keys used to look up values from test config files.
    # These keys define the wording of test configs and their internal
    # references.
    KEY_LOG_PATH = "log_path"
    KEY_TESTBED = "test_bed"
    KEY_TESTBED_NAME = "name"
    KEY_TEST_PATHS = "test_paths"
    KEY_TEST_SUITE = "test_suite"

    # Keys in test suite
    KEY_INCLUDE_FILTER = "include_filter"
    KEY_EXCLUDE_FILTER = "exclude_filter"

    # Keys for binary tests
    IKEY_BINARY_TEST_SOURCE = "binary_test_source"
    IKEY_BINARY_TEST_WORKING_DIRECTORY = "binary_test_working_directory"
    IKEY_BINARY_TEST_ENVP = "binary_test_envp"
    IKEY_BINARY_TEST_ARGS = "binary_test_args"
    IKEY_BINARY_TEST_LD_LIBRARY_PATH = "binary_test_ld_library_path"
    IKEY_BINARY_TEST_DISABLE_FRAMEWORK = "binary_test_disable_framework"
    IKEY_BINARY_TEST_STOP_NATIVE_SERVERS = "binary_test_stop_native_servers"
    IKEY_NATIVE_SERVER_PROCESS_NAME = "native_server_process_name"

    # Internal keys, used internally, not exposed to user's config files.
    IKEY_USER_PARAM = "user_params"
    IKEY_TESTBED_NAME = "testbed_name"
    IKEY_LOG_PATH = "log_path"
    IKEY_ABI_NAME = "abi_name"
    IKEY_ABI_BITNESS = "abi_bitness"
    IKEY_RUN_32BIT_ON_64BIT_ABI = "run_32bit_on_64bit_abi"
    IKEY_SKIP_ON_32BIT_ABI = "skip_on_32bit_abi"
    IKEY_SKIP_ON_64BIT_ABI = "skip_on_64bit_abi"
    IKEY_SKIP_IF_THERMAL_THROTTLING = "skip_if_thermal_throttling"

    IKEY_BUILD = "build"
    IKEY_DATA_FILE_PATH = "data_file_path"

    # sub fields of test_bed
    IKEY_ANDROID_DEVICE = "AndroidDevice"
    IKEY_PRODUCT_TYPE = "product_type"
    IKEY_PRODUCT_VARIANT = "product_variant"
    IKEY_BUILD_FLAVOR = "build_flavor"
    IKEY_BUILD_ID = "build_id"
    IKEY_BRANCH = "branch"
    IKEY_BUILD_ALIAS = "build_alias"
    IKEY_API_LEVEL = "api_level"
    IKEY_SERIAL = "serial"

    # Keys for web
    IKEY_ENABLE_WEB = "enable_web"

    # Keys for profiling
    IKEY_ENABLE_PROFILING = "enable_profiling"
    IKEY_BINARY_TEST_PROFILING_LIBRARY_PATH = "binary_test_profiling_library_path"
    IKEY_PROFILING_TRACING_PATH = "profiling_trace_path"
    IKEY_TRACE_FILE_TOOL_NAME = "trace_file_tool_name"

    # Keys for systrace (for hal tests)
    IKEY_ENABLE_SYSTRACE = "enable_systrace"
    IKEY_SYSTRACE_PROCESS_NAME = "systrace_process_name"
    IKEY_SYSTRACE_REPORT_PATH = "systrace_report_path"
    IKEY_SYSTRACE_REPORT_URL_PREFIX = "systrace_report_url_prefix"
    IKEY_SYSTRACE_REPORT_USE_DATE_DIRECTORY = "systrace_report_path_use_date_directory"
    IKEY_SYSTRACE_UPLAD_TO_DASHBOARD = "systrace_upload_to_dashboard"

    # Keys for coverage
    IKEY_ENABLE_COVERAGE = "enable_coverage"
    IKEY_MODULES = "modules"
    IKEY_SERVICE_JSON_PATH = "service_key_json_path"
    IKEY_DASHBOARD_POST_COMMAND = "dashboard_post_command"

    # Keys for the HAL HIDL GTest type (see VtsMultiDeviceTest.java).
    IKEY_PRECONDITION_HWBINDER_SERVICE = "precondition_hwbinder_service"
    IKEY_PRECONDITION_FEATURE = "precondition_feature"
    IKEY_PRECONDITION_FILE_PATH_PREFIX = "precondition_file_path_prefix"
    IKEY_PRECONDITION_LSHAL = "precondition_lshal"

    # Keys for toggle passthrough mode
    IKEY_PASSTHROUGH_MODE = "passthrough_mode"

    # Keys for the HAL HIDL Replay Test type.
    IKEY_HAL_HIDL_REPLAY_TEST_TRACE_PATHS = "hal_hidl_replay_test_trace_paths"
    IKEY_HAL_HIDL_PACKAGE_NAME = "hal_hidl_package_name"

    # Keys for special test cases
    IKEY_FFMPEG_BINARY_PATH = "ffmpeg_binary_path"

    # Keys for log uploading
    IKEY_ENABLE_LOG_UPLOADING = "enable_log_uploading"
    IKEY_LOG_UPLOADING_PATH = "log_uploading_path"
    IKEY_LOG_UPLOADING_USE_DATE_DIRECTORY = "log_uploading_use_date_directory"
    IKEY_LOG_UPLOADING_URL_PREFIX = "log_uploading_url_prefix"

    # A list of keys whose values in configs should not be passed to test
    # classes without unpacking first.
    RESERVED_KEYS = (KEY_TESTBED, KEY_LOG_PATH, KEY_TEST_PATHS)
