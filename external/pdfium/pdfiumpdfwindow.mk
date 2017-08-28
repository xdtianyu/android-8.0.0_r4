LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE:= libpdfiumpdfwindow

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
    fpdfsdk/pdfwindow/PWL_Button.cpp \
    fpdfsdk/pdfwindow/PWL_Caret.cpp \
    fpdfsdk/pdfwindow/PWL_ComboBox.cpp \
    fpdfsdk/pdfwindow/PWL_Edit.cpp \
    fpdfsdk/pdfwindow/PWL_EditCtrl.cpp \
    fpdfsdk/pdfwindow/PWL_FontMap.cpp \
    fpdfsdk/pdfwindow/PWL_Icon.cpp \
    fpdfsdk/pdfwindow/PWL_ListBox.cpp \
    fpdfsdk/pdfwindow/PWL_ScrollBar.cpp \
    fpdfsdk/pdfwindow/PWL_SpecialButton.cpp \
    fpdfsdk/pdfwindow/PWL_Utils.cpp \
    fpdfsdk/pdfwindow/PWL_Wnd.cpp \
    fpdfsdk/pdfwindow/cpwl_color.cpp \

LOCAL_C_INCLUDES := \
    external/pdfium \
    external/freetype/include \
    external/freetype/include/freetype

include $(BUILD_STATIC_LIBRARY)
