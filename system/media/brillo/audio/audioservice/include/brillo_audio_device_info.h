// copyright 2016 the android open source project
//
// licensed under the apache license, version 2.0 (the "license");
// you may not use this file except in compliance with the license.
// you may obtain a copy of the license at
//
//      http://www.apache.org/licenses/license-2.0
//
// unless required by applicable law or agreed to in writing, software
// distributed under the license is distributed on an "as is" basis,
// without warranties or conditions of any kind, either express or implied.
// see the license for the specific language governing permissions and
// limitations under the license.
//

// Type to represent audio devices in a brillo system.

#ifndef BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_DEVICE_INFO_H_
#define BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_DEVICE_INFO_H_

#include <sys/cdefs.h>

__BEGIN_DECLS

struct BAudioDeviceInfo;

typedef struct BAudioDeviceInfo BAudioDeviceInfo;

// A device type associated with an unknown or uninitialized device.
static const int TYPE_UNKNOWN = 0;

// A device type describing the speaker system (i.e. a mono speaker or stereo
// speakers) built in a device.
static const int TYPE_BUILTIN_SPEAKER = 1;

// A device type describing a headset, which is the combination of a headphones
// and microphone. This type represents just the transducer in the headset.
static const int TYPE_WIRED_HEADSET = 2;

// A device type describing a headset, which is the combination of a headphones
// and microphone. This type represents the microphone in the headset.
static const int TYPE_WIRED_HEADSET_MIC = 3;

// A device type describing a pair of wired headphones.
static const int TYPE_WIRED_HEADPHONES = 4;

// A device type describing the microphone(s) built in a device.
static const int TYPE_BUILTIN_MIC = 5;

// Create a BAudioDeviceInfo based on a type described above.
//
// Arg:
//   device: An int representing an audio type as defined above.
//
// Returns a pointer to a BAudioDeviceInfo object.
BAudioDeviceInfo* BAudioDeviceInfo_new(int device);

// Get the type of the device.
//
// Arg:
//   device: A pointer to a BAudioDeviceInfo object to be freed.
//
// Returns an int representing the type of the device.
int BAudioDeviceInfo_getType(BAudioDeviceInfo* device);

// Free a BAudioDeviceInfo.
//
// Arg:
//   device: A pointer to a BAudioDeviceInfo object to be freed.
void BAudioDeviceInfo_delete(BAudioDeviceInfo* device);

__END_DECLS

#endif  // BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_DEVICE_INFO_H_
