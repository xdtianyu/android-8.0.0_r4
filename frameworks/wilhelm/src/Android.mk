LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES :=     \
        assert.cpp        \
        ut/OpenSLESUT.c   \
        ut/slesutResult.c

LOCAL_C_INCLUDES:= $(LOCAL_PATH)/../include

LOCAL_CFLAGS += -fvisibility=hidden -UNDEBUG
LOCAL_CFLAGS += -Wall -Werror

LOCAL_MODULE := libOpenSLESUT

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_C_INCLUDES:= $(LOCAL_PATH)/../include

LOCAL_CFLAGS += -Wno-initializer-overrides
# -Wno-missing-field-initializers
# optional, see comments in MPH_to.c: -DUSE_DESIGNATED_INITIALIZERS -S
# and also see ../tools/mphgen/Makefile
LOCAL_CFLAGS += -DUSE_DESIGNATED_INITIALIZERS -UNDEBUG
LOCAL_CFLAGS += -Wall -Werror

LOCAL_SRC_FILES:=                     \
        assert.cpp \
        MPH_to.c \
        handlers.c

LOCAL_MODULE:= libopensles_helper

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

# do not claim support for any OpenSL ES or OpenMAX AL profiles
LOCAL_CFLAGS += -DUSE_PROFILES=0

# enable API logging; details are set separately by SL_TRACE_DEFAULT below
LOCAL_CFLAGS += -DUSE_TRACE
# or -UUSE_TRACE to disable API logging

# see Configuration.h for USE_DEBUG

# enable assert() to do runtime checking
LOCAL_CFLAGS += -UNDEBUG
# or -DNDEBUG for no runtime checking

# select the level of log messages
LOCAL_CFLAGS += -DUSE_LOG=SLAndroidLogLevel_Info
# or -DUSE_LOG=SLAndroidLogLevel_Verbose for verbose logging

# log all API entries and exits (also requires Debug or Verbose log level)
# LOCAL_CFLAGS += -DSL_TRACE_DEFAULT=SL_TRACE_ALL
# (otherwise a warning log on error results only)

# API level
LOCAL_CFLAGS += -DPLATFORM_SDK_VERSION=$(PLATFORM_SDK_VERSION)

# Reduce size of .so and hide internal global symbols
LOCAL_CFLAGS += -fvisibility=hidden -DLI_API='__attribute__((visibility("default")))'

LOCAL_SRC_FILES:=                     \
        OpenSLES_IID.cpp              \
        assert.cpp                    \
        classes.cpp                   \
        data.cpp                      \
        devices.cpp                   \
        entry.cpp                     \
        handler_bodies.cpp            \
        trace.cpp                     \
        locks.cpp                     \
        sles.cpp                      \
        sl_iid.cpp                    \
        sllog.cpp                     \
        ThreadPool.cpp                \
        android/AudioPlayer_to_android.cpp    \
        android/AudioRecorder_to_android.cpp  \
        android/MediaPlayer_to_android.cpp    \
        android/OutputMix_to_android.cpp      \
        android/VideoCodec_to_android.cpp     \
        android/BufferQueueSource.cpp         \
        android/CallbackProtector.cpp         \
        android/AacBqToPcmCbRenderer.cpp      \
        android/android_AudioSfDecoder.cpp    \
        android/android_AudioToCbRenderer.cpp \
        android/android_GenericMediaPlayer.cpp\
        android/android_GenericPlayer.cpp     \
        android/android_LocAVPlayer.cpp       \
        android/android_StreamPlayer.cpp      \
        android/android_Effect.cpp            \
        android/util/AacAdtsExtractor.cpp     \
        android/channels.cpp                  \
        autogen/IID_to_MPH.cpp                \
        objects/C3DGroup.cpp                  \
        objects/CAudioPlayer.cpp              \
        objects/CAudioRecorder.cpp            \
        objects/CEngine.cpp                   \
        objects/COutputMix.cpp                \
        objects/CMediaPlayer.cpp              \
        itf/IAndroidBufferQueue.cpp       \
        itf/IAndroidConfiguration.cpp     \
        itf/IAndroidEffect.cpp            \
        itf/IAndroidEffectCapabilities.cpp\
        itf/IAndroidEffectSend.cpp        \
        itf/IAcousticEchoCancellation.cpp \
        itf/IAutomaticGainControl.cpp     \
        itf/IBassBoost.cpp                \
        itf/IBufferQueue.cpp              \
        itf/IDynamicInterfaceManagement.cpp\
        itf/IEffectSend.cpp               \
        itf/IEngine.cpp                   \
        itf/IEngineCapabilities.cpp       \
        itf/IEnvironmentalReverb.cpp      \
        itf/IEqualizer.cpp                \
        itf/IMetadataExtraction.cpp       \
        itf/INoiseSuppression.cpp         \
        itf/IMuteSolo.cpp                 \
        itf/IObject.cpp                   \
        itf/IOutputMix.cpp                \
        itf/IPlay.cpp                     \
        itf/IPlaybackRate.cpp             \
        itf/IPrefetchStatus.cpp           \
        itf/IPresetReverb.cpp             \
        itf/IRecord.cpp                   \
        itf/ISeek.cpp                     \
        itf/IStreamInformation.cpp        \
        itf/IVideoDecoderCapabilities.cpp \
        itf/IVirtualizer.cpp              \
        itf/IVolume.cpp

