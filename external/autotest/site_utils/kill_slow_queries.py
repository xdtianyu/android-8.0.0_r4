#!/usr/bin/python
# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Kill slow queries in local autotest database."""

import logging
import MySQLdb
import optparse
import sys

import common
from autotest_lib.client.common_lib import global_config
from autotest_lib.site_utils import gmail_lib

AT_DIR='/usr/local/autotest'
DEFAULT_USER = global_config.global_config.get_config_value(
        'CROS', 'db_backup_user', type=str, default='')
DEFAULT_PASSWD = global_config.global_config.get_config_value(
        'CROS', 'db_backup_password', type=str, default='')
DEFAULT_MAIL = global_config.global_config.get_config_value(
        'SCHEDULER', 'notify_email', type=str, default='')


def parse_options():
    """Parse the command line arguments."""
    usage = 'usage: %prog [options]'
    parser = optparse.OptionParser(usage=usage)
    parser.add_option('-u', '--user', default=DEFAULT_USER,
                      help='User to login to the Autotest DB. Default is the '
                           'one defined in config file.')
    parser.add_option('-p', '--password', default=DEFAULT_PASSWD,
                      help='Password to login to the Autotest DB. Default is '
                           'the one defined in config file.')
    parser.add_option('-t', '--timeout', type=int, default=300,
                      help='Timeout boundry of the slow database query. '
                           'Default is 300s')
    parser.add_option('-m', '--mail', default=DEFAULT_MAIL,
                      help='Mail address to send the summary to. Default is '
                           'ChromeOS infra Deputy')
    options, args = parser.parse_args()
    return parser, options, args


def verify_options_and_args(options, args):
    """Verify the validity of options and args.

    @param options: The parsed options to verify.
    @param args: The parsed args to verify.

    @returns: True if verification passes, False otherwise.
    """
    if args:
        logging.error('Unknown arguments: ' + str(args))
        return False

    if not (options.user and options.password):
        logging.error('Failed to get the default user of password for Autotest'
                      ' DB. Please specify them through the command line.')
        return False
    return True


def format_the_output(slow_queries):
    """Convert a list of slow queries into a readable string format.

    e.g. [(a, b, c...)]  -->
         "Id: a
          Host: b
          User: c
          ...
         "
    @param slow_queries: A list of tuples, one tuple contains all the info about
                         one single slow query.

    @returns: one clean string representation of all the slow queries.
    """
    query_str_list = [('Id: %s\nUser: %s\nHost: %s\ndb: %s\nCommand: %s\n'
                       'Time: %s\nState: %s\nInfo: %s\n') %
                      q for q in slow_queries]
    return '\n'.join(query_str_list)


def kill_slow_queries(user, password, timeout):
    """Kill the slow database queries running beyond the timeout limit.

    @param user: User to login to the Autotest DB.
    @param password: Password to login to the Autotest DB.
    @param timeout: Timeout limit to kill the slow queries.

    @returns: string representation of all the killed slow queries.
    """
    db = MySQLdb.connect('localhost', user, password)
    cursor = db.cursor()
    # Get the processlist.
    cursor.execute('SHOW FULL PROCESSLIST')
    processlist = cursor.fetchall()
    # Filter out the slow queries and kill them.
    slow_queries = [p for p in processlist if p[4]=='Query' and p[5]>=timeout]
    queries_str = ''
    if slow_queries:
        queries_str = format_the_output(slow_queries)
        queries_ids = [q[0] for q in slow_queries]
        logging.info('Start killing following slow queries\n%s', queries_str)
        for query_id in queries_ids:
            logging.info('Killing %s...', query_id)
            cursor.execute('KILL %d' % query_id)
            logging.info('Done!')
    else:
        logging.info('No slow queries over %ds!', timeout)
    return queries_str


def main():
    """Main entry."""
    logging.basicConfig(format='%(asctime)s %(message)s',
                        datefmt='%m/%d/%Y %H:%M:%S', level=logging.DEBUG)

    parser, options, args = parse_options()
    if not verify_options_and_args(options, args):
        parser.print_help()
        return 1
    try:
        result_log_strs = kill_slow_queries(options.user, options.password,
                                            options.timeout)
        if result_log_strs:
            gmail_lib.send_email(
                options.mail,
                'Successfully killed slow autotest db queries',
                'Below are killed queries:\n%s' % result_log_strs)
    except Exception as e:
        logging.error('Failed to kill slow db queries.\n%s', e)
        gmail_lib.send_email(
            options.mail,
            'Failed to kill slow autotest db queries.',
            ('Error occurred during killing slow db queries:\n%s\n'
             'Detailed logs can be found in /var/log/slow_queries.log on db '
             'backup server.\nTo avoid db crash, please check ASAP.') % e)
        raise


if __name__ == '__main__':
    sys.exit(main())

