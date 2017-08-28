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

#ifndef WIFICOND_TEST_MOCK_SCAN_UTILS_H_
#define WIFICOND_TEST_MOCK_SCAN_UTILS_H_

#include <gmock/gmock.h>

#include "wificond/scanning/scan_utils.h"

namespace android {
namespace wificond {

class MockScanUtils : public ScanUtils {
 public:
  MockScanUtils(NetlinkManager* netlink_manager);
  ~MockScanUtils() override = default;

  MOCK_METHOD2(GetScanResult, bool(
      uint32_t interface_index,
      std::vector<::com::android::server::wifi::wificond::NativeScanResult>* out_scan_results));

  MOCK_METHOD4(Scan, bool(
      uint32_t interface_index,
      bool random_mac,
      const std::vector<std::vector<uint8_t>>& ssids,
      const std::vector<uint32_t>& freqs));

  MOCK_METHOD2(SubscribeScanResultNotification,void(
      uint32_t interface_index,
      OnScanResultsReadyHandler handler));

  MOCK_METHOD1(UnsubscribeScanResultNotification,
               void(uint32_t interface_index));

};  // class MockScanUtils

}  // namespace wificond
}  // namespace android

#endif  // WIFICOND_TEST_MOCK_SCAN_UTILS_H
