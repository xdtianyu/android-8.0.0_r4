/*
 * Copyright (C) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <functional>
#include <memory>
#include <vector>

#include <linux/netlink.h>
#include <linux/nl80211.h>

#include <gtest/gtest.h>

#include "wificond/scanning/scan_result.h"
#include "wificond/scanning/scan_utils.h"
#include "wificond/tests/mock_netlink_manager.h"

using std::bind;
using std::placeholders::_1;
using std::placeholders::_2;
using std::unique_ptr;
using std::vector;
using testing::Invoke;
using testing::NiceMock;
using testing::Return;
using testing::_;

using com::android::server::wifi::wificond::NativeScanResult;

namespace android {
namespace wificond {

namespace {

constexpr uint32_t kFakeInterfaceIndex = 12;
constexpr uint32_t kFakeScheduledScanIntervalMs = 20000;
constexpr uint32_t kFakeSequenceNumber = 1984;
constexpr int kFakeErrorCode = EIO;
constexpr int32_t kFakeRssiThreshold = -80;
constexpr bool kFakeUseRandomMAC = true;

// Currently, control messages are only created by the kernel and sent to us.
// Therefore NL80211Packet doesn't have corresponding constructor.
// For test we manually create control messages using this helper function.
NL80211Packet CreateControlMessageError(int error_code) {
  vector<uint8_t> data;
  data.resize(NLMSG_HDRLEN + NLA_ALIGN(sizeof(int)), 0);
  // Initialize length field.
  nlmsghdr* nl_header = reinterpret_cast<nlmsghdr*>(data.data());
  nl_header->nlmsg_len = data.size();
  nl_header->nlmsg_type = NLMSG_ERROR;
  nl_header->nlmsg_seq = kFakeSequenceNumber;
  nl_header->nlmsg_pid = getpid();
  int* error_field = reinterpret_cast<int*>(data.data() + NLMSG_HDRLEN);
  *error_field = -error_code;

  return NL80211Packet(data);
}

NL80211Packet CreateControlMessageAck() {
  return CreateControlMessageError(0);
}

// This is a helper function to mock the behavior of NetlinkManager::
// SendMessageAndGetResponses() when we expect a single packet response.
// |request_message| and |response| are mapped to existing parameters of
// SendMessageAndGetResponses().
// |mock_response| and |mock_return value| are additional parameters used
// for specifying expected results,
bool AppendMessageAndReturn(
    NL80211Packet& mock_response,
    bool mock_return_value,
    const NL80211Packet& request_message,
    vector<std::unique_ptr<const NL80211Packet>>* response) {
  response->push_back(std::make_unique<NL80211Packet>(mock_response));
  return mock_return_value;
}

}  // namespace

class ScanUtilsTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    ON_CALL(netlink_manager_,
            SendMessageAndGetResponses(_, _)).WillByDefault(Return(true));
  }

  NiceMock<MockNetlinkManager> netlink_manager_;
  ScanUtils scan_utils_{&netlink_manager_};
};

MATCHER_P(DoesNL80211PacketMatchCommand, command,
          "Check if the netlink packet matches |command|") {
  return arg.GetCommand() == command;
}

TEST_F(ScanUtilsTest, CanGetScanResult) {
  vector<NativeScanResult> scan_results;
  EXPECT_CALL(
      netlink_manager_,
      SendMessageAndGetResponses(
          DoesNL80211PacketMatchCommand(NL80211_CMD_GET_SCAN), _));

  // We don't use EXPECT_TRUE here because we need to mock a complete
  // response for NL80211_CMD_GET_SCAN to satisfy the parsing code called
  // by GetScanResult.
  // TODO(b/34231002): Mock response for NL80211_CMD_GET_SCAN.
  // TODO(b/34231420): Add validation of interface index.
  scan_utils_.GetScanResult(kFakeInterfaceIndex, &scan_results);
}

TEST_F(ScanUtilsTest, CanSendScanRequest) {
  NL80211Packet response = CreateControlMessageAck();
  EXPECT_CALL(
      netlink_manager_,
      SendMessageAndGetResponses(
          DoesNL80211PacketMatchCommand(NL80211_CMD_TRIGGER_SCAN), _)).
              WillOnce(Invoke(bind(
                  AppendMessageAndReturn, response, true, _1, _2)));

  EXPECT_TRUE(scan_utils_.Scan(kFakeInterfaceIndex, kFakeUseRandomMAC, {}, {}));
  // TODO(b/34231420): Add validation of requested scan ssids, threshold,
  // and frequencies.
}

TEST_F(ScanUtilsTest, CanHandleScanRequestFailure) {
  NL80211Packet response = CreateControlMessageError(kFakeErrorCode);
  EXPECT_CALL(
      netlink_manager_,
      SendMessageAndGetResponses(
          DoesNL80211PacketMatchCommand(NL80211_CMD_TRIGGER_SCAN), _)).
              WillOnce(Invoke(bind(
                  AppendMessageAndReturn, response, true, _1, _2)));
  EXPECT_FALSE(scan_utils_.Scan(kFakeInterfaceIndex, kFakeUseRandomMAC, {}, {}));
}

TEST_F(ScanUtilsTest, CanSendSchedScanRequest) {
  NL80211Packet response = CreateControlMessageAck();
  EXPECT_CALL(
      netlink_manager_,
       SendMessageAndGetResponses(
           DoesNL80211PacketMatchCommand(NL80211_CMD_START_SCHED_SCAN), _)).
              WillOnce(Invoke(bind(
                  AppendMessageAndReturn, response, true, _1, _2)));
  EXPECT_TRUE(scan_utils_.StartScheduledScan(
      kFakeInterfaceIndex,
      kFakeScheduledScanIntervalMs,
      kFakeRssiThreshold, kFakeUseRandomMAC, {}, {}, {}));
  // TODO(b/34231420): Add validation of requested scan ssids, threshold,
  // and frequencies.
}

TEST_F(ScanUtilsTest, CanHandleSchedScanRequestFailure) {
  NL80211Packet response = CreateControlMessageError(kFakeErrorCode);
  EXPECT_CALL(
      netlink_manager_,
       SendMessageAndGetResponses(
           DoesNL80211PacketMatchCommand(NL80211_CMD_START_SCHED_SCAN), _)).
              WillOnce(Invoke(bind(
                  AppendMessageAndReturn, response, true, _1, _2)));
  EXPECT_FALSE(scan_utils_.StartScheduledScan(
      kFakeInterfaceIndex,
      kFakeScheduledScanIntervalMs,
      kFakeRssiThreshold, kFakeUseRandomMAC, {}, {}, {}));
}

}  // namespace wificond
}  // namespace android
