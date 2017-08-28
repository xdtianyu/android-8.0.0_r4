#!/usr/bin/env python3.4
#
#   Copyright 2016 - Google
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

######################################################
# Broadcast events
######################################################
BROADCAST_WIFI_AWARE_AVAILABLE = "WifiAwareAvailable"
BROADCAST_WIFI_AWARE_NOT_AVAILABLE = "WifiAwareNotAvailable"

######################################################
# ConfigRequest keys
######################################################

CONFIG_KEY_5G_BAND = "Support5gBand"
CONFIG_KEY_MASTER_PREF = "MasterPreference"
CONFIG_KEY_CLUSTER_LOW = "ClusterLow"
CONFIG_KEY_CLUSTER_HIGH = "ClusterHigh"
CONFIG_KEY_ENABLE_IDEN_CB = "EnableIdentityChangeCallback"

######################################################
# PublishConfig keys
######################################################

PUBLISH_KEY_SERVICE_NAME = "ServiceName"
PUBLISH_KEY_SSI = "ServiceSpecificInfo"
PUBLISH_KEY_MATCH_FILTER = "MatchFilter"
PUBLISH_KEY_TYPE = "PublishType"
PUBLISH_KEY_COUNT = "PublishCount"
PUBLISH_KEY_TTL = "TtlSec"
PUBLISH_KEY_TERM_CB_ENABLED = "TerminateNotificationEnabled"

######################################################
# SubscribeConfig keys
######################################################

SUBSCRIBE_KEY_SERVICE_NAME = "ServiceName"
SUBSCRIBE_KEY_SSI = "ServiceSpecificInfo"
SUBSCRIBE_KEY_MATCH_FILTER = "MatchFilter"
SUBSCRIBE_KEY_TYPE = "SubscribeType"
SUBSCRIBE_KEY_COUNT = "SubscribeCount"
SUBSCRIBE_KEY_TTL = "TtlSec"
SUBSCRIBE_KEY_STYLE = "MatchStyle"
SUBSCRIBE_KEY_ENABLE_TERM_CB = "EnableTerminateNotification"

######################################################
# WifiAwareAttachCallback events
######################################################
EVENT_CB_ON_ATTACHED = "WifiAwareOnAttached"
EVENT_CB_ON_ATTACH_FAILED = "WifiAwareOnAttachFailed"

######################################################
# WifiAwareIdentityChangedListener events
######################################################
EVENT_CB_ON_IDENTITY_CHANGED = "WifiAwareOnIdentityChanged"

# WifiAwareAttachCallback & WifiAwareIdentityChangedListener events keys
EVENT_CB_KEY_REASON = "reason"
EVENT_CB_KEY_MAC = "mac"
EVENT_CB_KEY_LATENCY_MS = "latencyMs"
EVENT_CB_KEY_TIMESTAMP_MS = "timestampMs"

######################################################
# WifiAwareDiscoverySessionCallback events
######################################################
SESSION_CB_ON_PUBLISH_STARTED = "WifiAwareSessionOnPublishStarted"
SESSION_CB_ON_SUBSCRIBE_STARTED = "WifiAwareSessionOnSubscribeStarted"
SESSION_CB_ON_SESSION_CONFIG_UPDATED = "WifiAwareSessionOnSessionConfigUpdated"
SESSION_CB_ON_SESSION_CONFIG_FAILED = "WifiAwareSessionOnSessionConfigFailed"
SESSION_CB_ON_SESSION_TERMINATED = "WifiAwareSessionOnSessionTerminated"
SESSION_CB_ON_SERVICE_DISCOVERED = "WifiAwareSessionOnServiceDiscovered"
SESSION_CB_ON_MESSAGE_SENT = "WifiAwareSessionOnMessageSent"
SESSION_CB_ON_MESSAGE_SEND_FAILED = "WifiAwareSessionOnMessageSendFailed"
SESSION_CB_ON_MESSAGE_RECEIVED = "WifiAwareSessionOnMessageReceived"

# WifiAwareDiscoverySessionCallback events keys
SESSION_CB_KEY_CB_ID = "callbackId"
SESSION_CB_KEY_SESSION_ID = "sessionId"
SESSION_CB_KEY_REASON = "reason"
SESSION_CB_KEY_PEER_ID = "peerId"
SESSION_CB_KEY_SERVICE_SPECIFIC_INFO = "serviceSpecificInfo"
SESSION_CB_KEY_MATCH_FILTER = "matchFilter"
SESSION_CB_KEY_MESSAGE = "message"
SESSION_CB_KEY_MESSAGE_ID = "messageId"
SESSION_CB_KEY_MESSAGE_AS_STRING = "messageAsString"
SESSION_CB_KEY_LATENCY_MS = "latencyMs"
SESSION_CB_KEY_TIMESTAMP_MS = "timestampMs"

######################################################
# WifiAwareRangingListener events (RttManager.RttListener)
######################################################
RTT_LISTENER_CB_ON_SUCCESS = "WifiAwareRangingListenerOnSuccess"
RTT_LISTENER_CB_ON_FAILURE = "WifiAwareRangingListenerOnFailure"
RTT_LISTENER_CB_ON_ABORT = "WifiAwareRangingListenerOnAborted"

# WifiAwareRangingListener events (RttManager.RttListener) keys
RTT_LISTENER_CB_KEY_CB_ID = "callbackId"
RTT_LISTENER_CB_KEY_SESSION_ID = "sessionId"
RTT_LISTENER_CB_KEY_RESULTS = "Results"
RTT_LISTENER_CB_KEY_REASON = "reason"
RTT_LISTENER_CB_KEY_DESCRIPTION = "description"

######################################################

# Aware Data-Path Constants
DATA_PATH_INITIATOR = 0
DATA_PATH_RESPONDER = 1

# Maximum send retry
MAX_TX_RETRIES = 5
