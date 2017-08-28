
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

#ifndef CHRE_HOST_HOST_PROTOCOL_HOST_H_
#define CHRE_HOST_HOST_PROTOCOL_HOST_H_

#include <stdint.h>

#include "chre/platform/shared/host_protocol_common.h"
#include "chre_host/host_messages_generated.h"
#include "flatbuffers/flatbuffers.h"

namespace android {
namespace chre {

/**
 * Calling code should provide an implementation of this interface to handle
 * parsed results from decodeMessageFromChre().
 */
class IChreMessageHandlers {
 public:
  virtual ~IChreMessageHandlers() = default;

  virtual void handleNanoappMessage(
      uint64_t appId, uint32_t messageType, uint16_t hostEndpoint,
      const void *messageData, size_t messageDataLen) = 0;

  virtual void handleHubInfoResponse(
      const char *name, const char *vendor,
      const char *toolchain, uint32_t legacyPlatformVersion,
      uint32_t legacyToolchainVersion, float peakMips, float stoppedPower,
      float sleepPower, float peakPower, uint32_t maxMessageLen,
      uint64_t platformId, uint32_t version) = 0;

  virtual void handleNanoappListResponse(
      const ::chre::fbs::NanoappListResponseT& response) = 0;

  virtual void handleLoadNanoappResponse(
      const ::chre::fbs::LoadNanoappResponseT& response) = 0;
};

/**
 * A set of helper methods that simplify the encode/decode of FlatBuffers
 * messages used in communication with CHRE from the host.
 */
class HostProtocolHost : public ::chre::HostProtocolCommon {
 public:
  /**
   * Decodes a message sent from CHRE and invokes the appropriate handler
   * function in the provided interface implementation to handle the parsed
   * result.
   *
   * @param message Buffer containing a complete FlatBuffers CHRE message
   * @param messageLen Size of the message, in bytes
   * @param handlers Set of callbacks to handle the parsed message. If this
   *        function returns success, then exactly one of these functions was
   *        called.
   *
   * @return true if the message was parsed successfully and passed to a handler
   */
  static bool decodeMessageFromChre(const void *message, size_t messageLen,
                                    IChreMessageHandlers& handlers);

  /**
   * Encodes a message requesting hub information from CHRE
   *
   * @param builder A newly constructed FlatBufferBuilder that will be used to
   *        construct the message
   */
  static void encodeHubInfoRequest(flatbuffers::FlatBufferBuilder& builder);

  /**
   * Encodes a message requesting to load a nanoapp specified by the included
   * binary payload and metadata.
   *
   * @param builder A newly constructed FlatBufferBuilder that will be used to
   *        construct the message
   */
  static void encodeLoadNanoappRequest(
      flatbuffers::FlatBufferBuilder& builder, uint32_t transactionId,
      uint64_t appId, uint32_t appVersion, uint32_t targetApiVersion,
      const std::vector<uint8_t>& nanoappBinary);

  /**
   * Encodes a message requesting the list of loaded nanoapps from CHRE
   *
   * @param builder A newly constructed FlatBufferBuilder that will be used to
   *        construct the message
   */
  static void encodeNanoappListRequest(flatbuffers::FlatBufferBuilder& builder);

  /**
   * Decodes the host client ID included in the message container
   *
   * @param message Buffer containing a complete FlatBuffers CHRE message
   * @param messageLen Size of the message, in bytes
   * @param hostClientId Output parameter that will be populated with the client
   *        ID included in the message on success
   *
   * @return true if the host client ID was successfully decoded from the
   *         message
   */
  static bool extractHostClientId(const void *message, size_t messageLen,
                                  uint16_t *hostClientId);

  /**
   * Update the host client ID field in the MessageContainer.
   *
   * @param message Buffer containing a complete FlatBuffers CHRE message
   * @param messageLen Size of the message, in bytes
   * @param hostClientId The value to set the host client ID to
   *
   * @return true if the message was verified successfully, and we were able to
   *         modify the host client ID field
   */
  static bool mutateHostClientId(void *message, size_t messageLen,
                                 uint16_t hostClientId);
};

}  // namespace chre
}  // namespace android

#endif  // CHRE_HOST_HOST_PROTOCOL_HOST_H_
