# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""This class defines the CrosHost Label class."""

import logging
import os
import re

import common

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import global_config
from autotest_lib.client.cros.audio import cras_utils
from autotest_lib.client.cros.video import constants as video_test_constants
from autotest_lib.server.cros.dynamic_suite import constants as ds_constants
from autotest_lib.server.hosts import base_label
from autotest_lib.server.hosts import common_label
from autotest_lib.server.hosts import servo_host
from autotest_lib.site_utils import hwid_lib

# pylint: disable=missing-docstring

class BoardLabel(base_label.StringPrefixLabel):
    """Determine the correct board label for the device."""

    _NAME = ds_constants.BOARD_PREFIX.rstrip(':')

    def generate_labels(self, host):
        # We only want to apply the board labels once, which is when they get
        # added to the AFE.  That way we don't have to worry about the board
        # label switching on us if the wrong builds get put on the devices.
        # crbug.com/624207 records one event of the board label switching
        # unexpectedly on us.
        for label in host._afe_host.labels:
            if label.startswith(self._NAME + ':'):
                return [label.split(':')[-1]]

        # TODO(kevcheng): for now this will dup the code in CrosHost and a
        # separate cl will refactor the get_board in CrosHost to just return the
        # board without the BOARD_PREFIX and all the other callers will be
        # updated to not need to clear it out and this code will be replaced to
        # just call the host's get_board() method.
        release_info = utils.parse_cmd_output('cat /etc/lsb-release',
                                              run_method=host.run)
        return [release_info['CHROMEOS_RELEASE_BOARD']]


class LightSensorLabel(base_label.BaseLabel):
    """Label indicating if a light sensor is detected."""

    _NAME = 'lightsensor'
    _LIGHTSENSOR_SEARCH_DIR = '/sys/bus/iio/devices'
    _LIGHTSENSOR_FILES = [
        "in_illuminance0_input",
        "in_illuminance_input",
        "in_illuminance0_raw",
        "in_illuminance_raw",
        "illuminance0_input",
    ]

    def exists(self, host):
        search_cmd = "find -L %s -maxdepth 4 | egrep '%s'" % (
            self._LIGHTSENSOR_SEARCH_DIR, '|'.join(self._LIGHTSENSOR_FILES))
        # Run the search cmd following the symlinks. Stderr_tee is set to
        # None as there can be a symlink loop, but the command will still
        # execute correctly with a few messages printed to stderr.
        result = host.run(search_cmd, stdout_tee=None, stderr_tee=None,
                          ignore_status=True)

        return result.exit_status == 0


class BluetoothLabel(base_label.BaseLabel):
    """Label indicating if bluetooth is detected."""

    _NAME = 'bluetooth'

    def exists(self, host):
        result = host.run('test -d /sys/class/bluetooth/hci0',
                          ignore_status=True)

        return result.exit_status == 0


class ECLabel(base_label.BaseLabel):
    """Label to determine the type of EC on this host."""

    _NAME = 'ec:cros'

    def exists(self, host):
        cmd = 'mosys ec info'
        # The output should look like these, so that the last field should
        # match our EC version scheme:
        #
        #   stm | stm32f100 | snow_v1.3.139-375eb9f
        #   ti | Unknown-10de | peppy_v1.5.114-5d52788
        #
        # Non-Chrome OS ECs will look like these:
        #
        #   ENE | KB932 | 00BE107A00
        #   ite | it8518 | 3.08
        #
        # And some systems don't have ECs at all (Lumpy, for example).
        regexp = r'^.*\|\s*(\S+_v\d+\.\d+\.\d+-[0-9a-f]+)\s*$'

        ecinfo = host.run(command=cmd, ignore_status=True)
        if ecinfo.exit_status == 0:
            res = re.search(regexp, ecinfo.stdout)
            if res:
                logging.info("EC version is %s", res.groups()[0])
                return True
            logging.info("%s got: %s", cmd, ecinfo.stdout)
            # Has an EC, but it's not a Chrome OS EC
        logging.info("%s exited with status %d", cmd, ecinfo.exit_status)
        return False


