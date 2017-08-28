# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Script to archive old Autotest results to Google Storage.

Uses gsutil to archive files to the configured Google Storage bucket.
Upon successful copy, the local results directory is deleted.
"""

import logging
import os

from apiclient import discovery
from oauth2client.client import ApplicationDefaultCredentialsError
from oauth2client.client import GoogleCredentials
from autotest_lib.server.hosts import moblab_host

import common

# Cloud service
# TODO(ntang): move this to config.
CLOUD_SERVICE_ACCOUNT_FILE = moblab_host.MOBLAB_SERVICE_ACCOUNT_LOCATION
PUBSUB_SERVICE_NAME = 'pubsub'
PUBSUB_VERSION = 'v1beta2'
PUBSUB_SCOPES = ['https://www.googleapis.com/auth/pubsub']
# number of retry to publish an event.
_PUBSUB_NUM_RETRIES = 3


def _get_pubsub_service():
    """Gets the pubsub service api handle."""
    if not os.path.isfile(CLOUD_SERVICE_ACCOUNT_FILE):
        logging.error('No credential file found')
        return None

    try:
        credentials = GoogleCredentials.from_stream(CLOUD_SERVICE_ACCOUNT_FILE)
        if credentials.create_scoped_required():
            credentials = credentials.create_scoped(PUBSUB_SCOPES)
        return discovery.build(PUBSUB_SERVICE_NAME, PUBSUB_VERSION,
                                 credentials=credentials)
    except ApplicationDefaultCredentialsError as ex:
        logging.error('Failed to get credential.')
    except:
        logging.error('Failed to get the pubsub service handle.')

    return None


def publish_notifications(topic, messages=[]):
    """Publishes a test result notification to a given pubsub topic.

    @param topic: The Cloud pubsub topic.
    @param messages: A list of notification messages.

    @returns A list of pubsub message ids, and empty if fails.
    """
    pubsub = _get_pubsub_service()
    if pubsub:
        try:
            body = {'messages': messages}
            resp = pubsub.projects().topics().publish(topic=topic,
                    body=body).execute(num_retries=_PUBSUB_NUM_RETRIES)
            if resp:
                msgIds = resp.get('messageIds')
                if msgIds:
                    logging.debug('Published notification message')
                    return msgIds
        except:
            pass
    logging.error('Failed to publish test result notifiation.')
    return []
