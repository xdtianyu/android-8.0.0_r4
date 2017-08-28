/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package android.brillo.brilloaudioservice;

import android.brillo.brilloaudioservice.IAudioServiceCallback;

/*
 * Interface for BrilloAudioService that clients can use to get the list of
 * devices currently connected to the system as well as to control volume.
 * Clients can also register callbacks to be notified about changes.
 */
interface IBrilloAudioService {
  // Constants for device enumeration.
  const int GET_DEVICES_INPUTS = 1;
  const int GET_DEVICES_OUTPUTS = 2;

  // Constants for volume control.
  const int VOLUME_BUTTON_PRESS_DOWN = 1;
  const int VOLUME_BUTTON_PRESS_UP = 2;

  // Get the list of devices connected. If flag is GET_DEVICES_INPUTS, then
  // return input devices. Otherwise, return output devices.
  int[] GetDevices(int flag);

  // Set device for a given usage.
  // usage is an int of type audio_policy_force_use_t.
  // config is an int of type audio_policy_forced_cfg_t.
  void SetDevice(int usage, int config);

  // Get the maximum number of steps used for a given stream.
  int GetMaxVolumeSteps(int stream);

  // Set the maximum number of steps to use for a given stream.
  void SetMaxVolumeSteps(int stream, int max_steps);

  // Set the volume for a given (stream, device) tuple.
  void SetVolumeIndex(int stream, int device, int index);

  // Get the current volume for a given (stream, device) tuple.
  int GetVolumeIndex(int stream, int device);

  // Get stream used when volume buttons are pressed.
  int GetVolumeControlStream();

  // Set default stream to use when volume buttons are pressed.
  void SetVolumeControlStream(int stream);

  // Increment volume.
  void IncrementVolume();

  // Decrement volume.
  void DecrementVolume();

  // Register a callback object with the service.
  void RegisterServiceCallback(IAudioServiceCallback callback);

  // Unregister a callback object.
  void UnregisterServiceCallback(IAudioServiceCallback callback);
}
