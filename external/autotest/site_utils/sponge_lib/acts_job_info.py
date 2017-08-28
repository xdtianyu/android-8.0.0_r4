# Copyright 2017 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import glob
import json
import os

import logging

from autotest_lib.site_utils.sponge_lib import autotest_job_info


class ACTSSummaryEnums(object):
    """A class contains the attribute names used in a ACTS summary."""

    Requested = 'Requested'
    Failed = 'Failed'
    Unknown = 'Unknown'


class ACTSRecordEnums(object):
    """A class contains the attribute names used in an ACTS record."""

    BeginTime = 'Begin Time'
    Details = 'Details'
    EndTime = 'End Time'
    Extras = 'Extras'
    ExtraErrors = 'Extra Errors'
    Result = 'Result'
    TestClass = 'Test Class'
    TestName = 'Test Name'
    UID = 'UID'


class ACTSTaskInfo(autotest_job_info.AutotestTaskInfo):
    """Task info for an ACTS test."""

    tags = autotest_job_info.AutotestTaskInfo.tags + ['acts', 'testtracker']
    logs = autotest_job_info.AutotestTaskInfo.logs + ['results']

    def __init__(self, test, job):
        """
        @param test: The autotest test for this ACTS test.
        @param job: The job info that is the parent ot this task.
        """
        super(ACTSTaskInfo, self).__init__(test, job)

        summary_location = os.path.join(
                self.results_dir, 'results/latest/test_run_summary.json')

        build_info_location = os.path.join(self.results_dir,
                'results/BUILD_INFO-*')
        build_info_files = glob.iglob(build_info_location)

        try:
            build_info_file = next(build_info_files)
            logging.info('Using build info file: %s', build_info_file)
            with open(build_info_file) as fd:
                self.build_info = json.load(fd)
        except Exception as e:
            logging.exception(e)
            logging.error('Bad build info file.')
            self.build_info = {}

        try:
            build_prop_str = self.build_info['build_prop']
            prop_dict = {}
            self.build_info['build_prop'] = prop_dict
            lines = build_prop_str.splitlines()
            for line in lines:
                parts = line.split('=')

                if len(parts) != 2:
                    continue

                prop_dict[parts[0]] = parts[1]
        except Exception as e:
            logging.exception(e)
            logging.error('Bad build prop data, using default empty dict')
            self.build_info['build_prop'] = {}

        try:
            with open(summary_location) as fd:
                self._acts_summary = json.load(fd)

            self._summary_block = self._acts_summary['Summary']

            record_block = self._acts_summary['Results']
            self._records = list(ACTSRecord(record) for record in record_block)
            self.is_valid = True
        except Exception as e:
            logging.exception(e)
            logging.error('Bad acts data, reverting to autotest only.')
            self.is_valid = False
            self.tags = autotest_job_info.AutotestTaskInfo.tags

    @property
    def test_case_count(self):
        """The number of test cases run."""
        return self._summary_block[ACTSSummaryEnums.Requested]

    @property
    def failed_case_count(self):
        """The number of failed test cases."""
        return self._summary_block[ACTSSummaryEnums.Failed]

    @property
    def error_case_count(self):
        """The number of errored test cases."""
        return self._summary_block[ACTSSummaryEnums.Unknown]

    @property
    def records(self):
        """All records of test cases in the ACTS tests."""
        return self._records

    @property
    def effort_name(self):
        """The test tracker effort name."""
        return self.build_info.get('build_prop', {}).get(
            'ro.build.version.incremental', 'UNKNOWN_BUILD')

    @property
    def project_id(self):
        """The test tracker project id."""
        return self.keyvals.get('param-testtracker_project_id', None)

    @property
    def environment(self):
        """The name of the enviroment for test tracker."""
        return self.build_info.get('branch', 'UNKNOWN_BRANCH')


class ACTSRecord(object):
    """A single record of a test case in an ACTS test."""

    tags = ['acts', 'testtracker']

    def __init__(self, json_record):
        """
        @param json_record: The json info for this record
        """
        self._json_record = json_record

    @property
    def test_class(self):
        """The test class that was run."""
        return self._json_record[ACTSRecordEnums.TestClass]

    @property
    def test_case(self):
        """The test case that was run. None implies all in the class."""
        return self._json_record.get(ACTSRecordEnums.TestName, None)

    @property
    def uid(self):
        """The uid of the test case."""
        return self._json_record.get(ACTSRecordEnums.UID, None)

    @property
    def status(self):
        """The status of the test case."""
        return self._json_record[ACTSRecordEnums.Result]

    @property
    def start_time(self):
        """The start time of the test case."""
        return self._json_record[ACTSRecordEnums.BeginTime] / 1000.0

    @property
    def end_time(self):
        """The end time of the test case."""
        return self._json_record[ACTSRecordEnums.EndTime] / 1000.0

    @property
    def details(self):
        """Details about the test case."""
        return self._json_record.get(ACTSRecordEnums.Details, None)

    @property
    def extras(self):
        """Extra info about the test case."""
        return self._json_record.get(ACTSRecordEnums.Extras, None)

    @property
    def extra_errors(self):
        """Extra errors about the test case."""
        return self._json_record.get(ACTSRecordEnums.ExtraErrors, None)

    @property
    def uuid(self):
        """The test tracker uuid of the test case."""
        extras = self.extras
        if not extras:
            return None

        test_tracker_info = self.extras.get('test_tracker_info', None)
        if not test_tracker_info:
            return None

        return test_tracker_info.get('test_tracker_uuid', None)