class AccelsLabel(base_label.BaseLabel):
    """Determine the type of accelerometers on this host."""

    _NAME = 'accel:cros-ec'

    def exists(self, host):
        # Check to make sure we have ectool
        rv = host.run('which ectool', ignore_status=True)
        if rv.exit_status:
            logging.info("No ectool cmd found; assuming no EC accelerometers")
            return False

        # Check that the EC supports the motionsense command
        rv = host.run('ectool motionsense', ignore_status=True)
        if rv.exit_status:
            logging.info("EC does not support motionsense command; "
                         "assuming no EC accelerometers")
            return False

        # Check that EC motion sensors are active
        active = host.run('ectool motionsense active').stdout.split('\n')
        if active[0] == "0":
            logging.info("Motion sense inactive; assuming no EC accelerometers")
            return False

        logging.info("EC accelerometers found")
        return True


class ChameleonLabel(base_label.BaseLabel):
    """Determine if a Chameleon is connected to this host."""

    _NAME = 'chameleon'

    def exists(self, host):
        return host._chameleon_host is not None


class ChameleonConnectionLabel(base_label.StringPrefixLabel):
    """Return the Chameleon connection label."""

    _NAME = 'chameleon'

    def exists(self, host):
        return host._chameleon_host is not None


    def generate_labels(self, host):
        return [host.chameleon.get_label()]


class ChameleonPeripheralsLabel(base_label.StringPrefixLabel):
    """Return the Chameleon peripherals labels.

    The 'chameleon:bt_hid' label is applied if the bluetooth
    classic hid device, i.e, RN-42 emulation kit, is detected.

    Any peripherals plugged into the chameleon board would be
    detected and applied proper labels in this class.
    """

    _NAME = 'chameleon'

    def exists(self, host):
        return host._chameleon_host is not None


    def generate_labels(self, host):
        bt_hid_device = host.chameleon.get_bluetooh_hid_mouse()
        return ['bt_hid'] if bt_hid_device.CheckSerialConnection() else []


class AudioLoopbackDongleLabel(base_label.BaseLabel):
    """Return the label if an audio loopback dongle is plugged in."""

    _NAME = 'audio_loopback_dongle'

    def exists(self, host):
        nodes_info = host.run(command=cras_utils.get_cras_nodes_cmd(),
                              ignore_status=True).stdout
        if (cras_utils.node_type_is_plugged('HEADPHONE', nodes_info) and
            cras_utils.node_type_is_plugged('MIC', nodes_info)):
                return True
        return False


class PowerSupplyLabel(base_label.StringPrefixLabel):
    """
    Return the label describing the power supply type.

    Labels representing this host's power supply.
         * `power:battery` when the device has a battery intended for
                extended use
         * `power:AC_primary` when the device has a battery not intended
                for extended use (for moving the machine, etc)
         * `power:AC_only` when the device has no battery at all.
    """

    _NAME = 'power'

    def __init__(self):
        self.psu_cmd_result = None


    def exists(self, host):
        self.psu_cmd_result = host.run(command='mosys psu type',
                                       ignore_status=True)
        return self.psu_cmd_result.stdout.strip() != 'unknown'


    def generate_labels(self, host):
        if self.psu_cmd_result.exit_status:
            # The psu command for mosys is not included for all platforms. The
            # assumption is that the device will have a battery if the command
            # is not found.
            return ['battery']
        return [self.psu_cmd_result.stdout.strip()]


