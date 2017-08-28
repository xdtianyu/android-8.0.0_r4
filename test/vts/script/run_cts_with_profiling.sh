#!/bin/bash
#
# Copyright 2016 Google Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# allow write to /system partition
adb root
adb disable-verity
adb reboot
adb wait-for-device
adb root
adb remount

# push profiler libs
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.audio@2.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.audio.common@2.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.audio.effect@2.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.automotive.vehicle@2.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.automotive.vehicle@2.1-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.biometrics.fingerprint@2.1-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.bluetooth@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.boot@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.broadcastradio@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.camera.common@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.camera.device@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.camera.device@3.2-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.camera.metadata@3.2-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.camera.provider@2.4-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.configstore@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.contexthub@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.drm@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.dumpstate@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.gatekeeper@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.gnss@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.graphics.allocator@2.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.graphics.common@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.graphics.composer@2.1-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.graphics.mapper@2.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.health@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.ir@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.keymaster@3.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.light@2.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.media.omx@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.memtrack@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.nfc@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.power@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.radio@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.sensors@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.soundtrigger@2.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.thermal@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.tv.cec@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.tv.input@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.usb@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.vibrator@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.vr@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.wifi@1.0-vts.profiler.so system/lib64/
adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/lib64/android.hardware.wifi.supplicant@1.0-vts.profiler.so system/lib64/

adb push ${ANDROID_BUILD_TOP}/out/host/linux-x86/vts/android-vts/testcases/DATA/bin/vts_profiling_configure /data/local/tmp/

# get cts testcases
tests=()
while read -r test
do
  tests+=($test)
done < "${ANDROID_BUILD_TOP}/test/vts/script/cts_test_list.txt"

# run cts testcases
DATE=`date +%Y-%m-%d`
for i in ${tests[@]}
do
  echo Running $i
  adb shell rm /data/local/tmp/*.vts.trace
  adb reboot
  adb wait-for-device
  adb root
  sleep 5
  adb shell setenforce 0
  adb shell chmod 777 -R data/local/tmp
  adb shell ./data/local/tmp/vts_profiling_configure enable /system/lib64/
  cts-tradefed run commandAndExit cts --skip-device-info --skip-system-status-check com.android.compatibility.common.tradefed.targetprep.NetworkConnectivityChecker --skip-system-status-check com.android.tradefed.suite.checker.KeyguardStatusChecker -m $i
  adb shell ls /data/local/tmp/*.vts.trace > temp
  trace_path=$1/cts-traces/$DATE/$i
  rm -rf $trace_path
  mkdir -p $trace_path
  while read -r trace
  do
    adb pull $trace $trace_path
  done < "temp"
done

echo "done"
