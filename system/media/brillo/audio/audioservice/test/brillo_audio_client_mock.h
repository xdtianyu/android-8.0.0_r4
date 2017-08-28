// Copyright 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

// Mock for the brillo audio client.

#ifndef BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_CLIENT_MOCK_H_
#define BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_CLIENT_MOCK_H_

#include <gmock/gmock.h>
#include <gtest/gtest_prod.h>

#include "brillo_audio_client.h"

namespace brillo {

class BrilloAudioClientMock : public BrilloAudioClient {
 public:
  virtual ~BrilloAudioClientMock() = default;

  MOCK_METHOD0(OnBASDisconnect, void());

 private:
  friend class BrilloAudioClientTest;
  FRIEND_TEST(BrilloAudioClientTest,
              CheckInitializeRegistersForDeathNotifications);
  FRIEND_TEST(BrilloAudioClientTest, InitializeNoService);

  BrilloAudioClientMock() = default;
};

}  // namespace brillo

#endif  // BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_CLIENT_MOCK_H_
