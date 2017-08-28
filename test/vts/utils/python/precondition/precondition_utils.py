#
# Copyright (C) 2017 The Android Open Source Project
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

import logging

from vts.runners.host import const
from vts.runners.host import keys
from vts.utils.python.common import vintf_utils


def CanRunHidlHalTest(test_instance, dut, shell=None):
    """Checks HAL precondition of a test instance.

    Args:
        test_instance: the test instance which inherits BaseTestClass.
        dut: the AndroidDevice under test.
        shell: the ShellMirrorObject to execute command on the device.
               If not specified, the function creates one from dut.

    Returns:
        True if the precondition is satisfied; False otherwise.
    """
    if shell is None:
        dut.shell.InvokeTerminal("check_hal_preconditions")
        shell = dut.shell.check_hal_preconditions

    opt_params = [
        keys.ConfigKeys.IKEY_ABI_BITNESS,
        keys.ConfigKeys.IKEY_PRECONDITION_HWBINDER_SERVICE,
        keys.ConfigKeys.IKEY_PRECONDITION_FEATURE,
        keys.ConfigKeys.IKEY_PRECONDITION_FILE_PATH_PREFIX,
        keys.ConfigKeys.IKEY_PRECONDITION_LSHAL,
    ]
    test_instance.getUserParams(opt_param_names=opt_params)

    hwbinder_service_name = str(getattr(test_instance,
        keys.ConfigKeys.IKEY_PRECONDITION_HWBINDER_SERVICE, ""))
    if hwbinder_service_name:
        if not hwbinder_service_name.startswith("android.hardware."):
            logging.error("The given hwbinder service name %s is invalid.",
                          hwbinder_service_name)
        else:
            cmd_results = shell.Execute("ps -A")
            hwbinder_service_name += "@"
            if (any(cmd_results[const.EXIT_CODE]) or
                hwbinder_service_name not in cmd_results[const.STDOUT][0]):
                logging.warn("The required hwbinder service %s not found.",
                             hwbinder_service_name)
                return False

    feature = str(getattr(test_instance,
        keys.ConfigKeys.IKEY_PRECONDITION_FEATURE, ""))
    if feature:
        if not feature.startswith("android.hardware."):
            logging.error(
                "The given feature name %s is invalid for HIDL HAL.",
                feature)
        else:
            cmd_results = shell.Execute("pm list features")
            if (any(cmd_results[const.EXIT_CODE]) or
                feature not in cmd_results[const.STDOUT][0]):
                logging.warn("The required feature %s not found.",
                             feature)
                return False

    file_path_prefix = str(getattr(test_instance,
        keys.ConfigKeys.IKEY_PRECONDITION_FILE_PATH_PREFIX, ""))
    if file_path_prefix:
        cmd_results = shell.Execute("ls %s*" % file_path_prefix)
        if any(cmd_results[const.EXIT_CODE]):
            logging.warn("The required file (prefix: %s) not found.",
                         file_path_prefix)
            return False

    hal = str(getattr(test_instance,
        keys.ConfigKeys.IKEY_PRECONDITION_LSHAL, ""))
    if hal:
        vintf_xml = dut.getVintfXml()
        if vintf_xml:
            hwbinder_hals, passthrough_hals = vintf_utils.GetHalDescriptions(
                vintf_xml)
            if not hwbinder_hals or not passthrough_hals:
                logging.error("can't check precondition due to a "
                          "lshal output format error.")
            elif (hal not in hwbinder_hals and
                  hal not in passthrough_hals):
                logging.warn(
                    "The required hal %s not found by lshal.",
                    hal)
                return False
            elif (hal not in hwbinder_hals and
                  hal in passthrough_hals):
                if hasattr(test_instance, keys.ConfigKeys.IKEY_ABI_BITNESS):
                    bitness = getattr(test_instance,
                                      keys.ConfigKeys.IKEY_ABI_BITNESS)
                    if (bitness not in
                        passthrough_hals[hal].hal_archs):
                        logging.warn(
                            "The required feature %s found as a "
                            "passthrough hal but the client bitness %s "
                            "not supported",
                            hal, bitness)
                        return False
            else:
                logging.info(
                    "The feature %s found in lshal-emitted vintf xml", hal)

    return True
