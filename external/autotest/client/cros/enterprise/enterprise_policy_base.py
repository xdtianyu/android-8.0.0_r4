# Copyright 2015 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import json
import logging
import os

from autotest_lib.client.bin import test
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import chrome
from autotest_lib.client.cros import cryptohome
from autotest_lib.client.cros import httpd
from autotest_lib.client.cros.enterprise import enterprise_fake_dmserver

CROSQA_FLAGS = [
    '--gaia-url=https://gaiastaging.corp.google.com',
    '--lso-url=https://gaiastaging.corp.google.com',
    '--google-apis-url=https://www-googleapis-test.sandbox.google.com',
    '--oauth2-client-id=236834563817.apps.googleusercontent.com',
    '--oauth2-client-secret=RsKv5AwFKSzNgE0yjnurkPVI',
    ('--cloud-print-url='
     'https://cloudprint-nightly-ps.sandbox.google.com/cloudprint'),
    '--ignore-urlfetcher-cert-requests']
CROSALPHA_FLAGS = [
    ('--cloud-print-url='
     'https://cloudprint-nightly-ps.sandbox.google.com/cloudprint'),
    '--ignore-urlfetcher-cert-requests']
TESTDMS_FLAGS = [
    '--ignore-urlfetcher-cert-requests',
    '--disable-policy-key-verification']
FLAGS_DICT = {
    'prod': [],
    'crosman-qa': CROSQA_FLAGS,
    'crosman-alpha': CROSALPHA_FLAGS,
    'dm-test': TESTDMS_FLAGS,
    'dm-fake': TESTDMS_FLAGS
}
DMS_URL_DICT = {
    'prod': 'http://m.google.com/devicemanagement/data/api',
    'crosman-qa':
        'https://crosman-qa.sandbox.google.com/devicemanagement/data/api',
    'crosman-alpha':
        'https://crosman-alpha.sandbox.google.com/devicemanagement/data/api',
    'dm-test': 'http://chromium-dm-test.appspot.com/d/%s',
    'dm-fake': 'http://127.0.0.1:%d/'
}
DMSERVER = '--device-management-url=%s'
# Username and password for the fake dm server can be anything, since
# they are not used to authenticate against GAIA.
USERNAME = 'fake-user@managedchrome.com'
PASSWORD = 'fakepassword'


