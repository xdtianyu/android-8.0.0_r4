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

// Implementation of brillo_audio_client.h

#include "brillo_audio_client.h"

#include <base/logging.h>
#include <binder/Status.h>
#include <binderwrapper/binder_wrapper.h>

#include "brillo_audio_client_helpers.h"
#include "brillo_audio_device_info_def.h"
#include "brillo_audio_device_info_internal.h"

using android::binder::Status;

namespace brillo {

static const char kBrilloAudioServiceName[] =
    "android.brillo.brilloaudioservice.BrilloAudioService";

std::shared_ptr<BrilloAudioClient> BrilloAudioClient::instance_ = nullptr;

int BrilloAudioClient::callback_id_counter_ = 1;

BrilloAudioClient::~BrilloAudioClient() {}

std::weak_ptr<BrilloAudioClient> BrilloAudioClient::GetClientInstance() {
  if (!instance_) {
    instance_ = std::shared_ptr<BrilloAudioClient>(new BrilloAudioClient());
    if (!instance_->Initialize()) {
      LOG(ERROR) << "Could not Initialize the brillo audio client.";
      instance_.reset();
      return instance_;
    }
  }
  return instance_;
}

android::sp<android::IBinder> BrilloAudioClient::ConnectToService(
    const std::string& service_name, const base::Closure& callback) {
  android::BinderWrapper* binder_wrapper =
      android::BinderWrapper::GetOrCreateInstance();
  auto service = binder_wrapper->GetService(service_name);
  if (!service.get()) {
    return service;
  }
  binder_wrapper->RegisterForDeathNotifications(service, callback);
  return service;
}

void BrilloAudioClient::OnBASDisconnect() {
  LOG(WARNING) << "The brillo audio service died! Please reset the "
               << "BAudioManager.";
  instance_.reset();
}

bool BrilloAudioClient::Initialize() {
  auto service = ConnectToService(
      kBrilloAudioServiceName, base::Bind(&BrilloAudioClient::OnBASDisconnect,
                                          weak_ptr_factory_.GetWeakPtr()));
  if (!service.get()) {
    LOG(ERROR) << "Could not connect to brillo audio service.";
    return false;
  }
  brillo_audio_service_ = android::interface_cast<IBrilloAudioService>(service);
  return true;
}

int BrilloAudioClient::GetDevices(int flag, std::vector<int>& devices) {
  if (!brillo_audio_service_.get()) {
    OnBASDisconnect();
    return ECONNABORTED;
  }
  auto status = brillo_audio_service_->GetDevices(flag, &devices);
  return status.serviceSpecificErrorCode();
}

int BrilloAudioClient::SetDevice(audio_policy_force_use_t usage,
                                 audio_policy_forced_cfg_t config) {
  if (!brillo_audio_service_.get()) {
    OnBASDisconnect();
    return ECONNABORTED;
  }
  auto status = brillo_audio_service_->SetDevice(usage, config);
  return status.serviceSpecificErrorCode();
}

int BrilloAudioClient::GetMaxVolumeSteps(BAudioUsage usage, int* max_steps) {
  if (!brillo_audio_service_.get()) {
    OnBASDisconnect();
    return ECONNABORTED;
  }
  auto status = brillo_audio_service_->GetMaxVolumeSteps(
      BrilloAudioClientHelpers::GetStreamType(usage), max_steps);
  return status.serviceSpecificErrorCode();
}

int BrilloAudioClient::SetMaxVolumeSteps(BAudioUsage usage, int max_steps) {
  if (!brillo_audio_service_.get()) {
    OnBASDisconnect();
    return ECONNABORTED;
  }
  auto status = brillo_audio_service_->SetMaxVolumeSteps(
      BrilloAudioClientHelpers::GetStreamType(usage), max_steps);
  return status.serviceSpecificErrorCode();
}

int BrilloAudioClient::SetVolumeIndex(BAudioUsage usage,
                                      audio_devices_t device,
                                      int index) {
  if (!brillo_audio_service_.get()) {
    OnBASDisconnect();
    return ECONNABORTED;
  }
  auto status = brillo_audio_service_->SetVolumeIndex(
      BrilloAudioClientHelpers::GetStreamType(usage), device, index);
  return status.serviceSpecificErrorCode();
}

int BrilloAudioClient::GetVolumeIndex(BAudioUsage usage,
                                      audio_devices_t device,
                                      int* index) {
  if (!brillo_audio_service_.get()) {
    OnBASDisconnect();
    return ECONNABORTED;
  }
  auto status = brillo_audio_service_->GetVolumeIndex(
      BrilloAudioClientHelpers::GetStreamType(usage), device, index);
  return status.serviceSpecificErrorCode();
}

int BrilloAudioClient::GetVolumeControlStream(BAudioUsage* usage) {
  if (!brillo_audio_service_.get()) {
    OnBASDisconnect();
    return ECONNABORTED;
  }
  int stream;
  auto status = brillo_audio_service_->GetVolumeControlStream(&stream);
  *usage = BrilloAudioClientHelpers::GetBAudioUsage(
      static_cast<audio_stream_type_t>(stream));
  return status.serviceSpecificErrorCode();
}

int BrilloAudioClient::SetVolumeControlStream(BAudioUsage usage) {
  if (!brillo_audio_service_.get()) {
    OnBASDisconnect();
    return ECONNABORTED;
  }
  auto status = brillo_audio_service_->SetVolumeControlStream(
      BrilloAudioClientHelpers::GetStreamType(usage));
  return status.serviceSpecificErrorCode();
}

int BrilloAudioClient::IncrementVolume() {
  if (!brillo_audio_service_.get()) {
    OnBASDisconnect();
    return ECONNABORTED;
  }
  auto status = brillo_audio_service_->IncrementVolume();
  return status.serviceSpecificErrorCode();
}

int BrilloAudioClient::DecrementVolume() {
  if (!brillo_audio_service_.get()) {
    OnBASDisconnect();
    return ECONNABORTED;
  }
  auto status = brillo_audio_service_->DecrementVolume();
  return status.serviceSpecificErrorCode();
}

int BrilloAudioClient::RegisterAudioCallback(
    android::sp<AudioServiceCallback> callback, int* callback_id) {
  if (!brillo_audio_service_.get()) {
    OnBASDisconnect();
    return ECONNABORTED;
  }
  if (!brillo_audio_service_->RegisterServiceCallback(callback).isOk()) {
    *callback_id = 0;
    return ECONNABORTED;
  }
  for (auto& entry : callback_map_) {
    if (entry.second->Equals(callback)) {
      LOG(ERROR) << "Callback has already been registered.";
      *callback_id = 0;
      return EINVAL;
    }
  }
  *callback_id = callback_id_counter_++;
  callback_map_.emplace(*callback_id, callback);
  return 0;
}

int BrilloAudioClient::UnregisterAudioCallback(int callback_id) {
  if (!brillo_audio_service_.get()) {
    OnBASDisconnect();
    return ECONNABORTED;
  }
  auto callback_elem = callback_map_.find(callback_id);
  if (callback_elem == callback_map_.end()) {
    // If we were passed an invalid callback_id, do nothing.
    LOG(ERROR) << "Unregister called with invalid callback ID.";
    return EINVAL;
  }
  brillo_audio_service_->UnregisterServiceCallback(callback_elem->second.get());
  callback_map_.erase(callback_elem);
  return 0;
}

}  // namespace brillo
