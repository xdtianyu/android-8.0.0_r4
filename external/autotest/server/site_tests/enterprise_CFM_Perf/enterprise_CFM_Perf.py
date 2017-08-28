# Copyright (c) 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import csv, datetime, glob, math, os, re, time

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import tpm_utils
from autotest_lib.server import test
from autotest_lib.server.cros import cfm_jmidata_log_collector
from autotest_lib.server.cros.multimedia import remote_facade_factory

_SHORT_TIMEOUT = 5
_MEASUREMENT_DURATION_SECONDS = 10
_TOTAL_TEST_DURATION_SECONDS = 900
_PERF_RESULT_FILE = 'perf.csv'

_BASE_DIR = '/home/chronos/user/Storage/ext/'
_EXT_ID = 'ikfcpmgefdpheiiomgmhlmmkihchmdlj'
_JMI_DIR = '/0*/File\ System/000/t/00/*'
_JMI_SOURCE_DIR = _BASE_DIR + _EXT_ID + _JMI_DIR


class enterprise_CFM_Perf(test.test):
    """This is a server test which clears device TPM and runs
    enterprise_RemoraRequisition client test to enroll the device in to hotrod
    mode. After enrollment is successful, it collects and logs cpu, memory and
    temperature data from the device under test."""
    version = 1


    def _cpu_usage(self):
        """Returns cpu usage in %."""
        cpu_usage_start = self.system_facade.get_cpu_usage()
        time.sleep(_MEASUREMENT_DURATION_SECONDS)
        cpu_usage_end = self.system_facade.get_cpu_usage()
        return self.system_facade.compute_active_cpu_time(cpu_usage_start,
                cpu_usage_end) * 100


    def _memory_usage(self):
        """Returns total used memory in %."""
        total_memory = self.system_facade.get_mem_total()
        return ((total_memory - self.system_facade.get_mem_free())
                * 100 / total_memory)


    def _temperature_data(self):
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


    def enroll_device_and_start_hangout(self):
        """Enroll device into CFM and start hangout session."""
        current_date = datetime.datetime.now().strftime("%Y-%m-%d")
        hangout_name = current_date + '-cfm-perf'

        self.cfm_facade.enroll_device()
        self.cfm_facade.restart_chrome_for_cfm()
        self.cfm_facade.wait_for_telemetry_commands()

        if not self.cfm_facade.is_oobe_start_page():
            self.cfm_facade.wait_for_oobe_start_page()

        self.cfm_facade.skip_oobe_screen()
        self.cfm_facade.start_new_hangout_session(hangout_name)


    def collect_perf_data(self):
        """Use system facade to collect performance data from the DUT using
        xmlrpc and save it to csv file in results directory. Data collected
        includes:
                1. CPU usage
                2. Memory usage
                3. Thermal temperature
                4. Timestamp
                5. Board name
                6. Build id
        """
        start_time = time.time()
        perf_keyval = {}
        cpu_usage_list = list()
        memory_usage_list = list()
        temperature_list = list()
        board_name = self.system_facade.get_current_board()
        build_id = self.system_facade.get_chromeos_release_version()
        perf_file = open(os.path.join(self.resultsdir, _PERF_RESULT_FILE), 'w')
        writer = csv.writer(perf_file)
        writer.writerow(['cpu', 'memory', 'temperature', 'timestamp', 'board',
                         'build'])
        while (time.time() - start_time) < _TOTAL_TEST_DURATION_SECONDS:
            perf_keyval['cpu_usage'] = self._cpu_usage()
            perf_keyval['memory_usage'] = self._memory_usage()
            perf_keyval['temperature'] = self._temperature_data()
            writer.writerow([perf_keyval['cpu_usage'],
                             perf_keyval['memory_usage'],
                             perf_keyval['temperature'],
                             time.strftime('%Y/%m/%d %H:%M:%S'),
                             board_name,
                             build_id])
            self.write_perf_keyval(perf_keyval)
            cpu_usage_list.append(perf_keyval['cpu_usage'])
            memory_usage_list.append(perf_keyval['memory_usage'])
            temperature_list.append(perf_keyval['temperature'])
            time.sleep(_MEASUREMENT_DURATION_SECONDS)
        perf_file.close()
        utils.write_keyval(os.path.join(self.resultsdir, os.pardir),
                           {'perf_csv_folder': self.resultsdir})
        self.upload_perf_data(cpu_usage_list,
                              memory_usage_list,
                              temperature_list)


    def upload_perf_data(self, cpu_usage, memory_usage, temperature):
        """Write perf results to results-chart.json file for Perf Dashboard.

        @param cpu_usage: list of cpu usage values
        @param memory_usage: list of memory usage values
        @param temperature: list of temperature values
        """
        avg_cpu_usage = sum(cpu_usage)/len(cpu_usage)
        avg_memory_usage = sum(memory_usage)/len(memory_usage)
        avg_temp = sum(temperature)/len(temperature)

        peak_cpu_usage = max(cpu_usage)
        peak_memory_usage = max(memory_usage)
        peak_temp = max(temperature)

        self.output_perf_value(description='average_cpu_usage',
                value=avg_cpu_usage, units='percent', higher_is_better=False)
        self.output_perf_value(description='average_memory_usage',
                value=avg_memory_usage, units='percent', higher_is_better=False)
        self.output_perf_value(description='average_temperature',
                value=avg_temp, units='Celsius', higher_is_better=False)

        self.output_perf_value(description='peak_cpu_usage',
                value=peak_cpu_usage, units='percent', higher_is_better=False)
        self.output_perf_value(description='peak_memory_usage',
                value=peak_memory_usage, units='percent',
                higher_is_better=False)
        self.output_perf_value(description='peak_temperature',
                value=peak_temp, units='Celsius', higher_is_better=False)


    def _get_average(self, data_type, jmidata):
        """Computes mean of a list of numbers.

        @param data_type: Type of data to be retrieved from jmi data log.
        @param jmidata: Raw jmi data log to parse.
        @return Mean computed from the list of numbers.
        """
        data = self._get_data_from_jmifile(data_type, jmidata)
        if not data:
            return 0
        return float(sum(data)) / len(data)


    def _get_std_dev(self, data_type, jmidata):
        """Computes standard deviation of a list of numbers.

        @param data_type: Type of data to be retrieved from jmi data log.
        @param jmidata: Raw jmi data log to parse.
        @return Standard deviation computed from the list of numbers.
        """
        data = self._get_data_from_jmifile(data_type, jmidata)
        n = len(data)
        if not data or n == 1:
            return 0
        mean = float(sum(data)) / n
        variance = sum([(elem - mean) ** 2 for elem in data]) / (n -1)
        return math.sqrt(variance)


    def _get_max_value(self, data_type, jmidata):
        """Computes maximum value of a list of numbers.

        @param data_type: Type of data to be retrieved from jmi data log.
        @param jmidata: Raw jmi data log to parse.
        @return Maxium value from the list of numbers.
        """
        data = self._get_data_from_jmifile(data_type, jmidata)
        if not data:
            return 0
        return max(data)


    def _get_sum(self, data_type, jmidata):
        """Computes sum of a list of numbers.

        @param data_type: Type of data to be retrieved from jmi data log.
        @param jmidata: Raw jmi data log to parse.
        @return Sum computed from the list of numbers.
        """
        data = self._get_data_from_jmifile(data_type, jmidata)
        if not data:
            return 0
        return sum(data)


    def _get_last_value(self, data_type, jmidata):
        """Gets last value of a list of numbers.

        @param data_type: Type of data to be retrieved from jmi data log.
        @param jmidata: Raw jmi data log to parse.
        @return Mean computed from the list of numbers.
        """
        data = self._get_data_from_jmifile(data_type, jmidata)
        if not data:
            return 0
        return data[-1]


    def _get_data_from_jmifile(self, data_type, jmidata):
        """Gets data from jmidata log for given data type.

        @param data_type: Type of data to be retrieved from jmi data log.
        @param jmidata: Raw jmi data log to parse.
        @return Data for given data type from jmidata log.
        """
        return cfm_jmidata_log_collector.GetDataFromLogs(
                self, data_type, jmidata)


    def _get_file_to_parse(self):
        """Copy jmi logs from client to test's results directory.

        @return The newest jmi log file.
        """
        self.client.get_file(_JMI_SOURCE_DIR, self.resultsdir)
        source_jmi_files = self.resultsdir + '/0*'
        if not source_jmi_files:
            raise error.TestNAError('JMI data file not found.')
        newest_file = max(glob.iglob(source_jmi_files), key=os.path.getctime)
        return newest_file


    def upload_jmidata(self):
        """Write jmidata results to results-chart.json file for Perf Dashboard.
        """
        jmi_file = self._get_file_to_parse()
        jmifile_to_parse = open(jmi_file, 'r')
        jmidata = jmifile_to_parse.read()

        self.output_perf_value(description='sum_vid_in_frames_decoded',
                value=self._get_sum('frames_decoded', jmidata), units='frames',
                higher_is_better=True)

        self.output_perf_value(description='sum_vid_out_frames_encoded',
                value=self._get_sum('frames_encoded', jmidata), units='frames',
                higher_is_better=True)

        self.output_perf_value(description='vid_out_adapt_changes',
                value=self._get_last_value('adaptation_changes', jmidata),
                units='count', higher_is_better=False)

        self.output_perf_value(description='avg_video_out_encode_time',
                value=self._get_average('average_encode_time', jmidata),
                units='ms', higher_is_better=False)

        self.output_perf_value(description='std_dev_video_out_encode_time',
                value=self._get_std_dev('average_encode_time', jmidata),
                units='ms', higher_is_better=False)

        self.output_perf_value(description='max_video_out_encode_time',
                value=self._get_max_value('average_encode_time', jmidata),
                units='ms', higher_is_better=False)

        self.output_perf_value(description='vid_out_bandwidth_adapt',
                value=self._get_average('bandwidth_adaptation', jmidata),
                units='bool', higher_is_better=False)

        self.output_perf_value(description='vid_out_cpu_adapt',
                value=self._get_average('cpu_adaptation', jmidata),
                units='bool', higher_is_better=False)

        self.output_perf_value(description='avg_video_in_res',
                value=self._get_average('video_received_frame_height', jmidata),
                units='resolution', higher_is_better=True)

        self.output_perf_value(description='avg_video_out_res',
                value=self._get_average('video_sent_frame_height', jmidata),
                units='resolution', higher_is_better=True)

        self.output_perf_value(description='std_dev_video_out_res',
                value=self._get_std_dev('video_sent_frame_height', jmidata),
                units='resolution', higher_is_better=True)

        self.output_perf_value(description='avg_vid_in_framerate_decoded',
                value=self._get_average('framerate_decoded', jmidata),
                units='fps', higher_is_better=True)

        self.output_perf_value(description='avg_vid_out_framerate_input',
                value=self._get_average('framerate_outgoing', jmidata),
                units='fps', higher_is_better=True)

        self.output_perf_value(description='std_dev_vid_out_framerate_input',
                value=self._get_std_dev('framerate_outgoing', jmidata),
                units='fps', higher_is_better=True)

        self.output_perf_value(description='avg_vid_in_framerate_to_renderer',
                value=self._get_average('framerate_to_renderer', jmidata),
                units='fps', higher_is_better=True)

        self.output_perf_value(description='avg_vid_in_framerate_received',
                value=self._get_average('framerate_received', jmidata),
                units='fps', higher_is_better=True)

        self.output_perf_value(description='avg_vid_out_framerate_sent',
                value=self._get_average('framerate_sent', jmidata),
                units='fps', higher_is_better=True)

        self.output_perf_value(description='std_dev_vid_out_framerate_sent',
                value=self._get_std_dev('framerate_sent', jmidata),
                units='fps', higher_is_better=True)

        self.output_perf_value(description='avg_vid_in_frame_width',
                value=self._get_average('video_received_frame_width', jmidata),
                units='fps', higher_is_better=True)

        self.output_perf_value(description='avg_vid_out_frame_width',
                value=self._get_average('video_sent_frame_width', jmidata),
                units='fps', higher_is_better=True)

        self.output_perf_value(description='avg_vid_out_encode_cpu_usage',
                value=self._get_average('video_encode_cpu_usage', jmidata),
                units='percent', higher_is_better=False)

        total_vid_packets_sent = self._get_sum('video_packets_sent', jmidata)
        total_vid_packets_lost = self._get_sum('video_packets_lost', jmidata)
        lost_packet_percentage = float(total_vid_packets_lost)*100/ \
                                 float(total_vid_packets_sent) if \
                                 total_vid_packets_sent else 0

        self.output_perf_value(description='lost_packet_percentage',
                value=lost_packet_percentage, units='percent',
                higher_is_better=False)

        num_processors = self._get_data_from_jmifile('cpu_processors', jmidata)
        total_cpu = self._get_average('cpu_percent', jmidata)
        render_cpu = self._get_average('renderer_cpu_percent', jmidata)

        cpu_percentage = total_cpu/num_processors if num_processors else 0
        render_cpu_percent = render_cpu/num_processors if num_processors else 0

        self.output_perf_value(description='avg_cpu_usage_jmi',
                value=cpu_percentage,
                units='percent', higher_is_better=False)

        self.output_perf_value(description='avg_renderer_cpu_usage',
                value=render_cpu_percent,
                units='percent', higher_is_better=False)

        self.output_perf_value(description='avg_browser_cpu_usage',
                value=self._get_average('browser_cpu_percent', jmidata),
                units='percent', higher_is_better=False)

        self.output_perf_value(description='avg_gpu_cpu_usage',
                value=self._get_average('gpu_cpu_percent', jmidata),
                units='percent', higher_is_better=False)

        self.output_perf_value(description='avg_active_streams',
                value=self._get_average('num_active_vid_in_streams', jmidata),
                units='count', higher_is_better=True)

        self.output_perf_value(description='std_dev_active_streams',
                value=self._get_std_dev('num_active_vid_in_streams', jmidata),
                units='count', higher_is_better=True)


    def run_once(self, host=None):
        self.client = host

        factory = remote_facade_factory.RemoteFacadeFactory(
                host, no_chrome=True)
        self.system_facade = factory.create_system_facade()
        self.cfm_facade = factory.create_cfm_facade()

        tpm_utils.ClearTPMOwnerRequest(self.client)

        if self.client.servo:
            self.client.servo.switch_usbkey('dut')
            self.client.servo.set('usb_mux_sel3', 'dut_sees_usbkey')
            time.sleep(_SHORT_TIMEOUT)
            self.client.servo.set('dut_hub1_rst1', 'off')
            time.sleep(_SHORT_TIMEOUT)

        try:
            self.enroll_device_and_start_hangout()
            self.collect_perf_data()
            self.cfm_facade.end_hangout_session()
            self.upload_jmidata()
        except Exception as e:
            raise error.TestFail(str(e))

        tpm_utils.ClearTPMOwnerRequest(self.client)
