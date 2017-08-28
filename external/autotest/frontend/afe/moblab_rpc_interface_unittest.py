#!/usr/bin/python
#
# Copyright (c) 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Unit tests for frontend/afe/moblab_rpc_interface.py."""

import __builtin__
# The boto module is only available/used in Moblab for validation of cloud
# storage access. The module is not available in the test lab environment,
# and the import error is handled.
try:
    import boto
except ImportError:
    boto = None
import ConfigParser
import logging
import mox
import StringIO
import unittest

import common

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import global_config
from autotest_lib.client.common_lib import lsbrelease_utils
from autotest_lib.frontend import setup_django_environment
from autotest_lib.frontend.afe import frontend_test_utils
from autotest_lib.frontend.afe import moblab_rpc_interface
from autotest_lib.frontend.afe import rpc_utils
from autotest_lib.server import utils
from autotest_lib.server.hosts import moblab_host


class MoblabRpcInterfaceTest(mox.MoxTestBase,
                             frontend_test_utils.FrontendTestMixin):
    """Unit tests for functions in moblab_rpc_interface.py."""

    def setUp(self):
        super(MoblabRpcInterfaceTest, self).setUp()
        self._frontend_common_setup(fill_data=False)


    def tearDown(self):
        self._frontend_common_teardown()


    def setIsMoblab(self, is_moblab):
        """Set utils.is_moblab result.

        @param is_moblab: Value to have utils.is_moblab to return.
        """
        self.mox.StubOutWithMock(utils, 'is_moblab')
        utils.is_moblab().AndReturn(is_moblab)


    def _mockReadFile(self, path, lines=[]):
        """Mock out reading a file line by line.

        @param path: Path of the file we are mock reading.
        @param lines: lines of the mock file that will be returned when
                      readLine() is called.
        """
        mockFile = self.mox.CreateMockAnything()
        for line in lines:
            mockFile.readline().AndReturn(line)
        mockFile.readline()
        mockFile.close()
        open(path).AndReturn(mockFile)


    def testMoblabOnlyDecorator(self):
        """Ensure the moblab only decorator gates functions properly."""
        self.setIsMoblab(False)
        self.mox.ReplayAll()
        self.assertRaises(error.RPCException,
                          moblab_rpc_interface.get_config_values)


    def testGetConfigValues(self):
        """Ensure that the config object is properly converted to a dict."""
        self.setIsMoblab(True)
        config_mock = self.mox.CreateMockAnything()
        moblab_rpc_interface._CONFIG = config_mock
        config_mock.get_sections().AndReturn(['section1', 'section2'])
        config_mock.config = self.mox.CreateMockAnything()
        config_mock.config.items('section1').AndReturn([('item1', 'value1'),
                                                        ('item2', 'value2')])
        config_mock.config.items('section2').AndReturn([('item3', 'value3'),
                                                        ('item4', 'value4')])

        rpc_utils.prepare_for_serialization(
            {'section1' : [('item1', 'value1'),
                           ('item2', 'value2')],
             'section2' : [('item3', 'value3'),
                           ('item4', 'value4')]})
        self.mox.ReplayAll()
        moblab_rpc_interface.get_config_values()


    def testUpdateConfig(self):
        """Ensure that updating the config works as expected."""
        self.setIsMoblab(True)
        moblab_rpc_interface.os = self.mox.CreateMockAnything()

        self.mox.StubOutWithMock(__builtin__, 'open')
        self._mockReadFile(global_config.DEFAULT_CONFIG_FILE)

        self.mox.StubOutWithMock(lsbrelease_utils, 'is_moblab')
        lsbrelease_utils.is_moblab().AndReturn(True)

        self._mockReadFile(global_config.DEFAULT_MOBLAB_FILE,
                           ['[section1]', 'item1: value1'])

        moblab_rpc_interface.os = self.mox.CreateMockAnything()
        moblab_rpc_interface.os.path = self.mox.CreateMockAnything()
        moblab_rpc_interface.os.path.exists(
                moblab_rpc_interface._CONFIG.shadow_file).AndReturn(
                True)
        mockShadowFile = self.mox.CreateMockAnything()
        mockShadowFileContents = StringIO.StringIO()
        mockShadowFile.__enter__().AndReturn(mockShadowFileContents)
        mockShadowFile.__exit__(mox.IgnoreArg(), mox.IgnoreArg(),
                                mox.IgnoreArg())
        open(moblab_rpc_interface._CONFIG.shadow_file,
             'w').AndReturn(mockShadowFile)
        moblab_rpc_interface.os.system('sudo reboot')

        self.mox.ReplayAll()
        moblab_rpc_interface.update_config_handler(
                {'section1' : [('item1', 'value1'),
                               ('item2', 'value2')],
                 'section2' : [('item3', 'value3'),
                               ('item4', 'value4')]})

        # item1 should not be in the new shadow config as its updated value
        # matches the original config's value.
        self.assertEquals(
                mockShadowFileContents.getvalue(),
                '[section2]\nitem3 = value3\nitem4 = value4\n\n'
                '[section1]\nitem2 = value2\n\n')


    def testResetConfig(self):
        """Ensure that reset opens the shadow_config file for writing."""
        self.setIsMoblab(True)
        config_mock = self.mox.CreateMockAnything()
        moblab_rpc_interface._CONFIG = config_mock
        config_mock.shadow_file = 'shadow_config.ini'
        self.mox.StubOutWithMock(__builtin__, 'open')
        mockFile = self.mox.CreateMockAnything()
        file_contents = self.mox.CreateMockAnything()
        mockFile.__enter__().AndReturn(file_contents)
        mockFile.__exit__(mox.IgnoreArg(), mox.IgnoreArg(), mox.IgnoreArg())
        open(config_mock.shadow_file, 'w').AndReturn(mockFile)
        moblab_rpc_interface.os = self.mox.CreateMockAnything()
        moblab_rpc_interface.os.system('sudo reboot')
        self.mox.ReplayAll()
        moblab_rpc_interface.reset_config_settings()


    def testSetBotoKey(self):
        """Ensure that the botokey path supplied is copied correctly."""
        self.setIsMoblab(True)
        boto_key = '/tmp/boto'
        moblab_rpc_interface.os.path = self.mox.CreateMockAnything()
        moblab_rpc_interface.os.path.exists(boto_key).AndReturn(
                True)
        moblab_rpc_interface.shutil = self.mox.CreateMockAnything()
        moblab_rpc_interface.shutil.copyfile(
                boto_key, moblab_rpc_interface.MOBLAB_BOTO_LOCATION)
        self.mox.ReplayAll()
        moblab_rpc_interface.set_boto_key(boto_key)


    def testSetLaunchControlKey(self):
        """Ensure that the Launch Control key path supplied is copied correctly.
        """
        self.setIsMoblab(True)
        launch_control_key = '/tmp/launch_control'
        moblab_rpc_interface.os = self.mox.CreateMockAnything()
        moblab_rpc_interface.os.path = self.mox.CreateMockAnything()
        moblab_rpc_interface.os.path.exists(launch_control_key).AndReturn(
                True)
        moblab_rpc_interface.shutil = self.mox.CreateMockAnything()
        moblab_rpc_interface.shutil.copyfile(
                launch_control_key,
                moblab_host.MOBLAB_LAUNCH_CONTROL_KEY_LOCATION)
        moblab_rpc_interface.os.system('sudo restart moblab-devserver-init')
        self.mox.ReplayAll()
        moblab_rpc_interface.set_launch_control_key(launch_control_key)


    def testGetNetworkInfo(self):
        """Ensure the network info is properly converted to a dict."""
        self.setIsMoblab(True)

        self.mox.StubOutWithMock(moblab_rpc_interface, '_get_network_info')
        moblab_rpc_interface._get_network_info().AndReturn(('10.0.0.1', True))
        self.mox.StubOutWithMock(rpc_utils, 'prepare_for_serialization')

        rpc_utils.prepare_for_serialization(
               {'is_connected': True, 'server_ips': ['10.0.0.1']})
        self.mox.ReplayAll()
        moblab_rpc_interface.get_network_info()
        self.mox.VerifyAll()


    def testGetNetworkInfoWithNoIp(self):
        """Queries network info with no public IP address."""
        self.setIsMoblab(True)

        self.mox.StubOutWithMock(moblab_rpc_interface, '_get_network_info')
        moblab_rpc_interface._get_network_info().AndReturn((None, False))
        self.mox.StubOutWithMock(rpc_utils, 'prepare_for_serialization')

        rpc_utils.prepare_for_serialization(
               {'is_connected': False})
        self.mox.ReplayAll()
        moblab_rpc_interface.get_network_info()
        self.mox.VerifyAll()


    def testGetNetworkInfoWithNoConnectivity(self):
        """Queries network info with public IP address but no connectivity."""
        self.setIsMoblab(True)

        self.mox.StubOutWithMock(moblab_rpc_interface, '_get_network_info')
        moblab_rpc_interface._get_network_info().AndReturn(('10.0.0.1', False))
        self.mox.StubOutWithMock(rpc_utils, 'prepare_for_serialization')

        rpc_utils.prepare_for_serialization(
               {'is_connected': False, 'server_ips': ['10.0.0.1']})
        self.mox.ReplayAll()
        moblab_rpc_interface.get_network_info()
        self.mox.VerifyAll()


    def testGetCloudStorageInfo(self):
        """Ensure the cloud storage info is properly converted to a dict."""
        self.setIsMoblab(True)
        config_mock = self.mox.CreateMockAnything()
        moblab_rpc_interface._CONFIG = config_mock
        config_mock.get_config_value(
            'CROS', 'image_storage_server').AndReturn('gs://bucket1')
        config_mock.get_config_value(
            'CROS', 'results_storage_server', default=None).AndReturn(
                    'gs://bucket2')
        self.mox.StubOutWithMock(moblab_rpc_interface, '_get_boto_config')
        moblab_rpc_interface._get_boto_config().AndReturn(config_mock)
        config_mock.sections().AndReturn(['Credentials', 'b'])
        config_mock.options('Credentials').AndReturn(
            ['gs_access_key_id', 'gs_secret_access_key'])
        config_mock.get(
            'Credentials', 'gs_access_key_id').AndReturn('key')
        config_mock.get(
            'Credentials', 'gs_secret_access_key').AndReturn('secret')
        rpc_utils.prepare_for_serialization(
                {
                    'gs_access_key_id': 'key',
                    'gs_secret_access_key' : 'secret',
                    'use_existing_boto_file': True,
                    'image_storage_server' : 'gs://bucket1',
                    'results_storage_server' : 'gs://bucket2'
                })
        self.mox.ReplayAll()
        moblab_rpc_interface.get_cloud_storage_info()
        self.mox.VerifyAll()


    def testValidateCloudStorageInfo(self):
        """ Ensure the cloud storage info validation flow."""
        self.setIsMoblab(True)
        cloud_storage_info = {
            'use_existing_boto_file': False,
            'gs_access_key_id': 'key',
            'gs_secret_access_key': 'secret',
            'image_storage_server': 'gs://bucket1',
            'results_storage_server': 'gs://bucket2'}
        self.mox.StubOutWithMock(moblab_rpc_interface, '_is_valid_boto_key')
        self.mox.StubOutWithMock(moblab_rpc_interface, '_is_valid_bucket')
        moblab_rpc_interface._is_valid_boto_key(
                'key', 'secret').AndReturn((True, None))
        moblab_rpc_interface._is_valid_bucket(
                'key', 'secret', 'bucket1').AndReturn((True, None))
        moblab_rpc_interface._is_valid_bucket(
                'key', 'secret', 'bucket2').AndReturn((True, None))
        rpc_utils.prepare_for_serialization(
                {'status_ok': True })
        self.mox.ReplayAll()
        moblab_rpc_interface.validate_cloud_storage_info(cloud_storage_info)
        self.mox.VerifyAll()


    def testGetBucketNameFromUrl(self):
        """Gets bucket name from bucket URL."""
        self.assertEquals(
            'bucket_name-123',
            moblab_rpc_interface._get_bucket_name_from_url(
                    'gs://bucket_name-123'))
        self.assertEquals(
            'bucket_name-123',
            moblab_rpc_interface._get_bucket_name_from_url(
                    'gs://bucket_name-123/'))
        self.assertEquals(
            'bucket_name-123',
            moblab_rpc_interface._get_bucket_name_from_url(
                    'gs://bucket_name-123/a/b/c'))
        self.assertIsNone(moblab_rpc_interface._get_bucket_name_from_url(
            'bucket_name-123/a/b/c'))


    def testIsValidBotoKeyValid(self):
        """Tests the boto key validation flow."""
        if boto is None:
            logging.info('skip test since boto module not installed')
            return
        conn = self.mox.CreateMockAnything()
        self.mox.StubOutWithMock(boto, 'connect_gs')
        boto.connect_gs('key', 'secret').AndReturn(conn)
        conn.get_all_buckets().AndReturn(['a', 'b'])
        conn.close()
        self.mox.ReplayAll()
        valid, details = moblab_rpc_interface._is_valid_boto_key('key', 'secret')
        self.assertTrue(valid)
        self.mox.VerifyAll()


    def testIsValidBotoKeyInvalid(self):
        """Tests the boto key validation with invalid key."""
        if boto is None:
            logging.info('skip test since boto module not installed')
            return
        conn = self.mox.CreateMockAnything()
        self.mox.StubOutWithMock(boto, 'connect_gs')
        boto.connect_gs('key', 'secret').AndReturn(conn)
        conn.get_all_buckets().AndRaise(
                boto.exception.GSResponseError('bad', 'reason'))
        conn.close()
        self.mox.ReplayAll()
        valid, details = moblab_rpc_interface._is_valid_boto_key('key', 'secret')
        self.assertFalse(valid)
        self.assertEquals('The boto access key is not valid', details)
        self.mox.VerifyAll()


    def testIsValidBucketValid(self):
        """Tests the bucket vaildation flow."""
        if boto is None:
            logging.info('skip test since boto module not installed')
            return
        conn = self.mox.CreateMockAnything()
        self.mox.StubOutWithMock(boto, 'connect_gs')
        boto.connect_gs('key', 'secret').AndReturn(conn)
        conn.lookup('bucket').AndReturn('bucket')
        conn.close()
        self.mox.ReplayAll()
        valid, details = moblab_rpc_interface._is_valid_bucket(
                'key', 'secret', 'bucket')
        self.assertTrue(valid)
        self.mox.VerifyAll()


    def testIsValidBucketInvalid(self):
        """Tests the bucket validation flow with invalid key."""
        if boto is None:
            logging.info('skip test since boto module not installed')
            return
        conn = self.mox.CreateMockAnything()
        self.mox.StubOutWithMock(boto, 'connect_gs')
        boto.connect_gs('key', 'secret').AndReturn(conn)
        conn.lookup('bucket').AndReturn(None)
        conn.close()
        self.mox.ReplayAll()
        valid, details = moblab_rpc_interface._is_valid_bucket(
                'key', 'secret', 'bucket')
        self.assertFalse(valid)
        self.assertEquals("Bucket bucket does not exist.", details)
        self.mox.VerifyAll()


    def testGetShadowConfigFromPartialUpdate(self):
        """Tests getting shadow configuration based on partial upate."""
        partial_config = {
                'section1': [
                    ('opt1', 'value1'),
                    ('opt2', 'value2'),
                    ('opt3', 'value3'),
                    ('opt4', 'value4'),
                    ]
                }
        shadow_config_str = "[section1]\nopt2 = value2_1\nopt4 = value4_1"
        shadow_config = ConfigParser.ConfigParser()
        shadow_config.readfp(StringIO.StringIO(shadow_config_str))
        original_config = self.mox.CreateMockAnything()
        self.mox.StubOutWithMock(moblab_rpc_interface, '_read_original_config')
        self.mox.StubOutWithMock(moblab_rpc_interface, '_read_raw_config')
        moblab_rpc_interface._read_original_config().AndReturn(original_config)
        moblab_rpc_interface._read_raw_config(
                moblab_rpc_interface._CONFIG.shadow_file).AndReturn(shadow_config)
        original_config.get_config_value(
                'section1', 'opt1',
                allow_blank=True, default='').AndReturn('value1')
        original_config.get_config_value(
                'section1', 'opt2',
                allow_blank=True, default='').AndReturn('value2')
        original_config.get_config_value(
                'section1', 'opt3',
                allow_blank=True, default='').AndReturn('blah')
        original_config.get_config_value(
                'section1', 'opt4',
                allow_blank=True, default='').AndReturn('blah')
        self.mox.ReplayAll()
        shadow_config = moblab_rpc_interface._get_shadow_config_from_partial_update(
                partial_config)
        # opt1 same as the original.
        self.assertFalse(shadow_config.has_option('section1', 'opt1'))
        # opt2 reverts back to original
        self.assertFalse(shadow_config.has_option('section1', 'opt2'))
        # opt3 is updated from original.
        self.assertEquals('value3', shadow_config.get('section1', 'opt3'))
        # opt3 in shadow but updated again.
        self.assertEquals('value4', shadow_config.get('section1', 'opt4'))
        self.mox.VerifyAll()


    def testGetShadowConfigFromPartialUpdateWithNewSection(self):
        """
        Test getting shadown configuration based on partial update with new section.
        """
        partial_config = {
                'section2': [
                    ('opt5', 'value5'),
                    ('opt6', 'value6'),
                    ],
                }
        shadow_config_str = "[section1]\nopt2 = value2_1\n"
        shadow_config = ConfigParser.ConfigParser()
        shadow_config.readfp(StringIO.StringIO(shadow_config_str))
        original_config = self.mox.CreateMockAnything()
        self.mox.StubOutWithMock(moblab_rpc_interface, '_read_original_config')
        self.mox.StubOutWithMock(moblab_rpc_interface, '_read_raw_config')
        moblab_rpc_interface._read_original_config().AndReturn(original_config)
        moblab_rpc_interface._read_raw_config(
            moblab_rpc_interface._CONFIG.shadow_file).AndReturn(shadow_config)
        original_config.get_config_value(
                'section2', 'opt5',
                allow_blank=True, default='').AndReturn('value5')
        original_config.get_config_value(
                'section2', 'opt6',
                allow_blank=True, default='').AndReturn('blah')
        self.mox.ReplayAll()
        shadow_config = moblab_rpc_interface._get_shadow_config_from_partial_update(
                partial_config)
        # opt2 is still in shadow
        self.assertEquals('value2_1', shadow_config.get('section1', 'opt2'))
        # opt5 is not changed.
        self.assertFalse(shadow_config.has_option('section2', 'opt5'))
        # opt6 is updated.
        self.assertEquals('value6', shadow_config.get('section2', 'opt6'))
        self.mox.VerifyAll()


if __name__ == '__main__':
    unittest.main()
