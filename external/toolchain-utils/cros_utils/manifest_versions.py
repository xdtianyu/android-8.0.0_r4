# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Tools for searching/manipulating the manifests repository."""

from __future__ import print_function

__author__ = 'llozano@google.com (Luis Lozano)'

import os
import re
import shutil
import tempfile
import time

import command_executer
import logger


def IsCrosVersion(version):
  match = re.search(r'(\d+\.\d+\.\d+\.\d+)', version)
  return match is not None


def IsRFormatCrosVersion(version):
  match = re.search(r'(R\d+-\d+\.\d+\.\d+)', version)
  return match is not None


def RFormatCrosVersion(version):
  assert IsCrosVersion(version)
  tmp_major, tmp_minor = version.split('.', 1)
  rformat = 'R' + tmp_major + '-' + tmp_minor
  assert IsRFormatCrosVersion(rformat)
  return rformat


class ManifestVersions(object):
  """This class handles interactions with the manifests repo."""

  def __init__(self, internal=True):
    self.internal = internal
    self.clone_location = tempfile.mkdtemp()
    self.ce = command_executer.GetCommandExecuter()
    if internal:
      versions_git = ('https://chrome-internal.googlesource.com/'
                      'chromeos/manifest-versions.git')
    else:
      versions_git = (
          'https://chromium.googlesource.com/chromiumos/manifest-versions.git')
    commands = ['cd {0}'.format(self.clone_location),
                'git clone {0}'.format(versions_git)]
    ret = self.ce.RunCommands(commands)
    if ret:
      logger.GetLogger().LogFatal('Failed to clone manifest-versions.')

  def __del__(self):
    if self.clone_location:
      shutil.rmtree(self.clone_location)

  def TimeToVersion(self, my_time):
    """Convert timestamp to version number."""
    cur_time = time.mktime(time.gmtime())
    des_time = float(my_time)
    if cur_time - des_time > 7000000:
      logger.GetLogger().LogFatal('The time you specify is too early.')
    commands = ['cd {0}'.format(self.clone_location), 'cd manifest-versions',
                'git checkout -f $(git rev-list' +
                ' --max-count=1 --before={0} origin/master)'.format(my_time)]
    ret = self.ce.RunCommands(commands)
    if ret:
      logger.GetLogger().LogFatal('Failed to checkout manifest at '
                                  'specified time')
    path = os.path.realpath('{0}/manifest-versions/LKGM/lkgm.xml'.format(
        self.clone_location))
    pp = path.split('/')
    small = os.path.basename(path).split('.xml')[0]
    version = pp[-2] + '.' + small
    commands = ['cd {0}'.format(self.clone_location), 'cd manifest-versions',
                'git checkout master']
    self.ce.RunCommands(commands)
    return version

  def GetManifest(self, version, to_file):
    """Get the manifest file from a given chromeos-internal version."""
    assert not IsRFormatCrosVersion(version)
    version = version.split('.', 1)[1]
    os.chdir(self.clone_location)
    files = [os.path.join(r, f)
             for r, _, fs in os.walk('.') for f in fs if version in f]
    if files:
      command = 'cp {0} {1}'.format(files[0], to_file)
      ret = self.ce.RunCommand(command)
      if ret:
        raise RuntimeError('Cannot copy manifest to {0}'.format(to_file))
    else:
      raise RuntimeError('Version {0} is not available.'.format(version))
