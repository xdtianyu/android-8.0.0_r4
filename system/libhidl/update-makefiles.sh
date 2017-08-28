#!/bin/bash

# TODO(b/33276472)
if [ ! -d system/libhidl/transport ] ; then
  echo "Where is system/libhidl/transport?";
  exit 1;
fi

packages=(
    android.hidl.allocator@1.0
    android.hidl.base@1.0
    android.hidl.manager@1.0
    android.hidl.memory@1.0
    android.hidl.token@1.0
)

for package in "${packages[@]}"; do
    echo "Updating $package."
    hidl-gen -Lmakefile -r android.hidl:system/libhidl/transport $package
    hidl-gen -Landroidbp -r android.hidl:system/libhidl/transport $package
done