class StorageLabel(base_label.StringPrefixLabel):
    """
    Return the label describing the storage type.

    Determine if the internal device is SCSI or dw_mmc device.
    Then check that it is SSD or HDD or eMMC or something else.

    Labels representing this host's internal device type:
             * `storage:ssd` when internal device is solid state drive
             * `storage:hdd` when internal device is hard disk drive
             * `storage:mmc` when internal device is mmc drive
             * None          When internal device is something else or
                             when we are unable to determine the type
    """

    _NAME = 'storage'

    def __init__(self):
        self.type_str = ''


    def exists(self, host):
        # The output should be /dev/mmcblk* for SD/eMMC or /dev/sd* for scsi
        rootdev_cmd = ' '.join(['. /usr/sbin/write_gpt.sh;',
                                '. /usr/share/misc/chromeos-common.sh;',
                                'load_base_vars;',
                                'get_fixed_dst_drive'])
        rootdev = host.run(command=rootdev_cmd, ignore_status=True)
        if rootdev.exit_status:
            logging.info("Fail to run %s", rootdev_cmd)
            return False
        rootdev_str = rootdev.stdout.strip()

        if not rootdev_str:
            return False

        rootdev_base = os.path.basename(rootdev_str)

        mmc_pattern = '/dev/mmcblk[0-9]'
        if re.match(mmc_pattern, rootdev_str):
            # Use type to determine if the internal device is eMMC or somthing
            # else. We can assume that MMC is always an internal device.
            type_cmd = 'cat /sys/block/%s/device/type' % rootdev_base
            type = host.run(command=type_cmd, ignore_status=True)
            if type.exit_status:
                logging.info("Fail to run %s", type_cmd)
                return False
            type_str = type.stdout.strip()

            if type_str == 'MMC':
                self.type_str = 'mmc'
                return True

        scsi_pattern = '/dev/sd[a-z]+'
        if re.match(scsi_pattern, rootdev.stdout):
            # Read symlink for /sys/block/sd* to determine if the internal
            # device is connected via ata or usb.
            link_cmd = 'readlink /sys/block/%s' % rootdev_base
            link = host.run(command=link_cmd, ignore_status=True)
            if link.exit_status:
                logging.info("Fail to run %s", link_cmd)
                return False
            link_str = link.stdout.strip()
            if 'usb' in link_str:
                return False

            # Read rotation to determine if the internal device is ssd or hdd.
            rotate_cmd = str('cat /sys/block/%s/queue/rotational'
                              % rootdev_base)
            rotate = host.run(command=rotate_cmd, ignore_status=True)
            if rotate.exit_status:
                logging.info("Fail to run %s", rotate_cmd)
                return False
            rotate_str = rotate.stdout.strip()

            rotate_dict = {'0':'ssd', '1':'hdd'}
            self.type_str = rotate_dict.get(rotate_str)
            return True

        # All other internal device / error case will always fall here
        return False


    def generate_labels(self, host):
        return [self.type_str]


class ServoLabel(base_label.BaseLabel):
    """Label to apply if a servo is present."""

    _NAME = 'servo'

    def exists(self, host):
        """
        Check if the servo label should apply to the host or not.

        @returns True if a servo host is detected, False otherwise.
        """
        servo_args, _ = servo_host._get_standard_servo_args(host)
        servo_host_hostname = servo_args.get(servo_host.SERVO_HOST_ATTR)
        return (servo_host_hostname is not None
                and servo_host.servo_host_is_up(servo_host_hostname))


class VideoLabel(base_label.StringLabel):
    """Labels detailing video capabilities."""

    # List gathered from
    # https://chromium.googlesource.com/chromiumos/
    # platform2/+/master/avtest_label_detect/main.c#19
    _NAME = [
        'hw_jpeg_acc_dec',
        'hw_video_acc_h264',
        'hw_video_acc_vp8',
        'hw_video_acc_vp9',
        'hw_video_acc_enc_h264',
        'hw_video_acc_enc_vp8',
        'webcam',
    ]

    def generate_labels(self, host):
        result = host.run('/usr/local/bin/avtest_label_detect',
                          ignore_status=True).stdout
        return re.findall('^Detected label: (\w+)$', result, re.M)


class CTSArchLabel(base_label.StringLabel):
    """Labels to determine CTS abi."""

    _NAME = ['cts_abi_arm', 'cts_abi_x86']

    def _get_cts_abis(self, host):
        """Return supported CTS ABIs.

        @return List of supported CTS bundle ABIs.
        """
        cts_abis = {'x86_64': ['arm', 'x86'], 'arm': ['arm']}
        return cts_abis.get(host.get_cpu_arch(), [])


    def generate_labels(self, host):
        return ['cts_abi_' + abi for abi in self._get_cts_abis(host)]


class ArcLabel(base_label.BaseLabel):
    """Label indicates if host has ARC support."""

    _NAME = 'arc'

    @base_label.forever_exists_decorate
    def exists(self, host):
        return 0 == host.run('grep CHROMEOS_ARC_VERSION /etc/lsb-release',
                             ignore_status=True).exit_status


