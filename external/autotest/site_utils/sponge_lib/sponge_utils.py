# Copyright 2017 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""This module contains utilities for test to report result to Sponge.
"""

import logging
import traceback

from autotest_lib.site_utils.sponge_lib import autotest_dynamic_job
from autotest_lib.client.common_lib import decorators

try:
    import sponge
except ImportError:
    logging.debug('Module failed to be imported: sponge')
    sponge = None



class SpongeLogHandler(logging.Handler):
    """Helper log handler for logging during sponge."""
    def __init__(self, log_func):
        super(SpongeLogHandler, self).__init__()
        self.log_func = log_func

    def emit(self, record):
        log_entry = self.format(record)
        self.log_func(log_entry)



@decorators.test_module_available(sponge)
def upload_results(job, log=logging.debug):
    """Upload test results to Sponge with given job details.

    @param job: A job object created by tko/parsers.
    @param log: Logging method, default is logging.debug.

    @return: A url to the Sponge invocation.
    """
    start_level = logging.getLogger().level

    log_handler = SpongeLogHandler(log)
    logging.getLogger().addHandler(log_handler)
    logging.getLogger().setLevel(logging.DEBUG)

    logging.info("added log handler")

    try:
        logging.info('Starting sponge upload.')
        info = autotest_dynamic_job.DynamicJobInfo(job)
        return sponge.upload_utils.UploadInfo(info)
    except:
        stack = traceback.format_exc()
        logging.info('Failed to upload to sponge.')
        logging.info(str(stack))
    finally:
        logging.getLogger().removeHandler(log_handler)
        logging.getLogger().setLevel(start_level)
