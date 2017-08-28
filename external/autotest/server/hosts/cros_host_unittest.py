#!/usr/bin/python
# pylint: disable=missing-docstring

import unittest
import common

from autotest_lib.server.hosts import cros_host
from autotest_lib.server.hosts import servo_host


class DictFilteringTestCase(unittest.TestCase):

    """Tests for dict filtering methods on CrosHost."""

    def test_get_chameleon_arguments(self):
        got = cros_host.CrosHost.get_chameleon_arguments({
            'chameleon_host': 'host',
            'spam': 'eggs',
        })
        self.assertEqual(got, {'chameleon_host': 'host'})

    def test_get_plankton_arguments(self):
        got = cros_host.CrosHost.get_plankton_arguments({
            'plankton_host': 'host',
            'spam': 'eggs',
        })
        self.assertEqual(got, {'plankton_host': 'host'})

    def test_get_servo_arguments(self):
        got = cros_host.CrosHost.get_servo_arguments({
            servo_host.SERVO_HOST_ATTR: 'host',
            'spam': 'eggs',
        })
        self.assertEqual(got, {servo_host.SERVO_HOST_ATTR: 'host'})


if __name__ == "__main__":
    unittest.main()
