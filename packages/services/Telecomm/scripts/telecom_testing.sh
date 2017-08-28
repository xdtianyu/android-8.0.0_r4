lite_test_telecom() {
  usage="
  Usage: lite_test_telecom [-c CLASSNAME] [-d] [-a | -i] [-e], where

  -c CLASSNAME          Run tests only for the specified class/method. CLASSNAME
                          should be of the form SomeClassTest or SomeClassTest#testMethod.
  -d                    Waits for a debugger to attach before starting to run tests.
  -i                    Rebuild and reinstall the test apk before running tests (mmm).
  -a                    Rebuild all dependencies and reinstall the test apk before/
                          running tests (mmma).
  -e                    Run code coverage. Coverage will be output into the coverage/
                          directory in the repo root.
  -h                    This help message.
  "

  local OPTIND=1
  local class=
  local install=false
  local installwdep=false
  local debug=false
  local coverage=false

  while getopts "c:hadie" opt; do
    case "$opt" in
      h)
        echo "$usage"
        return 0;;
      \?)
        echo "$usage"
        return 0;;
      c)
        class=$OPTARG;;
      d)
        debug=true;;
      i)
        install=true;;
      a)
        install=true
        installwdep=true;;
      e)
        coverage=true;;
    esac
  done

  local T=$(gettop)

  if [ $install = true ] ; then
    local olddir=$(pwd)
    local emma_opt=
    cd $T
    # Build and exit script early if build fails

    if [ $coverage = true ] ; then
      emma_opt="EMMA_INSTRUMENT=true LOCAL_EMMA_INSTRUMENT=true EMMA_INSTRUMENT_STATIC=true"
    else
      emma_opt="EMMA_INSTRUMENT=false"
    fi

    if [ $installwdep = true ] ; then
      (export ${emma_opt}; mmma -j40 "packages/services/Telecomm/tests")
    else
      (export ${emma_opt}; mmm "packages/services/Telecomm/tests")
    fi
    if [ $? -ne 0 ] ; then
      echo "Make failed! try using -a instead of -i if building with coverage"
      return
    fi

    # Strip off any possible aosp_ prefix from the target product
    local canonical_product=$(sed 's/^aosp_//' <<< "$TARGET_PRODUCT")

    adb install -r -t "out/target/product/$canonical_product/data/app/TelecomUnitTests/TelecomUnitTests.apk"
    if [ $? -ne 0 ] ; then
      cd "$olddir"
      return $?
    fi
    cd "$olddir"
  fi

  local e_options=""
  if [ -n "$class" ] ; then
    e_options="${e_options} -e class com.android.server.telecom.tests.${class}"
  fi
  if [ $debug = true ] ; then
    e_options="${e_options} -e debug 'true'"
  fi
  if [ $coverage = true ] ; then
    e_options="${e_options} -e coverage 'true'"
  fi
  adb shell am instrument ${e_options} -w com.android.server.telecom.tests/android.test.InstrumentationTestRunner

  if [ $coverage = true ] ; then
    adb root
    adb wait-for-device
    adb pull /data/user/0/com.android.server.telecom.tests/files/coverage.ec /tmp/
    if [ ! -d "$T/coverage" ] ; then
      mkdir -p "$T/coverage"
    fi
    java -jar "$T/prebuilts/sdk/tools/jack-jacoco-reporter.jar" \
      --report-dir "$T/coverage/" \
      --metadata-file "$T/out/target/common/obj/APPS/TelecomUnitTests_intermediates/coverage.em" \
      --coverage-file "/tmp/coverage.ec" \
      --source-dir "$T/packages/services/Telecomm/src/"
  fi
}
