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

// Implementation of audio_volume_handler.h

#include "audio_volume_handler.h"

#include <base/files/file.h>
#include <base/files/file_util.h>
#include <base/logging.h>
#include <brillo/map_utils.h>
#include <brillo/message_loops/message_loop.h>
#include <brillo/strings/string_utils.h>

#include "audio_device_handler.h"

namespace brillo {

static const char kVolumeStateFilePath[] =
    "/data/misc/brilloaudioservice/volume.dat";

AudioVolumeHandler::AudioVolumeHandler() {
  for (auto stream : kSupportedStreams_) {
    step_sizes_.emplace(stream, kDefaultStepSize_);
  }
  selected_stream_ = AUDIO_STREAM_DEFAULT;
  volume_state_file_ = base::FilePath(kVolumeStateFilePath);
}

AudioVolumeHandler::~AudioVolumeHandler() {}

void AudioVolumeHandler::APSDisconnect() { aps_.clear(); }

void AudioVolumeHandler::APSConnect(
    android::sp<android::IAudioPolicyService> aps) {
  aps_ = aps;
  InitAPSAllStreams();
}

void AudioVolumeHandler::RegisterCallback(
    base::Callback<void(audio_stream_type_t, int, int)>& callback) {
  callback_ = callback;
}

int AudioVolumeHandler::ConvertToUserDefinedIndex(audio_stream_type_t stream,
                                                  int index) {
  return index / step_sizes_[stream];
}

int AudioVolumeHandler::ConvertToInternalIndex(audio_stream_type_t stream,
                                               int index) {
  return index * step_sizes_[stream];
}

void AudioVolumeHandler::TriggerCallback(audio_stream_type_t stream,
                                         int previous_index,
                                         int current_index) {
  int user_defined_previous_index =
      ConvertToUserDefinedIndex(stream, previous_index);
  int user_defined_current_index =
      ConvertToUserDefinedIndex(stream, current_index);
  MessageLoop::current()->PostTask(base::Bind(callback_,
                                              stream,
                                              user_defined_previous_index,
                                              user_defined_current_index));
}

void AudioVolumeHandler::GenerateVolumeFile() {
  for (auto stream : kSupportedStreams_) {
    for (auto device : AudioDeviceHandler::kSupportedOutputDevices_) {
      PersistVolumeConfiguration(stream, device, kDefaultCurrentIndex_);
    }
  }
  if (!kv_store_->Save(volume_state_file_)) {
    LOG(ERROR) << "Could not save volume data file!";
  }
}

int AudioVolumeHandler::GetVolumeMaxSteps(audio_stream_type_t stream) {
  return ConvertToUserDefinedIndex(stream, kMaxIndex_);
}

int AudioVolumeHandler::SetVolumeMaxSteps(audio_stream_type_t stream,
                                          int max_steps) {
  if (max_steps <= kMinIndex_ || max_steps > kMaxIndex_)
    return EINVAL;
  step_sizes_[stream] = kMaxIndex_ / max_steps;
  return 0;
}

int AudioVolumeHandler::GetVolumeCurrentIndex(audio_stream_type_t stream,
                                              audio_devices_t device) {
  auto key = kCurrentIndexKey_ + "." + string_utils::ToString(stream) + "." +
             string_utils::ToString(device);
  std::string value;
  kv_store_->GetString(key, &value);
  return std::stoi(value);
}

int AudioVolumeHandler::GetVolumeIndex(audio_stream_type_t stream,
                                       audio_devices_t device) {
  return ConvertToUserDefinedIndex(stream,
                                   GetVolumeCurrentIndex(stream, device));
}

int AudioVolumeHandler::SetVolumeIndex(audio_stream_type_t stream,
                                       audio_devices_t device,
                                       int index) {
  if (index < kMinIndex_ ||
      index > ConvertToUserDefinedIndex(stream, kMaxIndex_))
    return EINVAL;
  int previous_index = GetVolumeCurrentIndex(stream, device);
  int current_absolute_index = ConvertToInternalIndex(stream, index);
  PersistVolumeConfiguration(stream, device, current_absolute_index);
  TriggerCallback(stream, previous_index, current_absolute_index);
  return 0;
}

void AudioVolumeHandler::PersistVolumeConfiguration(audio_stream_type_t stream,
                                                    audio_devices_t device,
                                                    int index) {
  auto key = kCurrentIndexKey_ + "." + string_utils::ToString(stream) + "." +
             string_utils::ToString(device);
  kv_store_->SetString(key, string_utils::ToString(index));
  kv_store_->Save(volume_state_file_);
}

void AudioVolumeHandler::InitAPSAllStreams() {
  for (auto stream : kSupportedStreams_) {
    aps_->initStreamVolume(stream, kMinIndex_, kMaxIndex_);
    for (auto device : AudioDeviceHandler::kSupportedOutputDevices_) {
      int current_index = GetVolumeCurrentIndex(stream, device);
      aps_->setStreamVolumeIndex(stream, current_index, device);
    }
  }
}

void AudioVolumeHandler::SetVolumeFilePathForTesting(
    const base::FilePath& path) {
  volume_state_file_ = path;
}

void AudioVolumeHandler::Init(android::sp<android::IAudioPolicyService> aps) {
  aps_ = aps;
  kv_store_ = std::unique_ptr<KeyValueStore>(new KeyValueStore());
  if (!base::PathExists(volume_state_file_)) {
    // Generate key-value store and save it to a file.
    GenerateVolumeFile();
  } else {
    // Load the file. If loading fails, generate the file.
    if (!kv_store_->Load(volume_state_file_)) {
      LOG(ERROR) << "Could not load volume data file!";
      GenerateVolumeFile();
    }
  }
  // Inform APS.
  InitAPSAllStreams();
}

audio_stream_type_t AudioVolumeHandler::GetVolumeControlStream() {
  return selected_stream_;
}

void AudioVolumeHandler::SetVolumeControlStream(audio_stream_type_t stream) {
  selected_stream_ = stream;
}

int AudioVolumeHandler::GetNewVolumeIndex(int previous_index, int direction,
                                          audio_stream_type_t stream) {
  int current_index =
      previous_index + ConvertToInternalIndex(stream, direction);
  if (current_index < kMinIndex_) {
    return kMinIndex_;
  } else if (current_index > kMaxIndex_) {
    return kMaxIndex_;
  } else
    return current_index;
}

void AudioVolumeHandler::AdjustStreamVolume(audio_stream_type_t stream,
                                            int direction) {
  VLOG(1) << "Adjusting volume of stream " << selected_stream_
          << " in direction " << direction;
  auto device = aps_->getDevicesForStream(stream);
  int previous_index = GetVolumeCurrentIndex(stream, device);
  int current_index = GetNewVolumeIndex(previous_index, direction, stream);
  VLOG(1) << "Current index is " << current_index << " for stream " << stream
          << " and device " << device;
  aps_->setStreamVolumeIndex(stream, current_index, device);
  PersistVolumeConfiguration(selected_stream_, device, current_index);
  TriggerCallback(stream, previous_index, current_index);
}

void AudioVolumeHandler::AdjustVolumeActiveStreams(int direction) {
  if (selected_stream_ != AUDIO_STREAM_DEFAULT) {
    AdjustStreamVolume(selected_stream_, direction);
    return;
  }
  for (auto stream : kSupportedStreams_) {
    if (aps_->isStreamActive(stream)) {
      AdjustStreamVolume(stream, direction);
      return;
    }
  }
}

void AudioVolumeHandler::ProcessEvent(const struct input_event& event) {
  VLOG(1) << event.type << " " << event.code << " " << event.value;
  if (event.type == EV_KEY) {
    switch (event.code) {
      case KEY_VOLUMEDOWN:
        AdjustVolumeActiveStreams(-1);
        break;
      case KEY_VOLUMEUP:
        AdjustVolumeActiveStreams(1);
        break;
      default:
        // This event code is not supported by this handler.
        break;
    }
  }
}

}  // namespace brillo
