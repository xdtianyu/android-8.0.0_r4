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

// Implementation of brillo_audio_device_info.h.

#include "include/brillo_audio_device_info.h"

#include "brillo_audio_device_info_def.h"
#include "brillo_audio_device_info_internal.h"

using brillo::BAudioDeviceInfoInternal;

BAudioDeviceInfo* BAudioDeviceInfo_new(int device) {
  BAudioDeviceInfo* audio_device_info = new BAudioDeviceInfo;
  audio_device_info->internal_ =
      std::make_unique<BAudioDeviceInfoInternal>(device);
  return audio_device_info;
}

int BAudioDeviceInfo_getType(BAudioDeviceInfo* device) {
  return device->internal_->GetDeviceId();
}

void BAudioDeviceInfo_delete(BAudioDeviceInfo* device) {
  delete device;
}
