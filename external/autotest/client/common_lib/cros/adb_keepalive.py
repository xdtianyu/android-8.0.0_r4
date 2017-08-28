#!/usr/bin/python

# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import argparse
import logging
import pipes
import time

import common
from autotest_lib.client.bin import utils

_ADB_POLLING_INTERVAL_SECONDS = 10
_ADB_CONNECT_INTERVAL_SECONDS = 1


def _is_adb_connected():
    """Return true if adb is connected to the container."""
    output = utils.system_output('adb get-state', ignore_status=True)
    logging.debug('adb get-state: %s', output)
    return output.strip() == 'device'


def _ensure_adb_connected(target):
    """Ensures adb is connected to the container, reconnects otherwise."""
    while not _is_adb_connected():
        logging.info('adb not connected. attempting to reconnect')
        output = utils.system_output('adb connect %s' % pipes.quote(target),
                                     ignore_status=True)
        logging.debug('adb connect %s: %s', target, output)
        time.sleep(_ADB_CONNECT_INTERVAL_SECONDS)


if __name__ == '__main__':
    logging.basicConfig(level=logging.DEBUG)
    parser = argparse.ArgumentParser(description='ensure adb is connected')
    parser.add_argument('target', help='Device to connect to')
    args = parser.parse_args()

    logging.info('Starting adb_keepalive for target %s', args.target)

    while True:
        try:
            time.sleep(_ADB_POLLING_INTERVAL_SECONDS)
            _ensure_adb_connected(args.target)
        except KeyboardInterrupt:
            logging.info('Shutting down')
            break
