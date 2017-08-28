#
# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

vts_test_lib_hidl_packages := \
  libhwbinder \
  libhidlbase \
  libhidltransport \
  android.hardware.audio@2.0-vts.driver \
  android.hardware.audio.common@2.0-vts.driver \
  android.hardware.audio.effect@2.0-vts.driver \
  android.hardware.automotive.evs@1.0-vts.driver \
  android.hardware.automotive.vehicle@2.0-vts.driver \
  android.hardware.automotive.vehicle@2.1-vts.driver \
  android.hardware.biometrics.fingerprint@2.1-vts.driver \
  android.hardware.bluetooth@1.0-vts.driver \
  android.hardware.boot@1.0-vts.driver \
  android.hardware.broadcastradio@1.0-vts.driver \
  android.hardware.camera.common@1.0-vts.driver \
  android.hardware.camera.device@1.0-vts.driver \
  android.hardware.camera.device@3.2-vts.driver \
  android.hardware.camera.metadata@3.2-vts.driver \
  android.hardware.camera.provider@2.4-vts.driver \
  android.hardware.configstore@1.0-vts.driver \
  android.hardware.contexthub@1.0-vts.driver \
  android.hardware.drm@1.0-vts.driver \
  android.hardware.dumpstate@1.0-vts.driver \
  android.hardware.gatekeeper@1.0-vts.driver \
  android.hardware.gnss@1.0-vts.driver \
  android.hardware.graphics.common@1.0-vts.driver \
  android.hardware.graphics.bufferqueue@1.0-vts.driver \
  android.hardware.graphics.allocator@2.0-vts.driver \
  android.hardware.graphics.composer@2.1-vts.driver \
  android.hardware.graphics.mapper@2.0-vts.driver \
  android.hardware.health@1.0-vts.driver \
  android.hardware.ir@1.0-vts.driver \
  android.hardware.keymaster@3.0-vts.driver \
  android.hardware.light@2.0-vts.driver \
  android.hardware.media@1.0-vts.driver \
  android.hardware.media.omx@1.0-vts.driver \
  android.hardware.memtrack@1.0-vts.driver \
  android.hardware.nfc@1.0-vts.driver \
  android.hardware.power@1.0-vts.driver \
  android.hardware.radio@1.0-vts.driver \
  android.hardware.sensors@1.0-vts.driver \
  android.hardware.soundtrigger@2.0-vts.driver \
  android.hardware.thermal@1.0-vts.driver \
  android.hardware.tv.cec@1.0-vts.driver \
  android.hardware.tv.input@1.0-vts.driver \
  android.hardware.usb@1.0-vts.driver \
  android.hardware.vibrator@1.0-vts.driver \
  android.hardware.vr@1.0-vts.driver \
  android.hardware.wifi@1.0-vts.driver \
  android.hardware.wifi.supplicant@1.0-vts.driver \
  android.hardware.audio@2.0-vts.profiler \
  android.hardware.audio.common@2.0-vts.profiler \
  android.hardware.audio.effect@2.0-vts.profiler \
  android.hardware.automotive.vehicle@2.0-vts.profiler \
  android.hardware.automotive.vehicle@2.1-vts.profiler \
  android.hardware.biometrics.fingerprint@2.1-vts.profiler \
  android.hardware.bluetooth@1.0-vts.profiler \
  android.hardware.boot@1.0-vts.profiler \
  android.hardware.broadcastradio@1.0-vts.profiler \
  android.hardware.camera.common@1.0-vts.profiler \
  android.hardware.camera.device@1.0-vts.profiler \
  android.hardware.camera.device@3.2-vts.profiler \
  android.hardware.camera.metadata@3.2-vts.profiler \
  android.hardware.camera.provider@2.4-vts.profiler \
  android.hardware.configstore@1.0-vts.profiler \
  android.hardware.contexthub@1.0-vts.profiler \
  android.hardware.drm@1.0-vts.profiler \
  android.hardware.dumpstate@1.0-vts.profiler \
  android.hardware.automotive.evs@1.0-vts.profiler \
  android.hardware.gatekeeper@1.0-vts.profiler \
  android.hardware.gnss@1.0-vts.profiler \
  android.hardware.graphics.allocator@2.0-vts.profiler \
  android.hardware.graphics.common@1.0-vts.profiler \
  android.hardware.graphics.composer@2.1-vts.profiler \
  android.hardware.graphics.mapper@2.0-vts.profiler \
  android.hardware.health@1.0-vts.profiler \
  android.hardware.ir@1.0-vts.profiler \
  android.hardware.keymaster@3.0-vts.profiler \
  android.hardware.light@2.0-vts.profiler \
  android.hardware.media@1.0-vts.profiler \
  android.hardware.media.omx@1.0-vts.profiler \
  android.hardware.memtrack@1.0-vts.profiler \
  android.hardware.nfc@1.0-vts.profiler \
  android.hardware.power@1.0-vts.profiler \
  android.hardware.radio@1.0-vts.profiler \
  android.hardware.sensors@1.0-vts.profiler \
  android.hardware.soundtrigger@2.0-vts.profiler \
  android.hardware.thermal@1.0-vts.profiler \
  android.hardware.tv.cec@1.0-vts.profiler \
  android.hardware.tv.input@1.0-vts.profiler \
  android.hardware.usb@1.0-vts.profiler \
  android.hardware.vibrator@1.0-vts.profiler \
  android.hardware.vr@1.0-vts.profiler \
  android.hardware.wifi@1.0-vts.profiler \
  android.hardware.wifi.supplicant@1.0-vts.profiler \