EXCLUDE_SRC :=                            \
        sync.cpp                          \
        itf/I3DCommit.cpp                 \
        itf/I3DDoppler.cpp                \
        itf/I3DGrouping.cpp               \
        itf/I3DLocation.cpp               \
        itf/I3DMacroscopic.cpp            \
        itf/I3DSource.cpp                 \
        itf/IAudioDecoderCapabilities.cpp \
        itf/IAudioEncoder.cpp             \
        itf/IAudioEncoderCapabilities.cpp \
        itf/IAudioIODeviceCapabilities.cpp\
        itf/IDeviceVolume.cpp             \
        itf/IDynamicSource.cpp            \
        itf/ILEDArray.cpp                 \
        itf/IMIDIMessage.cpp              \
        itf/IMIDIMuteSolo.cpp             \
        itf/IMIDITempo.cpp                \
        itf/IMIDITime.cpp                 \
        itf/IMetadataTraversal.cpp        \
        itf/IPitch.cpp                    \
        itf/IRatePitch.cpp                \
        itf/IThreadSync.cpp               \
        itf/IVibra.cpp                    \
        itf/IVisualization.cpp

LOCAL_C_INCLUDES:=                                                  \
        $(LOCAL_PATH)/../include                                    \
        frameworks/av/media/libstagefright                        \
        frameworks/av/media/libstagefright/include                \
        frameworks/av/media/libstagefright/http                     \
        frameworks/native/include/media/openmax

LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/../include

LOCAL_CFLAGS += -Wno-multichar -Wno-invalid-offsetof

LOCAL_CFLAGS += -Wall -Wextra -Wno-unused-parameter -Werror

LOCAL_STATIC_LIBRARIES += \
        libopensles_helper        \
        libOpenSLESUT

LOCAL_SHARED_LIBRARIES :=         \
        liblog                    \
        libutils                  \
        libmedia                  \
        libaudioclient            \
        libaudiomanager           \
        libbinder                 \
        libstagefright            \
        libstagefright_foundation \
        libcutils                 \
        libgui                    \
        libdl                     \
        libandroid_runtime

LOCAL_EXPORT_SHARED_LIBRARY_HEADERS := \
        libmedia                       \
        libaudioclient

# For Brillo, we do not want this dependency as it significantly increases the
# size of the checkout. Also, the library is dependent on Java (which is not
# present in Brillo), so it doesn't really make sense to have it anyways. See
# b/24507845 for more details.
ifndef BRILLO
LOCAL_SHARED_LIBRARIES += \
        libstagefright_http_support
endif

LOCAL_MODULE := libwilhelm

ifeq ($(TARGET_BUILD_VARIANT),userdebug)
        LOCAL_CFLAGS += -DUSERDEBUG_BUILD=1
endif

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := sl_entry.cpp sl_iid.cpp assert.cpp
LOCAL_C_INCLUDES:=                                                  \
        frameworks/av/media/libstagefright                        \
        frameworks/av/media/libstagefright/include                \
        frameworks/native/include/media/openmax
LOCAL_MODULE := libOpenSLES
LOCAL_EXPORT_SHARED_LIBRARY_HEADERS := libwilhelm
LOCAL_CFLAGS += -DLI_API= -fvisibility=hidden -UNDEBUG \
                -DSL_API='__attribute__((visibility("default")))'
LOCAL_CFLAGS += -Wall -Werror
LOCAL_SHARED_LIBRARIES := libwilhelm liblog
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := xa_entry.cpp xa_iid.cpp assert.cpp
LOCAL_C_INCLUDES:=                                                  \
        frameworks/av/media/libstagefright                        \
        frameworks/av/media/libstagefright/include                \
        frameworks/native/include/media/openmax
LOCAL_MODULE := libOpenMAXAL
LOCAL_EXPORT_SHARED_LIBRARY_HEADERS := libwilhelm
LOCAL_CFLAGS += -DLI_API= -fvisibility=hidden -UNDEBUG \
                -DXA_API='__attribute__((visibility("default")))'
LOCAL_CFLAGS += -Wall -Werror
LOCAL_SHARED_LIBRARIES := libwilhelm liblog
include $(BUILD_SHARED_LIBRARY)
