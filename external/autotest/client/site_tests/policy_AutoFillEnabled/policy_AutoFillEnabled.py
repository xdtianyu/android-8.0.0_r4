# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.enterprise import enterprise_policy_base


class policy_AutoFillEnabled(enterprise_policy_base.EnterprisePolicyTest):
    """
    Test effect of AutoFillEnabled policy on Chrome OS behavior.

    This test verifies the behavior of Chrome OS for all valid values of the
    AutoFillEnabled user policy: True, False, and Not set. 'Not set'
    indicates no value, and will induce the enabled behavior by default,
    as would be seen by an un-managed user.

    When set True or not set, the Enable Autofill setting can be modified by
    the user. When set False, Enable Autofill setting is unchecked and cannot
    be modified.

    """
    version = 1

    POLICY_NAME = 'AutoFillEnabled'
    STARTUP_URLS = ['chrome://policy', 'chrome://settings/autofill']
    SUPPORTING_POLICIES = {
        'BookmarkBarEnabled': True,
        'RestoreOnStartupURLs': STARTUP_URLS,
        'RestoreOnStartup': 4
    }

    TEST_CASES = {
        'True_Enable': True,
        'False_Disable': False,
        'NotSet_Enable': None
    }

    def _is_autofill_setting_editable(self):
        """
        Check whether Enabled Autofill setting is editable.

        @returns: True if Enable Autofill setting is editable.

        """
        settings_tab = self.navigate_to_url(self.CHROME_SETTINGS_PAGE)
        js_code_is_editable = ('document.getElementById('
                               '"autofill-enabled").disabled')
        is_editable = not settings_tab.EvaluateJavaScript(js_code_is_editable)
        settings_tab.Close()
        return is_editable


    def _test_autofill_enabled(self, policy_value):
        """
        Verify CrOS enforces AutoFillEnabled policy.

        @param policy_value: policy value for this case.

        """
        autofill_is_enabled = self._is_autofill_setting_editable()
        if policy_value == True or policy_value == None:
            if not autofill_is_enabled:
                raise error.TestFail('Autofill should be enabled.')
        else:
            if autofill_is_enabled:
                raise error.TestFail('Autofill should be disabled.')


    def run_test_case(self, case):
        """
        Setup and run the test configured for the specified test case.

        @param case: Name of the test case to run.

        """
        case_value = self.TEST_CASES[case]
        self.setup_case(self.POLICY_NAME, case_value, self.SUPPORTING_POLICIES)
        self._test_autofill_enabled(case_value)
