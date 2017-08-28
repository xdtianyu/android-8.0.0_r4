# Copyright 2017 Google Inc. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Stub host object for Libiota devices."""

import common

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import dev_server
from autotest_lib.server import afe_utils
from autotest_lib.server.cros.dynamic_suite import constants as ds_constants
from autotest_lib.server.cros.dynamic_suite import tools
from autotest_lib.server.hosts import abstract_ssh


ARCHIVE_URL = ('https://pantheon.corp.google.com/storage/browser/'
               'abuildbot-build-archive/bbuildbot/libiota')


class IotaHost(abstract_ssh.AbstractSSHHost):
    """Provides a stub for downloading the Libiota autotest package."""

    VERSION_PREFIX = 'iota-version'

    def stage_server_side_package(self, image=None):
        """Stage autotest server-side package on devserver.

        @param image: Full path of an OS image to install or a build name.

        @return: A url to the autotest server-side package.

        @raise: error.AutoservError if fail to locate the build to test with, or
                fail to stage server-side package.
        """
        if image:
            image_name = tools.get_build_from_image(image)
            if not image_name:
                raise error.AutoservError(
                        'Failed to parse build name from %s' % image)
            ds = dev_server.ImageServer.resolve(image_name)
        else:
            job_repo_url = afe_utils.get_host_attribute(
                    self, ds_constants.JOB_REPO_URL)
            if job_repo_url:
                devserver_url, image_name = (
                        tools.get_devserver_build_from_package_url(job_repo_url)
                )
                ds = dev_server.ImageServer.resolve(image_name)
            else:
                labels = afe_utils.get_labels(self, self.VERSION_PREFIX)
                if not labels:
                    raise error.AutoservError(
                            'Failed to stage server-side package. The host has '
                            'no job_report_url attribute or version label.')
                image_name = labels[0][len(self.VERSION_PREFIX + ':'):]
                ds = dev_server.ImageServer.resolve(image_name)

        ds.stage_artifacts(image_name, ['autotest_server_package'],
                           archive_url=ARCHIVE_URL+image_name)
        return '%s/static/%s/%s' % (ds.url(), image_name,
                                    'autotest_server_package.tar.bz2')
