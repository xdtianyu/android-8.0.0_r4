LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE:= libpdfiumfxcrt

LOCAL_ARM_MODE := arm
LOCAL_NDK_STL_VARIANT := gnustl_static

LOCAL_CFLAGS += -O3 -fstrict-aliasing -fprefetch-loop-arrays -fexceptions
LOCAL_CFLAGS += -Wno-non-virtual-dtor -Wall -DOPJ_STATIC \
                -DV8_DEPRECATION_WARNINGS -D_CRT_SECURE_NO_WARNINGS
LOCAL_CFLAGS_arm64 += -D_FX_CPU_=_FX_X64_ -fPIC

# Mask some warnings. These are benign, but we probably want to fix them
# upstream at some point.
LOCAL_CFLAGS += -Wno-sign-compare -Wno-unused-parameter
LOCAL_CLANG_CFLAGS += -Wno-sign-compare

LOCAL_SRC_FILES := \
    core/fxcrt/fx_basic_array.cpp \
    core/fxcrt/fx_basic_bstring.cpp \
    core/fxcrt/fx_basic_buffer.cpp \
    core/fxcrt/fx_basic_coords.cpp \
    core/fxcrt/fx_basic_gcc.cpp \
    core/fxcrt/fx_basic_memmgr.cpp \
    core/fxcrt/fx_basic_utf.cpp \
    core/fxcrt/fx_basic_util.cpp \
    core/fxcrt/fx_basic_wstring.cpp \
    core/fxcrt/fx_bidi.cpp \
    core/fxcrt/fx_extension.cpp \
    core/fxcrt/fx_ucddata.cpp \
    core/fxcrt/fx_unicode.cpp \
    core/fxcrt/fx_xml_composer.cpp \
    core/fxcrt/fx_xml_parser.cpp \
    core/fxcrt/fxcrt_posix.cpp \
    core/fxcrt/fxcrt_stream.cpp \
    core/fxcrt/fxcrt_windows.cpp \

LOCAL_C_INCLUDES := \
    external/pdfium \
    external/freetype/include \
    external/freetype/include/freetype

include $(BUILD_STATIC_LIBRARY)
