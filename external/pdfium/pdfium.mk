LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := libpdfium

LOCAL_ARM_MODE := arm
LOCAL_NDK_STL_VARIANT := gnustl_static

LOCAL_CFLAGS += -O3 -fstrict-aliasing -fprefetch-loop-arrays -fexceptions
LOCAL_CFLAGS += -Wno-non-virtual-dtor -Wall -DOPJ_STATIC \
                -DV8_DEPRECATION_WARNINGS -D_CRT_SECURE_NO_WARNINGS

# Mask some warnings. These are benign, but we probably want to fix them
# upstream at some point.
LOCAL_CFLAGS += -Wno-sign-compare -Wno-unused-parameter
LOCAL_CLANG_CFLAGS += -Wno-sign-compare

LOCAL_STATIC_LIBRARIES := libpdfiumformfiller \
                          libpdfiumpdfwindow \
                          libpdfiumjavascript \
                          libpdfiumfpdfapi \
                          libpdfiumfxge \
                          libpdfiumfxedit \
                          libpdfiumfpdftext \
                          libpdfiumfxcrt \
                          libpdfiumfxcodec \
                          libpdfiumfpdfdoc \
                          libpdfiumfdrm \
                          libpdfiumagg23 \
                          libpdfiumbigint \
                          libpdfiumlcms \
                          libpdfiumjpeg \
                          libpdfiumopenjpeg \
                          libpdfiumzlib


# TODO: figure out why turning on exceptions requires manually linking libdl
LOCAL_SHARED_LIBRARIES := libdl libft2

LOCAL_SRC_FILES := \
    fpdfsdk/cba_annotiterator.cpp \
    fpdfsdk/cfx_systemhandler.cpp \
    fpdfsdk/cpdfsdk_annot.cpp \
    fpdfsdk/cpdfsdk_annothandlermgr.cpp \
    fpdfsdk/cpdfsdk_annotiteration.cpp \
    fpdfsdk/cpdfsdk_baannot.cpp \
    fpdfsdk/cpdfsdk_baannothandler.cpp \
    fpdfsdk/cpdfsdk_datetime.cpp \
    fpdfsdk/cpdfsdk_formfillenvironment.cpp \
    fpdfsdk/cpdfsdk_interform.cpp \
    fpdfsdk/cpdfsdk_pageview.cpp \
    fpdfsdk/cpdfsdk_widget.cpp \
    fpdfsdk/cpdfsdk_widgethandler.cpp \
    fpdfsdk/fpdf_dataavail.cpp \
    fpdfsdk/fpdf_ext.cpp \
    fpdfsdk/fpdf_flatten.cpp \
    fpdfsdk/fpdf_progressive.cpp \
    fpdfsdk/fpdf_searchex.cpp \
    fpdfsdk/fpdf_structtree.cpp \
    fpdfsdk/fpdf_sysfontinfo.cpp \
    fpdfsdk/fpdf_transformpage.cpp \
    fpdfsdk/fpdfdoc.cpp \
    fpdfsdk/fpdfeditimg.cpp \
    fpdfsdk/fpdfeditpage.cpp \
    fpdfsdk/fpdfeditpath.cpp \
    fpdfsdk/fpdfedittext.cpp \
    fpdfsdk/fpdfformfill.cpp \
    fpdfsdk/fpdfppo.cpp \
    fpdfsdk/fpdfsave.cpp \
    fpdfsdk/fpdftext.cpp \
    fpdfsdk/fpdfview.cpp \
    fpdfsdk/fsdk_actionhandler.cpp \
    fpdfsdk/fsdk_pauseadapter.cpp \
    fpdfsdk/pdfsdk_fieldaction.cpp \

LOCAL_C_INCLUDES := \
    external/pdfium \
    external/freetype/include \
    external/freetype/include/freetype

include $(BUILD_SHARED_LIBRARY)
