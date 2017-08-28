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

#include <inttypes.h>
#include <limits.h>

#include "qurt.h"

#include "chre/core/event_loop_manager.h"
#include "chre/core/host_comms_manager.h"
#include "chre/platform/context.h"
#include "chre/platform/memory.h"
#include "chre/platform/log.h"
#include "chre/platform/shared/host_protocol_chre.h"
#include "chre/platform/slpi/fastrpc.h"
#include "chre/util/fixed_size_blocking_queue.h"
#include "chre/util/macros.h"
#include "chre/util/unique_ptr.h"
#include "chre_api/chre/version.h"

using flatbuffers::FlatBufferBuilder;

namespace chre {

namespace {

constexpr size_t kOutboundQueueSize = 32;

//! Used to pass the client ID through the user data pointer in deferCallback
union HostClientIdCallbackData {
  uint16_t hostClientId;
  void *ptr;
};
static_assert(sizeof(uint16_t) <= sizeof(void*),
              "Pointer must at least fit a u16 for passing the host client ID");

struct LoadNanoappCallbackData {
  uint64_t appId;
  uint32_t transactionId;
  uint16_t hostClientId;
  UniquePtr<Nanoapp> nanoapp = MakeUnique<Nanoapp>();
};

enum class PendingMessageType {
  Shutdown,
  NanoappMessageToHost,
  HubInfoResponse,
  NanoappListResponse,
  LoadNanoappResponse,
};

struct PendingMessage {
  PendingMessage(PendingMessageType msgType, uint16_t hostClientId) {
    type = msgType;
    data.hostClientId = hostClientId;
  }

  PendingMessage(PendingMessageType msgType,
                 const MessageToHost *msgToHost = nullptr) {
    type = msgType;
    data.msgToHost = msgToHost;
  }

  PendingMessage(PendingMessageType msgType, FlatBufferBuilder *builder) {
    type = msgType;
    data.builder = builder;
  }

