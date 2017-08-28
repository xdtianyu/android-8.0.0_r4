#!/bin/bash

# A script to generate TZ data updates.
#
# Usage: ./createTimeZoneDistro.sh <tzupdate.properties file> <output file>
# See libcore.tzdata.update2.tools.CreateTimeZoneDistro for more information.

# Fail if anything below fails
set -e

if [[ -z "${ANDROID_BUILD_TOP}" ]]; then
  echo "Configure your environment with build/envsetup.sh and lunch"
  exit 1
fi

cd ${ANDROID_BUILD_TOP}
make tzdata_tools2-host

TOOLS_LIB=${ANDROID_BUILD_TOP}/out/host/common/obj/JAVA_LIBRARIES/tzdata_tools2-host_intermediates/javalib.jar
SHARED_LIB=${ANDROID_BUILD_TOP}/out/host/common/obj/JAVA_LIBRARIES/tzdata_shared2-host_intermediates/javalib.jar

cd -
java -cp ${TOOLS_LIB}:${SHARED_LIB} libcore.tzdata.update2.tools.CreateTimeZoneDistro $@
