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

#include "wificond/net/netlink_utils.h"

#include <string>
#include <vector>

#include <linux/netlink.h>
#include <linux/nl80211.h>

#include <android-base/logging.h>

#include "wificond/net/mlme_event_handler.h"
#include "wificond/net/nl80211_packet.h"

using std::string;
using std::unique_ptr;
using std::vector;

namespace android {
namespace wificond {

namespace {

uint32_t k2GHzFrequencyLowerBound = 2400;
uint32_t k2GHzFrequencyUpperBound = 2500;

}  // namespace
NetlinkUtils::NetlinkUtils(NetlinkManager* netlink_manager)
    : netlink_manager_(netlink_manager) {
  if (!netlink_manager_->IsStarted()) {
    netlink_manager_->Start();
  }
}

NetlinkUtils::~NetlinkUtils() {}

bool NetlinkUtils::GetWiphyIndex(uint32_t* out_wiphy_index) {
  NL80211Packet get_wiphy(
      netlink_manager_->GetFamilyId(),
      NL80211_CMD_GET_WIPHY,
      netlink_manager_->GetSequenceNumber(),
      getpid());
  get_wiphy.AddFlag(NLM_F_DUMP);
  vector<unique_ptr<const NL80211Packet>> response;
  if (!netlink_manager_->SendMessageAndGetResponses(get_wiphy, &response))  {
    LOG(ERROR) << "NL80211_CMD_GET_WIPHY dump failed";
    return false;
  }
  if (response.empty()) {
    LOG(DEBUG) << "No wiphy is found";
    return false;
  }
  for (auto& packet : response) {
    if (packet->GetMessageType() == NLMSG_ERROR) {
      LOG(ERROR) << "Receive ERROR message: "
                 << strerror(packet->GetErrorCode());
      return false;
    }
    if (packet->GetMessageType() != netlink_manager_->GetFamilyId()) {
      LOG(ERROR) << "Wrong message type for new interface message: "
                 << packet->GetMessageType();
      return false;
    }
    if (packet->GetCommand() != NL80211_CMD_NEW_WIPHY) {
      LOG(ERROR) << "Wrong command in response to "
                 << "a wiphy dump request: "
                 << static_cast<int>(packet->GetCommand());
      return false;
    }
    if (!packet->GetAttributeValue(NL80211_ATTR_WIPHY, out_wiphy_index)) {
      LOG(ERROR) << "Failed to get wiphy index from reply message";
      return false;
    }
  }
  return true;
}

bool NetlinkUtils::GetInterfaces(uint32_t wiphy_index,
                                 vector<InterfaceInfo>* interface_info) {
  NL80211Packet get_interfaces(
      netlink_manager_->GetFamilyId(),
      NL80211_CMD_GET_INTERFACE,
      netlink_manager_->GetSequenceNumber(),
      getpid());

  get_interfaces.AddFlag(NLM_F_DUMP);
  get_interfaces.AddAttribute(
      NL80211Attr<uint32_t>(NL80211_ATTR_WIPHY, wiphy_index));
  vector<unique_ptr<const NL80211Packet>> response;
  if (!netlink_manager_->SendMessageAndGetResponses(get_interfaces, &response)) {
    LOG(ERROR) << "NL80211_CMD_GET_INTERFACE dump failed";
    return false;
  }
  if (response.empty()) {
    LOG(ERROR) << "No interface is found";
    return false;
  }
  for (auto& packet : response) {
    if (packet->GetMessageType() == NLMSG_ERROR) {
      LOG(ERROR) << "Receive ERROR message: "
                 << strerror(packet->GetErrorCode());
      return false;
    }
    if (packet->GetMessageType() != netlink_manager_->GetFamilyId()) {
      LOG(ERROR) << "Wrong message type for new interface message: "
                 << packet->GetMessageType();
      return false;
    }
    if (packet->GetCommand() != NL80211_CMD_NEW_INTERFACE) {
      LOG(ERROR) << "Wrong command in response to "
                 << "an interface dump request: "
                 << static_cast<int>(packet->GetCommand());
      return false;
    }

    // In some situations, it has been observed that the kernel tells us
    // about a pseudo interface that does not have a real netdev.  In this
    // case, responses will have a NL80211_ATTR_WDEV, and not the expected
    // IFNAME/IFINDEX. In this case we just skip these pseudo interfaces.
    uint32_t if_index;
    if (!packet->GetAttributeValue(NL80211_ATTR_IFINDEX, &if_index)) {
      LOG(DEBUG) << "Failed to get interface index";
      continue;
    }

    // Today we don't check NL80211_ATTR_IFTYPE because at this point of time
    // driver always reports that interface is in STATION mode. Even when we
    // are asking interfaces infomation on behalf of tethering, it is still so
    // because hostapd is supposed to set interface to AP mode later.

    string if_name;
    if (!packet->GetAttributeValue(NL80211_ATTR_IFNAME, &if_name)) {
      LOG(WARNING) << "Failed to get interface name";
      continue;
    }

    vector<uint8_t> if_mac_addr;
    if (!packet->GetAttributeValue(NL80211_ATTR_MAC, &if_mac_addr)) {
      LOG(WARNING) << "Failed to get interface mac address";
      continue;
    }

    interface_info->emplace_back(if_index, if_name, if_mac_addr);
  }

  return true;
}

bool NetlinkUtils::SetInterfaceMode(uint32_t interface_index,
                                    InterfaceMode mode) {
  uint32_t set_to_mode = NL80211_IFTYPE_UNSPECIFIED;
  if (mode == STATION_MODE) {
    set_to_mode = NL80211_IFTYPE_STATION;
  } else {
    LOG(ERROR) << "Unexpected mode for interface with index: "
               << interface_index;
    return false;
  }
  NL80211Packet set_interface_mode(
      netlink_manager_->GetFamilyId(),
      NL80211_CMD_SET_INTERFACE,
      netlink_manager_->GetSequenceNumber(),
      getpid());
  // Force an ACK response upon success.
  set_interface_mode.AddFlag(NLM_F_ACK);

  set_interface_mode.AddAttribute(
      NL80211Attr<uint32_t>(NL80211_ATTR_IFINDEX, interface_index));
  set_interface_mode.AddAttribute(
      NL80211Attr<uint32_t>(NL80211_ATTR_IFTYPE, set_to_mode));

  if (!netlink_manager_->SendMessageAndGetAck(set_interface_mode)) {
    LOG(ERROR) << "NL80211_CMD_SET_INTERFACE failed";
    return false;
  }

  return true;
}

bool NetlinkUtils::GetWiphyInfo(
    uint32_t wiphy_index,
    BandInfo* out_band_info,
    ScanCapabilities* out_scan_capabilities,
    WiphyFeatures* out_wiphy_features) {
  NL80211Packet get_wiphy(
      netlink_manager_->GetFamilyId(),
      NL80211_CMD_GET_WIPHY,
      netlink_manager_->GetSequenceNumber(),
      getpid());
  get_wiphy.AddAttribute(NL80211Attr<uint32_t>(NL80211_ATTR_WIPHY, wiphy_index));
  unique_ptr<const NL80211Packet> response;
  if (!netlink_manager_->SendMessageAndGetSingleResponse(get_wiphy,
                                                         &response)) {
    LOG(ERROR) << "NL80211_CMD_GET_WIPHY failed";
    return false;
  }
  if (response->GetCommand() != NL80211_CMD_NEW_WIPHY) {
    LOG(ERROR) << "Wrong command in response to a get wiphy request: "
               << static_cast<int>(response->GetCommand());
    return false;
  }
  if (!ParseBandInfo(response.get(), out_band_info) ||
      !ParseScanCapabilities(response.get(), out_scan_capabilities)) {
    return false;
  }
  uint32_t feature_flags;
  if (!response->GetAttributeValue(NL80211_ATTR_FEATURE_FLAGS,
                                   &feature_flags)) {
    LOG(ERROR) << "Failed to get NL80211_ATTR_FEATURE_FLAGS";
    return false;
  }
  *out_wiphy_features = WiphyFeatures(feature_flags);
  return true;
}

bool NetlinkUtils::ParseScanCapabilities(
    const NL80211Packet* const packet,
    ScanCapabilities* out_scan_capabilities) {
  uint8_t max_num_scan_ssids;
  if (!packet->GetAttributeValue(NL80211_ATTR_MAX_NUM_SCAN_SSIDS,
                                   &max_num_scan_ssids)) {
    LOG(ERROR) << "Failed to get the capacity of maximum number of scan ssids";
    return false;
  }

  uint8_t max_num_sched_scan_ssids;
  if (!packet->GetAttributeValue(NL80211_ATTR_MAX_NUM_SCHED_SCAN_SSIDS,
                                 &max_num_sched_scan_ssids)) {
    LOG(ERROR) << "Failed to get the capacity of "
               << "maximum number of scheduled scan ssids";
    return false;
  }

  uint8_t max_match_sets;
  if (!packet->GetAttributeValue(NL80211_ATTR_MAX_MATCH_SETS,
                                   &max_match_sets)) {
    LOG(ERROR) << "Failed to get the capacity of maximum number of match set"
               << "of a scheduled scan";
    return false;
  }
  *out_scan_capabilities = ScanCapabilities(max_num_scan_ssids,
                                            max_num_sched_scan_ssids,
                                            max_match_sets);
  return true;
}

bool NetlinkUtils::ParseBandInfo(const NL80211Packet* const packet,
                                 BandInfo* out_band_info) {

  NL80211NestedAttr bands_attr(0);
  if (!packet->GetAttribute(NL80211_ATTR_WIPHY_BANDS, &bands_attr)) {
    LOG(ERROR) << "Failed to get NL80211_ATTR_WIPHY_BANDS";
    return false;
  }
  vector<NL80211NestedAttr> bands;
  if (!bands_attr.GetListOfNestedAttributes(&bands)) {
    LOG(ERROR) << "Failed to get bands within NL80211_ATTR_WIPHY_BANDS";
    return false;
  }
  vector<uint32_t> frequencies_2g;
  vector<uint32_t> frequencies_5g;
  vector<uint32_t> frequencies_dfs;
  for (unsigned int band_index = 0; band_index < bands.size(); band_index++) {
    NL80211NestedAttr freqs_attr(0);
    if (!bands[band_index].GetAttribute(NL80211_BAND_ATTR_FREQS, &freqs_attr)) {
      LOG(DEBUG) << "Failed to get NL80211_BAND_ATTR_FREQS";
      continue;
    }
    vector<NL80211NestedAttr> freqs;
    if (!freqs_attr.GetListOfNestedAttributes(&freqs)) {
      LOG(ERROR) << "Failed to get frequencies within NL80211_BAND_ATTR_FREQS";
      continue;
    }
    for (auto& freq : freqs) {
      uint32_t frequency_value;
      if (!freq.GetAttributeValue(NL80211_FREQUENCY_ATTR_FREQ,
                                  &frequency_value)) {
        LOG(DEBUG) << "Failed to get NL80211_FREQUENCY_ATTR_FREQ";
        continue;
      }
      // Channel is disabled in current regulatory domain.
      if (freq.HasAttribute(NL80211_FREQUENCY_ATTR_DISABLED)) {
        continue;
      }
      // If this is an available/usable DFS frequency, we should save it to
      // DFS frequencies list.
      uint32_t dfs_state;
      if (freq.GetAttributeValue(NL80211_FREQUENCY_ATTR_DFS_STATE,
                                 &dfs_state) &&
          (dfs_state == NL80211_DFS_AVAILABLE ||
               dfs_state == NL80211_DFS_USABLE)) {
        frequencies_dfs.push_back(frequency_value);
      } else {
        // Since there is no guarantee for the order of band attributes,
        // we do some math here.
        if (frequency_value > k2GHzFrequencyLowerBound &&
            frequency_value < k2GHzFrequencyUpperBound) {
          frequencies_2g.push_back(frequency_value);
        } else {
          frequencies_5g.push_back(frequency_value);
        }
      }
    }
  }
  *out_band_info = BandInfo(frequencies_2g, frequencies_5g, frequencies_dfs);
  return true;
}

bool NetlinkUtils::GetStationInfo(uint32_t interface_index,
                                  const vector<uint8_t>& mac_address,
                                  StationInfo* out_station_info) {
  NL80211Packet get_station(
      netlink_manager_->GetFamilyId(),
      NL80211_CMD_GET_STATION,
      netlink_manager_->GetSequenceNumber(),
      getpid());
  get_station.AddAttribute(NL80211Attr<uint32_t>(NL80211_ATTR_IFINDEX,
                                                 interface_index));
  get_station.AddAttribute(NL80211Attr<vector<uint8_t>>(NL80211_ATTR_MAC,
                                                        mac_address));

  unique_ptr<const NL80211Packet> response;
  if (!netlink_manager_->SendMessageAndGetSingleResponse(get_station,
                                                         &response)) {
    LOG(ERROR) << "NL80211_CMD_GET_STATION failed";
    return false;
  }
  if (response->GetCommand() != NL80211_CMD_NEW_STATION) {
    LOG(ERROR) << "Wrong command in response to a get station request: "
               << static_cast<int>(response->GetCommand());
    return false;
  }
  NL80211NestedAttr sta_info(0);
  if (!response->GetAttribute(NL80211_ATTR_STA_INFO, &sta_info)) {
    LOG(ERROR) << "Failed to get NL80211_ATTR_STA_INFO";
    return false;
  }
  int32_t tx_good, tx_bad;
  if (!sta_info.GetAttributeValue(NL80211_STA_INFO_TX_PACKETS, &tx_good)) {
    LOG(ERROR) << "Failed to get NL80211_STA_INFO_TX_PACKETS";
    return false;
  }
  if (!sta_info.GetAttributeValue(NL80211_STA_INFO_TX_FAILED, &tx_bad)) {
    LOG(ERROR) << "Failed to get NL80211_STA_INFO_TX_FAILED";
    return false;
  }
  int8_t current_rssi;
  if (!sta_info.GetAttributeValue(NL80211_STA_INFO_SIGNAL, &current_rssi)) {
    LOG(ERROR) << "Failed to get NL80211_STA_INFO_SIGNAL";
    return false;
  }
  NL80211NestedAttr tx_bitrate_attr(0);
  if (!sta_info.GetAttribute(NL80211_STA_INFO_TX_BITRATE,
                            &tx_bitrate_attr)) {
    LOG(ERROR) << "Failed to get NL80211_STA_INFO_TX_BITRATE";
    return false;
  }
  uint32_t tx_bitrate;
  if (!tx_bitrate_attr.GetAttributeValue(NL80211_RATE_INFO_BITRATE32,
                                         &tx_bitrate)) {
    LOG(ERROR) << "Failed to get NL80211_RATE_INFO_BITRATE32";
    return false;
  }

  *out_station_info = StationInfo(tx_good, tx_bad, tx_bitrate, current_rssi);
  return true;
}

void NetlinkUtils::SubscribeMlmeEvent(uint32_t interface_index,
                                      MlmeEventHandler* handler) {
  netlink_manager_->SubscribeMlmeEvent(interface_index, handler);
}

void NetlinkUtils::UnsubscribeMlmeEvent(uint32_t interface_index) {
  netlink_manager_->UnsubscribeMlmeEvent(interface_index);
}

void NetlinkUtils::SubscribeRegDomainChange(
    uint32_t wiphy_index,
    OnRegDomainChangedHandler handler) {
  netlink_manager_->SubscribeRegDomainChange(wiphy_index, handler);
}

void NetlinkUtils::UnsubscribeRegDomainChange(uint32_t wiphy_index) {
  netlink_manager_->UnsubscribeRegDomainChange(wiphy_index);
}

void NetlinkUtils::SubscribeStationEvent(uint32_t interface_index,
                                         OnStationEventHandler handler) {
  netlink_manager_->SubscribeStationEvent(interface_index, handler);
}

void NetlinkUtils::UnsubscribeStationEvent(uint32_t interface_index) {
  netlink_manager_->UnsubscribeStationEvent(interface_index);
}

}  // namespace wificond
}  // namespace android