  PendingMessageType type;
  union {
    const MessageToHost *msgToHost;
    uint16_t hostClientId;
    FlatBufferBuilder *builder;
  } data;
};

FixedSizeBlockingQueue<PendingMessage, kOutboundQueueSize>
    gOutboundQueue;

int copyToHostBuffer(const FlatBufferBuilder& builder, unsigned char *buffer,
                     size_t bufferSize, unsigned int *messageLen) {
  uint8_t *data = builder.GetBufferPointer();
  size_t size = builder.GetSize();
  int result;

  if (size > bufferSize) {
    LOGE("Encoded structure size %zu too big for host buffer %zu; dropping",
         size, bufferSize);
    result = CHRE_FASTRPC_ERROR;
  } else {
    memcpy(buffer, data, size);
    *messageLen = size;
    result = CHRE_FASTRPC_SUCCESS;
  }

  return result;
}

void constructNanoappListCallback(uint16_t /*eventType*/, void *deferCbData) {
  constexpr size_t kFixedOverhead = 56;
  constexpr size_t kPerNanoappSize = 16;

  HostClientIdCallbackData clientIdCbData;
  clientIdCbData.ptr = deferCbData;

  // TODO: need to add support for getting apps from multiple event loops
  bool pushed = false;
  EventLoop *eventLoop = getCurrentEventLoop();
  size_t expectedNanoappCount = eventLoop->getNanoappCount();
  DynamicVector<NanoappListEntryOffset> nanoappEntries;

  auto *builder = memoryAlloc<FlatBufferBuilder>(
      kFixedOverhead + expectedNanoappCount * kPerNanoappSize);
  if (builder == nullptr) {
    LOGE("Couldn't allocate builder for nanoapp list response");
  } else if (!nanoappEntries.reserve(expectedNanoappCount)) {
    LOGE("Couldn't reserve space for list of nanoapp offsets");
  } else {
    struct CallbackData {
      CallbackData(FlatBufferBuilder& builder_,
                   DynamicVector<NanoappListEntryOffset>& nanoappEntries_)
          : builder(builder_), nanoappEntries(nanoappEntries_) {}

      FlatBufferBuilder& builder;
      DynamicVector<NanoappListEntryOffset>& nanoappEntries;
    };

    auto callback = [](const Nanoapp *nanoapp, void *untypedData) {
      auto *data = static_cast<CallbackData *>(untypedData);
      HostProtocolChre::addNanoappListEntry(
          data->builder, data->nanoappEntries, nanoapp->getAppId(),
          nanoapp->getAppVersion(), true /*enabled*/,
          nanoapp->isSystemNanoapp());
    };

    // Add a NanoappListEntry to the FlatBuffer for each nanoapp
    CallbackData cbData(*builder, nanoappEntries);
    eventLoop->forEachNanoapp(callback, &cbData);
    HostProtocolChre::finishNanoappListResponse(*builder, nanoappEntries,
                                                clientIdCbData.hostClientId);

    pushed = gOutboundQueue.push(
        PendingMessage(PendingMessageType::NanoappListResponse, builder));
    if (!pushed) {
      LOGE("Couldn't push list response");
    }
  }

  if (!pushed && builder != nullptr) {
    memoryFree(builder);
  }
}

void finishLoadingNanoappCallback(uint16_t /*eventType*/, void *data) {
  UniquePtr<LoadNanoappCallbackData> cbData(
      static_cast<LoadNanoappCallbackData *>(data));

  EventLoop *eventLoop = getCurrentEventLoop();
  bool startedSuccessfully = (cbData->nanoapp->isLoaded()) ?
      eventLoop->startNanoapp(cbData->nanoapp) : false;

  constexpr size_t kInitialBufferSize = 48;
  auto *builder = memoryAlloc<FlatBufferBuilder>(kInitialBufferSize);
  if (builder == nullptr) {
    LOGE("Couldn't allocate memory for load nanoapp response");
  } else {
    HostProtocolChre::encodeLoadNanoappResponse(
        *builder, cbData->hostClientId, cbData->transactionId,
        startedSuccessfully);

    // TODO: if this fails, ideally we should block for some timeout until
    // there's space in the queue (like up to 1 second)
    if (!gOutboundQueue.push(PendingMessage(
            PendingMessageType::LoadNanoappResponse, builder))) {
      LOGE("Couldn't push load nanoapp response to outbound queue");
      memoryFree(builder);
    }
  }
}

int generateMessageToHost(const MessageToHost *msgToHost, unsigned char *buffer,
                          size_t bufferSize, unsigned int *messageLen) {
  // TODO: ideally we'd construct our flatbuffer directly in the
  // host-supplied buffer
  constexpr size_t kFixedSizePortion = 56;
  FlatBufferBuilder builder(msgToHost->message.size() + kFixedSizePortion);
  HostProtocolChre::encodeNanoappMessage(
    builder, msgToHost->appId, msgToHost->toHostData.messageType,
    msgToHost->toHostData.hostEndpoint, msgToHost->message.data(),
    msgToHost->message.size());

  int result = copyToHostBuffer(builder, buffer, bufferSize, messageLen);

  auto& hostCommsManager =
      EventLoopManagerSingleton::get()->getHostCommsManager();
  hostCommsManager.onMessageToHostComplete(msgToHost);

  return result;
}

int generateHubInfoResponse(uint16_t hostClientId, unsigned char *buffer,
                            size_t bufferSize, unsigned int *messageLen) {
  constexpr size_t kInitialBufferSize = 192;

  constexpr char kHubName[] = "CHRE on SLPI";
  constexpr char kVendor[] = "Google";
  constexpr char kToolchain[] = "Hexagon Tools 8.0 (clang "
    STRINGIFY(__clang_major__) "."
    STRINGIFY(__clang_minor__) "."
    STRINGIFY(__clang_patchlevel__) ")";
  constexpr uint32_t kLegacyPlatformVersion = 0;
  constexpr uint32_t kLegacyToolchainVersion =
    ((__clang_major__ & 0xFF) << 24) |
    ((__clang_minor__ & 0xFF) << 16) |
    (__clang_patchlevel__ & 0xFFFF);
  constexpr float kPeakMips = 350;
  constexpr float kStoppedPower = 0;
  constexpr float kSleepPower = 1;
  constexpr float kPeakPower = 15;

  FlatBufferBuilder builder(kInitialBufferSize);
  HostProtocolChre::encodeHubInfoResponse(
      builder, kHubName, kVendor, kToolchain, kLegacyPlatformVersion,
      kLegacyToolchainVersion, kPeakMips, kStoppedPower, kSleepPower,
      kPeakPower, CHRE_MESSAGE_TO_HOST_MAX_SIZE, chreGetPlatformId(),
      chreGetVersion(), hostClientId);

  return copyToHostBuffer(builder, buffer, bufferSize, messageLen);
}

int generateMessageFromBuilder(
    FlatBufferBuilder *builder, unsigned char *buffer, size_t bufferSize,
    unsigned int *messageLen) {
  CHRE_ASSERT(builder != nullptr);
  int result = copyToHostBuffer(*builder, buffer, bufferSize, messageLen);
  memoryFree(builder);
  return result;
}

/**
 * FastRPC method invoked by the host to block on messages
 *
 * @param buffer Output buffer to populate with message data
 * @param bufferLen Size of the buffer, in bytes
 * @param messageLen Output parameter to populate with the size of the message
 *        in bytes upon success
 *
 * @return 0 on success, nonzero on failure
 */
extern "C" int chre_slpi_get_message_to_host(
    unsigned char *buffer, int bufferLen, unsigned int *messageLen) {
  CHRE_ASSERT(buffer != nullptr);
  CHRE_ASSERT(bufferLen > 0);
  CHRE_ASSERT(messageLen != nullptr);
  int result = CHRE_FASTRPC_ERROR;

  if (bufferLen <= 0 || buffer == nullptr || messageLen == nullptr) {
    // Note that we can't use regular logs here as they can result in sending
    // a message, leading to an infinite loop if the error is persistent
    FARF(FATAL, "Invalid buffer size %d or bad pointers (buf %d len %d)",
         bufferLen, (buffer == nullptr), (messageLen == nullptr));
  } else {
    size_t bufferSize = static_cast<size_t>(bufferLen);
    PendingMessage pendingMsg = gOutboundQueue.pop();

    switch (pendingMsg.type) {
      case PendingMessageType::Shutdown:
        result = CHRE_FASTRPC_ERROR_SHUTTING_DOWN;
        break;

      case PendingMessageType::NanoappMessageToHost:
        result = generateMessageToHost(pendingMsg.data.msgToHost, buffer,
                                       bufferSize, messageLen);
        break;

      case PendingMessageType::HubInfoResponse:
        result = generateHubInfoResponse(pendingMsg.data.hostClientId, buffer,
                                         bufferSize, messageLen);
        break;

      case PendingMessageType::NanoappListResponse:
      case PendingMessageType::LoadNanoappResponse:
        result = generateMessageFromBuilder(pendingMsg.data.builder,
                                            buffer, bufferSize, messageLen);
        break;

      default:
        CHRE_ASSERT_LOG(false, "Unexpected pending message type");
    }
  }

  LOGD("Returning message to host (result %d length %u)", result, *messageLen);
  return result;
}

/**
 * FastRPC method invoked by the host to send a message to the system
 *
 * @param buffer
 * @param size
 *
 * @return 0 on success, nonzero on failure
 */
extern "C" int chre_slpi_deliver_message_from_host(const unsigned char *message,
                                                   int messageLen) {
  CHRE_ASSERT(message != nullptr);
  CHRE_ASSERT(messageLen > 0);
  int result = CHRE_FASTRPC_ERROR;

  if (message == nullptr || messageLen <= 0) {
    LOGE("Got null or invalid size (%d) message from host", messageLen);
  } else if (!HostProtocolChre::decodeMessageFromHost(
      message, static_cast<size_t>(messageLen))) {
    LOGE("Failed to decode/handle message");
  } else {
    result = CHRE_FASTRPC_SUCCESS;
  }

  return result;
}

}  // anonymous namespace

bool HostLink::sendMessage(const MessageToHost *message) {
  return gOutboundQueue.push(
      PendingMessage(PendingMessageType::NanoappMessageToHost, message));
}

void HostLinkBase::shutdown() {
  constexpr qurt_timer_duration_t kPollingIntervalUsec = 5000;

  // Push a null message so the blocking call in chre_slpi_get_message_to_host()
  // returns and the host can exit cleanly. If the queue is full, try again to
  // avoid getting stuck (no other new messages should be entering the queue at
  // this time). Don't wait too long as the host-side binary may have died in
  // a state where it's not blocked in chre_slpi_get_message_to_host().
  int retryCount = 5;
  FARF(MEDIUM, "Shutting down host link");
  while (!gOutboundQueue.push(PendingMessage(PendingMessageType::Shutdown))
         && --retryCount > 0) {
    qurt_timer_sleep(kPollingIntervalUsec);
  }

  if (retryCount <= 0) {
    // Don't use LOGE, as it may involve trying to send a message
    FARF(ERROR, "No room in outbound queue for shutdown message and host not "
         "draining queue!");
  } else {
    FARF(MEDIUM, "Draining message queue");

    // We were able to push the shutdown message. Wait for the queue to
    // completely flush before returning.
    int waitCount = 5;
    while (!gOutboundQueue.empty() && --waitCount > 0) {
      qurt_timer_sleep(kPollingIntervalUsec);
    }

    if (waitCount <= 0) {
      FARF(ERROR, "Host took too long to drain outbound queue; exiting anyway");
    } else {
      FARF(MEDIUM, "Finished draining queue");
    }
  }
}

void HostMessageHandlers::handleNanoappMessage(
    uint64_t appId, uint32_t messageType, uint16_t hostEndpoint,
    const void *messageData, size_t messageDataLen) {
  LOGD("Parsed nanoapp message from host: app ID 0x%016" PRIx64 ", endpoint "
       "0x%" PRIx16 ", msgType %" PRIu32 ", payload size %zu",
       appId, hostEndpoint, messageType, messageDataLen);

  HostCommsManager& manager =
      EventLoopManagerSingleton::get()->getHostCommsManager();
  manager.sendMessageToNanoappFromHost(
      appId, messageType, hostEndpoint, messageData, messageDataLen);
}

void HostMessageHandlers::handleHubInfoRequest(uint16_t hostClientId) {
  // We generate the response in the context of chre_slpi_get_message_to_host
  LOGD("Got hub info request from client ID %" PRIu16, hostClientId);
  gOutboundQueue.push(PendingMessage(
      PendingMessageType::HubInfoResponse, hostClientId));
}

void HostMessageHandlers::handleNanoappListRequest(uint16_t hostClientId) {
  LOGD("Got nanoapp list request from client ID %" PRIu16, hostClientId);
  HostClientIdCallbackData cbData = {};
  cbData.hostClientId = hostClientId;
  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::NanoappListResponse, cbData.ptr,
      constructNanoappListCallback);
}

