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

#include "wificond/client_interface_impl.h"

#include <vector>

#include <android-base/logging.h>
#include <wifi_system/supplicant_manager.h>

#include "wificond/client_interface_binder.h"
#include "wificond/net/mlme_event.h"
#include "wificond/net/netlink_utils.h"
#include "wificond/scanning/scan_result.h"
#include "wificond/scanning/scan_utils.h"
#include "wificond/scanning/scanner_impl.h"

using android::net::wifi::IClientInterface;
using com::android::server::wifi::wificond::NativeScanResult;
using android::sp;
using android::wifi_system::InterfaceTool;
using android::wifi_system::SupplicantManager;

using std::endl;
using std::string;
using std::unique_ptr;
using std::vector;

namespace android {
namespace wificond {

MlmeEventHandlerImpl::MlmeEventHandlerImpl(ClientInterfaceImpl* client_interface)
    : client_interface_(client_interface) {
}

MlmeEventHandlerImpl::~MlmeEventHandlerImpl() {
}

void MlmeEventHandlerImpl::OnConnect(unique_ptr<MlmeConnectEvent> event) {
  if (!event->IsTimeout() && event->GetStatusCode() == 0) {
    client_interface_->is_associated_ = true;
    client_interface_->RefreshAssociateFreq();
    client_interface_->bssid_ = event->GetBSSID();
  } else {
    if (event->IsTimeout()) {
      LOG(INFO) << "Connect timeout";
    }
    client_interface_->is_associated_ = false;
    client_interface_->bssid_.clear();
  }
}

void MlmeEventHandlerImpl::OnRoam(unique_ptr<MlmeRoamEvent> event) {
  if (event->GetStatusCode() == 0) {
    client_interface_->is_associated_ = true;
    client_interface_->RefreshAssociateFreq();
    client_interface_->bssid_ = event->GetBSSID();
  } else {
    client_interface_->is_associated_ = false;
    client_interface_->bssid_.clear();
  }
}

void MlmeEventHandlerImpl::OnAssociate(unique_ptr<MlmeAssociateEvent> event) {
  if (!event->IsTimeout() && event->GetStatusCode() == 0) {
    client_interface_->is_associated_ = true;
    client_interface_->RefreshAssociateFreq();
    client_interface_->bssid_ = event->GetBSSID();
  } else {
    if (event->IsTimeout()) {
      LOG(INFO) << "Associate timeout";
    }
    client_interface_->is_associated_ = false;
    client_interface_->bssid_.clear();
  }
}

void MlmeEventHandlerImpl::OnDisconnect(unique_ptr<MlmeDisconnectEvent> event) {
  client_interface_->is_associated_ = false;
  client_interface_->bssid_.clear();
}

void MlmeEventHandlerImpl::OnDisassociate(unique_ptr<MlmeDisassociateEvent> event) {
  client_interface_->is_associated_ = false;
  client_interface_->bssid_.clear();
}


ClientInterfaceImpl::ClientInterfaceImpl(
    uint32_t wiphy_index,
    const std::string& interface_name,
    uint32_t interface_index,
    const std::vector<uint8_t>& interface_mac_addr,
    InterfaceTool* if_tool,
    SupplicantManager* supplicant_manager,
    NetlinkUtils* netlink_utils,
    ScanUtils* scan_utils)
    : wiphy_index_(wiphy_index),
      interface_name_(interface_name),
      interface_index_(interface_index),
      interface_mac_addr_(interface_mac_addr),
      if_tool_(if_tool),
      supplicant_manager_(supplicant_manager),
      netlink_utils_(netlink_utils),
      scan_utils_(scan_utils),
      mlme_event_handler_(new MlmeEventHandlerImpl(this)),
      binder_(new ClientInterfaceBinder(this)),
      is_associated_(false) {
  netlink_utils_->SubscribeMlmeEvent(
      interface_index_,
      mlme_event_handler_.get());
  if (!netlink_utils_->GetWiphyInfo(wiphy_index_,
                               &band_info_,
                               &scan_capabilities_,
                               &wiphy_features_)) {
    LOG(ERROR) << "Failed to get wiphy info from kernel";
  }
  LOG(INFO) << "create scanner for interface with index: "
            << (int)interface_index_;
  scanner_ = new ScannerImpl(wiphy_index,
                             interface_index_,
                             scan_capabilities_,
                             wiphy_features_,
                             this,
                             netlink_utils_,
                             scan_utils_);
}

ClientInterfaceImpl::~ClientInterfaceImpl() {
  binder_->NotifyImplDead();
  scanner_->Invalidate();
  DisableSupplicant();
  netlink_utils_->UnsubscribeMlmeEvent(interface_index_);
  if_tool_->SetUpState(interface_name_.c_str(), false);
}

sp<android::net::wifi::IClientInterface> ClientInterfaceImpl::GetBinder() const {
  return binder_;
}

void ClientInterfaceImpl::Dump(std::stringstream* ss) const {
  *ss << "------- Dump of client interface with index: "
      << interface_index_ << " and name: " << interface_name_
      << "-------" << endl;
  *ss << "Max number of ssids for single shot scan: "
      << static_cast<int>(scan_capabilities_.max_num_scan_ssids) << endl;
  *ss << "Max number of ssids for scheduled scan: "
      << static_cast<int>(scan_capabilities_.max_num_sched_scan_ssids) << endl;
  *ss << "Max number of match sets for scheduled scan: "
      << static_cast<int>(scan_capabilities_.max_match_sets) << endl;
  *ss << "Device supports random MAC for single shot scan: "
      << wiphy_features_.supports_random_mac_oneshot_scan << endl;
  *ss << "Device supports random MAC for scheduled scan: "
      << wiphy_features_.supports_random_mac_sched_scan << endl;
  *ss << "------- Dump End -------" << endl;
}

bool ClientInterfaceImpl::EnableSupplicant() {
  return supplicant_manager_->StartSupplicant();
}

bool ClientInterfaceImpl::DisableSupplicant() {
  return supplicant_manager_->StopSupplicant();
}

bool ClientInterfaceImpl::GetPacketCounters(vector<int32_t>* out_packet_counters) {
  StationInfo station_info;
  if (!netlink_utils_->GetStationInfo(interface_index_,
                                      interface_mac_addr_,
                                      &station_info)) {
    return false;
  }
  out_packet_counters->push_back(station_info.station_tx_packets);
  out_packet_counters->push_back(station_info.station_tx_failed);

  return true;
}

bool ClientInterfaceImpl::SignalPoll(vector<int32_t>* out_signal_poll_results) {
  StationInfo station_info;
  if (!netlink_utils_->GetStationInfo(interface_index_,
                                      bssid_,
                                      &station_info)) {
    return false;
  }
  out_signal_poll_results->push_back(
      static_cast<int32_t>(station_info.current_rssi));
  // Convert from 100kbit/s to Mbps.
  out_signal_poll_results->push_back(
      static_cast<int32_t>(station_info.station_tx_bitrate/10));
  // Association frequency.
  out_signal_poll_results->push_back(
      static_cast<int32_t>(associate_freq_));

  return true;
}

const vector<uint8_t>& ClientInterfaceImpl::GetMacAddress() {
  return interface_mac_addr_;
}

bool ClientInterfaceImpl::requestANQP(
      const ::std::vector<uint8_t>& bssid,
      const ::android::sp<::android::net::wifi::IANQPDoneCallback>& callback) {
  // TODO(nywang): query ANQP information from wpa_supplicant.
  return true;
}

bool ClientInterfaceImpl::RefreshAssociateFreq() {
  // wpa_supplicant fetches associate frequency using the latest scan result.
  // We should follow the same method here before we find a better solution.
  std::vector<NativeScanResult> scan_results;
  if (!scan_utils_->GetScanResult(interface_index_, &scan_results)) {
    return false;
  }
  for (auto& scan_result : scan_results) {
    if (scan_result.associated) {
      associate_freq_ = scan_result.frequency;
    }
  }
  return false;
}

bool ClientInterfaceImpl::IsAssociated() const {
  return is_associated_;
}

}  // namespace wificond
}  // namespace android
