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

#include <string>
#include <vector>

#include <gtest/gtest.h>
#include <utils/StrongPointer.h>
#include <wifi_system/interface_tool.h>

#include "android/net/wifi/IClientInterface.h"
#include "android/net/wifi/IWificond.h"
#include "wificond/tests/integration/process_utils.h"

using android::net::wifi::IClientInterface;
using android::net::wifi::IWificond;
using android::wifi_system::InterfaceTool;
using android::wificond::tests::integration::ScopedDevModeWificond;
using android::wificond::tests::integration::SupplicantIsDead;
using android::wificond::tests::integration::SupplicantIsRunning;
using android::wificond::tests::integration::WaitForTrue;
using std::string;
using std::vector;

namespace android {
namespace wificond {
namespace {

constexpr int kSupplicantStartupTimeoutSeconds = 3;
constexpr int kSupplicantDeathTimeoutSeconds = 3;

}  // namespace

TEST(ClientInterfaceTest, CanCreateClientInterfaces) {
  ScopedDevModeWificond dev_mode;
  sp<IWificond> service = dev_mode.EnterDevModeOrDie();

  // We should be able to create an client interface.
  sp<IClientInterface> client_interface;
  EXPECT_TRUE(service->createClientInterface(&client_interface).isOk());
  EXPECT_NE(nullptr, client_interface.get());

  // The interface should start out down.
  string if_name;
  EXPECT_TRUE(client_interface->getInterfaceName(&if_name).isOk());
  EXPECT_TRUE(!if_name.empty());
  InterfaceTool if_tool;
  EXPECT_FALSE(if_tool.GetUpState(if_name.c_str()));

  // Mark the interface as up, just to test that we mark it down on teardown.
  EXPECT_TRUE(if_tool.SetUpState(if_name.c_str(), true));
  EXPECT_TRUE(if_tool.GetUpState(if_name.c_str()));

  // We should not be able to create two client interfaces.
  sp<IClientInterface> client_interface2;
  EXPECT_TRUE(service->createClientInterface(&client_interface2).isOk());
  EXPECT_EQ(nullptr, client_interface2.get());

  // We can tear down the created interface.
  EXPECT_TRUE(service->tearDownInterfaces().isOk());
  EXPECT_FALSE(if_tool.GetUpState(if_name.c_str()));
}

TEST(ClientInterfaceTest, CanStartStopSupplicant) {
  ScopedDevModeWificond dev_mode;
  sp<IWificond> service = dev_mode.EnterDevModeOrDie();
  sp<IClientInterface> client_interface;
  EXPECT_TRUE(service->createClientInterface(&client_interface).isOk());
  ASSERT_NE(nullptr, client_interface.get());

  for (int iteration = 0; iteration < 4; iteration++) {
    bool supplicant_started = false;
    EXPECT_TRUE(client_interface->enableSupplicant(&supplicant_started).isOk());
    EXPECT_TRUE(supplicant_started);

    EXPECT_TRUE(WaitForTrue(SupplicantIsRunning,
                            kSupplicantStartupTimeoutSeconds))
        << "Failed on iteration " << iteration;

    // We look for supplicant so quickly that we miss when it dies on startup
    sleep(1);
    EXPECT_TRUE(SupplicantIsRunning()) << "Failed on iteration " << iteration;

    bool supplicant_stopped = false;
    EXPECT_TRUE(
        client_interface->disableSupplicant(&supplicant_stopped).isOk());
    EXPECT_TRUE(supplicant_stopped);

    EXPECT_TRUE(WaitForTrue(SupplicantIsDead, kSupplicantDeathTimeoutSeconds))
        << "Failed on iteration " << iteration;
  }
}

TEST(ClientInterfaceTest, CanGetMacAddress) {
  ScopedDevModeWificond dev_mode;
  sp<IWificond> service = dev_mode.EnterDevModeOrDie();
  sp<IClientInterface> client_interface;
  EXPECT_TRUE(service->createClientInterface(&client_interface).isOk());
  ASSERT_NE(nullptr, client_interface.get());
  vector<uint8_t> mac_address;
  EXPECT_TRUE(client_interface->getMacAddress(&mac_address).isOk());
  EXPECT_TRUE(mac_address.size() == 6);
}

}  // namespace wificond
}  // namespace android
