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

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <string>
#include <vector>

#include "wifi_system/hostapd_manager.h"

using std::string;
using std::vector;

namespace android {
namespace wifi_system {
namespace {

const char kTestInterfaceName[] = "foobar0";
const char kTestSsidStr[] = "helloisitme";
const char kTestPassphraseStr[] = "yourelookingfor";
const int kTestChannel = 2;

#define CONFIG_COMMON_PREFIX \
    "interface=foobar0\n" \
    "driver=nl80211\n" \
    "ctrl_interface=/data/misc/wifi/hostapd/ctrl\n" \
    "ssid2=68656c6c6f" "6973" "6974" "6d65\n" \
    "channel=2\n" \
    "ieee80211n=1\n" \
    "hw_mode=g\n"

// If you generate your config file with both the test ssid
// and the test passphrase, you'll get this line in the config.
#define CONFIG_PSK_LINE \
    "wpa_psk=dffa36815281e5a6eca1910f254717fa2528681335e3bbec5966d2aa9221a66e\n"

#define CONFIG_WPA_SUFFIX \
    "wpa=3\n" \
    "wpa_pairwise=TKIP CCMP\n" \
    CONFIG_PSK_LINE

#define CONFIG_WPA2_SUFFIX \
    "wpa=2\n" \
    "rsn_pairwise=CCMP\n" \
    CONFIG_PSK_LINE

const char kExpectedOpenConfig[] =
  CONFIG_COMMON_PREFIX
  "ignore_broadcast_ssid=0\n"
  "wowlan_triggers=any\n";

const char kExpectedWpaConfig[] =
    CONFIG_COMMON_PREFIX
    "ignore_broadcast_ssid=0\n"
    "wowlan_triggers=any\n"
    CONFIG_WPA_SUFFIX;

const char kExpectedWpa2Config[] =
    CONFIG_COMMON_PREFIX
    "ignore_broadcast_ssid=0\n"
    "wowlan_triggers=any\n"
    CONFIG_WPA2_SUFFIX;

class HostapdManagerTest : public ::testing::Test {
 protected:
  string GetConfigForEncryptionType(
      HostapdManager::EncryptionType encryption_type) {
    return HostapdManager().CreateHostapdConfig(
        kTestInterfaceName,
        cstr2vector(kTestSsidStr),
        false,  // not hidden
        kTestChannel,
        encryption_type,
        cstr2vector(kTestPassphraseStr));
  }

  vector<uint8_t> cstr2vector(const char* data) {
    return vector<uint8_t>(data, data + strlen(data));
  }
};  // class HostapdManagerTest

}  // namespace

TEST_F(HostapdManagerTest, GeneratesCorrectOpenConfig) {
  string config = GetConfigForEncryptionType(
      HostapdManager::EncryptionType::kOpen);
  EXPECT_FALSE(config.empty());
  EXPECT_EQ(kExpectedOpenConfig, config);
}

TEST_F(HostapdManagerTest, GeneratesCorrectWpaConfig) {
  string config = GetConfigForEncryptionType(
      HostapdManager::EncryptionType::kWpa);
  EXPECT_FALSE(config.empty());
  EXPECT_EQ(kExpectedWpaConfig, config);
}

TEST_F(HostapdManagerTest, GeneratesCorrectWpa2Config) {
  string config = GetConfigForEncryptionType(
      HostapdManager::EncryptionType::kWpa2);
  EXPECT_FALSE(config.empty());
  EXPECT_EQ(kExpectedWpa2Config, config);
}

TEST_F(HostapdManagerTest, RespectsHiddenSetting) {
  string config = HostapdManager().CreateHostapdConfig(
        kTestInterfaceName,
        cstr2vector(kTestSsidStr),
        true,
        kTestChannel,
        HostapdManager::EncryptionType::kOpen,
        vector<uint8_t>());
  EXPECT_FALSE(config.find("ignore_broadcast_ssid=1\n") == string::npos);
  EXPECT_TRUE(config.find("ignore_broadcast_ssid=0\n") == string::npos);
}

TEST_F(HostapdManagerTest, CorrectlyInfersHwMode) {
  string config = HostapdManager().CreateHostapdConfig(
        kTestInterfaceName,
        cstr2vector(kTestSsidStr),
        true,
        44,
        HostapdManager::EncryptionType::kOpen,
        vector<uint8_t>());
  EXPECT_FALSE(config.find("hw_mode=a\n") == string::npos);
  EXPECT_TRUE(config.find("hw_mode=g\n") == string::npos);
}


}  // namespace wifi_system
}  // namespace android
