LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := desugar
LOCAL_SRC_FILES := $(call all-java-files-under, java)
LOCAL_JAR_MANIFEST := manifest.txt
LOCAL_STATIC_JAVA_LIBRARIES := \
    asm-5.2 \
    asm-commons-5.2 \
    asm-tree-5.2 \
    guava-20.0 \
    jsr305-3.0.1 \
    dagger2-auto-value-host \

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_IS_HOST_MODULE := true

# Use Dagger2 annotation processor
# b/25860419: annotation processors must be explicitly specified for grok
PROCESSOR_LIBRARIES := dagger2-auto-value-host
PROCESSOR_CLASSES := com.google.auto.value.processor.AutoValueProcessor
include external/dagger2/java_annotation_processors.mk

include $(BUILD_HOST_JAVA_LIBRARY)
