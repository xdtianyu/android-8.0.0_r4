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

// Handler for input events in /dev/input. AudioDaemonHandler is the base class
// that other handlers inherit.

#ifndef BRILLO_AUDIO_AUDIOSERVICE_AUDIO_DAEMON_HANDLER_H_
#define BRILLO_AUDIO_AUDIOSERVICE_AUDIO_DAEMON_HANDLER_H_

#include <linux/input.h>
#include <media/IAudioPolicyService.h>

namespace brillo {

class AudioDaemonHandler {
 public:
  virtual ~AudioDaemonHandler(){};

  // Initialize the handler.
  //
  // |aps| is a pointer to the binder object.
  virtual void Init(android::sp<android::IAudioPolicyService> aps) = 0;

  // Process input events from the kernel.
  //
  // |event| is a pointer to an input_event. This function should be able to
  // gracefully handle input events that are not relevant to the functionality
  // provided by this class.
  virtual void ProcessEvent(const struct input_event& event) = 0;

  // Inform the handler that the audio policy service has been disconnected.
  virtual void APSDisconnect() = 0;

  // Inform the handler that the audio policy service is reconnected.
  //
  // |aps| is a pointer to the binder object.
  virtual void APSConnect(android::sp<android::IAudioPolicyService> aps) = 0;

 protected:
  // Pointer to the audio policy service.
  android::sp<android::IAudioPolicyService> aps_;
};

}  // namespace brillo

#endif  // BRILLO_AUDIO_AUDIOSERVICE_AUDIO_DAEMON_HANDLER_H_