class EnterprisePolicyTest(test.test):
    """Base class for Enterprise Policy Tests."""

    WEB_PORT = 8080
    WEB_HOST = 'http://localhost:%d' % WEB_PORT
    CHROME_SETTINGS_PAGE = 'chrome://settings'
    CHROME_POLICY_PAGE = 'chrome://policy'
    SETTING_LABEL = 0
    SETTING_CHECKED = 1
    SETTING_DISABLED = 2

    def setup(self):
        os.chdir(self.srcdir)
        utils.make()


    def initialize(self, **kwargs):
        self._initialize_enterprise_policy_test(**kwargs)


    def _initialize_enterprise_policy_test(
            self, case, env='dm-fake', dms_name=None,
            username=USERNAME, password=PASSWORD):
        """Initialize test parameters, fake DM Server, and Chrome flags.

        @param case: String name of the test case to run.
        @param env: String environment of DMS and Gaia servers.
        @param username: String user name login credential.
        @param password: String password login credential.
        @param dms_name: String name of test DM Server.
        """
        self.case = case
        self.env = env
        self.username = username
        self.password = password
        self.dms_name = dms_name
        self.dms_is_fake = (env == 'dm-fake')
        self._enforce_variable_restrictions()

        # Initialize later variables to prevent error after an early failure.
        self._web_server = None
        self.cr = None

        # Start AutoTest DM Server if using local fake server.
        if self.dms_is_fake:
            self.fake_dm_server = enterprise_fake_dmserver.FakeDMServer(
                self.srcdir)
            self.fake_dm_server.start(self.tmpdir, self.debugdir)

        # Get enterprise directory of shared resources.
        client_dir = os.path.dirname(os.path.dirname(self.bindir))
        self.enterprise_dir = os.path.join(client_dir, 'cros/enterprise')

        # Log the test context parameters.
        logging.info('Test Context Parameters:')
        logging.info('  Case: %r', self.case)
        logging.info('  Environment: %r', self.env)
        logging.info('  Username: %r', self.username)
        logging.info('  Password: %r', self.password)
        logging.info('  Test DMS Name: %r', self.dms_name)


    def cleanup(self):
        # Clean up AutoTest DM Server if using local fake server.
        if self.dms_is_fake:
            self.fake_dm_server.stop()

        # Stop web server if it was started.
        if self._web_server:
            self._web_server.stop()

        # Close Chrome instance if opened.
        if self.cr:
            self.cr.close()


    def start_webserver(self):
        """Set up HTTP Server to serve pages from enterprise directory."""
        self._web_server = httpd.HTTPListener(
                self.WEB_PORT, docroot=self.enterprise_dir)
        self._web_server.run()


    def _enforce_variable_restrictions(self):
        """Validate class-level test context parameters.

        @raises error.TestError if context parameter has an invalid value,
                or a combination of parameters have incompatible values.
        """
        # Verify |env| is a valid environment.
        if self.env not in FLAGS_DICT:
            raise error.TestError('Environment is invalid: %s' % self.env)

        # Verify test |dms_name| is given iff |env| is 'dm-test'.
        if self.env == 'dm-test' and not self.dms_name:
            raise error.TestError('dms_name must be given when using '
                                  'env=dm-test.')
        if self.env != 'dm-test' and self.dms_name:
            raise error.TestError('dms_name must not be given when not using '
                                  'env=dm-test.')


    def setup_case(self, policy_name, policy_value, mandatory_policies={},
                   suggested_policies={}, policy_name_is_suggested=False,
                   skip_policy_value_verification=False):
        """Set up and confirm the preconditions of a test case.

        If the AutoTest fake DM Server is used, make a JSON policy blob
        and upload it to the fake DM server.

        Launch Chrome and sign in to Chrome OS. Examine the user's
        cryptohome vault, to confirm user is signed in successfully.

        Open the Policies page, and confirm that it shows the specified
        |policy_name| and has the correct |policy_value|.

        @param policy_name: Name of the policy under test.
        @param policy_value: Expected value for the policy under test.
        @param mandatory_policies: optional dict of mandatory policies
                (not policy_name) in name -> value format.
        @param suggested_policies: optional dict of suggested policies
                (not policy_name) in name -> value format.
        @param policy_name_is_suggested: True if policy_name a suggested policy.
        @param skip_policy_value_verification: True if setup_case should not
                verify that the correct policy value shows on policy page.

        @raises error.TestError if cryptohome vault is not mounted for user.
        @raises error.TestFail if |policy_name| and |policy_value| are not
                shown on the Policies page.
        """
        logging.info('Setting up case: (%s: %s)', policy_name, policy_value)
        logging.info('Mandatory policies: %s', mandatory_policies)
        logging.info('Suggested policies: %s', suggested_policies)

        if self.dms_is_fake:
            if policy_name_is_suggested:
                suggested_policies[policy_name] = policy_value
            else:
                mandatory_policies[policy_name] = policy_value
            self.fake_dm_server.setup_policy(self._make_json_blob(
                mandatory_policies, suggested_policies))

        self._launch_chrome_browser()
        if not cryptohome.is_vault_mounted(user=self.username,
                                           allow_fail=True):
            raise error.TestError('Expected to find a mounted vault for %s.'
                                  % self.username)
        if not skip_policy_value_verification:
            self.verify_policy_value(policy_name, policy_value)


    def _make_json_blob(self, mandatory_policies, suggested_policies):
        """Create JSON policy blob from mandatory and suggested policies.

        For the status of a policy to be shown as "Not set" on the
        chrome://policy page, the policy dictionary must contain no NVP for
        for that policy. Remove policy NVPs if value is None.

        @param mandatory_policies: dict of mandatory policies -> values.
        @param suggested_policies: dict of suggested policies -> values.

        @returns: JSON policy blob to send to the fake DM server.
        """
        # Remove "Not set" policies and json-ify dicts because the
        # FakeDMServer expects "policy": "{value}" not "policy": {value}.
        for policies_dict in [mandatory_policies, suggested_policies]:
            policies_to_pop = []
            for policy in policies_dict:
                value = policies_dict[policy]
                if value is None:
                    policies_to_pop.append(policy)
                elif isinstance(value, dict):
                    policies_dict[policy] = encode_json_string(value)
                elif isinstance(value, list):
                    if len(value) > 0 and isinstance(value[0], dict):
                        policies_dict[policy] = encode_json_string(value)
            for policy in policies_to_pop:
                policies_dict.pop(policy)

        modes_dict = {}
        if mandatory_policies:
            modes_dict['mandatory'] = mandatory_policies
        if suggested_policies:
            modes_dict['suggested'] = suggested_policies

        device_management_dict = {
            'google/chromeos/user': modes_dict,
            'managed_users': ['*'],
            'policy_user': self.username,
            'current_key_index': 0,
            'invalidation_source': 16,
            'invalidation_name': 'test_policy'
        }

        logging.info('Created policy blob: %s', device_management_dict)
        return encode_json_string(device_management_dict)


    def _get_policy_value_shown(self, policy_tab, policy_name):
        """Get the value shown for |policy_name| from the |policy_tab| page.

        Return the policy value for the policy given by |policy_name|, from
        from the chrome://policy page given by |policy_tab|.

        CAVEAT: the policy page does not display proper JSON. For example, lists
        are generally shown without the [ ] and cannot be distinguished from
        strings.  This function decodes what it can and returns the string it
        found when in doubt.

        @param policy_tab: Tab displaying the Policies page.
        @param policy_name: The name of the policy.

        @returns: The decoded value shown for the policy on the Policies page,
                with the aforementioned caveat.
        """
        row_values = policy_tab.EvaluateJavaScript('''
            var section = document.getElementsByClassName(
                    "policy-table-section")[0];
            var table = section.getElementsByTagName('table')[0];
            rowValues = '';
            for (var i = 1, row; row = table.rows[i]; i++) {
               if (row.className !== 'expanded-value-container') {
                  var name_div = row.getElementsByClassName('name elide')[0];
                  var name = name_div.textContent;
                  if (name === '%s') {
                     var value_span = row.getElementsByClassName('value')[0];
                     var value = value_span.textContent;
                     var status_div = row.getElementsByClassName(
                            'status elide')[0];
                     var status = status_div.textContent;
                     rowValues = [name, value, status];
                     break;
                  }
               }
            }
            rowValues;
        ''' % policy_name)

        value_shown = row_values[1].encode('ascii', 'ignore')
        status_shown = row_values[2].encode('ascii', 'ignore')
        logging.debug('Policy %s row: %s', policy_name, row_values)

        if status_shown == 'Not set.':
            return None
        return decode_json_string(value_shown)


    def _get_policy_value_from_new_tab(self, policy_name):
        """Get the policy value for |policy_name| from the Policies page.

        @param policy_name: string of policy name.

        @returns: decoded value of the policy as shown on chrome://policy.
        """
        values = self._get_policy_values_from_new_tab([policy_name])
        return values[policy_name]


    def _get_policy_values_from_new_tab(self, policy_names):
        """Get a given policy value by opening a new tab then closing it.

        @param policy_names: list of strings of policy names.

        @returns: dict of policy name mapped to decoded values of the policy as
                  shown on chrome://policy.
        """
        values = {}
        tab = self.navigate_to_url(self.CHROME_POLICY_PAGE)
        for policy_name in policy_names:
          values[policy_name] = self._get_policy_value_shown(tab, policy_name)
        tab.Close()

        return values


    def verify_policy_value(self, policy_name, expected_value):
        """
        Verify that the correct policy values shows in chrome://policy.

        @param policy_name: the policy we are checking.
        @param expected_value: the expected value for policy_name.

        @raises error.TestError if value does not match expected.

        """
        value_shown = self._get_policy_value_from_new_tab(policy_name)
        logging.info('Value decoded from chrome://policy: %s', value_shown)

        # If we expect a list and don't have a list, modify the value_shown.
        if isinstance(expected_value, list):
            if isinstance(value_shown, str):
                if '{' in value_shown: # List of dicts.
                    value_shown = decode_json_string('[%s]' % value_shown)
                elif ',' in value_shown: # List of strs.
                    value_shown = value_shown.split(',')
                else: # List with one str.
                    value_shown = [value_shown]
            elif not isinstance(value_shown, list): # List with one element.
                value_shown = [value_shown]

        if not expected_value == value_shown:
            raise error.TestError('chrome://policy shows the incorrect value '
                                  'for %s!  Expected %s, got %s.' % (
                                          policy_name, expected_value,
                                          value_shown))


    def _initialize_chrome_extra_flags(self):
        """
        Initialize flags used to create Chrome instance.

        @returns: list of extra Chrome flags.

        """
        # Construct DM Server URL flags if not using production server.
        env_flag_list = []
        if self.env != 'prod':
            if self.dms_is_fake:
                # Use URL provided by the fake AutoTest DM server.
                dmserver_str = (DMSERVER % self.fake_dm_server.server_url)
            else:
                # Use URL defined in the DMS URL dictionary.
                dmserver_str = (DMSERVER % (DMS_URL_DICT[self.env]))
                if self.env == 'dm-test':
                    dmserver_str = (dmserver_str % self.dms_name)

            # Merge with other flags needed by non-prod enviornment.
            env_flag_list = ([dmserver_str] + FLAGS_DICT[self.env])

        return env_flag_list


    def _launch_chrome_browser(self):
        """Launch Chrome browser and sign in."""
        extra_flags = self._initialize_chrome_extra_flags()

        logging.info('Chrome Browser Arguments:')
        logging.info('  extra_browser_args: %s', extra_flags)
        logging.info('  username: %s', self.username)
        logging.info('  password: %s', self.password)
        logging.info('  gaia_login: %s', not self.dms_is_fake)

        self.cr = chrome.Chrome(extra_browser_args=extra_flags,
                                username=self.username,
                                password=self.password,
                                gaia_login=not self.dms_is_fake,
                                disable_gaia_services=False,
                                autotest_ext=True)


    def navigate_to_url(self, url, tab=None):
        """Navigate tab to the specified |url|. Create new tab if none given.

        @param url: URL of web page to load.
        @param tab: browser tab to load (if any).
        @returns: browser tab loaded with web page.
        """
        logging.info('Navigating to URL: %r', url)
        if not tab:
            tab = self.cr.browser.tabs.New()
            tab.Activate()
        tab.Navigate(url, timeout=8)
        tab.WaitForDocumentReadyStateToBeComplete()
        return tab


    def get_elements_from_page(self, tab, cmd):
        """Get collection of page elements that match the |cmd| filter.

        @param tab: tab containing the page to be scraped.
        @param cmd: JavaScript command to evaluate on the page.
        @returns object containing elements on page that match the cmd.
        @raises: TestFail if matching elements are not found on the page.
        """
        try:
            elements = tab.EvaluateJavaScript(cmd)
        except Exception as err:
            raise error.TestFail('Unable to find matching elements on '
                                 'the test page: %s\n %r' %(tab.url, err))
        return elements


    def _get_settings_checkbox_properties(self, pref):
        """Get properties of the |pref| check box on the settings page.

        @param pref: pref attribute value of the check box setting.
        @returns: element properties of the check box setting.
        """
        js_cmd_get_props = ('''
        settings = document.getElementsByClassName(
                'checkbox controlled-setting-with-label');
        settingValues = '';
        for (var i = 0, setting; setting = settings[i]; i++) {
           var setting_label = setting.getElementsByTagName("label")[0];
           var label_input = setting_label.getElementsByTagName("input")[0];
           var input_pref = label_input.getAttribute("pref");
           if (input_pref == '%s') {
              settingValues = [setting_label.innerText.trim(),
                               label_input.checked, label_input.disabled];
              break;
           }
        }
        settingValues;
        ''' % pref)
        tab = self.navigate_to_url(self.CHROME_SETTINGS_PAGE)
        checkbox_props = self.get_elements_from_page(tab, js_cmd_get_props)
        tab.Close()
        return checkbox_props


    def run_once(self):
        """The run_once() method is required by all AutoTest tests.

        run_once() is defined herein to automatically determine which test
        case in the test class to run. The test class must have a public
        run_test_case() method defined. Note: The test class may override
        run_once() if it determines which test case to run.
        """
        logging.info('Running test case: %s', self.case)
        self.run_test_case(self.case)


