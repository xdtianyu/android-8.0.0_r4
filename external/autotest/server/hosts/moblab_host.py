# Copyright (c) 2014 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import os
import re
import time

import common
from autotest_lib.client.common_lib import error, global_config
from autotest_lib.client.common_lib.cros import retry
from autotest_lib.server.cros.dynamic_suite import frontend_wrappers
from autotest_lib.server.hosts import cros_host
from autotest_lib.server.hosts import cros_repair


AUTOTEST_INSTALL_DIR = global_config.global_config.get_config_value(
        'SCHEDULER', 'drone_installation_directory')

ENABLE_SSH_TUNNEL_FOR_MOBLAB = global_config.global_config.get_config_value(
        'CROS', 'enable_ssh_tunnel_for_moblab', type=bool, default=False)

#'/usr/local/autotest'
SHADOW_CONFIG_PATH = '%s/shadow_config.ini' % AUTOTEST_INSTALL_DIR
ATEST_PATH = '%s/cli/atest' % AUTOTEST_INSTALL_DIR
SUBNET_DUT_SEARCH_RE = (
        r'/?.*\((?P<ip>192.168.231.*)\) at '
        '(?P<mac>[0-9a-fA-F][0-9a-fA-F]:){5}([0-9a-fA-F][0-9a-fA-F])')
MOBLAB_IMAGE_STORAGE = '/mnt/moblab/static'
MOBLAB_HOME = '/home/moblab'
MOBLAB_BOTO_LOCATION = '%s/.boto' % MOBLAB_HOME
MOBLAB_LAUNCH_CONTROL_KEY_LOCATION = '%s/.launch_control_key' % MOBLAB_HOME
MOBLAB_SERVICE_ACCOUNT_LOCATION = '%s/.service_account.json' % MOBLAB_HOME
MOBLAB_AUTODIR = '/usr/local/autodir'
DHCPD_LEASE_FILE = '/var/lib/dhcp/dhcpd.leases'
MOBLAB_SERVICES = ['moblab-scheduler-init',
                   'moblab-database-init',
                   'moblab-devserver-init',
                   'moblab-gsoffloader-init',
                   'moblab-gsoffloader_s-init']
MOBLAB_PROCESSES = ['apache2', 'dhcpd']
DUT_VERIFY_SLEEP_SECS = 5
DUT_VERIFY_TIMEOUT = 15 * 60
MOBLAB_TMP_DIR = '/mnt/moblab/tmp'
MOBLAB_PORT = 80


