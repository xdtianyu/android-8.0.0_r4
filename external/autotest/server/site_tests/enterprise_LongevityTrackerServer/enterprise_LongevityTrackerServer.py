# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os
import re
import csv
import json
import time
import shutil
import urllib
import urllib2
import logging
import httplib

from autotest_lib.client.common_lib.cros import tpm_utils
from autotest_lib.server import autotest
from autotest_lib.server import test
from autotest_lib.server.cros.multimedia import remote_facade_factory


PERF_CAPTURE_ITERATIONS = 15 #Number of data points that will be uploaded.
PERF_CAPTURE_DURATION = 3600 #Duration in secs of each data point capture.
SAMPLE_INTERVAL = 60
METRIC_INTERVAL = 600
STABILIZATION_DURATION = 60
_MEASUREMENT_DURATION_SECONDS = 10
TMP_DIRECTORY = '/tmp/'
PERF_FILE_NAME_PREFIX = 'perf'
VERSION_PATTERN = r'^(\d+)\.(\d+)\.(\d+)$'
DASHBOARD_UPLOAD_URL = 'https://chromeperf.appspot.com/add_point'


class PerfUploadingError(Exception):
    """Exception raised in perf_uploader."""
    pass


class enterprise_LongevityTrackerServer(test.test):
    """Run Longevity Test: Collect performance data over long duration.

    Run enterprise_KioskEnrollment and clear the TPM as necessary. After
    enterprise enrollment is successful, collect and log cpu, memory, and
    temperature data from the device under test.
    """
    version = 1


    def initialize(self):
        self.temp_dir = os.path.split(self.tmpdir)[0]


    def _get_cpu_usage(self):
        """Returns cpu usage in %."""
        cpu_usage_start = self.system_facade.get_cpu_usage()
        time.sleep(_MEASUREMENT_DURATION_SECONDS)
        cpu_usage_end = self.system_facade.get_cpu_usage()
        return self.system_facade.compute_active_cpu_time(cpu_usage_start,
                cpu_usage_end) * 100


    def _get_memory_usage(self):
        """Returns total used memory in %."""
        total_memory = self.system_facade.get_mem_total()
        return ((total_memory - self.system_facade.get_mem_free())
                * 100 / total_memory)


    def _get_temperature_data(self):
        """Returns temperature sensor data in fahrenheit."""
        ectool = self.client.run('ectool version', ignore_status=True)
        if not ectool.exit_status:
            ec_temp = self.system_facade.get_ec_temperatures()
            return ec_temp[1]
        else:
            temp_sensor_name = 'temp0'
            if not temp_sensor_name:
                return 0
            MOSYS_OUTPUT_RE = re.compile('(\w+)="(.*?)"')
            values = {}
            cmd = 'mosys -k sensor print thermal %s' % temp_sensor_name
            for kv in MOSYS_OUTPUT_RE.finditer(self.client.run_output(cmd)):
                key, value = kv.groups()
                if key == 'reading':
                    value = int(value)
                values[key] = value
            return values['reading']


    #TODO(krishnargv@): Add a method to retrieve the version of the
    #                   Kiosk app from its manifest.
    def _initialize_test_variables(self):
        """Initialize test variables that will be uploaded to the dashboard."""
        self.subtest_name = self.kiosk_app_name
        self.board_name = self.system_facade.get_current_board()
        self.chromeos_version = self.system_facade.get_chromeos_release_version()
        self.epoch_minutes = str(int(time.time() / 60))
        self.point_id = self._get_point_id(self.chromeos_version,
                                           self.epoch_minutes)
        self.test_suite_name = self.tagged_testname
        logging.info("Board Name: %s", self.board_name)
        logging.info("Chromeos Version: %r", self.chromeos_version)
        logging.info("Point_id: %r", self.point_id)


    #TODO(krishnargv): Replace _get_point_id with a call to the
    #                  _get_id_from_version method of the perf_uploader.py.
    def _get_point_id(self, cros_version, epoch_minutes):
        """Compute point ID from ChromeOS version number and epoch minutes.

        @param cros_version: String of ChromeOS version number.
        @param epoch_minutes: String of minutes since 1970.

        @return unique integer ID computed from given version and epoch.
        """
        # Number of digits from each part of the Chrome OS version string.
        cros_version_col_widths = [0, 4, 3, 2]

        def get_digits(version_num, column_widths):
            if re.match(VERSION_PATTERN, version_num):
                computed_string = ''
                version_parts = version_num.split('.')
                for i, version_part in enumerate(version_parts):
                    if column_widths[i]:
                        computed_string += version_part.zfill(column_widths[i])
                return computed_string
            else:
                return None

        cros_digits = get_digits(cros_version, cros_version_col_widths)
        epoch_digits = epoch_minutes[-8:]
        if not cros_digits:
            return None
        return int(epoch_digits + cros_digits)


    def _open_perf_file(self, file_path):
        """Open a perf file. Write header line if new. Return file object.

        If the file on |file_path| already exists, then open file for
        appending only. Otherwise open for writing only.

        @param file_path: file path for perf file.
        @returns file object for the perf file.
        """
        if os.path.isfile(file_path):
            perf_file = open(file_path, 'a+')
        else:
            perf_file = open(file_path, 'w')
            perf_file.write('Time,CPU,Memory,Temperature (C)\r\n')
        return perf_file


    def elapsed_time(self, mark_time):
        """Get time elapsed since |mark_time|.

        @param mark_time: point in time from which elapsed time is measured.
        @returns time elapsed since the marked time.
        """
        return time.time() - mark_time


    def modulo_time(self, timer, interval):
        """Get time eplased on |timer| for the |interval| modulus.

        Value returned is used to adjust the timer so that it is synchronized
        with the current interval.

        @param timer: time on timer, in seconds.
        @param interval: period of time in seconds.
        @returns time elapsed from the start of the current interval.
        """
        return timer % int(interval)


    def syncup_time(self, timer, interval):
        """Get time remaining on |timer| for the |interval| modulus.

        Value returned is used to induce sleep just long enough to put the
        process back in sync with the timer.

        @param timer: time on timer, in seconds.
        @param interval: period of time in seconds.
        @returns time remaining till the end of the current interval.
        """
        return interval - (timer % int(interval))


    #TODO(krishnargv):  Replace _format_data_for_upload with a call to the
    #                   _format_for_upload method of the perf_uploader.py
    def _format_data_for_upload(self, chart_data):
        """Collect chart data into an uploadable data JSON object.

        @param chart_data: performance results formatted as chart data.
        """
        perf_values = {
            'format_version': '1.0',
            'benchmark_name': self.test_suite_name,
            'charts': chart_data,
        }
        #TODO(krishnargv): Add a method to capture the chrome_version.
        dash_entry = {
            'master': 'ChromeOS_Enterprise',
            'bot': 'cros-%s' % self.board_name,
            'point_id': self.point_id,
            'versions': {
                'cros_version': self.chromeos_version,

            },
            'supplemental': {
                'default_rev': 'r_cros_version',
                'kiosk_app_name': 'a_' + self.kiosk_app_name,

            },
            'chart_data': perf_values
        }
        return {'data': json.dumps(dash_entry)}


    #TODO(krishnargv):  Replace _send_to_dashboard with a call to the
    #                   _send_to_dashboard method of the perf_uploader.py
    def _send_to_dashboard(self, data_obj):
        """Send formatted perf data to the perf dashboard.

        @param data_obj: data object as returned by _format_data_for_upload().

        @raises PerfUploadingError if an exception was raised when uploading.
        """
        logging.debug('Data_obj to be uploaded: %s', data_obj)
        encoded = urllib.urlencode(data_obj)
        req = urllib2.Request(DASHBOARD_UPLOAD_URL, encoded)
        try:
            urllib2.urlopen(req)
        except urllib2.HTTPError as e:
            raise PerfUploadingError('HTTPError: %d %s for JSON %s\n' %
                                     (e.code, e.msg, data_obj['data']))
        except urllib2.URLError as e:
            raise PerfUploadingError('URLError: %s for JSON %s\n' %
                                     (str(e.reason), data_obj['data']))
        except httplib.HTTPException:
            raise PerfUploadingError('HTTPException for JSON %s\n' %
                                     data_obj['data'])


    def _append_to_aggregated_file(self, ts_file, ag_file):
        """Append contents of perf timestamp file to perf aggregated file.

        @param ts_file: file handle for performance timestamped file.
        @param ag_file: file handle for performance aggregated file.
        """
        next(ts_file)  # Skip fist line (the header) of timestamped file.
        for line in ts_file:
            ag_file.write(line)


    def _copy_aggregated_to_resultsdir(self, aggregated_fpath):
        """Copy perf aggregated file to results dir for AutoTest results.

        Note: The AutoTest results default directory is located at /usr/local/
        autotest/results/default/longevity_Tracker/results

        @param aggregated_fpath: file path to Aggregated performance values.
        """
        results_fpath = os.path.join(self.resultsdir, 'perf.csv')
        shutil.copy(aggregated_fpath, results_fpath)
        logging.info('Copied %s to %s)', aggregated_fpath, results_fpath)


    def _write_perf_keyvals(self, perf_results):
        """Write perf results to keyval file for AutoTest results.

        @param perf_results: dict of attribute performance metrics.
        """
        perf_keyval = {}
        perf_keyval['cpu_usage'] = perf_results['cpu']
        perf_keyval['memory_usage'] = perf_results['mem']
        perf_keyval['temperature'] = perf_results['temp']
        self.write_perf_keyval(perf_keyval)


    def _write_perf_results(self, perf_results):
        """Write perf results to results-chart.json file for Perf Dashboard.

        @param perf_results: dict of attribute performance metrics.
        """
        cpu_metric = perf_results['cpu']
        mem_metric = perf_results['mem']
        ec_metric = perf_results['temp']
        self.output_perf_value(description='cpu_usage', value=cpu_metric,
                               units='%', higher_is_better=False)
        self.output_perf_value(description='mem_usage', value=mem_metric,
                               units='%', higher_is_better=False)
        self.output_perf_value(description='max_temp', value=ec_metric,
                               units='Celsius', higher_is_better=False)


    def _read_perf_results(self):
        """Read perf results from results-chart.json file for Perf Dashboard.

        @returns dict of perf results, formatted as JSON chart data.
        """
        results_file = os.path.join(self.resultsdir, 'results-chart.json')
        with open(results_file, 'r') as fp:
            contents = fp.read()
            chart_data = json.loads(contents)
        # TODO(krishnargv): refactor this with a better method to delete.
        open(results_file, 'w').close()
        return chart_data


    def _record_perf_measurements(self, perf_values, perf_writer):
        """Record attribute performance measurements, and write to file.

        @param perf_values: dict of attribute performance values.
        @param perf_writer: file to write performance measurements.
        """
        # Get performance measurements.
        cpu_usage = '%.3f' % self._get_cpu_usage()
        mem_usage = '%.3f' % self._get_memory_usage()
        max_temp = '%.3f' % self._get_temperature_data()

        # Append measurements to attribute lists in perf values dictionary.
        perf_values['cpu'].append(cpu_usage)
        perf_values['mem'].append(mem_usage)
        perf_values['temp'].append(max_temp)

        # Write performance measurements to perf timestamped file.
        time_stamp = time.strftime('%Y/%m/%d %H:%M:%S')
        perf_writer.writerow([time_stamp, cpu_usage, mem_usage, max_temp])
        logging.info('Time: %s, CPU: %s, Mem: %s, Temp: %s',
                     time_stamp, cpu_usage, mem_usage, max_temp)


    def _record_90th_metrics(self, perf_values, perf_metrics):
        """Record 90th percentile metric of attribute performance values.

        @param perf_values: dict attribute performance values.
        @param perf_metrics: dict attribute 90%-ile performance metrics.
        """
        # Calculate 90th percentile for each attribute.
        cpu_values = perf_values['cpu']
        mem_values = perf_values['mem']
        temp_values = perf_values['temp']
        cpu_metric = sorted(cpu_values)[(len(cpu_values) * 9) // 10]
        mem_metric = sorted(mem_values)[(len(mem_values) * 9) // 10]
        temp_metric = sorted(temp_values)[(len(temp_values) * 9) // 10]
        logging.info('Performance values: %s', perf_values)
        logging.info('90th percentile: cpu: %s, mem: %s, temp: %s',
                     cpu_metric, mem_metric, temp_metric)

        # Append 90th percentile to each attribute performance metric.
        perf_metrics['cpu'].append(cpu_metric)
        perf_metrics['mem'].append(mem_metric)
        perf_metrics['temp'].append(temp_metric)


    def _get_median_metrics(self, metrics):
        """Returns median of each attribute performance metric.

        If no metric values were recorded, return 0 for each metric.

        @param metrics: dict of attribute performance metric lists.
        @returns dict of attribute performance metric medians.
        """
        if len(metrics['cpu']):
            cpu_metric = sorted(metrics['cpu'])[len(metrics['cpu']) // 2]
            mem_metric = sorted(metrics['mem'])[len(metrics['mem']) // 2]
            temp_metric = sorted(metrics['temp'])[len(metrics['temp']) // 2]
        else:
            cpu_metric = 0
            mem_metric = 0
            temp_metric = 0
        logging.info('Median of 90th percentile: cpu: %s, mem: %s, temp: %s',
                     cpu_metric, mem_metric, temp_metric)
        return {'cpu': cpu_metric, 'mem': mem_metric, 'temp': temp_metric}


    def _setup_kiosk_app_on_dut(self, kiosk_app_attributes=None):
        """Enroll the DUT and setup a Kiosk app."""
        info = self.client.host_info_store.get()
        app_config_id = info.get_label_value('app_config_id')
        if app_config_id and app_config_id.startswith(':'):
            app_config_id = app_config_id[1:]
        if kiosk_app_attributes:
            kiosk_app_attributes = kiosk_app_attributes.rstrip()
            self.kiosk_app_name, ext_id = kiosk_app_attributes.split(':')[:2]

        tpm_utils.ClearTPMOwnerRequest(self.client)
        logging.info("Enrolling the DUT to Kiosk mode")
        autotest.Autotest(self.client).run_test(
                'enterprise_KioskEnrollment',
                kiosk_app_attributes=kiosk_app_attributes,
                check_client_result=True)

        if self.kiosk_app_name == 'riseplayer':
            self.kiosk_facade.config_rise_player(ext_id, app_config_id)


    def _run_perf_capture_cycle(self):
        """Track performance of Chrome OS over a long period of time.

        This method collects performance measurements, and calculates metrics
        to upload to the performance dashboard. It creates two files to
        collect and store performance values and results: perf_<timestamp>.csv
        and perf_aggregated.csv.

        At the start, it creates a unique perf timestamped file in the test's
        temp_dir. As the cycle runs, it saves a time-stamped performance
        value after each sample interval. Periodically, it calculates
        the 90th percentile performance metrics from these values.

        The perf_<timestamp> files on the device will survive multiple runs
        of the longevity_Tracker by the server-side test, and will also
        survive multiple runs of the server-side test.

        At the end, it opens the perf aggregated file in the test's temp_dir,
        and appends the contents of the perf timestamped file. It then
        copies the perf aggregated file to the results directory as perf.csv.
        This perf.csv file will be consumed by the AutoTest backend when the
        server-side test ends.

        Note that the perf_aggregated.csv file will grow larger with each run
        of longevity_Tracker on the device by the server-side test. However,
        the server-side test will delete file in the end.

        This method will capture perf metrics every SAMPLE_INTERVAL secs, at
        each METRIC_INTERVAL the 90 percentile of the collected metrics is
        calculated and saved. The perf capture runs for PERF_CAPTURE_DURATION
        secs. At the end of the PERF_CAPTURE_DURATION time interval the median
        value of all 90th percentile metrics is returned.

        @returns list of median performance metrics.
        """
        test_start_time = time.time()

        perf_values = {'cpu': [], 'mem': [], 'temp': []}
        perf_metrics = {'cpu': [], 'mem': [], 'temp': []}

         # Create perf_<timestamp> file and writer.
        timestamp_fname = (PERF_FILE_NAME_PREFIX +
                           time.strftime('_%Y-%m-%d_%H-%M') + '.csv')
        timestamp_fpath = os.path.join(self.temp_dir, timestamp_fname)
        timestamp_file = self._open_perf_file(timestamp_fpath)
        timestamp_writer = csv.writer(timestamp_file)

        # Align time of loop start with the sample interval.
        test_elapsed_time = self.elapsed_time(test_start_time)
        time.sleep(self.syncup_time(test_elapsed_time, SAMPLE_INTERVAL))
        test_elapsed_time = self.elapsed_time(test_start_time)

        metric_start_time = time.time()
        metric_prev_time = metric_start_time

        metric_elapsed_prev_time = self.elapsed_time(metric_prev_time)
        offset = self.modulo_time(metric_elapsed_prev_time, METRIC_INTERVAL)
        metric_timer = metric_elapsed_prev_time + offset

        while self.elapsed_time(test_start_time) <= PERF_CAPTURE_DURATION:
            self._record_perf_measurements(perf_values, timestamp_writer)

            # Periodically calculate and record 90th percentile metrics.
            metric_elapsed_prev_time = self.elapsed_time(metric_prev_time)
            metric_timer = metric_elapsed_prev_time + offset
            if metric_timer >= METRIC_INTERVAL:
                self._record_90th_metrics(perf_values, perf_metrics)
                perf_values = {'cpu': [], 'mem': [], 'temp': []}

            # Set previous time to current time.
                metric_prev_time = time.time()
                metric_elapsed_prev_time = self.elapsed_time(metric_prev_time)

                metric_elapsed_time = self.elapsed_time(metric_start_time)
                offset = self.modulo_time(metric_elapsed_time, METRIC_INTERVAL)

                # Set the timer to time elapsed plus offset to next interval.
                metric_timer = metric_elapsed_prev_time + offset

            # Sync the loop time to the sample interval.
            test_elapsed_time = self.elapsed_time(test_start_time)
            time.sleep(self.syncup_time(test_elapsed_time, SAMPLE_INTERVAL))

        # Close perf timestamp file.
        timestamp_file.close()

         # Open perf timestamp file to read, and aggregated file to append.
        timestamp_file = open(timestamp_fpath, 'r')
        aggregated_fname = (PERF_FILE_NAME_PREFIX + '_aggregated.csv')
        aggregated_fpath = os.path.join(self.temp_dir, aggregated_fname)
        aggregated_file = self._open_perf_file(aggregated_fpath)

         # Append contents of perf timestamp file to perf aggregated file.
        self._append_to_aggregated_file(timestamp_file, aggregated_file)
        timestamp_file.close()
        aggregated_file.close()

        # Copy perf aggregated file to test results directory.
        self._copy_aggregated_to_resultsdir(aggregated_fpath)

        # Return median of each attribute performance metric.
        logging.info("Perf_metrics: %r ", perf_metrics)
        return self._get_median_metrics(perf_metrics)


    def run_once(self, host=None, kiosk_app_attributes=None):
        self.client = host
        self.kiosk_app_name = None

        factory = remote_facade_factory.RemoteFacadeFactory(
                host, no_chrome=True)
        self.system_facade = factory.create_system_facade()
        self.kiosk_facade = factory.create_kiosk_facade()

        self._setup_kiosk_app_on_dut(kiosk_app_attributes)
        time.sleep(STABILIZATION_DURATION)
        self._initialize_test_variables()

        self.perf_results = {'cpu': '0', 'mem': '0', 'temp': '0'}
        for iteration in range(PERF_CAPTURE_ITERATIONS):
            #TODO(krishnargv@): Add a method to verify that the Kiosk app is
            #                   active and is running on the DUT.
            logging.info("Running perf_capture Iteration: %d", iteration+1)
            self.perf_results = self._run_perf_capture_cycle()
            self._write_perf_keyvals(self.perf_results)
            self._write_perf_results(self.perf_results)

            # Post perf results directly to performance dashboard. You may view
            # uploaded data at https://chromeperf.appspot.com/new_points,
            # with test path pattern=ChromeOS_Enterprise/cros-*/longevity*/*
            chart_data = self._read_perf_results()
            data_obj = self._format_data_for_upload(chart_data)
            self._send_to_dashboard(data_obj)
        tpm_utils.ClearTPMOwnerRequest(self.client)