vts_test_lib_hidl_packages += \
  VtsHalAudioV2_0TargetTest \
  VtsHalAudioEffectV2_0TargetTest \
  VtsHalBiometricsFingerprintV2_1TargetTest \
  VtsHalBluetoothV1_0TargetTest \
  VtsHalBootV1_0TargetTest \
  VtsHalBroadcastradioV1_0TargetTest \
  VtsHalCameraProviderV2_4TargetTest \
  VtsHalConfigstoreV1_0TargetTest \
  VtsHalContexthubV1_0TargetTest \
  VtsHalDrmV1_0TargetTest \
  VtsHalGatekeeperV1_0TargetTest \
  VtsHalGnssV1_0TargetTest \
  VtsHalGraphicsComposerV2_1TargetTest \
  VtsHalGraphicsMapperV2_0TargetTest \
  VtsHalHealthV1_0TargetTest \
  VtsHalIrV1_0TargetTest \
  VtsHalKeymasterV3_0TargetTest \
  VtsHalLightV2_0TargetTest \
  VtsHalMediaOmxV1_0TargetComponentTest \
  VtsHalMediaOmxV1_0TargetAudioEncTest \
  VtsHalMediaOmxV1_0TargetAudioDecTest \
  VtsHalMediaOmxV1_0TargetVideoEncTest \
  VtsHalMediaOmxV1_0TargetVideoDecTest \
  VtsHalMemtrackV1_0TargetTest \
  VtsHalNfcV1_0TargetTest \
  VtsHalPowerV1_0TargetTest \
  VtsHalRadioV1_0TargetTest \
  VtsHalRenderscriptV1_0TargetTest \
  VtsHalSapV1_0TargetTest \
  VtsHalSensorsV1_0TargetTest \
  VtsHalSoundtriggerV2_0TargetTest \
  VtsHalThermalV1_0TargetTest \
  thermal_hidl_stress_test \
  VtsHalTvInputV1_0TargetTest \
  VtsHalUsbV1_0TargetTest \
  VtsHalVibratorV1_0TargetTest \
  VtsHalVrV1_0TargetTest \
  VtsHalWifiV1_0TargetTest \
  VtsHalWifiNanV1_0TargetTest \
  VtsHalWifiSupplicantV1_0TargetTest \

vts_test_lib_hidl_packages += \
  libvtswidevine_prebuilt \
