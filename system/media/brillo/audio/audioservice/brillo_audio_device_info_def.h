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

// Definition of BAudioDeviceInfo.

#ifndef BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_DEVICE_INFO_DEF_H_
#define BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_DEVICE_INFO_DEF_H_


#include <memory>

#include "brillo_audio_device_info_internal.h"
#include "include/brillo_audio_device_info.h"

using brillo::BAudioDeviceInfoInternal;

struct BAudioDeviceInfo {
  std::unique_ptr<BAudioDeviceInfoInternal> internal_;
};

#endif  // BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_DEVICE_INFO_DEF_H_
