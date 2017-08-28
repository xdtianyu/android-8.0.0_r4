#!/usr/bin/env python

# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""This script is used to calculate the daily summary of the total size of
the test results uploaded to Google Storage per day. It can also output the
test results with the largest size.
"""

import argparse
import time

import common
from autotest_lib.client.common_lib import time_utils
from autotest_lib.client.common_lib.cros.graphite import autotest_es


def get_summary(start_time, end_time, top=None, report_stat=False):
    """Get the summary of total size of test results for given period.

    @param start_time: Start time of the test results to search for.
    @param end_time: End time of the test results to search for.
    @param top: Number of top hits with the largest size of test results.
    @param report_stat: True to report the total test results size to statsd.
    """
    fields_returned = ['size_KB', 'time_recorded']
    if top > 0:
        fields_returned.append('result_dir')
    records = autotest_es.query(
            fields_returned=fields_returned,
            equality_constraints=[('_type', 'result_dir_size'),],
            range_constraints=[('time_recorded', start_time, end_time)],
            sort_specs=[{'time_recorded': 'asc'}])

    total_GB = sum([e['size_KB'] for e in records.hits]) / 1000000.0
    print 'Number of test result entries found: %d' % len(records.hits)
    print 'Total size of test results uploaded:  %.2f GB' % total_GB
    if top:
        hits = sorted(records.hits, key=lambda hit:hit['size_KB'], reverse=True)
        for hit in hits[:top]:
            print ('%s: \t%.2f MB' %
                   (hit['result_dir'], hit['size_KB']/1000.0))


def main():
    """main script. """
    t_now = time.time()
    t_now_minus_one_day = t_now - 3600*24
    parser = argparse.ArgumentParser()
    parser.add_argument('-l', type=int, dest='last',
                        help='last days to summary test results across',
                        default=None)
    parser.add_argument('--start', type=str, dest='start',
                        help=('Enter start time as: yyyy-mm-dd hh:mm:ss,'
                              'defualts to 24h ago.'),
                        default=time_utils.epoch_time_to_date_string(
                                t_now_minus_one_day))
    parser.add_argument('--end', type=str, dest='end',
                        help=('Enter end time in as: yyyy-mm-dd hh:mm:ss,'
                              'defualts to current time.'),
                        default=time_utils.epoch_time_to_date_string(t_now))
    parser.add_argument('-t', type=int, dest='top',
                        help='Print the top x of large result folders.',
                        default=0)
    parser.add_argument('-r', action='store_true', dest='report_stat',
                        default=False,
                        help='True to report total size to statsd.')
    options = parser.parse_args()

    if options.last:
        start_time = t_now - 3600*24*options.last
        end_time = t_now
    else:
        start_time = time_utils.to_epoch_time(options.start)
        end_time = time_utils.to_epoch_time(options.end)

    get_summary(start_time=start_time, end_time=end_time,
                top=options.top, report_stat=options.report_stat)


if __name__ == '__main__':
    main()
