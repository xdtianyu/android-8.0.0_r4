LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE:= libpdfiumfxcodec

LOCAL_ARM_MODE := arm
LOCAL_NDK_STL_VARIANT := gnustl_static

LOCAL_CFLAGS += -O3 -fstrict-aliasing -fprefetch-loop-arrays -fexceptions
LOCAL_CFLAGS += -Wno-non-virtual-dtor -Wall -DOPJ_STATIC \
                -DV8_DEPRECATION_WARNINGS -D_CRT_SECURE_NO_WARNINGS

# Mask some warnings. These are benign, but we probably want to fix them
# upstream at some point.
LOCAL_CFLAGS += -Wno-sign-compare -Wno-pointer-to-int-cast -Wno-unused-parameter
LOCAL_CLANG_CFLAGS += -Wno-sign-compare

LOCAL_SHARED_LIBRARIES := libz

LOCAL_SRC_FILES := \
    core/fxcodec/codec/fx_codec.cpp \
    core/fxcodec/codec/fx_codec_fax.cpp \
    core/fxcodec/codec/fx_codec_flate.cpp \
    core/fxcodec/codec/fx_codec_icc.cpp \
    core/fxcodec/codec/fx_codec_jbig.cpp \
    core/fxcodec/codec/fx_codec_jpeg.cpp \
    core/fxcodec/codec/fx_codec_jpx_opj.cpp \
    core/fxcodec/jbig2/JBig2_ArithDecoder.cpp \
    core/fxcodec/jbig2/JBig2_ArithIntDecoder.cpp \
    core/fxcodec/jbig2/JBig2_BitStream.cpp \
    core/fxcodec/jbig2/JBig2_Context.cpp \
    core/fxcodec/jbig2/JBig2_GrdProc.cpp \
    core/fxcodec/jbig2/JBig2_GrrdProc.cpp \
    core/fxcodec/jbig2/JBig2_GsidProc.cpp \
    core/fxcodec/jbig2/JBig2_HtrdProc.cpp \
    core/fxcodec/jbig2/JBig2_HuffmanDecoder.cpp \
    core/fxcodec/jbig2/JBig2_HuffmanTable.cpp \
    core/fxcodec/jbig2/JBig2_HuffmanTable_Standard.cpp \
    core/fxcodec/jbig2/JBig2_Image.cpp \
    core/fxcodec/jbig2/JBig2_PatternDict.cpp \
    core/fxcodec/jbig2/JBig2_PddProc.cpp \
    core/fxcodec/jbig2/JBig2_SddProc.cpp \
    core/fxcodec/jbig2/JBig2_Segment.cpp \
    core/fxcodec/jbig2/JBig2_SymbolDict.cpp \
    core/fxcodec/jbig2/JBig2_TrdProc.cpp \

LOCAL_C_INCLUDES := \
    external/pdfium \
    external/freetype/include \
    external/freetype/include/freetype

include $(BUILD_STATIC_LIBRARY)
