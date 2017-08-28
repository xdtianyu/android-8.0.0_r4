#!/usr/bin/python
#pylint: disable=C0111

import unittest

import common
from autotest_lib.client.common_lib import base_utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import lsbrelease_utils
from autotest_lib.client.common_lib import site_utils
from autotest_lib.client.common_lib import utils
from autotest_lib.client.common_lib.test_utils import mock


metrics = utils.metrics_mock

def test_function(arg1, arg2, arg3, arg4=4, arg5=5, arg6=6):
    """Test global function.
    """


class TestClass(object):
    """Test class.
    """

    def test_instance_function(self, arg1, arg2, arg3, arg4=4, arg5=5, arg6=6):
        """Test instance function.
        """


    @classmethod
    def test_class_function(cls, arg1, arg2, arg3, arg4=4, arg5=5, arg6=6):
        """Test class function.
        """


    @staticmethod
    def test_static_function(arg1, arg2, arg3, arg4=4, arg5=5, arg6=6):
        """Test static function.
        """


class GetFunctionArgUnittest(unittest.TestCase):
    """Tests for method get_function_arg_value."""

    def run_test(self, func, insert_arg):
        """Run test.

        @param func: Function being called with given arguments.
        @param insert_arg: Set to True to insert an object in the argument list.
                           This is to mock instance/class object.
        """
        if insert_arg:
            args = (None, 1, 2, 3)
        else:
            args = (1, 2, 3)
        for i in range(1, 7):
            self.assertEquals(utils.get_function_arg_value(
                    func, 'arg%d'%i, args, {}), i)

        self.assertEquals(utils.get_function_arg_value(
                func, 'arg7', args, {'arg7': 7}), 7)
        self.assertRaises(
                KeyError, utils.get_function_arg_value,
                func, 'arg3', args[:-1], {})


    def test_global_function(self):
        """Test global function.
        """
        self.run_test(test_function, False)


    def test_instance_function(self):
        """Test instance function.
        """
        self.run_test(TestClass().test_instance_function, True)


    def test_class_function(self):
        """Test class function.
        """
        self.run_test(TestClass.test_class_function, True)


    def test_static_function(self):
        """Test static function.
        """
        self.run_test(TestClass.test_static_function, False)


class VersionMatchUnittest(unittest.TestCase):
    """Test version_match function."""

    def test_version_match(self):
        """Test version_match function."""
        canary_build = 'lumpy-release/R43-6803.0.0'
        canary_release = '6803.0.0'
        cq_build = 'lumpy-release/R43-6803.0.0-rc1'
        cq_release = '6803.0.0-rc1'
        trybot_paladin_build = 'trybot-lumpy-paladin/R43-6803.0.0-b123'
        trybot_paladin_release = '6803.0.2015_03_12_2103'
        trybot_pre_cq_build = 'trybot-wifi-pre-cq/R43-7000.0.0-b36'
        trybot_pre_cq_release = '7000.0.2016_03_12_2103'
        trybot_toolchain_build = 'trybot-nyan_big-gcc-toolchain/R56-8936.0.0-b14'
        trybot_toolchain_release = '8936.0.2016_10_26_1403'


        builds = [canary_build, cq_build, trybot_paladin_build,
                  trybot_pre_cq_build, trybot_toolchain_build]
        releases = [canary_release, cq_release, trybot_paladin_release,
                    trybot_pre_cq_release, trybot_toolchain_release]
        for i in range(len(builds)):
            for j in range(len(releases)):
                self.assertEqual(
                        utils.version_match(builds[i], releases[j]), i==j,
                        'Build version %s should%s match release version %s.' %
                        (builds[i], '' if i==j else ' not', releases[j]))


class IsPuppylabVmUnittest(unittest.TestCase):
    """Test is_puppylab_vm function."""

    def test_is_puppylab_vm(self):
        """Test is_puppylab_vm function."""
        self.assertTrue(utils.is_puppylab_vm('localhost:8001'))
        self.assertTrue(utils.is_puppylab_vm('127.0.0.1:8002'))
        self.assertFalse(utils.is_puppylab_vm('localhost'))
        self.assertFalse(utils.is_puppylab_vm('localhost:'))
        self.assertFalse(utils.is_puppylab_vm('127.0.0.1'))
        self.assertFalse(utils.is_puppylab_vm('127.0.0.1:'))
        self.assertFalse(utils.is_puppylab_vm('chromeos-server.mtv'))
        self.assertFalse(utils.is_puppylab_vm('chromeos-server.mtv:8001'))


