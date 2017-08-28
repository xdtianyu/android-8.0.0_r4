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

// Helpers for the brillo audio client.

#ifndef BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_CLIENT_HELPERS_H_
#define BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_CLIENT_HELPERS_H_

#include <gtest/gtest_prod.h>
#include <system/audio.h>
#include <system/audio_policy.h>

#include "include/brillo_audio_manager.h"

namespace brillo {

class BrilloAudioClientHelpers {
 public:
  static audio_policy_force_use_t GetForceUse(BAudioUsage usage);
  static audio_stream_type_t GetStreamType(BAudioUsage usage);
  static BAudioUsage GetBAudioUsage(audio_stream_type_t stream);
};

}  // namespace brillo

#endif  // BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_CLIENT_HELPERS_H_
