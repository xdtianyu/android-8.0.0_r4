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

#include "brillo_audio_client_helpers.h"

namespace brillo {

audio_policy_force_use_t BrilloAudioClientHelpers::GetForceUse(
    BAudioUsage usage) {
  if (usage == kUsageMedia)
    return AUDIO_POLICY_FORCE_FOR_MEDIA;
  else
    return AUDIO_POLICY_FORCE_FOR_SYSTEM;
}

audio_stream_type_t BrilloAudioClientHelpers::GetStreamType(BAudioUsage usage) {
  switch (usage) {
    case kUsageAlarm:
      return AUDIO_STREAM_ALARM;
    case kUsageMedia:
      return AUDIO_STREAM_MUSIC;
    case kUsageNotifications:
      return AUDIO_STREAM_NOTIFICATION;
    case kUsageSystem:
      return AUDIO_STREAM_SYSTEM;
    default:
      return AUDIO_STREAM_DEFAULT;
  }
}

BAudioUsage BrilloAudioClientHelpers::GetBAudioUsage(
    audio_stream_type_t stream) {
  switch (stream) {
    case AUDIO_STREAM_ALARM:
      return kUsageAlarm;
    case AUDIO_STREAM_MUSIC:
      return kUsageMedia;
    case AUDIO_STREAM_NOTIFICATION:
      return kUsageNotifications;
    case AUDIO_STREAM_SYSTEM:
      return kUsageSystem;
    default:
      return kUsageInvalid;
  }
}

}  // namespace brillo