class IsInSameSubnetUnittest(unittest.TestCase):
    """Test is_in_same_subnet function."""

    def test_is_in_same_subnet(self):
        """Test is_in_same_subnet function."""
        self.assertTrue(utils.is_in_same_subnet('192.168.0.0', '192.168.1.2',
                                                23))
        self.assertFalse(utils.is_in_same_subnet('192.168.0.0', '192.168.1.2',
                                                24))
        self.assertTrue(utils.is_in_same_subnet('192.168.0.0', '192.168.0.255',
                                                24))
        self.assertFalse(utils.is_in_same_subnet('191.168.0.0', '192.168.0.0',
                                                24))

class GetWirelessSsidUnittest(unittest.TestCase):
    """Test get_wireless_ssid function."""

    DEFAULT_SSID = 'default'
    SSID_1 = 'ssid_1'
    SSID_2 = 'ssid_2'
    SSID_3 = 'ssid_3'

    def test_get_wireless_ssid(self):
        """Test is_in_same_subnet function."""
        god = mock.mock_god()
        god.stub_function_to_return(utils.CONFIG, 'get_config_value',
                                    self.DEFAULT_SSID)
        god.stub_function_to_return(utils.CONFIG, 'get_config_value_regex',
                                    {'wireless_ssid_1.2.3.4/24': self.SSID_1,
                                     'wireless_ssid_4.3.2.1/16': self.SSID_2,
                                     'wireless_ssid_4.3.2.111/32': self.SSID_3})
        self.assertEqual(self.SSID_1, utils.get_wireless_ssid('1.2.3.100'))
        self.assertEqual(self.SSID_2, utils.get_wireless_ssid('4.3.2.100'))
        self.assertEqual(self.SSID_3, utils.get_wireless_ssid('4.3.2.111'))
        self.assertEqual(self.DEFAULT_SSID,
                         utils.get_wireless_ssid('100.0.0.100'))


class LaunchControlBuildParseUnittest(unittest.TestCase):
    """Test various parsing functions related to Launch Control builds and
    devices.
    """

    def test_parse_launch_control_target(self):
        """Test parse_launch_control_target function."""
        target_tests = {
                ('shamu', 'userdebug'): 'shamu-userdebug',
                ('shamu', 'eng'): 'shamu-eng',
                ('shamu-board', 'eng'): 'shamu-board-eng',
                (None, None): 'bad_target',
                (None, None): 'target'}
        for result, target in target_tests.items():
            self.assertEqual(result, utils.parse_launch_control_target(target))


class GetOffloaderUriTest(unittest.TestCase):
    """Test get_offload_gsuri function."""
    _IMAGE_STORAGE_SERVER = 'gs://test_image_bucket'

    def setUp(self):
        self.god = mock.mock_god()

    def tearDown(self):
        self.god.unstub_all()

    def test_get_default_lab_offload_gsuri(self):
        """Test default lab offload gsuri ."""
        self.god.mock_up(utils.CONFIG, 'CONFIG')
        self.god.stub_function_to_return(lsbrelease_utils, 'is_moblab', False)
        self.assertEqual(utils.DEFAULT_OFFLOAD_GSURI,
                utils.get_offload_gsuri())

        self.god.check_playback()

    def test_get_default_moblab_offload_gsuri(self):
        self.god.mock_up(utils.CONFIG, 'CONFIG')
        self.god.stub_function_to_return(lsbrelease_utils, 'is_moblab', True)
        utils.CONFIG.get_config_value.expect_call(
                'CROS', 'image_storage_server').and_return(
                        self._IMAGE_STORAGE_SERVER)
        self.god.stub_function_to_return(site_utils,
                'get_interface_mac_address', 'test_mac')
        self.god.stub_function_to_return(site_utils, 'get_moblab_id', 'test_id')
        expected_gsuri = '%sresults/%s/%s/' % (
                self._IMAGE_STORAGE_SERVER, 'test_mac', 'test_id')
        cached_gsuri = site_utils.DEFAULT_OFFLOAD_GSURI
        site_utils.DEFAULT_OFFLOAD_GSURI = None
        gsuri = utils.get_offload_gsuri()
        site_utils.DEFAULT_OFFLOAD_GSURI = cached_gsuri
        self.assertEqual(expected_gsuri, gsuri)

        self.god.check_playback()

    def test_get_moblab_offload_gsuri(self):
        """Test default lab offload gsuri ."""
        self.god.mock_up(utils.CONFIG, 'CONFIG')
        self.god.stub_function_to_return(lsbrelease_utils, 'is_moblab', True)
        self.god.stub_function_to_return(site_utils,
                'get_interface_mac_address', 'test_mac')
        self.god.stub_function_to_return(site_utils, 'get_moblab_id', 'test_id')
        gsuri = '%s%s/%s/' % (
                utils.DEFAULT_OFFLOAD_GSURI, 'test_mac', 'test_id')
        self.assertEqual(gsuri, utils.get_offload_gsuri())

        self.god.check_playback()


