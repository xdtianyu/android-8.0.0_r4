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

#define LOG_TAG "ContextHubHal"
#define LOG_NDEBUG 0

#include "generic_context_hub.h"

#include <chrono>
#include <cinttypes>
#include <vector>

#include <utils/Log.h>

namespace android {
namespace hardware {
namespace contexthub {
namespace V1_0 {
namespace implementation {

using ::android::hardware::Return;
using ::android::hardware::contexthub::V1_0::AsyncEventType;
using ::android::hardware::contexthub::V1_0::Result;
using ::android::hardware::contexthub::V1_0::TransactionResult;
using ::android::chre::HostProtocolHost;
using ::flatbuffers::FlatBufferBuilder;

// Aliased for consistency with the way these symbols are referenced in
// CHRE-side code
namespace fbs = ::chre::fbs;

namespace {

constexpr uint32_t kDefaultHubId = 0;

// TODO: remove this macro once all methods are implemented
#define UNUSED(param) (void) (param)

constexpr uint8_t extractChreApiMajorVersion(uint32_t chreVersion) {
  return static_cast<uint8_t>(chreVersion >> 24);
}

constexpr uint8_t extractChreApiMinorVersion(uint32_t chreVersion) {
  return static_cast<uint8_t>(chreVersion >> 16);
}

constexpr uint16_t extractChrePatchVersion(uint32_t chreVersion) {
  return static_cast<uint16_t>(chreVersion);
}

}  // anonymous namespace

GenericContextHub::GenericContextHub() {
  constexpr char kChreSocketName[] = "chre";

  mSocketCallbacks = new SocketCallbacks(*this);
  if (!mClient.connectInBackground(kChreSocketName, mSocketCallbacks)) {
    ALOGE("Couldn't start socket client");
  }
}

Return<void> GenericContextHub::getHubs(getHubs_cb _hidl_cb) {
  constexpr auto kHubInfoQueryTimeout = std::chrono::seconds(5);
  std::vector<ContextHub> hubs;
  ALOGV("%s", __func__);

  // If we're not connected yet, give it some time
  int maxSleepIterations = 50;
  while (!mHubInfoValid && !mClient.isConnected() && --maxSleepIterations > 0) {
    std::this_thread::sleep_for(std::chrono::milliseconds(100));
  }

  if (!mClient.isConnected()) {
    ALOGE("Couldn't connect to hub daemon");
  } else if (!mHubInfoValid) {
    // We haven't cached the hub details yet, so send a request and block
    // waiting on a response
    std::unique_lock<std::mutex> lock(mHubInfoMutex);
    FlatBufferBuilder builder;
    HostProtocolHost::encodeHubInfoRequest(builder);

    ALOGD("Sending hub info request");
    if (!mClient.sendMessage(builder.GetBufferPointer(), builder.GetSize())) {
      ALOGE("Couldn't send hub info request");
    } else {
      mHubInfoCond.wait_for(lock, kHubInfoQueryTimeout,
                            [this]() { return mHubInfoValid; });
    }
  }

  if (mHubInfoValid) {
    hubs.push_back(mHubInfo);
  } else {
    ALOGE("Unable to get hub info from CHRE");
  }

  _hidl_cb(hubs);
  return Void();
}

Return<Result> GenericContextHub::registerCallback(
    uint32_t hubId, const sp<IContexthubCallback>& cb) {
  Result result;
  ALOGV("%s", __func__);

  // TODO: currently we only support 1 hub behind this HAL implementation
  if (hubId == kDefaultHubId) {
    mCallbacks = cb; // TODO: special handling for null?
    result = Result::OK;
  } else {
    result = Result::BAD_PARAMS;
  }

  return result;
}

Return<Result> GenericContextHub::sendMessageToHub(uint32_t hubId,
                                                   const ContextHubMsg& msg) {
  Result result;
  ALOGV("%s", __func__);

  if (hubId != kDefaultHubId) {
    result = Result::BAD_PARAMS;
  } else {
    FlatBufferBuilder builder(1024);
    HostProtocolHost::encodeNanoappMessage(
        builder, msg.appName, msg.msgType, msg.hostEndPoint, msg.msg.data(),
        msg.msg.size());

    if (!mClient.sendMessage(builder.GetBufferPointer(), builder.GetSize())) {
      result = Result::UNKNOWN_FAILURE;
    } else {
      result = Result::OK;
    }
  }

  return result;
}

Return<Result> GenericContextHub::loadNanoApp(
    uint32_t hubId, const NanoAppBinary& appBinary, uint32_t transactionId) {
  Result result;
  ALOGV("%s", __func__);

  if (hubId != kDefaultHubId) {
    result = Result::BAD_PARAMS;
  } else {
    FlatBufferBuilder builder(128 + appBinary.customBinary.size());
    uint32_t targetApiVersion = (appBinary.targetChreApiMajorVersion << 24) |
                                (appBinary.targetChreApiMinorVersion << 16);
    HostProtocolHost::encodeLoadNanoappRequest(
        builder, transactionId, appBinary.appId, appBinary.appVersion,
        targetApiVersion, appBinary.customBinary);
    if (!mClient.sendMessage(builder.GetBufferPointer(), builder.GetSize())) {
      result = Result::UNKNOWN_FAILURE;
    } else {
      result = Result::OK;
    }
  }

  ALOGD("Attempted to send load nanoapp request for app of size %zu with ID "
        "0x%016" PRIx64 " as transaction ID %" PRIu32 ": result %" PRIu32,
        appBinary.customBinary.size(), appBinary.appId, transactionId, result);

  return result;
}

Return<Result> GenericContextHub::unloadNanoApp(
    uint32_t hubId, uint64_t appId, uint32_t transactionId) {
  // TODO
  UNUSED(hubId);
  UNUSED(appId);
  UNUSED(transactionId);
  ALOGV("%s", __func__);
  return Result::UNKNOWN_FAILURE;
}

Return<Result> GenericContextHub::enableNanoApp(
    uint32_t hubId, uint64_t appId, uint32_t transactionId) {
  // TODO
  UNUSED(hubId);
  UNUSED(appId);
  UNUSED(transactionId);
  ALOGV("%s", __func__);
  return Result::UNKNOWN_FAILURE;
}

Return<Result> GenericContextHub::disableNanoApp(
    uint32_t hubId, uint64_t appId, uint32_t transactionId) {
  // TODO
  UNUSED(hubId);
  UNUSED(appId);
  UNUSED(transactionId);
  ALOGV("%s", __func__);
  return Result::UNKNOWN_FAILURE;
}

Return<Result> GenericContextHub::queryApps(uint32_t hubId) {
  Result result;
  ALOGV("%s", __func__);

  if (hubId != kDefaultHubId) {
    result = Result::BAD_PARAMS;
  } else {
    FlatBufferBuilder builder(64);
    HostProtocolHost::encodeNanoappListRequest(builder);
    if (!mClient.sendMessage(builder.GetBufferPointer(), builder.GetSize())) {
      result = Result::UNKNOWN_FAILURE;
    } else {
      result = Result::OK;
    }
  }

  return Result::UNKNOWN_FAILURE;
}

GenericContextHub::SocketCallbacks::SocketCallbacks(GenericContextHub& parent)
    : mParent(parent) {}

void GenericContextHub::SocketCallbacks::onMessageReceived(const void *data,
                                                           size_t length) {
  if (!HostProtocolHost::decodeMessageFromChre(data, length, *this)) {
    ALOGE("Failed to decode message");
  }
}

void GenericContextHub::SocketCallbacks::onConnected() {
  if (mHaveConnected) {
    ALOGI("Reconnected to CHRE daemon");
    if (mParent.mCallbacks != nullptr) {
      mParent.mCallbacks->handleHubEvent(AsyncEventType::RESTARTED);
    }
  }
  mHaveConnected = true;
}

void GenericContextHub::SocketCallbacks::onDisconnected() {
  ALOGW("Lost connection to CHRE daemon");
}

void GenericContextHub::SocketCallbacks::handleNanoappMessage(
    uint64_t appId, uint32_t messageType, uint16_t hostEndpoint,
    const void *messageData, size_t messageDataLen) {
  // TODO: this is not thread-safe w/registerCallback... we need something else
  // to confirm that it's safe for us to invoke the callback, and likely a lock
  // on stuff
  if (mParent.mCallbacks != nullptr) {
    ContextHubMsg msg;
    msg.appName = appId;
    msg.hostEndPoint = hostEndpoint;
    msg.msgType = messageType;

    // Dropping const from messageData when we wrap it in hidl_vec here. This is
    // safe because we won't modify it here, and the ContextHubMsg we pass to
    // the callback is const.
    msg.msg.setToExternal(
        const_cast<uint8_t *>(static_cast<const uint8_t *>(messageData)),
        messageDataLen, false /* shouldOwn */);

    mParent.mCallbacks->handleClientMsg(msg);
  }
}

void GenericContextHub::SocketCallbacks::handleHubInfoResponse(
    const char *name, const char *vendor,
    const char *toolchain, uint32_t legacyPlatformVersion,
    uint32_t legacyToolchainVersion, float peakMips, float stoppedPower,
    float sleepPower, float peakPower, uint32_t maxMessageLen,
    uint64_t platformId, uint32_t version) {
  ALOGD("Got hub info response");

  std::lock_guard<std::mutex> lock(mParent.mHubInfoMutex);
  if (mParent.mHubInfoValid) {
    ALOGI("Ignoring duplicate/unsolicited hub info response");
  } else {
    mParent.mHubInfo.name = name;
    mParent.mHubInfo.vendor = vendor;
    mParent.mHubInfo.toolchain = toolchain;
    mParent.mHubInfo.platformVersion = legacyPlatformVersion;
    mParent.mHubInfo.toolchainVersion = legacyToolchainVersion;
    mParent.mHubInfo.hubId = kDefaultHubId;

    mParent.mHubInfo.peakMips = peakMips;
    mParent.mHubInfo.stoppedPowerDrawMw = stoppedPower;
    mParent.mHubInfo.sleepPowerDrawMw = sleepPower;
    mParent.mHubInfo.peakPowerDrawMw = peakPower;

    mParent.mHubInfo.maxSupportedMsgLen = maxMessageLen;
    mParent.mHubInfo.chrePlatformId = platformId;

    mParent.mHubInfo.chreApiMajorVersion = extractChreApiMajorVersion(version);
    mParent.mHubInfo.chreApiMinorVersion = extractChreApiMinorVersion(version);
    mParent.mHubInfo.chrePatchVersion = extractChrePatchVersion(version);

    mParent.mHubInfoValid = true;
    mParent.mHubInfoCond.notify_all();
  }
}

void GenericContextHub::SocketCallbacks::handleNanoappListResponse(
    const fbs::NanoappListResponseT& response) {
  std::vector<HubAppInfo> appInfoList;

  ALOGV("Got nanoapp list response with %zu apps", response.nanoapps.size());
  for (const std::unique_ptr<fbs::NanoappListEntryT>& nanoapp
         : response.nanoapps) {
    // TODO: determine if this is really required, and if so, have
    // HostProtocolHost strip out null entries as part of decode
    if (nanoapp == nullptr) {
      continue;
    }

    ALOGV("App 0x%016" PRIx64 " ver 0x%" PRIx32 " enabled %d system %d",
          nanoapp->app_id, nanoapp->version, nanoapp->enabled,
          nanoapp->is_system);
    if (!nanoapp->is_system) {
      HubAppInfo appInfo;

      appInfo.appId = nanoapp->app_id;
      appInfo.version = nanoapp->version;
      appInfo.enabled = nanoapp->enabled;

      appInfoList.push_back(appInfo);
    }
  }

  // TODO: make this thread-safe w/setCallback
  mParent.mCallbacks->handleAppsInfo(appInfoList);
}

void GenericContextHub::SocketCallbacks::handleLoadNanoappResponse(
    const ::chre::fbs::LoadNanoappResponseT& response) {
  ALOGV("Got load nanoapp response for transaction %" PRIu32 " with result %d",
        response.transaction_id, response.success);

  TransactionResult result = (response.success) ?
      TransactionResult::SUCCESS : TransactionResult::FAILURE;
  mParent.mCallbacks->handleTxnResult(response.transaction_id, result);
}

IContexthub* HIDL_FETCH_IContexthub(const char* /* name */) {
  return new GenericContextHub();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace contexthub
}  // namespace hardware
}  // namespace android
