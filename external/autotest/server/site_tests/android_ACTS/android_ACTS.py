# Copyright 2015 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os

import common
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import global_config
from autotest_lib.server import afe_utils
from autotest_lib.server import test
from autotest_lib.site_utils import acts_lib

CONFIG_FOLDER_LOCATION = global_config.global_config.get_config_value(
    'ACTS', 'acts_config_folder', default='')

TEST_CONFIG_FILE_FOLDER = os.path.join(CONFIG_FOLDER_LOCATION,
                                       'autotest_config')
TEST_CAMPAIGN_FILE_FOLDER = os.path.join(CONFIG_FOLDER_LOCATION,
                                         'autotest_campaign')

DEFAULT_TEST_RELATIVE_LOG_PATH = 'results/logs'


class android_ACTS(test.test):
    """Run an Android CTS test case.

    Component relationship:
    Workstation ----(ssh)---> TestStation -----(adb)-----> Android DUT
    This code runs on Workstation.
    """
    version = 1

    def run_once(self,
                 testbed=None,
                 config_file=None,
                 testbed_name=None,
                 test_case=None,
                 test_file=None,
                 additional_configs=[],
                 additional_apks=[],
                 override_build_url=None,
                 override_acts_zip=None,
                 override_internal_acts_dir=None,
                 override_python_bin='python',
                 acts_timeout=7200,
                 perma_path=None,
                 additional_cmd_line_params=None,
                 testtracker_project_id=None):
        """Runs an acts test case.

        @param testbed: The testbed to test on.
        @config_file: The main config file to use for running the test. This
                      should be relative to the autotest_config folder.
        @param test_case: The test case to run. Should be None when test_file
                          is given.
        @param test_file: The campaign file to run. Should be None when
                          test_case is given. This should be relative to the
                          autotest_campaign folder. If multiple are given,
                          multiple test cases will be run.
        @param additional_configs: Any additional config files to use.
                                   These should be relative to the
                                   autotest_config folder.
        @param additional_apks: An array of apk info dictionaries.
                                apk = Name of the apk (eg. sl4a.apk)
                                package = Name of the package (eg. test.tools)
                                artifact = Name of the artifact, if not given
                                           package is used.
        @param override_build_url: The build url to fetch acts from. If None
                                   then the build url of the first adb device
                                   is used.
        @param override_acts_zip: If given a zip file on the drone is used
                                  rather than pulling a build.
        @param override_internal_acts_dir: The directory within the artifact
                                           where the acts framework folder
                                           lives.
        @param override_python_bin: Overrides the default python binary that
                                    is used.
        @param acts_timeout: How long to wait for acts to finish.
        @param perma_path: If given a permantent path will be used rather than
                           a temp path.
        @parm testtracker_project_id: ID to use for test tracker project.
        """
        host = next(v for v in testbed.get_adb_devices().values())

        if not host:
            raise error.TestError('No hosts defined for this test, cannot'
                                  ' determine build to grab artifact from.')

        job_repo_url = afe_utils.get_host_attribute(
            host, host.job_repo_url_attribute)
        test_station = testbed.teststation
        if not perma_path:
            ts_tempfolder = test_station.get_tmp_dir()
        else:
            test_station.run('rm -fr "%s"' % perma_path)
            test_station.run('mkdir "%s"' % perma_path)
            ts_tempfolder = perma_path
        target_zip = os.path.join(ts_tempfolder, 'acts.zip')

        if override_acts_zip and override_build_url:
            raise error.TestError('Cannot give both a url and zip override.')
        elif override_acts_zip:
            package = acts_lib.create_acts_package_from_zip(
                test_station, override_acts_zip, target_zip)
        elif override_build_url:
            build_url_pieces = override_build_url.split('/')
            if len(build_url_pieces) != 3:
                raise error.TestError(
                    'Override build url must be formatted as '
                    '<branch>/<target>/<build_id>')

            branch = build_url_pieces[0]
            target = build_url_pieces[1]
            build_id = build_url_pieces[2]
            package = acts_lib.create_acts_package_from_artifact(
                test_station, branch, target, build_id, job_repo_url,
                target_zip)
        else:
            package = acts_lib.create_acts_package_from_current_artifact(
                test_station, job_repo_url, target_zip)

        test_env = package.create_enviroment(
            testbed=testbed,
            container_directory=ts_tempfolder,
            testbed_name=testbed_name,
            internal_acts_directory=override_internal_acts_dir)

        test_env.install_sl4a_apk()

        for apk in additional_apks:
            test_env.install_apk(apk)

        test_env.setup_enviroment(python_bin=override_python_bin)

        test_env.upload_config(config_file)

        if additional_configs:
            for additional_config in additional_configs:
                test_env.upload_config(additional_config)

        if test_file:
            test_env.upload_campaign(test_file)

        results = test_env.run_test(
            config_file,
            campaign=test_file,
            test_case=test_case,
            python_bin=override_python_bin,
            timeout=acts_timeout,
            additional_cmd_line_params=additional_cmd_line_params)

        results.log_output()
        results.report_to_autotest(self)
        results.save_test_info(self)
        results.rethrow_exception()
