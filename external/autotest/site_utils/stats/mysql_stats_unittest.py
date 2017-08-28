"""Tests for mysql_stats."""

import common

import collections
import mock
import unittest

import mysql_stats


class MysqlStatsTest(unittest.TestCase):
    """Unittest for mysql_stats."""

    def testQueryAndEmit(self):
        """Test for QueryAndEmit."""
        cursor = mock.Mock()
        cursor.fetchone.return_value = ('Column-name', 0)

        # This shouldn't raise an exception.
        mysql_stats.QueryAndEmit(collections.defaultdict(lambda: 0), cursor)


if __name__ == '__main__':
    unittest.main()
