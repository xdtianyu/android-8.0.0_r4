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

// Mock of audio daemon.

#ifndef BRILLO_AUDIO_AUDIOSERVICE_TEST_AUDIO_DAEMON_MOCK_H_
#define BRILLO_AUDIO_AUDIOSERVICE_TEST_AUDIO_DAEMON_MOCK_H_

#include <gmock/gmock.h>
#include <gtest/gtest_prod.h>

#include "audio_daemon.h"

namespace brillo {

class AudioDaemonMock : public AudioDaemon {
 public:
  AudioDaemonMock() = default;
  ~AudioDaemonMock() {}

 private:
  friend class AudioDaemonTest;
  FRIEND_TEST(AudioDaemonTest, RegisterService);
  FRIEND_TEST(AudioDaemonTest, TestAPSConnectInitializesHandlersOnlyOnce);
  FRIEND_TEST(AudioDaemonTest, TestDeviceCallbackInitializesBASIfNULL);

  MOCK_METHOD0(InitializeHandlers, void());
};

}  // namespace brillo

#endif  // BRILLO_AUDIO_AUDIOSERVICE_TEST_AUDIO_DAEMON_MOCK_H_