class VideoGlitchLabel(base_label.BaseLabel):
    """Label indicates if host supports video glitch detection tests."""

    _NAME = 'video_glitch_detection'

    def exists(self, host):
        board = host.get_board().replace(ds_constants.BOARD_PREFIX, '')

        return board in video_test_constants.SUPPORTED_BOARDS


class InternalDisplayLabel(base_label.StringLabel):
    """Label that determines if the device has an internal display."""

    _NAME = 'internal_display'

    def generate_labels(self, host):
        from autotest_lib.client.cros.graphics import graphics_utils
        from autotest_lib.client.common_lib import utils as common_utils

        def __system_output(cmd):
            return host.run(cmd).stdout

        def __read_file(remote_path):
            return host.run('cat %s' % remote_path).stdout

        # Hijack the necessary client functions so that we can take advantage
        # of the client lib here.
        # FIXME: find a less hacky way than this
        original_system_output = utils.system_output
        original_read_file = common_utils.read_file
        utils.system_output = __system_output
        common_utils.read_file = __read_file
        try:
            return ([self._NAME]
                    if graphics_utils.has_internal_display()
                    else [])
        finally:
            utils.system_output = original_system_output
            common_utils.read_file = original_read_file


class LucidSleepLabel(base_label.BaseLabel):
    """Label that determines if device has support for lucid sleep."""

    # TODO(kevcheng): See if we can determine if this label is applicable a
    # better way (crbug.com/592146).
    _NAME = 'lucidsleep'
    LUCID_SLEEP_BOARDS = ['samus', 'lulu']

    def exists(self, host):
        board = host.get_board().replace(ds_constants.BOARD_PREFIX, '')
        return board in self.LUCID_SLEEP_BOARDS


class HWIDLabel(base_label.StringLabel):
    """Return all the labels generated from the hwid."""

    # We leave out _NAME because hwid_lib will generate everything for us.

    def __init__(self):
        # Grab the key file needed to access the hwid service.
        self.key_file = global_config.global_config.get_config_value(
                'CROS', 'HWID_KEY', type=str)


    def generate_labels(self, host):
        hwid_labels = []
        hwid = host.run_output('crossystem hwid').strip()
        hwid_info_list = hwid_lib.get_hwid_info(hwid, hwid_lib.HWID_INFO_LABEL,
                                                self.key_file).get('labels', [])

        for hwid_info in hwid_info_list:
            # If it's a prefix, we'll have:
            # {'name': prefix_label, 'value': postfix_label} and create
            # 'prefix_label:postfix_label'; otherwise it'll just be
            # {'name': label} which should just be 'label'.
            value = hwid_info.get('value', '')
            name = hwid_info.get('name', '')
            # There should always be a name but just in case there is not.
            if name:
                hwid_labels.append(name if not value else
                                   '%s:%s' % (name, value))
        return hwid_labels


    def get_all_labels(self):
        """We need to try all labels as a prefix and as standalone.

        We don't know for sure which labels are prefix labels and which are
        standalone so we try all of them as both.
        """
        all_hwid_labels = []
        try:
            all_hwid_labels = hwid_lib.get_all_possible_dut_labels(
                    self.key_file)
        except IOError:
            logging.error('Can not open key file: %s', self.key_file)
        except hwid_lib.HwIdException as e:
            logging.error('hwid service: %s', e)
        return all_hwid_labels, all_hwid_labels


CROS_LABELS = [
    AccelsLabel(),
    ArcLabel(),
    AudioLoopbackDongleLabel(),
    BluetoothLabel(),
    BoardLabel(),
    ChameleonConnectionLabel(),
    ChameleonLabel(),
    ChameleonPeripheralsLabel(),
    common_label.OSLabel(),
    CTSArchLabel(),
    ECLabel(),
    HWIDLabel(),
    InternalDisplayLabel(),
    LightSensorLabel(),
    LucidSleepLabel(),
    PowerSupplyLabel(),
    ServoLabel(),
    StorageLabel(),
    VideoGlitchLabel(),
    VideoLabel(),
]