def encode_json_string(object_value):
    """Convert given value to JSON format string.

    @param object_value: object to be converted.

    @returns: string in JSON format.
    """
    return json.dumps(object_value)


def decode_json_string(json_string):
    """Convert given JSON format string to an object.

    If no object is found, return json_string instead.  This is to allow
    us to "decode" items off the policy page that aren't real JSON.

    @param json_string: the JSON string to be decoded.

    @returns: Python object represented by json_string or json_string.
    """
    def _decode_list(json_list):
        result = []
        for value in json_list:
            if isinstance(value, unicode):
                value = value.encode('ascii')
            if isinstance(value, list):
                value = _decode_list(value)
            if isinstance(value, dict):
                value = _decode_dict(value)
            result.append(value)
        return result

    def _decode_dict(json_dict):
        result = {}
        for key, value in json_dict.iteritems():
            if isinstance(key, unicode):
                key = key.encode('ascii')
            if isinstance(value, unicode):
                value = value.encode('ascii')
            elif isinstance(value, list):
                value = _decode_list(value)
            result[key] = value
        return result

    try:
        # Decode JSON turning all unicode strings into ascii.
        # object_hook will get called on all dicts, so also handle lists.
        result = json.loads(json_string, encoding='ascii',
                            object_hook=_decode_dict)
        if isinstance(result, list):
            result = _decode_list(result)
        return result
    except ValueError as e:
        # Input not valid, e.g. '1, 2, "c"' instead of '[1, 2, "c"]'.
        logging.warning('Could not unload: %s (%s)', json_string, e)
        return json_string
