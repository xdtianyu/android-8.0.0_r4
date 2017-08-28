LOCAL_PATH := $(call my-dir)

# Unit tests are in Android.bp

#
# Canned perf.data file needed by unit test.
#
include $(CLEAR_VARS)
LOCAL_MODULE := canned.perf.data
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := DATA
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_NATIVE_TESTS)/perfprofd_test
LOCAL_SRC_FILES := canned.perf.data
include $(BUILD_PREBUILT)

#
# Second canned perf.data file needed by unit test.
#
include $(CLEAR_VARS)
LOCAL_MODULE := callchain.canned.perf.data
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := DATA
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_NATIVE_TESTS)/perfprofd_test
LOCAL_SRC_FILES := callchain.canned.perf.data
include $(BUILD_PREBUILT)
