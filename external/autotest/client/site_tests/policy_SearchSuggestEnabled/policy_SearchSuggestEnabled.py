# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.enterprise import enterprise_policy_base


class policy_SearchSuggestEnabled(enterprise_policy_base.EnterprisePolicyTest):
    """
    Test effect of SearchSuggestEnabled policy on Chrome OS behavior.

    This test verifies the behavior of Chrome OS for all valid values of the
    SearchSuggestEnabled user policy: True, False, and Not set. 'Not set'
    indicates no value, and will induce the default behavior that is seen by
    an unmanaged user: checked and user-editable.

    When True or Not set, search suggestions are given. When False, search
    suggestions are not given. When set either True or False, the setting is
    disabled, so users cannot change or override the setting. When not set
    users can change the setting.

    """
    version = 1

    POLICY_NAME = 'SearchSuggestEnabled'
    STARTUP_URLS = ['chrome://policy']
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


    def _test_search_suggest_enabled(self, policy_value):
        """
        Verify CrOS enforces SearchSuggestEnabled policy.

        @param policy_value: policy value expected.

        """
        setting_pref = 'search.suggest_enabled'
        properties = self._get_settings_checkbox_properties(setting_pref)
        setting_label = properties[self.SETTING_LABEL]
        setting_is_checked = properties[self.SETTING_CHECKED]
        setting_is_disabled = properties[self.SETTING_DISABLED]
        logging.info("Check box '%s' status: checked=%s, disabled=%s",
                     setting_label, setting_is_checked, setting_is_disabled)

        # Setting checked if policy is True, unchecked if False.
        if policy_value == True and not setting_is_checked:
            raise error.TestFail('Search Suggest should be checked.')
        if policy_value == False and setting_is_checked:
            raise error.TestFail('Search Suggest should be unchecked.')

        # Setting is enabled if policy is Not set, disabled if True or False.
        if policy_value == None:
            if setting_is_disabled:
                raise error.TestFail('Search Suggest should be editable.')
        else:
            if not setting_is_disabled:
                raise error.TestFail('Search Suggest should not be editable.')


    def run_test_case(self, case):
        """
        Setup and run the test configured for the specified test case.

        @param case: Name of the test case to run.

        """
        case_value = self.TEST_CASES[case]
        self.setup_case(self.POLICY_NAME, case_value, self.SUPPORTING_POLICIES)
        self._test_search_suggest_enabled(case_value)
