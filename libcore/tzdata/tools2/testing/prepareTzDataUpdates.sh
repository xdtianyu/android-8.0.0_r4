#!/bin/bash

# Used as part of manual testing of tzdata update mechanism.

if [[ -z "${ANDROID_BUILD_TOP}" ]]; then
  echo "Configure your environment with build/envsetup.sh and lunch"
  exit 1
fi

# Fail if anything below fails
set -e

cd $ANDROID_BUILD_TOP

# Get the ICU version by looking at the stubdata file
ICU_STUB_FILE=$(ls external/icu/icu4c/source/stubdata/icudt*l.dat)
if [ $(echo "$ICU_STUB_FILE" | wc -l) -ne 1 ]; then
  echo "Could not find unique ICU version from external/icu/icu4c/source/stubdata/icudt*l.dat"
  exit 1
fi

ICU_VERSION=${ICU_STUB_FILE##*icudt}
ICU_VERSION=${ICU_VERSION%l.dat}

echo "Current ICU version is ${ICU_VERSION}"

# Get the current tzdata version and find both the previous and new versions.
TZDATA=libc/zoneinfo/tzdata
TZLOOKUP=libc/zoneinfo/tzlookup.xml

TZHEADER=$(head -n1 bionic/$TZDATA | cut -c1-11)

TZ_CURRENT=${TZHEADER:6}

let currentYear=${TZ_CURRENT:0:4}
update=${TZ_CURRENT:4:1}

let currentUpdate=$(LC_CTYPE=C printf '%d' "'$update")
let previousUpdate=currentUpdate-1
if [ $previousUpdate -lt 97 ]; then
  let previousYear=currentYear-1
  PREVIOUSCOMMIT=$(cd bionic; git log --format=oneline ${TZDATA} | grep -E "${previousYear}.$" | head -n1)
  TZ_PREVIOUS=$(echo $PREVIOUSCOMMIT | sed "s|.*\(${previousYear}.\)\$|\1|")
else
  TZ_PREVIOUS=${currentYear}$(printf "\\$(printf '%03o' "${previousUpdate}")")
  PREVIOUSCOMMIT=$(cd bionic; git log --format=oneline ${TZDATA} | grep -E "${TZ_PREVIOUS}$" | head -n1)
fi

let nextUpdate=currentUpdate+1
TZ_NEXT=${currentYear}$(printf "\\$(printf '%03o' "${nextUpdate}")")

echo "Current version of bionic/${TZDATA} is ${TZ_CURRENT}"
echo "Previous version of bionic/${TZDATA} is ${TZ_PREVIOUS} from commit:"
echo "    ${PREVIOUSCOMMIT}"
echo "Next version of bionic/${TZDATA} is ${TZ_NEXT}"

TMP=$(mktemp -d)

TZ_PREVIOUS_SHA=${PREVIOUSCOMMIT%% *}

TMP_PREVIOUS=${TMP}/${TZ_PREVIOUS}_test
mkdir -p ${TMP_PREVIOUS}

echo "Copied ${TZ_PREVIOUS} tzdata to ${TMP_PREVIOUS}"
(cd bionic; git show ${TZ_PREVIOUS_SHA}:${TZDATA} > ${TMP_PREVIOUS}/tzdata)

cd libcore/tzdata/tools2

################################################################
# Preparing previous update version.
################################################################

# Create an icu_tzdata.dat for the previous tzdata version.
# Download the archive for the previous tzdata version.
PREVIOUS_TAR_GZ="tzdata${TZ_PREVIOUS}.tar.gz"
TMP_PREVIOUS_TAR_GZ=${TMP}/${PREVIOUS_TAR_GZ}
IANA_PREVIOUS_URL="ftp://ftp.iana.org/tz/releases/${PREVIOUS_TAR_GZ}"
echo "Downloading archive for ${TZ_PREVIOUS} version from ${IANA_PREVIOUS_URL}"
wget -O ${TMP_PREVIOUS_TAR_GZ} ftp://ftp.iana.org/tz/releases/tzdata2016d.tar.gz -o ${TMP}/${PREVIOUS_TAR_GZ}.log

ICU_UPDATE_RESOURCES_LOG=${TMP}/icuUpdateResources.log
echo "Creating icu_tzdata.dat file for ${TZ_PREVIOUS}/ICU ${ICU_VERSION}, may take a while, check ${ICU_UPDATE_RESOURCES_LOG} for details"
./createIcuUpdateResources.sh ${TMP_PREVIOUS_TAR_GZ} ${ICU_VERSION} &> ${ICU_UPDATE_RESOURCES_LOG}
mv icu_tzdata.dat ${TMP_PREVIOUS}

TZ_PREVIOUS_UPDATE_PROPERTIES=${TMP}/tzupdate.properties.${TZ_PREVIOUS}
cat > ${TZ_PREVIOUS_UPDATE_PROPERTIES} <<EOF
revision=1
rules.version=${TZ_PREVIOUS}
bionic.file=${TMP_PREVIOUS}/tzdata
icu.file=${TMP_PREVIOUS}/icu_tzdata.dat
tzlookup.file=${ANDROID_BUILD_TOP}/bionic/${TZLOOKUP}
EOF

TZ_PREVIOUS_UPDATE_ZIP=update_${TZ_PREVIOUS}_test.zip
./createTimeZoneDistro.sh ${TZ_PREVIOUS_UPDATE_PROPERTIES} ${TMP}/update_${TZ_PREVIOUS}_test.zip
adb push ${TMP}/${TZ_PREVIOUS_UPDATE_ZIP} /data/local/tmp
echo "Pushed ${TZ_PREVIOUS_UPDATE_ZIP} to /data/local/tmp"

################################################################
# Preparing current update version.
################################################################

# Replace the version number in the current tzdata to create the current version
TMP_CURRENT=${TMP}/${TZ_CURRENT}_test
mkdir -p ${TMP_CURRENT}
sed "1s/^tzdata${TZ_PREVIOUS}/tzdata${TZ_NEXT}/" ${TMP_PREVIOUS}/tzdata > ${TMP_CURRENT}/tzdata
echo "Transformed version ${TZ_PREVIOUS} of tzdata into version ${TZ_CURRENT}"

# Replace the version number in the previous icu_tzdata to create the next version
SEARCH=$(echo ${TZ_PREVIOUS} | sed "s/\(.\)/\1\\\x00/g")
REPLACE=$(echo ${TZ_CURRENT} | sed "s/\(.\)/\1\\\x00/g")
sed "s/$SEARCH/$REPLACE/" ${TMP_PREVIOUS}/icu_tzdata.dat > ${TMP_CURRENT}/icu_tzdata.dat
echo "Transformed version ${TZ_PREVIOUS} of icu_tzdata into version ${TZ_CURRENT}"

TZ_CURRENT_UPDATE_PROPERTIES=${TMP}/tzupdate.properties.${TZ_CURRENT}
cat > ${TZ_CURRENT_UPDATE_PROPERTIES} <<EOF
revision=1
rules.version=${TZ_CURRENT}
bionic.file=${TMP_CURRENT}/tzdata
icu.file=${TMP_CURRENT}/icu_tzdata.dat
tzlookup.file=${ANDROID_BUILD_TOP}/bionic/${TZLOOKUP}
EOF

TZ_CURRENT_UPDATE_ZIP=update_${TZ_CURRENT}_test.zip
./createTimeZoneDistro.sh ${TZ_CURRENT_UPDATE_PROPERTIES} ${TMP}/update_${TZ_CURRENT}_test.zip
adb push ${TMP}/${TZ_CURRENT_UPDATE_ZIP} /data/local/tmp
echo "Pushed ${TZ_CURRENT_UPDATE_ZIP} to /data/local/tmp"

################################################################
# Preparing next update version.
################################################################

# Replace the version number in the previous tzdata to create the next version
TMP_NEXT=${TMP}/${TZ_NEXT}_test
mkdir -p ${TMP_NEXT}
sed "1s/^tzdata${TZ_PREVIOUS}/tzdata${TZ_NEXT}/" ${TMP_PREVIOUS}/tzdata > ${TMP_NEXT}/tzdata
echo "Transformed version ${TZ_PREVIOUS} of tzdata into version ${TZ_NEXT}"

# Replace the version number in the previous icu_tzdata to create the next version
SEARCH=$(echo ${TZ_PREVIOUS} | sed "s/\(.\)/\1\\\x00/g")
REPLACE=$(echo ${TZ_NEXT} | sed "s/\(.\)/\1\\\x00/g")
sed "s/$SEARCH/$REPLACE/" ${TMP_PREVIOUS}/icu_tzdata.dat > ${TMP_NEXT}/icu_tzdata.dat
echo "Transformed version ${TZ_PREVIOUS} of icu_tzdata into version ${TZ_NEXT}"

TZ_NEXT_UPDATE_PROPERTIES=${TMP}/tzupdate.properties.${TZ_NEXT}
cat > ${TZ_NEXT_UPDATE_PROPERTIES} <<EOF
revision=1
rules.version=${TZ_NEXT}
bionic.file=${TMP_NEXT}/tzdata
icu.file=${TMP_NEXT}/icu_tzdata.dat
tzlookup.file=${ANDROID_BUILD_TOP}/bionic/${TZLOOKUP}
EOF

TZ_NEXT_UPDATE_ZIP=update_${TZ_NEXT}_test.zip
./createTimeZoneDistro.sh ${TZ_NEXT_UPDATE_PROPERTIES} ${TMP}/update_${TZ_NEXT}_test.zip
adb push ${TMP}/${TZ_NEXT_UPDATE_ZIP} /data/local/tmp
echo "Pushed ${TZ_NEXT_UPDATE_ZIP} to /data/local/tmp"

################################################################
# Preparing UpdateTestApp
################################################################

UPDATE_TEST_APP_LOG=${TMP}/updateTestApp.log
echo "Building and installing UpdateTestApp, check ${UPDATE_TEST_APP_LOG}"
cd $ANDROID_BUILD_TOP
make -j -l8 UpdateTestApp &> ${UPDATE_TEST_APP_LOG}
adb install -r ${ANDROID_PRODUCT_OUT}/data/app/UpdateTestApp/UpdateTestApp.apk &>> ${UPDATE_TEST_APP_LOG}

echo
echo "Paste the following into your shell"
echo "OLD_TZUPDATE=${TMP}/${TZ_PREVIOUS_UPDATE_ZIP}"
echo "CURRENT_TZUPDATE=${TMP}/${TZ_CURRENT_UPDATE_ZIP}"
echo "NEW_TZUPDATE=${TMP}/${TZ_NEXT_UPDATE_ZIP}"
