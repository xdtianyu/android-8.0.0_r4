LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE:= libpdfiumfpdfdoc

LOCAL_ARM_MODE := arm
LOCAL_NDK_STL_VARIANT := gnustl_static

LOCAL_CFLAGS += -O3 -fstrict-aliasing -fprefetch-loop-arrays -fexceptions
LOCAL_CFLAGS += -Wno-non-virtual-dtor -Wall -DOPJ_STATIC \
                -DV8_DEPRECATION_WARNINGS -D_CRT_SECURE_NO_WARNINGS

# Mask some warnings. These are benign, but we probably want to fix them
# upstream at some point.
LOCAL_CFLAGS += -Wno-sign-compare -Wno-unused-parameter
LOCAL_CLANG_CFLAGS += -Wno-sign-compare

LOCAL_SRC_FILES := \
    core/fpdfdoc/cline.cpp \
    core/fpdfdoc/clines.cpp \
    core/fpdfdoc/cpdf_aaction.cpp \
    core/fpdfdoc/cpdf_action.cpp \
    core/fpdfdoc/cpdf_actionfields.cpp \
    core/fpdfdoc/cpdf_annot.cpp \
    core/fpdfdoc/cpdf_annotlist.cpp \
    core/fpdfdoc/cpdf_apsettings.cpp \
    core/fpdfdoc/cpdf_bookmark.cpp \
    core/fpdfdoc/cpdf_bookmarktree.cpp \
    core/fpdfdoc/cpdf_defaultappearance.cpp \
    core/fpdfdoc/cpdf_dest.cpp \
    core/fpdfdoc/cpdf_docjsactions.cpp \
    core/fpdfdoc/cpdf_filespec.cpp \
    core/fpdfdoc/cpdf_formcontrol.cpp \
    core/fpdfdoc/cpdf_formfield.cpp \
    core/fpdfdoc/cpdf_iconfit.cpp \
    core/fpdfdoc/cpdf_interform.cpp \
    core/fpdfdoc/cpdf_link.cpp \
    core/fpdfdoc/cpdf_linklist.cpp \
    core/fpdfdoc/cpdf_metadata.cpp \
    core/fpdfdoc/cpdf_nametree.cpp \
    core/fpdfdoc/cpdf_numbertree.cpp \
    core/fpdfdoc/cpdf_occontext.cpp \
    core/fpdfdoc/cpdf_pagelabel.cpp \
    core/fpdfdoc/cpdf_variabletext.cpp \
    core/fpdfdoc/cpdf_viewerpreferences.cpp \
    core/fpdfdoc/cpvt_color.cpp \
    core/fpdfdoc/cpvt_fontmap.cpp \
    core/fpdfdoc/cpvt_generateap.cpp \
    core/fpdfdoc/cpvt_sectioninfo.cpp \
    core/fpdfdoc/cpvt_wordinfo.cpp \
    core/fpdfdoc/csection.cpp \
    core/fpdfdoc/ctypeset.cpp \
    core/fpdfdoc/doc_tagged.cpp \

LOCAL_C_INCLUDES := \
    external/pdfium \
    external/freetype/include \
    external/freetype/include/freetype

include $(BUILD_STATIC_LIBRARY)
