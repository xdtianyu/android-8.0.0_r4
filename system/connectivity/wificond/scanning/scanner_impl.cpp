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

#include "wificond/scanning/scanner_impl.h"

#include <string>
#include <vector>

#include <android-base/logging.h>

#include "wificond/client_interface_impl.h"
#include "wificond/scanning/scan_utils.h"

using android::binder::Status;
using android::net::wifi::IPnoScanEvent;
using android::net::wifi::IScanEvent;
using android::sp;
using com::android::server::wifi::wificond::NativeScanResult;
using com::android::server::wifi::wificond::PnoSettings;
using com::android::server::wifi::wificond::SingleScanSettings;

using std::string;
using std::vector;

using namespace std::placeholders;

namespace android {
namespace wificond {

ScannerImpl::ScannerImpl(uint32_t wiphy_index,
                         uint32_t interface_index,
                         const ScanCapabilities& scan_capabilities,
                         const WiphyFeatures& wiphy_features,
                         ClientInterfaceImpl* client_interface,
                         NetlinkUtils* netlink_utils,
                         ScanUtils* scan_utils)
    : valid_(true),
      scan_started_(false),
      pno_scan_started_(false),
      wiphy_index_(wiphy_index),
      interface_index_(interface_index),
      scan_capabilities_(scan_capabilities),
      wiphy_features_(wiphy_features),
      client_interface_(client_interface),
      netlink_utils_(netlink_utils),
      scan_utils_(scan_utils),
      scan_event_handler_(nullptr) {
  // Subscribe one-shot scan result notification from kernel.
  LOG(INFO) << "subscribe scan result for interface with index: "
            << (int)interface_index_;
  scan_utils_->SubscribeScanResultNotification(
      interface_index_,
      std::bind(&ScannerImpl::OnScanResultsReady,
                this,
                _1, _2, _3, _4));
  // Subscribe scheduled scan result notification from kernel.
  scan_utils_->SubscribeSchedScanResultNotification(
      interface_index_,
      std::bind(&ScannerImpl::OnSchedScanResultsReady,
                this,
                _1, _2));
}

ScannerImpl::~ScannerImpl() {
}

void ScannerImpl::Invalidate() {
  LOG(INFO) << "Unsubscribe scan result for interface with index: "
            << (int)interface_index_;
  scan_utils_->UnsubscribeScanResultNotification(interface_index_);
  scan_utils_->UnsubscribeSchedScanResultNotification(interface_index_);
}

bool ScannerImpl::CheckIsValid() {
  if (!valid_) {
    LOG(DEBUG) << "Calling on a invalid scanner object."
               << "Underlying client interface object was destroyed.";
  }
  return valid_;
}

Status ScannerImpl::getAvailable2gChannels(
    std::unique_ptr<vector<int32_t>>* out_frequencies) {
  if (!CheckIsValid()) {
    return Status::ok();
  }
  BandInfo band_info;
  if (!netlink_utils_->GetWiphyInfo(wiphy_index_,
                               &band_info,
                               &scan_capabilities_,
                               &wiphy_features_)) {
    LOG(ERROR) << "Failed to get wiphy info from kernel";
    out_frequencies->reset(nullptr);
    return Status::ok();
  }

  out_frequencies->reset(new vector<int32_t>(band_info.band_2g.begin(),
                                             band_info.band_2g.end()));
  return Status::ok();
}

Status ScannerImpl::getAvailable5gNonDFSChannels(
    std::unique_ptr<vector<int32_t>>* out_frequencies) {
  if (!CheckIsValid()) {
    return Status::ok();
  }
  BandInfo band_info;
  if (!netlink_utils_->GetWiphyInfo(wiphy_index_,
                               &band_info,
                               &scan_capabilities_,
                               &wiphy_features_)) {
    LOG(ERROR) << "Failed to get wiphy info from kernel";
    out_frequencies->reset(nullptr);
    return Status::ok();
  }

  out_frequencies->reset(new vector<int32_t>(band_info.band_5g.begin(),
                                             band_info.band_5g.end()));
  return Status::ok();
}

Status ScannerImpl::getAvailableDFSChannels(
    std::unique_ptr<vector<int32_t>>* out_frequencies) {
  if (!CheckIsValid()) {
    return Status::ok();
  }
  BandInfo band_info;
  if (!netlink_utils_->GetWiphyInfo(wiphy_index_,
                               &band_info,
                               &scan_capabilities_,
                               &wiphy_features_)) {
    LOG(ERROR) << "Failed to get wiphy info from kernel";
    out_frequencies->reset(nullptr);
    return Status::ok();
  }

  out_frequencies->reset(new vector<int32_t>(band_info.band_dfs.begin(),
                                             band_info.band_dfs.end()));
  return Status::ok();
}

Status ScannerImpl::getScanResults(vector<NativeScanResult>* out_scan_results) {
  if (!CheckIsValid()) {
    return Status::ok();
  }
  if (!scan_utils_->GetScanResult(interface_index_, out_scan_results)) {
    LOG(ERROR) << "Failed to get scan results via NL80211";
  }
  return Status::ok();
}

Status ScannerImpl::scan(const SingleScanSettings& scan_settings,
                         bool* out_success) {
  if (!CheckIsValid()) {
    *out_success = false;
    return Status::ok();
  }

  if (scan_started_) {
    LOG(WARNING) << "Scan already started";
  }
  // Only request MAC address randomization when station is not associated.
  bool request_random_mac =  wiphy_features_.supports_random_mac_oneshot_scan &&
      !client_interface_->IsAssociated();

  // Initialize it with an empty ssid for a wild card scan.
  vector<vector<uint8_t>> ssids = {{}};

  vector<vector<uint8_t>> skipped_scan_ssids;
  for (auto& network : scan_settings.hidden_networks_) {
    if (ssids.size() + 1 > scan_capabilities_.max_num_scan_ssids) {
      skipped_scan_ssids.emplace_back(network.ssid_);
      continue;
    }
    ssids.push_back(network.ssid_);
  }

  LogSsidList(skipped_scan_ssids, "Skip scan ssid for single scan");

  vector<uint32_t> freqs;
  for (auto& channel : scan_settings.channel_settings_) {
    freqs.push_back(channel.frequency_);
  }

  if (!scan_utils_->Scan(interface_index_, request_random_mac, ssids, freqs)) {
    *out_success = false;
    return Status::ok();
  }
  scan_started_ = true;
  *out_success = true;
  return Status::ok();
}

Status ScannerImpl::startPnoScan(const PnoSettings& pno_settings,
                                 bool* out_success) {
  if (!CheckIsValid()) {
    *out_success = false;
    return Status::ok();
  }
  if (pno_scan_started_) {
    LOG(WARNING) << "Pno scan already started";
  }
  // An empty ssid for a wild card scan.
  vector<vector<uint8_t>> scan_ssids = {{}};
  vector<vector<uint8_t>> match_ssids;
  // Empty frequency list: scan all frequencies.
  vector<uint32_t> freqs;

  vector<vector<uint8_t>> skipped_scan_ssids;
  vector<vector<uint8_t>> skipped_match_ssids;
  for (auto& network : pno_settings.pno_networks_) {
    // Add hidden network ssid.
    if (network.is_hidden_) {
      if (scan_ssids.size() + 1 > scan_capabilities_.max_num_sched_scan_ssids) {
        skipped_scan_ssids.emplace_back(network.ssid_);
        continue;
      }
      scan_ssids.push_back(network.ssid_);
    }

    if (match_ssids.size() + 1 > scan_capabilities_.max_match_sets) {
      skipped_match_ssids.emplace_back(network.ssid_);
      continue;
    }
    match_ssids.push_back(network.ssid_);
  }

  LogSsidList(skipped_scan_ssids, "Skip scan ssid for pno scan");
  LogSsidList(skipped_match_ssids, "Skip match ssid for pno scan");

  // Only request MAC address randomization when station is not associated.
  bool request_random_mac = wiphy_features_.supports_random_mac_sched_scan &&
      !client_interface_->IsAssociated();

  if (!scan_utils_->StartScheduledScan(interface_index_,
                                       pno_settings.interval_ms_,
                                       // TODO: honor both rssi thresholds.
                                       pno_settings.min_5g_rssi_,
                                       request_random_mac,
                                       scan_ssids,
                                       match_ssids,
                                       freqs)) {
    *out_success = false;
    LOG(ERROR) << "Failed to start pno scan";
    return Status::ok();
  }
  LOG(INFO) << "Pno scan started";
  pno_scan_started_ = true;
  *out_success = true;
  return Status::ok();
}

Status ScannerImpl::stopPnoScan(bool* out_success) {
  if (!CheckIsValid()) {
    *out_success = false;
    return Status::ok();
  }

  if (!pno_scan_started_) {
    LOG(WARNING) << "No pno scan started";
  }
  if (!scan_utils_->StopScheduledScan(interface_index_)) {
    *out_success = false;
    return Status::ok();
  }
  LOG(INFO) << "Pno scan stopped";
  pno_scan_started_ = false;
  *out_success = true;
  return Status::ok();
}

Status ScannerImpl::abortScan() {
  if (!CheckIsValid()) {
    return Status::ok();
  }

  if (!scan_started_) {
    LOG(WARNING) << "Scan is not started. Ignore abort request";
    return Status::ok();
  }
  if (!scan_utils_->AbortScan(interface_index_)) {
    LOG(WARNING) << "Abort scan failed";
  }
  return Status::ok();
}

Status ScannerImpl::subscribeScanEvents(const sp<IScanEvent>& handler) {
  if (!CheckIsValid()) {
    return Status::ok();
  }

  if (scan_event_handler_ != nullptr) {
    LOG(ERROR) << "Found existing scan events subscriber."
               << " This subscription request will unsubscribe it";
  }
  scan_event_handler_ = handler;
  return Status::ok();
}

Status ScannerImpl::unsubscribeScanEvents() {
  scan_event_handler_ = nullptr;
  return Status::ok();
}


Status ScannerImpl::subscribePnoScanEvents(const sp<IPnoScanEvent>& handler) {
  if (!CheckIsValid()) {
    return Status::ok();
  }

  if (pno_scan_event_handler_ != nullptr) {
    LOG(ERROR) << "Found existing pno scan events subscriber."
               << " This subscription request will unsubscribe it";
  }
  pno_scan_event_handler_ = handler;

  return Status::ok();
}

Status ScannerImpl::unsubscribePnoScanEvents() {
  pno_scan_event_handler_ = nullptr;
  return Status::ok();
}

void ScannerImpl::OnScanResultsReady(
    uint32_t interface_index,
    bool aborted,
    vector<vector<uint8_t>>& ssids,
    vector<uint32_t>& frequencies) {
  if (!scan_started_) {
    LOG(INFO) << "Received external scan result notification from kernel.";
  }
  scan_started_ = false;
  if (scan_event_handler_ != nullptr) {
    // TODO: Pass other parameters back once we find framework needs them.
    if (aborted) {
      LOG(WARNING) << "Scan aborted";
      scan_event_handler_->OnScanFailed();
    } else {
      scan_event_handler_->OnScanResultReady();
    }
  } else {
    LOG(WARNING) << "No scan event handler found.";
  }
}

void ScannerImpl::OnSchedScanResultsReady(uint32_t interface_index,
                                          bool scan_stopped) {
  if (pno_scan_event_handler_ != nullptr) {
    if (scan_stopped) {
      // If |pno_scan_started_| is false.
      // This stop notification might result from our own request.
      // See the document for NL80211_CMD_SCHED_SCAN_STOPPED in nl80211.h.
      if (pno_scan_started_) {
        LOG(WARNING) << "Unexpected pno scan stopped event";
        pno_scan_event_handler_->OnPnoScanFailed();
      }
      pno_scan_started_ = false;
    } else {
      LOG(INFO) << "Pno scan result ready event";
      pno_scan_event_handler_->OnPnoNetworkFound();
    }
  }
}

void ScannerImpl::LogSsidList(vector<vector<uint8_t>>& ssid_list,
                              string prefix) {
  if (ssid_list.empty()) {
    return;
  }
  string ssid_list_string;
  for (auto& ssid : ssid_list) {
    ssid_list_string += string(ssid.begin(), ssid.end());
    if (&ssid != &ssid_list.back()) {
      ssid_list_string += ", ";
    }
  }
  LOG(WARNING) << prefix << ": " << ssid_list_string;
}

}  // namespace wificond
}  // namespace android
