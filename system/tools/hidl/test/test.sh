#!/bin/bash

# Run hidl-gen against interfaces in errors/ to ensure it detects as many
# errors as possible.

if [ ! -d system/tools/hidl/test/errors/ ]; then
    echo "Where is system/tools/hidl/test/errors?";
    exit 1;
fi

# TODO(b/33276472)
if [ ! -d system/libhidl/transport ]; then
    echo "Where is system/libhidl/transport?";
    exit 1;
fi

if [[ "$@" == *"-h"* ]]; then
    echo "$0 [-h|-u|-a]"
    echo "    (No options)  Run and diff against expected output"
    echo "    -u            Update expected output"
    echo "    -a            Run and show actual output"
    echo "    -h            Show help text"
    exit 1
fi

if [[ "$@" == *"-u"* ]]; then update_files=true; fi
if [[ "$@" == *"-a"* ]]; then show_output=true; fi

function check() {
    local "${@}"
    COMMAND="hidl-gen -Lc++ -rtests:system/tools/hidl/test -randroid.hidl:system/libhidl/transport -o /tmp $package"

    if [ $show_output ]; then
        echo "Running: $COMMAND"
        $COMMAND
        echo
        return
    fi

    if [[ ! -z "$contains" ]]; then
        if [ $update_files ]; then
            # no files to update
            return
        fi
        $COMMAND 2>&1 | grep "$contains" -q
        if [ $? -eq 0 ]; then
            echo "Success for $package."
        else
            echo "Fail for $package; output doesn't contain '$contains'"
        fi
        return
    fi

    EXPECTED="system/tools/hidl/test/$filename"
    if [ $update_files ]; then
        $COMMAND 2>$EXPECTED;
        echo "Updated $filename."
    else
        $COMMAND 2>&1 | diff $EXPECTED -
        if [ $? -eq 0 ]; then
            echo "Success for $package."
        fi
    fi
}

check package="tests.errors.syntax@1.0" filename="errors/syntax.output"

check package="tests.errors.versioning@2.2" \
    contains="Cannot enforce minor version uprevs for tests.errors.versioning@2.2"

check package="tests.errors.versioning@3.3" \
    contains="Cannot enforce minor version uprevs for tests.errors.versioning@3.3"