void HostMessageHandlers::handleLoadNanoappRequest(
    uint16_t hostClientId, uint32_t transactionId, uint64_t appId,
    uint32_t appVersion, uint32_t targetApiVersion, const void *appBinary,
    size_t appBinaryLen) {
  auto cbData = MakeUnique<LoadNanoappCallbackData>();

  LOGD("Got load nanoapp request (txnId %" PRIu32 ") for appId 0x%016" PRIx64
       " version 0x%" PRIx32 " target API version 0x%08" PRIx32 " size %zu",
       transactionId, appId, appVersion, targetApiVersion, appBinaryLen);
  if (cbData.isNull() || cbData->nanoapp.isNull()) {
    LOGE("Couldn't allocate load nanoapp callback data");
  } else {
    cbData->transactionId = transactionId;
    cbData->hostClientId  = hostClientId;
    cbData->appId = appId;

    // Note that if this fails, we'll generate the error response in
    // the normal deferred callback
    cbData->nanoapp->loadFromBuffer(appId, appVersion, appBinary, appBinaryLen);
    if (!EventLoopManagerSingleton::get()->deferCallback(
            SystemCallbackType::FinishLoadingNanoapp, cbData.get(),
            finishLoadingNanoappCallback)) {
      LOGE("Couldn't post callback to finish loading nanoapp");
    } else {
      cbData.release();
    }
  }
}

}  // namespace chre
