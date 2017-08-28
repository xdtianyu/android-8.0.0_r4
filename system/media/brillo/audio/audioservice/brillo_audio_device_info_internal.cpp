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

// Internal helpers for BAudioDeviceInfo.

#include "brillo_audio_device_info_internal.h"

#include <base/logging.h>

#include "brillo_audio_device_info_def.h"

namespace brillo {

BAudioDeviceInfoInternal::BAudioDeviceInfoInternal(int device_id) {
  device_id_ = device_id;
}

int BAudioDeviceInfoInternal::GetDeviceId() {
  return device_id_;
}

audio_policy_forced_cfg_t BAudioDeviceInfoInternal::GetConfig() {
  switch (device_id_) {
    case TYPE_BUILTIN_SPEAKER:
      return AUDIO_POLICY_FORCE_SPEAKER;
    case TYPE_WIRED_HEADSET:
      return AUDIO_POLICY_FORCE_HEADPHONES;
    case TYPE_WIRED_HEADSET_MIC:
      return AUDIO_POLICY_FORCE_HEADPHONES;
    case TYPE_WIRED_HEADPHONES:
      return AUDIO_POLICY_FORCE_HEADPHONES;
    case TYPE_BUILTIN_MIC:
      return AUDIO_POLICY_FORCE_NONE;
    default:
      return AUDIO_POLICY_FORCE_NONE;
  }
}

audio_devices_t BAudioDeviceInfoInternal::GetAudioDevicesT() {
  switch (device_id_) {
    case TYPE_BUILTIN_SPEAKER:
      return AUDIO_DEVICE_OUT_SPEAKER;
    case TYPE_WIRED_HEADSET:
      return AUDIO_DEVICE_OUT_WIRED_HEADSET;
    case TYPE_WIRED_HEADSET_MIC:
      return AUDIO_DEVICE_IN_WIRED_HEADSET;
    case TYPE_WIRED_HEADPHONES:
      return AUDIO_DEVICE_OUT_WIRED_HEADPHONE;
    case TYPE_BUILTIN_MIC:
      return AUDIO_DEVICE_IN_BUILTIN_MIC;
    default:
      return AUDIO_DEVICE_NONE;
  }
}

BAudioDeviceInfoInternal* BAudioDeviceInfoInternal::CreateFromAudioDevicesT(
    unsigned int device) {
  int device_id = TYPE_UNKNOWN;
  switch (device) {
    case AUDIO_DEVICE_OUT_WIRED_HEADSET:
      device_id = TYPE_WIRED_HEADSET;
      break;
    case AUDIO_DEVICE_OUT_WIRED_HEADPHONE:
      device_id = TYPE_WIRED_HEADPHONES;
      break;
    case AUDIO_DEVICE_IN_WIRED_HEADSET:
      device_id = TYPE_WIRED_HEADSET_MIC;
      break;
  }
  if (device_id == TYPE_UNKNOWN) {
    LOG(ERROR) << "Unsupported device.";
    return nullptr;
  }
  return new BAudioDeviceInfoInternal(device_id);
}

}  // namespace brillo
