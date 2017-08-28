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

// Implementation of brillo_audio_service_impl.h

#include "brillo_audio_service_impl.h"

using android::binder::Status;

namespace brillo {

Status BrilloAudioServiceImpl::GetDevices(int flag,
                                          std::vector<int>* _aidl_return) {
  auto device_handler = audio_device_handler_.lock();
  if (!device_handler) {
    return Status::fromServiceSpecificError(
        EREMOTEIO, android::String8("The audio device handler died."));
  }
  if (flag == BrilloAudioService::GET_DEVICES_INPUTS) {
    device_handler->GetInputDevices(_aidl_return);
  } else if (flag == BrilloAudioService::GET_DEVICES_OUTPUTS) {
    device_handler->GetOutputDevices(_aidl_return);
  } else {
    return Status::fromServiceSpecificError(EINVAL,
                                            android::String8("Invalid flag."));
  }
  return Status::ok();
}

Status BrilloAudioServiceImpl::SetDevice(int usage, int config) {
  auto device_handler = audio_device_handler_.lock();
  if (!device_handler) {
    return Status::fromServiceSpecificError(
        EREMOTEIO, android::String8("The audio device handler died."));
  }
  int rc =
      device_handler->SetDevice(static_cast<audio_policy_force_use_t>(usage),
                                static_cast<audio_policy_forced_cfg_t>(config));
  if (rc) return Status::fromServiceSpecificError(rc);
  return Status::ok();
}

Status BrilloAudioServiceImpl::RegisterServiceCallback(
    const android::sp<IAudioServiceCallback>& callback) {
  callbacks_set_.insert(callback);
  return Status::ok();
}

Status BrilloAudioServiceImpl::UnregisterServiceCallback(
    const android::sp<IAudioServiceCallback>& callback) {
  callbacks_set_.erase(callback);
  return Status::ok();
}

void BrilloAudioServiceImpl::RegisterHandlers(
    std::weak_ptr<AudioDeviceHandler> audio_device_handler,
    std::weak_ptr<AudioVolumeHandler> audio_volume_handler) {
  audio_device_handler_ = audio_device_handler;
  audio_volume_handler_ = audio_volume_handler;
}

Status BrilloAudioServiceImpl::GetMaxVolumeSteps(int stream,
                                                 int* _aidl_return) {
  auto volume_handler = audio_volume_handler_.lock();
  if (!volume_handler) {
    return Status::fromServiceSpecificError(
        EREMOTEIO, android::String8("The audio volume handler died."));
  }
  *_aidl_return = volume_handler->GetVolumeMaxSteps(
      static_cast<audio_stream_type_t>(stream));
  return Status::ok();
}

Status BrilloAudioServiceImpl::SetMaxVolumeSteps(int stream, int max_steps) {
  auto volume_handler = audio_volume_handler_.lock();
  if (!volume_handler) {
    return Status::fromServiceSpecificError(
        EREMOTEIO, android::String8("The audio volume handler died."));
  }
  int rc = volume_handler->SetVolumeMaxSteps(
      static_cast<audio_stream_type_t>(stream), max_steps);
  if (rc)
    return Status::fromServiceSpecificError(rc);
  return Status::ok();
}

Status BrilloAudioServiceImpl::SetVolumeIndex(int stream,
                                              int device,
                                              int index) {
  auto volume_handler = audio_volume_handler_.lock();
  if (!volume_handler) {
    return Status::fromServiceSpecificError(
        EREMOTEIO, android::String8("The audio volume handler died."));
  }
  int rc =
      volume_handler->SetVolumeIndex(static_cast<audio_stream_type_t>(stream),
                                     static_cast<audio_devices_t>(device),
                                     index);
  if (rc)
    return Status::fromServiceSpecificError(rc);
  return Status::ok();
}

Status BrilloAudioServiceImpl::GetVolumeIndex(int stream,
                                              int device,
                                              int* _aidl_return) {
  auto volume_handler = audio_volume_handler_.lock();
  if (!volume_handler) {
    return Status::fromServiceSpecificError(
        EREMOTEIO, android::String8("The audio volume handler died."));
  }
  *_aidl_return =
      volume_handler->GetVolumeIndex(static_cast<audio_stream_type_t>(stream),
                                     static_cast<audio_devices_t>(device));
  return Status::ok();
}

Status BrilloAudioServiceImpl::IncrementVolume() {
  auto volume_handler = audio_volume_handler_.lock();
  if (!volume_handler) {
    return Status::fromServiceSpecificError(
        EREMOTEIO, android::String8("The audio volume handler died."));
  }
  volume_handler->AdjustVolumeActiveStreams(1);
  return Status::ok();
}

Status BrilloAudioServiceImpl::GetVolumeControlStream(int* _aidl_return) {
  auto volume_handler = audio_volume_handler_.lock();
  if (!volume_handler) {
    return Status::fromServiceSpecificError(
        EREMOTEIO, android::String8("The audio volume handler died."));
  }
  *_aidl_return = volume_handler->GetVolumeControlStream();
  return Status::ok();
}

Status BrilloAudioServiceImpl::SetVolumeControlStream(int stream) {
  auto volume_handler = audio_volume_handler_.lock();
  if (!volume_handler) {
    return Status::fromServiceSpecificError(
        EREMOTEIO, android::String8("The audio volume handler died."));
  }
  volume_handler->SetVolumeControlStream(
      static_cast<audio_stream_type_t>(stream));
  return Status::ok();
}

Status BrilloAudioServiceImpl::DecrementVolume() {
  auto volume_handler = audio_volume_handler_.lock();
  if (!volume_handler) {
    return Status::fromServiceSpecificError(
        EREMOTEIO, android::String8("The audio volume handler died."));
  }
  volume_handler->AdjustVolumeActiveStreams(-1);
  return Status::ok();
}

void BrilloAudioServiceImpl::OnDevicesConnected(
    const std::vector<int>& devices) {
  for (const auto& callback : callbacks_set_) {
    callback->OnAudioDevicesConnected(devices);
  }
}

void BrilloAudioServiceImpl::OnDevicesDisconnected(
    const std::vector<int>& devices) {
  for (const auto& callback : callbacks_set_) {
    callback->OnAudioDevicesDisconnected(devices);
  }
}

void BrilloAudioServiceImpl::OnVolumeChanged(audio_stream_type_t stream,
                                             int previous_index,
                                             int current_index) {
  for (const auto& callback : callbacks_set_) {
    callback->OnVolumeChanged(stream, previous_index, current_index);
  }
}

}  // namespace brillo
