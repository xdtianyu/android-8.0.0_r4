#!/usr/bin/python

# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Queries a MySQL database and emits status metrics to Monarch.

Note: confusingly, 'Innodb_buffer_pool_reads' is actually the cache-misses, not
the number of reads to the buffer pool.  'Innodb_buffer_pool_read_requests'
corresponds to the number of reads the the buffer pool.
"""
import logging
import sys

import MySQLdb
import time

import common

from autotest_lib.client.common_lib import global_config
from autotest_lib.client.common_lib import utils

try:
    from chromite.lib import metrics
    from chromite.lib import ts_mon_config
except ImportError:
    metrics = utils.metrics_mock
    ts_mon_config = utils.metrics_mock


AT_DIR='/usr/local/autotest'
DEFAULT_USER = global_config.global_config.get_config_value(
        'CROS', 'db_backup_user', type=str, default='')
DEFAULT_PASSWD = global_config.global_config.get_config_value(
        'CROS', 'db_backup_password', type=str, default='')
LOOP_INTERVAL = 60
EMITTED_STATUSES_COUNTERS = [
    'bytes_received',
    'bytes_sent',
    'connections',
    'Innodb_buffer_pool_read_requests',
    'Innodb_buffer_pool_reads',
    'questions',
    'slow_queries',
    'threads_created',
]

EMITTED_STATUS_GAUGES = ['threads_running', 'threads_connected']


def main():
    """Sets up ts_mon and repeatedly queries MySQL stats"""
    logging.basicConfig(stream=sys.stdout, level=logging.INFO)
    db = MySQLdb.connect('localhost', DEFAULT_USER, DEFAULT_PASSWD)
    cursor = db.cursor()

    with ts_mon_config.SetupTsMonGlobalState('mysql_stats', indirect=True):
      QueryLoop(cursor)


def QueryLoop(cursor):
    """Queries and emits metrics every LOOP_INTERVAL seconds.

    @param cursor: The mysql command line.
    """
    # Get the baselines for cumulative metrics. Otherwise the windowed rate at
    # the very beginning will be extremely high as it shoots up from 0 to its
    # current value.
    baselines = dict((s, GetStatus(cursor, s))
                     for s in EMITTED_STATUSES_COUNTERS)

    while True:
        now = time.time()
        QueryAndEmit(baselines, cursor)
        time_spent = time.time() - now
        sleep_duration = LOOP_INTERVAL - time_spent
        time.sleep(max(0, sleep_duration))


def GetStatus(cursor, s):
    """Get the status variable from database.

    @param cursor: MySQLdb cursor to query with.
    @param s: Name of the status variable.
    @returns The mysql query result.
    """
    cursor.execute('SHOW GLOBAL STATUS LIKE "%s";' % s)
    output = cursor.fetchone()[1]
    if not output:
        logging.error('Cannot find any global status like %s', s)
    return int(output)


def QueryAndEmit(baselines, cursor):
    """Queries MySQL for important stats and emits Monarch metrics

    @param baselines: A dict containing the initial values for the cumulative
                      metrics.
    @param cursor: The mysql command line.
    """

    for status in EMITTED_STATUSES_COUNTERS:
        delta = GetStatus(cursor, status) - baselines[status]
        metric_name = 'chromeos/autotest/afe_db/%s' % status.lower()
        metrics.Counter(metric_name).set(delta)

    for status in EMITTED_STATUS_GAUGES:
        metric_name = 'chromeos/autotest/afe_db/%s' % status.lower()
        metrics.Gauge(metric_name).set(GetStatus(cursor, status))

    pages_free = GetStatus(cursor, 'Innodb_buffer_pool_pages_free')
    pages_total = GetStatus(cursor, 'Innodb_buffer_pool_pages_total')

    metrics.Gauge('chromeos/autotest/afe_db/buffer_pool_pages').set(
        pages_free, fields={'used': False})

    metrics.Gauge('chromeos/autotest/afe_db/buffer_pool_pages').set(
        pages_total - pages_free, fields={'used': True})


if __name__ == '__main__':
  main()
