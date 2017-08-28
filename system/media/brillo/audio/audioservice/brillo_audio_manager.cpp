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

// Implementation of brillo_audio_manager.h.

#include "include/brillo_audio_manager.h"

#include <memory>
#include <stdlib.h>

#include "audio_service_callback.h"
#include "brillo_audio_client.h"
#include "brillo_audio_client_helpers.h"
#include "brillo_audio_device_info_def.h"
#include "brillo_audio_device_info_internal.h"

using brillo::AudioServiceCallback;
using brillo::BrilloAudioClient;
using brillo::BrilloAudioClientHelpers;

struct BAudioManager {
  std::weak_ptr<BrilloAudioClient> client_;
};

BAudioManager* BAudioManager_new() {
  auto client = BrilloAudioClient::GetClientInstance();
  if (!client.lock())
    return nullptr;
  BAudioManager* bam = new BAudioManager;
  bam->client_ = client;
  return bam;
}

int BAudioManager_getDevices(
    const BAudioManager* brillo_audio_manager, int flag,
    BAudioDeviceInfo* device_array[], unsigned int size,
    unsigned int* num_devices) {
  if (!brillo_audio_manager || !num_devices ||
      (flag != GET_DEVICES_INPUTS && flag != GET_DEVICES_OUTPUTS))
    return EINVAL;
  auto client = brillo_audio_manager->client_.lock();
  if (!client) {
    *num_devices = 0;
    return ECONNABORTED;
  }
  std::vector<int> devices;
  auto rc = client->GetDevices(flag, devices);
  if (rc) {
    *num_devices = 0;
    return rc;
  }
  unsigned int num_elems = (devices.size() < size) ? devices.size() : size;
  for (size_t i = 0; i < num_elems; i++) {
    device_array[i] = new BAudioDeviceInfo;
    device_array[i]->internal_ = std::unique_ptr<BAudioDeviceInfoInternal>(
        BAudioDeviceInfoInternal::CreateFromAudioDevicesT(devices[i]));
  }
  *num_devices = devices.size();
  return 0;
}

int BAudioManager_setInputDevice(const BAudioManager* brillo_audio_manager,
                                 const BAudioDeviceInfo* device) {
  if (!brillo_audio_manager || !device)
    return EINVAL;
  auto client = brillo_audio_manager->client_.lock();
  if (!client) {
    return ECONNABORTED;
  }
  return client->SetDevice(AUDIO_POLICY_FORCE_FOR_RECORD,
                           device->internal_->GetConfig());
}

int BAudioManager_setOutputDevice(
    const BAudioManager* brillo_audio_manager, const BAudioDeviceInfo* device,
    BAudioUsage usage) {
  if (!brillo_audio_manager || !device)
    return EINVAL;
  auto client = brillo_audio_manager->client_.lock();
  if (!client)
    return ECONNABORTED;
  return client->SetDevice(BrilloAudioClientHelpers::GetForceUse(usage),
                           device->internal_->GetConfig());
}

int BAudioManager_getMaxVolumeSteps(const BAudioManager* brillo_audio_manager,
                                    BAudioUsage usage,
                                    int* max_steps) {
  if (!brillo_audio_manager || !max_steps)
    return EINVAL;
  auto client = brillo_audio_manager->client_.lock();
  if (!client)
    return ECONNABORTED;
  return client->GetMaxVolumeSteps(usage, max_steps);
}

int BAudioManager_setMaxVolumeSteps(const BAudioManager* brillo_audio_manager,
                                    BAudioUsage usage,
                                    int max_steps) {
  if (!brillo_audio_manager || max_steps < 0 || max_steps > 100)
    return EINVAL;
  auto client = brillo_audio_manager->client_.lock();
  if (!client)
    return ECONNABORTED;
  return client->SetMaxVolumeSteps(usage, max_steps);
}

int BAudioManager_setVolumeIndex(const BAudioManager* brillo_audio_manager,
                                 BAudioUsage usage,
                                 const BAudioDeviceInfo* device,
                                 int index) {
  if (!brillo_audio_manager || !device) {
    return EINVAL;
  }
  auto client = brillo_audio_manager->client_.lock();
  if (!client) {
    return ECONNABORTED;
  }
  return client->SetVolumeIndex(
      usage, device->internal_->GetAudioDevicesT(), index);
}

int BAudioManager_getVolumeIndex(const BAudioManager* brillo_audio_manager,
                                 BAudioUsage usage,
                                 const BAudioDeviceInfo* device,
                                 int* index) {
  if (!brillo_audio_manager || !device || !index) {
    return EINVAL;
  }
  auto client = brillo_audio_manager->client_.lock();
  if (!client) {
    return ECONNABORTED;
  }
  return client->GetVolumeIndex(
      usage, device->internal_->GetAudioDevicesT(), index);
}

int BAudioManager_getVolumeControlUsage(
    const BAudioManager* brillo_audio_manager, BAudioUsage* usage) {
  if (!brillo_audio_manager || !usage) {
    return EINVAL;
  }
  auto client = brillo_audio_manager->client_.lock();
  if (!client) {
    return ECONNABORTED;
  }
  return client->GetVolumeControlStream(usage);
}

int BAudioManager_setVolumeControlUsage(
    const BAudioManager* brillo_audio_manager, BAudioUsage usage) {
  if (!brillo_audio_manager) {
    return EINVAL;
  }
  auto client = brillo_audio_manager->client_.lock();
  if (!client) {
    return ECONNABORTED;
  }
  return client->SetVolumeControlStream(usage);
}

int BAudioManager_incrementVolume(const BAudioManager* brillo_audio_manager) {
  if (!brillo_audio_manager) {
    return EINVAL;
  }
  auto client = brillo_audio_manager->client_.lock();
  if (!client) {
    return ECONNABORTED;
  }
  return client->IncrementVolume();
}

int BAudioManager_decrementVolume(const BAudioManager* brillo_audio_manager) {
  if (!brillo_audio_manager) {
    return EINVAL;
  }
  auto client = brillo_audio_manager->client_.lock();
  if (!client) {
    return ECONNABORTED;
  }
  return client->DecrementVolume();
}

int BAudioManager_registerAudioCallback(
    const BAudioManager* brillo_audio_manager, const BAudioCallback* callback,
    void* user_data, int* callback_id) {
  if (!brillo_audio_manager || !callback || !callback_id)
    return EINVAL;
  auto client = brillo_audio_manager->client_.lock();
  if (!client) {
    *callback_id = 0;
    return ECONNABORTED;
  }
  // This copies the BAudioCallback into AudioServiceCallback so the
  // BAudioCallback can be safely deleted.
  return client->RegisterAudioCallback(
      new AudioServiceCallback(callback, user_data), callback_id);
}

int BAudioManager_unregisterAudioCallback(
    const BAudioManager* brillo_audio_manager, int callback_id) {
  if (!brillo_audio_manager)
    return EINVAL;
  auto client = brillo_audio_manager->client_.lock();
  if (!client)
    return ECONNABORTED;
  return client->UnregisterAudioCallback(callback_id);
}

int BAudioManager_delete(BAudioManager* brillo_audio_manager) {
  if (!brillo_audio_manager)
    return EINVAL;
  delete brillo_audio_manager;
  return 0;
}
