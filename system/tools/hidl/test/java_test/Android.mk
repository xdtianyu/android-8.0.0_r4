LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_MODULE := hidl_test_java_lib
LOCAL_MODULE_STEM := hidl_test_java
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_JAVA_LIBRARIES :=                          \
    android.hidl.base-V1.0-java                  \
    android.hardware.tests.baz-V1.0-java         \
    android.hardware.tests.expression-V1.0-java  \
    android.hardware.tests.inheritance-V1.0-java

include $(BUILD_JAVA_LIBRARY)

################################################################################

include $(CLEAR_VARS)
LOCAL_MODULE := hidl_test_java
LOCAL_MODULE_CLASS := NATIVE_TESTS
LOCAL_SRC_FILES := hidl_test_java

LOCAL_ADDITIONAL_DEPENDENCIES :=                \
    hidl_test_java_lib                          \
    hidl_test_java_native                       \

LOCAL_REQUIRED_MODULES :=                       \
    hidl_test_java_lib                          \
    android.hidl.base-V1.0-java                 \
    android.hardware.tests.baz-V1.0-java

include $(BUILD_PREBUILT)
