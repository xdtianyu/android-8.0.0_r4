# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.cros import power_status
from autotest_lib.client.cros.enterprise import enterprise_policy_base


class policy_ChromeOsLockOnIdleSuspend(
            enterprise_policy_base.EnterprisePolicyTest):
    """
    Test effect of ChromeOsLockOnIdleSuspend policy on Chrome OS behavior.

    This test verifies the behaviour and appearance of the 'Require password
    to wake from sleep' check box setting in the Security: Idle Settings
    section of the chrome://settings page for all valid values of the user
    policy ChromeOsLockOnIdleSuspend: True, False, and Not set. The
    corresponding test cases are: True_Lock, False_Unlock, NotSet_Unlock.

    Note: True_Lock case is not run as part of the regression suite due to
    bug crbug.com/666430. See control.true_lock for details.

    """
    version = 1

    POLICY_NAME = 'ChromeOsLockOnIdleSuspend'
    TEST_CASES = {
        'True_Lock': True,
        'False_Unlock': False,
        'NotSet_Unlock': None
    }
    IDLE_ACTION_DELAY = 5
    POWER_MANAGEMENT_IDLE_SETTINGS = {
        'AC': {
            'Delays': {
                'ScreenDim': 2000,
                'ScreenOff': 3000,
                'IdleWarning': 4000,
                'Idle': (IDLE_ACTION_DELAY * 1000)
            },
            'IdleAction': 'Suspend'
        },
        'Battery': {
            'Delays': {
                'ScreenDim': 2000,
                'ScreenOff': 3000,
                'IdleWarning': 4000,
                'Idle': (IDLE_ACTION_DELAY * 1000)
            },
            'IdleAction': 'Suspend'
        }
    }
    PERCENT_CHARGE_MIN = 10
    STARTUP_URLS = ['chrome://policy', 'chrome://settings']
    SUPPORTING_POLICIES = {
        'AllowScreenLock': True,
        'LidCloseAction': 0,
        'PowerManagementIdleSettings': POWER_MANAGEMENT_IDLE_SETTINGS,
        'RestoreOnStartup': 4,
        'RestoreOnStartupURLs': STARTUP_URLS
    }


    def initialize(self, **kwargs):
        """Set up local variables and ensure sufficient battery charge."""
        self._power_status = power_status.get_status()
        if not self._power_status.on_ac():
            # Ensure that the battery has sufficient minimum charge.
            self._power_status.assert_battery_state(self.PERCENT_CHARGE_MIN)

        logging.info('Device power type is "%s"', self._power_type)
        super(policy_ChromeOsLockOnIdleSuspend, self).initialize(**kwargs)


    @property
    def _power_type(self):
        """Return type of power used by DUT: AC or Battery."""
        return 'AC' if self._power_status.on_ac() else 'Battery'


    def _is_screen_locked(self):
        """Return true if login status indicates that screen is locked."""
        login_status = utils.wait_for_value(
            lambda: self.cr.login_status['isScreenLocked'],
                    expected_value=True,
                    timeout_sec=self.IDLE_ACTION_DELAY)
        return login_status


    def _test_require_password_to_wake(self, policy_value):
        """
        Verify CrOS enforces ChromeOsLockOnIdleSuspend policy value.

        When ChromeOsLockOnIdleSuspend policy is set true (false), then the
        'Require password to wake from sleep' check box shall be (un)checked.
        When set either True or False, then the check box shall be uneditable.
        When Not set, then the check box shall be editable.

        @param policy_value: policy value for this case.
        @raises: TestFail if setting is incorrectly (un)checked or
                 (un)editable, based on the policy value.

        """
        # Get check box status from the settings page.
        setting_pref = 'settings.enable_screen_lock'
        properties = self._get_settings_checkbox_properties(setting_pref)
        setting_is_checked = properties[self.SETTING_CHECKED]
        setting_is_disabled = properties[self.SETTING_DISABLED]

        # Setting shall be checked if policy is set True, unchecked if False.
        if policy_value == True and not setting_is_checked:
            raise error.TestFail('"Require password to wake from sleep" '
                                 'setting should be checked.')
        if policy_value == False and setting_is_checked:
            raise error.TestFail('"Require password to wake from sleep" '
                                 'setting should be unchecked.')

        # Setting shall be enabled if policy is Not set, disabled if set.
        if policy_value == None:
            if setting_is_disabled:
                raise error.TestFail('"Require password to wake from sleep" '
                                     'setting should be editable.')
        else:
            if not setting_is_disabled:
                raise error.TestFail('"Require password to wake from sleep" '
                                     'setting should be uneditable.')

        # Screen shall be locked if the policy is True, else unlocked.
        screen_is_locked = self._is_screen_locked()
        if policy_value == True:
            if not screen_is_locked:
                raise error.TestFail('Screen should be locked.')
        else:
            if screen_is_locked:
                raise error.TestFail('Screen should be unlocked.')


    def run_test_case(self, case):
        """
        Setup and run the test configured for the specified test case.

        @param case: Name of the test case to run.

        """
        case_value = self.TEST_CASES[case]
        self.setup_case(self.POLICY_NAME, case_value, self.SUPPORTING_POLICIES)
        self._test_require_password_to_wake(case_value)
