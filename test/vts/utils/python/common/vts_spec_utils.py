#
# Copyright (C) 2017 The Android Open Source Project
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
#

import logging
import os

from google.protobuf import text_format
from vts.proto import ComponentSpecificationMessage_pb2 as CompSpecMsg


def HalPackageToNameAndVersion(hal_package):
    """Returns hal name and version given hal package name.

    Args:
        hal_package: string, e.g. 'android.hardware.vibrator@1.0'

    Return:
        tuple, hal name and version e.g. ('vibrator', '1.0')
    """
    # TODO(trong): check if proper package name.
    prefix = 'android.hardware.'
    if not hal_package.startswith(prefix):
        logging.error("Invalid hal package name: %s" % hal_package)
    [hal_name, hal_version] = hal_package[len(prefix):].split('@')
    return (hal_name, hal_version)


class VtsSpecParser(object):
    """Provides an API to parse .vts spec files.

    Attributes:
        data_file_path: string, path to vts data directory on target.
    """

    def __init__(self, data_file_path):
        """VtsSpecParser constructor.

        Args:
            data_file_path: string, path to vts data directory on target.
        """
        self._data_file_path = data_file_path

    def _VtsSpecDir(self, hal_name, hal_version):
        """Returns directory path to .vts spec files.

        Args:
            hal_name: string, name of the hal, e.g. 'vibrator'.
            hal_version: string, version of the hal, e.g '7.4'

        Returns:
            string, directory path to .vts spec files.
        """
        return os.path.join(
            self._data_file_path, 'spec', 'hardware', 'interfaces',
            hal_name.replace('.', '/'), hal_version, 'vts')

    def VtsSpecNames(self, hal_name, hal_version):
        """Returns list of .vts file names for given hal name and version.

        Args:
            hal_name: string, name of the hal, e.g. 'vibrator'.
            hal_version: string, version of the hal, e.g '7.4'

        Returns:
            list of string, .vts files for given hal name and version,
              e.g. ['Vibrator.vts', 'types.vts']
        """
        vts_spec_names = filter(
            lambda x: x.endswith('.vts'),
            os.listdir(self._VtsSpecDir(hal_name, hal_version)))
        return sorted(vts_spec_names)

    def VtsSpecProto(self, hal_name, hal_version, vts_spec_name=''):
        """Returns list of .vts protos for given hal name and version.

        Args:
            hal_name: string, name of the hal, e.g. 'vibrator'.
            hal_version: string, version of the hal, e.g '7.4'
            vts_spec:

        Returns:
            list with all vts spec protos for a given hal and version if
            vts_spec_name is not given. If vts_spec_name is not empty, then
            returns ComponentSpecificationMessage matching vts_spec_name.
            If no such vts_spec_name, return None.
        """
        if not vts_spec_name:
            vts_spec_protos = []
            for vts_spec in self.VtsSpecNames(hal_name, hal_version):
                vts_spec_proto = self.VtsSpecProto(hal_name, hal_version,
                                                   vts_spec)
                vts_spec_protos.append(vts_spec_proto)
            return vts_spec_protos
        else:
            if vts_spec_name in self.VtsSpecNames(hal_name, hal_version):
                vts_spec_proto = CompSpecMsg.ComponentSpecificationMessage()
                vts_spec_path = os.path.join(
                    self._VtsSpecDir(hal_name, hal_version), vts_spec_name)
                with open(vts_spec_path, 'r') as vts_spec_file:
                    vts_spec_string = vts_spec_file.read()
                    text_format.Merge(vts_spec_string, vts_spec_proto)
                return vts_spec_proto
