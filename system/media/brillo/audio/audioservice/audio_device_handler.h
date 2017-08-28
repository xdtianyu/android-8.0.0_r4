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

// Handler for input events in /dev/input. AudioDeviceHandler handles events
// only for audio devices being plugged in/removed from the system. Implements
// some of the functionality present in WiredAccessoryManager.java.

#ifndef BRILLO_AUDIO_AUDIOSERVICE_AUDIO_DEVICE_HANDLER_H_
#define BRILLO_AUDIO_AUDIOSERVICE_AUDIO_DEVICE_HANDLER_H_

#include <set>
#include <vector>

#include <base/bind.h>
#include <base/files/file_path.h>
#include <gtest/gtest_prod.h>
#include <linux/input.h>
#include <media/IAudioPolicyService.h>
#include <system/audio.h>
#include <system/audio_policy.h>

#include "audio_daemon_handler.h"

namespace brillo {

class AudioDeviceHandler : public AudioDaemonHandler {
 public:
  AudioDeviceHandler();
  virtual ~AudioDeviceHandler();

  // Get the current state of the headset jack and update AudioSystem based on
  // the initial state.
  //
  // |aps| is a pointer to the binder object.
  virtual void Init(android::sp<android::IAudioPolicyService> aps) override;

  // Process input events from the kernel. Connecting/disconnecting an audio
  // device will result in multiple calls to this method.
  //
  // |event| is a pointer to an input_event. This function should be able to
  // gracefully handle input events that are not relevant to the functionality
  // provided by this class.
  virtual void ProcessEvent(const struct input_event& event) override;

  // Inform the handler that the audio policy service has been disconnected.
  void APSDisconnect();

  // Inform the handler that the audio policy service is reconnected.
  //
  // |aps| is a pointer to the binder object.
  virtual void APSConnect(
      android::sp<android::IAudioPolicyService> aps) override;

  // Get the list of connected devices.
  //
  // |devices_list| is the vector to copy list of connected input devices to.
  void GetInputDevices(std::vector<int>* devices_list);

  // Get the list of connected output devices.
  //
  // |devices_list| is the vector to copy the list of connected output devices
  // to.
  void GetOutputDevices(std::vector<int>* devices_list);

  // Set device.
  //
  // |usage| is an int of type audio_policy_force_use_t
  // |config| is an int of type audio_policy_forced_cfg_t.
  //
  // Returns 0 on sucess and errno on failure.
  int SetDevice(audio_policy_force_use_t usage,
                audio_policy_forced_cfg_t config);

  // Enum used to represent whether devices are being connected or not. This is
  // used when triggering callbacks.
  enum DeviceConnectionState {
    kDevicesConnected,
    kDevicesDisconnected
  };

  // Register a callback function to call when device state changes.
  //
  // |callback| is an object of type base::Callback that accepts a
  // DeviceConnectionState and a vector of ints. See DeviceCallback() in
  // audio_daemon.h.
  void RegisterDeviceCallback(
      base::Callback<void(DeviceConnectionState,
                          const std::vector<int>& )>& callback);

