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

/*
 * Interface for the callback object registered with IBrilloAudioService. Used
 * to notify clients about changes to the audio system.
 */
interface IAudioServiceCallback {
  // Oneway call triggered when audio devices are connected to the system.
  oneway void OnAudioDevicesConnected(in int[] added_devices);

  // Oneway call triggered when audio devices are disconnected from the system.
  oneway void OnAudioDevicesDisconnected(in int[] removed_devices);

  // Oneway call triggered when the volume is changed. If there are
  // multiple active streams, this call will be called multiple times.
  oneway void OnVolumeChanged(
      int stream_type, int old_volume_index, int new_volume_index);
}
