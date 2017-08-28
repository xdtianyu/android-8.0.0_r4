#!/usr/bin/python
# -*- coding:utf-8 -*-
# Copyright 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Wrapper to run pylint with the right settings."""

from __future__ import print_function

import argparse
import os
import sys

DEFAULT_PYLINTRC_PATH = os.path.join(
    os.path.dirname(os.path.realpath(__file__)), 'pylintrc')

def get_parser():
    """Return a command line parser."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--init-hook', help='Init hook commands to run.')
    parser.add_argument('--executable-path',
                        help='The path of the pylint executable.',
                        default='pylint')
    parser.add_argument('--no-rcfile',
                        help='Specify to use the executable\'s default '
                        'configuration.',
                        action='store_true')
    parser.add_argument('files', nargs='+')
    return parser


def main(argv):
    """The main entry."""
    parser = get_parser()
    opts, unknown = parser.parse_known_args(argv)

    pylintrc = DEFAULT_PYLINTRC_PATH if not opts.no_rcfile else None

    cmd = [opts.executable_path]
    if pylintrc:
        # If we pass a non-existent rcfile to pylint, it'll happily ignore it.
        assert os.path.exists(pylintrc), 'Could not find %s' % pylintrc
        cmd += ['--rcfile', pylintrc]

    cmd += unknown + opts.files

    if opts.init_hook:
        cmd += ['--init-hook', opts.init_hook]

    os.execvp(cmd[0], cmd)


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
