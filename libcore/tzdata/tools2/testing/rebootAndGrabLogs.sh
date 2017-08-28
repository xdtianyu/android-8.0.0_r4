#!/bin/bash

# Used as part of manual testing of tzdata update mechanism.

PREFIX=${1-bootlogs}

echo "Rebooting...."
adb reboot && adb wait-for-device

TIME=${2-5}
LOGCAT=${PREFIX}.logcat
echo "Dumping logcat output in ${LOGCAT}, waiting for ${TIME} seconds"
adb logcat > ${LOGCAT} 2>/dev/null &
LOGCAT_PID=$!

sleep ${TIME}

# Kill the logcat process and wait, suppresses the Terminated message
# output by the shell.
kill ${LOGCAT_PID}
wait ${LOGCAT_PID} 2>/dev/null

DMESG=${PREFIX}.dmesg
echo "Dumping dmesg output in ${DMESG}"
adb shell dmesg > ${DMESG}