 private:
  friend class AudioDeviceHandlerTest;
  friend class AudioVolumeHandler;
  friend class AudioVolumeHandlerTest;
  FRIEND_TEST(AudioDeviceHandlerTest,
              DisconnectAllSupportedDevicesCallsDisconnect);
  FRIEND_TEST(AudioDeviceHandlerTest, InitCallsDisconnectAllSupportedDevices);
  FRIEND_TEST(AudioDeviceHandlerTest, InitialAudioStateMic);
  FRIEND_TEST(AudioDeviceHandlerTest, InitialAudioStateHeadphone);
  FRIEND_TEST(AudioDeviceHandlerTest, InitialAudioStateHeadset);
  FRIEND_TEST(AudioDeviceHandlerTest, InitialAudioStateNone);
  FRIEND_TEST(AudioDeviceHandlerTest, InitialAudioStateInvalid);
  FRIEND_TEST(AudioDeviceHandlerTest, ProcessEventEmpty);
  FRIEND_TEST(AudioDeviceHandlerTest, ProcessEventMicrophonePresent);
  FRIEND_TEST(AudioDeviceHandlerTest, ProcessEventHeadphonePresent);
  FRIEND_TEST(AudioDeviceHandlerTest, ProcessEventMicrophoneNotPresent);
  FRIEND_TEST(AudioDeviceHandlerTest, ProcessEventHeadphoneNotPresent);
  FRIEND_TEST(AudioDeviceHandlerTest, ProcessEventInvalid);
  FRIEND_TEST(AudioDeviceHandlerTest, UpdateAudioSystemNone);
  FRIEND_TEST(AudioDeviceHandlerTest, UpdateAudioSystemConnectMic);
  FRIEND_TEST(AudioDeviceHandlerTest, UpdateAudioSystemConnectHeadphone);
  FRIEND_TEST(AudioDeviceHandlerTest, UpdateAudioSystemConnectHeadset);
  FRIEND_TEST(AudioDeviceHandlerTest, UpdateAudioSystemDisconnectMic);
  FRIEND_TEST(AudioDeviceHandlerTest, UpdateAudioSystemDisconnectHeadphone);
  FRIEND_TEST(AudioDeviceHandlerTest, UpdateAudioSystemDisconnectHeadset);
  FRIEND_TEST(AudioDeviceHandlerTest, ConnectAudioDeviceInput);
  FRIEND_TEST(AudioDeviceHandlerTest, ConnectAudioDeviceOutput);
  FRIEND_TEST(AudioDeviceHandlerTest, DisconnectAudioDeviceInput);
  FRIEND_TEST(AudioDeviceHandlerTest, DisconnectAudioDeviceOutput);
  FRIEND_TEST(AudioVolumeHandlerTest, FileGeneration);

  // Read the initial state of audio devices in /sys/class/* and update
  // the audio policy service.
  //
  // |path| is the file that contains the initial audio jack state.
  void GetInitialAudioDeviceState(const base::FilePath& path);

  // Update the audio policy service once an input_event has completed.
  //
  // |headphone| is true is headphones are connected.
  // |microphone| is true is microphones are connected.
  void UpdateAudioSystem(bool headphone, bool microphone);

  // Notify the audio policy service that this device has been removed.
  //
  // |device| is the audio device whose state is to be changed.
  // |state| is the current state of |device|.
  virtual void NotifyAudioPolicyService(audio_devices_t device,
                                        audio_policy_dev_state_t state);

  // Connect an audio device by calling aps and add it to the appropriate set
  // (either connected_input_devices_ or connected_output_devices_).
  //
  // |device| is the audio device that has been added.
  void ConnectAudioDevice(audio_devices_t device);

  // Disconnect an audio device by calling aps and remove it from the
  // appropriate set (either connected_input_devices_ or
  // connected_output_devices_).
  //
  // |device| is the audio device that has been disconnected.
  void DisconnectAudioDevice(audio_devices_t device);

  // Disconnected all connected audio devices.
  void DisconnectAllConnectedDevices();

  // Disconnect all supported audio devices.
  void DisconnectAllSupportedDevices();

  // Trigger a callback when a device is either connected or disconnected.
  //
  // |state| is kDevicesConnected when |devices| are being connected.
  virtual void TriggerCallback(DeviceConnectionState state);

  // All input devices currently supported by AudioDeviceHandler.
  static const std::vector<audio_devices_t> kSupportedInputDevices_;
  // All output devices currently supported by AudioDeviceHandler.
  static const std::vector<audio_devices_t> kSupportedOutputDevices_;

 protected:
  // Set of connected input devices.
  std::set<audio_devices_t> connected_input_devices_;
  // Set of connected output devices.
  std::set<audio_devices_t> connected_output_devices_;
  // Vector of devices changed (used for callbacks to clients).
  std::vector<int> changed_devices_;
  // Keeps track of whether a headphone has been connected. Used by ProcessEvent
  // and UpdateAudioSystem.
  bool headphone_;
  // Keeps track of whether a microphone has been connected. Used by
  // ProcessEvent and UpdateAudioSystem.
  bool microphone_;
  // Callback object to call when device state changes.
  base::Callback<void(DeviceConnectionState,
                      const std::vector<int>& )> callback_;
};

}  // namespace brillo

#endif  // BRILLO_AUDIO_AUDIOSERVICE_AUDIO_DEVICE_HANDLER_H_
