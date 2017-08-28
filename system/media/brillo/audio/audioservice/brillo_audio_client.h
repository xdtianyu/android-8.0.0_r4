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

// Client for the brilloaudioservice.

#ifndef BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_CLIENT_H_
#define BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_CLIENT_H_

#include <map>
#include <memory>
#include <vector>

#include <base/bind.h>
#include <base/memory/weak_ptr.h>
#include <gtest/gtest_prod.h>
#include <media/IAudioPolicyService.h>

#include "android/brillo/brilloaudioservice/IBrilloAudioService.h"
#include "audio_service_callback.h"

using android::brillo::brilloaudioservice::IBrilloAudioService;

namespace brillo {

class BrilloAudioClient {
 public:
  virtual ~BrilloAudioClient();

  // Get or create a pointer to the client instance.
  //
  // Returns a weak_ptr to a BrilloAudioClient object.
  static std::weak_ptr<BrilloAudioClient> GetClientInstance();

  // Query brillo audio service to get list of connected audio devices.
  //
  // |flag| is an int which is either GET_DEVICES_INPUTS or GET_DEVICES_OUTPUTS.
  // |devices| is a reference to a vector of audio_devices_t.
  //
  // Returns 0 on success and errno on failure.
  int GetDevices(int flag, std::vector<int>& devices);

  // Register a callback object with the service.
  //
  // |callback| is a ref pointer to a callback object to be register with the
  // brillo audio service.
  // |callback_id| is a pointer to an int that represents a callback id token on
  // success and 0 on failure.
  //
  // Returns 0 on success and errno on failure.
  int RegisterAudioCallback(android::sp<AudioServiceCallback> callback,
                            int* callback_id);

  // Unregister a callback object with the service.
  //
  // |callback_id| is an int referring to the callback object.
  //
  // Returns 0 on success and errno on failure.
  int UnregisterAudioCallback(int callback_id);

  // Set a device to be the default. This does not communicate with the brillo
  // audio service but instead communicates directly with the audio policy
  // service.
  //
  // Please see system/audio_policy.h for details on these arguments.
  //
  // Returns 0 on success and errno on failure.
  int SetDevice(audio_policy_force_use_t usage,
                audio_policy_forced_cfg_t config);

  // Get the maximum number of steps for a given BAudioUsage.
  //
  // |usage| is an enum of type BAudioUsage.
  // |max_steps| is a pointer to the maximum number of steps.
  //
  // Returns 0 on success and errno on failure.
  int GetMaxVolumeSteps(BAudioUsage usage, int* max_steps);

  // Set the maximum number of steps to use for a given BAudioUsage.
  //
  // |usage| is an enum of type BAudioUsage.
  // |max_steps| is an int between 0 and 100.
  //
  // Returns 0 on success and errno on failure.
  int SetMaxVolumeSteps(BAudioUsage usage, int max_steps);

  // Set the volume index for a given BAudioUsage and device.
  //
  // |usage| is an enum of type BAudioUsage.
  // |device| is of type audio_devices_t.
  // |index| is an int representing the current index.
  //
  // Returns 0 on success and errno on failure.
  int SetVolumeIndex(BAudioUsage usage, audio_devices_t device, int index);

  // Get the volume index for a given BAudioUsage and device.
  //
  // |usage| is an enum of type BAudioUsage.
  // |device| is of type audio_devices_t.
  // |index| is a pointer to an int representing the current index.
  //
  // Returns 0 on success and errno on failure.
  int GetVolumeIndex(BAudioUsage usage, audio_devices_t device, int* index);

  // Get default stream to use for volume buttons.
  //
  // |usage| is a pointer to a BAudioUsage.
  //
  // Returns 0 on success and errno on failure.
  int GetVolumeControlStream(BAudioUsage* usage);

  // Set default stream to use for volume buttons.
  //
  // |usage| is an enum of type BAudioUsage.
  //
  // Returns 0 on success and errno on failure.
  int SetVolumeControlStream(BAudioUsage usage);

  // Increment the volume.
  //
  // Returns 0 on success and errno on failure.
  int IncrementVolume();

  // Decrement the volume.
  //
  // Returns 0 on success and errno on failure.
  int DecrementVolume();

 protected:
  BrilloAudioClient() = default;

 private:
  friend class BrilloAudioClientTest;
  FRIEND_TEST(BrilloAudioClientTest, InitializeNoService);
  FRIEND_TEST(BrilloAudioClientTest,
              CheckInitializeRegistersForDeathNotifications);

  // Initialize the BrilloAudioClient object and connects to the brillo audio
  // service and the audio policy service. It also registers for death
  // notifications.
  bool Initialize();

  // Callback to be triggered when the brillo audio service dies. It attempts to
  // reconnect to the service.
  virtual void OnBASDisconnect();

  // Helper method to connect to a service and register a callback to receive
  // death notifications.
  //
  // |service_name| is a string representing the name of the service.
  // |callback| is a base::Closure which will be called if the service dies.
  android::sp<android::IBinder> ConnectToService(const std::string& service_name,
                                                 const base::Closure& callback);

  // Pointer to the BrilloAudioClient object.
  static std::shared_ptr<BrilloAudioClient> instance_;

  // Used to generate weak_ptr to BrilloAudioClient for use in base::Bind.
  base::WeakPtrFactory<BrilloAudioClient> weak_ptr_factory_{this};
  // Pointer to the brillo audio service.
  android::sp<IBrilloAudioService> brillo_audio_service_;
  // Counter for callback IDs.
  static int callback_id_counter_;
  // Map of callback ids to callback objects.
  std::map<int, android::sp<AudioServiceCallback> > callback_map_;

  DISALLOW_COPY_AND_ASSIGN(BrilloAudioClient);
};

}  // namespace brillo

#endif  // BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_CLIENT_H_
