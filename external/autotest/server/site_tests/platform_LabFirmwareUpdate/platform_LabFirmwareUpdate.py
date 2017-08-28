# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server import autotest
from autotest_lib.server import test
from autotest_lib.server.cros.faft.rpc_proxy import RPCProxy

class platform_LabFirmwareUpdate(test.test):
    """For test or lab devices.  Test will fail if Software write protection
       is enabled.  Test will compare the installed firmware to those in
       the shellball.  If differ, execute chromeos-firmwareupdate
       --mode=recovery to reset RO and RW firmware. Basic procedure are:

       - check software write protect, if enable, attemp reset.
       - fail test if software write protect is enabled.
       - check if [ec, pd] is available on DUT.
       - get RO, RW versions of firmware, if RO != RW, update=True
       - get shellball versions of firmware
       - compare shellball version to DUT, update=True if shellball != DUT.
       - run chromeos-firwmareupdate --mode=recovery if update==True
       - reboot
    """
    version = 1

    def initialize(self, host):
        self.host = host
        # Make sure the client library is on the device so that the proxy
        # code is there when we try to call it.
        client_at = autotest.Autotest(self.host)
        client_at.install()
        self.faft_client = RPCProxy(self.host)

        # Check if EC, PD is available.
        # Check if DUT software write protect is disabled, failed otherwise.
        self._run_cmd('flashrom -p host --wp-status', checkfor='is disabled')
        self.has_ec = False
        self.has_pd = False
        mosys_output = self._run_cmd('mosys')
        if 'EC information' in mosys_output:
            self.has_ec = True
            self._run_cmd('flashrom -p ec --wp-status', checkfor='is disabled')
        if 'PD information' in mosys_output:
            self.has_pd = True
            self._run_cmd('flashrom -p ec:dev=1 --wp-status',
                         checkfor='is disabled')

    def _run_cmd(self, command, checkfor=''):
        """Run command on dut and return output.
           Optionally check output contain string 'checkfor'.
        """
        logging.info('Execute: %s', command)
        output = self.host.run(command, ignore_status=True).stdout
        logging.info('Output: %s', output.split('\n'))
        if checkfor and checkfor not in ''.join(output):
            raise error.TestFail('Expect %s in output of %s' %
                                 (checkfor, ' '.join(output)))
        return output

    def _get_version(self, pd=False):
        """Retrive RO, RW EC/PD version."""
        ro = None
        rw = None
        opt_arg = ''
        if pd: opt_arg = '--dev=1'
        lines = self._run_cmd('/usr/sbin/ectool %s version' % opt_arg)
        for line in lines.splitlines():
            if line.startswith('RO version:'):
                parts = line.split()
                ro = parts[2].strip()
            if line.startswith('RW version:'):
                parts = line.split()
                rw = parts[2].strip()
        return (ro, rw)

    def _bios_version(self):
        """Retrive RO, RW BIOS version."""
        ro = self.faft_client.system.get_crossystem_value('ro_fwid')
        rw = self.faft_client.system.get_crossystem_value('fwid')
        return (ro, rw)

    def _get_version_all(self):
        """Retrive BIOS, EC, and PD firmware version.

        @return firmware version tuple (bios, ec, pd)
        """
        pd_version = None
        ec_version = None
        bios_version = None
        if self.has_ec:
            (ec_ro, ec_rw) = self._get_version()
            if ec_ro == ec_rw:
                ec_version = ec_rw
            else:
                ec_version = '%s,%s' % (ec_ro, ec_rw)
            logging.info('Installed EC version: %s', ec_version)
        if self.has_pd:
            (pd_ro, pd_rw) = self._get_version(pd=True)
            if pd_ro == pd_rw:
                pd_version = pd_rw
            else:
                pd_version = '%s,%s' % (pd_ro, pd_rw)
            logging.info('Installed PD version: %s', pd_version)
        (bios_ro, bios_rw) = self._bios_version()
        if bios_ro == bios_rw:
            bios_version = bios_rw
        else:
            bios_version = '%s,%s' % (bios_ro, bios_rw)
        logging.info('Installed BIOS version: %s', bios_version)
        return (bios_version, ec_version, pd_version)

    def _get_shellball_version(self):
        """Get shellball firmware version.

        @return shellball firmware version tuple (bios, ec, pd)
        """
        ec = None
        bios = None
        pd = None
        shellball = self._run_cmd('/usr/sbin/chromeos-firmwareupdate -V')
        for line in shellball.splitlines():
            if line.startswith('BIOS version:'):
                parts = line.split()
                bios = parts[2].strip()
                logging.info('shellball bios %s', bios)
            elif line.startswith('EC version:'):
                parts = line.split()
                ec = parts[2].strip()
                logging.info('shellball ec %s', ec)
            elif line.startswith('PD version:'):
                parts = line.split()
                pd = parts[2].strip()
                logging.info('shellball pd %s', pd)
        return (bios, ec, pd)

    def run_once(self, replace=True):
        # Get DUT installed firmware versions.
        (installed_bios, installed_ec, installed_pd) = self._get_version_all()

        # Get shellball firmware versions.
        (shball_bios, shball_ec, shball_pd) = self._get_shellball_version()

        # Figure out if update is needed.
        need_update = False
        if installed_bios != shball_bios:
            need_update = True
            logging.info('BIOS mismatch %s, will update to %s',
                         installed_bios, shball_bios)
        if installed_ec and installed_ec != shball_ec:
            need_update = True
            logging.info('EC mismatch %s, will update to %s',
                         installed_ec, shball_ec)
        if installed_pd != shball_pd:
            need_update = True
            logging.info('PD mismatch %s, will update to %s',
                         installed_pd, shball_pd)

        # Update and reboot if needed.
        if need_update:
            output = self._run_cmd('/usr/sbin/chromeos-firmwareupdate '
                                   ' --mode=recovery', '(recovery) completed.')
            self.host.reboot()
            # Check that installed firmware match the shellball.
            (bios, ec, pd) = self._get_version_all()
            if (bios != shball_bios or ec != shball_ec or pd != shball_pd):
                logging.info('shball bios/ec/pd: %s/%s/%s',
                             shball_bios, shball_ec, shball_pd)
                logging.info('installed bios/ec/pd: %s/%s/%s', bios, ec, pd)
                raise error.TestFail('Version mismatch after firmware update')
            logging.info('*** Done firmware updated to match shellball. ***')
        else:
            logging.info('*** No firmware update is needed. ***')

