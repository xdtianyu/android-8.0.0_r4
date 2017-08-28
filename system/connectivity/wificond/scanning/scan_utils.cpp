/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "wificond/scanning/scan_utils.h"

#include <vector>

#include <linux/netlink.h>
#include <linux/nl80211.h>

#include <android-base/logging.h>

#include "wificond/net/netlink_manager.h"
#include "wificond/net/nl80211_packet.h"
#include "wificond/scanning/scan_result.h"

using com::android::server::wifi::wificond::NativeScanResult;
using std::unique_ptr;
using std::vector;

namespace android {
namespace wificond {
namespace {

constexpr uint8_t kElemIdSsid = 0;

}  // namespace

ScanUtils::ScanUtils(NetlinkManager* netlink_manager)
    : netlink_manager_(netlink_manager) {
  if (!netlink_manager_->IsStarted()) {
    netlink_manager_->Start();
  }
}

ScanUtils::~ScanUtils() {}

void ScanUtils::SubscribeScanResultNotification(
    uint32_t interface_index,
    OnScanResultsReadyHandler handler) {
  netlink_manager_->SubscribeScanResultNotification(interface_index, handler);
}

void ScanUtils::UnsubscribeScanResultNotification(uint32_t interface_index) {
  netlink_manager_->UnsubscribeScanResultNotification(interface_index);
}

void ScanUtils::SubscribeSchedScanResultNotification(
    uint32_t interface_index,
    OnSchedScanResultsReadyHandler handler) {
  netlink_manager_->SubscribeSchedScanResultNotification(interface_index,
                                                         handler);
}

void ScanUtils::UnsubscribeSchedScanResultNotification(
    uint32_t interface_index) {
  netlink_manager_->UnsubscribeSchedScanResultNotification(interface_index);
}

bool ScanUtils::GetScanResult(uint32_t interface_index,
                              vector<NativeScanResult>* out_scan_results) {
  NL80211Packet get_scan(
      netlink_manager_->GetFamilyId(),
      NL80211_CMD_GET_SCAN,
      netlink_manager_->GetSequenceNumber(),
      getpid());
  get_scan.AddFlag(NLM_F_DUMP);
  NL80211Attr<uint32_t> ifindex(NL80211_ATTR_IFINDEX, interface_index);
  get_scan.AddAttribute(ifindex);

  vector<unique_ptr<const NL80211Packet>> response;
  if (!netlink_manager_->SendMessageAndGetResponses(get_scan, &response))  {
    LOG(ERROR) << "NL80211_CMD_GET_SCAN dump failed";
    return false;
  }
  if (response.empty()) {
    LOG(INFO) << "Unexpected empty scan result!";
    return true;
  }

  for (auto& packet : response) {
    if (packet->GetMessageType() == NLMSG_ERROR) {
      LOG(ERROR) << "Receive ERROR message: "
                 << strerror(packet->GetErrorCode());
      continue;
    }
    if (packet->GetMessageType() != netlink_manager_->GetFamilyId()) {
      LOG(ERROR) << "Wrong message type: "
                 << packet->GetMessageType();
      continue;
    }
    uint32_t if_index;
    if (!packet->GetAttributeValue(NL80211_ATTR_IFINDEX, &if_index)) {
      LOG(ERROR) << "No interface index in scan result.";
      continue;
    }
    if (if_index != interface_index) {
      LOG(WARNING) << "Uninteresting scan result for interface: " << if_index;
      continue;
    }

    NativeScanResult scan_result;
    if (!ParseScanResult(std::move(packet), &scan_result)) {
      LOG(DEBUG) << "Ignore invalid scan result";
      continue;
    }
    out_scan_results->push_back(std::move(scan_result));
  }
  return true;
}

bool ScanUtils::ParseScanResult(unique_ptr<const NL80211Packet> packet,
                                NativeScanResult* scan_result) {
  if (packet->GetCommand() != NL80211_CMD_NEW_SCAN_RESULTS) {
    LOG(ERROR) << "Wrong command command for new scan result message";
    return false;
  }
  NL80211NestedAttr bss(0);
  if (packet->GetAttribute(NL80211_ATTR_BSS, &bss)) {
    vector<uint8_t> bssid;
    if (!bss.GetAttributeValue(NL80211_BSS_BSSID, &bssid)) {
      LOG(ERROR) << "Failed to get BSSID from scan result packet";
      return false;
    }
    uint32_t freq;
    if (!bss.GetAttributeValue(NL80211_BSS_FREQUENCY, &freq)) {
      LOG(ERROR) << "Failed to get Frequency from scan result packet";
      return false;
    }
    vector<uint8_t> ie;
    if (!bss.GetAttributeValue(NL80211_BSS_INFORMATION_ELEMENTS, &ie)) {
      LOG(ERROR) << "Failed to get Information Element from scan result packet";
      return false;
    }
    vector<uint8_t> ssid;
    if (!GetSSIDFromInfoElement(ie, &ssid)) {
      // Skip BSS without SSID IE.
      // These scan results are considered as malformed.
      return false;
    }
    uint64_t tsf;
    if (!bss.GetAttributeValue(NL80211_BSS_TSF, &tsf)) {
      LOG(ERROR) << "Failed to get TSF from scan result packet";
      return false;
    }
    uint64_t beacon_tsf;
    if (bss.GetAttributeValue(NL80211_BSS_BEACON_TSF, &beacon_tsf)) {
      if (beacon_tsf > tsf) {
        tsf = beacon_tsf;
      }
    }
    int32_t signal;
    if (!bss.GetAttributeValue(NL80211_BSS_SIGNAL_MBM, &signal)) {
      LOG(ERROR) << "Failed to get Signal Strength from scan result packet";
      return false;
    }
    uint16_t capability;
    if (!bss.GetAttributeValue(NL80211_BSS_CAPABILITY, &capability)) {
      LOG(ERROR) << "Failed to get capability field from scan result packet";
      return false;
    }
    bool associated = false;
    uint32_t bss_status;
    if (bss.GetAttributeValue(NL80211_BSS_STATUS, &bss_status) &&
            (bss_status == NL80211_BSS_STATUS_AUTHENTICATED ||
                bss_status == NL80211_BSS_STATUS_ASSOCIATED)) {
      associated = true;
    }

    *scan_result =
        NativeScanResult(ssid, bssid, ie, freq, signal, tsf, capability, associated);
  }
  return true;
}

bool ScanUtils::GetSSIDFromInfoElement(const vector<uint8_t>& ie,
                                       vector<uint8_t>* ssid) {
  // Information elements are stored in 'TLV' format.
  // Field:  |   Type     |          Length           |      Value      |
  // Length: |     1      |             1             |     variable    |
  // Content:| Element ID | Length of the Value field | Element payload |
  const uint8_t* end = ie.data() + ie.size();
  const uint8_t* ptr = ie.data();
  // +1 means we must have space for the length field.
  while (ptr + 1  < end) {
    uint8_t type = *ptr;
    uint8_t length = *(ptr + 1);
    // Length field is invalid.
    if (ptr + 1 + length >= end) {
      return false;
    }
    // SSID element is found.
    if (type == kElemIdSsid) {
      // SSID is an empty string.
      if (length == 0) {
        *ssid = vector<uint8_t>();
      } else {
        *ssid = vector<uint8_t>(ptr + 2, ptr + length + 2);
      }
      return true;
    }
    ptr += 2 + length;
  }
  return false;
}

bool ScanUtils::Scan(uint32_t interface_index,
                     bool request_random_mac,
                     const vector<vector<uint8_t>>& ssids,
                     const vector<uint32_t>& freqs) {
  NL80211Packet trigger_scan(
      netlink_manager_->GetFamilyId(),
      NL80211_CMD_TRIGGER_SCAN,
      netlink_manager_->GetSequenceNumber(),
      getpid());
  // If we do not use NLM_F_ACK, we only receive a unicast repsonse
  // when there is an error. If everything is good, scan results notification
  // will only be sent through multicast.
  // If NLM_F_ACK is set, there will always be an unicast repsonse, either an
  // ERROR or an ACK message. The handler will always be called and removed by
  // NetlinkManager.
  trigger_scan.AddFlag(NLM_F_ACK);
  NL80211Attr<uint32_t> if_index_attr(NL80211_ATTR_IFINDEX, interface_index);

  NL80211NestedAttr ssids_attr(NL80211_ATTR_SCAN_SSIDS);
  for (size_t i = 0; i < ssids.size(); i++) {
    ssids_attr.AddAttribute(NL80211Attr<vector<uint8_t>>(i, ssids[i]));
  }
  NL80211NestedAttr freqs_attr(NL80211_ATTR_SCAN_FREQUENCIES);
  for (size_t i = 0; i < freqs.size(); i++) {
    freqs_attr.AddAttribute(NL80211Attr<uint32_t>(i, freqs[i]));
  }

  trigger_scan.AddAttribute(if_index_attr);
  trigger_scan.AddAttribute(ssids_attr);
  // An absence of NL80211_ATTR_SCAN_FREQUENCIES attribue informs kernel to
  // scan all supported frequencies.
  if (!freqs.empty()) {
    trigger_scan.AddAttribute(freqs_attr);
  }

  if (request_random_mac) {
    trigger_scan.AddAttribute(
        NL80211Attr<uint32_t>(NL80211_ATTR_SCAN_FLAGS,
                              NL80211_SCAN_FLAG_RANDOM_ADDR));
  }
  // We are receiving an ERROR/ACK message instead of the actual
  // scan results here, so it is OK to expect a timely response because
  // kernel is supposed to send the ERROR/ACK back before the scan starts.
  vector<unique_ptr<const NL80211Packet>> response;
  if (!netlink_manager_->SendMessageAndGetAck(trigger_scan)) {
    LOG(ERROR) << "NL80211_CMD_TRIGGER_SCAN failed";
    return false;
  }
  return true;
}

bool ScanUtils::StopScheduledScan(uint32_t interface_index) {
  NL80211Packet stop_sched_scan(
      netlink_manager_->GetFamilyId(),
      NL80211_CMD_STOP_SCHED_SCAN,
      netlink_manager_->GetSequenceNumber(),
      getpid());
  // Force an ACK response upon success.
  stop_sched_scan.AddFlag(NLM_F_ACK);
  stop_sched_scan.AddAttribute(
      NL80211Attr<uint32_t>(NL80211_ATTR_IFINDEX, interface_index));
  vector<unique_ptr<const NL80211Packet>> response;
  int error_code;
  if (!netlink_manager_->SendMessageAndGetAckOrError(stop_sched_scan,
                                                     &error_code))  {
    LOG(ERROR) << "NL80211_CMD_STOP_SCHED_SCAN failed";
    return false;
  }
  if (error_code == ENOENT) {
    LOG(WARNING) << "Scheduled scan is not running!";
    return false;
  } else if (error_code != 0) {
    LOG(ERROR) << "Receive ERROR message in response to"
               << " 'stop scheduled scan' request: "
               << strerror(error_code);
    return false;
  }
  return true;
}

bool ScanUtils::AbortScan(uint32_t interface_index) {
  NL80211Packet abort_scan(
      netlink_manager_->GetFamilyId(),
      NL80211_CMD_ABORT_SCAN,
      netlink_manager_->GetSequenceNumber(),
      getpid());

  // Force an ACK response upon success.
  abort_scan.AddFlag(NLM_F_ACK);
  abort_scan.AddAttribute(
      NL80211Attr<uint32_t>(NL80211_ATTR_IFINDEX, interface_index));

  if (!netlink_manager_->SendMessageAndGetAck(abort_scan)) {
    LOG(ERROR) << "NL80211_CMD_ABORT_SCAN failed";
    return false;
  }
  return true;
}

bool ScanUtils::StartScheduledScan(
    uint32_t interface_index,
    uint32_t interval_ms,
    int32_t rssi_threshold,
    bool request_random_mac,
    const std::vector<std::vector<uint8_t>>& scan_ssids,
    const std::vector<std::vector<uint8_t>>& match_ssids,
    const std::vector<uint32_t>& freqs) {
  NL80211Packet start_sched_scan(
      netlink_manager_->GetFamilyId(),
      NL80211_CMD_START_SCHED_SCAN,
      netlink_manager_->GetSequenceNumber(),
      getpid());
  // Force an ACK response upon success.
  start_sched_scan.AddFlag(NLM_F_ACK);

  NL80211NestedAttr scan_ssids_attr(NL80211_ATTR_SCAN_SSIDS);
  for (size_t i = 0; i < scan_ssids.size(); i++) {
    scan_ssids_attr.AddAttribute(NL80211Attr<vector<uint8_t>>(i, scan_ssids[i]));
  }
  NL80211NestedAttr freqs_attr(NL80211_ATTR_SCAN_FREQUENCIES);
  for (size_t i = 0; i < freqs.size(); i++) {
    freqs_attr.AddAttribute(NL80211Attr<uint32_t>(i, freqs[i]));
  }

  //   Structure of attributes of scheduled scan filters:
  // |                                Nested Attribute: id: NL80211_ATTR_SCHED_SCAN_MATCH                           |
  // |     Nested Attributed: id: 0       |    Nested Attributed: id: 1         |      Nested Attr: id: 2     | ... |
  // | MATCH_SSID  | MATCH_RSSI(optional) | MATCH_SSID  | MACTCH_RSSI(optional) | MATCH_RSSI(optinal, global) | ... |
  NL80211NestedAttr scan_match_attr(NL80211_ATTR_SCHED_SCAN_MATCH);
  for (size_t i = 0; i < match_ssids.size(); i++) {
    NL80211NestedAttr match_group(i);
    match_group.AddAttribute(
        NL80211Attr<vector<uint8_t>>(NL80211_SCHED_SCAN_MATCH_ATTR_SSID, match_ssids[i]));
    match_group.AddAttribute(
        NL80211Attr<int32_t>(NL80211_SCHED_SCAN_MATCH_ATTR_RSSI, rssi_threshold));
    scan_match_attr.AddAttribute(match_group);
  }

  // Append all attributes to the NL80211_CMD_START_SCHED_SCAN packet.
  start_sched_scan.AddAttribute(
      NL80211Attr<uint32_t>(NL80211_ATTR_IFINDEX, interface_index));
  start_sched_scan.AddAttribute(scan_ssids_attr);
  // An absence of NL80211_ATTR_SCAN_FREQUENCIES attribue informs kernel to
  // scan all supported frequencies.
  if (!freqs.empty()) {
    start_sched_scan.AddAttribute(freqs_attr);
  }
  start_sched_scan.AddAttribute(
      NL80211Attr<uint32_t>(NL80211_ATTR_SCHED_SCAN_INTERVAL, interval_ms));
  start_sched_scan.AddAttribute(scan_match_attr);
  if (request_random_mac) {
    start_sched_scan.AddAttribute(
        NL80211Attr<uint32_t>(NL80211_ATTR_SCAN_FLAGS,
                              NL80211_SCAN_FLAG_RANDOM_ADDR));
  }

  vector<unique_ptr<const NL80211Packet>> response;
  if (!netlink_manager_->SendMessageAndGetAck(start_sched_scan)) {
    LOG(ERROR) << "NL80211_CMD_START_SCHED_SCAN failed";
    return false;
  }

  return true;
}

}  // namespace wificond
}  // namespace android