class MoblabHost(cros_host.CrosHost):
    """Moblab specific host class."""


    def _initialize_frontend_rpcs(self, timeout_min):
        """Initialize frontends for AFE and TKO for a moblab host.

        AFE and TKO are initialized differently based on |_use_tunnel|,
        which indicates that whether to use ssh tunnel to connect to moblab.

        @param timeout_min: The timeout minuties for AFE services.
        """
        if self._use_tunnel:
            self.web_address = self.rpc_server_tracker.tunnel_connect(
                    MOBLAB_PORT)
        # Pass timeout_min to self.afe
        self.afe = frontend_wrappers.RetryingAFE(timeout_min=timeout_min,
                                                 user='moblab',
                                                 server=self.web_address)
        # Use default timeout_min of MoblabHost for self.tko
        self.tko = frontend_wrappers.RetryingTKO(timeout_min=self.timeout_min,
                                                 user='moblab',
                                                 server=self.web_address)


    def _initialize(self, *args, **dargs):
        super(MoblabHost, self)._initialize(*args, **dargs)
        # TODO(jrbarnette):  Our superclass already initialized
        # _repair_strategy, and now we're re-initializing it here.
        # That's awkward, if not actually wrong.
        self._repair_strategy = cros_repair.create_moblab_repair_strategy()

        # Clear the Moblab Image Storage so that staging an image is properly
        # tested.
        if dargs.get('retain_image_storage') is not True:
            self.run('rm -rf %s/*' % MOBLAB_IMAGE_STORAGE)
        self.web_address = dargs.get('web_address', self.hostname)
        self._use_tunnel = (ENABLE_SSH_TUNNEL_FOR_MOBLAB and
                            self.web_address == self.hostname)
        self.timeout_min = dargs.get('rpc_timeout_min', 1)
        self._initialize_frontend_rpcs(self.timeout_min)


    @staticmethod
    def check_host(host, timeout=10):
        """
        Check if the given host is an moblab host.

        @param host: An ssh host representing a device.
        @param timeout: The timeout for the run command.


        @return: True if the host device has adb.

        @raises AutoservRunError: If the command failed.
        @raises AutoservSSHTimeout: Ssh connection has timed out.
        """
        try:
            result = host.run(
                    'grep -q moblab /etc/lsb-release && '
                    '! test -f /mnt/stateful_partition/.android_tester',
                    ignore_status=True, timeout=timeout)
        except (error.AutoservRunError, error.AutoservSSHTimeout):
            return False
        return result.exit_status == 0


    def install_boto_file(self, boto_path=''):
        """Install a boto file on the Moblab device.

        @param boto_path: Path to the boto file to install. If None, sends the
                          boto file in the current HOME directory.

        @raises error.TestError if the boto file does not exist.
        """
        if not boto_path:
            boto_path = os.path.join(os.getenv('HOME'), '.boto')
        if not os.path.exists(boto_path):
            raise error.TestError('Boto File:%s does not exist.' % boto_path)
        self.send_file(boto_path, MOBLAB_BOTO_LOCATION)
        self.run('chown moblab:moblab %s' % MOBLAB_BOTO_LOCATION)


    def get_autodir(self):
        """Return the directory to install autotest for client side tests."""
        return self.autodir or MOBLAB_AUTODIR


    def run_as_moblab(self, command, **kwargs):
        """Moblab commands should be ran as the moblab user not root.

        @param command: Command to run as user moblab.
        """
        command = "su - moblab -c '%s'" % command
        return self.run(command, **kwargs)


    def reboot(self, **dargs):
        """Reboot the Moblab Host and wait for its services to restart."""
        super(MoblabHost, self).reboot(**dargs)
        # In general after a reboot, we want to wait till the web frontend
        # and other Autotest services are up before executing. However should
        # something be wrong with these services, repair needs to be able
        # to continue and reimage the device.
        try:
            self.wait_afe_up()
        except Exception as e:
            logging.error('DUT has rebooted but AFE has failed to load.: %s',
                          e)


    def wait_afe_up(self, timeout_min=5):
        """Wait till the AFE is up and loaded.

        Attempt to reach the Moblab's AFE and database through its RPC
        interface.

        @param timeout_min: Minutes to wait for the AFE to respond. Default is
                            5 minutes.

        @raises urllib2.HTTPError if AFE does not respond within the timeout.
        """
        # Use moblabhost's own AFE object with a longer timeout to wait for the
        # AFE to load. Also re-create the ssh tunnel for connections to moblab.
        # Set the timeout_min to be longer than self.timeout_min for rebooting.
        self._initialize_frontend_rpcs(timeout_min)
        # Verify the AFE can handle a simple request.
        self._check_afe()
        # Reset the timeout_min after rebooting checks for afe services.
        self.afe.set_timeout(self.timeout_min)


    def _wake_devices(self):
        """Search the subnet and attempt to ping any available duts.

        Fills up the arp table with entries about devices on the subnet.

        Either uses fping or directly pings devices listed in the dhcpd lease
        file.
        """
        fping_result = self.run('fping -g 192.168.231.100 192.168.231.120',
                                ignore_status=True)
        # If fping is not on the system, ping entries in the dhcpd lease file.
        if fping_result.exit_status == 127:
            leases = set(self.run('grep ^lease %s' % DHCPD_LEASE_FILE,
                                  ignore_status=True).stdout.splitlines())
            for lease in leases:
                ip = re.match('lease (?P<ip>.*) {', lease).groups('ip')
                self.run('ping %s -w 1' % ip, ignore_status=True)


    def add_dut(self, hostname):
        """Add a DUT hostname to the AFE.

        @param hostname: DUT hostname to add.
        """
        result = self.run_as_moblab('%s host create %s' % (ATEST_PATH,
                                                           hostname))
        logging.debug('atest host create output for host %s:\n%s',
                      hostname, result.stdout)


    def find_and_add_duts(self):
        """Discover DUTs on the testing subnet and add them to the AFE.

        Runs 'arp -a' on the Moblab host and parses the output to discover DUTs
        and if they are not already in the AFE, adds them.
        """
        self._wake_devices()
        existing_hosts = [host.hostname for host in self.afe.get_hosts()]
        arp_command = self.run('arp -a')
        for line in arp_command.stdout.splitlines():
            match = re.match(SUBNET_DUT_SEARCH_RE, line)
            if match:
                dut_hostname = match.group('ip')
                if dut_hostname in existing_hosts:
                    break
                self.add_dut(dut_hostname)


    def verify_software(self):
        """Verify working software on a Chrome OS system.

        Tests for the following conditions:
         1. All conditions tested by the parent version of this
            function.
         2. Ensures that Moblab services are running.
         3. Ensures that both DUTs successfully run Verify.

        """
        # In case cleanup or powerwash wiped the autodir, create an empty
        # directory.
        self.run('mkdir -p %s' % MOBLAB_AUTODIR)
        super(MoblabHost, self).verify_software()
        self._verify_moblab_services()
        self._verify_duts()


    @retry.retry(error.AutoservError, timeout_min=2, delay_sec=10)
    def _verify_upstart_service(self, service):
        """Retry to verify the required moblab services are up and running.

        Regarding crbug.com/649811, moblab services takes longer to restart
        under the new provision framework. This is a fix to retry the service
        check until all services are successfully restarted.

        @param service: the moblab upstart service.

        @return True if this service is started and running, otherwise False.
        """
        return self.upstart_status(service)


    def _verify_moblab_services(self):
        """Verify the required Moblab services are up and running.

        @raises AutoservError if any moblab service is not running.
        """
        for service in MOBLAB_SERVICES:
            if not self._verify_upstart_service(service):
                raise error.AutoservError('Moblab service: %s is not running.'
                                          % service)
        for process in MOBLAB_PROCESSES:
            try:
                self.run('pgrep %s' % process)
            except error.AutoservRunError:
                raise error.AutoservError('Moblab process: %s is not running.'
                                          % process)


    def _check_afe(self):
        """Verify whether afe of moblab works before verify its DUTs.

        Verifying moblab sometimes happens after a successful provision, in
        which case moblab is restarted but tunnel of afe is not re-connected.
        This func is used to check whether afe is working now.

        @return True if afe works, otherwise, raise urllib2.HTTPError.
        """
        try:
            self.afe.get_hosts()
        except:
            logging.debug('AFE is not responding')
            raise

        return True


    def _verify_duts(self):
        """Verify the Moblab DUTs are up and running.

        @raises AutoservError if no DUTs are in the Ready State.
        """
        # Check whether afe is well connected, if not, restart it.
        try:
            self._check_afe()
        except:
            self.wait_afe_up()

        # Add the DUTs if they have not yet been added.
        self.find_and_add_duts()
        # Ensure a boto file is installed in case this Moblab was wiped in
        # repair.
        self.install_boto_file()
        hosts = self.afe.reverify_hosts()
        logging.debug('DUTs scheduled for reverification: %s', hosts)
        # Wait till all pending special tasks are completed.
        total_time = 0
        while (self.afe.get_special_tasks(is_complete=False) and
               total_time < DUT_VERIFY_TIMEOUT):
            total_time = total_time + DUT_VERIFY_SLEEP_SECS
            time.sleep(DUT_VERIFY_SLEEP_SECS)
        if not self.afe.get_hosts(status='Ready'):
            for host in self.afe.get_hosts():
                logging.error('DUT: %s Status: %s', host, host.status)
            raise error.AutoservError('Moblab has 0 Ready DUTs')


    def get_platform(self):
        """Determine the correct platform label for this host.

        For Moblab devices '_moblab' is appended.

        @returns a string representing this host's platform.
        """
        return super(MoblabHost, self).get_platform() + '_moblab'


    def make_tmp_dir(self, base=MOBLAB_TMP_DIR):
        """Creates a temporary directory.

        @param base: The directory where it should be created.

        @return Path to a newly created temporary directory.
        """
        self.run('mkdir -p %s' % base)
        return self.run('mktemp -d -p %s' % base).stdout.strip()


    def get_os_type(self):
        return 'moblab'
