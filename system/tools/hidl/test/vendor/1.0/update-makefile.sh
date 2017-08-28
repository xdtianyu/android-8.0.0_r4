#!/bin/bash

options="-r tests:system/tools/hidl/test/ \
         -r android.hidl:system/libhidl/transport \
         -r android.hardware:hardware/interfaces"

hidl-gen -Lmakefile $options tests.vendor@1.0;
hidl-gen -Landroidbp $options tests.vendor@1.0;
