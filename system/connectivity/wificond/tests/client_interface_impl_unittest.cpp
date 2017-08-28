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

#include <memory>
#include <vector>

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <wifi_system/supplicant_manager.h>
#include <wifi_system_test/mock_interface_tool.h>
#include <wifi_system_test/mock_supplicant_manager.h>

#include "wificond/client_interface_impl.h"
#include "wificond/tests/mock_netlink_manager.h"
#include "wificond/tests/mock_netlink_utils.h"
#include "wificond/tests/mock_scan_utils.h"

using android::wifi_system::MockInterfaceTool;
using android::wifi_system::MockSupplicantManager;
using android::wifi_system::SupplicantManager;
using std::unique_ptr;
using std::vector;
using testing::NiceMock;
using testing::Return;
using testing::_;

namespace android {
namespace wificond {
namespace {

const uint32_t kTestWiphyIndex = 2;
const char kTestInterfaceName[] = "testwifi0";
const uint32_t kTestInterfaceIndex = 42;

class ClientInterfaceImplTest : public ::testing::Test {
 protected:

  void SetUp() override {
    EXPECT_CALL(*netlink_utils_,
                SubscribeMlmeEvent(kTestInterfaceIndex, _));
    EXPECT_CALL(*netlink_utils_,
                GetWiphyInfo(kTestWiphyIndex, _, _, _));
    client_interface_.reset(new ClientInterfaceImpl{
        kTestWiphyIndex,
        kTestInterfaceName,
        kTestInterfaceIndex,
        vector<uint8_t>{0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
        if_tool_.get(),
        supplicant_manager_.get(),
        netlink_utils_.get(),
        scan_utils_.get()});
  }

  void TearDown() override {
    EXPECT_CALL(*netlink_utils_,
                UnsubscribeMlmeEvent(kTestInterfaceIndex));
    EXPECT_CALL(*supplicant_manager_, StopSupplicant())
        .WillOnce(Return(false));
  }

  unique_ptr<NiceMock<MockInterfaceTool>> if_tool_{
      new NiceMock<MockInterfaceTool>};
  unique_ptr<NiceMock<MockSupplicantManager>> supplicant_manager_{
      new NiceMock<MockSupplicantManager>};
  unique_ptr<NiceMock<MockNetlinkManager>> netlink_manager_{
      new NiceMock<MockNetlinkManager>()};
  unique_ptr<NiceMock<MockNetlinkUtils>> netlink_utils_{
      new NiceMock<MockNetlinkUtils>(netlink_manager_.get())};
  unique_ptr<NiceMock<MockScanUtils>> scan_utils_{
      new NiceMock<MockScanUtils>(netlink_manager_.get())};
  unique_ptr<ClientInterfaceImpl> client_interface_;
};  // class ClientInterfaceImplTest

}  // namespace

TEST_F(ClientInterfaceImplTest, ShouldReportEnableFailure) {
  EXPECT_CALL(*supplicant_manager_, StartSupplicant())
      .WillOnce(Return(false));
  EXPECT_FALSE(client_interface_->EnableSupplicant());
}

TEST_F(ClientInterfaceImplTest, ShouldReportEnableSuccess) {
  EXPECT_CALL(*supplicant_manager_, StartSupplicant())
      .WillOnce(Return(true));
  EXPECT_TRUE(client_interface_->EnableSupplicant());
}

TEST_F(ClientInterfaceImplTest, ShouldReportDisableFailure) {
  EXPECT_CALL(*supplicant_manager_, StopSupplicant())
      .WillOnce(Return(false));
  EXPECT_FALSE(client_interface_->DisableSupplicant());
}

TEST_F(ClientInterfaceImplTest, ShouldReportDisableSuccess) {
  EXPECT_CALL(*supplicant_manager_, StopSupplicant())
      .WillOnce(Return(true));
  EXPECT_TRUE(client_interface_->DisableSupplicant());
}

}  // namespace wificond
}  // namespace android
