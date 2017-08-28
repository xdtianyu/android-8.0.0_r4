/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef ANDROID_HARDWARE_CONTEXTHUB_V1_0_CONTEXTHUB_H
#define ANDROID_HARDWARE_CONTEXTHUB_V1_0_CONTEXTHUB_H

#include <future>

#include <android/hardware/contexthub/1.0/IContexthub.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

#include "chre_host/socket_client.h"
#include "chre_host/host_protocol_host.h"

namespace android {
namespace hardware {
namespace contexthub {
namespace V1_0 {
namespace implementation {

using ::android::hardware::contexthub::V1_0::ContextHub;
using ::android::hardware::contexthub::V1_0::ContextHubMsg;
using ::android::hardware::contexthub::V1_0::IContexthub;
using ::android::hardware::contexthub::V1_0::IContexthubCallback;
using ::android::hardware::contexthub::V1_0::NanoAppBinary;
using ::android::hardware::contexthub::V1_0::Result;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::sp;

class GenericContextHub : public IContexthub {
 public:
  GenericContextHub();

  // Methods from ::android::hardware::contexthub::V1_0::IContexthub follow.
  Return<void> getHubs(getHubs_cb _hidl_cb) override;
  Return<Result> registerCallback(uint32_t hubId, const sp<IContexthubCallback>& cb) override;
  Return<Result> sendMessageToHub(uint32_t hubId, const ContextHubMsg& msg) override;
  Return<Result> loadNanoApp(uint32_t hubId, const NanoAppBinary& appBinary, uint32_t transactionId) override;
  Return<Result> unloadNanoApp(uint32_t hubId, uint64_t appId, uint32_t transactionId) override;
  Return<Result> enableNanoApp(uint32_t hubId, uint64_t appId, uint32_t transactionId) override;
  Return<Result> disableNanoApp(uint32_t hubId, uint64_t appId, uint32_t transactionId) override;
  Return<Result> queryApps(uint32_t hubId) override;

 private:
  ::android::chre::SocketClient mClient;
  sp<IContexthubCallback> mCallbacks;

  class SocketCallbacks : public ::android::chre::SocketClient::ICallbacks,
                          public ::android::chre::IChreMessageHandlers {
   public:
    SocketCallbacks(GenericContextHub& parent);
    void onMessageReceived(const void *data, size_t length) override;
    void onConnected() override;
    void onDisconnected() override;

    void handleNanoappMessage(
        uint64_t appId, uint32_t messageType, uint16_t hostEndpoint,
        const void *messageData, size_t messageDataLen) override;
    void handleHubInfoResponse(
        const char *name, const char *vendor,
        const char *toolchain, uint32_t legacyPlatformVersion,
        uint32_t legacyToolchainVersion, float peakMips, float stoppedPower,
        float sleepPower, float peakPower, uint32_t maxMessageLen,
        uint64_t platformId, uint32_t version) override;

    void handleNanoappListResponse(
        const ::chre::fbs::NanoappListResponseT& response) override;

    void handleLoadNanoappResponse(
      const ::chre::fbs::LoadNanoappResponseT& response) override;

   private:
    GenericContextHub& mParent;
    bool mHaveConnected = false;
  };

  sp<SocketCallbacks> mSocketCallbacks;

  // Cached hub info used for getHubs(), and synchronization primitives to make
  // that function call synchronous if we need to query it
  ContextHub mHubInfo;
  bool mHubInfoValid = false;
  std::mutex mHubInfoMutex;
  std::condition_variable mHubInfoCond;
};

extern "C" IContexthub* HIDL_FETCH_IContexthub(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace contexthub
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_CONTEXTHUB_V1_0_CONTEXTHUB_H