class GetBuiltinEthernetNicNameTest(unittest.TestCase):
    """Test get moblab public network interface name."""

    def setUp(self):
        self.god = mock.mock_god()

    def tearDown(self):
        self.god.unstub_all()

    def test_is_eth0(self):
        """Test when public network interface is eth0."""
        run_func = self.god.create_mock_function('run_func')
        self.god.stub_with(base_utils, 'run', run_func)
        run_func.expect_call('readlink -f /sys/class/net/eth0').and_return(
                base_utils.CmdResult(exit_status=0, stdout='not_u_s_b'))
        eth = utils.get_built_in_ethernet_nic_name()
        self.assertEqual('eth0', eth)
        self.god.check_playback()

    def test_readlin_fails(self):
        """Test when readlink does not work."""
        run_func = self.god.create_mock_function('run_func')
        self.god.stub_with(base_utils, 'run', run_func)
        run_func.expect_call('readlink -f /sys/class/net/eth0').and_return(
                base_utils.CmdResult(exit_status=-1, stdout='blah'))
        eth = utils.get_built_in_ethernet_nic_name()
        self.assertEqual('eth0', eth)
        self.god.check_playback()

    def test_no_readlink(self):
        """Test when readlink does not exist."""
        run_func = self.god.create_mock_function('run_func')
        self.god.stub_with(base_utils, 'run', run_func)
        run_func.expect_call('readlink -f /sys/class/net/eth0').and_raises(
                error.CmdError('readlink', 'no such command'))
        eth = utils.get_built_in_ethernet_nic_name()
        self.assertEqual('eth0', eth)
        self.god.check_playback()

    def test_is_eth1(self):
        """Test when public network interface is eth1."""
        run_func = self.god.create_mock_function('run_func')
        self.god.stub_with(base_utils, 'run', run_func)
        run_func.expect_call('readlink -f /sys/class/net/eth0').and_return(
                base_utils.CmdResult(exit_status=0, stdout='is usb'))
        run_func.expect_call('readlink -f /sys/class/net/eth1').and_return(
                base_utils.CmdResult(exit_status=0, stdout='not_u_s_b'))
        eth = utils.get_built_in_ethernet_nic_name()
        self.assertEqual('eth1', eth)
        self.god.check_playback()


class  MockMetricsTest(unittest.TestCase):
    """Test metrics mock class can handle various metrics calls."""

    def test_Counter(self):
        """Test the mock class can create an instance and call any method.
        """
        c = metrics.Counter('counter')
        c.increment(fields={'key': 1})


    def test_Context(self):
        """Test the mock class can handle context class.
        """
        test_value = None
        with metrics.SecondsTimer('context') as t:
            test_value = 'called_in_context'
            t['random_key'] = 'pass'
        self.assertEqual('called_in_context', test_value)


    def test_decorator(self):
        """Test the mock class can handle decorator.
        """
        class TestClass(object):

            def __init__(self):
                self.value = None

        test_value = TestClass()
        test_value.value = None
        @metrics.SecondsTimerDecorator('decorator')
        def test(arg):
            arg.value = 'called_in_decorator'

        test(test_value)
        self.assertEqual('called_in_decorator', test_value.value)


    def test_setitem(self):
        """Test the mock class can handle set item call.
        """
        timer = metrics.SecondsTimer('name')
        timer['random_key'] = 'pass'


if __name__ == "__main__":
    unittest.main()
